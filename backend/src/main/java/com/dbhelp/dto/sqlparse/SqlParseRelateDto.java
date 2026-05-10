package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlParseRelateDto {
    private String operator;
    private QualifiedColumnRefDto column;
    private Boolean necessary;
}
