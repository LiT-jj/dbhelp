package com.dbhelp.service.metadata.dialect;

import com.dbhelp.dto.metadata.CatalogEntry;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.TableEntry;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL：逻辑「库」对应实例上的 database；须连到具体 database 后再枚举 schema/table。
 */
public class PostgresqlMetadataDialect implements MetadataDialect {

    @Override
    public boolean reconnectPerCatalogForTables() {
        return true;
    }

    @Override
    public List<CatalogEntry> listCatalogs(Connection connection) throws SQLException {
        List<CatalogEntry> out = new ArrayList<CatalogEntry>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT datname FROM pg_database WHERE datallowconn AND NOT datistemplate ORDER BY datname")) {
            while (rs.next()) {
                out.add(new CatalogEntry(rs.getString(1)));
            }
        }
        return out;
    }

    @Override
    public List<TableEntry> listTables(Connection connection, String catalog) throws SQLException {
        List<TableEntry> list = new ArrayList<TableEntry>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                             + "WHERE table_schema NOT IN ('pg_catalog','information_schema') "
                             + "ORDER BY table_schema, table_name")) {
            while (rs.next()) {
                String schema = rs.getString(1);
                String name = rs.getString(2);
                String type = rs.getString(3);
                list.add(new TableEntry(catalog, schema, name, type));
            }
        }
        return list;
    }

    @Override
    public List<ColumnEntry> listColumns(Connection connection, String catalog, String schema, String table)
            throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        String schemaPattern = schema != null && !schema.isEmpty() ? schema : "public";
        return JdbcMetadataSupport.readColumns(md, null, schemaPattern, table);
    }
}
