package com.dbhelp.dto.constraint;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RigidColumnConstraintDto {

    private String name;

    /** {@link java.sql.Types}，可为 null */
    private Integer jdbcType;

    /** information_schema.columns.DATA_TYPE */
    private String dataType;

    /** information_schema.columns.COLUMN_TYPE 原文 */
    private String columnType;

    private Boolean nullable;

    /** 数值列 unsigned（MySQL） */
    private Boolean unsigned;

    private List<RigidConstraintItemDto> constraints = new ArrayList<>();
}
