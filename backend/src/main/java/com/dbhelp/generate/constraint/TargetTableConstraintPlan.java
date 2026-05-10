package com.dbhelp.generate.constraint;

import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.generate.faker.FakedValueProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 单目标表上的约束计划：等号关联合并池、{@code col > ref} / {@code col < ref} 转为有序生成与数值边界，
 * 供 {@link com.dbhelp.generate.pipeline.DataFakerGeneratePipeline} 协调一行内多列。
 */
public final class TargetTableConstraintPlan {

    private static final ObjectMapper OM = new ObjectMapper();

    private final UnionFind uf;
    /** 每个连通分量根（字符串），拓扑有序：lesser 侧先于 greater 侧 */
    private final List<String> clusterRootsTopo;
    private final Map<String, ColumnConstraintHints> hintsByColumn;
    /** greaterCol -> lesserCol（greater 的值应大于 lesser） */
    private final List<GreaterEdge> greaterEdges;

    private TargetTableConstraintPlan(
            UnionFind uf,
            List<String> clusterRootsTopo,
            Map<String, ColumnConstraintHints> hintsByColumn,
            List<GreaterEdge> greaterEdges) {
        this.uf = uf;
        this.clusterRootsTopo = clusterRootsTopo;
        this.hintsByColumn = hintsByColumn;
        this.greaterEdges = greaterEdges;
    }

    public static TargetTableConstraintPlan build(
            String catalog,
            String schema,
            String table,
            List<String> columnNames,
            List<Map<String, Object>> hard,
            List<Map<String, Object>> soft) {
        Map<String, ColumnConstraintHints> hints = new HashMap<>();
        for (String cn : columnNames) {
            hints.put(cn, new ColumnConstraintHints());
        }
        UnionFind uf = new UnionFind(columnNames);
        List<GreaterEdge> greaterEdges = new ArrayList<>();

        Set<String> known = new LinkedHashSet<>(columnNames);
        ingestList(hard, catalog, schema, table, hints, uf, greaterEdges, known);
        ingestList(soft, catalog, schema, table, hints, uf, greaterEdges, known);

        mergeEqualityPools(uf, hints, columnNames);

        List<String> rootsTopo = topoClusterRoots(columnNames, uf, greaterEdges);
        return new TargetTableConstraintPlan(uf, rootsTopo, hints, greaterEdges);
    }

    private static void ingestList(
            List<Map<String, Object>> list,
            String catalog,
            String schema,
            String table,
            Map<String, ColumnConstraintHints> hints,
            UnionFind uf,
            List<GreaterEdge> greaterEdges,
            Set<String> knownColumns) {
        if (list == null) {
            return;
        }
        for (Map<String, Object> c : list) {
            if (c == null || !matchesTable(c, catalog, schema, table)) {
                continue;
            }
            String colName = str(c.get("column"));
            if (colName == null || !hints.containsKey(colName)) {
                continue;
            }
            String kind = upper(str(c.get("kind")));
            if (kind == null) {
                continue;
            }
            ColumnConstraintHints h = hints.get(colName);
            switch (kind) {
                case "NOT_NULL":
                    h.setNotNull(true);
                    break;
                case "EQUAL":
                    addEqualTokens(h, c.get("equalValue"));
                    break;
                case "RANGE":
                    addRangeSpan(h, c.get("rangeMin"), c.get("rangeMax"));
                    break;
                case "RELATE":
                    parseRelate(c.get("relateExpr"), colName, uf, greaterEdges, knownColumns);
                    break;
                default:
                    break;
            }
        }
    }

    private static void parseRelate(
            Object relateExpr,
            String column,
            UnionFind uf,
            List<GreaterEdge> greaterEdges,
            Set<String> knownColumns) {
        if (relateExpr == null) {
            return;
        }
        String expr = String.valueOf(relateExpr).trim();
        if (expr.isEmpty()) {
            return;
        }
        try {
            JsonNode n = OM.readTree(expr);
            String op = n.path("op").asText(n.path("operator").asText("=")).trim();
            String refCol = str(n.get("refColumn"));
            if (refCol == null || !knownColumns.contains(refCol) || !knownColumns.contains(column)) {
                return;
            }
            if ("=".equals(op) || "==".equals(op)) {
                uf.union(column, refCol);
                return;
            }
            if (">".equals(op)) {
                // column > refCol  => greater column, lesser refCol
                greaterEdges.add(new GreaterEdge(column, refCol));
                return;
            }
            if ("<".equals(op)) {
                // column < refCol => greater refCol, lesser column
                greaterEdges.add(new GreaterEdge(refCol, column));
            }
        } catch (Exception ignored) {
            // 非法 JSON：跳过
        }
    }

