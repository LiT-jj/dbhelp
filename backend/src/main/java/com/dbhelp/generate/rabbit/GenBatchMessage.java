package com.dbhelp.generate.rabbit;

import com.dbhelp.dto.metadata.ColumnEntry;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单批造数投递到 RabbitMQ 的载荷；由消费者解析任务库连接并写入 JDBC/CSV。
 */
@Data
public class GenBatchMessage {

    private long taskId;

    /** 与 {@link com.dbhelp.entity.GenerateTask#getTargetRows()} 一致，便于校验 */
    private long totalRows;

    private String sinkType;

    private String dbType;

    private String catalog;
    private String schema;
    private String table;

    private List<ColumnEntry> columns = new ArrayList<ColumnEntry>();

    private List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();

    /** CSV 模式下相对工作目录的路径，例如 data/gen-1-x-t.csv */
    private String csvRelativePath;

    /** 是否写 CSV 表头（每个文件首条批次为 true） */
    private boolean csvWriteHeader;
}
