package com.dbhelp.generate.pipeline;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.dto.metadata.ColumnEntry;
import com.dbhelp.dto.metadata.ColumnsMetadataRequest;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.entity.GenerateTaskStatus;
import com.dbhelp.generate.config.TaskConnectionPayloadFactory;
import com.dbhelp.generate.constraint.TargetTableConstraintPlan;
import com.dbhelp.generate.faker.FakedValueProducer;
import com.dbhelp.generate.rabbit.GenBatchMessage;
import com.dbhelp.generate.rabbit.GenBatchRabbitPublisher;
import com.dbhelp.generate.rabbit.GenRabbitPendingCoordinator;
import com.dbhelp.generate.sink.CsvAppendSink;
import com.dbhelp.generate.sink.JdbcBatchInsertSink;
import com.dbhelp.generate.types.CanonicalTypeResolver;
import com.dbhelp.generate.types.SqlCanonicalType;
import com.dbhelp.mapper.GenerateTaskMapper;
import com.dbhelp.service.generate.GenerateTaskMetricsRegistry;
import com.dbhelp.service.metadata.ConnectionPayloadResolver;
import com.dbhelp.service.metadata.DatabaseMetadataService;
import com.dbhelp.service.metadata.ResolvedConnection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;

/**
 * 根据任务配置、列元数据与 Datafaker 生成数据；direct 时进程内写入 JDBC/CSV，
 * rabbitmq 时仅投递批次消息，由 {@link com.dbhelp.generate.rabbit.GenBatchListener} 写入并更新进度。
 */
@Component
public class DataFakerGeneratePipeline {

    private static final Logger log = LoggerFactory.getLogger(DataFakerGeneratePipeline.class);

    private final DatabaseMetadataService databaseMetadataService;
    private final ConnectionPayloadResolver connectionPayloadResolver;
    private final ObjectMapper objectMapper;
    private final GenerateTaskMapper generateTaskMapper;
    private final GenerateTaskMetricsRegistry metricsRegistry;
    private final GenRabbitPendingCoordinator rabbitPending;
    private final ObjectProvider<GenBatchRabbitPublisher> rabbitPublisherProvider;
    private final long producerAwaitTimeoutMs;

    public DataFakerGeneratePipeline(
            DatabaseMetadataService databaseMetadataService,
            ConnectionPayloadResolver connectionPayloadResolver,
            ObjectMapper objectMapper,
            GenerateTaskMapper generateTaskMapper,
            GenerateTaskMetricsRegistry metricsRegistry,
            GenRabbitPendingCoordinator rabbitPending,
            ObjectProvider<GenBatchRabbitPublisher> rabbitPublisherProvider,
            @Value("${dbhelp.generate.rabbit.producer-await-timeout-ms:3600000}") long producerAwaitTimeoutMs) {
        this.databaseMetadataService = databaseMetadataService;
        this.connectionPayloadResolver = connectionPayloadResolver;
        this.objectMapper = objectMapper;
        this.generateTaskMapper = generateTaskMapper;
        this.metricsRegistry = metricsRegistry;
        this.rabbitPending = rabbitPending;
        this.rabbitPublisherProvider = rabbitPublisherProvider;
        this.producerAwaitTimeoutMs = producerAwaitTimeoutMs;
    }