    private static void mergeEqualityPools(UnionFind uf, Map<String, ColumnConstraintHints> hints, List<String> columnNames) {
        Map<String, List<String>> buckets = new HashMap<>();
        for (String col : columnNames) {
            String r = uf.find(col);
            buckets.computeIfAbsent(r, k -> new ArrayList<>()).add(col);
        }
        for (List<String> group : buckets.values()) {
            if (group.size() <= 1) {
                continue;
            }
            LinkedHashSet<String> mergedEq = new LinkedHashSet<>();
            for (String col : group) {
                mergedEq.addAll(hints.get(col).getEqualCandidates());
            }
            ArrayList<String> pool = new ArrayList<>(mergedEq);
            for (String col : group) {
                ColumnConstraintHints h = hints.get(col);
                h.getEqualCandidates().clear();
                h.getEqualCandidates().addAll(pool);
            }
        }
    }

    /**
     * 对簇内列合并 RANGE 区间（求交），用于等号簇共用同一取值时在数值上尽量同时满足各列 RANGE。
     */
    static RangeSpan intersectAllRanges(List<String> members, Map<String, ColumnConstraintHints> hintsByColumn) {
        Double lo = null;
        Double hi = null;
        boolean any = false;
        for (String m : members) {
            ColumnConstraintHints hh = hintsByColumn.get(m);
            if (hh == null) {
                continue;
            }
            for (RangeSpan rs : hh.getRangeCandidates()) {
                any = true;
                Double a = rs.getMin();
                Double b = rs.getMax();
                lo = lo == null ? a : max(lo, a);
                hi = hi == null ? b : min(hi, b);
            }
        }
        if (!any) {
            return null;
        }
        if (lo != null && hi != null && lo > hi) {
            return null;
        }
        return new RangeSpan(lo, hi);
    }

