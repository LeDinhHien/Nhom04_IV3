package server;

import client.view.DarkThemeUtil;
import javax.swing.SwingUtilities;
import server.view.ServerMonitorFrame;

/**
 * Điểm khởi chạy ứng dụng Server (ServerMain).
 */
public class ServerMain {

    public static void main(String[] args) {
        DarkThemeUtil.applyDarkTheme();
        SwingUtilities.invokeLater(() -> {
            ServerMonitorFrame frame = new ServerMonitorFrame();
            frame.setVisible(true);
        });
    }
}
