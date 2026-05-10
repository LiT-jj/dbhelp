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
 * SQL Server：catalog 对应数据库名；可用 sys.databases 列举库。
 */
public class SqlServerMetadataDialect implements MetadataDialect {

    @Override
    public boolean reconnectPerCatalogForTables() {
        return false;
    }

    @Override
    public List<CatalogEntry> listCatalogs(Connection connection) throws SQLException {
        List<CatalogEntry> out = new ArrayList<CatalogEntry>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sys.databases ORDER BY name")) {
            while (rs.next()) {
                out.add(new CatalogEntry(rs.getString(1)));
            }
            return out;
        } catch (SQLException ex) {
            DatabaseMetaData md = connection.getMetaData();
            for (String n : JdbcMetadataSupport.readCatalogNames(md)) {
                out.add(new CatalogEntry(n));
            }
            return out;
        }
    }

    @Override
    public List<TableEntry> listTables(Connection connection, String catalog) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        return JdbcMetadataSupport.readTables(md, catalog, null);
    }

    @Override
    public List<ColumnEntry> listColumns(Connection connection, String catalog, String schema, String table)
            throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        return JdbcMetadataSupport.readColumns(md, catalog, schema, table);
    }
}
