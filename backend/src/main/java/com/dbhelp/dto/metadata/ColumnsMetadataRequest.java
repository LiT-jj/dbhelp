package com.dbhelp.dto.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ColumnsMetadataRequest extends ConnectionPayload {
    /** 库名（逻辑库 / catalog / database） */
    private String catalog;
    /** 可为空：MySQL 系一般为 null；PostgreSQL 常用 public */
    private String schema;
    private String table;
}
