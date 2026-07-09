package health.tabia.sdrunner.store;

import health.tabia.sdrunner.core.model.ConnectionProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {

    @Test
    void persistsProfilesWithEncryptedPasswordAndVerifiesPassphrase(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("app.db");

        // First run: set passphrase, seed verifier, save a profile.
        MasterKeyManager keys = new MasterKeyManager();
        keys.unlock("correct horse battery staple");
        ProfileStore store = new ProfileStore(db, keys);
        assertTrue(store.isFirstRun());
        store.initVerifier();

        ConnectionProfile p = new ConnectionProfile("p1", "local mysql");
        p.setHost("localhost");
        p.setPort(3307);
        p.setUser("root");
        p.setPassword("s3cr3t");
        store.saveProfile(p);

        List<ConnectionProfile> loaded = store.listProfiles();
        assertEquals(1, loaded.size());
        assertEquals("s3cr3t", loaded.get(0).getPassword(), "password should decrypt back");

        // Password must be encrypted at rest (not equal to plaintext, prefixed).
        String raw = readRawPassword(db);
        assertTrue(raw.startsWith(Encryptor.PREFIX), "stored password must be encrypted");
        assertFalse(raw.contains("s3cr3t"));

        // Reopen with correct passphrase -> verifies.
        MasterKeyManager ok = new MasterKeyManager();
        ok.unlock("correct horse battery staple");
        ProfileStore store2 = new ProfileStore(db, ok);
        assertFalse(store2.isFirstRun());
        assertTrue(store2.verifyPassphrase());

        // Reopen with wrong passphrase -> does not verify.
        MasterKeyManager wrong = new MasterKeyManager();
        wrong.unlock("wrong passphrase");
        ProfileStore store3 = new ProfileStore(db, wrong);
        assertFalse(store3.verifyPassphrase());
    }

    private static String readRawPassword(Path db) throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             var rs = c.createStatement().executeQuery("SELECT password FROM profiles LIMIT 1")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
