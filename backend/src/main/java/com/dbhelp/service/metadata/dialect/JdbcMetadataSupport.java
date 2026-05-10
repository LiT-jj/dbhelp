package com.dbhelp.service.metadata.dialect;

import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.TableEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class JdbcMetadataSupport {

    static final String[] TABLE_TYPES = new String[]{"TABLE", "VIEW"};

    private JdbcMetadataSupport() {
    }

    static List<String> readCatalogNames(DatabaseMetaData md) throws SQLException {
        Set<String> names = new LinkedHashSet<String>();
        try (ResultSet rs = md.getCatalogs()) {
            while (rs.next()) {
                String cat = rs.getString("TABLE_CAT");
                if (cat != null && !cat.isEmpty()) {
                    names.add(cat);
                }
            }
        }
        return new ArrayList<String>(names);
    }

    static List<TableEntry> readTables(DatabaseMetaData md, String catalog, String schemaPattern) throws SQLException {
        List<TableEntry> list = new ArrayList<TableEntry>();
        try (ResultSet rs = md.getTables(catalog, schemaPattern, "%", TABLE_TYPES)) {
            while (rs.next()) {
                String cat = rs.getString("TABLE_CAT");
                String schem = rs.getString("TABLE_SCHEM");
                String name = rs.getString("TABLE_NAME");
                String type = rs.getString("TABLE_TYPE");
                list.add(new TableEntry(cat, schem, name, type));
            }
        }
        return list;
    }

    static List<ColumnEntry> readColumns(DatabaseMetaData md, String catalog, String schemaPattern, String table)
            throws SQLException {
        List<ColumnEntry> list = new ArrayList<ColumnEntry>();
        try (ResultSet rs = md.getColumns(catalog, schemaPattern, table, "%")) {
            while (rs.next()) {
                list.add(readColumnRow(rs));
            }
        }
        return list;
    }

    static ColumnEntry readColumnRow(ResultSet rs) throws SQLException {
        String name = rs.getString("COLUMN_NAME");
        int jdbcType = rs.getInt("DATA_TYPE");
        String typeName = rs.getString("TYPE_NAME");
        int size = rs.getInt("COLUMN_SIZE");
        if (rs.wasNull()) {
            size = 0;
        }
        int digits = rs.getInt("DECIMAL_DIGITS");
        if (rs.wasNull()) {
            digits = 0;
        }
        int nullableCode = rs.getInt("NULLABLE");
        boolean nullable = nullableCode == DatabaseMetaData.columnNullable;
        int ord = rs.getInt("ORDINAL_POSITION");
        String def = rs.getString("COLUMN_DEF");
        String remarks = rs.getString("REMARKS");

        /*
        * JDBC 的 java.sql.Types 只有：DATE、TIME、TIMESTAMP，没有 YEAR。
        * MySQL 有专属的 YEAR 类型（1901–2155），但 JDBC 标准不认识它。
        * 驱动为了 “能跑”，只能把它映射到最近的标准类型：DATE。
        * */
        if (typeName.equalsIgnoreCase("year")) {
            jdbcType = Types.SMALLINT;
        }
        return new ColumnEntry(name, jdbcType, typeName, size, digits, nullable, ord, def, remarks);
    }
}
