package com.dbhelp.service.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.dto.generate.GenerateTaskCreateRequest;
import com.dbhelp.dto.generate.TargetTableRefDto;
import com.dbhelp.dto.generate.GenerateTaskDetailDto;
import com.dbhelp.dto.generate.GenerateTaskMetricsDto;
import com.dbhelp.dto.generate.GenerateTaskSummaryDto;
import com.dbhelp.dto.generate.GenerateOptionsDto;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.entity.GenerateTaskStatus;
import com.dbhelp.mapper.GenerateTaskMapper;
import com.dbhelp.dto.generate.GenerateTaskPageDto;
import com.dbhelp.dto.generate.GenerateTaskWarningEntryDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GenerateTaskService {

    private final GenerateTaskMapper generateTaskMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final GenerateTaskAsyncExecutor asyncExecutor;
    private final GenerateTaskMetricsRegistry metricsRegistry;

    public GenerateTaskService(
            GenerateTaskMapper generateTaskMapper,
            ObjectMapper objectMapper,
            Validator validator,
            GenerateTaskAsyncExecutor asyncExecutor,
            GenerateTaskMetricsRegistry metricsRegistry) {
        this.generateTaskMapper = generateTaskMapper;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.asyncExecutor = asyncExecutor;
        this.metricsRegistry = metricsRegistry;
    }

    @Transactional
    public GenerateTask create(GenerateTaskCreateRequest req) throws Exception {
        normalizeRequest(req);
        validateRequest(req);
        GenerateTask entity = new GenerateTask();
        entity.setName(req.getName().trim());
        entity.setDescription(buildDescription(req));
        entity.setStatus(GenerateTaskStatus.PENDING.name());
        entity.setProgressPercent(0);
        entity.setProcessedRows(0L);
        entity.setTargetRows(req.getOptions().getRowCount());
        entity.setConfigJson(objectMapper.writeValueAsString(toStoredConfig(req)));
        entity.setCheckpointJson(null);
        entity.setErrorMessage(null);
        entity.setWarningJson(null);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        generateTaskMapper.insert(entity);
        return generateTaskMapper.selectById(entity.getId());
    }

    /**
     * 分页检索：名称、描述支持模糊匹配；状态精确匹配（空表示不限）。
     */
    public GenerateTaskPageDto pageSummaries(String name, String description, String status, long page, long pageSize) {
        long p = Math.max(1L, page);
        long ps = Math.min(100L, Math.max(1L, pageSize));
        LambdaQueryWrapper<GenerateTask> q = Wrappers.lambdaQuery();
        if (StringUtils.hasText(name)) {
            q.like(GenerateTask::getName, name.trim());
        }
        if (StringUtils.hasText(description)) {
            q.like(GenerateTask::getDescription, description.trim());
        }
        if (StringUtils.hasText(status)) {
            q.eq(GenerateTask::getStatus, status.trim());
        }
        q.orderByDesc(GenerateTask::getCreatedAt);
        long total = generateTaskMapper.selectCount(q);
        long offset = (p - 1) * ps;
        // 不用 PaginationInnerInterceptor，避免与可选 JSQLParser / 其他 SQL 库在 classpath 上冲突；当前数据源为 MySQL。
        q.last(String.format("LIMIT %d OFFSET %d", ps, offset));
        List<GenerateTask> rows = generateTaskMapper.selectList(q);
        GenerateTaskPageDto out = new GenerateTaskPageDto();
        out.setTotal(total);
        out.setPage(p);
        out.setPageSize(ps);
        out.setRecords(rows.stream().map(this::toSummary).collect(Collectors.toList()));
        return out;
    }

    @Transactional
    public void delete(long id) {
        GenerateTask t = require(id);
        if (GenerateTaskStatus.RUNNING.name().equals(t.getStatus())) {
            throw new IllegalArgumentException("运行中的任务请先取消后再删除");
        }
        metricsRegistry.unregister(id);
        generateTaskMapper.deleteById(id);
    }

    public GenerateTaskDetailDto getDetail(long id) {
        GenerateTask t = require(id);
        GenerateTaskDetailDto d = new GenerateTaskDetailDto();
        copySummary(t, d);
        d.setConfigJson(t.getConfigJson());
        d.setCheckpointJson(t.getCheckpointJson());
        d.setErrorMessage(t.getErrorMessage());
        d.setWarningJson(t.getWarningJson());
        d.setWarningEntries(parseWarningEntries(t.getWarningJson()));
        return d;
    }

    public GenerateTaskMetricsDto metrics(long id) {
        GenerateTask t = require(id);
        GenerateTaskMetricsDto m = new GenerateTaskMetricsDto();
        m.setStatus(t.getStatus());
        m.setProgressPercent(t.getProgressPercent());
        m.setProcessedRows(t.getProcessedRows());
        m.setTargetRows(t.getTargetRows());
        m.setInstantTps(metricsRegistry.computeInstantTps(id));
        Boolean c = metricsRegistry.isRegisteredCancelled(id);
        m.setCancelled(c != null ? c : false);
        return m;
    }

    @Transactional
    public void prepareAndStart(long id, boolean resume) {
        GenerateTask t = require(id);
        if (!GenerateTaskStatus.PENDING.name().equals(t.getStatus())) {
            throw new IllegalArgumentException("仅 PENDING 状态可启动，当前: " + t.getStatus());
        }
        if (!resume) {
            generateTaskMapper.update(
                    null,
                    Wrappers.<GenerateTask>lambdaUpdate()
                            .set(GenerateTask::getProcessedRows, 0L)
                            .set(GenerateTask::getProgressPercent, 0)
                            .set(GenerateTask::getCheckpointJson, null)
                            .set(GenerateTask::getErrorMessage, null)
                            .set(GenerateTask::getWarningJson, null)
                            .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                            .eq(GenerateTask::getId, id));
        }
        asyncExecutor.execute(id, resume);
    }

    @Transactional
    public void retry(long id, boolean resumeFromCheckpoint) {
        GenerateTask t = require(id);
        String st = t.getStatus();
        boolean failedOrCancelled =
                GenerateTaskStatus.FAILED.name().equals(st) || GenerateTaskStatus.CANCELLED.name().equals(st);
        boolean successRerun =
                GenerateTaskStatus.SUCCESS.name().equals(st) && !resumeFromCheckpoint;
        if (!failedOrCancelled && !successRerun) {
            if (GenerateTaskStatus.SUCCESS.name().equals(st)) {
                throw new IllegalArgumentException("已成功的任务请使用重头执行（不可从断点继续），当前: " + st);
            }
            throw new IllegalArgumentException("仅 FAILED、CANCELLED 可重试，或 SUCCESS 可重头执行，当前: " + st);
        }
        generateTaskMapper.update(
                null,
                Wrappers.<GenerateTask>lambdaUpdate()
                        .set(GenerateTask::getStatus, GenerateTaskStatus.PENDING.name())
                        .set(GenerateTask::getErrorMessage, null)
                        .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                        .eq(GenerateTask::getId, id));
        if (!resumeFromCheckpoint) {
            generateTaskMapper.update(
                    null,
                    Wrappers.<GenerateTask>lambdaUpdate()
                            .set(GenerateTask::getProcessedRows, 0L)
                            .set(GenerateTask::getProgressPercent, 0)
                            .set(GenerateTask::getCheckpointJson, null)
                            .set(GenerateTask::getWarningJson, null)
                            .set(GenerateTask::getUpdatedAt, LocalDateTime.now())
                            .eq(GenerateTask::getId, id));
        }
        asyncExecutor.execute(id, resumeFromCheckpoint);
    }

    @Transactional
    public void cancel(long id) {
        GenerateTask t = require(id);
        if (!GenerateTaskStatus.RUNNING.name().equals(t.getStatus())) {
            throw new IllegalArgumentException("仅 RUNNING 可取消，当前: " + t.getStatus());
        }
        metricsRegistry.cancel(id);
    }

    private GenerateTask require(long id) {
        GenerateTask t = generateTaskMapper.selectById(id);
        if (t == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        return t;
    }

    private void normalizeRequest(GenerateTaskCreateRequest req) {
        if (req.getOptions() == null) {
            req.setOptions(new GenerateOptionsDto());
        }
        if (!StringUtils.hasText(req.getOptions().getSinkType())) {
            req.getOptions().setSinkType("JDBC");
        }
        if (!StringUtils.hasText(req.getOptions().getTransport())) {
            req.getOptions().setTransport("direct");
        }
        if (req.getTargets() == null) {
            req.setTargets(java.util.Collections.emptyList());
        }
        if (req.getHardConstraints() == null) {
            req.setHardConstraints(java.util.Collections.emptyList());
        }
        if (req.getSoftConstraints() == null) {
            req.setSoftConstraints(java.util.Collections.emptyList());
        }
    }

    private void validateRequest(GenerateTaskCreateRequest req) {
        Set<ConstraintViolation<GenerateTaskCreateRequest>> v1 = validator.validate(req);
        if (!v1.isEmpty()) {
            throw new IllegalArgumentException(v1.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; ")));
        }
        String mode = req.getConnectionMode() == null ? "" : req.getConnectionMode().trim().toUpperCase();
        if ("SAVED".equals(mode)) {
            if (req.getConnectionId() == null) {
                throw new IllegalArgumentException("SAVED 模式必须提供 connectionId");
            }
        } else if ("INLINE".equals(mode)) {
            ConnectionPayload inline = req.getInlineConnection();
            if (inline == null) {
                throw new IllegalArgumentException("INLINE 模式必须提供 inlineConnection");
            }
            Set<ConstraintViolation<ConnectionPayload>> v2 =
                    validator.validate(inline, ConnectionPayload.Inline.class);
            if (!v2.isEmpty()) {
                throw new IllegalArgumentException(v2.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; ")));
            }
        } else {
            throw new IllegalArgumentException("connectionMode 必须是 SAVED 或 INLINE");
        }
    }

    private Map<String, Object> toStoredConfig(GenerateTaskCreateRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 1);
        m.put("connectionMode", req.getConnectionMode());
        m.put("connectionId", req.getConnectionId());
        m.put("inlineConnection", req.getInlineConnection());
        m.put("targets", req.getTargets());
        m.put("hardConstraints", req.getHardConstraints());
        m.put("softConstraints", req.getSoftConstraints());
        m.put("options", req.getOptions());
        return m;
    }

    private String buildDescription(GenerateTaskCreateRequest req) {
        if (StringUtils.hasText(req.getDescription())) {
            return req.getDescription().trim();
        }
        StringBuilder sb = new StringBuilder("为 ");
        for (int i = 0; i < req.getTargets().size(); i++) {
            if (i > 0) {
                sb.append("，");
            }
            TargetTableRefDto t = req.getTargets().get(i);
            sb.append(t.getCatalog()).append('.').append(t.getTable());
        }
        sb.append(" 造 ").append(req.getOptions().getRowCount()).append(" 条数据");
        return sb.toString();
    }

    private GenerateTaskSummaryDto toSummary(GenerateTask t) {
        GenerateTaskSummaryDto d = new GenerateTaskSummaryDto();
        copySummary(t, d);
        return d;
    }

    private List<GenerateTaskWarningEntryDto> parseWarningEntries(String warningJson) {
        if (!StringUtils.hasText(warningJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(warningJson, new TypeReference<List<GenerateTaskWarningEntryDto>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static void copySummary(GenerateTask t, GenerateTaskSummaryDto d) {
        d.setId(t.getId());
        d.setName(t.getName());
        d.setDescription(t.getDescription());
        d.setStatus(t.getStatus());
        d.setProgressPercent(t.getProgressPercent());
        d.setProcessedRows(t.getProcessedRows());
        d.setTargetRows(t.getTargetRows());
        d.setCreatedAt(t.getCreatedAt());
        d.setUpdatedAt(t.getUpdatedAt());
    }
}
