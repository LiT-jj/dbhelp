package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * URL 模板占位符顺序（与 {@link DatabaseType} 一对多）。
 */
@Getter
@Setter
@TableName("database_type_placeholder")
public class DatabaseTypePlaceholder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long databaseTypeId;
    private Integer placeholderIdx;
    private String placeholderKey;
}
