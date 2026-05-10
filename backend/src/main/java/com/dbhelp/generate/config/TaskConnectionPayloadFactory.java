package com.dbhelp.generate.config;

import com.dbhelp.dto.metadata.ConnectionPayload;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 从任务 {@code config_json} 根节点解析连接信息。
 */
public final class TaskConnectionPayloadFactory {

    private TaskConnectionPayloadFactory() {
    }

    public static void fillConnection(JsonNode root, ConnectionPayload target) {
        String mode = root.path("connectionMode").asText("SAVED");
        if ("SAVED".equalsIgnoreCase(mode)) {
            if (root.hasNonNull("connectionId")) {
                target.setConnectionId(root.get("connectionId").asLong());
            }
            return;
        }
        JsonNode inl = root.path("inlineConnection");
        if (inl.isMissingNode() || inl.isNull()) {
            return;
        }
        target.setDbType(text(inl, "dbType"));
        target.setHost(text(inl, "host"));
        if (inl.has("port") && !inl.get("port").isNull()) {
            target.setPort(inl.get("port").asInt());
        }
        target.setDatabaseName(text(inl, "databaseName"));
        target.setUsername(text(inl, "username"));
        target.setPassword(text(inl, "password"));
        target.setDriverClass(text(inl, "driverClass"));
        target.setUrlTemplate(text(inl, "urlTemplate"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
