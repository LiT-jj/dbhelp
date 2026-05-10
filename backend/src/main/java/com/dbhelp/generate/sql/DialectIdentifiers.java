package com.dbhelp.generate.sql;

import java.util.Locale;

/**
 * 限定符引用（MySQL 反引号 / Oracle 双引号大写习惯）。
 */
public final class DialectIdentifiers {

    private DialectIdentifiers() {
    }

    public static String quoteIdentifier(String dbTypeUpper, String name) {
        if (name == null) {
            return "";
        }
        String dt = dbTypeUpper == null ? "" : dbTypeUpper.trim().toUpperCase(Locale.ROOT);
        if ("ORACLE".equals(dt)) {
            return "\"" + name.replace("\"", "\"\"") + "\"";
        }
        return "`" + name.replace("`", "``") + "`";
    }

    public static String qualifiedTable(String dbTypeUpper, String catalog, String schema, String table) {
        String dt = dbTypeUpper == null ? "" : dbTypeUpper.trim().toUpperCase(Locale.ROOT);
        if (table == null || table.isEmpty()) {
            return "";
        }
        if ("ORACLE".equals(dt)) {
            String sch = (schema != null && !schema.isEmpty()) ? schema : catalog;
            if (sch != null && !sch.isEmpty()) {
                return quoteIdentifier(dt, sch) + "." + quoteIdentifier(dt, table);
            }
            return quoteIdentifier(dt, table);
        }
        if (catalog != null && !catalog.isEmpty()) {
            return quoteIdentifier(dt, catalog) + "." + quoteIdentifier(dt, table);
        }
        return quoteIdentifier(dt, table);
    }
}
