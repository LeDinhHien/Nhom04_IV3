package common.protocol;

import java.io.Serializable;

/**
 * Các cờ lệnh của giao thức truyền thông trên nền TCP.
 */
public enum PacketType implements Serializable {
    // Xác thực & Trạng thái người dùng
    LOGIN_REQ,
    LOGIN_RES,
    REGISTER_REQ,
    REGISTER_RES,
    LOGOUT,
    ONLINE_USERS_LIST,

    // Chat văn bản & Lịch sử trò chuyện
    CHAT_MESSAGE,
    LOAD_HISTORY_REQ,
    LOAD_HISTORY_RES,

    // Báo hiệu truyền tệp tin (Signaling qua Server)
    FILE_TRANSFER_REQ,
    FILE_TRANSFER_ACCEPT,
    FILE_TRANSFER_REJECT,
    FILE_TRANSFER_COMPLETE,

    // Truyền tệp tin P2P / Điều khiển / Relay dự phòng qua Server
    P2P_HANDSHAKE,
    FILE_CHUNK,
    FILE_CHUNK_ACK,
    TRANSFER_PAUSE,
    TRANSFER_RESUME,
    TRANSFER_CANCEL
}
