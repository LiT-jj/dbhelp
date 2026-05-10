package com.dbhelp.service.metadata;

import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.entity.JdbcConnection;

import java.util.Locale;
import java.util.Map;

/**
 * 已解析的 JDBC 连接参数（用于打开物理连接）。
 */
public class ResolvedConnection {

    private final String urlTemplate;
    private final String host;
    private final int port;
    private final String databaseName;
    private final String driverClass;
    private final String username;
    private final String password;
    /** 与 database_type.code 对齐，大写 */
    private final String dbType;

    public ResolvedConnection(
            String urlTemplate,
            String host,
            int port,
            String databaseName,
            String driverClass,
            String username,
            String password,
            String dbType) {
        this.urlTemplate = urlTemplate;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.driverClass = driverClass;
        this.username = username;
        this.password = password;
        this.dbType = dbType;
    }

    public static ResolvedConnection fromMap(Map<String, Object> m) {
        String dbType = String.valueOf(m.get("dbType")).trim().toUpperCase(Locale.ROOT);
        return new ResolvedConnection(
                String.valueOf(m.get("urlTemplate")),
                String.valueOf(m.get("host")),
                Integer.parseInt(String.valueOf(m.get("port"))),
                String.valueOf(m.get("databaseName")),
                String.valueOf(m.get("driverClass")),
                String.valueOf(m.get("username")),
                String.valueOf(m.get("password")),
                dbType);
    }

    public static ResolvedConnection fromPayload(ConnectionPayload p) {
        String dbType = p.getDbType().trim().toUpperCase(Locale.ROOT);
        return new ResolvedConnection(
                p.getUrlTemplate(),
                p.getHost(),
                p.getPort(),
                p.getDatabaseName(),
                p.getDriverClass(),
                p.getUsername(),
                p.getPassword(),
                dbType);
    }

    public static ResolvedConnection fromEntity(JdbcConnection e) {
        String dbType = e.getDbType() == null ? "" : e.getDbType().trim().toUpperCase(Locale.ROOT);
        return new ResolvedConnection(
                e.getUrlTemplate(),
                e.getHost(),
                e.getPort(),
                e.getDatabaseName(),
                e.getDriverClass(),
                e.getUsername(),
                e.getPassword(),
                dbType);
    }

    public String getJdbcUrl() {
        return JdbcUrlBuilder.build(urlTemplate, host, port, databaseName);
    }

    public ResolvedConnection withDatabase(String newDatabase) {
        return new ResolvedConnection(
                urlTemplate, host, port, newDatabase,
                driverClass, username, password, dbType);
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDbType() {
        return dbType;
    }
}
