package common.model;

import java.io.Serializable;

/**
 * Lớp biểu diễn tin nhắn văn bản hoặc nhật ký truyền tệp tin giữa 2 người dùng.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String sender;
    private String receiver;
    private String encryptedContent;
    private String timestamp;
    private boolean isFileLog;
    private FileMetadata fileMetadata;

    public Message() {
    }

    public Message(int id, String sender, String receiver, String encryptedContent, String timestamp, boolean isFileLog) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.encryptedContent = encryptedContent;
        this.timestamp = timestamp;
        this.isFileLog = isFileLog;
    }

    public Message(String sender, String receiver, String encryptedContent, String timestamp, boolean isFileLog) {
        this.sender = sender;
        this.receiver = receiver;
        this.encryptedContent = encryptedContent;
        this.timestamp = timestamp;
        this.isFileLog = isFileLog;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getEncryptedContent() {
        return encryptedContent;
    }

    public void setEncryptedContent(String encryptedContent) {
        this.encryptedContent = encryptedContent;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFileLog() {
        return isFileLog;
    }

    public void setFileLog(boolean isFileLog) {
        this.isFileLog = isFileLog;
    }

    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }

    public void setFileMetadata(FileMetadata fileMetadata) {
        this.fileMetadata = fileMetadata;
    }
}
