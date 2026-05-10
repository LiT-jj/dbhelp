package com.dbhelp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dbhelp.entity.DatabaseType;
import com.dbhelp.entity.DatabaseTypePlaceholder;
import com.dbhelp.entity.JdbcUrlParameter;
import com.dbhelp.mapper.DatabaseTypeMapper;
import com.dbhelp.mapper.DatabaseTypePlaceholderMapper;
import com.dbhelp.mapper.JdbcUrlParameterMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DatabaseTypeService {

    private final DatabaseTypeMapper databaseTypeMapper;
    private final DatabaseTypePlaceholderMapper placeholderMapper;
    private final JdbcUrlParameterMapper jdbcUrlParameterMapper;

    public DatabaseTypeService(
            DatabaseTypeMapper databaseTypeMapper,
            DatabaseTypePlaceholderMapper placeholderMapper,
            JdbcUrlParameterMapper jdbcUrlParameterMapper) {
        this.databaseTypeMapper = databaseTypeMapper;
        this.placeholderMapper = placeholderMapper;
        this.jdbcUrlParameterMapper = jdbcUrlParameterMapper;
    }

    public List<DatabaseType> listAllWithDetails() {
        List<DatabaseType> types = databaseTypeMapper.selectList(
                Wrappers.<DatabaseType>lambdaQuery().orderByAsc(DatabaseType::getSortOrder));
        attachChildren(types);
        return types;
    }

    public Optional<DatabaseType> findByCodeWithDetails(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        DatabaseType one = databaseTypeMapper.selectOne(
                Wrappers.<DatabaseType>lambdaQuery().eq(DatabaseType::getCode, c));
        if (one == null) {
            return Optional.empty();
        }
        attachChildren(java.util.Collections.singletonList(one));
        return Optional.of(one);
    }

    private void attachChildren(List<DatabaseType> types) {
        if (types.isEmpty()) {
            return;
        }
        List<Long> ids = types.stream().map(DatabaseType::getId).collect(Collectors.toList());

        List<DatabaseTypePlaceholder> placeholders = placeholderMapper.selectList(
                Wrappers.<DatabaseTypePlaceholder>lambdaQuery().in(DatabaseTypePlaceholder::getDatabaseTypeId, ids));
        Map<Long, List<String>> phByType = placeholders.stream()
                .collect(Collectors.groupingBy(DatabaseTypePlaceholder::getDatabaseTypeId,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparing(DatabaseTypePlaceholder::getPlaceholderIdx))
                                        .map(DatabaseTypePlaceholder::getPlaceholderKey)
                                        .collect(Collectors.toList()))));

        List<JdbcUrlParameter> params = jdbcUrlParameterMapper.selectList(
                Wrappers.<JdbcUrlParameter>lambdaQuery().in(JdbcUrlParameter::getDatabaseTypeId, ids));
        Map<Long, List<JdbcUrlParameter>> paramByType = params.stream()
                .collect(Collectors.groupingBy(JdbcUrlParameter::getDatabaseTypeId));

        for (DatabaseType t : types) {
            t.setUrlPlaceholderKeys(phByType.getOrDefault(t.getId(), java.util.Collections.emptyList()));
            List<JdbcUrlParameter> jp = paramByType.getOrDefault(t.getId(), java.util.Collections.emptyList());
            jp.sort(Comparator.comparingInt(JdbcUrlParameter::getSortOrder));
            t.setJdbcUrlParameters(jp);
        }
    }
}
