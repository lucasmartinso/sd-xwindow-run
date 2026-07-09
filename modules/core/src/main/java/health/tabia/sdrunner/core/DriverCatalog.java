package health.tabia.sdrunner.core;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Lists JDBC driver class names available on the classpath.
 * Port of the runner's DriverUtils.
 */
public final class DriverCatalog {

    private DriverCatalog() {
    }

    public static List<String> availableDrivers() {
        // Ensure bundled drivers are registered before enumerating.
        touch("com.mysql.cj.jdbc.Driver");
        touch("org.sqlite.JDBC");

        List<String> names = new ArrayList<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            names.add(drivers.nextElement().getClass().getName());
        }
        return names;
    }

    private static void touch(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException ignored) {
            // driver not on classpath; skip
        }
    }
}
