package com.dbhelp.generate.sink;

import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.generate.sql.DialectIdentifiers;
import com.dbhelp.service.metadata.ResolvedConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 按方言拼装 INSERT 并 {@link PreparedStatement#executeBatch()}。
 */
public final class JdbcBatchInsertSink {

    private static final Logger log = LoggerFactory.getLogger(JdbcBatchInsertSink.class);

    private JdbcBatchInsertSink() {
    }

    public static int insertBatch(
            ResolvedConnection rc,
            String dbTypeUpper,
            String catalog,
            String schema,
            String table,
            List<ColumnEntry> columns,
            List<Map<String, Object>> rows) throws SQLException {
        if (rows == null || rows.isEmpty() || columns == null || columns.isEmpty()) {
            return 0;
        }
        List<ColumnEntry> useCols = new ArrayList<ColumnEntry>();
        for (ColumnEntry c : columns) {
            if (c == null || c.getName() == null) {
                continue;
            }
            useCols.add(c);
        }
        if (useCols.isEmpty()) {
            return 0;
        }
        String dt = dbTypeUpper == null ? "" : dbTypeUpper.trim().toUpperCase(Locale.ROOT);
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(DialectIdentifiers.qualifiedTable(dt, catalog, schema, table));
        sql.append(" (");
        for (int i = 0; i < useCols.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(DialectIdentifiers.quoteIdentifier(dt, useCols.get(i).getName()));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < useCols.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        final String sqlText = sql.toString();
        try {
            Class.forName(rc.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("找不到 JDBC 驱动类: " + rc.getDriverClass(), e);
        }
        Properties props = new Properties();
        props.setProperty("user", rc.getUsername());
        props.setProperty("password", rc.getPassword());
        try (Connection conn = DriverManager.getConnection(rc.getJdbcUrl(), props);
             PreparedStatement ps = conn.prepareStatement(sqlText)) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < useCols.size(); i++) {
                    String colName = useCols.get(i).getName();
                    ColumnEntry ce = useCols.get(i);
                    Object raw = row.get(colName);
                    ps.setObject(i + 1, JdbcWriteValueNormalizer.normalize(dt, ce, raw));
                }
                ps.addBatch();
            }
            try {
                int[] res = ps.executeBatch();
                int n = 0;
                for (int v : res) {
                    if (v >= 0) {
                        n += v;
                    } else if (v == java.sql.Statement.SUCCESS_NO_INFO) {
                        n++;
                    }
                }
                return n;
            } catch (SQLException ex) {
                logBatchFailure(sqlText, useCols, rows, ex);
                throw wrapSqlException(sqlText, ex);
            }
        }
    }

    private static void logBatchFailure(
            String sqlText, List<ColumnEntry> useCols, List<Map<String, Object>> rows, SQLException ex) {
        log.error("JDBC 批量插入失败: {}", ex.getMessage());
        log.error("SQL: {}", sqlText);
        if (rows != null && !rows.isEmpty()) {
            log.error("首行绑定值(便于核对类型/长度): {}", formatRowSnippet(useCols, rows.get(0)));
        }
        if (rows != null && rows.size() > 1) {
            log.error("批大小={}，若错误指向某一行的列，请结合业务核对该行数据。", rows.size());
        }
    }

    /** 单行摘要，避免日志过长 */
    private static String formatRowSnippet(List<ColumnEntry> useCols, Map<String, Object> row) {
        if (row == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < useCols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = useCols.get(i).getName();
            Object v = row.get(name);
            sb.append(name).append('=');
            if (v == null) {
                sb.append("null");
            } else {
                String s = String.valueOf(v);
                if (s.length() > 120) {
                    s = s.substring(0, 117) + "...";
                }
                sb.append(s);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static SQLException wrapSqlException(String sqlText, SQLException cause) {
        return new SQLException(cause.getMessage() + " | SQL: " + sqlText, cause);
    }
}
