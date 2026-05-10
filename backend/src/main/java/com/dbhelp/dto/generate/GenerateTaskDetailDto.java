package com.dbhelp.dto.generate;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class GenerateTaskDetailDto extends GenerateTaskSummaryDto {
    private String configJson;
    private String checkpointJson;
    private String errorMessage;

    /** 由 {@code warning_json} 解析；批次级非致命错误（如 JDBC 插入失败） */
    private List<GenerateTaskWarningEntryDto> warningEntries = new ArrayList<>();

    /** 原始 JSON，便于前端或其它客户端自行解析 */
    private String warningJson;
}
