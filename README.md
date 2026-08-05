# User Management（用户管理系统）

一个基于 **Spring Boot 2.7 + Java 17** 的简单用户管理系统，提供完整的 RESTful API 和简单的 Web 前端页面。数据存储在内存中（`ConcurrentHashMap`），无需数据库即可运行。

> 🚀 **本项目由自研的 [nanobot-java-cli](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 编程 Agent 开发完成** —— 通过自然语言对话驱动代码生成、重构与调试，展示了 AI 辅助编程在实际项目中的落地应用。

## 功能特性

- ✅ 用户的增删改查（CRUD）RESTful API
- ✅ 参数校验（用户名、邮箱、密码、年龄）
- ✅ 用户名唯一性校验
- ✅ 全局异常处理（统一错误响应格式）
- ✅ 内置简单 Web 前端页面
- ✅ 内存存储，开箱即用，无需配置数据库

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 编程语言 |
| Spring Boot 2.7.18 | 应用框架 |
| Spring Web | RESTful 接口 |
| Spring Validation | 参数校验 |
| Maven | 构建工具 |

## 项目结构

```
my-first-nanobot-server/
├── src/main/java/com/example/usermanagement/
│   ├── controller/          # REST 控制器
│   │   ├── UserController.java
│   │   └── AuthController.java   # 登录认证接口
│   ├── entity/              # 实体类
│   │   └── User.java
│   ├── service/             # 业务逻辑层
│   │   ├── UserService.java
│   │   └── AuthService.java      # 登录认证服务
│   ├── repository/          # 数据访问层（内存存储）
│   │   └── UserRepository.java
│   ├── config/              # 配置
│   │   ├── AuthInterceptor.java  # 登录拦截器
│   │   └── WebConfig.java        # Web 配置
│   ├── exception/           # 异常处理
│   │   ├── GlobalExceptionHandler.java
│   │   └── ResourceNotFoundException.java
│   └── UserManagementApplication.java  # 启动类
├── src/main/resources/
│   ├── static/
│   │   ├── index.html       # Web 主页面（需登录）
│   │   └── login.html       # 登录页面
│   └── application.yml      # 配置文件
├── pom.xml                  # Maven 配置
└── README.md
```

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.6+

### 运行方式

#### 方式一：Maven 直接运行

```bash
mvn spring-boot:run
```

#### 方式二：打包运行

```bash
# 打包
mvn clean package

# 运行
java -jar target/user-management-1.0.0.jar
```

启动后访问：
- 登录页面：<http://localhost:8080/login.html>（默认账号 `admin / admin123`）
- Web 主页面：<http://localhost:8080/index.html>（需登录后访问）
- API 接口：<http://localhost:8080/api/users>（需登录，携带 Session Cookie）

## 登录认证

系统提供了基于 **Session** 的简单登录认证功能。

- 默认管理员账号：`admin` / `admin123`（首次启动自动创建）
- 所有 `/api/users/**` 接口均需登录后访问，未登录返回 `401`
- 前端 `index.html` 会自动校验登录状态，未登录跳转到 `login.html`

### 登录认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/login` | 登录，请求体 `{"username":"admin","password":"admin123"}` |
| `POST` | `/api/auth/logout` | 退出登录 |
| `GET` | `/api/auth/me` | 获取当前登录用户信息 |

## 配置

默认配置见 `src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8080` | 服务端口 |
| `spring.application.name` | `user-management` | 应用名称 |

## API 文档

所有接口统一以 `/api/users` 为前缀，返回 JSON 格式数据。

### 1. 新增用户

```
POST /api/users
```

**请求体：**

```json
{
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "password": "123456",
  "age": 25
}
```

**响应：** `201 Created`，返回创建的用户对象。

### 2. 查询所有用户

```
GET /api/users
```

**响应：** `200 OK`，返回用户列表。

### 3. 查询单个用户

```
GET /api/users/{id}
```

**响应：** `200 OK`，返回对应 id 的用户；不存在则返回 `404`。

### 4. 修改用户

```
PUT /api/users/{id}
```

**请求体：**

```json
{
  "username": "zhangsan",
  "email": "new@example.com",
  "password": "654321",
  "age": 26
}
```

**响应：** `200 OK`，返回更新后的用户对象。

### 5. 删除用户

```
DELETE /api/users/{id}
```

**响应：** `204 No Content`；不存在则返回 `404`。

### 字段校验规则

| 字段 | 规则 |
|------|------|
| `username` | 必填，长度不超过 50，且唯一 |
| `email` | 必填，需为合法邮箱格式 |
| `password` | 长度 6-20 |
| `age` | 必填，整数 |

### 错误响应格式

所有错误统一返回如下格式：

```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "错误信息"
}
```

| HTTP 状态码 | 说明 |
|-------------|------|
| `400` | 参数校验失败 / 用户名重复等业务错误 |
| `404` | 用户不存在 |
| `500` | 服务器内部错误 |

## 数据存储说明

> ⚠️ 本项目使用**内存存储**（`ConcurrentHashMap`），应用重启后数据会丢失。如需持久化，可自行接入 MySQL、PostgreSQL 等数据库。

## 许可证

[MIT](LICENSE)

## 关于

本项目由我自研的 **nanobot-java-cli** 编程 Agent 生成。nanobot-java-cli 是一款基于 Java 的 AI 编程助手命令行工具，能够理解自然语言指令，自动完成代码编写、项目搭建、重构与调试等开发任务，让开发者通过对话即可快速构建完整项目。

如果你对 AI 辅助编程或 nanobot-java-cli 感兴趣，欢迎访问 [项目主页](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 了解更多。
