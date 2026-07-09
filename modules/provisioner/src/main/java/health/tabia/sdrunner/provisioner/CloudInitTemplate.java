package health.tabia.sdrunner.provisioner;

/**
 * Generates a cloud-init user-data document that installs MySQL on a VM, sets the root
 * password, creates an initial database and applies a seed. Pure function (unit-testable);
 * the actual VM launch is delegated to a configurable launcher (see {@link VmMysqlProvisioner}).
 */
public final class CloudInitTemplate {

    private CloudInitTemplate() {
    }

    public static String render(EnvironmentSpec spec) {
        String db = spec.database() == null ? "" : spec.database();
        String seed = spec.seedSql() == null ? "" : spec.seedSql();
        StringBuilder sb = new StringBuilder();
        sb.append("#cloud-config\n");
        sb.append("package_update: true\n");
        sb.append("packages:\n  - mysql-server\n");
        sb.append("write_files:\n");
        sb.append("  - path: /root/seed.sql\n    permissions: '0600'\n    content: |\n");
        sb.append("      CREATE DATABASE IF NOT EXISTS ").append(db.isBlank() ? "app" : db).append(";\n");
        sb.append("      USE ").append(db.isBlank() ? "app" : db).append(";\n");
        for (String line : seed.split("\n")) {
            sb.append("      ").append(line).append('\n');
        }
        sb.append("runcmd:\n");
        // Allow remote connections and set the root password.
        sb.append("  - sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf || true\n");
        sb.append("  - systemctl enable --now mysql\n");
        sb.append("  - mysql -e \"ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '")
                .append(spec.rootPassword()).append("';\"\n");
        sb.append("  - mysql -e \"CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '")
                .append(spec.rootPassword()).append("'; GRANT ALL ON *.* TO 'root'@'%'; FLUSH PRIVILEGES;\"\n");
        sb.append("  - mysql -uroot -p").append(spec.rootPassword()).append(" < /root/seed.sql\n");
        return sb.toString();
    }
}
