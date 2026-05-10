package com.dbhelp.controller;

import com.dbhelp.common.JsonMaps;
import com.dbhelp.entity.JdbcConnection;
import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.service.JdbcConnectionService;
import com.dbhelp.service.JdbcConnectionTester;
import com.dbhelp.validation.ValidationGroups;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final JdbcConnectionService jdbcConnectionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final JdbcConnectionTester jdbcConnectionTester;

    public ConnectionController(
            JdbcConnectionService jdbcConnectionService,
            ObjectMapper objectMapper,
            Validator validator,
            JdbcConnectionTester jdbcConnectionTester) {
        this.jdbcConnectionService = jdbcConnectionService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.jdbcConnectionTester = jdbcConnectionTester;
    }

    @GetMapping
    public List<JdbcConnection> list() {
        return jdbcConnectionService.listAll();
    }

    @GetMapping("/{id}")
    public JdbcConnection get(@PathVariable long id) {
        return jdbcConnectionService.requireById(id);
    }

    @PostMapping
    public JdbcConnection create(@Validated(ValidationGroups.Create.class) @RequestBody JdbcConnection req) {
        return jdbcConnectionService.create(req);
    }

    @PutMapping("/{id}")
    public JdbcConnection update(
            @PathVariable long id,
            @Validated(ValidationGroups.Update.class) @RequestBody JdbcConnection req) {
        return jdbcConnectionService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        jdbcConnectionService.delete(id);
        return JsonMaps.success();
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> req) {
        try {
            ConnectionPayload payload = JdbcConnectionTester.payloadFromMap(req);
            jdbcConnectionTester.verify(payload);
            return JsonMaps.of("success", true, "message", "连接成功");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return JsonMaps.of("success", false, "message", msg);
        }
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<JdbcConnection> exportConfig() {
        return jdbcConnectionService.listAll();
    }

    @PostMapping("/import")
    public Map<String, Object> importConfig(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                return JsonMaps.successExtra("imported", 0);
            }
            List<JdbcConnection> list = objectMapper.readValue(content.getBytes(StandardCharsets.UTF_8),
                    new TypeReference<List<JdbcConnection>>() {
                    });
            int n = 0;
            for (int i = 0; i < list.size(); i++) {
                JdbcConnection c = list.get(i);
                Set<ConstraintViolation<JdbcConnection>> violations =
                        validator.validate(c, ValidationGroups.Create.class);
                if (!violations.isEmpty()) {
                    String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
                    throw new IllegalArgumentException("导入第 " + (i + 1) + " 条校验失败: " + msg);
                }
                jdbcConnectionService.create(c);
                n++;
            }
            return JsonMaps.successExtra("imported", n);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("导入解析失败: " + ex.getMessage());
        }
    }
}
