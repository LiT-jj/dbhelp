package com.dbhelp.generate.constraint;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对单行 Map 应用任务中的硬/软约束（首期：EQUAL、NOT_NULL、RANGE 数值/字符串粗判）。
 */
public final class SimpleConstraintApplier {

    private SimpleConstraintApplier() {
    }

    public static void applyAll(List<Map<String, Object>> constraints, String columnName, Map<String, Object> row) {
        if (constraints == null || columnName == null) {
            return;
        }
        for (Map<String, Object> c : constraints) {
            if (c == null) {
                continue;
            }
            String col = stringVal(c.get("column"));
            if (col == null || !col.equalsIgnoreCase(columnName)) {
                continue;
            }
            String kind = stringVal(c.get("kind"));
            if (kind == null) {
                continue;
            }
            switch (kind.toUpperCase(Locale.ROOT)) {
                case "NOT_NULL":
                    if (row.get(columnName) == null) {
                        row.put(columnName, "");
                    }
                    break;
                case "EQUAL":
                    if (c.containsKey("equalValue") && c.get("equalValue") != null) {
                        row.put(columnName, c.get("equalValue"));
                    }
                    break;
                case "RANGE":
                    Object cur = row.get(columnName);
                    if (cur instanceof Number) {
                        double v = ((Number) cur).doubleValue();
                        Double min = parseDouble(c.get("rangeMin"));
                        Double max = parseDouble(c.get("rangeMax"));
                        if (min != null && v < min) {
                            row.put(columnName, min);
                        }
                        if (max != null && v > max) {
                            row.put(columnName, max);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static String stringVal(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double parseDouble(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
