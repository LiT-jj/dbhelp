package com.dbhelp.dto.sqlparse;

/**
 * 当 AST 仅有一段表限定名（落在 {@link com.jsjjlt.sqlparser.entity.RefTab#getPrefix()}）时，
 * 在对外 DTO 里把它标成 {@code schema} 还是 {@code catalog}（如 SQL Server 的 database）。
 */
public enum SingleQualifierRole {
    SCHEMA,
    CATALOG
}
