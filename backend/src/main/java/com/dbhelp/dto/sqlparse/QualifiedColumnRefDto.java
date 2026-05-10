package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 列所属表 + 列名，不含列别名。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualifiedColumnRefDto {
    private String catalog;
    private String schema;
    private String table;
    private String column;
}
