# 数据库测试工具（前后端分离）

基于设计文档实现的前后端项目：
- 前端：`Vue3 + Vite + Element Plus`
- 后端：`Spring Boot + RESTful API`

## 目录结构
- `frontend`：前端源码
- `backend`：后端源码
- `接口清单.md`：接口列表
- `设计文档.md`：原始设计文档

## 功能覆盖
- 主布局：顶部导航、侧边菜单、主内容区、底部状态栏
- 页面：连接管理、表结构、数据生成、自定义约束、数据比对、设置
- 接口：按设计文档中的 `/api/*` 路由实现完整分组
- 健壮性：参数校验、统一异常处理、标准状态码、友好错误提示

## 启动说明

## 1) 启动后端
要求：`JDK 8+`、`Maven 3.6+`

```bash
cd backend
mvn clean package -DskipTests
java -jar target/database-test-tool-0.0.1-SNAPSHOT.jar
```

默认地址：`http://localhost:8080`

## 2) 启动前端
要求：`Node.js 16+`

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 3) 前后端联调
- 前端请求后端地址固定为：`http://localhost:8080`
- 需先启动后端，再启动前端

## 已完成的本地验证
- 前端：`npm run build` 通过
- 后端：`mvn -DskipTests package` 通过

## 部署建议
- 前端：`npm run build` 后将 `frontend/dist` 部署到 Nginx
- 后端：打包后直接 `java -jar` 运行或容器化部署

## 注意
当前版本为“设计文档实现版（可运行）”，包含完整页面骨架与 API 路由。若你需要我继续做“真实数据库连接、多库驱动、JSqlParser 深度约束提取、Redis/RabbitMQ 接入”，我可以在这个基础上继续迭代成生产版。
