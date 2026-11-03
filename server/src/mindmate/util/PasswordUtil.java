package mindmate.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing.
 *
 * PBKDF2-HMAC-SHA256 with a per-user random salt. A plain SHA-256 hash can be
 * tried billions of times a second on a GPU; PBKDF2 with a high iteration count
 * is deliberately slow, so the same attack becomes impractical. Everything used
 * here ships with the JDK, so there is no extra dependency.
 *
 * Stored form:  iterations:salt:hash   (salt and hash Base64)
 */
public final class PasswordUtil {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    public static String hash(String plainText) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = pbkdf2(plainText, salt, ITERATIONS);
        return ITERATIONS + ":"
             + Base64.getEncoder().encodeToString(salt) + ":"
             + Base64.getEncoder().encodeToString(key);
    }

    public static boolean matches(String plainText, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(plainText, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private static byte[] pbkdf2(String plainText, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    plainText == null ? new char[0] : plainText.toCharArray(),
                    salt, iterations, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("PBKDF2 is not available", ex);
        }
    }

    /** Session tokens are stored hashed, so a leaked table cannot be replayed. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] bytes = d.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
