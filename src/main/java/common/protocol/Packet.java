package common.protocol;

import java.io.Serializable;

/**
 * Gói tin chung trao đổi trên kênh điều khiển và kênh truyền dữ liệu.
 */
public class Packet implements Serializable {

    private static final long serialVersionUID = 1L;

    private PacketType type;
    private String sender;
    private String receiver;
    private Object payload;
    private boolean success;
    private String message;

    public Packet() {
    }

    public Packet(PacketType type, String sender, String receiver, Object payload) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.payload = payload;
        this.success = true;
    }

    public Packet(PacketType type, boolean success, String message, Object payload) {
        this.type = type;
        this.success = success;
        this.message = message;
        this.payload = payload;
    }

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
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

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
