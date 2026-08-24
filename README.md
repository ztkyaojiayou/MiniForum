# MiniForum · 微博式社区 × 推荐系统

> **一个"小红书式推荐系统"在微博形态社区的完整落地** —— 业务侧全链路（召回 → 排序 → 重排 → 冷启动 → 实时特征 → AB 实验 → 离线评估），弱训练侧（ItemCF + 规则加权 + Thompson bandit），**纯 Java 实现**，Spring Boot 2.7 + 内存存储 + JSON 持久化，零第三方中间件即可跑通。

---

## 一、核心：推荐系统（业务侧全链路）

### 在线漏斗（一次推荐请求）

```
GET /api/recommend/feed  (session:userId)
  → 用户画像(话题/类目兴趣权重, 时间衰减)
  → 6 路召回并融合：热门 / 话题 / 类目 / ItemCF / 新内容 / 关注
       (rank归一化 1/(rank+60) + 通道加权 + 去重)
  → 微博式排序 rankScore = (interact + quality + interest + social
                            + author + hot + realtime + explore) × 时效衰减
  → MMR 打散重排(同类连续≤2 + 多样性)
  → 冷启动兜底(新用户补热门) + 逐条记 EXPOSE
  → 下发：带可解释理由 + 命中的召回路来源
```

### 行为闭环（生产形态 = Kafka → Flink → Redis）

```
点赞/收藏/评论/转发/搜索/关注/浏览/点击/负反馈
  → BehaviorLogger（织入现有 service + 推荐曝光/打点）
  → BehaviorLogRepository（JSON 持久化，画像/评估的事实源）
  → BehaviorEventQueue(模拟 Kafka) → RealtimeFeatureWindow(模拟 Flink, 窗口聚合)
      → RealtimeFeatureStore(模拟 Redis) → 下一次排序特征 realtime 生效
```

### 关键特性

- **可解释推荐**：每条带理由——"因为你看过 #话题#" / "你关注的人发布了" / "和大家互动过的帖子相似" / "大家都在看"
- **多路召回 + 可配权重**：`RecConfig` 配置中心控制各通道/排序特征权重，运行时热更新
- **Thompson bandit 冷启动**：新内容 Beta 后验 + 曝光惩罚，探索加分随用户冷热衰减
- **实时特征**：近线层窗口聚合用户话题投影 + 物品热度爆发，参与排序
- **AB 实验**：哈希分层正交分桶，实验组走多样性变体，行为日志携带 expId 可离线归因
- **离线评估**：时间切分 + 7 指标（AUC / GAUC / Recall@K / NDCG@K / Coverage / Diversity / Freshness）
- **生产适配代码**：Kafka / Redis / Nacos 适配在 `recommend/prod/`（`@Profile("prod")` 激活，默认内存实现）

---

## 二、微博风格体验

| 模块 | 亮点 |
|---|---|
| **热搜榜** | 首页右栏常驻，爆/沸/热/新标签 + 前 3 名序号高亮 + 热度"万"缩写 + 刷新 |
| **信息流** | 最新 / 关注 / 热门 / **推荐** 四 Tab；**新微博提示条** + 新卡高亮渐隐 |
| **卡片** | 点赞乐观更新+动效、计数万缩写、**转发泡**（内嵌原帖灰框）、推荐理由行 |
| **转发** | 弹窗预填"转发微博" + 可选"**同时作为评论发表**" |
| **评论** | 热度/时间排序、评论点赞、**楼中楼**"共 N 条回复 · 展开回复" |
| **互动** | 关注按钮三态（关注→已关注→hover 取消关注）、发博字数临界提示 |
| **游客/账号** | 游客可浏览热门（参考微博首页）、注册即自动登录、账号管理仅管理员、点右上角用户名进"我的主页" |

---

## 三、技术栈

