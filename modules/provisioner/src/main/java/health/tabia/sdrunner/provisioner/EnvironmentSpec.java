package health.tabia.sdrunner.provisioner;

/**
 * Desired state for a provisioned MySQL environment.
 *
 * @param name         logical name (used for container name, label and volume)
 * @param mysqlVersion image tag, e.g. "8"
 * @param hostPort     published host port; 0 = pick a free one
 * @param rootPassword MySQL root password
 * @param database     optional initial database (MYSQL_DATABASE)
 * @param seedSql      optional seed script (statements separated by ';')
 */
public record EnvironmentSpec(
        String name,
        String mysqlVersion,
        int hostPort,
        String rootPassword,
        String database,
        String seedSql
) {
    public String containerName() {
        return "sdr-env-" + name;
    }

    public String volumeName() {
        return "sdr-" + name + "-data";
    }
}
