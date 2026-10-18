package server.controller;

import common.model.FileMetadata;
import common.model.Message;
import common.model.User;
import common.protocol.Packet;
import common.protocol.PacketType;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Luồng xử lý riêng cho từng Client kết nối tới Server.
 */
public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ServerController serverController;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User currentUser;
    private volatile boolean connected = true;
    private final String clientIpAddress;

    public ClientHandler(Socket clientSocket, ServerController serverController) {
        this.clientSocket = clientSocket;
        this.serverController = serverController;
        this.clientIpAddress = clientSocket.getInetAddress().getHostAddress();
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(clientSocket.getInputStream());

            while (connected && !clientSocket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof Packet) {
                    handlePacket((Packet) obj);
                }
            }
        } catch (EOFException e) {
            // Client ngắt kết nối bình thường
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                serverController.log("Ngắt kết nối từ địa chỉ " + clientIpAddress + ": " + e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    private void handlePacket(Packet packet) {
        if (packet == null || packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {
            case LOGIN_REQ:
                handleLogin(packet);
                break;

            case REGISTER_REQ:
                handleRegister(packet);
                break;

            case LOGOUT:
                handleLogout();
                break;

            case CHAT_MESSAGE:
                if (packet.getPayload() instanceof Message) {
                    serverController.routeChatMessage((Message) packet.getPayload(), this);
                }
                break;

            case LOAD_HISTORY_REQ:
                handleLoadHistory(packet);
                break;

            case FILE_TRANSFER_REQ:
                if (packet.getPayload() instanceof FileMetadata) {
                    serverController.handleFileTransferRequest((FileMetadata) packet.getPayload(), this);
                }
                break;

            case FILE_TRANSFER_ACCEPT:
                if (packet.getPayload() instanceof FileMetadata) {
                    serverController.handleFileTransferAccept((FileMetadata) packet.getPayload(), this);
                }
                break;

            case FILE_TRANSFER_REJECT:
                if (packet.getPayload() instanceof FileMetadata) {
                    serverController.handleFileTransferReject((FileMetadata) packet.getPayload(), packet.getMessage());
                }
                break;

            case FILE_TRANSFER_COMPLETE:
                if (packet.getPayload() instanceof FileMetadata) {
                    serverController.handleFileTransferComplete((FileMetadata) packet.getPayload());
                }
                break;

            case FILE_CHUNK:
            case FILE_CHUNK_ACK:
            case TRANSFER_PAUSE:
            case TRANSFER_RESUME:
            case TRANSFER_CANCEL:
                serverController.relayAndCachePacket(packet);
                break;

            default:
                break;
        }
    }

    private void handleLogin(Packet packet) {
        if (!(packet.getPayload() instanceof User)) {
            sendPacket(new Packet(PacketType.LOGIN_RES, false, "Thông tin đăng nhập không hợp lệ.", null));
            return;
        }
        User reqUser = (User) packet.getPayload();
        String username = reqUser.getUsername();
        String password = reqUser.getPasswordHash();

        User authenticatedUser = serverController.getUserDAO().authenticate(username, password);
        if (authenticatedUser != null) {
            authenticatedUser.setIpAddress(clientIpAddress);
            authenticatedUser.setOnline(true);
            this.currentUser = authenticatedUser;

            Packet successRes = new Packet(PacketType.LOGIN_RES, true, "Đăng nhập thành công!", authenticatedUser);
            sendPacket(successRes);

            serverController.registerActiveClient(authenticatedUser, this);
        } else {
            Packet failRes = new Packet(PacketType.LOGIN_RES, false, "Sai tên đăng nhập hoặc mật khẩu!", null);
            sendPacket(failRes);
        }
    }

    private void handleRegister(Packet packet) {
        if (!(packet.getPayload() instanceof User)) {
            sendPacket(new Packet(PacketType.REGISTER_RES, false, "Thông tin đăng ký không hợp lệ.", null));
            return;
        }
        User reqUser = (User) packet.getPayload();
        String username = reqUser.getUsername();
        String password = reqUser.getPasswordHash();

        if (serverController.getUserDAO().isUsernameTaken(username)) {
            sendPacket(new Packet(PacketType.REGISTER_RES, false, "Tên đăng nhập '" + username + "' đã được sử dụng!", null));
            return;
        }

        boolean created = serverController.getUserDAO().registerUser(username, password);
        if (created) {
            serverController.log("Tài khoản mới được tạo: [" + username + "] (" + clientIpAddress + ")");
            sendPacket(new Packet(PacketType.REGISTER_RES, true, "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.", null));
            serverController.broadcastUserList();
        } else {
            sendPacket(new Packet(PacketType.REGISTER_RES, false, "Không thể tạo tài khoản. Vui lòng thử lại.", null));
        }
    }

    private void handleLoadHistory(Packet packet) {
        if (currentUser == null) {
            return;
        }
        String partner = packet.getReceiver();
        if (partner == null && packet.getPayload() instanceof String) {
            partner = (String) packet.getPayload();
        }
        if (partner == null || partner.isEmpty()) {
            return;
        }

        List<Message> history = serverController.getMessageDAO().getConversationHistory(currentUser.getUsername(), partner);
        Packet res = new Packet(PacketType.LOAD_HISTORY_RES, partner, currentUser.getUsername(), history);
        sendPacket(res);
    }

    private void handleLogout() {
        if (currentUser != null) {
            serverController.removeActiveClient(currentUser.getUsername(), this);
            currentUser = null;
        }
    }

    public synchronized void sendPacket(Packet packet) {
        if (!connected || out == null || clientSocket.isClosed()) {
            return;
        }
        try {
            out.reset();
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            closeConnection();
        }
    }

    public synchronized void closeConnection() {
        if (!connected) {
            return;
        }
        connected = false;
        if (currentUser != null) {
            serverController.removeActiveClient(currentUser.getUsername(), this);
        }
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
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
