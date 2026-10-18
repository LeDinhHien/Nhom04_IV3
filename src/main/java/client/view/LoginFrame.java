package client.view;

import client.controller.ChatSocketController;
import client.controller.ServerDiscoveryClient;
import client.model.ClientSession;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Giao diện Đăng nhập hệ thống nền tối (Đăng nhập - ChatApp).
 * Thiết kế dạng thẻ (Card) căn giữa trên nền tối #1E1F22, tự động tìm và kết nối máy chủ.
 */
public class LoginFrame extends JFrame {

    private final JTextField txtUsername;
    private final JPasswordField txtPassword;
    private final JButton btnLogin;
    private final JButton btnRegister;
    private final JLabel lblStatus;

    private final ClientSession session;
    private final ChatSocketController chatController;

    public LoginFrame() {
        this.session = new ClientSession();
        this.chatController = new ChatSocketController(session);

        setTitle("Đăng nhập - ChatApp");
        setSize(460, 420);
        setMinimumSize(new Dimension(420, 390));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DarkThemeUtil.BG_PRIMARY);
        root.setBorder(new EmptyBorder(20, 28, 14, 28));
        setContentPane(root);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        JPanel cardPanel = new JPanel();
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        DarkThemeUtil.applyCardStyle(cardPanel, DarkThemeUtil.BG_SECONDARY, 28);
        cardPanel.setPreferredSize(new Dimension(370, 305));

        JLabel lblTitle = new JLabel("ĐĂNG NHẬP HỆ THỐNG", SwingConstants.CENTER);
        lblTitle.setFont(DarkThemeUtil.FONT_TITLE);
        lblTitle.setForeground(DarkThemeUtil.TEXT_PRIMARY);
        lblTitle.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lblUser = new JLabel("Tên đăng nhập");
        lblUser.setFont(DarkThemeUtil.FONT_BOLD);
        lblUser.setForeground(DarkThemeUtil.TEXT_MUTED);
        lblUser.setAlignmentX(CENTER_ALIGNMENT);

        txtUsername = new JTextField();
        DarkThemeUtil.styleTextField(txtUsername, "Nhập tên đăng nhập...");
        txtUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lblPass = new JLabel("Mật khẩu");
        lblPass.setFont(DarkThemeUtil.FONT_BOLD);
        lblPass.setForeground(DarkThemeUtil.TEXT_MUTED);
        lblPass.setAlignmentX(CENTER_ALIGNMENT);

        txtPassword = new JPasswordField();
        DarkThemeUtil.stylePasswordField(txtPassword, "Nhập mật khẩu...");
        txtPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel buttonRow = new JPanel(new GridBagLayout());
        buttonRow.setOpaque(false);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridy = 0;

