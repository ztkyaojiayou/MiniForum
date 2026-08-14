# MiniForum（迷你微博论坛系统）

一个基于 **Spring Boot 2.7 + Java 17** 的**微博风格轻量博客系统**，无需任何数据库与第三方中间件，开箱即用。支持发帖、分类、标签、话题、关注流、点赞、评论、收藏、转发、@提及、消息通知、站内私信、热搜榜、全文搜索、数据看板、深色模式等 **40+ 项功能**，全部前后端闭环，内置 11 个原生静态页面。

> 🚀 **本项目由自研的 [nanobot-java-cli](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 编程 Agent 开发完成** —— 通过自然语言对话驱动需求规划、代码生成、重构、调试与迭代，展示了 AI 辅助编程在实际项目中的完整落地。

## 功能特性

### 📦 内容发布
- ✅ 发帖 / 编辑 / 删除（标题 + 正文，正文最长 5000 字）
- ✅ **Markdown 渲染**（前端 marked.js，后端存原文）
- ✅ **帖子分类**：固定 12 类（科技 / 数码 / 游戏 / 娱乐 / 体育 / 财经 / 汽车 / 时事 / 教育 / 生活 / 美食 / 其他）
- ✅ **标签**：发帖打 1~5 个标签，按标签筛选
- ✅ **话题**：内容 `#话题#` 自动提取，话题榜 + 话题筛选
- ✅ 草稿 / 发布状态管理
- ✅ **回收站**：软删除、可恢复、30 天自动清理
- ✅ **阅读量统计** + 热门帖子排行

### 👥 社交互动
- ✅ 关注 / 粉丝，**关注流**（首页只看关注的人）
- ✅ 点赞 / 取消点赞
- ✅ 评论（发表 / 查看 / 删除）
- ✅ 收藏 / 取消收藏 + 我的收藏列表
- ✅ **转发**（一键转发原帖，转发计数，通知原帖作者）
- ✅ **@提及**（`@用户名` 自动识别 + 通知 + 可点击跳转）
- ✅ **消息通知中心**（被点赞 / 评论 / 关注 / 转发 / 提及时通知，未读角标、已读管理）
- ✅ **站内私信**（双人会话、聊天页、未读数，HTTP 轮询刷新）
- ✅ 个人主页（聚合资料 + 动态 + 粉丝/关注数）

### 🔍 发现与数据
- ✅ **全文搜索**：帖子（标题 / 内容 / 标签 / 话题）+ 用户综合搜索，搜索词计入热搜
- ✅ **热搜榜**：标签热度聚合（阅读×1 + 点赞×2 + 评论×3，30 天时间衰减），独立热搜详情页（Top50 + 排名趋势）
- ✅ **微博式三栏首页**：左分类导航 ｜ 中发帖框 + 信息流（最新/关注/热门）｜ 右热搜榜
- ✅ **分类页独立路由**：`/category/tech` 直达分类，可分享、刷新不丢状态
- ✅ 数据看板（用户 / 帖子 / 评论 / 点赞 / 今日新增统计）

### 🎨 趣味与体验
- ✅ 随机灵感便签（内置名言库）
- ✅ 随机决策转盘（一键帮你做选择）
- ✅ **深色模式**：全站 11 页支持，localStorage 记忆偏好，跟随系统
- ✅ 健康检测接口

### ⚙️ 系统能力
- ✅ 用户注册 / 登录 / 退出（Session 认证 + 全局登录拦截）
- ✅ 密码安全（SHA-256 + 盐加密，用户信息脱敏）
- ✅ 修改密码 / 修改资料（昵称 / 头像 / 简介）
- ✅ **JSON 文件持久化**（`data/*.json`，定时落盘 + 启动加载，重启不丢）
- ✅ 全局异常处理、统一响应体、JSR-303 参数校验

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 编程语言（LTS） |
| Spring Boot 2.7.18 | 应用框架 |
| Spring Web | RESTful 接口 |
| Spring Validation | 参数校验 |
| Maven | 构建工具 |
| JSON 文件持久化 | 内存存储（`ConcurrentHashMap`）+ `data/*.json` 落盘 |

**零第三方中间件**：无数据库、无 Redis、无消息队列、无 WebSocket —— 全部功能基于纯 Java 实现。

## 项目结构

```
my-first-nanobot-server/
├── src/main/java/com/tkzou/miniforum/
│   ├── controller/        # REST 控制器（17 个）
│   │   ├── AuthController.java        # 登录/注册/退出
│   │   ├── PostController.java        # 发帖/编辑/删除/详情/热门/回收站/转发/点赞
│   │   ├── UserController.java        # 用户管理/资料/主页聚合
│   │   ├── CommentController.java     # 评论
│   │   ├── FollowController.java      # 关注/粉丝/关注流
│   │   ├── NotificationController.java# 消息通知
│   │   ├── FavoriteController.java    # 收藏
│   │   ├── CategoryController.java    # 帖子分类
│   │   ├── HotSearchController.java   # 热搜榜
│   │   ├── TagController.java         # 标签/话题
│   │   ├── SearchController.java      # 全文搜索
│   │   ├── MessageController.java     # 站内私信
│   │   ├── DashboardController.java   # 数据看板
│   │   ├── QuoteController.java       # 灵感便签
│   │   ├── DecisionController.java    # 决策转盘
│   │   ├── HealthController.java      # 健康检测
│   │   └── HomeController.java        # 页面路由
│   ├── service/           # 业务逻辑层（12 个）
│   ├── repository/        # 数据访问层（10 个，内存存储）
│   ├── entity/            # 实体类（10 个）
│   ├── dto/               # 请求/响应 DTO（18 个）
│   ├── persistence/       # JSON 持久化（DataStore）
│   ├── config/            # 登录拦截器 + Web 配置
│   ├── exception/         # 全局异常处理
│   ├── util/              # 密码加密工具
│   └── MiniForumApplication.java     # 启动类
├── src/main/resources/
│   ├── static/            # 11 个原生静态页面
│   │   ├── index.html         # 首页（三栏：左分类/中信息流/右热搜）
│   │   ├── login.html         # 登录/注册
│   │   ├── post.html          # 发帖页
│   │   ├── detail.html        # 帖子详情
│   │   ├── my.html            # 个人中心
│   │   ├── user.html          # 他人主页
│   │   ├── notification.html  # 通知中心
│   │   ├── message.html       # 私信聊天
│   │   ├── hot.html           # 热搜榜详情页
│   │   ├── quote.html         # 灵感便签
│   │   └── wheel.html         # 决策转盘
│   └── application.yml      # 配置文件
├── data/                  # 运行时 JSON 数据（自动生成）
├── docs/                  # 需求规划 / API 文档 / 功能全景
├── scripts/               # 辅助脚本（演示数据初始化等）
├── Dockerfile             # 容器化部署
├── pom.xml                # Maven 配置
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
java -jar target/mini-forum-1.0.0.jar
```

#### 方式三：Docker 部署

```bash
# 构建镜像
docker build -t mini-forum .

# 运行（数据目录挂载到宿主机 data 目录）
docker run -d -p 8090:8090 -v $(pwd)/data:/app/data mini-forum
```

#### 方式四：启动脚本（Windows / Linux）

```bash
# Windows
scripts/start.bat

# Linux / macOS
./scripts/start.sh
```

### 访问地址

启动后访问 <http://localhost:8090/>，将自动跳转到登录页。

- 首页（三栏）：<http://localhost:8090/index.html>
- 登录页：<http://localhost:8090/login.html>
- 发帖页：<http://localhost:8090/post.html>
- 热搜榜详情页：<http://localhost:8090/hot.html>

**默认账号**：`admin / admin123`（首次启动自动创建）

## 登录认证

系统提供基于 **Session** 的登录认证功能。

- 默认管理员账号：`admin` / `admin123`
- 发布帖子、互动等接口均需登录，未登录返回 `401`
- 前端页面自动校验登录状态，未登录跳转到 `login.html`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/register` | 注册 |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `GET` | `/api/auth/me` | 获取当前登录用户 |

## 配置

默认配置见 `src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8090` | 服务端口 |
| `app.data-dir` | `./data` | 数据持久化目录 |
| `app.persistence.enabled` | `true` | 是否启用 JSON 持久化 |
| `app.persistence.interval-ms` | `30000` | 定时保存间隔（毫秒） |

## API 概览

所有接口统一返回 `code / message / data` 结构。完整接口文档见 **[docs/API.md](docs/API.md)**。

| 模块 | 主要接口 |
|------|----------|
| 用户 | `POST /api/users`、`GET /api/users`、`PUT /api/users/{id}/profile`、`PUT /api/users/{id}/password`、`GET /api/users/{id}/profile` |
| 帖子 | `POST /api/posts`、`GET /api/posts`（支持 page/size/tag/category）、`GET /api/posts/{id}`、`PUT /api/posts/{id}`、`DELETE /api/posts/{id}`、`GET /api/posts/hot`、`POST /api/posts/{id}/like`、`POST /api/posts/{id}/repost`、回收站/恢复 |
| 评论 | `POST /api/posts/{postId}/comments`、`GET /api/posts/{postId}/comments`、`DELETE /api/comments/{id}` |
| 关注 | `POST /api/follows/{userId}`、`DELETE /api/follows/{userId}`、`GET /api/follows/following`、`GET /api/follows/followers`、`GET /api/follows/feed` |
| 通知 | `GET /api/notifications`、`GET /api/notifications/unread-count`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all` |
| 收藏 | `POST /api/favorites/{postId}`、`DELETE /api/favorites/{postId}`、`GET /api/favorites/my` |
| 分类 | `GET /api/categories` |
| 热搜 | `GET /api/hot/search` |
| 标签/话题 | `GET /api/tags`、`GET /api/tags/topics`、`GET /api/tags/{tag}/posts` |
| 搜索 | `GET /api/search?keyword=` |
| 私信 | `GET /api/messages/conversations`、`POST /api/messages/send`、`GET /api/messages/{conversationId}`、`GET /api/messages/unread` |
| 看板 | `GET /api/dashboard/stats` |
| 趣味 | `GET /api/quotes/random`、`POST /api/decide` |
| 健康 | `GET /api/health`、`GET /api/health/ping` |

### 错误响应

| HTTP 状态码 | 说明 |
|-------------|------|
| `400` | 参数校验失败 / 业务错误 |
| `401` | 未登录或登录失效 |
| `404` | 资源不存在 |
| `500` | 服务器内部错误 |

## 数据存储

> 项目使用**内存存储**（`ConcurrentHashMap`）+ **JSON 文件持久化**：
> - 运行时数据常驻内存，读写高效；
> - 每 30 秒自动落盘到 `data/*.json`，应用关闭 / 重启时自动加载，**重启不丢数据**；
> - 如需切换数据库，将 `app.persistence.enabled` 置为 `false` 即可关闭持久化。

`data/` 目录文件：`users.json`、`posts.json`、`comments.json`、`likes.json`、`follows.json`、`notifications.json`、`favorites.json`、`search-records.json`、`conversations.json`、`messages.json`

## 测试

```bash
mvn test
```

共 **32 个单元测试**（5 个测试类，覆盖用户 / 密码 / 帖子 / 搜索 / 私信核心逻辑）。

## 文档索引

| 文档 | 说明 |
|------|------|
| [docs/API.md](docs/API.md) | 完整接口文档 |
| [docs/系统功能全景.md](docs/系统功能全景.md) | 功能全景盘点（基于源码核验） |
| [docs/第四期需求规划.md](docs/第四期需求规划.md) | 第四期需求规划 |
| [docs/微博化改版规划.md](docs/微博化改版规划.md) | 微博化改版规划 |
| [docs/功能迭代规划.md](docs/功能迭代规划.md) | 功能迭代规划 |

## 功能闭环

```
发帖（分类/标签/Markdown/话题/@提及）
  → 浏览（三栏首页/分类/搜索/热搜/热门/话题榜）
  → 互动（点赞/评论/收藏/转发/关注）
  → 通知（点赞/评论/关注/转发/提及 → 通知中心）
  → 私信（双人会话/聊天）
  → 个人中心（我的帖子/草稿/收藏/回收站/资料）
```

## 许可证

[MIT](LICENSE)

## 关于

本项目由自研的 **nanobot-java-cli** 编程 Agent 开发完成。nanobot-java-cli 是一款基于 Java 的 AI 编程助手命令行工具，能够理解自然语言指令，自动完成需求规划、代码编写、重构、调试与测试等开发任务，让开发者通过对话即可快速构建完整项目。本项目的全部功能（40+ 项）均由 AI 辅助完成开发与迭代。

如果你对 AI 辅助编程或 nanobot-java-cli 感兴趣，欢迎访问 [项目主页](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 了解更多。
