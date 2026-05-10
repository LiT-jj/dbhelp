package com.dbhelp.service;

import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.service.metadata.ConnectionPayloadResolver;
import com.dbhelp.service.metadata.JdbcUrlBuilder;
import com.dbhelp.service.metadata.ResolvedConnection;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * 使用 JDBC 校验连接是否可用（与元数据服务一致的 URL 拼装方式）。
 */
@Component
public class JdbcConnectionTester {

    private final ConnectionPayloadResolver connectionPayloadResolver;

    public JdbcConnectionTester(ConnectionPayloadResolver connectionPayloadResolver) {
        this.connectionPayloadResolver = connectionPayloadResolver;
    }

    public void verify(ConnectionPayload payload) throws SQLException {
        ResolvedConnection rc = connectionPayloadResolver.resolve(payload);
        try {
            Class.forName(rc.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("找不到 JDBC 驱动类: " + rc.getDriverClass(), e);
        }
        Properties props = new Properties();
        props.setProperty("user", rc.getUsername());
        props.setProperty("password", rc.getPassword());
        String url = JdbcUrlBuilder.build(rc.getUrlTemplate(), rc.getHost(), rc.getPort(), rc.getDatabaseName());
        try (Connection ignored = DriverManager.getConnection(url, props)) {
            if (!ignored.isValid(5)) {
                throw new SQLException("连接无效");
            }
        }
    }

    /** 从通用 JSON Map 组装 {@link ConnectionPayload}（供 /connections/test 使用）。 */
    public static ConnectionPayload payloadFromMap(Map<String, Object> map) {
        ConnectionPayload p = new ConnectionPayload();
        if (map == null) {
            return p;
        }
        Object cid = map.get("connectionId");
        if (cid != null) {
            p.setConnectionId(((Number) cid).longValue());
        }
        putStr(map, "dbType", p::setDbType);
        putStr(map, "host", p::setHost);
        Object port = map.get("port");
        if (port != null) {
            p.setPort(((Number) port).intValue());
        }
        putStr(map, "databaseName", p::setDatabaseName);
        putStr(map, "username", p::setUsername);
        putStr(map, "password", p::setPassword);
        putStr(map, "driverClass", p::setDriverClass);
        putStr(map, "urlTemplate", p::setUrlTemplate);
        return p;
    }

    private static void putStr(Map<String, Object> map, String key, java.util.function.Consumer<String> setter) {
        Object v = map.get(key);
        if (v != null) {
            setter.accept(String.valueOf(v));
        }
    }
}
