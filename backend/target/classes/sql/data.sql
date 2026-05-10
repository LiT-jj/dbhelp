-- 初始数据：每次启动清空并重建（开发用）。生产请改为迁移工具或条件插入。
REPLACE INTO database_type (id, code, display_name, driver_class, url_template, default_port, dialect_family, url_parameter_style, remark, sort_order) VALUES
(1, 'MYSQL', 'MySQL', 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://{host}:{port}/{database}', 3306, 'mysql', 'QUERY', 'Oracle MySQL，使用 Connector/J 8+ 驱动。', 10),
(2, 'MARIADB', 'MariaDB', 'org.mariadb.jdbc.Driver', 'jdbc:mariadb://{host}:{port}/{database}', 3306, 'mysql', 'QUERY', 'MariaDB JDBC 驱动，URL 参数与 MySQL 相近；细节以 mariadb.com 文档为准。', 20),
(3, 'GOLDENDB', 'GoldenDB', 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://{host}:{port}/{database}', 3306, 'mysql', 'QUERY', 'GoldenDB 通常提供 MySQL 兼容访问方式；驱动与 JDBC URL 习惯与 MySQL 一致，生产环境请以 GoldenDB 官方文档为准。', 30),
(4, 'POSTGRESQL', 'PostgreSQL', 'org.postgresql.Driver', 'jdbc:postgresql://{host}:{port}/{database}', 5432, 'postgresql', 'QUERY', '参数名称与含义以 PostgreSQL JDBC 文档为准。', 40),
(5, 'SQLSERVER', 'Microsoft SQL Server', 'com.microsoft.sqlserver.jdbc.SQLServerDriver', 'jdbc:sqlserver://{host}:{port};databaseName={database}', 1433, 'sqlserver', 'SEMICOLON', 'SQL Server 使用分号分隔属性；databaseName 已在模板中给出，其余属性继续用分号追加。', 50),
(6, 'ORACLE', 'Oracle', 'oracle.jdbc.OracleDriver', 'jdbc:oracle:thin:@//{host}:{port}/{database}', 1521, 'oracle', 'SEMICOLON', '模板为 Service Name 形式 thin URL；SID 形式请改用 jdbc:oracle:thin:@{host}:{port}:{sid} 并自行调整占位符。', 60),
(7, 'H2', 'H2 (embedded/server)', 'org.h2.Driver', 'jdbc:h2:tcp://{host}:{port}/{database}', 9092, 'h2', 'SEMICOLON', 'H2 常用于本地/测试；TCP 远程默认端口示例为 9092，请以实际启动参数为准。', 70);

REPLACE INTO database_type_placeholder (database_type_id, placeholder_idx, placeholder_key) VALUES
(1, 0, 'host'),
(1, 1, 'port'),
(1, 2, 'database'),
(2, 0, 'host'),
(2, 1, 'port'),
(2, 2, 'database'),
(3, 0, 'host'),
(3, 1, 'port'),
(3, 2, 'database'),
(4, 0, 'host'),
(4, 1, 'port'),
(4, 2, 'database'),
(5, 0, 'host'),
(5, 1, 'port'),
(5, 2, 'database'),
(6, 0, 'host'),
(6, 1, 'port'),
(6, 2, 'database'),
(7, 0, 'host'),
(7, 1, 'port'),
(7, 2, 'database');

-- MYSQL
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(1, 1, 'useSSL', 'false', '是否使用 SSL；内网可为 false。', TRUE, 0),
(2, 1, 'allowPublicKeyRetrieval', 'true', 'MySQL 8 默认认证插件下常需设为 true。', TRUE, 1),
(3, 1, 'serverTimezone', 'UTC', '会话时区，避免与服务器时区不一致导致错误。', TRUE, 2),
(4, 1, 'characterEncoding', 'UTF-8', '字符编码。', TRUE, 3),
(5, 1, 'useUnicode', 'true', '使用 Unicode。', FALSE, 4),
(6, 1, 'connectTimeout', '30000', '连接超时（毫秒）。', FALSE, 5),
(7, 1, 'socketTimeout', '60000', '网络读取超时（毫秒）。', FALSE, 6),
(8, 1, 'autoReconnect', 'false', '不建议依赖自动重连，连接池场景通常为 false。', FALSE, 7),
(9, 1, 'failOverReadOnly', 'false', '故障切换后是否只读。', FALSE, 8),
(10, 1, 'maxReconnects', '3', '自动重连最大次数（若启用）。', FALSE, 9),
(11, 1, 'initialTimeout', '2', '重连初始间隔（秒）。', FALSE, 10),
(12, 1, 'rewriteBatchedStatements', 'true', '批量语句重写，提升批量插入性能。', FALSE, 11),
(13, 1, 'zeroDateTimeBehavior', 'CONVERT_TO_NULL', '零日期时间处理方式。', FALSE, 12),
(14, 1, 'sessionVariables', NULL, '启动时执行的会话变量，例如 sql_mode=''STRICT_TRANS_TABLES''。', FALSE, 13);
-- MARIADB
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(15, 2, 'useSSL', 'false', '是否使用 SSL；内网可为 false。', TRUE, 0),
(16, 2, 'allowPublicKeyRetrieval', 'true', 'MySQL 8 默认认证插件下常需设为 true。', TRUE, 1),
(17, 2, 'serverTimezone', 'UTC', '会话时区，避免与服务器时区不一致导致错误。', TRUE, 2),
(18, 2, 'characterEncoding', 'UTF-8', '字符编码。', TRUE, 3),
(19, 2, 'useUnicode', 'true', '使用 Unicode。', FALSE, 4),
(20, 2, 'connectTimeout', '30000', '连接超时（毫秒）。', FALSE, 5),
(21, 2, 'socketTimeout', '60000', '网络读取超时（毫秒）。', FALSE, 6),
(22, 2, 'autoReconnect', 'false', '不建议依赖自动重连，连接池场景通常为 false。', FALSE, 7),
(23, 2, 'failOverReadOnly', 'false', '故障切换后是否只读。', FALSE, 8),
(24, 2, 'maxReconnects', '3', '自动重连最大次数（若启用）。', FALSE, 9),
(25, 2, 'initialTimeout', '2', '重连初始间隔（秒）。', FALSE, 10),
(26, 2, 'rewriteBatchedStatements', 'true', '批量语句重写，提升批量插入性能。', FALSE, 11),
(27, 2, 'zeroDateTimeBehavior', 'CONVERT_TO_NULL', '零日期时间处理方式。', FALSE, 12),
(28, 2, 'sessionVariables', NULL, '启动时执行的会话变量，例如 sql_mode=''STRICT_TRANS_TABLES''。', FALSE, 13);
-- GOLDENDB
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(29, 3, 'useSSL', 'false', '是否使用 SSL；内网可为 false。', TRUE, 0),
(30, 3, 'allowPublicKeyRetrieval', 'true', 'MySQL 8 默认认证插件下常需设为 true。', TRUE, 1),
(31, 3, 'serverTimezone', 'UTC', '会话时区，避免与服务器时区不一致导致错误。', TRUE, 2),
(32, 3, 'characterEncoding', 'UTF-8', '字符编码。', TRUE, 3),
(33, 3, 'useUnicode', 'true', '使用 Unicode。', FALSE, 4),
(34, 3, 'connectTimeout', '30000', '连接超时（毫秒）。', FALSE, 5),
(35, 3, 'socketTimeout', '60000', '网络读取超时（毫秒）。', FALSE, 6),
(36, 3, 'autoReconnect', 'false', '不建议依赖自动重连，连接池场景通常为 false。', FALSE, 7),
(37, 3, 'failOverReadOnly', 'false', '故障切换后是否只读。', FALSE, 8),
(38, 3, 'maxReconnects', '3', '自动重连最大次数（若启用）。', FALSE, 9),
(39, 3, 'initialTimeout', '2', '重连初始间隔（秒）。', FALSE, 10),
(40, 3, 'rewriteBatchedStatements', 'true', '批量语句重写，提升批量插入性能。', FALSE, 11),
(41, 3, 'zeroDateTimeBehavior', 'CONVERT_TO_NULL', '零日期时间处理方式。', FALSE, 12),
(42, 3, 'sessionVariables', NULL, '启动时执行的会话变量，例如 sql_mode=''STRICT_TRANS_TABLES''。', FALSE, 13),
(43, 3, 'useServerPrepStmts', 'true', '是否使用服务端预处理语句。', FALSE, 14),
(44, 3, 'cachePrepStmts', 'true', '是否缓存预处理语句。', FALSE, 15),
(45, 3, 'prepStmtCacheSqlLimit', '2048', '单条预处理 SQL 缓存长度上限。', FALSE, 16),
(46, 3, 'prepStmtCacheSize', '250', '预处理语句缓存条数。', FALSE, 17),
(47, 3, 'metadataCacheSize', '50', '元数据缓存大小。', FALSE, 18);
-- POSTGRESQL
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(48, 4, 'ssl', 'false', '是否启用 SSL。', TRUE, 0),
(49, 4, 'sslmode', 'disable', 'SSL 模式：disable、allow、prefer、require、verify-ca、verify-full。', TRUE, 1),
(50, 4, 'connectTimeout', '10', '建立连接超时（秒）。', TRUE, 2),
(51, 4, 'socketTimeout', '0', '读取超时（秒），0 表示无限制。', FALSE, 3),
(52, 4, 'loginTimeout', '10', '登录超时（秒）。', FALSE, 4),
(53, 4, 'ApplicationName', 'dbhelp', '将在 pg_stat_activity.application_name 中显示。', FALSE, 5),
(54, 4, 'currentSchema', NULL, '默认 schema。', FALSE, 6),
(55, 4, 'stringtype', 'unspecified', '绑定字符串类型行为，常用 unspecified。', FALSE, 7),
(56, 4, 'charset', 'UTF8', '字符集。', FALSE, 8),
(57, 4, 'tcpKeepAlive', 'true', 'TCP keep-alive。', FALSE, 9);
-- SQLSERVER
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(58, 5, 'encrypt', 'false', '是否加密传输；内网开发环境常为 false。', TRUE, 0),
(59, 5, 'trustServerCertificate', 'true', '为 true 时跳过服务器证书校验（仅建议在可信网络）。', TRUE, 1),
(60, 5, 'loginTimeout', '30', '登录超时（秒）。', FALSE, 2),
(61, 5, 'connectRetryCount', '1', '连接重试次数。', FALSE, 3),
(62, 5, 'connectRetryInterval', '10', '重试间隔（秒）。', FALSE, 4),
(63, 5, 'sendStringParametersAsUnicode', 'true', '字符串是否按 Unicode 发送。', FALSE, 5),
(64, 5, 'applicationName', 'dbhelp', '客户端应用名称。', FALSE, 6);
-- ORACLE
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(65, 6, 'oracle.net.CONNECT_TIMEOUT', '10000', '连接超时（毫秒）。', TRUE, 0),
(66, 6, 'oracle.jdbc.ReadTimeout', '0', '读取超时（毫秒），0 表示默认。', FALSE, 1),
(67, 6, 'defaultRowPrefetch', '10', '预取行数。', FALSE, 2),
(68, 6, 'v$session.program', 'dbhelp', '会话 program 信息（部分版本支持）。', FALSE, 3);
-- H2
REPLACE INTO jdbc_url_parameter (id, database_type_id, name, default_value, description, recommended, sort_order) VALUES
(69, 7, 'MODE', 'MySQL', '兼容模式，例如 MySQL、PostgreSQL。', FALSE, 0),
(70, 7, 'DATABASE_TO_LOWER', 'false', '是否将未引用的标识符转为小写。', FALSE, 1),
(71, 7, 'CASE_INSENSITIVE_IDENTIFIERS', 'false', '标识符是否大小写不敏感。', FALSE, 2);