package client.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import server.controller.DiscoveryServer;

/**
 * Tự động tìm kiếm và kết nối tới Server mà người dùng không cần nhập IP hay Port.
 * Thứ tự ưu tiên:
 * 1. Kiểm tra file client_config.properties (timeout 500ms).
 * 2. Thử kết nối 127.0.0.1:8888 (timeout 200ms).
 * 3. Quét tất cả NetworkInterface đang hoạt động (UDP Broadcast + quét nhanh dải /24 bằng ThreadPool).
 */
public final class ServerDiscoveryClient {

    private static final String CONFIG_FILE = "client_config.properties";
    private static final String KEY_SERVER_IP = "server.ip";
    private static final String KEY_SERVER_PORT = "server.port";
    public static final int DEFAULT_TCP_PORT = 8888;

    private ServerDiscoveryClient() {
    }

    /**
     * Tự động tìm kiếm máy chủ và thiết lập kết nối TCP cho ChatSocketController.
     */
    public static void connectAutomatically(ChatSocketController chatController) throws IOException {
        if (chatController.isConnected()) {
            return;
        }

        // Bước 1: Kiểm tra cấu hình đã lưu trong client_config.properties (timeout 500ms)
        Properties props = loadConfig();
        String savedIp = props.getProperty(KEY_SERVER_IP);
        int savedPort = parsePort(props.getProperty(KEY_SERVER_PORT), DEFAULT_TCP_PORT);

        if (savedIp != null && !savedIp.trim().isEmpty()) {
            try {
                chatController.connect(savedIp.trim(), savedPort, 500);
                return;
            } catch (IOException ignored) {
                // Nếu IP cũ không phản hồi thì chuyển sang bước tiếp theo
            }
        }

        // Bước 2: Thử kết nối nhanh 127.0.0.1:8888 (timeout 200ms)
        try {
            chatController.connect("127.0.0.1", DEFAULT_TCP_PORT, 200);
            saveServerConfig("127.0.0.1", DEFAULT_TCP_PORT);
            return;
        } catch (IOException ignored) {
        }

        // Bước 3: Tự động dò tìm qua UDP Broadcast & quét song song dải /24 của các card mạng
        DiscoveredEndpoint endpoint = discoverViaUdpAndSubnetScan(1600);
        if (endpoint != null) {
            chatController.connect(endpoint.ip, endpoint.port, 1500);
            saveServerConfig(endpoint.ip, endpoint.port);
            return;
        }

        throw new IOException("Không tìm thấy máy chủ đang hoạt động trên mạng. Hãy đảm bảo máy chủ đã được khởi động.");
    }

    /**
     * Lưu địa chỉ IP và cổng máy chủ vào file client_config.properties.
     */
    public static synchronized void saveServerConfig(String ip, int port) {
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY_SERVER_IP, ip.trim());
        props.setProperty(KEY_SERVER_PORT, String.valueOf(port));
        try (FileOutputStream fos = new FileOutputStream(new File(CONFIG_FILE))) {
            props.store(fos, "ChatApp Server Auto-Discovery Configuration");
        } catch (IOException ignored) {
        }
    }

    /**
     * Đọc địa chỉ IP đã lưu trong client_config.properties (nếu có).
     */
    public static String getSavedServerIp() {
        Properties props = loadConfig();
        return props.getProperty(KEY_SERVER_IP, "");
    }

    public static int getSavedServerPort() {
        Properties props = loadConfig();
        return parsePort(props.getProperty(KEY_SERVER_PORT), DEFAULT_TCP_PORT);
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists() && file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    private static int parsePort(String portStr, int fallback) {
        if (portStr == null || portStr.trim().isEmpty()) {
            return fallback;
        }
        try {
            int p = Integer.parseInt(portStr.trim());
            return (p >= 1 && p <= 65535) ? p : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Gửi gói UDP Broadcast trên toàn bộ các card mạng và quét song song dải /24 bằng ThreadPool.
     */
    private static DiscoveredEndpoint discoverViaUdpAndSubnetScan(int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs);

            byte[] reqBytes = DiscoveryServer.DISCOVER_REQ_PREFIX.getBytes(StandardCharsets.UTF_8);

            // 1. Gửi tới 255.255.255.255:8889
            try {
                DatagramPacket globalBroadcast = new DatagramPacket(
                        reqBytes,
                        reqBytes.length,
                        InetAddress.getByName("255.255.255.255"),
                        DiscoveryServer.DISCOVERY_UDP_PORT
                );
                socket.send(globalBroadcast);
            } catch (IOException ignored) {
            }

            // 2. Duyệt tất cả các card mạng đang bật
            List<String> subnetPrefixes = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr instanceof Inet4Address) {
                        InetAddress broadcast = ia.getBroadcast();
                        if (broadcast != null) {
                            try {
                                DatagramPacket subnetBroadcast = new DatagramPacket(
                                        reqBytes,
                                        reqBytes.length,
                                        broadcast,
                                        DiscoveryServer.DISCOVERY_UDP_PORT
                                );
                                socket.send(subnetBroadcast);
                            } catch (IOException ignored) {
                            }
                        }

                        String hostIp = addr.getHostAddress();
                        int lastDot = hostIp.lastIndexOf('.');
                        if (lastDot > 0) {
                            String prefix = hostIp.substring(0, lastDot + 1);
                            if (!subnetPrefixes.contains(prefix)) {
                                subnetPrefixes.add(prefix);
                            }
                        }
                    }
                }
            }

            // 3. Quét nhanh song song dải /24 của các card mạng bằng ThreadPool
            AtomicBoolean found = new AtomicBoolean(false);
            ExecutorService scanPool = Executors.newFixedThreadPool(32);
            for (String prefix : subnetPrefixes) {
                for (int host = 1; host <= 254; host++) {
                    final String targetIp = prefix + host;
                    scanPool.execute(() -> {
                        if (found.get() || socket.isClosed()) {
                            return;
                        }
                        try {
                            DatagramPacket probe = new DatagramPacket(
                                    reqBytes,
                                    reqBytes.length,
                                    InetAddress.getByName(targetIp),
                                    DiscoveryServer.DISCOVERY_UDP_PORT
                            );
                            socket.send(probe);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
            scanPool.shutdown();

            // 4. Lắng nghe phản hồi đầu tiên từ DiscoveryServer
            byte[] recvBuf = new byte[512];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(recvPacket);
            found.set(true);
            scanPool.shutdownNow();

            String reply = new String(recvPacket.getData(), 0, recvPacket.getLength(), StandardCharsets.UTF_8).trim();
            if (reply.startsWith(DiscoveryServer.DISCOVER_RES_PREFIX)) {
                String portPart = reply.substring(DiscoveryServer.DISCOVER_RES_PREFIX.length()).trim();
                int discoveredPort = parsePort(portPart, DEFAULT_TCP_PORT);
                String discoveredIp = recvPacket.getAddress().getHostAddress();
                return new DiscoveredEndpoint(discoveredIp, discoveredPort);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static final class DiscoveredEndpoint {
        final String ip;
        final int port;

        DiscoveredEndpoint(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
