package health.tabia.sdrunner.store;

import health.tabia.sdrunner.core.model.ConnectionProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists connection profiles and query history in a local SQLite database
 * (plain JDBC — simpler than JPA/Liquibase for the POC). Passwords are encrypted at rest
 * via {@link Encryptor}. A "verifier" setting validates the master passphrase.
 */
public class ProfileStore {
    private static final Logger log = LoggerFactory.getLogger(ProfileStore.class);
    private static final String VERIFIER_KEY = "verifier";
    private static final String VERIFIER_PLAINTEXT = "SDR_OK";

    private final String jdbcUrl;
    private final MasterKeyManager keys;

    public ProfileStore(Path dbFile, MasterKeyManager keys) {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        this.keys = keys;
        initSchema();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() {
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS profiles (
                      id TEXT PRIMARY KEY, name TEXT, host TEXT, port INTEGER,
                      database TEXT, username TEXT, password TEXT,
                      driver_class TEXT, extra_params TEXT
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      profile_id TEXT, sql TEXT, executed_at INTEGER
                    )""");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init store schema", e);
        }
    }

    /** True if the DB has never had a passphrase set. */
    public boolean isFirstRun() {
        return getSetting(VERIFIER_KEY) == null;
    }

    /** On first run, seed the verifier using the (already unlocked) key. */
    public void initVerifier() {
        requireUnlocked();
        try {
            putSetting(VERIFIER_KEY, keys.encryptor().encode(VERIFIER_PLAINTEXT));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write verifier", e);
        }
    }

    /** Validate that the currently unlocked key matches the stored verifier. */
    public boolean verifyPassphrase() {
        requireUnlocked();
        String stored = getSetting(VERIFIER_KEY);
        if (stored == null) {
            return false;
        }
        try {
            return VERIFIER_PLAINTEXT.equals(keys.encryptor().decode(stored));
        } catch (Exception e) {
            return false;
        }
    }

    public List<ConnectionProfile> listProfiles() {
        requireUnlocked();
        List<ConnectionProfile> out = new ArrayList<>();
        try (Connection c = open();
             ResultSet rs = c.createStatement().executeQuery("SELECT * FROM profiles ORDER BY name")) {
            while (rs.next()) {
                ConnectionProfile p = new ConnectionProfile(rs.getString("id"), rs.getString("name"));
                p.setHost(rs.getString("host"));
                p.setPort(rs.getInt("port"));
                p.setDatabase(rs.getString("database"));
                p.setUser(rs.getString("username"));
                p.setPassword(keys.encryptor().decode(rs.getString("password")));
                p.setDriverClass(rs.getString("driver_class"));
                p.setExtraParams(rs.getString("extra_params"));
                out.add(p);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list profiles", e);
        }
        return out;
    }

    public void saveProfile(ConnectionProfile p) {
        requireUnlocked();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO profiles (id,name,host,port,database,username,password,driver_class,extra_params)
                     VALUES (?,?,?,?,?,?,?,?,?)
                     ON CONFLICT(id) DO UPDATE SET
                       name=excluded.name, host=excluded.host, port=excluded.port,
                       database=excluded.database, username=excluded.username, password=excluded.password,
                       driver_class=excluded.driver_class, extra_params=excluded.extra_params
                     """)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getHost());
            ps.setInt(4, p.getPort());
            ps.setString(5, p.getDatabase());
            ps.setString(6, p.getUser());
            ps.setString(7, keys.encryptor().encode(p.getPassword() == null ? "" : p.getPassword()));
            ps.setString(8, p.getDriverClass());
            ps.setString(9, p.getExtraParams());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save profile", e);
        }
    }

    public void deleteProfile(String id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM profiles WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete profile", e);
        }
    }

    public void addHistory(String profileId, String sql, long executedAt) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO history (profile_id, sql, executed_at) VALUES (?,?,?)")) {
            ps.setString(1, profileId);
            ps.setString(2, sql);
            ps.setLong(3, executedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to add history: {}", e.getMessage());
        }
    }

    public List<QueryHistoryEntry> listHistory(int limit) {
        List<QueryHistoryEntry> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM history ORDER BY executed_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QueryHistoryEntry(rs.getLong("id"), rs.getString("profile_id"),
                            rs.getString("sql"), rs.getLong("executed_at")));
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to list history: {}", e.getMessage());
        }
        return out;
    }

    private String getSetting(String key) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read setting", e);
        }
    }

    private void putSetting(String key, String value) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to write setting", e);
        }
    }

    private void requireUnlocked() {
        if (!keys.isUnlocked()) {
            throw new IllegalStateException("Store is locked — unlock with the master passphrase first");
        }
    }
}