        btnLogin = DarkThemeUtil.createStyledButton("Đăng nhập", DarkThemeUtil.ACCENT_PRIMARY, DarkThemeUtil.TEXT_PRIMARY);
        btnRegister = DarkThemeUtil.createStyledButton("Đăng ký", DarkThemeUtil.BG_INPUT, DarkThemeUtil.TEXT_PRIMARY);

        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 6);
        buttonRow.add(btnLogin, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, 6, 0, 0);
        buttonRow.add(btnRegister, gbc);

        lblStatus = new JLabel(" ", SwingConstants.CENTER);
        lblStatus.setFont(DarkThemeUtil.FONT_SMALL);
        lblStatus.setForeground(DarkThemeUtil.TEXT_MUTED);
        lblStatus.setAlignmentX(CENTER_ALIGNMENT);

        JPanel userLabelWrap = new JPanel(new BorderLayout());
        userLabelWrap.setOpaque(false);
        userLabelWrap.add(lblUser, BorderLayout.WEST);
        userLabelWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JPanel passLabelWrap = new JPanel(new BorderLayout());
        passLabelWrap.setOpaque(false);
        passLabelWrap.add(lblPass, BorderLayout.WEST);
        passLabelWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        cardPanel.add(lblTitle);
        cardPanel.add(Box.createVerticalStrut(22));
        cardPanel.add(userLabelWrap);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(txtUsername);
        cardPanel.add(Box.createVerticalStrut(14));
        cardPanel.add(passLabelWrap);
        cardPanel.add(Box.createVerticalStrut(6));
        cardPanel.add(txtPassword);
        cardPanel.add(Box.createVerticalStrut(20));
        cardPanel.add(buttonRow);
        cardPanel.add(Box.createVerticalStrut(12));
        cardPanel.add(lblStatus);

        centerWrapper.add(cardPanel);
        root.add(centerWrapper, BorderLayout.CENTER);

        // Nút nhỏ tinh tế ở góc dưới (dự phòng khi khác subnet hoàn toàn mà UDP bị chặn)
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomBar.setOpaque(false);

        JButton btnServerConfig = new JButton("Máy chủ");
        btnServerConfig.setFont(DarkThemeUtil.FONT_SMALL);
        btnServerConfig.setForeground(DarkThemeUtil.TEXT_MUTED);
        btnServerConfig.setBackground(DarkThemeUtil.BG_PRIMARY);
        btnServerConfig.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        btnServerConfig.setFocusPainted(false);
        btnServerConfig.setContentAreaFilled(false);
        btnServerConfig.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnServerConfig.addActionListener(e -> openManualServerDialog());

        bottomBar.add(btnServerConfig);
        root.add(bottomBar, BorderLayout.SOUTH);

        setupCallbacks();

        btnLogin.addActionListener(e -> performAuth(true));
        btnRegister.addActionListener(e -> performAuth(false));
        txtPassword.addActionListener(e -> performAuth(true));
    }

    private void setupCallbacks() {
        chatController.setLoginResultCallback((success, message) -> SwingUtilities.invokeLater(() -> {
            setControlsEnabled(true);
            if (success) {
                lblStatus.setText("Đăng nhập thành công!");
                lblStatus.setForeground(DarkThemeUtil.ACCENT_SUCCESS);
                MainChatFrame mainFrame = new MainChatFrame(chatController);
                mainFrame.setVisible(true);
                dispose();
            } else {
                lblStatus.setText(message);
                lblStatus.setForeground(DarkThemeUtil.ACCENT_DANGER);
                JOptionPane.showMessageDialog(this, message, "Đăng nhập không thành công", JOptionPane.WARNING_MESSAGE);
            }
        }));

        chatController.setRegisterResultCallback((success, message) -> SwingUtilities.invokeLater(() -> {
            setControlsEnabled(true);
            if (success) {
                lblStatus.setText("Đăng ký thành công!");
                lblStatus.setForeground(DarkThemeUtil.ACCENT_SUCCESS);
                JOptionPane.showMessageDialog(this, message, "Đăng ký tài khoản", JOptionPane.INFORMATION_MESSAGE);
            } else {
                lblStatus.setText(message);
                lblStatus.setForeground(DarkThemeUtil.ACCENT_DANGER);
                JOptionPane.showMessageDialog(this, message, "Đăng ký không thành công", JOptionPane.WARNING_MESSAGE);
            }
        }));
    }

    private void performAuth(boolean isLogin) {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            lblStatus.setForeground(DarkThemeUtil.ACCENT_WARNING);
            return;
        }

        setControlsEnabled(false);
        lblStatus.setText("Đang tự động kết nối máy chủ...");
        lblStatus.setForeground(DarkThemeUtil.TEXT_MUTED);

        new Thread(() -> {
            try {
                ServerDiscoveryClient.connectAutomatically(chatController);
                if (isLogin) {
                    chatController.sendLoginRequest(username, password);
                } else {
                    chatController.sendRegisterRequest(username, password);
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    setControlsEnabled(true);
                    lblStatus.setText("Không tìm thấy máy chủ.");
                    lblStatus.setForeground(DarkThemeUtil.ACCENT_DANGER);
                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage(),
                            "Không thể kết nối",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }, "Client-AutoConnect-Thread").start();
    }

    private void openManualServerDialog() {
        String currentIp = ServerDiscoveryClient.getSavedServerIp();
        if (currentIp.isEmpty()) {
            currentIp = "127.0.0.1";
        }
        String input = (String) JOptionPane.showInputDialog(
                this,
                "Nhập địa chỉ IP máy chủ (chỉ cần nhập 1 lần nếu mạng chặn tự động tìm kiếm):",
                "Thiết lập máy chủ",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentIp
        );
        if (input != null && !input.trim().isEmpty()) {
            String trimmed = input.trim();
            String host = trimmed;
            int port = ServerDiscoveryClient.getSavedServerPort();
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                host = parts[0].trim();
                try {
                    port = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            ServerDiscoveryClient.saveServerConfig(host, port);
            chatController.disconnect();
            lblStatus.setText("Đã lưu địa chỉ máy chủ.");
            lblStatus.setForeground(DarkThemeUtil.ACCENT_SUCCESS);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnLogin.setEnabled(enabled);
        btnRegister.setEnabled(enabled);
        txtUsername.setEnabled(enabled);
        txtPassword.setEnabled(enabled);
    }
}
