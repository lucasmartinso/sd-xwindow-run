package health.tabia.sdrunner.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and manages JDBC connection pools. Port of the runner's DatasourceEngine,
 * simplified to HikariCP directly (no Spring) and keyed by a single profile id.
 */
public class ConnectionEngine implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ConnectionEngine.class);

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    /** A pool tied to a saved profile; reused across queries. */
    public DataSource register(String profileId, String url, String username, String password, String driver) {
        HikariDataSource ds = build("HikariPool-" + profileId, url, username, password, driver);
        HikariDataSource previous = pools.put(profileId, ds);
        if (previous != null && !previous.isClosed()) {
            previous.close();
        }
        return ds;
    }

    public Optional<DataSource> get(String profileId) {
        return Optional.ofNullable(pools.get(profileId));
    }

    public void remove(String profileId) {
        HikariDataSource ds = pools.remove(profileId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    /** A short-lived pool used for "test connection" / seeding; caller must close it. */
    public DisposableDataSource createDisposable(String url, String username, String password, String driver) {
        log.info("Creating disposable datasource");
        HikariDataSource ds = build("HikariPool-Disposable-" + UUID.randomUUID(), url, username, password, driver);
        return new DisposableDataSource(ds);
    }

    private HikariDataSource build(String poolName, String url, String username, String password, String driver) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName(poolName);
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        if (driver != null && !driver.isBlank()) {
            cfg.setDriverClassName(driver);
        }
        cfg.setMaximumPoolSize(8);
        return new HikariDataSource(cfg);
    }

    @Override
    public void close() {
        pools.values().forEach(ds -> {
            if (!ds.isClosed()) {
                ds.close();
            }
        });
        pools.clear();
    }

    /** Wraps a Hikari pool so try-with-resources closes it. */
    public static final class DisposableDataSource implements Closeable {
        private final HikariDataSource dataSource;

        public DisposableDataSource(HikariDataSource dataSource) {
            this.dataSource = dataSource;
        }

        public DataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() {
            if (!dataSource.isClosed()) {
                log.info("Removing DataSource: {}", dataSource.getPoolName());
                dataSource.close();
            }
        }
    }
}
