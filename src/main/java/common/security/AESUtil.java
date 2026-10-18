package common.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tiện ích mã hóa và giải mã đối xứng AES-128 cho tin nhắn văn bản và các khối dữ liệu tệp tin.
 */
public final class AESUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final byte[] PRIMARY_KEY_BYTES = "ChatAppSecret128".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LEGACY_KEY_BYTES = "Nhom04ChatKey128".getBytes(StandardCharsets.UTF_8);

    private static final SecretKeySpec PRIMARY_KEY = new SecretKeySpec(PRIMARY_KEY_BYTES, ALGORITHM);
    private static final SecretKeySpec LEGACY_KEY = new SecretKeySpec(LEGACY_KEY_BYTES, ALGORITHM);

    private AESUtil() {
    }

    /**
     * Mã hóa chuỗi văn bản sang chuỗi Base64 bằng AES-128.
     */
    public static String encryptText(String plainText) {
        if (plainText == null) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, PRIMARY_KEY);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa văn bản AES-128", e);
        }
    }

    /**
     * Giải mã chuỗi Base64 về văn bản gốc bằng AES-128.
     * Hỗ trợ tự động giải mã cả khóa hiện tại lẫn khóa phiên bản cũ trong SQLite,
     * nếu không phải chuỗi mã hóa hợp lệ thì giữ nguyên chuỗi gốc.
     */
    public static String decryptText(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        String trimmed = cipherText.trim();
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(trimmed);
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, PRIMARY_KEY);
                byte[] decryptedBytes = cipher.doFinal(decodedBytes);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception firstEx) {
                Cipher legacyCipher = Cipher.getInstance(TRANSFORMATION);
                legacyCipher.init(Cipher.DECRYPT_MODE, LEGACY_KEY);
                byte[] decryptedLegacy = legacyCipher.doFinal(decodedBytes);
                return new String(decryptedLegacy, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return cipherText;
        }
    }

    /**
     * Mã hóa trực tiếp mảng byte của từng khối dữ liệu (chunk) khi truyền tệp.
     */
    public static byte[] encryptBytes(byte[] data) {
        if (data == null) {
            return new byte[0];
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, PRIMARY_KEY);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa mảng byte AES-128", e);
        }
    }

    /**
     * Giải mã trực tiếp mảng byte của từng khối dữ liệu (chunk) khi nhận tệp.
     */
    public static byte[] decryptBytes(byte[] encryptedData) {
        if (encryptedData == null) {
            return new byte[0];
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, PRIMARY_KEY);
            return cipher.doFinal(encryptedData);
        } catch (Exception firstEx) {
            try {
                Cipher legacyCipher = Cipher.getInstance(TRANSFORMATION);
                legacyCipher.init(Cipher.DECRYPT_MODE, LEGACY_KEY);
                return legacyCipher.doFinal(encryptedData);
            } catch (Exception e) {
                throw new RuntimeException("Lỗi giải mã mảng byte AES-128", e);
            }
        }
    }
}
