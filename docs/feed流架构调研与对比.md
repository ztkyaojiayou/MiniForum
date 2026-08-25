# Feed 流架构调研与对比（生产级 vs MiniForum 现状）

> 调研日期：2026-08-25
> 主题：主流互联网 C 端 feed 流（关注流/timeline）系统的架构设计，并与 MiniForum 当前实现对比。
> 配套：与《推荐系统深度调研报告-抖音版》《微博推荐调研》《推荐系统设计方案》并列。
> 说明：本环境 WebSearch/WebFetch 受限，内容基于公开工程资料（Twitter/微博/Instagram 架构分享、System Design 经典资料）与行业共识综合；标注【推断】处为未官方实锤的量化细节。

---

## 0. 一句话核心洞察

**生产级关注流 = 把"读时聚合"提前到"写时扩散"（推模式），再用"推拉结合"按账号粉丝量/活跃度分流来中和写放大**。存储上 timeline 只存 ID 序列（Redis ZSet），读用游标分页、配多级缓存。**Kafka 是 fanout 的事件分发总线，不是 feed 的存储。**

---

## 1. Feed 流是什么：Timeline vs 信息流

| 维度 | Timeline / 关注流 | 信息流 / 推荐流 |
|---|---|---|
| 内容来源 | 关系图谱（关注的人） | 兴趣图谱（算法推测） |
| 排序 | 时间为主（确定性） | 算法打分（非确定性） |
| 可重放性 | 高（可游标稳定翻页） | 低（结果漂移） |
| 核心矛盾 | **关系扩散**（一帖到多读者） | 候选召回 + 排序算力 |
| 架构主题 | fanout（推/拉/混合） | 近线候选 + 向量检索 + 精排 |

> 本报告聚焦 **Timeline / 关注流**；推荐流架构见《推荐系统深度调研报告-抖音版》。

关注流的本质是一个集合操作：
```
timeline(U) = sort_desc(merge(posts by following(U)), key = created_at)
```

---

## 2. 三大模式：Push / Pull / Hybrid

### 2.1 推模式（Push / Fanout-on-write / 写扩散）
发帖时把帖子 ID 写入**每个粉丝的 inbox**，读时间线 = 读自己的 inbox。
- ✅ 读 O(1)、天然按时间排序、关注后补写历史即可
- ❌ 写放大 O(粉丝数)、大V写爆炸、粉丝表存储成本、删帖遍历
- 适用：粉丝规模小、读多写少（早期 Twitter）

### 2.2 拉模式（Pull / Fanout-on-read / 读扩散）—— MiniForum 现状
发帖只写一份，读时现场拉取关注对象时间线合并。
- ✅ 写 O(1)、存储省、取关/删帖天然一致
- ❌ 读放大 O(关注数)（MiniForum 是更极端的 O(全站帖子)）、归并开销、热点作者读压力
- 适用：关注数少的场景（RSS、Instagram 算法流）

### 2.3 推拉结合（Hybrid）—— 微博/Twitter 做法
**按粉丝量级分人**：普通用户（粉丝少）走推，大V（粉丝多）走拉；**按活跃度分流**：只推给活跃粉丝，非活跃上线时回溯补拉。

```
发帖用户 A
  ├─ 大V(粉丝>阈值) ──▶ 只写 A 自己的 outbox（Redis 限量），读者刷 feed 时拉取合并
  └─ 普通用户 ──▶ Fanout Worker（过滤活跃粉丝）→ 批量写每个活跃粉丝的 inbox

读取端 B（关注大V A + 普通用户 C）：
  读自己 inbox[B]（含 C 的帖） + 拉取大V A 的 outbox 新帖 → 合并排序 → 返回
```

- 阈值经验：粉丝 10万~100万 级【推断】
- Instagram 反面案例：2016 算法排序后，因推模式每次改排序要重推，**全面转拉模式 + 强缓存**——说明**模式选择与排序策略强耦合**（纯时间线适合推，算法排序适合拉）

### 2.4 三模式对比表

| 维度 | 推模式 | 拉模式 | 推拉结合 |
|---|---|---|---|
| 写路径 | 慢（1帖→N次写） | 快（1帖→1次写） | 普通慢、大V快 |
| 读路径 | 快（O(1)读inbox） | 慢（O(M)合并） | 快（inbox+少数大V拉） |
| 读放大 | O(1) | O(关注数) | O(关注的大V数) |
| 写放大 | O(粉丝数) | O(1) | O(活跃普通粉丝) |
| 大V问题 | 严重 | 中 | 已解决 |
| 取关/删帖一致性 | 遍历inbox | 天然一致 | 普通遍历、大V天然 |
| 存储成本 | 高 | 低 | 中 |