| 技术 | 说明 |
|---|---|
| Java 17 + Spring Boot 2.7 | 应用框架 |
| 内存存储 + JSON 持久化 | `ConcurrentHashMap` + `data/*.json`（30s 落盘，重启不丢） |
| 推荐算法（弱训练侧） | ItemCF（共现余弦）、规则加权排序、Thompson sampling，纯 Java 手写 |
| 中间件形态 | Kafka / Flink / Redis / Nacos 以「接口 + 内存实现默认 + `@Profile("prod")` 适配」三件套落地 |

---

## 四、目录结构（推荐子系统为骨架）

```
src/main/java/com/tkzou/miniforum/
├── recommend/                  # ★ 推荐子系统（feature 式，业务侧）
│   ├── service/   RecommendService      漏斗编排核心
│   ├── recall/    RecallService + 6 通道 + MergeRecallService
│   ├── rank/      RuleRankService       微博式排序
│   ├── rerank/    DiversifyRerankService MMR 打散重排
│   ├── coldstart/ ColdStartService / NewItemPool / ThompsonBandit
│   ├── feature/   用户画像 / 物品特征 / 实时特征(接口+内存实现)
│   ├── behavior/  统一行为日志(实体/仓库/采集器, 织入现有 service)
│   ├── stream/    事件队列(模拟Kafka) + 实时窗口(模拟Flink) + 存储(模拟Redis)
│   ├── model/     ItemCF 构建/模型/打分/存储
│   ├── config/    RecConfig 配置中心
│   ├── ab/        AB 实验分桶
│   ├── eval/      离线评估(时间切分 + 7 指标)
│   ├── domain/    管道中间类型(RecallHit/Candidate/RankedItem/Context)
│   └── prod/      Kafka/Redis/Nacos 生产适配(@Profile("prod"))
├── controller/    RecommendController(feed/related/track) 等
├── service/       PostService 等(织入行为埋点) + SimulatedActivityService(模拟活动)
├── repository/ / entity/ / dto/ / persistence/ / config/ / util/
```

> 完整数据流程说明见 `docs/推荐系统设计方案.md`；微博场景设计依据见 `docs/微博推荐调研.md`。

---

## 五、快速开始

```bash
# 1. 启动（JDK 17，注意 JAVA_HOME 指向 JDK17）
JAVA_HOME='D:\devSoftWare\jdk17\jdk-17.0.19+10' mvn spring-boot:run
# 访问 http://localhost:8090

# 2. （可选）造演示数据：30 用户 / 150 帖 / 大量互动
python scripts/seed_recsys_data.py

# 3. 登录体验推荐（密码统一 admin123）
#    user01~user30 → 首页切到 "✨ 推荐" Tab，看个性化理由 + 相关推荐
```

**默认账号**：`admin / admin123`（管理员，有账号管理权限）

---

## 六、配置

### 推荐系统（`app.rec.*`）
各召回通道权重、排序特征权重、冷启动比例、打散/MMR 参数、时效半衰期等，见 `application.yml`，可在运行时经配置中心更新（生产走 Nacos）。

### 模拟活动（`app.sim.*`，让系统持续"转起来"）
| 配置 | 默认 | 说明 |
|---|---|---|
| `app.sim.enabled` | `true` | 开关 |
| `app.sim.interval-ms` | `900000` | 每轮间隔（15 分钟） |
| `app.sim.posts-per-tick` | `2` | 每轮新帖数 |
| `app.sim.interactions-per-tick` | `2` | 每轮互动数 |

---

## 七、测试

`JAVA_HOME='D:\devSoftWare\jdk17\jdk-17.0.19+10' mvn test` —— **43 个测试**，含推荐系统端到端集成测试（`@SpringBootTest` 真实装配跑通"造数→召回→排序→重排→推荐→评估"）。

---

## 八、文档

| 文档 | 内容 |
|---|---|
| [docs/推荐系统设计方案.md](docs/推荐系统设计方案.md) | 推荐系统方案（架构/漏斗/数据流/优先级/里程碑） |
| [docs/微博推荐调研.md](docs/微博推荐调研.md) | 微博推荐产品与分发机制深度调研 |
