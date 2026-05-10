package com.dbhelp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.entity.JdbcConnection;
import com.dbhelp.mapper.JdbcConnectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JdbcConnectionService {

    private final JdbcConnectionMapper jdbcConnectionMapper;

    public JdbcConnectionService(JdbcConnectionMapper jdbcConnectionMapper) {
        this.jdbcConnectionMapper = jdbcConnectionMapper;
    }

    public List<JdbcConnection> listAll() {
        return jdbcConnectionMapper.selectList(Wrappers.<JdbcConnection>lambdaQuery().orderByDesc(JdbcConnection::getUpdatedAt));
    }

    public JdbcConnection requireById(Long id) {
        JdbcConnection c = jdbcConnectionMapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("连接不存在: " + id);
        }
        return c;
    }

    public JdbcConnection create(JdbcConnection incoming) {
        LocalDateTime now = LocalDateTime.now();
        incoming.setId(null);
        incoming.setCreatedAt(now);
        incoming.setUpdatedAt(now);
        jdbcConnectionMapper.insert(incoming);
        return jdbcConnectionMapper.selectById(incoming.getId());
    }

    public JdbcConnection update(Long id, JdbcConnection patch) {
        JdbcConnection existing = requireById(id);
        existing.setName(patch.getName());
        existing.setDbType(patch.getDbType());
        existing.setHost(patch.getHost());
        existing.setPort(patch.getPort());
        existing.setDatabaseName(patch.getDatabaseName());
        existing.setUsername(patch.getUsername());
        existing.setDriverClass(patch.getDriverClass());
        existing.setUrlTemplate(patch.getUrlTemplate());
        if (StringUtils.hasText(patch.getPassword())) {
            existing.setPassword(patch.getPassword());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        jdbcConnectionMapper.updateById(existing);
        return jdbcConnectionMapper.selectById(id);
    }

    public void delete(Long id) {
        if (jdbcConnectionMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("连接不存在: " + id);
        }
    }
}
