package server.controller;

import common.model.FileMetadata;
import common.model.Message;
import common.model.User;
import common.protocol.FileChunkPacket;
import common.protocol.Packet;
import common.protocol.PacketType;
import common.security.AESUtil;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import server.model.DatabaseConnection;
import server.model.MessageDAO;
import server.model.UserDAO;

/**
 * Bộ điều khiển trung tâm của Server: quản lý kết nối đa luồng bằng ExecutorService,
 * danh sách người dùng trực tuyến bằng ConcurrentHashMap, định tuyến tin nhắn 1-1,
 * báo hiệu P2P và đồng bộ đệm tệp tin cho phép cả 2 phía tải tệp tức thời bất cứ lúc nào.
 */
public class ServerController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final File SERVER_CACHE_DIR = new File(".chatapp_server_cache");

    private ServerSocket serverSocket;
    private DiscoveryServer discoveryServer;
    private ExecutorService clientThreadPool;
    private volatile boolean running = false;

    private final ConcurrentHashMap<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FileMetadata> transferMetadataMap = new ConcurrentHashMap<>();
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    private final Consumer<String> logCallback;
    private final Runnable clientTableUpdateCallback;

    public ServerController(Consumer<String> logCallback, Runnable clientTableUpdateCallback) {
        this.logCallback = logCallback;
        this.clientTableUpdateCallback = clientTableUpdateCallback;
        DatabaseConnection.initDatabase();
        if (!SERVER_CACHE_DIR.exists()) {
            SERVER_CACHE_DIR.mkdirs();
        }
    }

    public synchronized void startServer(int port) throws IOException {
        if (running) {
            return;
        }
        DatabaseConnection.initDatabase();
        if (!SERVER_CACHE_DIR.exists()) {
            SERVER_CACHE_DIR.mkdirs();
        }
        serverSocket = new ServerSocket(port);
        clientThreadPool = Executors.newFixedThreadPool(60);
        running = true;

        discoveryServer = new DiscoveryServer(port, this::log);
        discoveryServer.start();

        log("Máy chủ đã khởi động trên cổng TCP " + port + ". Đang sẵn sàng tiếp nhận kết nối...");

        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    log("Kết nối mới từ địa chỉ: " + clientIp + ":" + clientSocket.getPort());
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientThreadPool.execute(handler);
                } catch (IOException e) {
                    if (running) {
                        log("Lỗi khi tiếp nhận kết nối: " + e.getMessage());
                    }
                }
            }
        }, "Server-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stopServer() {
        if (!running) {
            return;
        }
        running = false;
        log("Đang dừng máy chủ và đóng các kết nối hiện tại...");

        if (discoveryServer != null) {
            discoveryServer.stop();
        }

        for (ClientHandler handler : activeClients.values()) {
            handler.closeConnection();
        }
        activeClients.clear();

        if (clientThreadPool != null && !clientThreadPool.isShutdown()) {
            clientThreadPool.shutdownNow();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Lỗi khi đóng ServerSocket: " + e.getMessage());
            }
        }

        notifyTableUpdate();
        log("Máy chủ đã dừng hoàn toàn.");
    }

    public boolean isRunning() {
        return running;
    }

    public UserDAO getUserDAO() {
        return userDAO;
    }

    public MessageDAO getMessageDAO() {
        return messageDAO;
    }

    public Map<String, ClientHandler> getActiveClients() {
        return activeClients;
    }

    public void registerActiveClient(User user, ClientHandler handler) {
        ClientHandler oldHandler = activeClients.put(user.getUsername(), handler);
        if (oldHandler != null && oldHandler != handler) {
            oldHandler.closeConnection();
        }
        log("Người dùng [" + user.getUsername() + "] đã trực tuyến từ địa chỉ: " + user.getIpAddress());
        notifyTableUpdate();
        broadcastUserList();
    }

    public void removeActiveClient(String username, ClientHandler handler) {
        if (username != null) {
            boolean removed = activeClients.remove(username, handler);
            if (removed) {
                log("Người dùng [" + username + "] đã ngoại tuyến.");
                notifyTableUpdate();
                broadcastUserList();
            }
        }
    }

    public void broadcastUserList() {
        List<User> allUsers = userDAO.getAllUsers();
        for (User u : allUsers) {
            ClientHandler activeHandler = activeClients.get(u.getUsername());
            if (activeHandler != null && activeHandler.getCurrentUser() != null) {
                u.setOnline(true);
                u.setIpAddress(activeHandler.getCurrentUser().getIpAddress());
                u.setP2pPort(activeHandler.getCurrentUser().getP2pPort());
            } else {
                u.setOnline(false);
                u.setIpAddress("Ngoại tuyến");
            }
        }

        Packet listPacket = new Packet(PacketType.ONLINE_USERS_LIST, "SERVER", "ALL", allUsers);
        for (ClientHandler handler : activeClients.values()) {
            handler.sendPacket(listPacket);
        }
    }

    public void routeChatMessage(Message msg, ClientHandler senderHandler) {
        if (msg == null) {
            return;
        }
        msg.setTimestamp(LocalDateTime.now().format(TIME_FMT));
        messageDAO.saveMessage(msg);

        log("Tin nhắn từ [" + msg.getSender() + "] tới [" + msg.getReceiver() + "]");

        Packet chatPacket = new Packet(PacketType.CHAT_MESSAGE, msg.getSender(), msg.getReceiver(), msg);
        senderHandler.sendPacket(chatPacket);

        ClientHandler receiverHandler = activeClients.get(msg.getReceiver());
        if (receiverHandler != null && receiverHandler != senderHandler) {
            receiverHandler.sendPacket(chatPacket);
        }
    }

    /**
     * Khi Người gửi (A) bắt đầu tải tệp lên:
     * Lưu metadata, đính kèm IP của A và báo hiệu sang cho B để đồng bộ ngầm P2P ngay lập tức.
     */
    public void handleFileTransferRequest(FileMetadata metadata, ClientHandler senderHandler) {
        if (metadata == null) {
            return;
        }
        String senderIp = senderHandler.getClientIpAddress();
        metadata.setSenderIp(senderIp);
        transferMetadataMap.put(metadata.getTransferId(), metadata);
        messageDAO.saveOrUpdateFileTransfer(metadata, "UPLOADING");

        ClientHandler receiverHandler = activeClients.get(metadata.getReceiver());
        if (receiverHandler != null) {
            metadata.setReceiverIp(receiverHandler.getClientIpAddress());
            Packet forwardPacket = new Packet(PacketType.FILE_TRANSFER_REQ, metadata.getSender(), metadata.getReceiver(), metadata);
            receiverHandler.sendPacket(forwardPacket);
        }
        log("Đang đồng bộ tệp tin: [" + metadata.getSender() + "] -> [" + metadata.getReceiver() + "] | " + metadata.getFileName());
    }

    /**
     * Khi một Client yêu cầu kéo các khối dữ liệu tệp tin (từ chunk resumeFromChunk):
     * Nếu Server đã có bản đệm trong .chatapp_server_cache thì truyền ngay các khối 64KB cho Client đó;
     * đồng thời chuyển tiếp tới đối phương nếu đang trực tuyến.
     */
    public void handleFileTransferAccept(FileMetadata metadata, ClientHandler requesterHandler) {
        if (metadata == null) {
            return;
        }
        String detectedIp = requesterHandler.getClientIpAddress();
        if (metadata.getReceiverIp() == null || metadata.getReceiverIp().isEmpty()
                || "0.0.0.0".equals(metadata.getReceiverIp())
                || "127.0.0.1".equals(metadata.getReceiverIp())) {
            metadata.setReceiverIp(detectedIp);
        }

        // Kiểm tra xem tệp đã có trong bộ đệm của Server chưa
        File cachedServerFile = getServerCacheFile(metadata.getTransferId(), metadata.getFileName());
        if (cachedServerFile.exists() && cachedServerFile.length() == metadata.getFileSize()) {
            streamCachedFileToClient(metadata, cachedServerFile, requesterHandler);
            return;
        }

        ClientHandler senderHandler = activeClients.get(metadata.getSender());
        if (senderHandler != null && senderHandler != requesterHandler) {
            Packet acceptPacket = new Packet(PacketType.FILE_TRANSFER_ACCEPT, metadata.getReceiver(), metadata.getSender(), metadata);
            senderHandler.sendPacket(acceptPacket);
        }
    }

    private void streamCachedFileToClient(FileMetadata metadata, File cachedFile, ClientHandler targetHandler) {
        clientThreadPool.execute(() -> {
            int chunkSize = metadata.getChunkSize() > 0 ? metadata.getChunkSize() : FileMetadata.DEFAULT_CHUNK_SIZE;
            int totalChunks = Math.max(1, (int) Math.ceil((double) metadata.getFileSize() / chunkSize));
            int startChunk = Math.max(0, metadata.getResumeFromChunk());

            try (RandomAccessFile raf = new RandomAccessFile(cachedFile, "r")) {
                raf.seek((long) startChunk * chunkSize);
                byte[] buffer = new byte[chunkSize];
                for (int chunkId = startChunk; chunkId < totalChunks; chunkId++) {
                    int bytesRead = raf.read(buffer);
                    if (bytesRead == -1) {
                        bytesRead = 0;
                    }
                    byte[] rawChunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                    byte[] encryptedChunk = AESUtil.encryptBytes(rawChunk);
                    boolean isLast = (chunkId == totalChunks - 1);

                    FileChunkPacket chunkPacket = new FileChunkPacket(
                            metadata.getTransferId(),
                            chunkId,
                            bytesRead,
                            encryptedChunk,
                            isLast
                    );
                    Packet relayPkt = new Packet(PacketType.FILE_CHUNK, metadata.getSender(), metadata.getReceiver(), chunkPacket);
                    targetHandler.sendPacket(relayPkt);
                }
            } catch (IOException e) {
                log("Lỗi truyền tệp từ bộ đệm máy chủ: " + e.getMessage());
            }
        });
    }

    public void handleFileTransferReject(FileMetadata metadata, String reason) {
        if (metadata == null) {
            return;
        }
        messageDAO.saveOrUpdateFileTransfer(metadata, "CANCELLED");
        ClientHandler senderHandler = activeClients.get(metadata.getSender());
        if (senderHandler != null) {
            Packet rejectPacket = new Packet(PacketType.FILE_TRANSFER_REJECT, false, reason, metadata);
            senderHandler.sendPacket(rejectPacket);
        }
        ClientHandler receiverHandler = activeClients.get(metadata.getReceiver());
        if (receiverHandler != null && receiverHandler != senderHandler) {
            Packet rejectPacket = new Packet(PacketType.FILE_TRANSFER_REJECT, false, reason, metadata);
            receiverHandler.sendPacket(rejectPacket);
        }
    }

    /**
     * Khi người gửi hoàn tất đẩy tệp tin 100%:
     * Lưu vào bảng file_transfers và messages trong SQLite, đồng thời thông báo cho cả A và B
     * hiển thị dòng tệp tin trên khung chat.
     */
    public synchronized void handleFileTransferComplete(FileMetadata metadata) {
        if (metadata == null) {
            return;
        }
        transferMetadataMap.put(metadata.getTransferId(), metadata);
        messageDAO.saveOrUpdateFileTransfer(metadata, "COMPLETED");

        String structuredLog = "FILE_CARD|" + metadata.getTransferId() + "|"
                + metadata.getFileName() + "|"
                + metadata.getFileSize() + "|"
                + metadata.getMd5Checksum();

        String encryptedLog = AESUtil.encryptText(structuredLog);
        String now = LocalDateTime.now().format(TIME_FMT);

        Message fileLogMsg = new Message(metadata.getSender(), metadata.getReceiver(), encryptedLog, now, true);
        fileLogMsg.setFileMetadata(metadata);
        messageDAO.saveMessage(fileLogMsg);

        log("Đã tải lên tệp [" + metadata.getFileName() + "] từ [" + metadata.getSender()
                + "] tới [" + metadata.getReceiver() + "].");

        Packet completePacket = new Packet(PacketType.FILE_TRANSFER_COMPLETE, metadata.getSender(), metadata.getReceiver(), fileLogMsg);

        ClientHandler senderHandler = activeClients.get(metadata.getSender());
        if (senderHandler != null) {
            senderHandler.sendPacket(completePacket);
        }
        ClientHandler receiverHandler = activeClients.get(metadata.getReceiver());
        if (receiverHandler != null && receiverHandler != senderHandler) {
            receiverHandler.sendPacket(completePacket);
        }
    }

    /**
     * Lưu khối dữ liệu tệp vào bộ đệm của Server và chuyển tiếp ngay cho người nhận nếu đang trực tuyến.
     */
    public void relayAndCachePacket(Packet packet) {
        if (packet == null) {
            return;
        }
        if (packet.getType() == PacketType.FILE_CHUNK && packet.getPayload() instanceof FileChunkPacket) {
            FileChunkPacket chunk = (FileChunkPacket) packet.getPayload();
            FileMetadata meta = transferMetadataMap.get(chunk.getTransferId());
            if (meta != null) {
                saveChunkToServerCache(meta, chunk);
            }
        }

        if (packet.getReceiver() != null) {
            ClientHandler target = activeClients.get(packet.getReceiver());
            if (target != null) {
                target.sendPacket(packet);
            }
        }
    }

    private void saveChunkToServerCache(FileMetadata meta, FileChunkPacket chunk) {
        File cacheFile = getServerCacheFile(meta.getTransferId(), meta.getFileName());
        int chunkSize = meta.getChunkSize() > 0 ? meta.getChunkSize() : FileMetadata.DEFAULT_CHUNK_SIZE;
        try {
            byte[] decrypted = AESUtil.decryptBytes(chunk.getEncryptedData());
            synchronized (this) {
                try (RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw")) {
                    raf.seek((long) chunk.getChunkId() * chunkSize);
                    if (chunk.getActualSize() > 0 && decrypted.length >= chunk.getActualSize()) {
                        raf.write(decrypted, 0, chunk.getActualSize());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private File getServerCacheFile(String transferId, String fileName) {
        String safeName = fileName != null ? fileName.replaceAll("[\\\\/:*?\"<>|]", "_") : "file";
        return new File(SERVER_CACHE_DIR, transferId + "_" + safeName);
    }

    public List<User> getActiveUserSnapshots() {
        List<User> list = new ArrayList<>();
        for (ClientHandler handler : activeClients.values()) {
            if (handler.getCurrentUser() != null) {
                list.add(handler.getCurrentUser());
            }
        }
        return list;
    }

    public void log(String text) {
        String timestamped = "[" + LocalDateTime.now().format(TIME_FMT) + "] " + text;
        if (logCallback != null) {
            logCallback.accept(timestamped);
        } else {
            System.out.println(timestamped);
        }
    }

    private void notifyTableUpdate() {
        if (clientTableUpdateCallback != null) {
            clientTableUpdateCallback.run();
        }
    }
}
