package com.dbhelp.entity;

/**
 * 造数任务状态（持久化 {@link GenerateTask#getStatus()}）。
 */
public enum GenerateTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
