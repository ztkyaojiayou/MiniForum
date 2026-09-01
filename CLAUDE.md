# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库中的工作提供指引。

## 项目概览

**MiniForum** —— 一个微博风格轻量论坛系统，核心是**生产级推荐系统**（多路召回 → 排序 → MMR 打散重排 → 冷启动 → 实时特征 → AB 实验 → 离线评估）。技术栈为 Spring Boot 2.7 + Java 17，Maven 多模块。默认构建**零中间件**（内存 + JSON 文件持久化到 `data/`），并通过 `prod` Maven profile 提供 Kafka / Redis / Nacos / Flink / MySQL 生产适配。完整功能清单见 `README.md`；`docs/` 下是深入的方案/调研文档（推荐系统、feed 架构、内容分发、生产化落地清单）。

本项目由自研的 AI 编程 Agent（nanocode）开发。`README.md` 与 `docs/*.md`（中文）内容详实且权威 —— 在发明新行为前请先阅读。

## 构建 / 运行 / 测试

根 `pom.xml` 是聚合器（`forum-parent`），需用 `-pl` 指定模块。**`demo-runner` 是可运行应用**（把 admin + recommend 聚合为单进程）。

```bash
# 运行应用（端口 8090）
mvn -pl demo-runner spring-boot:run

# 打包可运行 jar
mvn clean package
java -jar demo-runner/target/demo-runner-1.0.0.jar

# 测试（全部模块）或单个模块 / 单个测试
mvn test
mvn -pl forum-recommend-server test
mvn -pl forum-recommend-server -Dtest=SomeTest -DfailIfNoTests=false test

# 生产构建：追加 forum-flink-nearline 模块 + 编译 src/prod/java 适配
mvn -Pprod clean package
SPRING_PROFILES_ACTIVE=prod java -jar demo-runner/target/demo-runner-1.0.0.jar
```

- 默认账号：`admin / admin123`（管理员）；所有种子用户密码均为 `admin123`。
- 运行时数据位于 `data/*.json`（已 gitignore）；用 `python scripts/seed_recsys_data.py` 重建演示数据。
- Docker：`docker build -t mini-forum . && docker run -p 8090:8090 -v $(pwd)/data:/app/data mini-forum`（多阶段构建，`-DskipTests`）。
- 端口统一为 8090（application.yml、Dockerfile EXPOSE 一致）。

## 架构

### 模块反应堆（依赖顺序，无环）

```
forum-core（纯库，无 main、无 web 依赖）
   ← forum-admin-server      （帖子/用户/评论/关注/feed/搜索/热搜/通知/私信 的 controller + service）
   ← forum-recommend-server  （召回/排序/重排/冷启动/画像/AB/配置 + 生产适配）
   ← forum-offline-job       （离线评估调度器；依赖 recommend）
   ← forum-flink-nearline    （Flink 实时特征作业；仅 -Pprod，独立进程）
   ← demo-runner             （Spring Boot 应用：启动类、DataStore 持久化、web 装配）
```

`forum-recommend-server` 与 `forum-admin-server` **都是库**——都不能独立运行；`demo-runner` 是唯一可启动的应用，也是唯一含 `static/`、`application.yml` 与 `main` 启动类的模块。`forum-core` 产出 `test-jar`，供 recommend/offline 的测试使用。

### 中间件「接口 + 内存默认实现 + prod 适配」三件套模式

每个外部系统（Kafka、Redis、Nacos、Flink、MySQL、ClickHouse）都用同一种方式建模，这也是零中间件默认的关窍：

1. 主源码树里定义**接口**（各包顶层，如 `repository/PostRepository`）与默认的**内存实现**（归入同包 `impl/` 子包，如 `repository/impl/InMemoryPostRepository`，始终激活）。
2. 一个 `@Profile("prod")` 的**真实适配**——仅在 `-Pprod` 下编译（demo-runner 的 `src/prod/java`，或 recommend-server 的 `src/main/java/.../prod/`），仅在 `SPRING_PROFILES_ACTIVE=prod` 下激活。
3. Spring 按 profile 选择对应 bean；其余代码只依赖接口，两种模式下行为一致。

示例：`KafkaBehaviorLogger` / `KafkaBehaviorIngestor` / `KafkaPostCreated*`、`RedisRealtimeFeatureStore`、`MySqlFollowRepository`（MySQL 事实 + Redis ZSET 热缓存） / `RedisFollowFeedStore`、`NacosConfigService`、`FlinkRealtimeWindow`、`MySqlDataStore` / `MySqlOutboxStore`。已知的生产环境取舍见 README §6（如 Flink 未聚合用户 topicClicks、单实例假设、≤30s 数据丢失窗口）。

### 推荐管道（在线，每次 `/api/recommend/feed`）

