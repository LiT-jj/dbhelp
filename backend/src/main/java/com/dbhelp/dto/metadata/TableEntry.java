package com.dbhelp.dto.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableEntry {
    private String catalog;
    private String schema;
    private String name;
    /** JDBC TABLE_TYPE，如 TABLE、VIEW */
    private String tableType;
}
