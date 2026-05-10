package com.dbhelp.dto.sqlparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 表 → 字段 → 约束 的一对多对多关系（仅约束侧，不含别名）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlConstraintRelationDto {
    private List<SqlParseTableConstraintDto> tables = new ArrayList<>();
}
