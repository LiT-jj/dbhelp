package com.dbhelp.dto.sqlparse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * {@code POST /api/constraint/soft/parse} 请求体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SqlParseRequest {
    private String sql;
    /**
     * 与 {@link com.jsjjlt.sqlparser.entity.SQLContext#getTable2constraint(String)} 一致：
     * 当列上表引用缺少限定前缀时，用该值补全到 {@link com.jsjjlt.sqlparser.entity.RefTab#getPrefix()}。
     */
    private String prefix;
    /**
     * 仅一段限定名且未解析出 catalog 时，将这一段解释为 schema 或 catalog（默认 schema，适合 PostgreSQL / 多数 MySQL 写法）。
     */
    private SingleQualifierRole singleQualifierRole = SingleQualifierRole.SCHEMA;
    /**
     * 为 true 时与 {@link com.jsjjlt.sqlparser.JsqlParser#parse(String, boolean)} 的 strict 一致：存在错误则抛 {@link com.jsjjlt.sqlparser.ParseException}，
     * 接口仍会返回 {@code errors}，且 {@code constraints} 可能为空。
     */
    private Boolean strict;
}
