package com.dbhelp.service.generate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.entity.GenerateTaskStatus;
import com.dbhelp.generate.pipeline.DataFakerGeneratePipeline;
import com.dbhelp.mapper.GenerateTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 造数任务的同步执行入口（由 {@link GenerateTaskAsyncExecutor} 在专用线程池中异步调用）。
 * <p>
 * 仅当 {@code config_json.targets} 为非空数组时调用 {@link DataFakerGeneratePipeline}；
 * 否则任务标记为 {@link GenerateTaskStatus#FAILED}（不再提供无 targets 的进度模拟）。
 * <p>
 * 本类负责：启动时 {@link GenerateTaskMetricsRegistry#register(long)}、
 * 异常或配置错误时 {@link GenerateTaskStatus#FAILED}、结束时 {@link GenerateTaskMetricsRegistry#unregister(long)}。
 */
@Component
public class GenerateTaskWorker {

    private final GenerateTaskMapper generateTaskMapper;

    private final GenerateTaskMetricsRegistry metricsRegistry;

    private final ObjectMapper objectMapper;

    private final DataFakerGeneratePipeline dataFakerGeneratePipeline;

    public GenerateTaskWorker(
            GenerateTaskMapper generateTaskMapper,
            GenerateTaskMetricsRegistry metricsRegistry,
            ObjectMapper objectMapper,
            DataFakerGeneratePipeline dataFakerGeneratePipeline) {
        this.generateTaskMapper = generateTaskMapper;
        this.metricsRegistry = metricsRegistry;
        this.objectMapper = objectMapper;
        this.dataFakerGeneratePipeline = dataFakerGeneratePipeline;
    }

    /**
     * 执行单个造数任务的一次运行。
     *
     * @param taskId 任务主键
     * @param resume 是否断点续跑，传给 {@link DataFakerGeneratePipeline#execute}
     */
    public void run(long taskId, boolean resume) {
        GenerateTask task = generateTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        metricsRegistry.register(taskId);
        try {
            JsonNode config = objectMapper.readTree(task.getConfigJson());
            JsonNode targetsNode = config.path("targets");
            if (!targetsNode.isArray() || targetsNode.size() == 0) {
                throw new IllegalStateException("未配置造数目标表（config.targets 必须为非空数组）");
            }
            patchTask(task, GenerateTaskStatus.RUNNING, null, null, null, null, null);
            dataFakerGeneratePipeline.execute(taskId, task, config, resume);
        } catch (Exception e) {
            generateTaskMapper.update(
                    null,
                    Wrappers.<GenerateTask>lambdaUpdate()
                            .set(GenerateTask::getStatus, GenerateTaskStatus.FAILED.name())
                            .set(GenerateTask::getErrorMessage, truncate(e.getMessage(), 3900))
                            .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                            .eq(GenerateTask::getId, taskId));
        } finally {
            metricsRegistry.unregister(taskId);
        }
    }

    /**
     * 全量更新任务字段。{@code null} 的参数表示本次不修改该列。
     */
    private void patchTask(
            GenerateTask task,
            GenerateTaskStatus status,
            Long processedRows,
            Integer progressPercent,
            String checkpointJson,
            String errorMessage,
            Long targetRows) {
        long id = task.getId();
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getStatus, status.name())
                        .set(processedRows != null, GenerateTask::getProcessedRows, processedRows)
                        .set(progressPercent != null, GenerateTask::getProgressPercent, progressPercent)
                        .set(checkpointJson != null, GenerateTask::getCheckpointJson, checkpointJson)
                        .set(errorMessage != null, GenerateTask::getErrorMessage, errorMessage)
                        .set(targetRows != null, GenerateTask::getTargetRows, targetRows)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, id));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
