package com.dbhelp.repository;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryRepository {
    private final AtomicLong id = new AtomicLong(1);
    private final List<Map<String, Object>> constraints = new ArrayList<>();
    private final List<Map<String, Object>> sqlHistory = new ArrayList<>();
    private final List<Map<String, Object>> projects = new ArrayList<>();

    public long nextId() {
        return id.getAndIncrement();
    }

    public List<Map<String, Object>> constraints() {
        return constraints;
    }

    public List<Map<String, Object>> sqlHistory() {
        return sqlHistory;
    }

    public List<Map<String, Object>> projects() {
        return projects;
    }

    public static Map<String, Object> nowTimestamps(Map<String, Object> map, boolean create) {
        if (create) {
            map.put("createdAt", LocalDateTime.now().toString());
        }
        map.put("updatedAt", LocalDateTime.now().toString());
        return map;
    }

}
