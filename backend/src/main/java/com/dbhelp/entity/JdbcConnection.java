package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dbhelp.validation.ValidationGroups;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 用户保存的 JDBC 连接配置（与前端表单字段一致）。
 */
@Getter
@Setter
@TableName("jdbc_connection")
@JsonIgnoreProperties(ignoreUnknown = true)
public class JdbcConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "name不能为空")
    private String name;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "dbType不能为空")
    @TableField("db_type")
    private String dbType;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "host不能为空")
    private String host;

    @NotNull(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "port不能为空")
    @Min(value = 1, groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "port必须大于0")
    private Integer port;

    @TableField("database_name")
    private String databaseName;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "username不能为空")
    private String username;

    /** 新建必填；更新可不传。请求体可写入；序列化响应时不输出 */
    @NotBlank(groups = ValidationGroups.Create.class, message = "password不能为空")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "driverClass不能为空")
    @TableField("driver_class")
    private String driverClass;

    @NotBlank(groups = {ValidationGroups.Create.class, ValidationGroups.Update.class}, message = "urlTemplate不能为空")
    @TableField("url_template")
    private String urlTemplate;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
