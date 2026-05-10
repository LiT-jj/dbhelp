# -*- coding: utf-8 -*-
"""Generate sql/data.sql for dbhelp seed data."""
from pathlib import Path

def esc(s: str) -> str:
    return s.replace("'", "''")

def mysql_like_params():
    return [
        ("useSSL", "false", "是否使用 SSL；内网可为 false。", True),
        ("allowPublicKeyRetrieval", "true", "MySQL 8 默认认证插件下常需设为 true。", True),
        ("serverTimezone", "UTC", "会话时区，避免与服务器时区不一致导致错误。", True),
        ("characterEncoding", "UTF-8", "字符编码。", True),
        ("useUnicode", "true", "使用 Unicode。", False),
        ("connectTimeout", "30000", "连接超时（毫秒）。", False),
        ("socketTimeout", "60000", "网络读取超时（毫秒）。", False),
        ("autoReconnect", "false", "不建议依赖自动重连，连接池场景通常为 false。", False),
        ("failOverReadOnly", "false", "故障切换后是否只读。", False),
        ("maxReconnects", "3", "自动重连最大次数（若启用）。", False),
        ("initialTimeout", "2", "重连初始间隔（秒）。", False),
        ("rewriteBatchedStatements", "true", "批量语句重写，提升批量插入性能。", False),
        ("zeroDateTimeBehavior", "CONVERT_TO_NULL", "零日期时间处理方式。", False),
        ("sessionVariables", "", "启动时执行的会话变量，例如 sql_mode='STRICT_TRANS_TABLES'。", False),
    ]

def golden_extra():
    return [
        ("useServerPrepStmts", "true", "是否使用服务端预处理语句。", False),
        ("cachePrepStmts", "true", "是否缓存预处理语句。", False),
        ("prepStmtCacheSqlLimit", "2048", "单条预处理 SQL 缓存长度上限。", False),
        ("prepStmtCacheSize", "250", "预处理语句缓存条数。", False),
        ("metadataCacheSize", "50", "元数据缓存大小。", False),
    ]

def pg_params():
    return [
        ("ssl", "false", "是否启用 SSL。", True),
        ("sslmode", "disable", "SSL 模式：disable、allow、prefer、require、verify-ca、verify-full。", True),
        ("connectTimeout", "10", "建立连接超时（秒）。", True),
        ("socketTimeout", "0", "读取超时（秒），0 表示无限制。", False),
        ("loginTimeout", "10", "登录超时（秒）。", False),
        ("ApplicationName", "dbhelp", "将在 pg_stat_activity.application_name 中显示。", False),
        ("currentSchema", "", "默认 schema。", False),
        ("stringtype", "unspecified", "绑定字符串类型行为，常用 unspecified。", False),
        ("charset", "UTF8", "字符集。", False),
        ("tcpKeepAlive", "true", "TCP keep-alive。", False),
    ]

def sqlserver_params():
    return [
        ("encrypt", "false", "是否加密传输；内网开发环境常为 false。", True),
        ("trustServerCertificate", "true", "为 true 时跳过服务器证书校验（仅建议在可信网络）。", True),
        ("loginTimeout", "30", "登录超时（秒）。", False),
        ("connectRetryCount", "1", "连接重试次数。", False),
        ("connectRetryInterval", "10", "重试间隔（秒）。", False),
        ("sendStringParametersAsUnicode", "true", "字符串是否按 Unicode 发送。", False),
        ("applicationName", "dbhelp", "客户端应用名称。", False),
    ]

def oracle_params():
    return [
        ("oracle.net.CONNECT_TIMEOUT", "10000", "连接超时（毫秒）。", True),
        ("oracle.jdbc.ReadTimeout", "0", "读取超时（毫秒），0 表示默认。", False),
        ("defaultRowPrefetch", "10", "预取行数。", False),
        ("v$session.program", "dbhelp", "会话 program 信息（部分版本支持）。", False),
    ]

def h2_params():
    return [
        ("MODE", "MySQL", "兼容模式，例如 MySQL、PostgreSQL。", False),
        ("DATABASE_TO_LOWER", "false", "是否将未引用的标识符转为小写。", False),
        ("CASE_INSENSITIVE_IDENTIFIERS", "false", "标识符是否大小写不敏感。", False),
    ]

def rows_jdbc(start_id, type_id, params):
    lines = []
    rid = start_id
    for i, (name, dv, desc, rec) in enumerate(params):
        dv_sql = "NULL" if dv == "" else "'" + esc(dv) + "'"
        lines.append(
            "(%d, %d, '%s', %s, '%s', %s, %d)"
            % (rid, type_id, esc(name), dv_sql, esc(desc), "TRUE" if rec else "FALSE", i)
        )
        rid += 1
    return lines, rid

