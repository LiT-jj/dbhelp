package com.dbhelp.controller;

import com.dbhelp.entity.DatabaseType;
import com.dbhelp.service.DatabaseTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/database-types")
public class DatabaseTypeController {

    private final DatabaseTypeService databaseTypeService;

    public DatabaseTypeController(DatabaseTypeService databaseTypeService) {
        this.databaseTypeService = databaseTypeService;
    }

    @GetMapping
    public List<DatabaseType> list() {
        return databaseTypeService.listAllWithDetails();
    }

    @GetMapping("/{code}")
    public DatabaseType get(@PathVariable String code) {
        return databaseTypeService.findByCodeWithDetails(code)
                .orElseThrow(() -> new IllegalArgumentException("未知的数据库类型: " + code));
    }
}
