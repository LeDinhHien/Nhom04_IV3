package client.controller;

import client.model.TransferState;
import client.view.FileMessageCard;
import common.model.FileMetadata;
import common.protocol.FileChunkPacket;
import common.protocol.Packet;
import common.protocol.PacketType;
import common.security.AESUtil;
import common.security.HashUtil;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Kênh TCP số 2: Điều khiển tải tệp lên và đồng bộ P2P ngầm sang bộ đệm .chatapp_cache/<transferId>_<fileName>.
 * - Khi Người gửi (A) chọn file: Tự động chạy tiến trình tải tệp lên 0% -> 100% theo các chunk 64KB mã hóa AES-128,
 *   đồng thời đồng bộ ngầm qua P2P / Server sang thư mục .chatapp_cache của B mà không bắt đợi B phê duyệt.
 * - Bất cứ khi nào A hoặc B click vào tên tệp trên khung chat: Mở JFileChooser (Save Dialog) để lưu tệp.
 *   Nếu đã có trong .chatapp_cache thì ghi ngay và kiểm tra MD5; nếu chưa có thì kéo qua Socket P2P / Server
 *   theo các khối 64KB (hỗ trợ Resume từ file .part) về thẳng đường dẫn vừa chọn.
 */
public class P2PFileController {

    public static final int CHUNK_SIZE = 65536; // 64KB
    private static final int P2P_CONNECT_TIMEOUT_MS = 2000;
    private static final int P2P_SERVER_TIMEOUT_MS = 600000; // 10 phút phục vụ P2P trực tiếp
    private static final File LOCAL_CACHE_DIR = new File(".chatapp_cache");

    private final ChatSocketController chatSocketController;
    private final Component parentComponent;
    private final BiConsumer<String, FileMessageCard> addCardToChatCallback;
    private final Consumer<FileMessageCard> removeCardFromChatCallback;

    private final Map<String, TransferState> activeTransfers = new ConcurrentHashMap<>();
    private final Map<String, FileMessageCard> transferCards = new ConcurrentHashMap<>();
    private final Map<String, ServerSocket> p2pServers = new ConcurrentHashMap<>();

    private File lastDirectory = new File(System.getProperty("user.home"));

    public P2PFileController(ChatSocketController chatSocketController,
                             Component parentComponent,
                             BiConsumer<String, FileMessageCard> addCardToChatCallback,
                             Consumer<FileMessageCard> removeCardFromChatCallback) {
        this.chatSocketController = chatSocketController;
        this.parentComponent = parentComponent;
        this.addCardToChatCallback = addCardToChatCallback;
        this.removeCardFromChatCallback = removeCardFromChatCallback;

        if (!LOCAL_CACHE_DIR.exists()) {
            LOCAL_CACHE_DIR.mkdirs();
        }

        this.chatSocketController.setRelayPacketCallback(this::handleIncomingChunkPacket);
    }

    /**
     * Lấy đường dẫn tệp trong thư mục đệm ẩn .chatapp_cache/<transferId>_<fileName>.
     */
    public File getLocalCacheFile(FileMetadata metadata) {
        if (!LOCAL_CACHE_DIR.exists()) {
            LOCAL_CACHE_DIR.mkdirs();
        }
        String safeName = metadata.getFileName() != null
                ? metadata.getFileName().replaceAll("[\\\\/:*?\"<>|]", "_")
                : "file";
        return new File(LOCAL_CACHE_DIR, metadata.getTransferId() + "_" + safeName);
    }

