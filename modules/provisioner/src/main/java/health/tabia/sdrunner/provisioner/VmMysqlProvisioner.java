package health.tabia.sdrunner.provisioner;

import health.tabia.sdrunner.core.ConnectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

/**
 * Provisions MySQL inside a VM. Renders a cloud-init document and delegates the actual VM
 * launch to a configurable command (env SD_VM_LAUNCHER), so it works with whatever the host
 * provides (multipass, vagrant, cloud CLI, libvirt...). The launcher receives the cloud-init
 * file path via the {cloudinit} placeholder and the env name via {name}; it must print the
 * VM IP as the last line of stdout.
 *
 * If SD_VM_LAUNCHER is not set, {@link #create} throws with guidance (no hypervisor assumed).
 */
public class VmMysqlProvisioner implements MysqlProvisioner {
    private static final Logger log = LoggerFactory.getLogger(VmMysqlProvisioner.class);
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private final ConnectionEngine engine = new ConnectionEngine();
    private final String launcher;

    public VmMysqlProvisioner() {
        this(System.getenv("SD_VM_LAUNCHER"));
    }

    public VmMysqlProvisioner(String launcher) {
        this.launcher = launcher;
    }

    @Override
    public ProvisionResult create(EnvironmentSpec spec) {
        if (launcher == null || launcher.isBlank()) {
            throw new UnsupportedOperationException(
                    "VM mode requires SD_VM_LAUNCHER (e.g. 'multipass launch --name {name} "
                            + "--cloud-init {cloudinit} ... && <print-ip>'). Cloud-init is generated for you.");
        }
        try {
            Path cloudInit = Files.createTempFile("sdr-cloudinit-" + spec.name(), ".yaml");
            Files.writeString(cloudInit, CloudInitTemplate.render(spec));
            String cmd = launcher
                    .replace("{cloudinit}", cloudInit.toAbsolutePath().toString())
                    .replace("{name}", spec.name());
            log.info("Launching VM for env {} via configured launcher", spec.name());
            String ip = lastLine(run("bash", "-lc", cmd));
            if (ip.isBlank()) {
                throw new RuntimeException("Launcher did not return a VM IP");
            }
            ProvisionResult result = new ProvisionResult(
                    spec.name(), "vm:" + spec.name(), ip.trim(), 3306, spec.rootPassword(), spec.database());
            awaitReady(result, 300);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare cloud-init", e);
        }
    }

    private void awaitReady(ProvisionResult r, int timeoutSeconds) {
        String url = "jdbc:mysql://" + r.host() + ":" + r.port()
                + "/?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            try (ConnectionEngine.DisposableDataSource ds =
                         engine.createDisposable(url, "root", r.rootPassword(), DRIVER);
                 Connection c = ds.dataSource().getConnection()) {
                if (c.isValid(3)) {
                    log.info("VM MySQL ready for env {} at {}", r.name(), r.host());
                    return;
                }
            } catch (Exception ignored) {
                // keep waiting
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new RuntimeException("VM MySQL not ready in time for env " + r.name());
    }

    @Override
    public void stop(String name) {
        runOptional(System.getenv("SD_VM_STOP"), name);
    }

    @Override
    public void remove(String name, boolean removeVolume) {
        runOptional(System.getenv("SD_VM_REMOVE"), name);
    }

    @Override
    public boolean isRunning(String name) {
        String cmd = System.getenv("SD_VM_STATUS");
        if (cmd == null || cmd.isBlank()) {
            return false;
        }
        try {
            return !run("bash", "-lc", cmd.replace("{name}", name)).isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private void runOptional(String template, String name) {
        if (template == null || template.isBlank()) {
            log.warn("No VM command configured for env {}", name);
            return;
        }
        try {
            run("bash", "-lc", template.replace("{name}", name));
        } catch (Exception e) {
            log.warn("VM command failed for {}: {}", name, e.getMessage());
        }
    }

    private static String lastLine(String output) {
        String[] lines = output.strip().split("\n");
        return lines.length == 0 ? "" : lines[lines.length - 1];
    }

    private static String run(String... cmd) {
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
            p.waitFor();
            return sb.toString();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to run: " + String.join(" ", cmd), e);
        }
    }
}
