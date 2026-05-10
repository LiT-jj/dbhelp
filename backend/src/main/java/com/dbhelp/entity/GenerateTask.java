package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("generate_task")
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** {@link GenerateTaskStatus} 名称 */
    private String status;

    @TableField("progress_percent")
    private Integer progressPercent;

    @TableField("processed_rows")
    private Long processedRows;

    @TableField("target_rows")
    private Long targetRows;

    @TableField("config_json")
    private String configJson;

    @TableField("checkpoint_json")
    private String checkpointJson;

    @TableField("error_message")
    private String errorMessage;

    /**
     * JSON 数组：批次级告警（如 Rabbit 消费侧 JDBC 失败仍继续时累积）。
     * 结构见 {@link com.dbhelp.dto.generate.GenerateTaskWarningEntryDto}。
     */
    @TableField("warning_json")
    private String warningJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
