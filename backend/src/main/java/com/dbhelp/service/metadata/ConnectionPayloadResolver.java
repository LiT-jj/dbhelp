package com.dbhelp.service.metadata;

import com.dbhelp.dto.metadata.ConnectionPayload;
import com.dbhelp.service.JdbcConnectionService;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConnectionPayloadResolver {

    private final JdbcConnectionService jdbcConnectionService;
    private final Validator validator;

    public ConnectionPayloadResolver(JdbcConnectionService jdbcConnectionService, Validator validator) {
        this.jdbcConnectionService = jdbcConnectionService;
        this.validator = validator;
    }

    public ResolvedConnection resolve(ConnectionPayload payload) {
        if (payload.getConnectionId() != null) {
            return ResolvedConnection.fromEntity(jdbcConnectionService.requireById(payload.getConnectionId()));
        }
        validateInline(payload);
        return ResolvedConnection.fromPayload(payload);
    }

    private void validateInline(ConnectionPayload payload) {
        Set<ConstraintViolation<ConnectionPayload>> violations =
                validator.validate(payload, ConnectionPayload.Inline.class);
        if (!violations.isEmpty()) {
            String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
            throw new IllegalArgumentException(msg);
        }
    }
}
