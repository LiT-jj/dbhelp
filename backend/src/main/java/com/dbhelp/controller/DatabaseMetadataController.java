package com.dbhelp.controller;

import com.dbhelp.dto.metadata.CatalogEntry;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.dto.metadata.TableEntry;
import com.dbhelp.dto.metadata.TablesMetadataRequest;
import com.dbhelp.service.metadata.DatabaseMetadataService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/metadata")
public class DatabaseMetadataController {

    private final DatabaseMetadataService databaseMetadataService;

    public DatabaseMetadataController(DatabaseMetadataService databaseMetadataService) {
        this.databaseMetadataService = databaseMetadataService;
    }

    /** 根据连接信息列出全部库（catalog / database 概念因品种而异） */
    @PostMapping("/catalogs")
    public List<CatalogEntry> catalogs(@RequestBody ConnectionPayload request) throws SQLException {
        return databaseMetadataService.listCatalogs(request);
    }

    /** 根据连接信息列出指定一个或多个库下的表 */
    @PostMapping("/tables")
    public List<TableEntry> tables(@RequestBody TablesMetadataRequest request) throws SQLException {
        return databaseMetadataService.listTables(request);
    }

    /** 根据连接信息列出指定库、表的全部列 */
    @PostMapping("/columns")
    public List<ColumnEntry> columns(@RequestBody ColumnsMetadataRequest request) throws SQLException {
        return databaseMetadataService.listColumns(request);
    }
}
