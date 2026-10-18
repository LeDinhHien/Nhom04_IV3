package server.model;

import common.model.FileMetadata;
import common.model.Message;
import common.security.AESUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy xuất dữ liệu tin nhắn và lịch sử truyền tệp tin trên SQLite.
 */
public class MessageDAO {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public synchronized boolean saveMessage(Message message) {
        if (message.getTimestamp() == null || message.getTimestamp().isEmpty()) {
            message.setTimestamp(LocalDateTime.now().format(FORMATTER));
        }
        String sql = "INSERT INTO messages(sender, receiver, content, is_file_log, timestamp) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, message.getSender());
            ps.setString(2, message.getReceiver());
            ps.setString(3, message.getEncryptedContent());
            ps.setInt(4, message.isFileLog() ? 1 : 0);
            ps.setString(5, message.getTimestamp());
            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        message.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized List<Message> getConversationHistory(String user1, String user2) {
        List<Message> history = new ArrayList<>();
        String sql = "SELECT id, sender, receiver, content, is_file_log, timestamp "
                + "FROM messages "
                + "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) "
                + "ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean isFileLog = rs.getInt("is_file_log") == 1;
                    Message msg = new Message(
                            rs.getInt("id"),
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("content"),
                            rs.getString("timestamp"),
                            isFileLog
                    );
                    if (isFileLog) {
                        FileMetadata meta = parseFileMetadataFromLog(conn, msg);
                        msg.setFileMetadata(meta);
                    }
                    history.add(msg);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    private FileMetadata parseFileMetadataFromLog(Connection conn, Message msg) {
        String decrypted = AESUtil.decryptText(msg.getEncryptedContent());
        // Định dạng lưu: FILE_CARD|<transferId>|<fileName>|<fileSize>|<md5Checksum>
        if (decrypted != null && decrypted.startsWith("FILE_CARD|")) {
            String[] parts = decrypted.split("\\|", 5);
            if (parts.length == 5) {
                try {
                    String transferId = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);
                    String md5 = parts[4];
                    int totalChunks = (int) Math.ceil((double) fileSize / FileMetadata.DEFAULT_CHUNK_SIZE);
                    if (totalChunks == 0) {
                        totalChunks = 1;
                    }
                    return new FileMetadata(
                            transferId,
                            fileName,
                            fileSize,
                            md5,
                            FileMetadata.DEFAULT_CHUNK_SIZE,
                            totalChunks,
                            msg.getSender(),
                            msg.getReceiver()
                    );
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // Dự phòng truy vấn từ bảng file_transfers nếu tin nhắn cũ
        String sql = "SELECT transfer_id, file_name, file_size, md5_checksum FROM file_transfers "
                + "WHERE sender = ? AND receiver = ? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getSender());
            ps.setString(2, msg.getReceiver());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long size = rs.getLong("file_size");
                    int chunks = Math.max(1, (int) Math.ceil((double) size / FileMetadata.DEFAULT_CHUNK_SIZE));
                    return new FileMetadata(
                            rs.getString("transfer_id"),
                            rs.getString("file_name"),
                            size,
                            rs.getString("md5_checksum"),
                            FileMetadata.DEFAULT_CHUNK_SIZE,
                            chunks,
                            msg.getSender(),
                            msg.getReceiver()
                    );
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public synchronized void saveOrUpdateFileTransfer(FileMetadata metadata, String status) {
        if (metadata == null || metadata.getTransferId() == null) {
            return;
        }
        String sql = "INSERT INTO file_transfers(transfer_id, sender, receiver, file_name, file_size, md5_checksum, status, timestamp) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(transfer_id) DO UPDATE SET status = excluded.status, timestamp = excluded.timestamp";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, metadata.getTransferId());
            ps.setString(2, metadata.getSender());
            ps.setString(3, metadata.getReceiver());
            ps.setString(4, metadata.getFileName());
            ps.setLong(5, metadata.getFileSize());
            ps.setString(6, metadata.getMd5Checksum());
            ps.setString(7, status);
            ps.setString(8, LocalDateTime.now().format(FORMATTER));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
