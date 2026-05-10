package com.dbhelp.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JDBC URL 上追加参数的书写风格（存储于 {@code database_type.url_parameter_style}）。
 */
@Getter
@AllArgsConstructor
public enum UrlParameterStyle {
    QUERY("QUERY"),
    SEMICOLON("SEMICOLON");

    @EnumValue
    private final String dbValue;
}