def main():
    root = Path(__file__).resolve().parents[1]
    out = root / "src" / "main" / "resources" / "sql" / "data.sql"

    lines = []
    lines.append("-- 初始数据：每次启动清空并重建（开发用）。生产请改为迁移工具或条件插入。")
    lines.append("DELETE FROM jdbc_url_parameter;")
    lines.append("DELETE FROM database_type_placeholder;")
    lines.append("DELETE FROM database_type;")
    lines.append("")

    types = [
        (1, "MYSQL", "MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://{host}:{port}/{database}", 3306, "mysql", "QUERY", "Oracle MySQL，使用 Connector/J 8+ 驱动。", 10),
        (2, "MARIADB", "MariaDB", "org.mariadb.jdbc.Driver", "jdbc:mariadb://{host}:{port}/{database}", 3306, "mysql", "QUERY", "MariaDB JDBC 驱动，URL 参数与 MySQL 相近；细节以 mariadb.com 文档为准。", 20),
        (3, "GOLDENDB", "GoldenDB", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://{host}:{port}/{database}", 3306, "mysql", "QUERY", "GoldenDB 通常提供 MySQL 兼容访问方式；驱动与 JDBC URL 习惯与 MySQL 一致，生产环境请以 GoldenDB 官方文档为准。", 30),
        (4, "POSTGRESQL", "PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://{host}:{port}/{database}", 5432, "postgresql", "QUERY", "参数名称与含义以 PostgreSQL JDBC 文档为准。", 40),
        (5, "SQLSERVER", "Microsoft SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://{host}:{port};databaseName={database}", 1433, "sqlserver", "SEMICOLON", "SQL Server 使用分号分隔属性；databaseName 已在模板中给出，其余属性继续用分号追加。", 50),
        (6, "ORACLE", "Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//{host}:{port}/{database}", 1521, "oracle", "SEMICOLON", "模板为 Service Name 形式 thin URL；SID 形式请改用 jdbc:oracle:thin:@{host}:{port}:{sid} 并自行调整占位符。", 60),
        (7, "H2", "H2 (embedded/server)", "org.h2.Driver", "jdbc:h2:tcp://{host}:{port}/{database}", 9092, "h2", "SEMICOLON", "H2 常用于本地/测试；TCP 远程默认端口示例为 9092，请以实际启动参数为准。", 70),
    ]

    vals = []
    for t in types:
        tid, code, dname, drv, url, port, dia, ups, rm, so = t
        vals.append(
            "(%d, '%s', '%s', '%s', '%s', %d, '%s', '%s', '%s', %d)"
            % (tid, esc(code), esc(dname), esc(drv), esc(url), port, esc(dia), ups, esc(rm), so)
        )
    lines.append(
        "INSERT INTO database_type (id, code, display_name, driver_class, url_template, default_port, dialect_family, url_parameter_style, remark, sort_order) VALUES\n"
        + ",\n".join(vals)
        + ";"
    )
    lines.append("")

    ph_rows = []
    for tid in range(1, 8):
        for idx, key in enumerate(["host", "port", "database"]):
            ph_rows.append("(%d, %d, '%s')" % (tid, idx, key))
    lines.append(
        "INSERT INTO database_type_placeholder (database_type_id, placeholder_idx, placeholder_key) VALUES\n"
        + ",\n".join(ph_rows)
        + ";"
    )
    lines.append("")

    jdbc_parts = []
    rid = 1

    p = mysql_like_params()
    r, rid = rows_jdbc(rid, 1, p)
    jdbc_parts.append("-- MYSQL\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    r, rid = rows_jdbc(rid, 2, mysql_like_params())
    jdbc_parts.append("-- MARIADB\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    g = mysql_like_params() + golden_extra()
    r, rid = rows_jdbc(rid, 3, g)
    jdbc_parts.append("-- GOLDENDB\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    r, rid = rows_jdbc(rid, 4, pg_params())
    jdbc_parts.append("-- POSTGRESQL\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    r, rid = rows_jdbc(rid, 5, sqlserver_params())
    jdbc_parts.append("-- SQLSERVER\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    r, rid = rows_jdbc(rid, 6, oracle_params())
    jdbc_parts.append("-- ORACLE\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    r, rid = rows_jdbc(rid, 7, h2_params())
    jdbc_parts.append("-- H2\nINSERT INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES\n" + ",\n".join(r) + ";")

    lines.extend(jdbc_parts)
    lines.append("")
    lines.append("-- H2：重置自增，便于后续手工 INSERT")
    lines.append("ALTER TABLE database_type ALTER COLUMN id RESTART WITH 8;")
    lines.append("ALTER TABLE database_type_placeholder ALTER COLUMN id RESTART WITH 100;")
    lines.append("ALTER TABLE jdbc_url_parameter ALTER COLUMN id RESTART WITH %d;" % rid)

    out.write_text("\n".join(lines), encoding="utf-8")
    print("Wrote", out, "max jdbc id", rid - 1)


if __name__ == "__main__":
    main()
