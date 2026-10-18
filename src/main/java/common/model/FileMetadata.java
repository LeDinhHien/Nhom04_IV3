package common.model;

import java.io.Serializable;

/**
 * Lớp lưu trữ thông tin siêu dữ liệu (Metadata) của phiên truyền tệp tin.
 */
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int DEFAULT_CHUNK_SIZE = 65536; // 64KB

    private String transferId;
    private String fileName;
    private long fileSize;
    private String md5Checksum;
    private int chunkSize = DEFAULT_CHUNK_SIZE;
    private int totalChunks;
    private String sender;
    private String receiver;
    private String senderIp;
    private int senderP2PPort;
    private String receiverIp;
    private int receiverP2PPort;
    private int resumeFromChunk = 0;
    private boolean relayMode = false;

    public FileMetadata() {
    }

    public FileMetadata(String transferId, String fileName, long fileSize, String md5Checksum,
                        int chunkSize, int totalChunks, String sender, String receiver) {
        this.transferId = transferId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.md5Checksum = md5Checksum;
        this.chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        this.totalChunks = totalChunks;
        this.sender = sender;
        this.receiver = receiver;
        this.resumeFromChunk = 0;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5Checksum() {
        return md5Checksum;
    }

    public void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
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

    public String getSenderIp() {
        return senderIp;
    }

    public void setSenderIp(String senderIp) {
        this.senderIp = senderIp;
    }

    public int getSenderP2PPort() {
        return senderP2PPort;
    }

    public void setSenderP2PPort(int senderP2PPort) {
        this.senderP2PPort = senderP2PPort;
    }

    public String getReceiverIp() {
        return receiverIp;
    }

    public void setReceiverIp(String receiverIp) {
        this.receiverIp = receiverIp;
    }

    public int getReceiverP2PPort() {
        return receiverP2PPort;
    }

    public void setReceiverP2PPort(int receiverP2PPort) {
        this.receiverP2PPort = receiverP2PPort;
    }

    public int getResumeFromChunk() {
        return resumeFromChunk;
    }

    public void setResumeFromChunk(int resumeFromChunk) {
        this.resumeFromChunk = resumeFromChunk;
    }

    public boolean isRelayMode() {
        return relayMode;
    }

    public void setRelayMode(boolean relayMode) {
        this.relayMode = relayMode;
    }
}
