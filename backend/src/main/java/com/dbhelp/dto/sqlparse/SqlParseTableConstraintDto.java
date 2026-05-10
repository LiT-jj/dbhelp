package com.dbhelp.dto.sqlparse;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SqlParseTableConstraintDto {
    private QualifiedTableRefDto table;
    private List<SqlParseColumnConstraintDto> columns = new ArrayList<>();
}
