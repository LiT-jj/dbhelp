package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 持久化的数据库品种元数据（驱动、URL 模板、默认端口等）。
 * <p>占位符列表与 JDBC URL 参数行由 {@link com.dbhelp.service.DatabaseTypeService} 额外装载。</p>
 */
@Getter
@Setter
@TableName("database_type")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseType {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String displayName;
    private String driverClass;
    private String urlTemplate;
    private Integer defaultPort;
    private String dialectFamily;
    private UrlParameterStyle urlParameterStyle;
    private String remark;
    private Integer sortOrder;

    @TableField(exist = false)
    private List<String> urlPlaceholderKeys = new ArrayList<String>();

    @TableField(exist = false)
    private List<JdbcUrlParameter> jdbcUrlParameters = new ArrayList<JdbcUrlParameter>();
}
