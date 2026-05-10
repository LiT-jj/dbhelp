package com.dbhelp.service.metadata;

/**
 * 将 {@code urlTemplate} 中的占位符替换为实际连接参数。
 */
public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    public static String build(String urlTemplate, String host, int port, String database) {
        if (urlTemplate == null) {
            throw new IllegalArgumentException("urlTemplate 不能为空");
        }
        return urlTemplate
                .replace("{host}", host == null ? "" : host)
                .replace("{port}", String.valueOf(port))
                .replace("{database}", database == null ? "" : database);
    }
}