### 2.5 大V为何走拉：写放大 vs 读放大的成本模型

**决定模式选择的关键不是"粉丝多"本身，而是两种模式的成本结构对比**：

```
推模式成本 = 发帖数 × 平均粉丝数      （每发一帖，写 N 个 inbox）
拉模式成本 = 阅读次数 × 关注数        （每读一次，合并 N 个 outbox）
```

- **大V粉丝多** → 推模式里"×粉丝数"这一项爆炸 → 写放大是致命伤（一帖百万/千万次写）
- **但读者关注的大V数量少**（普通人真正关注的大V通常个位数）→ 拉模式里"×关注数"这一项很小 → 读放大可接受

#### 数量级对比【推断】

| 账号类型 | 推模式成本 | 拉模式成本 |
|---|---|---|
| 大V（100万粉丝，日发 3 帖） | 每天 **300万次** inbox 写，写入系统被压垮 | 读者刷 feed 时各拉 1 个 outbox key，可缓存，几乎免费 |
| 普通用户（100粉丝，日发 3 帖） | 每帖写 100 条 inbox，微不足道 ✅ | 每个读者要合并 100 个作者 outbox，读放大反而大 ❌ |

#### 拉模式对大V可行的关键前提：outbox 是可缓存的共享热点

- **outbox（拉）**：所有粉丝读的是**同一个**大V的 outbox → 共享热点 key → 多级缓存（L1 本地 + Redis）命中率极高，读放大被缓存摊薄
- **inbox（推）**：每个粉丝**私有**自己的 inbox → 无法共享缓存

所以"大V走拉"的本质是：**把大V发帖的百万次写，转嫁成每个读者读 outbox 的几次读 + 共享缓存**——用共享读去换掉私有写。

#### 结论

> 分流标准 = **写放大与读放大的成本临界**：当"发帖数 × 粉丝数"（推的写放大）远大于"读者数 × 关注的大V数"（拉的读放大）时，走拉更划算。实践中以粉丝量级为代理指标（如 10万~100万【推断】），大V走拉 + outbox 多级缓存，普通用户走推。见 §11 阶段 5。

---

## 3. Fanout 的工程实现

### 3.1 异步化
- 发帖请求**立即返回**，fanout 绝不能落在请求路径上。
- 链路：API → 帖子服务(写DB) → MQ事件 → Fanout Consumer（异步）→ 写粉丝 inbox。
- 粉丝量大时**批量化**：粉丝列表分片，每 worker 处理一批，MQ 削峰。

### 3.2 事务性 Outbox（保证不丢不重）
发帖与发 fanout 事件跨系统（DB 与 MQ），用 outbox 模式保证必达：
```
同一本地事务：INSERT posts + INSERT outbox(status=PENDING)
  → Outbox Relayer（轮询或订阅 binlog/CDC，如 Debezium/Canal）
  → 发 Kafka 事件 → Fanout Consumer → 成功更新 status=DONE
```
- 幂等：Fanout Consumer 以 `(follower_id, post_id)` 为幂等键，或利用 Redis ZADD 天然幂等。

### 3.3 粉丝列表（Follower Graph）
- 独立成**图服务**，支撑"A 的粉丝列表"与"B 的关注列表"。
- 大V粉丝列表分片（shard by follower_id），fanout 并发查各分片。

---

## 4. Feed 存储：timeline 只存 ID，内容分离

### 4.1 核心原则
```
Redis inbox[B]（只存帖子ID + score）:
  [ (post_901, ts) , (post_800, ts), ... ]   ← 最新在前
帖子详情另存：Redis 详情缓存 / MySQL
读时间线 → 拿到 ID 列表 → 批量回源（pipeline/multi-get）帖子详情
```

### 4.2 数据结构选型

| 存储 | 结构 | 优缺点 | 适用 |
|---|---|---|---|
| Redis List | `LPUSH` + `LTRIM` | 内存省、写O(1)、LRANGE分页；删除/去重麻烦 | 轻量纯时间线 |
| **Redis ZSet** | `ZADD id ts` + `ZREVRANGEBYSCORE` | **天然按时间排序、去重、删除O(log n)、游标友好** | **生产级主流** |
| 内存 | `Map<uid, ArrayDeque<Long>>` | 最快、单机 | 原型/单机 Demo |
| HBase/Cassandra 宽表 | rowkey=user_id | 海量用户 | 超大规模 |

- **封顶**：每用户 inbox 只保留最近 500~1000 条【推断】，超限 ZREMRANGEBYRANK 淘汰。
- **持久性权衡**：Redis 可当热缓存（可重建），帖子表和关注关系是唯一事实源。

