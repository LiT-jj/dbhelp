package com.dbhelp.dto.generate;

import lombok.Data;

@Data
public class GenerateTaskMetricsDto {
    private String status;
    private Integer progressPercent;
    private Long processedRows;
    private Long targetRows;
    /** 最近窗口估算的插入 TPS */
    private Double instantTps;
    private Boolean cancelled;
}
