package health.tabia.sdrunner.provisioner;

/** Provisions/manages a MySQL environment (container or, in Fase 4, a VM). */
public interface MysqlProvisioner {

    ProvisionResult create(EnvironmentSpec spec);

    void stop(String name);

    void remove(String name, boolean removeVolume);

    /** @return true if a container for this environment is running. */
    boolean isRunning(String name);
}
