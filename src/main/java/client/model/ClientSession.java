package client.model;

import common.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lưu trữ thông tin phiên làm việc hiện tại của Client.
 */
public class ClientSession {

    private User currentUser;
    private String localIpAddress = "127.0.0.1";
    private String serverIp = "127.0.0.1";
    private int serverPort = 8888;
    private List<User> userList = new ArrayList<>();

    public ClientSession() {
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public synchronized String getLocalIpAddress() {
        return localIpAddress;
    }

    public synchronized void setLocalIpAddress(String localIpAddress) {
        this.localIpAddress = localIpAddress;
    }

    public synchronized String getServerIp() {
        return serverIp;
    }

    public synchronized void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public synchronized int getServerPort() {
        return serverPort;
    }

    public synchronized void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public synchronized List<User> getUserList() {
        return new ArrayList<>(userList);
    }

    public synchronized void setUserList(List<User> users) {
        if (users == null) {
            this.userList = new ArrayList<>();
        } else {
            this.userList = new ArrayList<>(users);
        }
    }

    public synchronized User findUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        for (User u : userList) {
            if (username.equals(u.getUsername())) {
                return u;
            }
        }
        return null;
    }

    public synchronized void clear() {
        currentUser = null;
        userList = Collections.emptyList();
    }
}
