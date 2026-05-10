package com.dbhelp.service.sqlparse;

import com.dbhelp.dto.sqlparse.QualifiedColumnRefDto;
import com.dbhelp.dto.sqlparse.QualifiedTableRefDto;
import com.dbhelp.dto.sqlparse.SingleQualifierRole;
import com.dbhelp.dto.sqlparse.SqlParseColumnConstraintDto;
import com.dbhelp.dto.sqlparse.SqlParseEqualDto;
import com.dbhelp.dto.sqlparse.SqlParseErrorDto;
import com.dbhelp.dto.sqlparse.SqlParseRangeDto;
import com.dbhelp.dto.sqlparse.SqlParseRelateDto;
import com.dbhelp.dto.sqlparse.SqlParseTableConstraintDto;
import com.jsjjlt.sqlparser.ParseError;
import com.jsjjlt.sqlparser.constraint.ColumnConstraint;
import com.jsjjlt.sqlparser.constraint.TableConstraint;
import com.jsjjlt.sqlparser.entity.Equal;
import com.jsjjlt.sqlparser.entity.RefCol;
import com.jsjjlt.sqlparser.entity.RefTab;
import com.jsjjlt.sqlparser.entity.Relate;
import com.jsjjlt.sqlparser.range.DateRange;
import com.jsjjlt.sqlparser.range.DateTimeRange;
import com.jsjjlt.sqlparser.range.NumericRange;
import com.jsjjlt.sqlparser.range.Range;
import com.jsjjlt.sqlparser.range.StringRange;
import com.jsjjlt.sqlparser.range.TimeRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将解析器内部结构转为前端友好的 DTO：显式 catalog/schema、不包含别名。
 */
public final class SqlParseResultMapper {

    private SqlParseResultMapper() {
    }

    public static List<SqlParseErrorDto> toErrorDtos(List<ParseError> errors) {
        if (errors == null || errors.isEmpty()) {
            return Collections.emptyList();
        }
        return errors.stream().map(SqlParseResultMapper::toErrorDto).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static SqlParseErrorDto toErrorDto(ParseError e) {
        if (e == null) {
            return null;
        }
        Throwable c = e.getCause();
        String causeClass = c == null ? null : c.getClass().getName();
        String causeMessage = c == null ? null : c.getMessage();
        return new SqlParseErrorDto(
                e.getStage(),
                e.getSql(),
                e.getStatement(),
                e.getMessage(),
                causeClass,
                causeMessage);
    }

    public static List<SqlParseTableConstraintDto> toTableConstraints(
            Map<RefTab, TableConstraint> raw,
            SingleQualifierRole singleQualifierRole) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        SingleQualifierRole role = singleQualifierRole != null ? singleQualifierRole : SingleQualifierRole.SCHEMA;
        List<SqlParseTableConstraintDto> out = new ArrayList<>();
        for (Map.Entry<RefTab, TableConstraint> e : raw.entrySet()) {
            RefTab tab = e.getKey();
            TableConstraint tc = e.getValue();
            if (tab == null || tc == null) {
                continue;
            }
            SqlParseTableConstraintDto row = new SqlParseTableConstraintDto();
            row.setTable(toQualifiedTable(tab, role));
            List<SqlParseColumnConstraintDto> cols = new ArrayList<>();
            if (tc.getColumn2constraint() != null) {
                for (ColumnConstraint cc : tc.getColumn2constraint().values()) {
                    if (cc == null) {
                        continue;
                    }
                    cols.add(toColumnConstraint(cc, role));
                }
            }
            Comparator<String> strKey = Comparator.nullsLast(String::compareTo);
            cols.sort(Comparator
                    .comparing((SqlParseColumnConstraintDto c) -> c.getColumn().getCatalog(), strKey)
                    .thenComparing(c -> c.getColumn().getSchema(), strKey)
                    .thenComparing(c -> c.getColumn().getTable(), strKey)
                    .thenComparing(c -> c.getColumn().getColumn(), strKey));
            row.setColumns(cols);
            out.add(row);
        }
        Comparator<String> strKey = Comparator.nullsLast(String::compareTo);
        out.sort(Comparator
                .comparing((SqlParseTableConstraintDto r) -> r.getTable().getCatalog(), strKey)
                .thenComparing(r -> r.getTable().getSchema(), strKey)
                .thenComparing(r -> r.getTable().getTable(), strKey));
        return out;
    }

