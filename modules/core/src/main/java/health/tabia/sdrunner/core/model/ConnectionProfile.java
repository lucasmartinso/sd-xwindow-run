package health.tabia.sdrunner.core.model;

/**
 * A database connection profile. In Fase 1 it lives only in memory; Fase 2 persists it
 * (with the password encrypted at rest).
 */
public class ConnectionProfile {
    private String id;
    private String name;
    private String host = "localhost";
    private int port = 3306;
    private String database = "";
    private String user = "root";
    private String password = "";
    private String driverClass = "com.mysql.cj.jdbc.Driver";
    private String extraParams = "allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";

    public ConnectionProfile() {
    }

    public ConnectionProfile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String jdbcUrl() {
        StringBuilder sb = new StringBuilder("jdbc:mysql://").append(host).append(':').append(port).append('/');
        if (database != null) {
            sb.append(database);
        }
        if (extraParams != null && !extraParams.isBlank()) {
            sb.append('?').append(extraParams);
        }
        return sb.toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }
    public String getExtraParams() { return extraParams; }
    public void setExtraParams(String extraParams) { this.extraParams = extraParams; }

    @Override
    public String toString() {
        return name != null ? name : jdbcUrl();
    }
}
