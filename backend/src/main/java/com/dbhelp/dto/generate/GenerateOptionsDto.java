package com.dbhelp.dto.generate;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class GenerateOptionsDto {

    @NotNull
    @Min(1)
    @Max(10_000_000)
    private Long rowCount = 1000L;

    @NotNull
    @Min(1)
    @Max(64)
    private Integer concurrency = 2;

    @NotNull
    @Min(1)
    @Max(50_000)
    private Integer batchSize = 500;

    /** JDBC：批量插入；CSV：追加写文件 */
    private String sinkType = "JDBC";

    /**
     * direct：编排线程内生成并写入；rabbitmq：生成后投递 MQ，由消费者写入（需启用 Rabbit 配置）。
     */
    private String transport = "direct";
}
