package com.dbhelp.dto.generate;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GenerateTaskPageDto {
    private List<GenerateTaskSummaryDto> records = new ArrayList<>();
    private long total;
    private long page;
    private long pageSize;
}
