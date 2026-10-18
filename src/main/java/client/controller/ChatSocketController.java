package client.controller;

import client.model.ClientSession;
import common.model.FileMetadata;
import common.model.Message;
import common.model.User;
import common.protocol.FileChunkPacket;
import common.protocol.Packet;
import common.protocol.PacketType;
import common.security.AESUtil;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Kênh TCP số 1: Quản lý kết nối Socket độc lập tới Server để xác thực,
 * đồng bộ danh sách liên hệ, nhắn tin văn bản AES-128, báo hiệu truyền tệp
 * và hỗ trợ chuyển tiếp luồng dự phòng (Relay Stream).
 */
public class ChatSocketController {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected = false;
    private final ClientSession session;

    private BiConsumer<Boolean, String> loginResultCallback;
    private BiConsumer<Boolean, String> registerResultCallback;
    private Consumer<List<User>> userListCallback;
    private Consumer<Message> incomingMessageCallback;
    private BiConsumer<String, List<Message>> historyLoadedCallback;
    private Consumer<FileMetadata> fileRequestCallback;
    private Consumer<FileMetadata> fileAcceptCallback;
    private BiConsumer<FileMetadata, String> fileRejectCallback;
    private Consumer<Message> fileCompleteCallback;
    private Consumer<Packet> relayPacketCallback;
    private Consumer<String> connectionLostCallback;

    public ChatSocketController(ClientSession session) {
        this.session = session;
    }

