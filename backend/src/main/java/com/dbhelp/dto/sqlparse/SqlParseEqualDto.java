package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlParseEqualDto {
    private String value;
    private Boolean necessary;
}
