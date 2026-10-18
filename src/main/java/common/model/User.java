package common.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lớp biểu diễn thông tin người dùng trong hệ thống.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private String passwordHash;
    private String ipAddress;
    private int p2pPort;
    private boolean online;

    public User() {
    }

    public User(int id, String username, String passwordHash, String ipAddress, int p2pPort, boolean online) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.ipAddress = ipAddress;
        this.p2pPort = p2pPort;
        this.online = online;
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getP2pPort() {
        return p2pPort;
    }

    public void setP2pPort(int p2pPort) {
        this.p2pPort = p2pPort;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return username;
    }
}