    public ClientSession getSession() {
        return session;
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public synchronized void connect(String serverIp, int serverPort) throws IOException {
        connect(serverIp, serverPort, 2000);
    }

    public synchronized void connect(String serverIp, int serverPort, int timeoutMs) throws IOException {
        if (isConnected()
                && serverIp.equals(session.getServerIp())
                && serverPort == session.getServerPort()) {
            return;
        }
        disconnect();

        Socket newSocket = new Socket();
        newSocket.connect(new InetSocketAddress(serverIp, serverPort), timeoutMs);
        this.socket = newSocket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
        this.connected = true;

        String localIp = socket.getLocalAddress().getHostAddress();
        session.setLocalIpAddress(localIp);
        session.setServerIp(serverIp);
        session.setServerPort(serverPort);

        Thread listenerThread = new Thread(this::listenLoop, "Client-ChatSocket-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        try {
            while (connected && socket != null && !socket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof Packet) {
                    handlePacket((Packet) obj);
                }
            }
        } catch (EOFException e) {
            if (connected) {
                notifyConnectionLost("Máy chủ đã đóng kết nối.");
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                notifyConnectionLost("Mất kết nối tới máy chủ: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePacket(Packet packet) {
        if (packet == null || packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {
            case LOGIN_RES:
                if (packet.isSuccess() && packet.getPayload() instanceof User) {
                    User loggedInUser = (User) packet.getPayload();
                    session.setCurrentUser(loggedInUser);
                }
                if (loginResultCallback != null) {
                    loginResultCallback.accept(packet.isSuccess(), packet.getMessage());
                }
                break;

            case REGISTER_RES:
                if (registerResultCallback != null) {
                    registerResultCallback.accept(packet.isSuccess(), packet.getMessage());
                }
                break;

            case ONLINE_USERS_LIST:
                if (packet.getPayload() instanceof List<?>) {
                    List<User> users = (List<User>) packet.getPayload();
                    session.setUserList(users);
                    if (userListCallback != null) {
                        userListCallback.accept(users);
                    }
                }
                break;

            case CHAT_MESSAGE:
                if (packet.getPayload() instanceof Message) {
                    Message msg = (Message) packet.getPayload();
                    if (incomingMessageCallback != null) {
                        incomingMessageCallback.accept(msg);
                    }
                }
                break;

            case LOAD_HISTORY_RES:
                if (packet.getPayload() instanceof List<?>) {
                    List<Message> history = (List<Message>) packet.getPayload();
                    String partner = packet.getSender();
                    if (historyLoadedCallback != null) {
                        historyLoadedCallback.accept(partner, history);
                    }
                }
                break;

            case FILE_TRANSFER_REQ:
                if (packet.getPayload() instanceof FileMetadata) {
                    FileMetadata metadata = (FileMetadata) packet.getPayload();
                    if (fileRequestCallback != null) {
                        fileRequestCallback.accept(metadata);
                    }
                }
                break;

            case FILE_TRANSFER_ACCEPT:
                if (packet.getPayload() instanceof FileMetadata) {
                    FileMetadata metadata = (FileMetadata) packet.getPayload();
                    if (fileAcceptCallback != null) {
                        fileAcceptCallback.accept(metadata);
                    }
                }
                break;

            case FILE_TRANSFER_REJECT:
                if (packet.getPayload() instanceof FileMetadata) {
                    FileMetadata metadata = (FileMetadata) packet.getPayload();
                    if (fileRejectCallback != null) {
                        fileRejectCallback.accept(metadata, packet.getMessage());
                    }
                }
                break;

            case FILE_TRANSFER_COMPLETE:
                if (packet.getPayload() instanceof Message) {
                    Message fileLog = (Message) packet.getPayload();
                    if (fileCompleteCallback != null) {
                        fileCompleteCallback.accept(fileLog);
                    }
                }
                break;

            case FILE_CHUNK:
            case FILE_CHUNK_ACK:
            case TRANSFER_PAUSE:
            case TRANSFER_RESUME:
            case TRANSFER_CANCEL:
                if (relayPacketCallback != null) {
                    relayPacketCallback.accept(packet);
                }
                break;

            default:
                break;
        }
    }

    public void sendLoginRequest(String username, String password) {
        User user = new User(username, password);
        user.setIpAddress(session.getLocalIpAddress());
        Packet packet = new Packet(PacketType.LOGIN_REQ, username, "SERVER", user);
        sendPacket(packet);
    }

    public void sendRegisterRequest(String username, String password) {
        User user = new User(username, password);
        Packet packet = new Packet(PacketType.REGISTER_REQ, username, "SERVER", user);
        sendPacket(packet);
    }

    public void sendChatMessage(String receiver, String plainText) {
        if (session.getCurrentUser() == null || plainText == null || plainText.trim().isEmpty()) {
            return;
        }
        String sender = session.getCurrentUser().getUsername();
        String encrypted = AESUtil.encryptText(plainText);
        Message msg = new Message(sender, receiver, encrypted, null, false);
        Packet packet = new Packet(PacketType.CHAT_MESSAGE, sender, receiver, msg);
        sendPacket(packet);
    }

    public void requestChatHistory(String partnerUsername) {
        if (session.getCurrentUser() == null || partnerUsername == null) {
            return;
        }
        Packet packet = new Packet(
                PacketType.LOAD_HISTORY_REQ,
                session.getCurrentUser().getUsername(),
                partnerUsername,
                partnerUsername
        );
        sendPacket(packet);
    }

    public void sendFileTransferRequest(FileMetadata metadata) {
        if (metadata == null) {
            return;
        }
        Packet packet = new Packet(PacketType.FILE_TRANSFER_REQ, metadata.getSender(), metadata.getReceiver(), metadata);
        sendPacket(packet);
    }

    public void sendFileTransferAccept(FileMetadata metadata) {
        if (metadata == null) {
            return;
        }
        Packet packet = new Packet(PacketType.FILE_TRANSFER_ACCEPT, metadata.getReceiver(), metadata.getSender(), metadata);
        sendPacket(packet);
    }

    public void sendFileTransferReject(FileMetadata metadata, String reason) {
        if (metadata == null) {
            return;
        }
        String myName = session.getCurrentUser() != null ? session.getCurrentUser().getUsername() : metadata.getReceiver();
        String other = myName.equals(metadata.getSender()) ? metadata.getReceiver() : metadata.getSender();
        Packet packet = new Packet(PacketType.FILE_TRANSFER_REJECT, false, reason, metadata);
        packet.setSender(myName);
        packet.setReceiver(other);
        sendPacket(packet);
    }

    public void sendFileTransferComplete(FileMetadata metadata) {
        if (metadata == null) {
            return;
        }
        Packet packet = new Packet(PacketType.FILE_TRANSFER_COMPLETE, metadata.getSender(), metadata.getReceiver(), metadata);
        sendPacket(packet);
    }

    public void sendRelayChunk(String sender, String receiver, FileChunkPacket chunkPacket) {
        Packet packet = new Packet(PacketType.FILE_CHUNK, sender, receiver, chunkPacket);
        sendPacket(packet);
    }

    public void sendRelayControl(PacketType controlType, String sender, String receiver, Object payload) {
        Packet packet = new Packet(controlType, sender, receiver, payload);
        sendPacket(packet);
    }

    public synchronized void sendPacket(Packet packet) {
        if (!connected || out == null || socket == null || socket.isClosed()) {
            return;
        }
        try {
            out.reset();
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            notifyConnectionLost("Không thể gửi dữ liệu tới máy chủ: " + e.getMessage());
        }
    }

    public synchronized void disconnect() {
        if (connected && out != null) {
            try {
                out.writeObject(new Packet(PacketType.LOGOUT,
                        session.getCurrentUser() != null ? session.getCurrentUser().getUsername() : "",
                        "SERVER", null));
                out.flush();
            } catch (IOException ignored) {
            }
        }
        connected = false;
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void notifyConnectionLost(String message) {
        connected = false;
        if (connectionLostCallback != null) {
            connectionLostCallback.accept(message);
        }
    }

    public void setLoginResultCallback(BiConsumer<Boolean, String> loginResultCallback) {
        this.loginResultCallback = loginResultCallback;
    }

    public void setRegisterResultCallback(BiConsumer<Boolean, String> registerResultCallback) {
        this.registerResultCallback = registerResultCallback;
    }

    public void setUserListCallback(Consumer<List<User>> userListCallback) {
        this.userListCallback = userListCallback;
    }

    public void setIncomingMessageCallback(Consumer<Message> incomingMessageCallback) {
        this.incomingMessageCallback = incomingMessageCallback;
    }

    public void setHistoryLoadedCallback(BiConsumer<String, List<Message>> historyLoadedCallback) {
        this.historyLoadedCallback = historyLoadedCallback;
    }

    public void setFileRequestCallback(Consumer<FileMetadata> fileRequestCallback) {
        this.fileRequestCallback = fileRequestCallback;
    }

    public void setFileAcceptCallback(Consumer<FileMetadata> fileAcceptCallback) {
        this.fileAcceptCallback = fileAcceptCallback;
    }

    public void setFileRejectCallback(BiConsumer<FileMetadata, String> fileRejectCallback) {
        this.fileRejectCallback = fileRejectCallback;
    }

    public void setFileCompleteCallback(Consumer<Message> fileCompleteCallback) {
        this.fileCompleteCallback = fileCompleteCallback;
    }

    public void setRelayPacketCallback(Consumer<Packet> relayPacketCallback) {
        this.relayPacketCallback = relayPacketCallback;
    }

    public void setConnectionLostCallback(Consumer<String> connectionLostCallback) {
        this.connectionLostCallback = connectionLostCallback;
    }
}
