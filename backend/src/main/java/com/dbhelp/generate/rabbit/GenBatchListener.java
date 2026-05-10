package com.dbhelp.generate.rabbit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.entity.GenerateTaskStatus;
import com.dbhelp.generate.config.TaskConnectionPayloadFactory;
import com.dbhelp.generate.sink.CsvAppendSink;
import com.dbhelp.generate.sink.JdbcBatchInsertSink;
import com.dbhelp.mapper.GenerateTaskMapper;
import com.dbhelp.service.generate.GenerateTaskMetricsRegistry;
import com.dbhelp.service.generate.GenerateTaskWarningAppender;
import com.dbhelp.service.metadata.ConnectionPayloadResolver;
import com.dbhelp.service.metadata.ResolvedConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 消费造数批次：解析任务连接、写入 JDBC/CSV，并更新进度与终态。
 * <p>
 * JDBC 单批写入失败时仅记录日志并跳过本批进度累加，不抛异常，以便 ACK 并继续消费后续消息；
 * 连接解析等非插入异常仍向上抛出，由 {@link #onMessage} 标记任务失败（可按需再收窄）。
 */
@Component
@ConditionalOnProperty(prefix = "dbhelp.generate.rabbit", name = "enabled", havingValue = "true")
public class GenBatchListener {

    private static final Logger log = LoggerFactory.getLogger(GenBatchListener.class);

    private final GenerateTaskMapper generateTaskMapper;
    private final ObjectMapper objectMapper;
    private final ConnectionPayloadResolver connectionPayloadResolver;
    private final GenerateTaskMetricsRegistry metricsRegistry;
    private final GenRabbitPendingCoordinator pendingCoordinator;
    private final GenerateTaskWarningAppender warningAppender;

    public GenBatchListener(
            GenerateTaskMapper generateTaskMapper,
            ObjectMapper objectMapper,
            ConnectionPayloadResolver connectionPayloadResolver,
            GenerateTaskMetricsRegistry metricsRegistry,
            GenRabbitPendingCoordinator pendingCoordinator,
            GenerateTaskWarningAppender warningAppender) {
        this.generateTaskMapper = generateTaskMapper;
        this.objectMapper = objectMapper;
        this.connectionPayloadResolver = connectionPayloadResolver;
        this.metricsRegistry = metricsRegistry;
        this.pendingCoordinator = pendingCoordinator;
        this.warningAppender = warningAppender;
    }

    @RabbitListener(queues = "${dbhelp.generate.rabbit.queue}", ackMode = "MANUAL")
    public void onMessage(
            GenBatchMessage msg,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        long taskId = msg.getTaskId();
        try {
            handle(msg);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("造数批次消费失败 taskId={}", taskId, e);
            patchFailed(taskId, truncate(e.getMessage(), 3900));
            channel.basicNack(deliveryTag, false, false);
        } finally {
            pendingCoordinator.consumed(taskId);
        }
    }

    private void handle(GenBatchMessage msg) throws Exception {
        long taskId = msg.getTaskId();
        if (msg.getRows() == null || msg.getRows().isEmpty()) {
            return;
        }
        GenerateTask task = generateTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在，跳过批次 taskId={}", taskId);
            return;
        }
        String st = task.getStatus();
        if (GenerateTaskStatus.CANCELLED.name().equals(st) || GenerateTaskStatus.FAILED.name().equals(st)) {
            return;
        }
        if (metricsRegistry.isCancelled(taskId)) {
            return;
        }

        String sink = msg.getSinkType() == null ? "JDBC" : msg.getSinkType().trim().toUpperCase(Locale.ROOT);
        if ("CSV".equals(sink)) {
            CsvAppendSink.appendBatch(
                    Paths.get(msg.getCsvRelativePath()),
                    msg.getColumns(),
                    msg.getRows(),
                    msg.isCsvWriteHeader());
        } else {
            JsonNode root = objectMapper.readTree(task.getConfigJson());
            ConnectionPayload connPayload = new ConnectionPayload();
            TaskConnectionPayloadFactory.fillConnection(root, connPayload);
            ResolvedConnection rc = connectionPayloadResolver.resolve(connPayload);
            String dbType = rc.getDbType();
            try {
                JdbcBatchInsertSink.insertBatch(
                        rc,
                        dbType,
                        msg.getCatalog(),
                        msg.getSchema(),
                        msg.getTable(),
                        msg.getColumns(),
                        msg.getRows());
            } catch (Exception e) {
                log.warn(
                        "JDBC 批量插入失败，跳过本批进度（详情见任务 warningEntries） taskId={} catalog={} schema={} table={} err={}",
                        taskId,
                        msg.getCatalog(),
                        msg.getSchema(),
                        msg.getTable(),
                        e.getMessage());
                warningAppender.appendJdbcBatchWarning(
                        taskId, msg.getCatalog(), msg.getSchema(), msg.getTable(), e);
                return;
            }
        }

        task = generateTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (GenerateTaskStatus.CANCELLED.name().equals(task.getStatus())) {
            return;
        }

        long cap = safeLong(task.getTargetRows());
        if (cap <= 0) {
            cap = msg.getTotalRows();
        }
        long prev = safeLong(task.getProcessedRows());
        int batch = msg.getRows().size();
        long newProcessed = cap > 0 ? Math.min(prev + batch, cap) : prev + batch;
        int pct = cap <= 0 ? 100 : (int) Math.min(100L, newProcessed * 100L / cap);
        String cp = "{\"totalDoneRows\":" + newProcessed + "}";
        boolean done = cap > 0 && newProcessed >= cap;
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getProcessedRows, newProcessed)
                        .set(GenerateTask::getProgressPercent, pct)
                        .set(GenerateTask::getCheckpointJson, cp)
                        .set(done, GenerateTask::getStatus, GenerateTaskStatus.SUCCESS.name())
                        .set(done, GenerateTask::getErrorMessage, null)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
        metricsRegistry.recordRows(taskId, batch, System.currentTimeMillis());
    }

    private void patchFailed(long taskId, String err) {
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getStatus, GenerateTaskStatus.FAILED.name())
                        .set(GenerateTask::getErrorMessage, err)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
