package com.dbhelp.generate.types;

import com.dbhelp.dto.metadata.ColumnEntry;

import java.sql.Types;
import java.util.Locale;

/**
 * JDBC 元数据 + 类型名 → {@link SqlCanonicalType}（首期覆盖 MySQL / Oracle 常见类型）。
 */
public final class CanonicalTypeResolver {

    private CanonicalTypeResolver() {
    }

    public static SqlCanonicalType resolve(String dbTypeUpper, ColumnEntry col) {
        if (col == null) {
            return SqlCanonicalType.UNKNOWN;
        }
        String dt = dbTypeUpper == null ? "" : dbTypeUpper.trim().toUpperCase(Locale.ROOT);
        String tn = col.getTypeName() == null ? "" : col.getTypeName().toUpperCase(Locale.ROOT);
        Integer jdbc = col.getJdbcType();

        // 部分驱动对 DATETIME/DATE 的 DATA_TYPE 不统一，优先按类型名识别（避免被当成 BIGINT/UNKNOWN 后写入毫秒数字串）
        if (tn.contains("DATETIME") || tn.contains("TIMESTAMP")) {
            return SqlCanonicalType.DATETIME;
        }
        if (tn.contains("DATE") && !tn.contains("DATETIME")) {
            return SqlCanonicalType.DATE;
        }
        if ("TIME".equals(tn) || (tn.startsWith("TIME") && !tn.contains("STAMP"))) {
            return SqlCanonicalType.TIME;
        }

        if (tn.contains("BLOB") || tn.contains("RAW") || tn.contains("BINARY") || tn.contains("VARBINARY")) {
            return SqlCanonicalType.BYTES;
        }
        if ("ORACLE".equals(dt) && "NUMBER".equals(tn)) {
            Integer scale = col.getDecimalDigits();
            if (scale != null && scale > 0) {
                return SqlCanonicalType.DECIMAL;
            }
            return SqlCanonicalType.INT64;
        }
        if (tn.contains("CHAR") || tn.contains("CLOB") || tn.contains("TEXT") || tn.contains("JSON")) {
            return SqlCanonicalType.STRING;
        }
        if (jdbc != null) {
            switch (jdbc) {
                case Types.BIT:
                case Types.BOOLEAN:
                    return SqlCanonicalType.BOOLEAN;
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                    return SqlCanonicalType.INT32;
                case Types.BIGINT:
                    return SqlCanonicalType.INT64;
                case Types.DECIMAL:
                case Types.NUMERIC:
                    return SqlCanonicalType.DECIMAL;
                case Types.REAL:
                    return SqlCanonicalType.FLOAT;
                case Types.FLOAT:
                case Types.DOUBLE:
                    return SqlCanonicalType.DOUBLE;
                case Types.DATE:
                    return SqlCanonicalType.DATE;
                case Types.TIME:
                case Types.TIME_WITH_TIMEZONE:
                    return SqlCanonicalType.TIME;
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    return SqlCanonicalType.DATETIME;
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                case Types.BLOB:
                    return SqlCanonicalType.BYTES;
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.LONGVARCHAR:
                case Types.NCHAR:
                case Types.NVARCHAR:
                case Types.CLOB:
                case Types.NCLOB:
                    return SqlCanonicalType.STRING;
                default:
                    break;
            }
        }
        return SqlCanonicalType.UNKNOWN;
    }
}