    /**
     * 仅从 {@link RefTab} 的 prefix/name 映射；不依赖 jsqlparser 工程内对 catalog/database 的扩展。
     * 单段 prefix 由 {@link SingleQualifierRole} 决定写入 DTO 的 catalog 还是 schema。
     */
    public static QualifiedTableRefDto toQualifiedTable(RefTab tab, SingleQualifierRole role) {
        if (tab == null) {
            return new QualifiedTableRefDto(null, null, null);
        }
        String catalog = null;
        String schema = emptyToNull(tab.getPrefix());
        String table = tab.getName();
        if (schema != null && role == SingleQualifierRole.CATALOG) {
            catalog = schema;
            schema = null;
        }
        return new QualifiedTableRefDto(catalog, schema, table);
    }

    public static QualifiedColumnRefDto toQualifiedColumn(RefCol col, SingleQualifierRole role) {
        if (col == null) {
            return new QualifiedColumnRefDto(null, null, null, null);
        }
        QualifiedTableRefDto t = toQualifiedTable(col.getPrefix(), role);
        return new QualifiedColumnRefDto(t.getCatalog(), t.getSchema(), t.getTable(), col.getName());
    }

    private static SqlParseColumnConstraintDto toColumnConstraint(ColumnConstraint cc, SingleQualifierRole role) {
        RefCol refCol = cc.getRefCol() != null ? cc.getRefCol() : null;
        SqlParseColumnConstraintDto dto = new SqlParseColumnConstraintDto();
        dto.setColumn(toQualifiedColumn(refCol, role));
        if (cc.getEquals() != null) {
            dto.setEquals(cc.getEquals().stream().map(SqlParseResultMapper::toEqual).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        if (cc.getRanges() != null) {
            dto.setRanges(cc.getRanges().stream().map(SqlParseResultMapper::toRange).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        if (cc.getRelates() != null) {
            dto.setRelates(cc.getRelates().stream().map(r -> toRelate(r, role)).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        return dto;
    }

    private static SqlParseEqualDto toEqual(Equal e) {
        if (e == null) {
            return null;
        }
        return new SqlParseEqualDto(e.getValue(), e.getNecessary());
    }

    private static SqlParseRelateDto toRelate(Relate r, SingleQualifierRole role) {
        if (r == null) {
            return null;
        }
        return new SqlParseRelateDto(r.getOperator(), toQualifiedColumn(r.getColumn(), role), r.isNecessary());
    }

    private static SqlParseRangeDto toRange(Range range) {
        if (range == null) {
            return null;
        }
        Boolean necessary = range.isNecessary() ? Boolean.TRUE : Boolean.FALSE;
        if (range instanceof StringRange) {
            StringRange sr = (StringRange) range;
            return new SqlParseRangeDto(SqlParseRangeDto.Kind.STRING, sr.getMin(), sr.getMax(), necessary);
        }
        if (range instanceof NumericRange) {
            NumericRange nr = (NumericRange) range;
            String min = nr.getMin() == null ? null : nr.getMin().toPlainString();
            String max = nr.getMax() == null ? null : nr.getMax().toPlainString();
            return new SqlParseRangeDto(SqlParseRangeDto.Kind.NUMERIC, min, max, necessary);
        }
        if (range instanceof DateRange) {
            DateRange dr = (DateRange) range;
            String min = dr.getMin() == null ? null : dr.getMin().toString();
            String max = dr.getMax() == null ? null : dr.getMax().toString();
            return new SqlParseRangeDto(SqlParseRangeDto.Kind.DATE, min, max, necessary);
        }
        if (range instanceof DateTimeRange) {
            DateTimeRange dr = (DateTimeRange) range;
            String min = dr.getMin() == null ? null : dr.getMin().toString();
            String max = dr.getMax() == null ? null : dr.getMax().toString();
            return new SqlParseRangeDto(SqlParseRangeDto.Kind.DATETIME, min, max, necessary);
        }
        if (range instanceof TimeRange) {
            TimeRange tr = (TimeRange) range;
            String min = durationToString(tr.getMin());
            String max = durationToString(tr.getMax());
            return new SqlParseRangeDto(SqlParseRangeDto.Kind.TIME, min, max, necessary);
        }
        return new SqlParseRangeDto(SqlParseRangeDto.Kind.OTHER, null, null, necessary);
    }

    private static String durationToString(Duration d) {
        return d == null ? null : d.toString();
    }

    private static String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
