package com.dbhelp.dto.sqlparse;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SqlParseColumnConstraintDto {
    private QualifiedColumnRefDto column;
    private List<SqlParseEqualDto> equals = new ArrayList<>();
    private List<SqlParseRangeDto> ranges = new ArrayList<>();
    private List<SqlParseRelateDto> relates = new ArrayList<>();
}
