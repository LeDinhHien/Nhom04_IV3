package common.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tiện ích băm mật khẩu (SHA-256) và kiểm tra tính toàn vẹn tệp tin (MD5, SHA-256).
 */
public final class HashUtil {

    private static final int BUFFER_SIZE = 65536; // 64KB

    private HashUtil() {
    }

    /**
     * Băm mật khẩu người dùng bằng thuật toán SHA-256.
     */
    public static String hashPasswordSHA256(String password) {
        if (password == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Không hỗ trợ thuật toán SHA-256", e);
        }
    }

    /**
     * Tính mã băm MD5 của tệp tin theo buffer 64KB.
     */
    public static String calculateFileMD5(File file) throws IOException {
        return calculateFileHash(file, "MD5");
    }

    /**
     * Tính mã băm SHA-256 của tệp tin theo buffer 64KB.
     */
    public static String calculateFileSHA256(File file) throws IOException {
        return calculateFileHash(file, "SHA-256");
    }

    private static String calculateFileHash(File file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Không hỗ trợ thuật toán băm: " + algorithm, e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
