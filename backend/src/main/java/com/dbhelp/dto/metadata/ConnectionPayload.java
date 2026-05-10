package com.dbhelp.dto.metadata;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 连接信息：可使用已保存的 {@code connectionId}，或直接传 JDBC 连接字段（与保存连接时一致）。
 * <p>直接传字段时走 {@link Inline} 分组校验（与 {@link com.dbhelp.service.metadata.ConnectionPayloadResolver} 配合）。</p>
 */
@Data
public class ConnectionPayload {

    /** 直接传连接字段时的分组 */
    public interface Inline {
    }

    /** 可选；若提供则从库读取完整连接（含密码） */
    private Long connectionId;

    @NotBlank(groups = Inline.class, message = "dbType不能为空")
    private String dbType;

    @NotBlank(groups = Inline.class, message = "host不能为空")
    private String host;

    @NotNull(groups = Inline.class, message = "port不能为空")
    @Min(value = 1, groups = Inline.class, message = "port必须大于0")
    private Integer port;

    @NotBlank(groups = Inline.class, message = "databaseName不能为空")
    private String databaseName;

    @NotBlank(groups = Inline.class, message = "username不能为空")
    private String username;

    @NotBlank(groups = Inline.class, message = "password不能为空")
    private String password;

    @NotBlank(groups = Inline.class, message = "driverClass不能为空")
    private String driverClass;

    @NotBlank(groups = Inline.class, message = "urlTemplate不能为空")
    private String urlTemplate;
}
