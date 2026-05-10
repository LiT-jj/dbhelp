-- 开发环境默认 H2；语法兼顾 MySQL 常见写法，便于迁到 MySQL。
-- 若目标库不支持 IF NOT EXISTS，请按需裁剪。
create database if not exists dbhelp;
use dbhelp;

CREATE TABLE IF NOT EXISTS database_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    driver_class VARCHAR(512) NOT NULL,
    url_template VARCHAR(1024) NOT NULL,
    default_port INT NOT NULL,
    dialect_family VARCHAR(64),
    url_parameter_style VARCHAR(32) NOT NULL,
    remark VARCHAR(4000),
    sort_order INT NOT NULL,
    CONSTRAINT uk_database_type_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS database_type_placeholder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_type_id BIGINT NOT NULL,
    placeholder_idx INT NOT NULL,
    placeholder_key VARCHAR(64) NOT NULL,
    CONSTRAINT fk_dtp_database_type FOREIGN KEY (database_type_id) REFERENCES database_type (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS jdbc_url_parameter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    database_type_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    default_value VARCHAR(512),
    description VARCHAR(4000),
    recommended BOOLEAN NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_jup_database_type FOREIGN KEY (database_type_id) REFERENCES database_type (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS jdbc_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    db_type VARCHAR(64) NOT NULL,
    host VARCHAR(512) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(256) DEFAULT NULL,
    username VARCHAR(256) NOT NULL,
    password VARCHAR(512) NOT NULL,
    driver_class VARCHAR(512) NOT NULL,
    url_template VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS generate_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    target_rows BIGINT NOT NULL DEFAULT 0,
    config_json JSON NOT NULL,
    checkpoint_json JSON,
    error_message JSON,
    warning_json JSON,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);
