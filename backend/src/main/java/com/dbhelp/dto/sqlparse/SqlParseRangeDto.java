package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对应解析器中的 {@link com.jsjjlt.sqlparser.range.Range} 子类，用 kind 区分。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlParseRangeDto {
    public enum Kind {
        STRING,
        NUMERIC,
        DATE,
        DATETIME,
        TIME,
        OTHER
    }

    private Kind kind;
    private String min;
    private String max;
    private Boolean necessary;
}
