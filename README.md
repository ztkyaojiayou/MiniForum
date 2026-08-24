# MiniForum（迷你微博论坛系统）

一个基于 **Spring Boot 2.7 + Java 17** 的**微博风格轻量博客系统**，无需任何数据库与第三方中间件，开箱即用。支持发帖、分类、标签、话题、关注流、点赞、评论、收藏、转发、@提及、消息通知、站内私信、热搜榜、全文搜索、数据看板、深色模式等 **40+ 项功能**，全部前后端闭环，内置 11 个原生静态页面。

> 🚀 **本项目由自研的 [nanocode](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 编程 Agent 开发完成** —— 通过自然语言对话驱动需求规划、代码生成、重构、调试与迭代，展示了 AI 辅助编程在实际项目中的完整落地。

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
- ✅ **个性化推荐流**：多路召回（热门 / 话题 / 类目 / ItemCF / 新内容 / 关注）+ 微博式排序 + 打散重排，**每条带可解释推荐理由**（"因为你看过 #话题# / 你关注的人发布了 / 大家都在看"）
- ✅ **详情相关推荐**：详情页"看过这篇的人还看"（ItemCF 相似帖）
- ✅ **行为打点闭环**：点击 / 不感兴趣 → 行为日志 → 用户画像 / 实时特征（模拟 Kafka + Flink → Redis 链路，生产适配代码见 recommend/prod）
- ✅ **冷启动**：新内容 Thompson bandit 探索、新用户热门兜底 + 热搜协同
- ✅ **AB 实验**：哈希分桶分层正交，实验组走多样性变体配置，行为日志携带 expId 可离线归因
- ✅ **离线评估**：时间切分 + AUC/GAUC/Recall@K/NDCG@K/Coverage/Diversity/Freshness 指标
- ✅ **微博式三栏首页**：左分类导航 ｜ 中发帖框 + 信息流（最新/关注/热门/**推荐**）｜ 右热搜榜
- ✅ **分类页独立路由**：`/category/tech` 直达分类，可分享、刷新不丢状态
- ✅ 数据看板（用户 / 帖子 / 评论 / 点赞 / 今日新增统计）

### 🎨 趣味与体验
- ✅ 随机灵感便签（内置名言库）
- ✅ 随机决策转盘（一键帮你做选择）
- ✅ **深色模式**：全站 11 页支持，localStorage 记忆偏好，跟随系统
- ✅ 健康检测接口

### ⚙️ 系统能力
- ✅ 用户注册 / 登录 / 退出（Session 认证 + 全局登录拦截 + 游客浏览）
- ✅ **我的主页**：点击右上角账号名进入个人中心（资料编辑 + 发帖/粉丝/关注统计 + 我的文章/草稿/收藏/回收站）
- ✅ **账号管理仅管理员**："用户管理"按钮仅 admin 可见，接口层也做了管理员校验
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
│   ├── controller/        # REST 控制器（18 个）
│   │   ├── ...
│   │   └── RecommendController.java   # 推荐：feed/related/track
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
│   ├── service/           # 业务逻辑层（13 个）
│   ├── repository/        # 数据访问层（11 个，内存存储）
│   ├── entity/            # 实体类（11 个）
│   ├── dto/               # 请求/响应 DTO（19 个）
│   ├── recommend/         # 推荐系统子系统（业务侧，弱训练侧）
│   │   ├── recall/        #   多路召回（热门/话题/类目/ItemCF/新内容/关注）+ 融合
│   │   ├── rank/          #   微博式规则排序（interact/quality/interest/social/author/hot/realtime）
│   │   ├── rerank/        #   打散 + MMR 多样性重排
│   │   ├── feature/       #   用户画像 / 物品特征 / 实时特征（接口 + 内存实现）
│   │   ├── behavior/      #   行为日志（实体/仓库/采集器，织入点赞·收藏·评论·转发·搜索·关注·浏览）
│   │   ├── stream/        #   事件队列(模拟Kafka) + 实时特征窗口(模拟Flink) + 存储(模拟Redis)
│   │   ├── coldstart/     #   Thompson bandit 新内容池 / 冷启动服务
│   │   ├── config/        #   RecConfig 配置中心（接口 + 内存实现）
│   │   ├── ab/            #   AB 实验分桶（hash 分层正交）
│   │   ├── eval/          #   离线评估（时间切分 + 7 指标）
│   │   ├── model/         #   ItemCF 构建/模型/打分
│   │   ├── service/       #   RecommendService 漏斗编排
│   │   └── prod/          #   生产适配（Kafka/Redis/Nacos，@Profile("prod")，默认不连接）
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
├── scripts/               # 辅助脚本（seed_users / seed_posts / seed_interactions / **seed_recsys_data** 造数等）
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

系统提供基于 **Session** 的登录认证功能，并支持**游客浏览**（参考微博首页设计）：

- 默认管理员账号：`admin` / `admin123`
- **游客可浏览**：热门帖子、最新动态、搜索、标签、帖子详情、相关推荐（无需登录）
- **需登录**：发帖、点赞、评论、收藏、转发、关注、私信、个性化推荐、行为打点（写操作返回 `401`）
- 首页未登录时**弱化登录入口**：显示"登录 / 注册"按钮 + 发帖框处提示登录，不强制跳转登录页
- 注册成功即自动登录

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/register` | 注册（公开，注册后自动登录） |
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
| **推荐** | `GET /api/recommend/feed`（推荐流）、`GET /api/recommend/related`（相关推荐）、`POST /api/recommend/track`（点击/负反馈打点） |
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

共 **43 个测试**（覆盖用户 / 密码 / 帖子 / 搜索 / 私信 + 推荐系统 19 个，含端到端集成测试）。

## 推荐系统（如何体验）

1. 启动服务后先造数（让推荐有数据可用）：
   ```bash
   python scripts/seed_recsys_data.py   # 30 用户 / 150 帖 / 交互+关注+搜索，写入行为日志
   ```
2. 登录任意 `user01`~`user30`（密码统一为 `admin123`，与 admin 一致），首页信息流切换到 **✨ 推荐** Tab：
   - 每条带**推荐理由**与召回路来源（"因为你看过 #话题# / 你关注的人发布了 / 大家都在看"）
   - 点击卡片记 **CLICK**、点 🙅 记"不感兴趣"（负反馈）
   - 详情页底部显示 **相关推荐**（ItemCF "看过这篇的人还看"）
3. 推荐原理：多路召回（热门/话题/类目/ItemCF/新内容/关注）→ 微博式排序 → 打散重排 → 冷启动兜底，详见 [docs/推荐系统设计方案.md](docs/推荐系统设计方案.md) 与 [docs/微博推荐调研.md](docs/微博推荐调研.md)。
4. 中间件形态：默认**内存实现**跑通全链路（模拟 Kafka→Flink→Redis），生产适配代码在 `recommend/prod/`（Kafka/Redis/Nacos，`@Profile("prod")` 激活，默认不连接）。

## 模拟活动（定时造数，让系统"转起来"）

系统内置 **模拟活动任务**（`SimulatedActivityService`），定时产生少量新帖与互动，让信息流、热搜、推荐像真实社区一样持续演进。默认**克制**：每 15 分钟随机产生 1~2 条动态 + 少量点赞/评论。

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `app.sim.enabled` | `true` | 是否启用 |
| `app.sim.interval-ms` | `900000` | 每轮间隔（毫秒）= 15 分钟 |
| `app.sim.posts-per-tick` | `2` | 每轮新帖数（随机 1~N） |
| `app.sim.interactions-per-tick` | `2` | 每轮互动数（点赞/评论） |

> 关闭：`app.sim.enabled: false`；加快测试：把 `interval-ms` 调小（如 `20000`）后重启，观察日志"模拟活动一轮：新建 X 帖，互动 Y 次"。

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

本项目由自研的 **nanocode** 编程 Agent 开发完成。nanocode 是一款基于 Java 的 AI 编程助手命令行工具，能够理解自然语言指令，自动完成需求规划、代码编写、重构、调试与测试等开发任务，让开发者通过对话即可快速构建完整项目。本项目的全部功能（40+ 项）均由 AI 辅助完成开发与迭代。

如果你对 AI 辅助编程或 nanocode 感兴趣，欢迎访问 [项目主页](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 了解更多。
