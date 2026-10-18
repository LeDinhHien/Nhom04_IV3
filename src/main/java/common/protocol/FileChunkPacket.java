package common.protocol;

import java.io.Serializable;

/**
 * Gói tin chứa dữ liệu của từng khối (chunk 64KB) tệp tin đã được mã hóa AES-128.
 */
public class FileChunkPacket implements Serializable {

    private static final long serialVersionUID = 1L;

    private String transferId;
    private int chunkId;
    private int actualSize;
    private byte[] encryptedData;
    private boolean lastChunk;

    public FileChunkPacket() {
    }

    public FileChunkPacket(String transferId, int chunkId, int actualSize, byte[] encryptedData, boolean lastChunk) {
        this.transferId = transferId;
        this.chunkId = chunkId;
        this.actualSize = actualSize;
        this.encryptedData = encryptedData;
        this.lastChunk = lastChunk;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public int getChunkId() {
        return chunkId;
    }

    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    public int getActualSize() {
        return actualSize;
    }

    public void setActualSize(int actualSize) {
        this.actualSize = actualSize;
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(byte[] encryptedData) {
        this.encryptedData = encryptedData;
    }

    public boolean isLastChunk() {
        return lastChunk;
    }

    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
    }
}
