package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 不含表别名；catalog/schema 由后端从 {@link com.jsjjlt.sqlparser.entity.RefTab} 的 prefix 与 {@link SingleQualifierRole} 映射得到。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualifiedTableRefDto {
    private String catalog;
    private String schema;
    private String table;
}
