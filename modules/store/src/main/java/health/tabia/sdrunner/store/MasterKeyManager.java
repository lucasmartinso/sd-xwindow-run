package health.tabia.sdrunner.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the master passphrase-derived key in memory only. Replaces the runner's
 * DataSecurityManager, whose key came from CareOS via ackHealthcheck — here the key
 * comes from the user's master passphrase.
 */
public class MasterKeyManager {
    private static final Logger log = LoggerFactory.getLogger(MasterKeyManager.class);

    private final Encryptor encryptor = new Encryptor();

    public void unlock(String passphrase) {
        try {
            encryptor.init(passphrase);
            log.info("Master key initialized (in memory)");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize master key", e);
        }
    }

    public boolean isUnlocked() {
        return encryptor.isEnabled();
    }

    public Encryptor encryptor() {
        return encryptor;
    }
}
