# MiniForum（迷你微博论坛系统）

一个基于 **Spring Boot 2.7 + Java 17**、**以生产级推荐系统为核心**的**微博风格轻量博客系统**，无需任何数据库与第三方中间件，开箱即用。支持发帖、分类、标签、话题、关注流、点赞、评论、收藏、转发、@提及、消息通知、站内私信、热搜榜、全文搜索、数据看板、深色模式等 **40+ 项功能**，全部前后端闭环，内置 11 个原生静态页面。

> 🎯 **推荐是本项目的核心**——完整生产级推荐系统（业务侧全链路）：多路召回（热门 / 话题 / 类目 / ItemCF / 新内容 / 关注）→ 微博式排序 → MMR 打散重排 → Thompson 冷启动 → 实时特征 → AB 实验 → 离线评估。每条推荐带可解释理由，行为全量回流闭环，生产适配代码齐备；微服务拆分后独立为在线（forum-recommend-server）/ 离线（forum-offline-job）/ 近线（forum-flink-nearline）三模块。弱训练侧（ItemCF + 规则加权），纯 Java 实现。详见 [推荐系统](#-推荐系统)。

> 🚀 **本项目由自研的 [nanocode](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 编程 Agent 开发完成** —— 通过自然语言对话驱动需求规划、代码生成、重构、调试与迭代，展示了 AI 辅助编程在实际项目中的完整落地。

## 功能特性

### 🎯 推荐系统
- ✅ **个性化推荐流**（✨ 推荐 Tab）：6 路召回（热门 / 话题 / 类目 / ItemCF / 新内容 / 关注）+ rank 归一化融合 + 微博式排序 + MMR 打散重排
- ✅ **可解释推荐**：每条带推荐理由（"因为你看过 #话题#" / "你关注的人发布了" / "和大家互动过的帖子相似" / "大家都在看"）与召回路来源
- ✅ **行为闭环**：点赞/收藏/评论/转发/搜索/关注/浏览/曝光/点击/负反馈 → 统一行为日志 → 画像 / ItemCF / 实时特征（模拟 Kafka→Flink→Redis 链路）
- ✅ **冷启动**：新内容 Thompson bandit 探索 + 曝光惩罚；新用户热门兜底
- ✅ **新帖流量池/赛马**：渐进式曝光档位（50→500→5000→50000）+ **Wilson 置信区间下界**判晋级/停止（仿抖音赛马机制）
- ✅ **阅读时长信号**：详情页停留时长（DWELL）上报 → 进画像（时长加权）/ ItemCF / 物品热度分（仿抖音"观看时长"）
- ✅ **详情相关推荐**：详情页"看过这篇的人还看"（ItemCF 相似帖）
- ✅ **AB 实验 + 离线评估**：哈希分层分桶；时间切分 + AUC/GAUC/Recall@K/NDCG@K/Coverage/Diversity/Freshness 7 指标
- ✅ **定时离线评估**：每 30 分钟自动跑一次评估，指标写日志 + 落盘 `data/eval-report.json`（可观察推荐质量随数据积累的变化，让系统"活"起来）
- ✅ **生产适配**：Kafka / Redis / Nacos / **Flink / MySQL** 适配代码（`-Pprod` 编译 + `@Profile("prod")` 激活，默认内存实现）
- ✅ **模拟活动**：定时任务持续产生新帖与互动，让系统像真实社区"转起来"

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
- ✅ 关注 / 粉丝，**关注流**（首页只看关注的人）；**关注按钮三态**（关注→已关注→hover 取消关注）
- ✅ 点赞 / 取消点赞（**乐观更新 + 点亮动效**，计数"万"缩写）
- ✅ 评论（发表 / 查看 / 删除；**热度/时间排序 + 评论点赞 + 楼中楼回复**）
- ✅ 收藏 / 取消收藏 + 我的收藏列表
- ✅ **转发**（一键转发原帖，转发计数，通知原帖作者；**转发弹窗预填"转发微博" + 可选"同时作为评论发表" + 转发泡**）
- ✅ **@提及**（`@用户名` 自动识别 + 通知 + 可点击跳转）
- ✅ **消息通知中心**（被点赞 / 评论 / 关注 / 转发 / 提及时通知，未读角标、已读管理）
- ✅ **站内私信**（双人会话、聊天页、未读数，HTTP 轮询刷新）
- ✅ 个人主页（聚合资料 + 动态 + 粉丝/关注数；**右上角用户名可点击进"我的主页"**）

### 🔍 发现与数据
- ✅ **全文搜索**：帖子（标题 / 内容 / 标签 / 话题）+ 用户综合搜索，搜索词计入热搜
- ✅ **热搜榜**：标签热度聚合（阅读×1 + 点赞×2 + 评论×3，30 天时间衰减），**首页右栏常驻（爆/沸/热/新标签）** + 独立热搜详情页（Top50 + 排名趋势）
- ✅ **新微博提示条**：有 N 条新微博，点击查看 + 新卡高亮渐隐
- ✅ 微博式三栏首页：左分类导航 ｜ 中发帖框 + 信息流（最新/关注/热门/**推荐**）｜ 右热搜 + 热门帖子
- ✅ 分类页独立路由：`/category/tech` 直达分类，可分享、刷新不丢状态
- ✅ 数据看板（用户 / 帖子 / 评论 / 点赞 / 今日新增统计）

### 🎨 趣味与体验
- ✅ 随机灵感便签（内置名言库）
- ✅ 随机决策转盘（一键帮你做选择）
- ✅ **深色模式**：全站 11 页支持，localStorage 记忆偏好，跟随系统
- ✅ 健康检测接口

### ⚙️ 系统能力
- ✅ 用户注册（**注册即自动登录**）/ 登录 / 退出（Session 认证 + 全局登录拦截 + **游客可浏览热门**）
- ✅ **账号管理仅管理员**："用户管理"按钮仅 admin 可见，接口层也有校验
- ✅ 密码安全（SHA-256 + 盐加密，用户信息脱敏），**所有账号密码统一为 admin123**
- ✅ 修改密码 / 修改资料（昵称 / 头像 / 简介）
- ✅ **JSON 文件持久化**（`data/*.json`，定时落盘 + 启动加载，重启不丢）
- ✅ 全局异常处理、统一响应体、JSR-303 参数校验

## 推荐系统：架构与数据流转链路

### 1. 总体分层（对标生产：离线 / 近线 / 在线）

| 层 | 时效 | 本模块对应 | 职责 |
|---|---|---|---|
| **离线层** | 小时 ~ 天 | `recommend/model`、`recommend/eval` | 行为日志 → ItemCF 相似度表；时间切分离线评估 |
| **近线层** | 秒 ~ 分 | `recommend/stream`、`recommend/feature` | 实时特征窗口聚合（模拟 Kafka → Flink → Redis） |
| **在线层** | 毫秒 | `recommend/service` + `recall/rank/rerank/coldstart` | 漏斗编排，低延迟下发；配置 / AB 分流 |

### 2. 在线请求链路（一次 `/api/recommend/feed`）

```
GET /api/recommend/feed?page&size      (session: userId)
  │
  ▼  RecommendService.recommend(ctx, username, expId)
  ├─① 画像      UserProfileService.userProfile(uid)
  │            → 话题/类目兴趣权重(时间衰减) + 最近交互序列 + 活跃度
  ├─② 召回      RecallService.recall(ctx) → 6 路并行:
  │             hot(热度分) / topic(兴趣话题) / category(兴趣类目)
  │             / itemcf(历史相似) / newitem(冷启池) / follow(关注+二度转发)
  │            └ MergeRecallService: 每路 rank归一化 1/(rank+60)
  │                 + 通道加权(RecConfig) + 去重 → List<Candidate>
  ├─③ 排序      RuleRankService.rank(ctx, candidates)
  │            rankScore = (Σ w_f·f + explore) × 时效衰减(半衰期4h)
  │            特征: interact·quality·interest·social·author·hot·realtime
  │            → List<RankedItem>(携带特征分构成 + 推荐理由)
  ├─④ 重排      DiversifyRerankService.rerank: 同类连续≤2 打散 + MMR → TopN
  ├─⑤ 冷启动    冷用户热门兜底 + 新内容 explore(Thompson)
  ├─⑥ 曝光      逐条 BehaviorLogger.log(EXPOSE, scene, expId)
  └─⑦ 下发      组装 RecommendPostVO(帖子 + reason + sources + score) → PageResult
```

### 3. 行为回流链路（数据闭环，推荐越用越准）

**本地模式（默认，全内存模拟 Kafka→Flink→Redis）**
```
用户行为(赞/藏/评/转/搜/关注/浏览/曝光/点击/负反馈)
  → BehaviorLogger（InMemoryBehaviorLogger）
     ├→ BehaviorLogRepository → data/behavior-log.json   ← 画像/评估事实源
     └→ BehaviorEventQueue（模拟 Kafka）
          ├→ RealtimeFeatureWindow（模拟 Flink, 每 5s flush）
          │    └→ RealtimeFeatureStore（模拟 Redis）
          │         └→ 下次排序特征 realtime（用户话题投影 + 物品热度爆发）
          └→ ColdStartFeedbackListener
                └→ NewItemPool.recordOutcome（Thompson 后验: 点击 α+1 / 曝光无转化 β+1）
  → ItemCfModelStore 按行为数变化自动重建相似度表 → 供 itemcf 召回 + 详情"相关推荐"
```

**生产模式（`-Pprod` 编译 + `--spring.profiles.active=prod`，真实中间件）**
```
用户行为 → KafkaBehaviorLogger → Kafka topic "behavior-log"（一份行为, 两个独立消费组）
  ├─▶ [Flink 作业 group=mini-forum-realtime]  ← 近线
  │      KafkaSource → 滑动窗口(5min/1min) → Redis "realtime:{user|post}:{id}"(TTL 60s)
  │        └─▶ 在线排序 realtimeMatch 读 Redis（RedisRealtimeFeatureStore）
  └─▶ [KafkaBehaviorConsumer group=mini-forum-offline]  ← 离线侧（应用内, 500ms poll）
         ├─▶ BehaviorLogRepository（内存）→ 画像 / ItemCF / 离线评估
         └─▶ BehaviorEventQueue → ColdStartFeedbackListener → Thompson 后验

持久化：MySqlDataStore 每 30s 把内存各仓库（含行为）快照到 MySQL mini_store，重启 loadAll 恢复；
       JSON DataStore 在 prod 下禁用（@Profile("!prod")）。
```

### 4. 离线训练与评估链路

```
behavior-log（过滤曝光/负反馈等非反馈信号）
  → TimeSplitter 时间切分（前 80% 训练 / 后 20% 测试，禁随机切分）
  → 训练集构建 ItemCF + 热门信号 → 为测试用户生成 TopK 排序
  → 对比测试集真实深度互动 → Metrics：
     AUC / GAUC / Recall@K / NDCG@K / Coverage / Diversity / Freshness
  → 结论：离线只做初筛，最终以线上 AB 为准

定时调度（OfflineEvalScheduler，默认每 30 分钟自动执行）：
  行为数不足时跳过 → evaluate() → 指标写日志 + 追加 data/eval-report.json（累积趋势）
```

### 5. 配置 / AB / 生产适配

```
RecConfig（召回权重 / 排序权重 / 冷启比例 / 打散参数 / 时效半衰期）
  ├─ InMemoryConfigService（默认, 加载 application.yml, 运行时热更新）
  └─ prod.nacos.NacosConfigService（@Profile("prod"), 配置中心下发）

AbExperimentService：floorMod(hash(uid:salt), 100) 分桶
  → 对照组 A 全局配置 / 实验组 B 多样性变体 → 行为日志带 expId → 离线归因

生产适配（`-Pprod` 编译 src/prod/java，`@Profile("prod")` 运行时激活，默认内存实现）：
  prod.kafka.KafkaBehaviorLogger（行为→Kafka topic behavior-log）
  prod.kafka.KafkaBehaviorConsumer（Kafka behavior-log→行为库+事件队列，离线侧回灌）
  prod.kafka.KafkaPostCreatedProducer（发帖→Kafka topic post-created，@Profile("prod")）
  prod.kafka.KafkaPostCreatedConsumer（post-created→预热流量池，@Profile("prod")）
  prod.redis.RedisRealtimeFeatureStore（实时特征→Redis, TTL 60s）
  prod.redis.RedisFollowRepository（关注关系→Redis Hash+ZSET 索引，@Profile("prod") 高频读写）
  prod.nacos.NacosConfigService（配置→Nacos rec-config, 监听热更新）
  prod.flink.FlinkRealtimeWindow（Flink 实时特征作业：Kafka→滑动窗口→Redis，独立进程）
  prod.mysql.MySqlDataStore（MySQL 持久化：JSON 快照表 mini_store，替代 JSON 文件）
```

### 6. 生产模式闭环审计

| 数据路径 | 落点 | 状态 |
|---|---|---|
| 行为采集 → Kafka | KafkaBehaviorLogger | ✅ |
| Kafka → 近线实时特征(Redis) | Flink 作业（独立消费组 mini-forum-realtime） | ✅ |
| Kafka → 离线侧行为库 | KafkaBehaviorConsumer（独立消费组 mini-forum-offline） | ✅ |
| 在线请求读近线特征 | realtimeMatch → RedisRealtimeFeatureStore | ✅ |
| 在线请求读画像/ItemCF | 内存库（消费者喂 + MySQL 恢复） | ✅ |
| 行为 → 冷启动反馈 | consumer → BehaviorEventQueue → ColdStartFeedbackListener | ✅ |
| 持久化 → 重启恢复 | MySqlDataStore mini_store（JSON DataStore 已禁用） | ✅ |
| 配置 → Nacos | NacosConfigService（内存配置已禁用） | ✅ |

**已知取舍（非致命，落地前需知晓）**：
1. Flink 暂不算用户 topicClicks（缺帖子维度 join）——prod 下 realtime 特征偏"物品热度爆发"，用户话题投影弱化
2. 单实例假设：多实例部署时各实例内存库独立、Kafka 消费组分片，画像/ItemCF 不共享
3. ≤30s 数据丢失窗口：行为先入内存、每 30s 落 MySQL，崩溃丢窗口内数据（可调小 `app.persistence.interval-ms`）
4. Kafka 自动提交 offset + 崩溃重放可能重复计数（可加幂等去重）

## 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 编程语言（LTS） |
| Spring Boot 2.7.18 | 应用框架 |
| Spring Web | RESTful 接口 |
| Spring Validation | 参数校验 |
| 推荐算法（弱训练侧） | ItemCF（共现余弦）、规则加权排序、Thompson sampling，纯 Java 手写 |
| 中间件形态 | Kafka / Flink / Redis / Nacos 以「接口 + 内存实现默认 + `@Profile("prod")` 适配」三件套落地 |
| Maven | 构建工具，**多模块聚合**（forum-core / forum-admin-server / forum-recommend-server / forum-offline-job / forum-flink-nearline / demo-runner） |
| JSON 文件持久化 | 内存存储（`ConcurrentHashMap`）+ `data/*.json` 落盘 |

**零第三方中间件**：无数据库、无 Redis、无消息队列、无 WebSocket —— 全部功能基于纯 Java 实现。

## 项目结构

```
my-first-nanobot-server/                  # Maven 多模块（父 POM forum-parent，聚合 6 模块）
├── forum-core/              # ★ 共享域（纯库，无 main）
│   └── src/main/java/com/tkzou/miniforum/
│       ├── entity/ repository/ dto/ common/ exception/ util/    # 数据层与基础件
│       ├── feed/            #   关注流 inbox（FollowFeedStore 接口 + 内存/Redis 实现）
│       └── recommend/       #   behavior(行为日志) + stream(事件接口) 共享件
│       └── dto/PostAssembler.java   # 帖子视图装配（admin 与 recommend 共用，破依赖环）
├── forum-admin-server/      # ★ 主业务：帖子/用户/评论/关注/feed/搜索/热搜/通知/私信
│   └── src/main/java/com/tkzou/miniforum/{controller, service, config, exception}
├── forum-recommend-server/  # ★ 推荐核心：召回/排序/重排/冷启动/画像/AB/配置 + 生产适配
│   └── src/main/java/com/tkzou/miniforum/recommend/
│       ├── recall/ rank/ rerank/ coldstart/ feature/ model/     # 推荐管道
│       ├── config/ ab/ domain/ service/ stream/                 # 配置/AB/编排/事件
│       └── prod/            #   Kafka/Redis/Nacos 生产适配(@Profile("prod"))
├── forum-offline-job/       # 离线层：离线评估（OfflineEvalScheduler）+ OfflineJobApplication
├── forum-flink-nearline/    # 近线层：Flink 实时特征作业（-Pprod 才构建，独立进程）
├── demo-runner/             # ★ 演示启动器：聚合 admin+recommend 单进程
│   ├── src/main/java/.../MiniForumApplication.java   # 启动类（扫描 com.tkzou.miniforum）
│   ├── src/main/java/.../persistence/DataStore.java  # JSON 持久化
│   ├── src/main/java/.../controller/RecommendController.java  # 推荐 web 装配
│   └── src/main/resources/static/  # 11 个原生静态页面 + application.yml
├── data/                    # 运行时 JSON 数据（自动生成，gitignore）
├── docs/                    # 需求规划 / API 文档 / 推荐系统方案
├── scripts/                 # 辅助脚本（启停 + seed_users / seed_posts / seed_recsys_data 造数）
├── Dockerfile
├── pom.xml                  # 父 POM（forum-parent）
└── README.md
```

> 模块依赖（无环）：`forum-core` ← `forum-admin-server` / `forum-recommend-server` / `forum-offline-job` / `forum-flink-nearline`；`demo-runner` 聚合 admin + recommend 运行演示；`forum-offline-job` 依赖 recommend；`forum-flink-nearline` 依赖 core + recommend。

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.6+

### 运行方式

```bash
# 方式一：Maven 直接运行（root 是父 POM 聚合器，需指定 demo-runner 模块）
JAVA_HOME='D:\devSoftWare\jdk17\jdk-17.0.19+10' mvn -pl demo-runner spring-boot:run

# 方式二：打包运行
mvn clean package && java -jar demo-runner/target/demo-runner-1.0.0.jar
```

启动后访问 <http://localhost:8090/>，将自动跳转到登录页。**默认账号**：`admin / admin123`（管理员）。

> **生产构建**：`-Pprod` 会追加 `forum-flink-nearline` 模块（Flink 实时特征作业），并编译 `demo-runner` 的 `src/prod/java`（MySqlDataStore MySQL 持久化）：
> ```bash
> mvn -Pprod clean package   # 生产包（含 Flink 作业 / MySQL 适配）
> SPRING_PROFILES_ACTIVE=prod java -jar demo-runner/target/demo-runner-1.0.0.jar  # 运行时激活真适配（需 Kafka/Redis/Nacos/MySQL）
> ```
> 本地默认（不带 `-Pprod`、不切 prod profile）仍是零中间件、内存 + JSON 文件。

### 体验推荐系统

```bash
# 1.（可选）造演示数据：30 用户 / 150 帖 / 大量互动（密码统一 admin123）
python scripts/seed_recsys_data.py

# 2. 登录 user01~user30（密码 admin123）→ 首页切到 "✨ 推荐" Tab
#    每条带推荐理由；点开帖子详情底部有"相关推荐"；点🙅 记负反馈
# 3. 模拟活动任务每 15 分钟自动产生新帖 + 互动，让系统持续"转起来"
```

### 登录认证

- 默认管理员账号：`admin` / `admin123`（拥有账号管理权限）
- **游客可浏览**：热门帖子、最新动态、搜索、标签、帖子详情（无需登录）
- **需登录**：发帖、点赞、评论、收藏、转发、关注、私信、个性化推荐（写操作返回 `401`）
- 首页未登录时弱化登录入口：显示"登录 / 注册"按钮 + 发帖框处提示登录
- **注册**：登录页可切换到"注册"表单，注册成功即自动登录

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/auth/register` | 注册（公开，注册后自动登录） |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `GET` | `/api/auth/me` | 获取当前登录用户 |

### 配置

默认配置见 `src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8090` | 服务端口 |
| `app.data-dir` | `./data` | 数据持久化目录 |
| `app.persistence.enabled` | `true` | 是否启用 JSON 持久化 |
| `app.persistence.interval-ms` | `30000` | 定时保存间隔（毫秒） |
| `app.rec.*` | — | 推荐系统配置（召回/排序权重、冷启比例、打散参数、**定时评估**等） |
| `app.rec.eval-enabled` | `true` | 定时离线评估开关 |
| `app.rec.eval-interval-ms` | `1800000` | 离线评估间隔（毫秒，默认 30 分钟） |
| `app.sim.enabled` | `true` | 模拟活动开关 |
| `app.sim.interval-ms` | `900000` | 模拟活动间隔（毫秒，默认 15 分钟） |
| `app.sim.posts-per-tick` | `2` | 每轮新帖数 |
| `app.sim.interactions-per-tick` | `2` | 每轮互动数 |

### API 概览

所有接口统一返回 `code / message / data` 结构。完整接口文档见 **[docs/API.md](docs/API.md)**。

| 模块 | 主要接口 |
|------|----------|
| 用户 | `POST /api/users`、`GET /api/users`、`PUT /api/users/{id}/profile`、`PUT /api/users/{id}/password`、`GET /api/users/{id}/profile` |
| 帖子 | `POST /api/posts`、`GET /api/posts`（支持 page/size/tag/category/**topic**）、`GET /api/posts/{id}`、`PUT /api/posts/{id}`、`DELETE /api/posts/{id}`、`GET /api/posts/hot`、`POST /api/posts/{id}/like`、`POST /api/posts/{id}/repost`、回收站/恢复 |
| 评论 | `POST /api/posts/{postId}/comments`（支持 **parentId 楼中楼回复**）、`GET /api/posts/{postId}/comments?sort=heat|time`、`POST /api/comments/{commentId}/like`、`DELETE /api/comments/{id}` |
| 关注 | `POST /api/follows/{userId}`、`DELETE /api/follows/{userId}`、`GET /api/follows/following`、`GET /api/follows/followers`、`GET /api/follows/feed` |
| 通知 | `GET /api/notifications`、`GET /api/notifications/unread-count`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all` |
| 收藏 | `POST /api/favorites/{postId}`、`DELETE /api/favorites/{postId}`、`GET /api/favorites/my` |
| 分类 | `GET /api/categories` |
| 热搜 | `GET /api/hot/search`（带 爆/沸/热/新 等级） |
| 标签/话题 | `GET /api/tags`、`GET /api/tags/topics`、`GET /api/tags/{tag}/posts` |
| 搜索 | `GET /api/search?keyword=` |
| **推荐** | `GET /api/recommend/feed`（推荐流）、`GET /api/recommend/related`（相关推荐）、`POST /api/recommend/track`（点击/负反馈打点） |
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

`data/` 目录文件：`users.json`、`posts.json`、`comments.json`、`likes.json`、`follows.json`、`notifications.json`、`favorites.json`、`search-records.json`、`conversations.json`、`messages.json`、`behavior-log.json`（推荐行为日志）。

## 测试

```bash
JAVA_HOME='D:\devSoftWare\jdk17\jdk-17.0.19+10' mvn test
```

共 **87 个测试**（分布在各模块：forum-core 24 / forum-admin-server 14 / forum-recommend-server 16 / forum-offline-job 6 / demo-runner 27，含端到端集成测试）。

## 推荐系统（深度参考）

| 文档 | 内容 |
|---|---|
| [docs/推荐系统设计方案.md](docs/推荐系统设计方案.md) | 推荐系统方案（架构 / 数据流 / 优先级 / 里程碑） |
| [docs/微博推荐调研.md](docs/微博推荐调研.md) | 微博推荐产品与分发机制深度调研 |
| [docs/推荐系统深度调研报告-抖音版.md](docs/推荐系统深度调研报告-抖音版.md) | 抖音短视频推荐架构 + 微博/小红书三方对比 |
| [docs/推荐系统微服务拆分方案.md](docs/推荐系统微服务拆分方案.md) | 大厂生产级推荐系统微服务拆分架构（三横×功能域 + 从现状单体的分阶段迁移） |
| [docs/feed流架构调研与对比.md](docs/feed流架构调研与对比.md) | 生产级关注流架构（推/拉/混合）+ 与本项目对比与演进 |
| [docs/内容分发系统架构调研与对比.md](docs/内容分发系统架构调研与对比.md) | 发帖→展示的内容分发全链路（统一事件总线 Kafka + 多路下游 + Outbox）+ 本项目差距清单 |
| [docs/内容生产系统架构调研与对比.md](docs/内容生产系统架构调研与对比.md) | 发帖写路径（校验/幂等/内容处理/状态机/落库/事件发布）+ 一条帖子从生产到展示的全生命周期 |
| [docs/生产化落地开发清单.md](docs/生产化落地开发清单.md) | 生产化落地开发清单（P0 幂等+Outbox → P1 数据中间件化+Snowflake → P2 分发增强 → P3 加固编排，prod 优先） |
| [docs/数据存储矩阵.md](docs/数据存储矩阵.md) | 数据存储放置决策（MySQL=事实 / Redis=热数据 / Kafka=事件 / ClickHouse=行为全量）+ 行为日志选型 |
| [docs/系统功能全景.md](docs/系统功能全景.md) | 功能全景盘点（基于源码核验） |
| [docs/API.md](docs/API.md) | 完整接口文档 |
| [docs/微博化改版规划.md](docs/微博化改版规划.md) | 微博化改版规划 |

## 功能闭环

```
发帖（分类/标签/Markdown/话题/@提及）
  → 浏览（三栏首页/分类/搜索/热搜/热门/话题榜/✨推荐）
  → 互动（点赞/评论/收藏/转发/关注）
  → 通知（点赞/评论/关注/转发/提及 → 通知中心）
  → 私信（双人会话/聊天）
  → 个人中心（我的主页/我的帖子/草稿/收藏/回收站/资料）
```

## 许可证

[MIT](LICENSE)

## 关于

本项目由自研的 **nanocode** 编程 Agent 开发完成。nanocode 是一款基于 Java 的 AI 编程助手命令行工具，能够理解自然语言指令，自动完成需求规划、代码编写、重构、调试与测试等开发任务，让开发者通过对话即可快速构建完整项目。本项目的全部功能（40+ 项）均由 AI 辅助完成开发与迭代。

如果你对 AI 辅助编程或 nanocode 感兴趣，欢迎访问 [项目主页](https://github.com/ztkyaojiayou/my-first-nanobot-build-server) 了解更多。