    private static List<String> topoClusterRoots(List<String> columnNames, UnionFind uf, List<GreaterEdge> edges) {
        Map<String, Set<String>> adj = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        Set<String> roots = columnNames.stream().map(uf::find).collect(Collectors.toCollection(LinkedHashSet::new));
        for (String r : roots) {
            adj.put(r, new LinkedHashSet<>());
            indeg.put(r, 0);
        }
        for (GreaterEdge e : edges) {
            String g = uf.find(e.greater);
            String l = uf.find(e.lesser);
            if (g.equals(l)) {
                continue;
            }
            // lesser -> greater（先生成 lesser）
            if (!adj.containsKey(l)) {
                adj.put(l, new LinkedHashSet<>());
                indeg.putIfAbsent(l, 0);
            }
            if (!indeg.containsKey(g)) {
                indeg.put(g, 0);
            }
            if (adj.get(l).add(g)) {
                indeg.put(g, indeg.getOrDefault(g, 0) + 1);
            }
        }
        Deque<String> q = new ArrayDeque<>();
        for (String r : roots) {
            if (indeg.getOrDefault(r, 0) == 0) {
                q.add(r);
            }
        }
        List<String> order = new ArrayList<>();
        while (!q.isEmpty()) {
            String u = q.removeFirst();
            order.add(u);
            for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                int d = indeg.get(v) - 1;
                indeg.put(v, d);
                if (d == 0) {
                    q.add(v);
                }
            }
        }
        if (order.size() < roots.size()) {
            // 环：退回字典序保证终止
            List<String> rest = new ArrayList<>(roots);
            Collections.sort(rest);
            order.clear();
            order.addAll(rest);
        }
        return order;
    }

    public Map<String, Object> generateRow(
            Map<String, ColumnEntry> colByName,
            String dbTypeUpper,
            Faker faker,
            Random random) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String root : clusterRootsTopo) {
            String canonRoot = uf.find(root);
            List<String> members = uf.members(canonRoot);
            Collections.sort(members);

            String sampleColName = members.get(0);
            ColumnConstraintHints merged = mergeHintsForCluster(members, hintsByColumn);
            applyGreaterBounds(canonRoot, merged, row);

            ColumnEntry sampleCol = colByName.get(sampleColName);
            if (sampleCol == null) {
                continue;
            }
            Object v =
                    FakedValueProducer.valueFor(
                            dbTypeUpper,
                            sampleCol,
                            faker,
                            random,
                            merged,
                            () -> intersectAllRanges(members, hintsByColumn));
            for (String m : members) {
                row.put(m, v);
            }
        }
        return row;
    }

    /** 当前生成的簇若作为「大于」侧的 greater，则用已在 row 中的 lesser 数值作下界（不含）。 */
    private void applyGreaterBounds(String generatingClusterRoot, ColumnConstraintHints h, Map<String, Object> row) {
        h.clearRelateBounds();
        Double bestLower = null;
        for (GreaterEdge e : greaterEdges) {
            if (!uf.find(e.greater).equals(generatingClusterRoot)) {
                continue;
            }
            Object lv = row.get(e.lesser);
            Double d = asDouble(lv);
            if (d == null) {
                continue;
            }
            bestLower = bestLower == null ? d : Math.max(bestLower, d);
        }
        if (bestLower != null) {
            h.setLowerExclusive(bestLower);
        }
    }

    private static ColumnConstraintHints mergeHintsForCluster(
            List<String> members, Map<String, ColumnConstraintHints> hintsByColumn) {
        ColumnConstraintHints out = new ColumnConstraintHints();
        LinkedHashSet<String> eq = new LinkedHashSet<>();
        ArrayList<RangeSpan> ranges = new ArrayList<>();
        boolean notNull = false;
        for (String m : members) {
            ColumnConstraintHints h = hintsByColumn.get(m);
            if (h == null) {
                continue;
            }
            eq.addAll(h.getEqualCandidates());
            ranges.addAll(h.getRangeCandidates());
            notNull |= h.isNotNull();
        }
        out.getEqualCandidates().addAll(eq);
        out.getRangeCandidates().addAll(ranges);
        out.setNotNull(notNull);
        return out;
    }

    private static void addEqualTokens(ColumnConstraintHints h, Object equalValue) {
        if (equalValue == null) {
            return;
        }
        String s = String.valueOf(equalValue).trim();
        if (s.isEmpty()) {
            return;
        }
        if (s.contains(",")) {
            for (String p : s.split(",")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    h.getEqualCandidates().add(t);
                }
            }
        } else {
            h.getEqualCandidates().add(s);
        }
    }

    private static void addRangeSpan(ColumnConstraintHints h, Object minO, Object maxO) {
        Double min = parseDouble(minO);
        Double max = parseDouble(maxO);
        if (min == null && max == null) {
            return;
        }
        h.getRangeCandidates().add(new RangeSpan(min, max));
    }

    private static Double parseDouble(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return parseDouble(v);
    }

    private static boolean matchesTable(Map<String, Object> c, String catalog, String schema, String table) {
        return norm(str(c.get("catalog"))).equals(norm(catalog))
                && norm(str(c.get("schema"))).equals(norm(schema))
                && norm(str(c.get("table"))).equals(norm(table));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static Double max(Double a, Double b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    private static Double min(Double a, Double b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.min(a, b);
    }

    private static final class GreaterEdge {
        final String greater;
        final String lesser;

        GreaterEdge(String greater, String lesser) {
            this.greater = greater;
            this.lesser = lesser;
        }
    }

    /**
     * 并查集：等号关联的列共用等值池。
     */
    static final class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        UnionFind(Iterable<String> all) {
            for (String x : all) {
                parent.put(x, x);
            }
        }

        String find(String x) {
            String p = parent.get(x);
            if (p == null) {
                parent.put(x, x);
                return x;
            }
            if (!p.equals(x)) {
                p = find(p);
                parent.put(x, p);
            }
            return p;
        }

        void union(String a, String b) {
            if (a == null || b == null) {
                return;
            }
            if (!parent.containsKey(a) || !parent.containsKey(b)) {
                return;
            }
            String ra = find(a);
            String rb = find(b);
            if (!ra.equals(rb)) {
                parent.put(ra, rb);
            }
        }

        List<String> members(String root) {
            String canon = find(root);
            List<String> out = new ArrayList<>();
            for (String k : parent.keySet()) {
                if (find(k).equals(canon)) {
                    out.add(k);
                }
            }
            Collections.sort(out);
            return out;
        }
    }

    /** used by tests */
    List<String> getClusterRootsTopo() {
        return clusterRootsTopo;
    }
}
