package server.model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối và khởi tạo cấu trúc cơ sở dữ liệu nhúng SQLite.
 */
public final class DatabaseConnection {

    private static final String DB_URL = "jdbc:sqlite:chat_database.db";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static synchronized void initDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE NOT NULL, "
                + "password_hash TEXT NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "sender TEXT NOT NULL, "
                + "receiver TEXT NOT NULL, "
                + "content TEXT NOT NULL, "
                + "is_file_log INTEGER DEFAULT 0, "
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        String createFileTransfersTable = "CREATE TABLE IF NOT EXISTS file_transfers ("
                + "transfer_id TEXT PRIMARY KEY, "
                + "sender TEXT, "
                + "receiver TEXT, "
                + "file_name TEXT, "
                + "file_size INTEGER, "
                + "md5_checksum TEXT, "
                + "status TEXT, "
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute(createUsersTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createFileTransfersTable);
        } catch (SQLException e) {
            throw new RuntimeException("Không thể khởi tạo cơ sở dữ liệu SQLite: " + e.getMessage(), e);
        }
    }
}
