package com.dbhelp.service.metadata.dialect;

import com.dbhelp.dto.metadata.CatalogEntry;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.TableEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 按数据库品种发现 catalog / 表 / 列；PG 与 MySQL 等对 JDBC 元数据用法不同。
 */
public interface MetadataDialect {

    /** PostgreSQL 等需要每个逻辑库单独建连枚举表；MySQL 可在一条连接上用 catalog 参数切换 */
    boolean reconnectPerCatalogForTables();

    List<CatalogEntry> listCatalogs(Connection connection) throws SQLException;

    List<TableEntry> listTables(Connection connection, String catalog) throws SQLException;

    List<ColumnEntry> listColumns(Connection connection, String catalog, String schema, String table) throws SQLException;
}