由 `RecommendService` 编排 → `UserProfileService.userProfile`（画像，三域之一，见 `forum-recommend-server/recommend/profile|feature|graph`）→ `RecallService.recall`（6 路并行召回：热门/话题/类目/ItemCF/新内容/关注）→ `MergeRecallService`（每路 `1/(rank+60)` 归一化 + 通道加权 `RecConfig` + 去重）→ `CoarseRankService`（粗排，简化实现按融合分截断到 `coarseTopN`，默认 200 即透传）→ `RuleFineRankService.rank`（加权特征求和 × 时效衰减半衰期 4h）→ `DiversifyRerankService`（同类连续 ≤2 打散 + MMR）→ 冷启动（新内容 Thompson、新用户热门兜底）→ 曝光打点。每条候选携带可解释理由 + 召回路来源。标注调用链见 `README.md`。

### 行为回流闭环（数据回流以改进推荐）

每个互动（赞/藏/评/转/搜/关注/浏览/曝光/点击/负反馈）→ `BehaviorLogger` → 同一事件的两种消费方：

- `BehaviorLogRepository` → `data/behavior-log.json` → 用户画像 + 离线评估 + ItemCF 相似度表（随行为数变化自动重建）。
- `BehaviorEventQueue`（模拟 Kafka）→ `RealtimeFeatureWindow`（模拟 Flink，每 5s flush）→ `RealtimeFeatureStore`（模拟 Redis）→ 下次排序的实时特征；以及 → `ColdStartFeedbackListener` → `NewItemPool` 的 Thompson 后验更新。

生产模式下消费组拓扑会变化（见 README §3-4）：一个 Kafka topic 被 Flink 作业（group `mini-forum-realtime`）与一个应用内消费者（group `mini-forum-offline`）分别消费。

### 离线 / 近线 / 在线分层

- **离线**（`recommend/model`、`recommend/eval`、`forum-offline-job`）：ItemCF 相似度 + 时间切分（80/20）离线评估，7 项指标（AUC/GAUC/Recall@K/NDCG@K/Coverage/Diversity/Freshness）。`OfflineEvalScheduler` 每 30 分钟运行 → 写 `data/eval-report.json`。
- **近线**（`recommend/stream`、`recommend/feature`、`forum-flink-nearline`）：实时特征窗口聚合（Kafka→Flink→Redis）。
- **在线**（`recommend/service` + recall/rank/rerank/coldstart）：低延迟漏斗编排。

### 共享事件总线（近期工作——「一份事件、多路消费」）

`forum-core/recommend/stream/` 定义领域事件与总线接口（`PostCreatedEventBus`、`PostCreatedEvent`、`PostCreatedProducer`、`OutboxStore`、`InMemoryOutboxStore`、`BehaviorEventQueue`、`PostCreatedConsumer`）。下游消费者实现 `PostCreatedConsumer` 接口（`name()` 消费组标识 + `onPostCreated` 处理），由 `PostCreatedConsumerRegistrar`（recommend-server）利用 Spring 的 `List<PostCreatedConsumer>` 自动收集并统一注册到总线——订阅关系集中一处、新增消费方零改动。当前三个并列订阅者：`FanoutPostCreatedConsumer`（关注流扇出）、`SearchIndexPostCreatedConsumer`（建搜索索引）、`TrafficPoolPostCreatedConsumer`（预热冷启动池）。对标生产环境 Kafka 将一份 `post-created` 事件广播给多个独立消费组。新增下游消费者：新建 `@Component` 实现 `PostCreatedConsumer` 即可，而非直接调用 repository。

### 持久化

默认 `DataStore`（demo-runner）每 30s（`app.persistence.interval-ms`）把内存 `ConcurrentHashMap` 各仓库快照到 `data/*.json`，启动时加载。`prod` 下 `MySqlDataStore` 取而代之（JSON 存储为 `@Profile("!prod")`）。实体位于 `forum-core/entity/`，仓储接口位于 `forum-core/repository/`、内存默认实现在 `repository/impl/`（prod 行级实现在 demo-runner `src/prod`）。

## 约定

- 包名 `com.tkzou.miniforum`，遵循 Spring Boot 标准分层；Javadoc/注释用中文描述意图。
- 静态页面放在 `demo-runner/src/main/resources/static/`（无独立前端）；页面通过 AJAX 调 REST API。所有接口统一返回 `code/message/data`。
- `data/`、`target/`、`.idea/`、`.nanocode/` 已 gitignore——不要提交运行时数据。

## 测试

共 **173 个测试**（core 44 / admin 39 / recommend 54 / offline 8 / demo-runner 28，含端到端集成测试）。`forum-core` 的 test-jar 提供 `TestBehaviors`，供 recommend/offline 测试共用。运行单个测试需加 `-pl <module> -Dtest=<ClassName>`；因模块可能不含该测试，请使用 `-DfailIfNoTests=false`。
