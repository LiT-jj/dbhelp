package com.dbhelp.service.metadata;

import com.dbhelp.dto.metadata.CatalogEntry;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.dto.metadata.TableEntry;
import com.dbhelp.dto.metadata.TablesMetadataRequest;
import com.dbhelp.service.metadata.dialect.MetadataDialect;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class DatabaseMetadataService {

    private final ConnectionPayloadResolver connectionPayloadResolver;
    private final MetadataDialectRegistry dialectRegistry;

    public DatabaseMetadataService(
            ConnectionPayloadResolver connectionPayloadResolver,
            MetadataDialectRegistry dialectRegistry) {
        this.connectionPayloadResolver = connectionPayloadResolver;
        this.dialectRegistry = dialectRegistry;
    }

    public List<CatalogEntry> listCatalogs(ConnectionPayload request) throws SQLException {
        ResolvedConnection rc = connectionPayloadResolver.resolve(request);
        MetadataDialect dialect = dialectRegistry.resolve(rc.getDbType());
        try (Connection connection = open(rc)) {
            return dialect.listCatalogs(connection);
        }
    }

    public List<TableEntry> listTables(TablesMetadataRequest request) throws SQLException {
        if (request.getCatalogs() == null || request.getCatalogs().isEmpty()) {
            throw new IllegalArgumentException("catalogs 不能为空");
        }
        ResolvedConnection base = connectionPayloadResolver.resolve(request);
        MetadataDialect dialect = dialectRegistry.resolve(base.getDbType());
        List<TableEntry> out = new ArrayList<TableEntry>();
        if (dialect.reconnectPerCatalogForTables()) {
            for (String catalog : request.getCatalogs()) {
                ResolvedConnection rc = base.withDatabase(catalog);
                try (Connection connection = open(rc)) {
                    out.addAll(dialect.listTables(connection, catalog));
                }
            }
        } else {
            try (Connection connection = open(base)) {
                for (String catalog : request.getCatalogs()) {
                    out.addAll(dialect.listTables(connection, catalog));
                }
            }
        }
        return out;
    }

    public List<ColumnEntry> listColumns(ColumnsMetadataRequest request) throws SQLException {
        if (request.getCatalog() == null || request.getCatalog().trim().isEmpty()) {
            throw new IllegalArgumentException("catalog 不能为空");
        }
        if (request.getTable() == null || request.getTable().trim().isEmpty()) {
            throw new IllegalArgumentException("table 不能为空");
        }
        ResolvedConnection base = connectionPayloadResolver.resolve(request);
        MetadataDialect dialect = dialectRegistry.resolve(base.getDbType());
        String catalog = request.getCatalog().trim();
        if (dialect.reconnectPerCatalogForTables()) {
            ResolvedConnection rc = base.withDatabase(catalog);
            try (Connection connection = open(rc)) {
                return dialect.listColumns(connection, catalog, request.getSchema(), request.getTable().trim());
            }
        }
        try (Connection connection = open(base)) {
            return dialect.listColumns(connection, catalog, request.getSchema(), request.getTable().trim());
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
}
