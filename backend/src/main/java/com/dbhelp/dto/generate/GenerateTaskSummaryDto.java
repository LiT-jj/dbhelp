package com.dbhelp.dto.generate;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GenerateTaskSummaryDto {
    private Long id;
    private String name;
    private String description;
    private String status;
    private Integer progressPercent;
    private Long processedRows;
    private Long targetRows;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
