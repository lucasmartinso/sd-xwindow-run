package health.tabia.sdrunner.provisioner;

import health.tabia.sdrunner.core.model.ConnectionProfile;

import java.util.UUID;

/** Outcome of provisioning: how to reach the new MySQL. */
public record ProvisionResult(
        String name,
        String containerId,
        String host,
        int port,
        String rootPassword,
        String database
) {
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + (database == null ? "" : database)
                + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    }

    /** Build a savable connection profile (Fase 2) pointing at this environment. */
    public ConnectionProfile toProfile() {
        ConnectionProfile p = new ConnectionProfile(UUID.randomUUID().toString(), "env: " + name);
        p.setHost(host);
        p.setPort(port);
        p.setDatabase(database == null ? "" : database);
        p.setUser("root");
        p.setPassword(rootPassword);
        return p;
    }
}
