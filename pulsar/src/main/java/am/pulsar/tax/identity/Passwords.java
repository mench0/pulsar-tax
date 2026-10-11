package am.pulsar.tax.identity;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** PBKDF2-SHA256 (210 000 итераций, соль и хэш в hex), SHA-256 токенов и случайные токены. */
public final class Passwords {

    static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex), ITERATIONS, 256);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Сравнение за постоянное время. */
    public static boolean matches(String password, String saltHex, String hashHex) {
        return MessageDigest.isEqual(hash(password, saltHex).getBytes(StandardCharsets.US_ASCII),
                hashHex.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
