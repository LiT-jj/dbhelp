package com.dbhelp.generate.sink;

import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.generate.types.CanonicalTypeResolver;
import com.dbhelp.generate.types.SqlCanonicalType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 按列 JDBC 语义类型把行值规范成 {@link PreparedStatement#setObject} 可接受的类型，
 * 避免 DATETIME 列收到毫秒数字或空串导致 MySQL Data truncation。
 */
public final class JdbcWriteValueNormalizer {

    private JdbcWriteValueNormalizer() {
    }

    public static Object normalize(String dbTypeUpper, ColumnEntry col, Object raw) {
        if (col == null) {
            return raw;
        }
        SqlCanonicalType t = CanonicalTypeResolver.resolve(dbTypeUpper, col);
        switch (t) {
            case DATE:
                return asSqlDate(raw, col);
            case TIME:
                return asSqlTime(raw, col);
            case DATETIME:
                return asSqlTimestamp(raw, col);
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                return sanitizeFiniteNumeric(raw, col, t);
            default:
                return raw;
        }
    }

    /**
     * Double / Float 运算可能产生 Infinity、NaN，直接 setObject 会导致 MySQL 等库写入失败；
     * 可空列置 null，否则置 0（DECIMAL 用 {@link BigDecimal#ZERO}）。
     */
    private static Object sanitizeFiniteNumeric(Object raw, ColumnEntry col, SqlCanonicalType t) {
        if (!(raw instanceof Number)) {
            return raw;
        }
        double dv = ((Number) raw).doubleValue();
        if (Double.isFinite(dv)) {
            return raw;
        }
        if (Boolean.TRUE.equals(col.getNullable())) {
            return null;
        }
        switch (t) {
            case FLOAT:
                return 0.0f;
            case DOUBLE:
                return 0.0d;
            case DECIMAL:
                return BigDecimal.ZERO;
            default:
                return raw;
        }
    }

    private static Object asSqlTimestamp(Object raw, ColumnEntry col) {
        if (raw == null) {
            return nullableOrNowTs(col);
        }
        if (raw instanceof Timestamp) {
            return raw;
        }
        if (raw instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) raw).getTime());
        }
        if (raw instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) raw);
        }
        if (raw instanceof LocalDate) {
            return Timestamp.valueOf(((LocalDate) raw).atStartOfDay());
        }
        if (raw instanceof Instant) {
            return Timestamp.from((Instant) raw);
        }
        if (raw instanceof Number) {
            return new Timestamp(epochMillis(((Number) raw).longValue()));
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return nullableOrNowTs(col);
            }
            if (s.chars().allMatch(Character::isDigit)) {
                try {
                    return new Timestamp(epochMillis(Long.parseLong(s)));
                } catch (NumberFormatException ignored) {
                    return nullableOrNowTs(col);
                }
            }
            try {
                return Timestamp.valueOf(s);
            } catch (IllegalArgumentException ignored) {
                // 常见 "yyyy-MM-dd HH:mm:ss" 容错
                try {
                    String t = s.length() >= 19 ? s.substring(0, 19).trim().replace('/', '-') : s;
                    return Timestamp.valueOf(t);
                } catch (IllegalArgumentException ignored2) {
                    return nullableOrNowTs(col);
                }
            }
        }
        return raw;
    }

    private static Object asSqlDate(Object raw, ColumnEntry col) {
        if (raw == null) {
            return Boolean.TRUE.equals(col.getNullable()) ? null : new Date(System.currentTimeMillis());
        }
        if (raw instanceof Date) {
            return raw;
        }
        if (raw instanceof Timestamp) {
            return new Date(((Timestamp) raw).getTime());
        }
        if (raw instanceof LocalDate) {
            return Date.valueOf((LocalDate) raw);
        }
        if (raw instanceof Number) {
            return new Date(epochMillis(((Number) raw).longValue()));
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return Boolean.TRUE.equals(col.getNullable()) ? null : new Date(System.currentTimeMillis());
            }
            if (s.chars().allMatch(Character::isDigit)) {
                try {
                    return new Date(epochMillis(Long.parseLong(s)));
                } catch (NumberFormatException ignored) {
                    return Boolean.TRUE.equals(col.getNullable()) ? null : new Date(System.currentTimeMillis());
                }
            }
            try {
                return Date.valueOf(LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s));
            } catch (Exception ignored) {
                return Boolean.TRUE.equals(col.getNullable()) ? null : new Date(System.currentTimeMillis());
            }
        }
        return raw;
    }

    private static Object asSqlTime(Object raw, ColumnEntry col) {
        long dayMs = TimeUnit.DAYS.toMillis(1);
        if (raw == null) {
            return nullableOrNowTime(col);
        }
        if (raw instanceof Time) {
            return raw;
        }
        if (raw instanceof Timestamp) {
            long ms = ((Timestamp) raw).getTime() % dayMs;
            if (ms < 0) {
                ms += dayMs;
            }
            return new Time(ms);
        }
        if (raw instanceof Number) {
            long ms = epochMillis(((Number) raw).longValue()) % dayMs;
            if (ms < 0) {
                ms += dayMs;
            }
            return new Time(ms);
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return nullableOrNowTime(col);
            }
            if (s.chars().allMatch(Character::isDigit)) {
                try {
                    long ms = epochMillis(Long.parseLong(s)) % dayMs;
                    if (ms < 0) {
                        ms += dayMs;
                    }
                    return new Time(ms);
                } catch (NumberFormatException ignored) {
                    return nullableOrNowTime(col);
                }
            }
            try {
                return Time.valueOf(s.length() >= 8 ? s.substring(0, 8) : s);
            } catch (IllegalArgumentException ignored) {
                return nullableOrNowTime(col);
            }
        }
        return raw;
    }

    private static Timestamp nullableOrNowTs(ColumnEntry col) {
        return Boolean.TRUE.equals(col.getNullable()) ? null : new Timestamp(System.currentTimeMillis());
    }

    private static Time nullableOrNowTime(ColumnEntry col) {
        long dayMs = TimeUnit.DAYS.toMillis(1);
        long ms = System.currentTimeMillis() % dayMs;
        return Boolean.TRUE.equals(col.getNullable()) ? null : new Time(ms);
    }

    /**
     * 大于约 10^11 视为毫秒时间戳，否则视为秒（与常见 Unix 秒级时间戳兼容）。
     */
    private static long epochMillis(long n) {
        long abs = Math.abs(n);
        if (abs > 10_000_000_000L) {
            return n;
        }
        return n * 1000L;
    }
}
