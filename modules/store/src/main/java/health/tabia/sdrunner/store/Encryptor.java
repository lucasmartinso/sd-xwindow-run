package health.tabia.sdrunner.store;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HexFormat;

/**
 * Attribute-level encryption. Port of the runner's utils/Encryptor (guava replaced by
 * java.util.HexFormat). PBE with HMAC-SHA256 + AES-128; random salt and IV per value.
 *
 * Difference from the runner: the key is not delivered by a server — it is derived from a
 * user passphrase supplied via {@link MasterKeyManager}.
 */
public class Encryptor {
    public static final String PREFIX = "$sdr$0$";
    private static final char SEPARATOR = '$';
    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final String ALGORITHM = "PBEWithHmacSHA256AndAES_128";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int AES_BLOCK_SIZE = 16;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 4096;
    private static final String CONTEXT = "sd-runner-attribute-context-v0";

    private volatile SecretKey secretKey;

    /** Derive the key from the passphrase (kept only in memory). */
    public void init(String passphrase) throws GeneralSecurityException {
        this.secretKey = SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(new PBEKeySpec(passphrase.toCharArray()));
    }

    public boolean isEnabled() {
        return secretKey != null;
    }

    public String encode(String plaintext) throws GeneralSecurityException {
        if (!isEnabled()) {
            throw new IllegalStateException("Encryption key was not initialized");
        }
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] iv = new byte[AES_BLOCK_SIZE];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, cipherParams(salt, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return PREFIX + HEX.formatHex(salt) + SEPARATOR + HEX.formatHex(iv) + SEPARATOR + HEX.formatHex(encrypted);
    }

    public String decode(String encoded) throws GeneralSecurityException {
        if (!isEnabled() || encoded == null || !encoded.startsWith(PREFIX)) {
            return encoded;
        }
        String[] parts = encoded.substring(PREFIX.length()).split("\\" + SEPARATOR);
        if (parts.length != 3) {
            return encoded;
        }
        byte[] salt = HEX.parseHex(parts[0]);
        byte[] iv = HEX.parseHex(parts[1]);
        byte[] encrypted = HEX.parseHex(parts[2]);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, cipherParams(salt, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private AlgorithmParameterSpec cipherParams(byte[] salt, byte[] iv) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));
        mac.update(CONTEXT.getBytes(StandardCharsets.UTF_8));
        return new PBEParameterSpec(mac.doFinal(), ITERATION_COUNT, new IvParameterSpec(iv));
    }
}