---

## 5. 分页与游标

- **为什么不用 offset**：性能（扫描丢弃前 N 行）+ 一致性（feed 持续追加，新帖顶掉位置 → 翻页重复/丢帖）。
- **since_id / max_id**（Twitter 约定）：
  - `since_id`：返回 `id > since_id` 的帖（增量刷新）
  - `max_id`：返回 `id <= max_id` 的帖（向下翻历史）
- **ZSet 配合**：`ZREVRANGEBYSCORE inbox (max_id) (-inf) LIMIT 0 20`；`(inf) (since_id)`。
- 现代实现用**不透明 base64 游标**（编码 `(last_id, ts)`）规避边界问题（GraphQL Relay 规范）。

---

## 6. 多级缓存

```
客户端(304/ETag) → CDN/网关 → L1本地(Caffeine，热点用户时间线) → L2 Redis(inbox+详情) → MySQL
```

- **热点用户时间线**：个性化关注流不能共享响应缓存，但组成它的"大V outbox"是共享热点 key——用多级缓存扛。
- **一致性**：inbox 是 append-only，允许毫秒~秒级最终一致；删帖用主动失效 + 读取兜底校验。

---

## 7. 排序：纯时间线 vs 智能排序

- **纯时间**：`ORDER BY created_at DESC`，可缓存、可游标稳定分页、实现简单。
- **智能排序（EdgeRank 式）**：`score = 亲密度 × 内容权重 × 时间衰减`——第一档复杂度，无需 ML。
- **插入卡片**（微博/Twitter 做法）：底层时间线保持时间序，**渲染层按固定槽位（第2/5/8位）插推荐卡/广告/社交卡**（"你关注的人关注了 X"）——不污染 timeline 缓存。
- Instagram 因算法排序转拉模式，说明排序与模式强耦合。

---

## 8. 可扩展性问题 & Kafka 与 feed 的关系

### 8.1 核心矛盾
| 问题 | 解法 |
|---|---|
| 写放大 | 推拉结合、大V分流、活跃粉丝过滤 |
| 读放大 | inbox、多级缓存、批量回源 |
| 大V问题 | 大V走拉 + outbox 多级缓存 |
| 粉丝表膨胀 | 图服务 + 分片 + 缓存 |
| 热 key | L1 本地缓存 |
| 跨页漂移 | 游标分页 |

### 8.2 Kafka vs feed（常见误区）
- **误区**：用 Kafka 当 feed 存储（每用户一个 topic）。❌ 错——Kafka 是"队列"（消费即移走游标），feed 需要"可随机定位的集合"。
- **正确分工**：
```
Kafka = 事件分发总线（把"新帖事件"扇出给 Fanout Worker，跨系统解耦/削峰）
Redis/inbox = feed 事实存储（保存每个用户最终的时间线集合）
```
- 类比：Kafka 像邮局（负责送信），inbox 像收件箱（负责按人存放）。

---

## 9. 大厂案例速览

| 平台 | 关注流做法 |
|---|---|
| **微博** | 早期纯拉 → 推模式 → **推拉结合**：普通用户推、大V拉（outbox 多级缓存）；热搜是独立系统（全站实时聚合） |
| **Twitter** | 早期纯推（Redis List LPUSH+LTRIM）→ 大V问题 → 混合（celebrity 跳过扇出，读者拉取合并）→ 后续 Tier 分层 + 算法时间线 |
| **Facebook** | News Feed 从诞生即排序（EdgeRank：亲密度×权重×衰减）；个体走推、大 Page 走拉 |
| **抖音** | 关注流**刻意保持简单**（时间线/轻排序），重心全在推荐流——"关注流简单是产品选择而非技术落后" |
| **知乎** | 关注流是"动态流"（聚合关注的人的所有动作）；关注话题流 = 话题当"伪用户"，两路合并去重 |

---

## 10. MiniForum 现状 vs 生产级

| 维度 | MiniForum 现在 | 生产级 |
|---|---|---|
| 关注流 | `FollowService.getFollowFeed` **全表扫描**所有帖子→按关注人过滤→内存排序（O(全站帖子)） | 读自己 inbox（O(1)） |
| 推荐流 FollowRecall | 同样全表扫描 | 关注是召回通道之一（**方向正确 ✅**） |
| 分页 | offset 分页（新帖导致重复/丢帖） | 游标分页 |
| 存储 | 无 inbox；帖子全量内存 | Redis ZSet inbox（ID 序列 + 封顶） |
| 缓存 | 无 | 多级缓存 + 热点 key |
| 大V | 无概念 | 推拉结合分流 |
| **基建** | ✅ 已备好：`PostCreatedNotifier`（发帖事件挂载点）+ `BehaviorEventQueue`（事件总线）+ Redis 关注关系 + Kafka prod 适配 | 推模式天然挂载点 |