    /**
     * Khi Người gửi (A) chọn 1 file từ máy tính:
     * Không bắt A chờ B chấp nhận. Đẩy tệp lên phiên chat (chạy 0% -> 100% theo các chunk 64KB mã hóa AES-128),
     * lưu vào bộ đệm .chatapp_cache và đồng bộ ngầm qua P2P sang B.
     */
    public void sendFile(File file, String receiverUsername) {
        if (file == null || !file.exists() || !file.isFile() || receiverUsername == null) {
            return;
        }
        lastDirectory = file.getParentFile();

        String senderUsername = chatSocketController.getSession().getCurrentUser().getUsername();
        String transferId = UUID.randomUUID().toString();
        long fileSize = file.length();
        int totalChunks = Math.max(1, (int) Math.ceil((double) fileSize / CHUNK_SIZE));

        FileMetadata metadata = new FileMetadata(
                transferId,
                file.getName(),
                fileSize,
                "",
                CHUNK_SIZE,
                totalChunks,
                senderUsername,
                receiverUsername
        );
        metadata.setSenderIp(chatSocketController.getSession().getLocalIpAddress());

        TransferState state = new TransferState(metadata, true);
        state.setSourceFile(file);
        activeTransfers.put(transferId, state);

        FileMessageCard card = new FileMessageCard(metadata, true, file);
        bindCardInteractions(card, state);
        transferCards.put(transferId, card);

        if (addCardToChatCallback != null) {
            addCardToChatCallback.accept(receiverUsername, card);
        }

        startImmediateUploadAndSync(state, card);
    }

