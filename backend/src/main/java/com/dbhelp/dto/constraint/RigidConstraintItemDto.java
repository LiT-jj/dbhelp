package com.dbhelp.dto.constraint;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单列上的一条刚性约束（范围 / 等值集合 / 非空）。
 */
@Data
public class RigidConstraintItemDto {

    /** RANGE | EQUAL | NOT_NULL */
    private String kind;

    /** 如 COLUMN_DEFINITION */
    private String source = "COLUMN_DEFINITION";

    /** RANGE：最小值（字符串，避免大整数精度问题） */
    private String min;
    /** RANGE：最大值 */
    private String max;

    /** RANGE：是否闭区间，默认 true */
    private Boolean closed = Boolean.TRUE;

    /** EQUAL：ENUM/SET 合法取值列表 */
    private List<String> allowedValues = new ArrayList<>();

    /** SET 时为 SET，便于前端区分逗号分隔子集语义 */
    private String subtype;

    /** 补充说明（如 DECIMAL 精度、FLOAT 近似等） */
    private String notes;
}
