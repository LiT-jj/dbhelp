package com.dbhelp.dto.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntry {
    /** 库名（MySQL catalog / PG database / SQL Server database 等） */
    private String name;
}
