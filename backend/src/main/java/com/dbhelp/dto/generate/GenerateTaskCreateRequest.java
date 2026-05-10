package com.dbhelp.dto.generate;

import com.dbhelp.dto.metadata.ConnectionPayload;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 创建造数任务：连接（二选一）+ 目标库表 + 约束 JSON + 造数参数。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateTaskCreateRequest {

    @NotBlank
    private String name;

    /** 可选；为空时由服务端根据目标表与 rowCount 生成 */
    private String description;

    /** SAVED：仅用 connectionId；INLINE：使用 inlineConnection */
    @NotBlank
    private String connectionMode;

    private Long connectionId;

    @Valid
    private ConnectionPayload inlineConnection;

    @NotEmpty
    private List<@Valid TargetTableRefDto> targets = new ArrayList<>();

    /** 刚性约束：每项为通用 Map（kind、scope、payload 等），后端占位阶段可为空 */
    private List<Map<String, Object>> hardConstraints = new ArrayList<>();

    private List<Map<String, Object>> softConstraints = new ArrayList<>();

    @Valid
    @NotNull
    private GenerateOptionsDto options = new GenerateOptionsDto();
}
