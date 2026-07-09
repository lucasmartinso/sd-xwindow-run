package health.tabia.sdrunner.provisioner;

import health.tabia.sdrunner.core.ConnectionEngine;
import health.tabia.sdrunner.core.QueryRunner;
import health.tabia.sdrunner.core.RunningQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: provisions a real MySQL container, seeds it and reads the seeded rows.
 * Enabled only when SD_IT_DOCKER=1 (requires a working Docker daemon).
 */
@EnabledIfEnvironmentVariable(named = "SD_IT_DOCKER", matches = "1")
class DockerMysqlProvisionerIT {

    @Test
    void provisionsSeedsAndQueries() {
        DockerMysqlProvisioner provisioner = new DockerMysqlProvisioner();
        String name = "it" + System.nanoTime() % 100000;
        String seed = """
                CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(32));
                INSERT INTO users VALUES (1,'alice'),(2,'bob'),(3,'carol');
                """;
        EnvironmentSpec spec = new EnvironmentSpec(name, "8", 0, "dev", "app", seed);

        ProvisionResult r = null;
        try {
            r = provisioner.create(spec);
            assertTrue(provisioner.isRunning(name), "container should be running");

            List<List<String>> rows = query(r, "SELECT id, name FROM users ORDER BY id");
            assertEquals(3, rows.size());
            assertEquals("alice", rows.get(0).get(1));
            assertEquals("carol", rows.get(2).get(1));
        } finally {
            if (r != null) {
                provisioner.remove(name, true);
            }
        }
    }

    private static List<List<String>> query(ProvisionResult r, String sql) {
        ConnectionEngine engine = new ConnectionEngine();
        RunningQueries running = new RunningQueries();
        List<List<String>> rows = new ArrayList<>();
        Exception[] err = {null};
        try (ConnectionEngine.DisposableDataSource ds =
                     engine.createDisposable(r.jdbcUrl(), "root", r.rootPassword(), "com.mysql.cj.jdbc.Driver")) {
            QueryRunner.runQuery(false, sql, ds.dataSource(), running, UUID.randomUUID().toString(), null, null,
                    (n, cols) -> {}, (n, row) -> rows.add(row), () -> {}, e -> err[0] = e);
        } finally {
            engine.close();
        }
        if (err[0] != null) {
            throw new RuntimeException(err[0]);
        }
        return rows;
    }
}
