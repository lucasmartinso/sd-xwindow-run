package health.tabia.sdrunner.provisioner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VmModeTest {

    @Test
    void cloudInitContainsCoreDirectives() {
        EnvironmentSpec spec = new EnvironmentSpec("prod", "8", 0, "s3cr3t", "app",
                "CREATE TABLE t(id INT);");
        String yaml = CloudInitTemplate.render(spec);

        assertTrue(yaml.startsWith("#cloud-config"));
        assertTrue(yaml.contains("mysql-server"), "should install mysql-server");
        assertTrue(yaml.contains("CREATE DATABASE IF NOT EXISTS app"), "should create the database");
        assertTrue(yaml.contains("s3cr3t"), "should set root password");
        assertTrue(yaml.contains("CREATE TABLE t(id INT);"), "should embed the seed");
    }

    @Test
    void vmProvisionerRequiresLauncher() {
        VmMysqlProvisioner p = new VmMysqlProvisioner((String) null);
        EnvironmentSpec spec = new EnvironmentSpec("x", "8", 0, "dev", "app", "");
        assertThrows(UnsupportedOperationException.class, () -> p.create(spec));
    }
}
