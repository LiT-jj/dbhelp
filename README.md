# dbhelp（数据库测试 / 造数工具）

前后端分离的数据库辅助工具：管理 JDBC 连接、拉取元数据、编排造数任务等。后端提供 REST API，前端为单页应用。

| 模块 | 技术栈 |
|------|--------|
| 前端 `frontend/` | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios |
| 后端 `backend/` | Spring Boot 2.7、Java 8、MyBatis-Plus、Spring Security（接口放行）、Spring AMQP（可选） |

## 仓库结构

```
dbhelp/
├── backend/              # Spring Boot 工程（Maven）
├── frontend/             # Vue 3 + Vite 工程
├── 接口清单.md           # HTTP 接口索引（与后端路由对照时请以代码为准）
├── 设计文档.md           # 产品设计说明
└── README.md             # 本文件
```

当前前端导航包含：**连接管理**、**造数任务**（列表与新建在同一模块内切换）。`frontend/src/views` 下另有若干页面组件，是否接入路由以仓库内 `router.js` 为准。

## 环境要求

| 组件 | 说明 |
|------|------|
| JDK | 8+（与 `backend/pom.xml` 中 `java.version` 一致） |
| Maven | 3.6+ |
| Node.js | 16+（建议 LTS，与前端构建一致） |
| MySQL | 用于应用自身业务库（表结构由启动时 SQL 初始化脚本加载，见下） |
| RabbitMQ | **可选**：默认配置启用了造数流水线中的 RabbitMQ；本地若无 Broker，请改用「仅直连」配置（见 [配置说明](#配置说明)） |

## 配置说明

### 数据库（必填）

后端默认从 `backend/src/main/resources/application.yml` 读取数据源。请在本机准备 MySQL，并创建与配置中一致的数据库名（默认示例为 `jdbc:mysql://localhost:5501/dbhelp`，可按需修改端口与库名）。

启动时会执行：

- `classpath:sql/ddl.sql` — 建表
- `classpath:sql/data.sql` — 初始数据（若存在）

**不要将真实账号密码提交到公开仓库。** 推荐在本机使用 `backend/src/main/resources/application-local.yml` 覆盖敏感项（该文件已列入根目录 `.gitignore`，需自行创建）。

### 造数与 RabbitMQ（可选）

默认 `application.yml` 中示例为：

- `dbhelp.generate.transport: rabbitmq` — 大批量造数走队列分批消费
- `dbhelp.generate.rabbit.enabled: true` — 启用项目内 Rabbit 相关 Bean（主类已排除 Spring 自带 `RabbitAutoConfiguration`，仅使用项目自定义配置）

若本地**没有** RabbitMQ，可新增 `application-local.yml`，例如：

```yaml
dbhelp:
  generate:
    transport: direct
    rabbit:
      enabled: false
```

这样在进程内完成写入，无需启动 Broker（具体行为以后端 `DataFakerGeneratePipeline` 与任务选项为准）。

### 跨域

后端已对 `/api/**` 配置 CORS（`WebConfig`），开发模式下前端（默认 `http://localhost:5173`）可直接请求 `http://localhost:8080`。

## 本地启动

### 1. 启动后端

```bash
cd backend
mvn -DskipTests package
java -jar target/database-test-tool-0.0.1-SNAPSHOT.jar
```

开发时常用：

```bash
cd backend
mvn spring-boot:run
```

- 默认 HTTP 端口：`8080`
- 前端 `api.js` 中 `baseURL` 为 `http://localhost:8080`，若改端口请同步修改前端或改用构建期环境变量（需自行接入）。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 开发服务器端口：`5173`（见 `frontend/vite.config.js`）

### 3. 访问顺序

先启动 **MySQL**（并保证连接配置正确），再启动 **后端**，最后启动 **前端**；若使用默认 Rabbit 造数链路，请确保 **RabbitMQ** 可用或已按上文改为 `direct`。

## 构建与部署

```bash
# 前端静态资源
cd frontend && npm run build
# 产物：frontend/dist，可挂到 Nginx 等静态服务器

# 后端可执行包
cd backend && mvn -DskipTests package
# 产物：backend/target/database-test-tool-0.0.1-SNAPSHOT.jar
```

部署静态前端时，请将 API 基地址改为你环境中的网关或后端地址（当前源码硬编码为开发环境，生产部署需按你们的发布方式调整）。

## 文档与约定

- 接口路径与语义以 **`接口清单.md`** 与 **`backend` 中 Controller** 为准；`frontend/src/api.js` 中部分路径可能尚未在后端实现。
- 根目录 `.gitignore` 已忽略 `target/`、`node_modules/`、本地密钥文件等，克隆后需在本地自行配置数据源与可选中间件。

## 本地校验（参考）

- 后端：`mvn -DskipTests package`
- 前端：`npm run build`
