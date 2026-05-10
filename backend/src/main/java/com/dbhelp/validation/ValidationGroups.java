package com.dbhelp.validation;

/**
 * Bean Validation 分组：新建 / 更新 采用不同约束（如密码仅在新建必填）。
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    public interface Create {
    }

    public interface Update {
    }
}
