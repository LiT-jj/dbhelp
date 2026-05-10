package com.dbhelp.generate.constraint;

/**
 * 数值范围约束（来自 RANGE kind，min/max 可为 null 表示无界）。
 */
public final class RangeSpan {
    private final Double min;
    private final Double max;

    public RangeSpan(Double min, Double max) {
        this.min = min;
        this.max = max;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }
}
