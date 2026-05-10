package com.dbhelp.dto.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnEntry {
    private String name;
    /** {@link java.sql.Types} */
    private Integer jdbcType;
    private String typeName;
    private Integer columnSize;
    private Integer decimalDigits;
    private Boolean nullable;
    /** 从 1 开始 */
    private Integer ordinalPosition;
    private String columnDefault;
    private String remarks;
}
