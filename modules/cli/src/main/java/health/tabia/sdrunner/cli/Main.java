package health.tabia.sdrunner.cli;

import health.tabia.sdrunner.core.ConnectionEngine;
import health.tabia.sdrunner.core.QueryRunner;
import health.tabia.sdrunner.core.RunningQueries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smoke-test CLI for Fase 0: connect to a JDBC database and run one SQL statement,
 * printing the streamed result.
 *
 * Usage:
 *   sd-runner-cli --url jdbc:mysql://host:3306 --user root --pass dev --sql "SELECT 1"
 *                 [--driver com.mysql.cj.jdbc.Driver] [--modifying] [--limit N]
 */
public final class Main {

    public static void main(String[] args) {
        Map<String, String> opts = parse(args);
        String url = require(opts, "url");
        String user = opts.getOrDefault("user", "");
        String pass = opts.getOrDefault("pass", "");
        String driver = opts.get("driver");
        String sql = require(opts, "sql");
        boolean modifying = opts.containsKey("modifying");
        Integer limit = opts.containsKey("limit") ? Integer.parseInt(opts.get("limit")) : null;

        ConnectionEngine engine = new ConnectionEngine();
        RunningQueries running = new RunningQueries();
        String execId = UUID.randomUUID().toString();

        int[] rows = {0};
        boolean[] failed = {false};

        try (ConnectionEngine.DisposableDataSource ds = engine.createDisposable(url, user, pass, driver)) {
            QueryRunner.runQuery(
                    modifying, sql, ds.dataSource(), running, execId, limit, null,
                    (n, columns) -> System.out.println(String.join(" | ", columns)),
                    (n, row) -> {
                        System.out.println(String.join(" | ", row));
                        rows[0]++;
                    },
                    () -> System.out.println("-- done (" + rows[0] + " row(s)) --"),
                    e -> {
                        failed[0] = true;
                        System.err.println("ERROR: " + e.getMessage());
                    }
            );
        } finally {
            engine.close();
        }
        System.exit(failed[0] ? 1 : 0);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> map = new HashMap<>();
        List<String> flags = List.of("modifying");
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            String key = args[i].substring(2);
            if (flags.contains(key)) {
                map.put(key, "true");
            } else if (i + 1 < args.length) {
                map.put(key, args[++i]);
            }
        }
        return map;
    }

    private static String require(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null || value.isBlank()) {
            System.err.println("Missing required --" + key);
            System.exit(2);
        }
        return value;
    }
}