    /**
     * Thực hiện tải tệp lên từ 0% -> 100% theo từng khối 64KB mã hóa AES-128,
     * mở cổng P2P phục vụ tải trực tiếp và đồng bộ ngầm sang bộ đệm của người nhận.
     */
    private void startImmediateUploadAndSync(TransferState state, FileMessageCard card) {
        FileMetadata metadata = state.getMetadata();
        String transferId = metadata.getTransferId();
        File sourceFile = state.getSourceFile();

        state.setCancelled(false);
        state.setInterrupted(false);

        new Thread(() -> {
            try {
                if (!chatSocketController.isConnected()) {
                    throw new IOException("Mất kết nối máy chủ");
                }

                int startChunk = state.getLastTransferredChunk();
                int totalChunks = metadata.getTotalChunks();
                int initialPercent = totalChunks > 0 ? (int) ((startChunk * 100L) / totalChunks) : 0;
                card.updateTransferProgress(initialPercent, "Đang tải lên... " + initialPercent + "%");

                String md5 = HashUtil.calculateFileMD5(sourceFile);
                metadata.setMd5Checksum(md5);

                // Sao lưu vào bộ đệm cục bộ .chatapp_cache/<transferId>_<fileName>
                File cacheFile = getLocalCacheFile(metadata);
                if (!cacheFile.exists() || cacheFile.length() != sourceFile.length()) {
                    Files.copy(sourceFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                // Mở cổng P2P ngầm để đối phương có thể kết nối trực tiếp kéo chunk bất cứ lúc nào
                openP2PFileServer(state, cacheFile);

                // Báo hiệu cho Server và người nhận (B) biết tệp đang được đẩy lên
                chatSocketController.sendFileTransferRequest(metadata);

                // Đọc tệp theo từng khối 64KB bằng RandomAccessFile, mã hóa AES-128 và đẩy đồng bộ
                try (RandomAccessFile raf = new RandomAccessFile(sourceFile, "r")) {
                    raf.seek((long) startChunk * CHUNK_SIZE);
                    byte[] buffer = new byte[CHUNK_SIZE];

                    for (int chunkId = startChunk; chunkId < totalChunks; chunkId++) {
                        if (state.isCancelled()) {
                            return;
                        }
                        if (!chatSocketController.isConnected()) {
                            throw new IOException("Gián đoạn kết nối khi đang tải tệp lên");
                        }

                        int bytesRead = raf.read(buffer);
                        if (bytesRead == -1) {
                            bytesRead = 0;
                        }
                        byte[] rawChunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                        byte[] encryptedChunk = AESUtil.encryptBytes(rawChunk);
                        boolean isLast = (chunkId == totalChunks - 1);

                        FileChunkPacket chunkPacket = new FileChunkPacket(
                                transferId,
                                chunkId,
                                bytesRead,
                                encryptedChunk,
                                isLast
                        );

                        chatSocketController.sendRelayChunk(metadata.getSender(), metadata.getReceiver(), chunkPacket);

                        int completedChunks = chunkId + 1;
                        state.setLastTransferredChunk(completedChunks);
                        int percent = (int) ((completedChunks * 100L) / totalChunks);
                        card.updateTransferProgress(percent, "Đang tải lên... " + percent + "%");

                        if (totalChunks < 80) {
                            Thread.sleep(12);
                        } else {
                            Thread.sleep(3);
                        }
                    }
                }

                state.setCompleted(true);
                card.markReadyToDownload(cacheFile);
                chatSocketController.sendFileTransferComplete(metadata);
            } catch (Exception e) {
                state.setInterrupted(true);
                card.markInterrupted();
            }
        }, "Upload-Sync-" + transferId).start();
    }

    /**
     * Mở ServerSocket(0) ngầm trên máy gửi để phục vụ kết nối Socket P2P trực tiếp từ người nhận.
     */
    private void openP2PFileServer(TransferState state, File fileToServe) {
        FileMetadata metadata = state.getMetadata();
        String transferId = metadata.getTransferId();
        closeP2PServer(transferId);

        try {
            ServerSocket serverSocket = new ServerSocket(0);
            serverSocket.setSoTimeout(P2P_SERVER_TIMEOUT_MS);
            p2pServers.put(transferId, serverSocket);
            metadata.setSenderP2PPort(serverSocket.getLocalPort());

            Thread p2pAcceptThread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket peerSocket = serverSocket.accept();
                        new Thread(() -> serveChunksToPeerSocket(metadata, fileToServe, peerSocket),
                                "P2P-PeerWorker-" + transferId).start();
                    } catch (IOException e) {
                        break;
                    }
                }
            }, "P2P-Server-" + transferId);
            p2pAcceptThread.setDaemon(true);
            p2pAcceptThread.start();
        } catch (IOException ignored) {
        }
    }

    private void serveChunksToPeerSocket(FileMetadata metadata, File fileToServe, Socket peerSocket) {
        try (Socket socket = peerSocket;
             ObjectOutputStream p2pOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream p2pIn = new ObjectInputStream(socket.getInputStream());
             RandomAccessFile raf = new RandomAccessFile(fileToServe, "r")) {

            p2pOut.flush();
            int startChunk = 0;
            Object req = p2pIn.readObject();
            if (req instanceof Packet) {
                Packet pkt = (Packet) req;
                if (pkt.getType() == PacketType.P2P_HANDSHAKE && pkt.getPayload() instanceof Integer) {
                    startChunk = Math.max(0, (Integer) pkt.getPayload());
                }
            }

            int chunkSize = metadata.getChunkSize() > 0 ? metadata.getChunkSize() : CHUNK_SIZE;
            int totalChunks = Math.max(1, (int) Math.ceil((double) fileToServe.length() / chunkSize));
            raf.seek((long) startChunk * chunkSize);
            byte[] buffer = new byte[chunkSize];

            for (int chunkId = startChunk; chunkId < totalChunks; chunkId++) {
                int bytesRead = raf.read(buffer);
                if (bytesRead == -1) {
                    bytesRead = 0;
                }
                byte[] raw = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                byte[] encrypted = AESUtil.encryptBytes(raw);
                boolean isLast = (chunkId == totalChunks - 1);

                FileChunkPacket chunkPacket = new FileChunkPacket(
                        metadata.getTransferId(),
                        chunkId,
                        bytesRead,
                        encrypted,
                        isLast
                );
                p2pOut.reset();
                p2pOut.writeObject(chunkPacket);
                p2pOut.flush();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Khi Người nhận (B) nhận được thông báo FILE_TRANSFER_REQ:
     * Chuẩn bị bộ đệm ngầm và đồng bộ P2P vào .chatapp_cache/<transferId>_<fileName>.
     */
    public void onIncomingFileRequest(FileMetadata metadata) {
        if (metadata == null) {
            return;
        }
        String transferId = metadata.getTransferId();
        TransferState state = activeTransfers.computeIfAbsent(transferId, k -> new TransferState(metadata, false));
        state.setMetadata(metadata);

        File cacheFile = getLocalCacheFile(metadata);
        state.setPartFile(new File(LOCAL_CACHE_DIR, transferId + ".part"));
        state.setFinalDestinationFile(cacheFile);

        // Thử kết nối P2P trực tiếp tới người gửi để đồng bộ ngầm vào .chatapp_cache nhanh nhất
        if (metadata.getSenderIp() != null && metadata.getSenderP2PPort() > 0) {
            new Thread(() -> syncCacheViaDirectP2P(state, null), "P2P-BackgroundSync-" + transferId).start();
        }
    }

    /**
     * Khi quá trình tải tệp lên của A đạt 100% (Server gửi FILE_TRANSFER_COMPLETE):
     * Hiển thị dòng tệp tin gọn gàng ngay trên khung chat của cả A và B.
     */
    public FileMessageCard getOrCreateCompletedCard(FileMetadata metadata, boolean isMine) {
        if (metadata == null) {
            return null;
        }
        String transferId = metadata.getTransferId();
        File cacheFile = getLocalCacheFile(metadata);

        TransferState state = activeTransfers.computeIfAbsent(transferId, k -> new TransferState(metadata, isMine));
        state.setMetadata(metadata);
        state.setCompleted(true);

        FileMessageCard card = transferCards.get(transferId);
        if (card == null) {
            card = new FileMessageCard(metadata, isMine, cacheFile.exists() ? cacheFile : null);
            bindCardInteractions(card, state);
            transferCards.put(transferId, card);
        }
        card.markReadyToDownload(cacheFile.exists() ? cacheFile : state.getSourceFile());
        return card;
    }

    /**
     * Gắn sự kiện khi người dùng (cả A hoặc B) click vào tên tệp để lưu về máy,
     * hoặc bấm [Gửi lại] | [Xóa] khi có lỗi mạng.
     */
    public void bindCardInteractions(FileMessageCard card, TransferState state) {
        card.setOnClickSaveAction(() -> promptAndSaveFileToDisk(card, state));

        card.setOnRetryAction(() -> {
            if (state.isSenderMode() && state.getSourceFile() != null && state.getSourceFile().exists()) {
                startImmediateUploadAndSync(state, card);
            } else {
                promptAndSaveFileToDisk(card, state);
            }
        });

        card.setOnDeleteAction(() -> {
            state.setCancelled(true);
            closeP2PServer(state.getTransferId());
            activeTransfers.remove(state.getTransferId());
            transferCards.remove(state.getTransferId());
            if (removeCardFromChatCallback != null) {
                removeCardFromChatCallback.accept(card);
            }
        });
    }

    /**
     * Khi A hoặc B click chuột vào dòng tên tệp tin trên khung chat:
     * Mở JFileChooser (Save Dialog) để chọn nơi lưu tệp.
     * - Nếu tệp đã có sẵn trong bộ đệm cục bộ (.chatapp_cache) hoặc tệp gốc của người gửi:
     *   ghi ngay ra đường dẫn vừa chọn, kiểm tra MD5 và báo "Đã lưu tệp thành công".
     * - Nếu chưa có trong bộ đệm cục bộ: kích hoạt luồng Socket P2P / Server kéo các khối 64KB
     *   (hỗ trợ Resume từ file .part) về thẳng đường dẫn vừa chọn.
     */
    private void promptAndSaveFileToDisk(FileMessageCard card, TransferState state) {
        FileMetadata metadata = state.getMetadata();
        if (metadata == null) {
            return;
        }

        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle("Lưu tệp tin về máy");
        chooser.setSelectedFile(new File(lastDirectory, metadata.getFileName()));

        int result = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(parentComponent));
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        File targetFile = chooser.getSelectedFile();
        if (targetFile.getParentFile() != null) {
            lastDirectory = targetFile.getParentFile();
            if (!lastDirectory.exists()) {
                lastDirectory.mkdirs();
            }
        }

        new Thread(() -> {
            try {
                // 1. Kiểm tra nguồn tệp cục bộ hoặc bộ đệm .chatapp_cache
                File readySource = findReadyLocalFile(state, metadata);
                if (readySource != null) {
                    card.updateTransferProgress(50, "Đang ghi tệp và kiểm tra toàn vẹn...");
                    if (!readySource.getCanonicalPath().equals(targetFile.getCanonicalPath())) {
                        Files.copy(readySource.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (verifyMd5Checksum(targetFile, metadata.getMd5Checksum())) {
                        card.markSavedSuccessfully(targetFile);
                        return;
                    }
                }

                // 2. Nếu chưa có trong bộ đệm cục bộ, kéo qua P2P Socket hoặc Server về thẳng đường dẫn vừa chọn
                pullFileChunksToTarget(state, card, targetFile);
            } catch (Exception e) {
                card.markInterrupted();
            }
        }, "Save-File-" + metadata.getTransferId()).start();
    }

    private File findReadyLocalFile(TransferState state, FileMetadata metadata) {
        if (state.getSourceFile() != null
                && state.getSourceFile().exists()
                && state.getSourceFile().length() == metadata.getFileSize()) {
            return state.getSourceFile();
        }
        File cacheFile = getLocalCacheFile(metadata);
        if (cacheFile.exists() && cacheFile.length() == metadata.getFileSize()) {
            return cacheFile;
        }
        return null;
    }

    /**
     * Kéo các khối dữ liệu (Chunk 64KB + Resume qua file .part) về đường dẫn người dùng vừa chọn.
     */
    private void pullFileChunksToTarget(TransferState state, FileMessageCard card, File targetFile) {
        FileMetadata metadata = state.getMetadata();
        int chunkSize = metadata.getChunkSize() > 0 ? metadata.getChunkSize() : CHUNK_SIZE;
        File partFile = new File(
                targetFile.getParentFile(),
                targetFile.getName() + "." + metadata.getMd5Checksum() + ".part"
        );
        state.setPartFile(partFile);
        state.setFinalDestinationFile(targetFile);

        int resumeChunk = 0;
        if (partFile.exists() && partFile.length() > 0) {
            resumeChunk = (int) (partFile.length() / chunkSize);
            if (resumeChunk >= metadata.getTotalChunks()) {
                resumeChunk = 0;
            }
        }
        metadata.setResumeFromChunk(resumeChunk);
        state.setLastTransferredChunk(resumeChunk);

        int initPercent = metadata.getTotalChunks() > 0
                ? (int) ((resumeChunk * 100L) / metadata.getTotalChunks())
                : 0;
        card.updateTransferProgress(initPercent, "Đang tải về... " + initPercent + "%");

        // Ưu tiên 1: Thử kéo trực tiếp qua Socket P2P từ đối phương
        if (syncCacheViaDirectP2P(state, card)) {
            try {
                File cacheFile = getLocalCacheFile(metadata);
                if (partFile.exists() && partFile.length() == metadata.getFileSize()) {
                    Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(targetFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else if (cacheFile.exists() && cacheFile.length() == metadata.getFileSize()) {
                    Files.copy(cacheFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                if (verifyMd5Checksum(targetFile, metadata.getMd5Checksum())) {
                    card.markSavedSuccessfully(targetFile);
                    return;
                }
            } catch (IOException ignored) {
            }
        }

        // Ưu tiên 2: Yêu cầu kéo các chunk còn thiếu qua kênh đồng bộ
        if (!chatSocketController.isConnected()) {
            card.markInterrupted();
            return;
        }
        chatSocketController.sendFileTransferAccept(metadata);
    }

    /**
     * Kết nối Socket P2P trực tiếp tới cổng P2P của người gửi để tải các khối 64KB mã hóa AES-128.
     */
    private boolean syncCacheViaDirectP2P(TransferState state, FileMessageCard cardToUpdate) {
        FileMetadata metadata = state.getMetadata();
        if (metadata.getSenderIp() == null || metadata.getSenderIp().isEmpty() || metadata.getSenderP2PPort() <= 0) {
            return false;
        }

        File cacheFile = getLocalCacheFile(metadata);
        if (cacheFile.exists() && cacheFile.length() == metadata.getFileSize()) {
            return true;
        }

        File writeTarget = (cardToUpdate != null && state.getPartFile() != null)
                ? state.getPartFile()
                : new File(LOCAL_CACHE_DIR, metadata.getTransferId() + ".part");

        int chunkSize = metadata.getChunkSize() > 0 ? metadata.getChunkSize() : CHUNK_SIZE;
        int resumeChunk = 0;
        if (writeTarget.exists() && writeTarget.length() > 0) {
            resumeChunk = (int) (writeTarget.length() / chunkSize);
            if (resumeChunk >= metadata.getTotalChunks()) {
                resumeChunk = 0;
            }
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(metadata.getSenderIp(), metadata.getSenderP2PPort()), P2P_CONNECT_TIMEOUT_MS);
            try (ObjectOutputStream p2pOut = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream p2pIn = new ObjectInputStream(socket.getInputStream());
                 RandomAccessFile raf = new RandomAccessFile(writeTarget, "rw")) {

                p2pOut.flush();
                p2pOut.writeObject(new Packet(
                        PacketType.P2P_HANDSHAKE,
                        metadata.getReceiver(),
                        metadata.getSender(),
                        resumeChunk
                ));
                p2pOut.flush();

                while (!state.isCancelled()) {
                    Object obj = p2pIn.readObject();
                    if (obj instanceof FileChunkPacket) {
                        FileChunkPacket chunk = (FileChunkPacket) obj;
                        byte[] decrypted = AESUtil.decryptBytes(chunk.getEncryptedData());
                        raf.seek((long) chunk.getChunkId() * chunkSize);
                        if (chunk.getActualSize() > 0 && decrypted.length >= chunk.getActualSize()) {
                            raf.write(decrypted, 0, chunk.getActualSize());
                        }

                        int doneChunks = chunk.getChunkId() + 1;
                        state.setLastTransferredChunk(doneChunks);
                        if (cardToUpdate != null) {
                            int pct = (int) ((doneChunks * 100L) / metadata.getTotalChunks());
                            cardToUpdate.updateTransferProgress(pct, "Đang tải về... " + pct + "%");
                        }

                        if (chunk.isLastChunk() || doneChunks >= metadata.getTotalChunks()) {
                            break;
                        }
                    }
                }
            }

            if (writeTarget.exists() && writeTarget.length() == metadata.getFileSize()) {
                Files.copy(writeTarget.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Nhận từng khối dữ liệu (FILE_CHUNK) đồng bộ ngầm vào .chatapp_cache
     * hoặc ghi trực tiếp vào tệp người dùng đang bấm tải về.
     */
    private void handleIncomingChunkPacket(Packet packet) {
        if (packet == null || packet.getType() != PacketType.FILE_CHUNK
                || !(packet.getPayload() instanceof FileChunkPacket)) {
            return;
        }

        FileChunkPacket chunk = (FileChunkPacket) packet.getPayload();
        String transferId = chunk.getTransferId();
        TransferState state = activeTransfers.get(transferId);
        if (state == null || state.isCancelled()) {
            return;
        }

        FileMetadata metadata = state.getMetadata();
        int chunkSize = metadata.getChunkSize() > 0 ? metadata.getChunkSize() : CHUNK_SIZE;
        File cacheFile = getLocalCacheFile(metadata);
        File partFile = state.getPartFile() != null
                ? state.getPartFile()
                : new File(LOCAL_CACHE_DIR, transferId + ".part");

        try {
            byte[] decrypted = AESUtil.decryptBytes(chunk.getEncryptedData());
            synchronized (state) {
                try (RandomAccessFile raf = new RandomAccessFile(partFile, "rw")) {
                    raf.seek((long) chunk.getChunkId() * chunkSize);
                    if (chunk.getActualSize() > 0 && decrypted.length >= chunk.getActualSize()) {
                        raf.write(decrypted, 0, chunk.getActualSize());
                    }
                }
            }

            int doneChunks = chunk.getChunkId() + 1;
            state.setLastTransferredChunk(doneChunks);
            FileMessageCard card = transferCards.get(transferId);

            // Nếu người dùng đang chủ động lưu ra một tệp đích ngoài thư mục cache, hiển thị % tải về
            File userTarget = state.getFinalDestinationFile();
            boolean isUserSaving = userTarget != null && !userTarget.equals(cacheFile);
            if (card != null && isUserSaving) {
                int percent = (int) ((doneChunks * 100L) / metadata.getTotalChunks());
                card.updateTransferProgress(percent, "Đang tải về... " + percent + "%");
            }

            if (chunk.isLastChunk() || doneChunks >= metadata.getTotalChunks()) {
                if (partFile.exists()) {
                    Files.copy(partFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                if (isUserSaving && userTarget != null) {
                    Files.move(partFile.toPath(), userTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    if (verifyMd5Checksum(userTarget, metadata.getMd5Checksum())) {
                        if (card != null) {
                            card.markSavedSuccessfully(userTarget);
                        }
                    } else if (card != null) {
                        card.markInterrupted();
                    }
                } else if (partFile.exists() && !partFile.equals(cacheFile)) {
                    partFile.delete();
                }
            }
        } catch (IOException e) {
            FileMessageCard card = transferCards.get(transferId);
            if (card != null) {
                card.markInterrupted();
            }
        }
    }

    /**
     * Khi người gửi nhận được yêu cầu kéo các chunk còn thiếu từ người nhận.
     */
    public void onFileTransferAccepted(FileMetadata reqMeta) {
        if (reqMeta == null) {
            return;
        }
        TransferState state = activeTransfers.get(reqMeta.getTransferId());
        if (state == null) {
            return;
        }
        File source = findReadyLocalFile(state, reqMeta);
        if (source == null) {
            return;
        }

        new Thread(() -> {
            int chunkSize = reqMeta.getChunkSize() > 0 ? reqMeta.getChunkSize() : CHUNK_SIZE;
            int totalChunks = Math.max(1, (int) Math.ceil((double) source.length() / chunkSize));
            int startChunk = Math.max(0, reqMeta.getResumeFromChunk());

            try (RandomAccessFile raf = new RandomAccessFile(source, "r")) {
                raf.seek((long) startChunk * chunkSize);
                byte[] buffer = new byte[chunkSize];
                for (int chunkId = startChunk; chunkId < totalChunks; chunkId++) {
                    int bytesRead = raf.read(buffer);
                    if (bytesRead == -1) {
                        bytesRead = 0;
                    }
                    byte[] raw = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                    byte[] encrypted = AESUtil.encryptBytes(raw);
                    boolean isLast = (chunkId == totalChunks - 1);

                    FileChunkPacket chunkPacket = new FileChunkPacket(
                            reqMeta.getTransferId(),
                            chunkId,
                            bytesRead,
                            encrypted,
                            isLast
                    );
                    chatSocketController.sendRelayChunk(reqMeta.getSender(), reqMeta.getReceiver(), chunkPacket);
                }
            } catch (IOException ignored) {
            }
        }, "Sender-ChunkPush-" + reqMeta.getTransferId()).start();
    }

    public void onFileTransferRejected(FileMetadata metadata, String reason) {
        if (metadata == null) {
            return;
        }
        FileMessageCard card = transferCards.get(metadata.getTransferId());
        if (card != null) {
            card.markInterrupted();
        }
    }

    public void handleConnectionLostForActiveTransfers() {
        for (Map.Entry<String, TransferState> entry : activeTransfers.entrySet()) {
            TransferState state = entry.getValue();
            if (!state.isCompleted() && !state.isCancelled()) {
                state.setInterrupted(true);
                FileMessageCard card = transferCards.get(entry.getKey());
                if (card != null) {
                    card.markInterrupted();
                }
            }
        }
    }

    private boolean verifyMd5Checksum(File file, String expectedMd5) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (expectedMd5 == null || expectedMd5.trim().isEmpty()) {
            return true;
        }
        try {
            String actualMd5 = HashUtil.calculateFileMD5(file);
            return actualMd5.equalsIgnoreCase(expectedMd5.trim());
        } catch (IOException e) {
            return false;
        }
    }

    private void closeP2PServer(String transferId) {
        ServerSocket ss = p2pServers.remove(transferId);
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }
}
