package client;

import client.view.DarkThemeUtil;
import client.view.LoginFrame;
import javax.swing.SwingUtilities;

/**
 * Điểm khởi chạy ứng dụng Client (ClientMain).
 */
public class ClientMain {

    public static void main(String[] args) {
        DarkThemeUtil.applyDarkTheme();
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
