package com.dbhelp.generate.faker;

import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.generate.constraint.ColumnConstraintHints;
import com.dbhelp.generate.constraint.RangeSpan;
import com.dbhelp.generate.types.CanonicalTypeResolver;
import com.dbhelp.generate.types.SqlCanonicalType;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 基于 Datafaker 与 {@link SqlCanonicalType} 生成单列值。
 */
public final class FakedValueProducer {

    private static final int MAX_TRIES = 64;

    private FakedValueProducer() {
    }

    /**
     * 无约束随机（兼容旧调用）。
     */
    public static Object valueFor(String dbTypeUpper, ColumnEntry col, Faker faker, Random random) {
        return valueFor(dbTypeUpper, col, faker, random, null, () -> null);
    }

    /**
     * 按 hard/soft 合并后的约束提示造数；等值池与范围池各自随机选一；
     * 等号簇的范围交集由 {@code clusterRangeIntersect} 提供（可为 null）。
     */
    public static Object valueFor(
            String dbTypeUpper,
            ColumnEntry col,
            Faker faker,
            Random random,
            ColumnConstraintHints hints,
            Supplier<RangeSpan> clusterRangeIntersect) {
        SqlCanonicalType t = CanonicalTypeResolver.resolve(dbTypeUpper, col);
        int maxLen =
                col.getColumnSize() == null ? 255 : Math.max(1, Math.min(col.getColumnSize(), 4000));

        boolean notNull = hints != null && hints.isNotNull();
        if (Boolean.FALSE.equals(col.getNullable())) {
            notNull = true;
        }

        RangeSpan intersect = clusterRangeIntersect != null ? clusterRangeIntersect.get() : null;
        if (hints == null) {
            return valueForUnconstrained(t, col, faker, random, maxLen, notNull);
        }

        List<String> eq = hints.getEqualCandidates();
        if (eq != null && !eq.isEmpty()) {
            for (int k = 0; k < MAX_TRIES; k++) {
                String pick = eq.get(random.nextInt(eq.size()));
                Object v = coerce(pick, t, col, faker, random, maxLen);
                v = adjustForRelateLower(t, v, hints.getLowerExclusive());
                if (satisfiesRelate(t, v, hints)) {
                    return v;
                }
            }
        }

        List<RangeSpan> spanList = new ArrayList<>(hints.getRangeCandidates());
        if (intersect != null && (intersect.getMin() != null || intersect.getMax() != null)) {
            spanList.add(0, intersect);
        }
        if (!spanList.isEmpty()) {
            for (int k = 0; k < MAX_TRIES; k++) {
                RangeSpan span = spanList.get(random.nextInt(spanList.size()));
                Object v = randomInSpan(t, col, faker, random, span, hints.getLowerExclusive(), maxLen);
                if (v != null && satisfiesRelate(t, v, hints)) {
                    return v;
                }
            }
        }

        Object v = valueForUnconstrained(t, col, faker, random, maxLen, false);
        v = adjustForRelateLower(t, v, hints.getLowerExclusive());
        if (t == SqlCanonicalType.STRING || t == SqlCanonicalType.UNKNOWN) {
            v = clampStringLowerBound(v, hints.getLowerExclusive());
        }
        if (Boolean.FALSE.equals(col.getNullable()) && v == null) {
            v = defaultNonNull(t, col, faker, random, maxLen);
        }
        return v;
    }

