package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 某一种 {@link DatabaseType} 在 JDBC URL 上支持的单个参数（默认值与描述）。
 */
@Getter
@Setter
@TableName("jdbc_url_parameter")
public class JdbcUrlParameter {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long databaseTypeId;
    private String name;
    private String defaultValue;
    private String description;
    private Boolean recommended;
    private Integer sortOrder;
}
