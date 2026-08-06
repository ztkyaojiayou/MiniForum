# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Run/Test Commands

```bash
# 构建
mvn clean package

# 直接运行（开发模式）
mvn spring-boot:run

# 运行打包后的 jar
java -jar target/user-management-1.0.0.jar

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=TestClassName

# Windows 运维脚本（位于 scripts/ 目录）
scripts\start.bat      # 后台启动（需要先 mvn clean package）
scripts\stop.bat       # 停止服务
scripts\restart.bat    # 重启服务
```

## 技术栈

- Java 17 + Spring Boot 2.7.18
- Spring Web + Spring Validation
- Maven 构建
- **无数据库**：使用 `ConcurrentHashMap` 内存存储，应用重启后数据丢失
- **无前端框架**：通过 `src/main/resources/static/` 提供纯 HTML 页面

## 架构概览

标准 Spring Boot 分层架构，包路径 `com.example.usermanagement`：

```
controller/     # REST 控制器 + 根路径路由
  ├── UserController   — /api/users CRUD（需登录）
  ├── AuthController   — /api/auth 登录/登出/当前用户
  └── HomeController   — "http://localhost:8080/" 根路径，根据登录状态重定向
service/        # 业务逻辑
  ├── UserService      — 用户 CRUD + 用户名唯一性校验
  └── AuthService      — 登录校验，@PostConstruct 自动创建 admin/admin123 管理员
repository/     # 数据访问
  └── UserRepository   — ConcurrentHashMap 实现，线程安全
entity/         # 数据模型
  └── User             — AtomicLong 自增 ID
config/         # 配置
  ├── AuthInterceptor  — Session 拦截器，拦截 /api/users/** 返回 401
  └── WebConfig        — 注册拦截器
exception/      # 全局异常处理
  ├── GlobalExceptionHandler  — 统一 JSON 错误响应 {timestamp, status, error, message}
  └── ResourceNotFoundException — 404 异常
```

## 认证流程

基于 **HttpSession** 的简单认证：
1. `POST /api/auth/login` → AuthService 校验用户名密码 → 写入 session（`userId`, `username`）
2. AuthInterceptor 拦截 `/api/users/**` → 检查 session 中是否有 `userId` → 无则 401
3. 前端 `login.html` 登录成功后跳转 `index.html`；`index.html` 调用 `/api/auth/me` 校验登录态

拦截器放行 OPTIONS 预检请求以支持 CORS 场景。

## 唯一数据存储约束

`UserRepository` 使用 `ConcurrentHashMap<Long, User>` 作为唯一数据存储。所有对用户数据的读写都必须通过 Repository，不存在二级缓存或数据库同步。新增功能时如需持久化，替换 Repository 实现即可（接口兼容 `save/findById/findByUsername/findAll/deleteById/existsById`）。

## 默认管理员

`AuthService.initDefaultAdmin()` 在启动时自动创建 `admin / admin123`（仅当不存在时）。密码明文存储和比较，没有加密/哈希。
