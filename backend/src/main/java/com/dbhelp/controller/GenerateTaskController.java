package com.dbhelp.controller;

import com.dbhelp.common.JsonMaps;
import com.dbhelp.dto.generate.GenerateTaskCreateRequest;
import com.dbhelp.dto.generate.GenerateTaskDetailDto;
import com.dbhelp.dto.generate.GenerateTaskMetricsDto;
import com.dbhelp.dto.generate.GenerateTaskPageDto;
import com.dbhelp.entity.GenerateTask;
import com.dbhelp.service.generate.GenerateTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/generate/tasks")
@Validated
public class GenerateTaskController {

    private final GenerateTaskService generateTaskService;

    public GenerateTaskController(GenerateTaskService generateTaskService) {
        this.generateTaskService = generateTaskService;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody GenerateTaskCreateRequest request) throws Exception {
        GenerateTask task = generateTaskService.create(request);
        return JsonMaps.successExtra(
                "id", task.getId(),
                "name", task.getName(),
                "description", task.getDescription(),
                "status", task.getStatus());
    }

    @GetMapping
    public GenerateTaskPageDto list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return generateTaskService.pageSummaries(name, description, status, page, pageSize);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        generateTaskService.delete(id);
        return JsonMaps.success();
    }

    @GetMapping("/{id}")
    public GenerateTaskDetailDto detail(@PathVariable long id) {
        return generateTaskService.getDetail(id);
    }

    @GetMapping("/{id}/metrics")
    public GenerateTaskMetricsDto metrics(@PathVariable long id) {
        return generateTaskService.metrics(id);
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable long id) {
        generateTaskService.prepareAndStart(id, false);
        return JsonMaps.success();
    }

    @PostMapping("/{id}/retry")
    public Map<String, Object> retry(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean resume = true;
        if (body != null && body.containsKey("resumeFromCheckpoint")) {
            resume = Boolean.parseBoolean(String.valueOf(body.get("resumeFromCheckpoint")));
        }
        generateTaskService.retry(id, resume);
        return JsonMaps.success();
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id) {
        generateTaskService.cancel(id);
        return JsonMaps.success();
    }
}
