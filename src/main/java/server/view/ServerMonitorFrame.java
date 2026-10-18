package server.view;

import client.view.DarkThemeUtil;
import common.model.User;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import server.controller.ServerController;

/**
 * Giao diện giám sát máy chủ nền tối (ChatApp Server Monitor).
 */
public class ServerMonitorFrame extends JFrame {

    private final JTextField txtPort;
    private final JButton btnStart;
    private final JButton btnStop;
    private final JLabel lblStatus;

    private final JComboBox<String> cboNetworkIps;
    private final DefaultTableModel clientTableModel;
    private final JTable tblClients;
    private final JTextArea txtServerLog;

    private final ServerController serverController;

    public ServerMonitorFrame() {
        setTitle("ChatApp Server Monitor");
        setSize(940, 650);
        setMinimumSize(new Dimension(820, 540));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(DarkThemeUtil.BG_PRIMARY);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        setContentPane(root);

        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setOpaque(false);

        JPanel controlCard = new JPanel(new BorderLayout(12, 8));
        DarkThemeUtil.applyCardStyle(controlCard, DarkThemeUtil.BG_SECONDARY, 12);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        JLabel lblPort = new JLabel("Cổng lắng nghe:");
        lblPort.setFont(DarkThemeUtil.FONT_BOLD);
        lblPort.setForeground(DarkThemeUtil.TEXT_PRIMARY);

        txtPort = new JTextField("8888", 6);
        DarkThemeUtil.styleTextField(txtPort, "8888");

        btnStart = DarkThemeUtil.createStyledButton("Khởi động", DarkThemeUtil.ACCENT_SUCCESS, DarkThemeUtil.TEXT_PRIMARY);
        btnStop = DarkThemeUtil.createStyledButton("Dừng", DarkThemeUtil.ACCENT_DANGER, DarkThemeUtil.TEXT_PRIMARY);
        btnStop.setEnabled(false);

        leftControls.add(lblPort);
        leftControls.add(txtPort);
        leftControls.add(btnStart);
        leftControls.add(btnStop);

        lblStatus = new JLabel("Trạng thái: Đang dừng");
        lblStatus.setFont(DarkThemeUtil.FONT_BOLD);
        lblStatus.setForeground(DarkThemeUtil.TEXT_MUTED);

        controlCard.add(leftControls, BorderLayout.WEST);
        controlCard.add(lblStatus, BorderLayout.EAST);

        JPanel ipCard = new JPanel(new BorderLayout(10, 6));
        DarkThemeUtil.applyCardStyle(ipCard, DarkThemeUtil.BG_SECONDARY, 12);

        JLabel lblIpTitle = new JLabel("Địa chỉ IP Máy chủ:");
        lblIpTitle.setFont(DarkThemeUtil.FONT_BOLD);
        lblIpTitle.setForeground(DarkThemeUtil.TEXT_PRIMARY);

        cboNetworkIps = new JComboBox<>();
        cboNetworkIps.setFont(DarkThemeUtil.FONT_MONO);
        cboNetworkIps.setBackground(DarkThemeUtil.BG_INPUT);
        cboNetworkIps.setForeground(DarkThemeUtil.TEXT_PRIMARY);
        refreshIpList();

        JPanel ipActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ipActionPanel.setOpaque(false);

        JButton btnCopyIp = DarkThemeUtil.createStyledButton("Sao chép IP", DarkThemeUtil.ACCENT_PRIMARY, DarkThemeUtil.TEXT_PRIMARY);
        JButton btnRefreshIp = DarkThemeUtil.createStyledButton("Làm mới", DarkThemeUtil.BG_INPUT, DarkThemeUtil.TEXT_PRIMARY);

        btnCopyIp.addActionListener(e -> copySelectedIp());
        btnRefreshIp.addActionListener(e -> refreshIpList());

        ipActionPanel.add(btnCopyIp);
        ipActionPanel.add(btnRefreshIp);

        ipCard.add(lblIpTitle, BorderLayout.WEST);
        ipCard.add(cboNetworkIps, BorderLayout.CENTER);
        ipCard.add(ipActionPanel, BorderLayout.EAST);

        topContainer.add(controlCard, BorderLayout.NORTH);
        topContainer.add(ipCard, BorderLayout.CENTER);
        root.add(topContainer, BorderLayout.NORTH);

        String[] columns = {"Tên người dùng", "Địa chỉ IP", "Trạng thái"};
        clientTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblClients = new JTable(clientTableModel);
        tblClients.setRowHeight(30);
        tblClients.setFont(DarkThemeUtil.FONT_REGULAR);
        tblClients.getTableHeader().setFont(DarkThemeUtil.FONT_BOLD);
        tblClients.setShowHorizontalLines(true);

        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        DarkThemeUtil.applyCardStyle(tableCard, DarkThemeUtil.BG_SECONDARY, 12);

        JLabel lblTableHeader = new JLabel("Danh sách Người dùng Trực tuyến");
        lblTableHeader.setFont(DarkThemeUtil.FONT_SUBTITLE);
        lblTableHeader.setForeground(DarkThemeUtil.TEXT_PRIMARY);

        JScrollPane tableScroll = new JScrollPane(tblClients);
        tableScroll.setBorder(BorderFactory.createLineBorder(DarkThemeUtil.BORDER_SUBTLE));

        tableCard.add(lblTableHeader, BorderLayout.NORTH);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        JPanel logCard = new JPanel(new BorderLayout(0, 8));
        DarkThemeUtil.applyCardStyle(logCard, DarkThemeUtil.BG_SECONDARY, 12);

        JPanel logHeaderPanel = new JPanel(new BorderLayout());
        logHeaderPanel.setOpaque(false);

        JLabel lblLogHeader = new JLabel("Nhật ký hệ thống");
        lblLogHeader.setFont(DarkThemeUtil.FONT_SUBTITLE);
        lblLogHeader.setForeground(DarkThemeUtil.TEXT_PRIMARY);

        JButton btnClearLog = DarkThemeUtil.createStyledButton("Xóa nhật ký", DarkThemeUtil.BG_INPUT, DarkThemeUtil.TEXT_MUTED);
        btnClearLog.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        logHeaderPanel.add(lblLogHeader, BorderLayout.WEST);
        logHeaderPanel.add(btnClearLog, BorderLayout.EAST);

        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setFont(DarkThemeUtil.FONT_MONO);
        txtServerLog.setBackground(DarkThemeUtil.BG_PRIMARY);
        txtServerLog.setForeground(DarkThemeUtil.TEXT_PRIMARY);
        txtServerLog.setLineWrap(true);
        txtServerLog.setWrapStyleWord(true);
        txtServerLog.setBorder(new EmptyBorder(8, 10, 8, 10));

        btnClearLog.addActionListener(e -> txtServerLog.setText(""));

        JScrollPane logScroll = new JScrollPane(txtServerLog);
        logScroll.setBorder(BorderFactory.createLineBorder(DarkThemeUtil.BORDER_SUBTLE));

        logCard.add(logHeaderPanel, BorderLayout.NORTH);
        logCard.add(logScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, logCard);
        splitPane.setDividerLocation(210);
        splitPane.setResizeWeight(0.38);
        splitPane.setBorder(null);
        splitPane.setBackground(DarkThemeUtil.BG_PRIMARY);
        root.add(splitPane, BorderLayout.CENTER);

        serverController = new ServerController(this::appendLog, this::refreshClientTable);

        btnStart.addActionListener(e -> onStartServer());
        btnStop.addActionListener(e -> onStopServer());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (serverController.isRunning()) {
                    int choice = JOptionPane.showConfirmDialog(
                            ServerMonitorFrame.this,
                            "Máy chủ đang hoạt động. Bạn có muốn dừng máy chủ và thoát?",
                            "Xác nhận thoát",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                    serverController.stopServer();
                }
                dispose();
                System.exit(0);
            }
        });

        appendLog("Hệ thống giám sát sẵn sàng. Cơ sở dữ liệu SQLite đã được khởi tạo.");
        SwingUtilities.invokeLater(this::onStartServer);
    }

    private void onStartServer() {
        if (serverController.isRunning()) {
            return;
        }
        String portStr = txtPort.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập cổng hợp lệ (1 - 65535).", "Lỗi cổng", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            serverController.startServer(port);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            txtPort.setEnabled(false);
            lblStatus.setText("Trạng thái: Đang hoạt động (Cổng " + port + ")");
            lblStatus.setForeground(DarkThemeUtil.ACCENT_SUCCESS);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Không thể khởi động máy chủ trên cổng " + port + ":\n" + ex.getMessage(),
                    "Lỗi khởi động",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void onStopServer() {
        serverController.stopServer();
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        txtPort.setEnabled(true);
        lblStatus.setText("Trạng thái: Đang dừng");
        lblStatus.setForeground(DarkThemeUtil.TEXT_MUTED);
    }

    private void appendLog(String logLine) {
        SwingUtilities.invokeLater(() -> {
            txtServerLog.append(logLine + "\n");
            txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
        });
    }

    private void refreshClientTable() {
        SwingUtilities.invokeLater(() -> {
            clientTableModel.setRowCount(0);
            List<User> activeUsers = serverController.getActiveUserSnapshots();
            for (User u : activeUsers) {
                clientTableModel.addRow(new Object[]{
                        u.getUsername(),
                        u.getIpAddress(),
                        "Trực tuyến"
                });
            }
        });
    }

    private void refreshIpList() {
        cboNetworkIps.removeAllItems();
        List<String> detected = detectActiveIpv4List();
        for (String item : detected) {
            cboNetworkIps.addItem(item);
        }
    }

    private void copySelectedIp() {
        Object selected = cboNetworkIps.getSelectedItem();
        if (selected == null) {
            return;
        }
        String text = selected.toString();
        String ipOnly = text;
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace != -1 && lastSpace + 1 < text.length()) {
            ipOnly = text.substring(lastSpace + 1).trim();
        }
        StringSelection selection = new StringSelection(ipOnly);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        appendLog("Đã sao chép địa chỉ IP vào bộ nhớ tạm: " + ipOnly);
    }

    private List<String> detectActiveIpv4List() {
        List<String> results = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        String display = ni.getDisplayName() != null ? ni.getDisplayName() : ni.getName();
                        String lower = (display + " " + ni.getName()).toLowerCase();

                        String badge;
                        if (lower.contains("zerotier") || lower.contains("zt")) {
                            badge = "[ZeroTier]";
                        } else if (lower.contains("tailscale")) {
                            badge = "[Tailscale]";
                        } else if (lower.contains("radmin") || lower.contains("hamachi")) {
                            badge = "[VPN]";
                        } else {
                            badge = "[Wi-Fi / LAN]";
                        }
                        String entry = badge + " " + display + " - " + ip;
                        if (badge.equals("[ZeroTier]") || badge.equals("[Tailscale]")) {
                            results.add(0, entry);
                        } else {
                            results.add(entry);
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        results.add("[Localhost] Máy cục bộ - 127.0.0.1");
        return results;
    }
}
