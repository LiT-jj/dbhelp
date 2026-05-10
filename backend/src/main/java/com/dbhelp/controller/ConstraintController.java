package com.dbhelp.controller;

import com.dbhelp.common.JsonMaps;
import com.dbhelp.dto.constraint.RigidConstraintParseResponse;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.dto.sqlparse.SingleQualifierRole;
import com.dbhelp.dto.sqlparse.SqlConstraintRelationDto;
import com.dbhelp.dto.sqlparse.SqlParseErrorDto;
import com.dbhelp.dto.sqlparse.SqlParseRequest;
import com.dbhelp.dto.sqlparse.SqlParseTableConstraintDto;
import com.dbhelp.service.constraint.RigidConstraintService;
import com.dbhelp.service.sqlparse.SqlParseResultMapper;
import com.jsjjlt.sqlparser.JsqlParser;
import com.jsjjlt.sqlparser.ParseException;
import com.jsjjlt.sqlparser.ParseResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/constraint")
public class ConstraintController {

    private final RigidConstraintService rigidConstraintService;

    public ConstraintController(RigidConstraintService rigidConstraintService) {
        this.rigidConstraintService = rigidConstraintService;
    }

    /**
     * 软约束：解析 SQL（原 {@code POST /api/sql/parse}）。
     * <p>{@code parseOk} 表示 strict 语义下是否无错误；非 strict 时部分语句失败仍可能返回已合并的约束。</p>
     */
    @PostMapping({"/soft/parse", "/soft/parse/"})
    public Map<String, Object> parseSoftSql(@RequestBody SqlParseRequest request) {
        if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
            return JsonMaps.failure("SQL 不能为空");
        }
        String sql = request.getSql().trim();
        String prefix = request.getPrefix();
        SingleQualifierRole role = request.getSingleQualifierRole() != null
                ? request.getSingleQualifierRole()
                : SingleQualifierRole.SCHEMA;
        boolean strict = Boolean.TRUE.equals(request.getStrict());

        JsqlParser jsqlParser = new JsqlParser();
        try {
            ParseResult parse = jsqlParser.parse(sql, strict);
            List<SqlParseTableConstraintDto> tables = SqlParseResultMapper.toTableConstraints(
                    parse.getContext().getTable2constraint(prefix), role);
            SqlConstraintRelationDto constraints = new SqlConstraintRelationDto(tables);
            List<SqlParseErrorDto> errors = SqlParseResultMapper.toErrorDtos(parse.getErrors());
            return JsonMaps.successExtra(
                    "constraints", constraints,
                    "errors", errors,
                    "parseOk", !parse.hasErrors());
        } catch (ParseException ex) {
            SqlConstraintRelationDto empty = new SqlConstraintRelationDto(Collections.emptyList());
            List<SqlParseErrorDto> errors = SqlParseResultMapper.toErrorDtos(ex.getErrors());
            return JsonMaps.successExtra(
                    "constraints", empty,
                    "errors", errors,
                    "parseOk", false);
        } catch (Exception e) {
            return JsonMaps.failure("SQL解析异常: " + e.getMessage());
        }
    }

    /**
     * 刚性约束：按连接与库表从 MySQL {@code information_schema} 推导 RANGE / EQUAL / NOT_NULL（首期仅 MYSQL）。
     */
    @PostMapping({"/hard/parse", "/hard/parse/"})
    public Map<String, Object> parseHardConstraints(@RequestBody ColumnsMetadataRequest request) {
        if (request == null) {
            return JsonMaps.failure("请求体不能为空");
        }
        try {
            RigidConstraintParseResponse res = rigidConstraintService.parse(request);
            return JsonMaps.successExtra(
                    "dbType", res.getDbType(),
                    "tableRef", res.getTableRef(),
                    "columns", res.getColumns());
        } catch (IllegalArgumentException e) {
            return JsonMaps.failure(e.getMessage());
        } catch (SQLException e) {
            return JsonMaps.failure("查询元数据失败: " + e.getMessage());
        }
    }
}
