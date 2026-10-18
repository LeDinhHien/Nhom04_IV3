package client.model;

import common.model.FileMetadata;
import java.io.File;

/**
 * Lưu trạng thái tiến trình truyền tệp tin (phục vụ Pause, Resume và khôi phục khi gián đoạn).
 */
public class TransferState {

    private String transferId;
    private FileMetadata metadata;
    private File sourceFile;
    private File partFile;
    private File finalDestinationFile;
    private volatile int lastTransferredChunk;
    private volatile boolean isPaused;
    private volatile boolean isCancelled;
    private volatile boolean isCompleted;
    private volatile boolean isInterrupted;
    private final boolean senderMode;

    public TransferState(FileMetadata metadata, boolean senderMode) {
        this.metadata = metadata;
        this.transferId = metadata != null ? metadata.getTransferId() : "";
        this.senderMode = senderMode;
        this.lastTransferredChunk = metadata != null ? metadata.getResumeFromChunk() : 0;
        this.isPaused = false;
        this.isCancelled = false;
        this.isCompleted = false;
        this.isInterrupted = false;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public FileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(FileMetadata metadata) {
        this.metadata = metadata;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public File getPartFile() {
        return partFile;
    }

    public void setPartFile(File partFile) {
        this.partFile = partFile;
    }

    public File getFinalDestinationFile() {
        return finalDestinationFile;
    }

    public void setFinalDestinationFile(File finalDestinationFile) {
        this.finalDestinationFile = finalDestinationFile;
    }

    public int getLastTransferredChunk() {
        return lastTransferredChunk;
    }

    public void setLastTransferredChunk(int lastTransferredChunk) {
        this.lastTransferredChunk = lastTransferredChunk;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.isCancelled = cancelled;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
    }

    public boolean isInterrupted() {
        return isInterrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.isInterrupted = interrupted;
    }

    public boolean isSenderMode() {
        return senderMode;
    }
}
