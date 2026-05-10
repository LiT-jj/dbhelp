package com.dbhelp.dto.generate;

import lombok.Data;

/**
 * 造数过程中非致命失败（如 JDBC 批次写入失败但继续消费）的一条告警记录。
 */
@Data
public class GenerateTaskWarningEntryDto {

    /** ISO-8601 时间字符串 */
    private String at;

    private String catalog;
    private String schema;
    private String table;

    /** 截断后的异常摘要 */
    private String message;
}