**结论**：关注流是全表扫描的最原始拉模式，但**推模式的基建已埋好**——`PostCreatedNotifier` 是发帖后扇出的挂载点，`KafkaPostCreatedConsumer` 已在 prod 消费发帖事件，Redis 关注关系已就绪。缺的只是"inbox 结构 + 扇出逻辑 + 读 inbox"。

---

## 11. 分阶段演进建议（每阶段独立可上线）

| 阶段 | 动作 | 收益 | 复杂度 |
|---|---|---|---|
| **0 游标分页** | follow/recommend 从 offset 改游标（before/since） | 消除翻页重复/丢帖 | 低 |
| **1 按作者分桶** | PostRepository 加 `authorId → SortedSet<Post>` 索引；关注流改"每关注作者取最近 K 条合并" | 读从 O(全站) 降到 O(关注数×K) | 低 |
| **2 内存 inbox（推）** | `FollowFeedStore` 接口 + 内存实现：发帖时经 `PostCreatedNotifier` 扇出 postId 到每个粉丝的 inbox（封顶500） | 读 O(1)，读放大归零 | 中 |
| **3 Redis ZSet + Kafka 扇出** | `RedisFollowFeedStore`（`feed:{uid}` ZSet，score=postId，pipeline 扇出）；prod 由 Kafka 消费者扇出（异步） | 多实例、生产级 | 中高 |
| **4 智能排序+推荐卡** | 关注流复用现有 `RankService` 打分（新鲜度×互动×同作者打散）；第 2/5/8 位插推荐卡 | 关注流变"信息流" | 中 |
| **5 大V分流** | `FollowFeedStore` 预留 `isFanoutSkipped(userId)`（粉丝超阈值走拉） | 解决写放大 | 预留开关 |

> 阶段 5 的分流依据见 §2.5（大V为何走拉的成本模型）：大V粉丝多 → 推的写放大 O(粉丝数) 爆炸，而读者关注的大V数少 → 拉的读放大 O(关注的大V数) 很小，且 outbox 是可缓存的共享热点，故大V走拉 + outbox 多级缓存、普通用户走推。

### 各复杂度的触发门槛（规模经验值【推断】）
- **大V分流**：单用户粉丝 > 几万~几十万才需要；几万用户的项目不会出现
- **异步扇出（MQ）**：单帖要写 > 几千个 inbox 才需要；小项目同步扇出毫秒级
- **多级缓存**：单 Key 读 QPS 上千才需要
- **分库分表**：千万级帖子/百万级 DAU 才考虑

### 不建议做的（当前规模）
- ❌ 不上 MQ 做发帖异步扇出（内存同步扇出毫秒级；prod 已通过 Kafka 消费者获得）
- ❌ 不分库分表、多级缓存集群、专门大V outbox
- ❌ 关注流直接上 ML 排序（先规则排序）

### 关键决策建议（Java 侧）
- Redis 客户端：Lettuce（异步）或 Redisson；本地缓存 Caffeine；MQ 优先 Kafka
- Outbox Relayer：Spring 定时轮询（简单）或 Debezium/Canal（生产级）
- ID 生成：Snowflake 类，全局单调带时间戳，天然可做游标
- Redis 结构：直接用 **ZSet**（member=postId，score=单调ID），后续排序/去重/删除留余地
  - **本项目 Post.nextId() 是单调 AtomicLong，直接当 score 最省事**

---

## 12. 参考来源

- Raffi Krikorian《Timelines at Scale》（推拉结合经典）：https://www.infoq.com/presentations/Twitter-Timeline-Scalability/
- Twitter《Redis at Twitter》：https://blog.twitter.com/engineering/en_us/a/2016/redis-at-twitter
- Twitter《Timelines at scale》：https://blog.twitter.com/engineering/en_us/a/2018/timelines-at-scale.html
- Facebook《Serving Facebook Multifeed》：https://www.infoq.com/presentations/Serving-Facebook-Multifeed/
- Instagram 工程博客（算法排序转拉模式）：https://instagram-engineering.com/
- Transactional Outbox 模式（microservices.io）：https://microservices.io/patterns/data/transactional-outbox.html
- Redis Sorted Sets：https://redis.io/docs/data-types/sorted-sets/
- GraphQL Relay Cursor Connections：https://relay.dev/graphql/connections.htm
- System Design Primer（Feed 系统设计）：https://github.com/donnemartin/system-design-primer
