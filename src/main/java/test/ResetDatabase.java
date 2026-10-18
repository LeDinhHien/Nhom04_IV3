package test;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import server.model.DatabaseConnection;

/**
 * Tiện ích dọn sạch toàn bộ dữ liệu thử nghiệm trong cơ sở dữ liệu SQLite (chat_database.db),
 * đặt lại bộ đếm tự tăng (AUTOINCREMENT) về 1 và xóa sạch các tệp đệm (.chatapp_cache, .part).
 *
 * Cách dùng trên Apache NetBeans:
 * 1. Tắt Server và Client đang chạy.
 * 2. Nhấp chuột phải vào file ResetDatabase.java -> Chọn Run File (Shift + F6).
 */
public class ResetDatabase {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("         TIỆN ÍCH RESET CƠ SỞ DỮ LIỆU & BỘ ĐỆM CHATAPP            ");
        System.out.println("==================================================================");

        try {
            // Đảm bảo cấu trúc bảng tồn tại trước khi thực hiện lệnh DELETE
            DatabaseConnection.initDatabase();

            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                int deletedMessages = stmt.executeUpdate("DELETE FROM messages;");
                int deletedTransfers = stmt.executeUpdate("DELETE FROM file_transfers;");
                int deletedUsers = stmt.executeUpdate("DELETE FROM users;");

                // Reset bộ đếm ID tự tăng AUTOINCREMENT về lại 1 từ đầu
                try {
                    stmt.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('messages', 'file_transfers', 'users');");
                } catch (SQLException ignored) {
                    // Bảng sqlite_sequence chỉ xuất hiện sau khi có ít nhất 1 bản ghi AUTOINCREMENT
                }

                stmt.execute("VACUUM;");

                System.out.println("- Đã xóa số tin nhắn (messages)          : " + deletedMessages);
                System.out.println("- Đã xóa số lịch sử tệp (file_transfers) : " + deletedTransfers);
                System.out.println("- Đã xóa số tài khoản (users)            : " + deletedUsers);
                System.out.println("- Đã đặt lại bộ đếm ID tự tăng (sqlite_sequence) về 1.");
            }

            // Dọn sạch các thư mục bộ đệm và các tệp .part truyền dở
            int cleanedCacheFiles = 0;
            cleanedCacheFiles += cleanDirectoryContents(new File(".chatapp_cache"));
            cleanedCacheFiles += cleanDirectoryContents(new File(".chatapp_server_cache"));
            cleanedCacheFiles += deletePartFilesInRoot(new File("."));

            System.out.println("- Đã dọn sạch số tệp đệm (.chatapp_cache / .part): " + cleanedCacheFiles);
            System.out.println("------------------------------------------------------------------");
            System.out.println("[RESET THÀNH CÔNG] Đã xóa sạch toàn bộ tài khoản, tin nhắn và lịch sử truyền tệp trong SQLite!");
            System.out.println("==================================================================");

        } catch (SQLException e) {
            System.err.println("[LỖI RESET SQLITE] Không thể xóa dữ liệu trong chat_database.db: " + e.getMessage());
            System.err.println("Hãy đảm bảo bạn đã tắt ServerMain trước khi chạy ResetDatabase.");
            e.printStackTrace();
        }
    }

    /**
     * Xóa đệ quy toàn bộ tệp bên trong thư mục cache tạm.
     */
    private static int cleanDirectoryContents(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        int deletedCount = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deletedCount += cleanDirectoryContents(f);
                }
                if (f.delete()) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }

    /**
     * Xóa các tệp .part truyền dở nằm ở thư mục gốc của dự án (nếu có).
     */
    private static int deletePartFilesInRoot(File rootDir) {
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) {
            return 0;
        }
        int deletedCount = 0;
        File[] partFiles = rootDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".part"));
        if (partFiles != null) {
            for (File pf : partFiles) {
                if (pf.isFile() && pf.delete()) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }
}
