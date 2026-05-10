package com.dbhelp.service.metadata.dialect;

import com.dbhelp.dto.metadata.CatalogEntry;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.TableEntry;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL / MariaDB / GoldenDB（MySQL 协议）等：{@link DatabaseMetaData#getTables(String, String, String, String[])} 的 catalog 即库名。
 */
public class MysqlFamilyMetadataDialect implements MetadataDialect {

    @Override
    public boolean reconnectPerCatalogForTables() {
        return false;
    }

    @Override
    public List<CatalogEntry> listCatalogs(Connection connection) throws SQLException {
        DatabaseMetaData md = connection.getMetaData();
        List<String> names = JdbcMetadataSupport.readCatalogNames(md);
        List<CatalogEntry> out = new ArrayList<CatalogEntry>();
        for (String n : names) {
            out.add(new CatalogEntry(n));
        }
        return out;
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
        return JdbcMetadataSupport.readColumns(md, catalog, null, table);
    }
}