    public void execute(long taskId, GenerateTask task, JsonNode root, boolean resume) throws Exception {
        JsonNode targetsNode = root.path("targets");
        if (!targetsNode.isArray() || targetsNode.size() == 0) {
            return;
        }
        List<TargetRef> targets = new ArrayList<TargetRef>();
        for (JsonNode n : targetsNode) {
            if (n == null || !n.has("table")) {
                continue;
            }
            TargetRef tr = new TargetRef();
            tr.catalog = text(n, "catalog");
            tr.schema = text(n, "schema");
            tr.table = text(n, "table");
            if (tr.table != null && !tr.table.isEmpty()) {
                targets.add(tr);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        ConnectionPayload connPayload = new ConnectionPayload();
        TaskConnectionPayloadFactory.fillConnection(root, connPayload);
        ResolvedConnection rc = connectionPayloadResolver.resolve(connPayload);
        String dbType = rc.getDbType();

        JsonNode opt = root.path("options");
        long totalRows = opt.path("rowCount").asLong(1L);
        int batchSize = Math.max(1, opt.path("batchSize").asInt(500));
        String sinkType = opt.path("sinkType").asText("JDBC").trim().toUpperCase(Locale.ROOT);
        String transport = opt.path("transport").asText("direct").trim().toLowerCase(Locale.ROOT);
        boolean wantsRabbit = "rabbitmq".equals(transport);
        GenBatchRabbitPublisher rabbitPublisher = rabbitPublisherProvider.getIfAvailable();
        boolean useRabbit = wantsRabbit && rabbitPublisher != null;
        if (wantsRabbit && rabbitPublisher == null) {
            throw new IllegalStateException(
                    "options.transport=rabbitmq 需要启用 Rabbit：在 application.yml 设置 dbhelp.generate.rabbit.enabled=true 并配置连接与队列");
        }
        if (useRabbit && resume && safeLong(task.getProcessedRows()) > 0) {
            throw new IllegalStateException("RabbitMQ 传输模式下暂不支持断点续跑（processedRows>0）");
        }

        List<Map<String, Object>> hard = readMapList(root, "hardConstraints");
        List<Map<String, Object>> soft = readMapList(root, "softConstraints");

        long[] perTarget = splitRows(totalRows, targets.size());
        long processed = resume ? safeLong(task.getProcessedRows()) : 0L;
        if (processed >= totalRows) {
            finalizeSuccess(taskId, totalRows);
            return;
        }

        Faker faker = FakedValueProducer.defaultFaker();
        Random random = new Random(taskId * 31L + System.nanoTime());
        long globalIndex = 0;
        Map<String, Boolean> csvHeader = new HashMap<String, Boolean>();

        for (int ti = 0; ti < targets.size(); ti++) {
            TargetRef tr = targets.get(ti);
            long quota = perTarget[ti];
            ColumnsMetadataRequest cm = new ColumnsMetadataRequest();
            TaskConnectionPayloadFactory.fillConnection(root, cm);
            cm.setCatalog(tr.catalog);
            cm.setSchema(tr.schema);
            cm.setTable(tr.table);
            List<ColumnEntry> allCols = databaseMetadataService.listColumns(cm);
            List<ColumnEntry> cols = filterWritableColumns(dbType, allCols);
            if (cols.isEmpty()) {
                continue;
            }
            List<String> colNames = new ArrayList<String>();
            Map<String, ColumnEntry> colByName = new LinkedHashMap<String, ColumnEntry>();
            for (ColumnEntry c : cols) {
                colNames.add(c.getName());
                colByName.put(c.getName(), c);
            }
            TargetTableConstraintPlan constraintPlan =
                    TargetTableConstraintPlan.build(tr.catalog, tr.schema, tr.table, colNames, hard, soft);
            long rowInTarget = 0;
            while (rowInTarget < quota) {
                if (metricsRegistry.isCancelled(taskId)) {
                    patchCancelled(taskId, Math.min(globalIndex, totalRows), totalRows, useRabbit);
                    return;
                }
                if (globalIndex < processed) {
                    long skip = Math.min(quota - rowInTarget, processed - globalIndex);
                    rowInTarget += skip;
                    globalIndex += skip;
                    continue;
                }
                int n = (int) Math.min(batchSize, quota - rowInTarget);
                List<Map<String, Object>> batch = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < n; i++) {
                    Map<String, Object> row = constraintPlan.generateRow(colByName, dbType, faker, random);
                    batch.add(row);
                    rowInTarget++;
                    globalIndex++;
                }
                if (useRabbit) {
                    Path file = Paths.get("data", "gen-" + taskId + "-" + safeFilePart(tr.catalog) + "-" + safeFilePart(tr.table) + ".csv");
                    String csvKey = file.toString();
                    boolean csvHeaderFlag = "CSV".equals(sinkType) && !csvHeader.containsKey(csvKey);
                    if ("CSV".equals(sinkType)) {
                        csvHeader.put(csvKey, true);
                    }
                    publishRabbitBatch(
                            taskId,
                            totalRows,
                            sinkType,
                            dbType,
                            tr.catalog,
                            tr.schema,
                            tr.table,
                            cols,
                            batch,
                            "CSV".equals(sinkType) ? csvKey : null,
                            csvHeaderFlag,
                            rabbitPublisher);
                } else if ("CSV".equals(sinkType)) {
                    Path file = Paths.get("data", "gen-" + taskId + "-" + safeFilePart(tr.catalog) + "-" + safeFilePart(tr.table) + ".csv");
                    String key = file.toString();
                    boolean header = !csvHeader.containsKey(key);
                    CsvAppendSink.appendBatch(file, cols, batch, header);
                    csvHeader.put(key, true);
                } else {
                    JdbcBatchInsertSink.insertBatch(rc, dbType, tr.catalog, tr.schema, tr.table, cols, batch);
                }
                processed = globalIndex;
                if (!useRabbit) {
                    int pct = (int) Math.min(100L, processed * 100L / Math.max(1L, totalRows));
                    String cp = "{\"totalDoneRows\":" + processed + "}";
                    patchProgress(taskId, processed, pct, cp);
                    metricsRegistry.recordRows(taskId, batch.size(), System.currentTimeMillis());
                }
            }
        }
        if (useRabbit) {
            awaitRabbitAndFinalize(taskId, totalRows);
        } else {
            finalizeSuccess(taskId, totalRows);
        }
    }

    private void publishRabbitBatch(
            long taskId,
            long totalRows,
            String sinkType,
            String dbType,
            String catalog,
            String schema,
            String table,
            List<ColumnEntry> cols,
            List<Map<String, Object>> batch,
            String csvRelativePath,
            boolean csvWriteHeader,
            GenBatchRabbitPublisher publisher) {
        GenBatchMessage m = new GenBatchMessage();
        m.setTaskId(taskId);
        m.setTotalRows(totalRows);
        m.setSinkType(sinkType);
        m.setDbType(dbType);
        m.setCatalog(catalog);
        m.setSchema(schema);
        m.setTable(table);
        m.setColumns(new ArrayList<ColumnEntry>(cols));
        m.setRows(new ArrayList<Map<String, Object>>(batch));
        m.setCsvRelativePath(csvRelativePath);
        m.setCsvWriteHeader(csvWriteHeader);
        rabbitPending.published(taskId);
        try {
            publisher.publish(m);
        } catch (RuntimeException e) {
            rabbitPending.consumed(taskId);
            throw e;
        }
    }

    private void awaitRabbitAndFinalize(long taskId, long totalRows) throws TimeoutException {
        try {
            rabbitPending.awaitZero(taskId, producerAwaitTimeoutMs);
        } catch (TimeoutException e) {
            generateTaskMapper.update(
                    null,
                    Wrappers.<GenerateTask>lambdaUpdate()
                            .set(GenerateTask::getStatus, GenerateTaskStatus.FAILED.name())
                            .set(GenerateTask::getErrorMessage, "等待 RabbitMQ 消费超时")
                            .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                            .eq(GenerateTask::getId, taskId));
            throw e;
        }
        GenerateTask t2 = generateTaskMapper.selectById(taskId);
        if (t2 == null) {
            return;
        }
        if (GenerateTaskStatus.CANCELLED.name().equals(t2.getStatus())) {
            return;
        }
        if (GenerateTaskStatus.FAILED.name().equals(t2.getStatus())) {
            throw new IllegalStateException("造数失败: " + t2.getErrorMessage());
        }
        if (!GenerateTaskStatus.SUCCESS.name().equals(t2.getStatus())) {
            finalizeSuccess(taskId, totalRows);
        }
    }

    private void finalizeSuccess(long taskId, long totalRows) {
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getStatus, GenerateTaskStatus.SUCCESS.name())
                        .set(GenerateTask::getProcessedRows, totalRows)
                        .set(GenerateTask::getProgressPercent, 100)
                        .set(GenerateTask::getCheckpointJson, "{\"totalDoneRows\":" + totalRows + "}")
                        .set(GenerateTask::getErrorMessage, null)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
    }

