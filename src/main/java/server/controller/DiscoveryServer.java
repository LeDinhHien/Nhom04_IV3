package server.controller;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Máy chủ UDP lắng nghe bản tin tự động dò tìm Server (Auto-Discovery) trên cổng UDP 8889.
 * Khi nhận được "CHATAPP_DISCOVER_REQ", phản hồi ngay "CHATAPP_DISCOVER_RES:<TCP_PORT>".
 */
public class DiscoveryServer {

    public static final int DISCOVERY_UDP_PORT = 8889;
    public static final String DISCOVER_REQ_PREFIX = "CHATAPP_DISCOVER_REQ";
    public static final String DISCOVER_RES_PREFIX = "CHATAPP_DISCOVER_RES:";

    private DatagramSocket udpSocket;
    private volatile boolean running = false;
    private final int tcpPort;
    private final Consumer<String> logCallback;

    public DiscoveryServer(int tcpPort, Consumer<String> logCallback) {
        this.tcpPort = tcpPort;
        this.logCallback = logCallback;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            udpSocket = new DatagramSocket(DISCOVERY_UDP_PORT, InetAddress.getByName("0.0.0.0"));
            udpSocket.setBroadcast(true);
            running = true;

            Thread udpThread = new Thread(this::listenLoop, "UDP-Discovery-Server");
            udpThread.setDaemon(true);
            udpThread.start();
            log("Dịch vụ tự động tìm kiếm máy chủ (UDP Port " + DISCOVERY_UDP_PORT + ") đã bật.");
        } catch (IOException e) {
            log("Không thể mở cổng UDP Discovery " + DISCOVERY_UDP_PORT + ": " + e.getMessage());
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[512];
        while (running && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (received.startsWith(DISCOVER_REQ_PREFIX)) {
                    String responseText = DISCOVER_RES_PREFIX + tcpPort;
                    byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            packet.getAddress(),
                            packet.getPort()
                    );
                    udpSocket.send(responsePacket);
                }
            } catch (IOException e) {
                if (running) {
                    log("Lỗi nhận gói tin UDP Discovery: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        running = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }

    private void log(String msg) {
        if (logCallback != null) {
            logCallback.accept(msg);
        }
    }
}
