package health.tabia.sdrunner.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the connection + streaming query pipeline against an in-memory SQLite,
 * so the core can be tested without a running MySQL.
 */
class QueryRunnerTest {

    @Test
    void runsSelectAndStreamsRows() {
        ConnectionEngine engine = new ConnectionEngine();
        RunningQueries running = new RunningQueries();

        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean[] done = {false};
        Exception[] error = {null};

        try (ConnectionEngine.DisposableDataSource ds =
                     engine.createDisposable("jdbc:sqlite::memory:", "", "", "org.sqlite.JDBC")) {
            QueryRunner.runQuery(
                    false,
                    "SELECT 1 AS ok, 'hello' AS greeting",
                    ds.dataSource(), running, UUID.randomUUID().toString(), null, null,
                    (n, cols) -> columns.addAll(cols),
                    (n, row) -> rows.add(row),
                    () -> done[0] = true,
                    e -> error[0] = e
            );
        } finally {
            engine.close();
        }

        assertFalse(error[0] != null, "should not error: " + error[0]);
        assertEquals(List.of("ok", "greeting"), columns);
        assertEquals(1, rows.size());
        assertEquals("1", rows.get(0).get(0));
        assertEquals("hello", rows.get(0).get(1));
        assertEquals(0, running.size(), "running registry should be cleared");
    }
}