    private static Object valueForUnconstrained(
            SqlCanonicalType t, ColumnEntry col, Faker faker, Random random, int maxLen, boolean notNull) {
        switch (t) {
            case BOOLEAN:
                return random.nextBoolean();
            case INT32:
                return random.nextInt(1_000_000_000);
            case INT64:
                return ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE / 1000);
            case DECIMAL:
                int scale = col.getDecimalDigits() == null ? 2 : Math.min(8, Math.max(0, col.getDecimalDigits()));
                BigDecimal bd = BigDecimal.valueOf(random.nextDouble() * 1_000_000).setScale(scale, RoundingMode.HALF_UP);
                if (col.getColumnSize() != null && col.getColumnSize() > 0) {
                    bd = bd.abs().remainder(BigDecimal.TEN.pow(Math.min(18, col.getColumnSize())));
                }
                return bd;
            case FLOAT:
                return random.nextFloat() * 1000f;
            case DOUBLE:
                return random.nextDouble() * 1_000_000d;
            case STRING:
                String s = faker.lorem().characters(1, Math.min(32, maxLen));
                return truncate(s, maxLen);
            case DATE:
                return Date.valueOf(LocalDate.now().minusDays(random.nextInt(3650)));
            case TIME:
                return new Timestamp(System.currentTimeMillis() - random.nextInt(86_400_000));
            case DATETIME:
                return Timestamp.valueOf(LocalDateTime.now().minusSeconds(random.nextInt(86_400 * 365)));
            case BYTES:
                return null;
            case UNKNOWN:
            default:
                if (notNull) {
                    return truncate(faker.lorem().word(), maxLen);
                }
                return random.nextDouble() < 0.1 ? null : truncate(faker.lorem().word(), maxLen);
        }
    }

    private static Object defaultNonNull(SqlCanonicalType t, ColumnEntry col, Faker faker, Random random, int maxLen) {
        Object v = valueForUnconstrained(t, col, faker, random, maxLen, true);
        return v == null ? "" : v;
    }

    private static Object coerce(
            String raw, SqlCanonicalType t, ColumnEntry col, Faker faker, Random random, int maxLen) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        switch (t) {
            case BOOLEAN:
                return Boolean.parseBoolean(s) || "1".equals(s) || "Y".equalsIgnoreCase(s);
            case INT32:
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return random.nextInt(1_000_000);
                }
            case INT64:
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException e) {
                    return ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE / 1000);
                }
            case DECIMAL:
                try {
                    int scale =
                            col.getDecimalDigits() == null
                                    ? 2
                                    : Math.min(8, Math.max(0, col.getDecimalDigits()));
                    return new BigDecimal(s).setScale(scale, RoundingMode.HALF_UP);
                } catch (Exception e) {
                    return BigDecimal.valueOf(random.nextDouble() * 1000).setScale(2, RoundingMode.HALF_UP);
                }
            case FLOAT:
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    return random.nextFloat() * 100f;
                }
            case DOUBLE:
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return random.nextDouble() * 100d;
                }
            case DATE:
                try {
                    return Date.valueOf(s);
                } catch (Exception e) {
                    return Date.valueOf(LocalDate.now());
                }
            case TIME:
            case DATETIME:
                return valueForUnconstrained(t, col, faker, random, maxLen, true);
            case STRING:
            default:
                return truncate(s, maxLen);
        }
    }

    private static Object randomInSpan(
            SqlCanonicalType t,
            ColumnEntry col,
            Faker faker,
            Random random,
            RangeSpan span,
            Double lowerExclusive,
            int maxLen) {
        double lo = span.getMin() != null ? span.getMin() : Double.NEGATIVE_INFINITY;
        double hi = span.getMax() != null ? span.getMax() : Double.POSITIVE_INFINITY;
        if (lowerExclusive != null) {
            lo = Math.max(lo, strictLowerNumeric(lowerExclusive, t));
        }
        if (hi < lo) {
            return null;
        }
        switch (t) {
            case INT32: {
                int a = (int) Math.ceil(lo);
                int b = (int) Math.floor(hi);
                if (b < a) {
                    return a;
                }
                long span32 = (long) b - (long) a + 1L;
                if (span32 <= 0L) {
                    return a;
                }
                if (span32 <= (long) Integer.MAX_VALUE) {
                    return a + random.nextInt((int) span32);
                }
                long offset32 = nextLongExclusive(random, span32);
                return (int) Math.min((long) Integer.MAX_VALUE, Math.max((long) Integer.MIN_VALUE, (long) a + offset32));
            }
            case INT64: {
                long a64 = (long) Math.ceil(lo);
                long b64 = (long) Math.floor(hi);
                if (b64 < a64) {
                    return a64;
                }
                long span64;
                try {
                    span64 = Math.addExact(Math.subtractExact(b64, a64), 1L);
                } catch (ArithmeticException e) {
                    span64 = Long.MAX_VALUE;
                }
                return a64 + nextLongExclusive(random, span64);
            }
            case DECIMAL: {
                int scale =
                        col.getDecimalDigits() == null ? 2 : Math.min(8, Math.max(0, col.getDecimalDigits()));
                double x = lo + random.nextDouble() * (hi - lo);
                x = finiteSample(x, lo, hi);
                return BigDecimal.valueOf(x).setScale(scale, RoundingMode.HALF_UP);
            }
            case FLOAT:
                return (float) finiteSample(lo + random.nextDouble() * (hi - lo), lo, hi);
            case DOUBLE:
                return finiteSample(lo + random.nextDouble() * (hi - lo), lo, hi);
            default:
                return valueForUnconstrained(t, col, faker, random, maxLen, false);
        }
    }

    /** 极大 lo/hi 下乘法可能溢出为 Infinity，收敛到区间内有限值 */
    private static double finiteSample(double x, double lo, double hi) {
        if (Double.isFinite(x)) {
            return x;
        }
        double mid = (lo + hi) / 2.0;
        if (Double.isFinite(mid)) {
            return mid;
        }
        return 0.0;
    }

    /**
     * 在 [0, bound) 上均匀取 long；{@code bound} 必须 &gt; 0。
     * 避免 {@code bound} 大于 {@link Integer#MAX_VALUE} 时强转为 int 溢出成负数导致 {@code nextInt} 抛错。
     */
    private static long nextLongExclusive(Random random, long bound) {
        if (bound <= 0L) {
            return 0L;
        }
        if (bound <= (long) Integer.MAX_VALUE) {
            return random.nextInt((int) bound);
        }
        return ThreadLocalRandom.current().nextLong(0L, bound);
    }

    /** 严格大于 lowerExclusive 时可取的最小数值（随类型离散化）。 */
    private static double strictLowerNumeric(double lowerExclusive, SqlCanonicalType t) {
        switch (t) {
            case INT32:
            case INT64:
                return Math.floor(lowerExclusive) + 1.0;
            default:
                return lowerExclusive + Math.max(1e-9, Math.abs(lowerExclusive) * 1e-12);
        }
    }

    private static Object adjustForRelateLower(SqlCanonicalType t, Object v, Double lowerExclusive) {
        if (lowerExclusive == null || v == null) {
            return v;
        }
        if (v instanceof Number) {
            double n = ((Number) v).doubleValue();
            double min = strictLowerNumeric(lowerExclusive, t);
            if (n > lowerExclusive && n >= min - 1e-15) {
                return v;
            }
            switch (t) {
                case INT32:
                    return (int) Math.max(min, Math.ceil(lowerExclusive + 1e-9));
                case INT64:
                    return (long) Math.max(min, Math.ceil(lowerExclusive + 1e-9));
                case DECIMAL:
                    return BigDecimal.valueOf(min).setScale(8, RoundingMode.HALF_UP);
                case FLOAT:
                    return (float) min;
                case DOUBLE:
                    return min;
                default:
                    return v;
            }
        }
        return v;
    }

    private static Object clampStringLowerBound(Object v, Double lowerExclusive) {
        if (lowerExclusive == null || v == null) {
            return v;
        }
        String s = String.valueOf(v);
        try {
            double ref = lowerExclusive;
            double sv = Double.parseDouble(s.trim());
            if (sv <= ref) {
                return String.valueOf((long) Math.floor(ref) + 1);
            }
        } catch (NumberFormatException ignored) {
            // 非数值字符串：简单字典序大于参考字符串表示
            String ref = String.valueOf(lowerExclusive.longValue());
            if (s.compareTo(ref) <= 0) {
                return ref + "_x";
            }
        }
        return v;
    }

    private static boolean satisfiesRelate(SqlCanonicalType t, Object v, ColumnConstraintHints h) {
        if (h.getLowerExclusive() == null && h.getUpperExclusive() == null) {
            return true;
        }
        if (v instanceof Number) {
            double n = ((Number) v).doubleValue();
            if (h.getLowerExclusive() != null && n <= h.getLowerExclusive()) {
                return false;
            }
            if (h.getUpperExclusive() != null && n >= h.getUpperExclusive()) {
                return false;
            }
            return true;
        }
        return true;
    }

    public static Faker defaultFaker() {
        return new Faker(new Locale("zh", "CN"));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
