package com.dbhelp.service.generate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.mapper.GenerateTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将非致命运行期错误（如单批 JDBC 失败但继续消费）追加到任务的 {@code warning_json}。
 */
@Service
public class GenerateTaskWarningAppender {

    private static final int MAX_ENTRIES = 100;
    private static final int MAX_MESSAGE_LEN = 2000;
    private static final int MAX_JSON_CHARS = 15000;

    private final GenerateTaskMapper generateTaskMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<Long, Object>();

    public GenerateTaskWarningAppender(GenerateTaskMapper generateTaskMapper, ObjectMapper objectMapper) {
        this.generateTaskMapper = generateTaskMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 线程安全地追加一条告警；失败时仅吞掉异常，避免影响主流程。
     */
    public void appendJdbcBatchWarning(
            long taskId, String catalog, String schema, String table, Throwable error) {
        Object lock = locks.computeIfAbsent(taskId, k -> new Object());
        synchronized (lock) {
            try {
                doAppend(taskId, catalog, schema, table, error);
            } catch (Exception e) {
                // 不向上抛，避免消费者线程因写告警失败而中断
            }
        }
    }

    private void doAppend(long taskId, String catalog, String schema, String table, Throwable error) throws Exception {
        GenerateTask task = generateTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        ArrayNode arr;
        if (StringUtils.hasText(task.getWarningJson())) {
            try {
                arr = (ArrayNode) objectMapper.readTree(task.getWarningJson());
                if (!arr.isArray()) {
                    arr = objectMapper.createArrayNode();
                }
            } catch (Exception e) {
                arr = objectMapper.createArrayNode();
            }
        } else {
            arr = objectMapper.createArrayNode();
        }

        ObjectNode item = objectMapper.createObjectNode();
        item.put("at", Instant.now().toString());
        item.put("catalog", catalog != null ? catalog : "");
        item.put("schema", schema != null ? schema : "");
        item.put("table", table != null ? table : "");
        String msg = summarize(error);
        item.put("message", msg);
        arr.add(item);

        while (arr.size() > MAX_ENTRIES) {
            arr.remove(0);
        }

        String json = objectMapper.writeValueAsString(arr);
        while (json.length() > MAX_JSON_CHARS && arr.size() > 1) {
            arr.remove(0);
            json = objectMapper.writeValueAsString(arr);
        }

        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getWarningJson, json)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, taskId));
    }

    private static String summarize(Throwable error) {
        String msg = error.getMessage();
        if (!StringUtils.hasText(msg) && error.getCause() != null) {
            msg = error.getCause().getMessage();
        }
        if (!StringUtils.hasText(msg)) {
            msg = error.getClass().getSimpleName();
        }
        return msg.length() <= MAX_MESSAGE_LEN ? msg : msg.substring(0, MAX_MESSAGE_LEN);
    }
}
