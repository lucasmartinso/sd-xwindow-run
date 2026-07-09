package health.tabia.sdrunner.provisioner;

import health.tabia.sdrunner.core.ConnectionEngine;
import health.tabia.sdrunner.core.QueryRunner;
import health.tabia.sdrunner.core.RunningQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Provisions MySQL as a Docker container with a persistent named volume, waits until it is
 * ready, and optionally seeds it. Uses the local `docker` CLI via ProcessBuilder.
 *
 * NOTE: running this from inside a container requires the Docker socket to be mounted
 * (docker-out-of-docker) — a security-sensitive choice; see Fase 3/4 plans.
 */
public class DockerMysqlProvisioner implements MysqlProvisioner {
    private static final Logger log = LoggerFactory.getLogger(DockerMysqlProvisioner.class);
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private final ConnectionEngine engine = new ConnectionEngine();

    @Override
    public ProvisionResult create(EnvironmentSpec spec) {
        int port = spec.hostPort() > 0 ? spec.hostPort() : freePort();
        String image = "mysql:" + spec.mysqlVersion();

        run(true, "docker", "volume", "create", spec.volumeName());

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "-d",
                "--name", spec.containerName(),
                "--label", "sd-runner=" + spec.name(),
                "-e", "MYSQL_ROOT_PASSWORD=" + spec.rootPassword(),
                "-p", port + ":3306",
                "-v", spec.volumeName() + ":/var/lib/mysql"));
        if (spec.database() != null && !spec.database().isBlank()) {
            cmd.add("-e");
            cmd.add("MYSQL_DATABASE=" + spec.database());
        }
        cmd.add(image);

        Result r = run(false, cmd.toArray(new String[0]));
        if (r.exit != 0) {
            throw new RuntimeException("docker run failed: " + r.output);
        }
        String containerId = r.output.trim();
        log.info("Started {} ({}) on port {}", spec.containerName(), shortId(containerId), port);

        ProvisionResult result = new ProvisionResult(
                spec.name(), containerId, "127.0.0.1", port, spec.rootPassword(), spec.database());

        awaitReady(result, 120);
        if (spec.seedSql() != null && !spec.seedSql().isBlank()) {
            seed(result, spec.seedSql());
        }
        return result;
    }

    private void awaitReady(ProvisionResult r, int timeoutSeconds) {
        String url = "jdbc:mysql://" + r.host() + ":" + r.port()
                + "/?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (ConnectionEngine.DisposableDataSource ds =
                         engine.createDisposable(url, "root", r.rootPassword(), DRIVER);
                 Connection c = ds.dataSource().getConnection()) {
                if (c.isValid(3)) {
                    log.info("MySQL ready for env {}", r.name());
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            sleep(2000);
        }
        throw new RuntimeException("MySQL not ready in time for env " + r.name(), last);
    }

    private void seed(ProvisionResult r, String seedSql) {
        RunningQueries running = new RunningQueries();
        try (ConnectionEngine.DisposableDataSource ds =
                     engine.createDisposable(r.jdbcUrl(), "root", r.rootPassword(), DRIVER)) {
            for (String stmt : seedSql.split(";")) {
                String sql = stmt.strip();
                if (sql.isEmpty()) {
                    continue;
                }
                Exception[] err = {null};
                QueryRunner.runQuery(true, sql, ds.dataSource(), running,
                        UUID.randomUUID().toString(), null, null,
                        (n, cols) -> {}, (n, row) -> {}, () -> {}, e -> err[0] = e);
                if (err[0] != null) {
                    throw new RuntimeException("Seed failed on: " + sql, err[0]);
                }
            }
            log.info("Seed applied for env {}", r.name());
        }
    }

    @Override
    public void stop(String name) {
        run(true, "docker", "stop", "sdr-env-" + name);
    }

    @Override
    public void remove(String name, boolean removeVolume) {
        run(true, "docker", "rm", "-f", "sdr-env-" + name);
        if (removeVolume) {
            run(true, "docker", "volume", "rm", "sdr-" + name + "-data");
        }
    }

    @Override
    public boolean isRunning(String name) {
        Result r = run(true, "docker", "ps", "-q", "-f", "name=sdr-env-" + name);
        return r.exit == 0 && !r.output.isBlank();
    }

    // ---- helpers ----

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free port", e);
        }
    }

    private static String shortId(String id) {
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Result(int exit, String output) {
    }

    private static Result run(boolean tolerant, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int exit = p.waitFor();
            if (exit != 0 && !tolerant) {
                log.debug("Command {} exited {}: {}", Arrays.toString(cmd), exit, sb);
            }
            return new Result(exit, sb.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (tolerant) {
                return new Result(-1, String.valueOf(e.getMessage()));
            }
            throw new RuntimeException("Failed to run: " + Arrays.toString(cmd), e);
        }
    }
}