    private void patchCancelled(long taskId, long processed, long totalRows, boolean useRabbit) {
        int pct = (int) Math.min(100L, processed * 100L / Math.max(1L, totalRows));
        String cp = "{\"totalDoneRows\":" + processed + "}";
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getStatus, GenerateTaskStatus.CANCELLED.name())
                        .set(GenerateTask::getProcessedRows, processed)
                        .set(GenerateTask::getProgressPercent, pct)
                        .set(GenerateTask::getCheckpointJson, cp)
                        .set(GenerateTask::getErrorMessage, "用户取消")
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
        if (useRabbit) {
            try {
                rabbitPending.awaitZero(taskId, producerAwaitTimeoutMs);
            } catch (TimeoutException e) {
                log.warn("取消后等待 Rabbit 批次结束超时 taskId={}", taskId, e);
            }
        }
    }

    private void patchProgress(long taskId, long processed, int pct, String cp) {
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getProcessedRows, processed)
                        .set(GenerateTask::getProgressPercent, pct)
                        .set(GenerateTask::getCheckpointJson, cp)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
    }

    private static long[] splitRows(long total, int nTargets) {
        long[] arr = new long[nTargets];
        if (nTargets <= 0) {
            return arr;
        }
        long base = total / nTargets;
        long rem = total % nTargets;
        for (int i = 0; i < nTargets; i++) {
            arr[i] = base + (i < rem ? 1 : 0);
        }
        return arr;
    }

    private static List<ColumnEntry> filterWritableColumns(String dbTypeUpper, List<ColumnEntry> all) {
        List<ColumnEntry> out = new ArrayList<ColumnEntry>();
        for (ColumnEntry c : all) {
            if (CanonicalTypeResolver.resolve(dbTypeUpper, c) == SqlCanonicalType.BYTES) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    private List<Map<String, Object>> readMapList(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || !n.isArray()) {
            return new ArrayList<Map<String, Object>>();
        }
        try {
            return objectMapper.convertValue(n, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IllegalArgumentException e) {
            return new ArrayList<Map<String, Object>>();
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private static String safeFilePart(String s) {
        if (s == null || s.isEmpty()) {
            return "x";
        }
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static final class TargetRef {
        private String catalog;
        private String schema;
        private String table;
    }
}
