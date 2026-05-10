package com.dbhelp.generate.constraint;

import java.util.ArrayList;
import java.util.List;

/**
 * 单列造数时的约束提示：在 {@link com.dbhelp.generate.faker.FakedValueProducer#valueFor}
 * 内优先按等值/范围池与关联边界生成。
 */
public final class ColumnConstraintHints {

    private final List<String> equalCandidates = new ArrayList<>();
    private final List<RangeSpan> rangeCandidates = new ArrayList<>();
    private boolean notNull;

    /** 关联约束：本列 &gt; 引用列时，数值下界（不含） */
    private Double lowerExclusive;
    /** 关联约束：本列 &lt; 引用列时，数值上界（不含）；或生成引用列时来自左侧的上界 */
    private Double upperExclusive;

    public List<String> getEqualCandidates() {
        return equalCandidates;
    }

    public List<RangeSpan> getRangeCandidates() {
        return rangeCandidates;
    }

    public boolean isNotNull() {
        return notNull;
    }

    public void setNotNull(boolean notNull) {
        this.notNull = notNull;
    }

    public Double getLowerExclusive() {
        return lowerExclusive;
    }

    public void setLowerExclusive(Double lowerExclusive) {
        this.lowerExclusive = lowerExclusive;
    }

    public Double getUpperExclusive() {
        return upperExclusive;
    }

    public void setUpperExclusive(Double upperExclusive) {
        this.upperExclusive = upperExclusive;
    }

    public ColumnConstraintHints copy() {
        ColumnConstraintHints c = new ColumnConstraintHints();
        c.equalCandidates.addAll(this.equalCandidates);
        for (RangeSpan r : this.rangeCandidates) {
            c.rangeCandidates.add(new RangeSpan(r.getMin(), r.getMax()));
        }
        c.notNull = this.notNull;
        c.lowerExclusive = this.lowerExclusive;
        c.upperExclusive = this.upperExclusive;
        return c;
    }

    public void clearRelateBounds() {
        this.lowerExclusive = null;
        this.upperExclusive = null;
    }
}
