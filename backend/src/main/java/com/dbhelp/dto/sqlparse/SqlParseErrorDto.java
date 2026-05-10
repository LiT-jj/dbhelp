package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析过程产生的错误（来自 {@link com.jsjjlt.sqlparser.ParseResult#getErrors()}），
 * 不包含 {@link Throwable} 本体，便于 JSON 序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlParseErrorDto {
    private String stage;
    private String sql;
    private String statement;
    private String message;
    private String causeClass;
    private String causeMessage;
}
