package com.dbhelp.service.constraint.mysql;

import com.dbhelp.dto.constraint.RigidColumnConstraintDto;
import com.dbhelp.dto.constraint.RigidConstraintItemDto;
import com.dbhelp.dto.constraint.RigidConstraintParseResponse;
import com.dbhelp.dto.constraint.RigidTableRefDto;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.service.metadata.ResolvedConnection;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * 从 MySQL {@code information_schema.COLUMNS} 推导数值 RANGE、ENUM/SET 的 EQUAL、NOT_NULL。
 */
public final class MysqlRigidConstraintResolver {

    private MysqlRigidConstraintResolver() {
    }

    public static RigidConstraintParseResponse resolve(ResolvedConnection rc, ColumnsMetadataRequest req)
            throws SQLException {
        String catalog = req.getCatalog().trim();
        String table = req.getTable().trim();
        String schema = req.getSchema();
        ResolvedConnection use = rc.withDatabase(catalog);

        RigidConstraintParseResponse out = new RigidConstraintParseResponse();
        out.setDbType("MYSQL");
        out.setTableRef(new RigidTableRefDto(catalog, schema, table));

        try (Connection conn = open(use)) {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, "
                    + "NUMERIC_PRECISION, NUMERIC_SCALE "
                    + "FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, catalog);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.getColumns().add(buildColumn(rs));
                    }
                }
            }
        }
        return out;
    }

    private static RigidColumnConstraintDto buildColumn(ResultSet rs) throws SQLException {
        String name = rs.getString("COLUMN_NAME");
        String dataType = rs.getString("DATA_TYPE");
        String columnType = rs.getString("COLUMN_TYPE");
        boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
        Integer prec = rs.getObject("NUMERIC_PRECISION") == null ? null : rs.getInt("NUMERIC_PRECISION");
        Integer scale = rs.getObject("NUMERIC_SCALE") == null ? null : rs.getInt("NUMERIC_SCALE");

        RigidColumnConstraintDto col = new RigidColumnConstraintDto();
        col.setName(name);
        col.setDataType(dataType);
        col.setColumnType(columnType);
        col.setNullable(nullable);
        col.setJdbcType(mapJdbcType(dataType, columnType));

        boolean unsigned = columnType != null && columnType.toLowerCase(Locale.ROOT).contains("unsigned");
        col.setUnsigned(unsigned);

        String dtLower = dataType == null ? "" : dataType.toLowerCase(Locale.ROOT);
        String ctLower = columnType == null ? "" : columnType.toLowerCase(Locale.ROOT);

        if (!nullable) {
            RigidConstraintItemDto nn = new RigidConstraintItemDto();
            nn.setKind("NOT_NULL");
            nn.setSource("COLUMN_DEFINITION");
            col.getConstraints().add(nn);
        }

        if (ctLower.startsWith("enum(")) {
            List<String> vals = EnumSetParser.parseEnumOrSetMembers(columnType, true);
            RigidConstraintItemDto eq = new RigidConstraintItemDto();
            eq.setKind("EQUAL");
            eq.setSource("COLUMN_DEFINITION");
            eq.getAllowedValues().addAll(vals);
            col.getConstraints().add(eq);
            return col;
        }
        if (ctLower.startsWith("set(")) {
            List<String> vals = EnumSetParser.parseEnumOrSetMembers(columnType, false);
            RigidConstraintItemDto eq = new RigidConstraintItemDto();
            eq.setKind("EQUAL");
            eq.setSource("COLUMN_DEFINITION");
            eq.setSubtype("SET");
            eq.getAllowedValues().addAll(vals);
            eq.setNotes("值为逗号分隔子集，成员须来自 allowedValues");
            col.getConstraints().add(eq);
            return col;
        }

        if ("decimal".equals(dtLower) || "numeric".equals(dtLower)) {
            addDecimalRange(col, columnType, prec, scale);
            return col;
        }
        if ("float".equals(dtLower) || "double".equals(dtLower) || "real".equals(dtLower)) {
            RigidConstraintItemDto r = new RigidConstraintItemDto();
            r.setKind("RANGE");
            r.setSource("COLUMN_DEFINITION");
            if ("float".equals(dtLower)) {
                r.setMin(String.valueOf(-Float.MAX_VALUE));
                r.setMax(String.valueOf(Float.MAX_VALUE));
            } else {
                r.setMin(String.valueOf(-Double.MAX_VALUE));
                r.setMax(String.valueOf(Double.MAX_VALUE));
            }
            r.setNotes("IEEE754 近似范围，严格边界以实例为准");
            col.getConstraints().add(r);
            return col;
        }

        if ("year".equals(dtLower)) {
            RigidConstraintItemDto r = new RigidConstraintItemDto();
            r.setKind("RANGE");
            r.setSource("COLUMN_DEFINITION");
            r.setMin("1901");
            r.setMax("2155");
            r.setNotes("MySQL YEAR(4) 典型范围");
            col.getConstraints().add(r);
            return col;
        }

        if ("bit".equals(dtLower)) {
            int bits = parseBitLength(columnType);
            if (bits > 0) {
                BigInteger max = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
                RigidConstraintItemDto r = new RigidConstraintItemDto();
                r.setKind("RANGE");
                r.setSource("COLUMN_DEFINITION");
                r.setMin("0");
                r.setMax(max.toString());
                col.getConstraints().add(r);
            }
            return col;
        }

        switch (dtLower) {
            case "tinyint":
                addIntegerRange(col, 8, unsigned);
                break;
            case "smallint":
                addIntegerRange(col, 16, unsigned);
                break;
            case "mediumint":
                addIntegerRange(col, 24, unsigned);
                break;
            case "int":
            case "integer":
                addIntegerRange(col, 32, unsigned);
                break;
            case "bigint":
                addBigIntRange(col, unsigned);
                break;
            default:
                break;
        }
        return col;
    }

    private static void addIntegerRange(RigidColumnConstraintDto col, int bits, boolean unsigned) {
        BigInteger maxSigned = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        BigInteger minSigned = BigInteger.ONE.shiftLeft(bits - 1).negate();
        RigidConstraintItemDto r = new RigidConstraintItemDto();
        r.setKind("RANGE");
        r.setSource("COLUMN_DEFINITION");
        if (unsigned) {
            r.setMin("0");
            r.setMax(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).toString());
        } else {
            r.setMin(minSigned.toString());
            r.setMax(maxSigned.toString());
        }
        col.getConstraints().add(r);
    }

    private static void addBigIntRange(RigidColumnConstraintDto col, boolean unsigned) {
        RigidConstraintItemDto r = new RigidConstraintItemDto();
        r.setKind("RANGE");
        r.setSource("COLUMN_DEFINITION");
        if (unsigned) {
            r.setMin("0");
            r.setMax("18446744073709551615");
        } else {
            r.setMin("-9223372036854775808");
            r.setMax("9223372036854775807");
        }
        col.getConstraints().add(r);
    }

    private static void addDecimalRange(RigidColumnConstraintDto col, String columnType, Integer prec, Integer scale) {
        int p = prec != null ? prec : 10;
        int s = scale != null ? scale : 0;
        if (columnType != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)(?:decimal|numeric)\\((\\d+)\\s*,\\s*(\\d+)\\)")
                    .matcher(columnType);
            if (m.find()) {
                p = Integer.parseInt(m.group(1));
                s = Integer.parseInt(m.group(2));
            }
        }
        int intDigits = Math.max(0, p - s);
        BigDecimal tenPow = BigDecimal.TEN.pow(intDigits);
        BigDecimal unit = s > 0 ? BigDecimal.ONE.scaleByPowerOfTen(-s) : BigDecimal.ONE;
        BigDecimal max = tenPow.subtract(unit);
        BigDecimal min = max.negate();

        RigidConstraintItemDto r = new RigidConstraintItemDto();
        r.setKind("RANGE");
        r.setSource("COLUMN_DEFINITION");
        r.setMin(min.toPlainString());
        r.setMax(max.toPlainString());
        r.setNotes("DECIMAL(" + p + "," + s + ") 闭区间");
        col.getConstraints().add(r);
    }

    private static int parseBitLength(String columnType) {
        if (columnType == null) {
            return 1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)bit\\((\\d+)\\)").matcher(columnType);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 1;
    }

    private static Integer mapJdbcType(String dataType, String columnType) {
        if (dataType == null) {
            return Types.OTHER;
        }
        String d = dataType.toLowerCase(Locale.ROOT);
        if (columnType != null && columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) {
            return Types.VARCHAR;
        }
        if (columnType != null && columnType.toLowerCase(Locale.ROOT).startsWith("set(")) {
            return Types.VARCHAR;
        }
        switch (d) {
            case "tinyint":
                return Types.TINYINT;
            case "smallint":
                return Types.SMALLINT;
            case "mediumint":
                return Types.INTEGER;
            case "int":
            case "integer":
                return Types.INTEGER;
            case "bigint":
                return Types.BIGINT;
            case "decimal":
            case "numeric":
                return Types.DECIMAL;
            case "float":
                return Types.FLOAT;
            case "double":
                return Types.DOUBLE;
            case "date":
                return Types.DATE;
            case "datetime":
            case "timestamp":
                return Types.TIMESTAMP;
            case "year":
                return Types.DATE;
            case "time":
                return Types.TIME;
            case "bit":
                return Types.BIT;
            case "char":
            case "varchar":
                return Types.VARCHAR;
            case "binary":
            case "varbinary":
                return Types.VARBINARY;
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
                return Types.BLOB;
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
                return Types.LONGVARCHAR;
            default:
                return Types.OTHER;
        }
    }

    private static Connection open(ResolvedConnection rc) throws SQLException {
        try {
            Class.forName(rc.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("找不到 JDBC 驱动类: " + rc.getDriverClass(), e);
        }
        Properties props = new Properties();
        props.setProperty("user", rc.getUsername());
        props.setProperty("password", rc.getPassword());
        return DriverManager.getConnection(rc.getJdbcUrl(), props);
    }

    /** 解析 enum/set 括号内列表 */
    static final class EnumSetParser {

        static List<String> parseEnumOrSetMembers(String columnType, boolean enumType) {
            List<String> out = new ArrayList<String>();
            if (columnType == null) {
                return out;
            }
            String lower = columnType.trim().toLowerCase(Locale.ROOT);
            String keyword = enumType ? "enum(" : "set(";
            int idx = lower.indexOf(keyword);
            if (idx < 0) {
                return out;
            }
            String body = extractBalancedParenBody(columnType, columnType.indexOf('(', idx));
            if (body == null) {
                return out;
            }
            for (String raw : splitQuotedCsv(body)) {
                String v = stripQuotes(raw.trim());
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
            return out;
        }

        private static String extractBalancedParenBody(String columnType, int openParenIdx) {
            int depth = 0;
            for (int i = openParenIdx; i < columnType.length(); i++) {
                char c = columnType.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return columnType.substring(openParenIdx + 1, i);
                    }
                }
            }
            return null;
        }

        private static List<String> splitQuotedCsv(String inner) {
            List<String> parts = new ArrayList<String>();
            StringBuilder cur = new StringBuilder();
            boolean inQuote = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\'') {
                    if (inQuote && i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                        cur.append('\'');
                        i++;
                    } else {
                        inQuote = !inQuote;
                    }
                } else if (c == ',' && !inQuote) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
            parts.add(cur.toString());
            return parts;
        }

        private static String stripQuotes(String token) {
            String t = token.trim();
            if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'")) {
                return t.substring(1, t.length() - 1).replace("''", "'");
            }
            return t;
        }
    }
}
