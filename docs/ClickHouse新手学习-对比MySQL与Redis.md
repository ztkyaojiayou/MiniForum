# ClickHouse 新手学习（对比 MySQL / Redis，你会的两个数据库）

> 创建：2026-08-28
> 读者：会 MySQL（OLTP 行式）、Redis（内存 KV）的新手。本文全部用"和你会的东西对比"来讲 ClickHouse。
> 核心一句话：**MySQL 是行式（适合事务、点查），Redis 是内存 KV（适合缓存），ClickHouse 是列式 OLAP（适合海量数据聚合分析）**——三个不是替代关系，是分工。
> 配套：本项目用它存**行为日志**（`ClickHouseBehaviorStore`，Kafka Engine 摄入 + 离线画像/ItemCF/评估读取）。

---

## 0. 一句话全貌

```
ClickHouse = 俄罗斯 Yandex 开源的【列式】OLAP 数据库
  定位：海量数据（亿~万亿行）的【聚合分析】，不是业务主存储
  哲学：写入只管 append（不更新不删除），查询只读需要的"列" → 极快

你和它的关系：MySQL 是"记流水账的账本"（每一行是一个完整的交易）
             ClickHouse 是"按列堆的海量档案"（同一列的所有值堆在一起，分析时只翻需要的列）
```

**为什么需要它（业务场景）**：一个 App 每天产生千万级行为日志（谁看了/点了/赞了什么），要按"用户/帖子/时间"聚合分析出画像和热度。这些数据：
- **只增不改**（append-only，日志不会被 UPDATE）；
- **量大**（比业务表多 1~2 个数量级）；
- **查询都是聚合**（`GROUP BY 用户，SUM(行为)`），很少"按 id 查单行"。

MySQL 能存但**分析很慢**（见 §2），Redis 能快但**存不下**（内存太贵）。这就是 ClickHouse 的位置。

---

## 1. 最核心的认知：行式 vs 列式

**MySQL 是行式存储**：数据一行挨着一行存（`id=1 的那行所有列紧挨着`）。

```
MySQL 内存/磁盘布局（行式）：
[ id=1, userId=1, type=LIKE, ts=10:00 ] [ id=2, userId=2, type=VIEW, ts=10:01 ] [ id=3, userId=1, type=LIKE, ts=10:02 ] ...
```

**ClickHouse 是列式存储**：同一列的**所有值**紧挨着存（`所有 userId 在一起`，`所有 type 在一起`）。

```
ClickHouse 布局（列式）：
userId: [1, 2, 1, ...]  ← 一整列连续存放
type:   [LIKE, VIEW, LIKE, ...]
ts:     [10:00, 10:01, 10:02, ...]
```

### 为什么列式分析快（用实际例子）

**场景**：统计"每个用户的点赞数"——`SELECT userId, count() FROM behavior_log WHERE type='LIKE' GROUP BY userId`。

**MySQL（行式）要怎么做**：
```
全表扫所有行 → 对每行【把整行（id/userId/postId/type/ts/durationSec/scene/expId 全字段）都读进内存】
→ 过滤 type='LIKE' → 按 userId 分组计数
```
痛点：**只用了 2 个字段，却要把每行的 8 个字段全读出来**。数据量 1 亿行 × 8 字段 = 大量 IO 浪费。

**ClickHouse（列式）要怎么做**：
```
只读 type 和 userId 这两列（其他列物理上不碰）
→ 在内存里过滤 + 分组计数
```
收益：**只读需要的列**（列式存储的天然优势），IO 减少 4~8 倍；再加上**压缩**（同一列值相似，压缩比 3~10 倍）+ **向量化执行**（一次处理一批值），**聚合查询比 MySQL 快 10~100 倍**。

> 一句话：**行式擅长"要一整行"（点查、事务），列式擅长"只要某几列做统计"（聚合分析）**。

---

## 2. 三库对比表（你会的 vs 你要学的）

| 维度 | **MySQL**（你会） | **Redis**（你会） | **ClickHouse**（要学） |
|---|---|---|---|
| 定位 | OLTP 业务主存储 | 内存缓存/热数据 | **OLAP 分析仓库** |
| 存储结构 | 行式 | KV（内存） | **列式** |
| 擅长 | 增删改查事务（ACID）、点查 | 高频读写、秒级缓存 | **海量数据聚合分析**（亿行 GROUP BY） |
| 不擅长 | 海量聚合（全表扫慢） | 存不下大容量（内存贵） | 事务、点查、频繁更新 |
| 写入 | 频繁 UPDATE/DELETE | 快（内存） | **append-only 快，更新/删除贵** |
| 并发 | 行锁/事务 | 极高（单线程+原子） | 高吞吐写入，查询一般 |
| 一致性 | 强一致（事务） | 取决于配置 | **弱一致**（异步合并，最终一致） |
| 语言 | 标准 SQL | 自己的命令（GET/SET...） | **类 SQL**（你会 SQL 就能上手） |
| 索引 | B+树（点查快） | O(1) 哈希 | **稀疏索引**（按排序键） |
| 典型场景 | 用户/帖子/订单 | 会话/缓存/排行榜 | **行为日志/指标/明细分析** |
| 数据量 | 千万级 OK | 内存上限 | **亿~万亿级** |

**三个的协作关系（不是竞争）**：
```
业务写入 → MySQL（主存储，强一致）
热点读   → Redis（缓存，扛 QPS）
海量分析 → 从 MySQL/日志 → ClickHouse（离线聚合，喂画像/报表/推荐）
```

---

## 3. ClickHouse 的核心概念（新手只学这几个就够）

### 3.1 MergeTree 引擎（ClickHouse 的地基）

ClickHouse 几乎所有表都是 MergeTree 家族的。它决定了数据怎么存、怎么查、怎么合并：

```sql
CREATE TABLE behavior_log (
    id UInt64,
    userId UInt64,
    type String,
    timestamp DateTime,
    ...
) ENGINE = MergeTree
  PARTITION BY toDate(timestamp)   -- 分区：按天分文件
  ORDER BY (userId, timestamp)     -- 排序键：数据物理上按 userId+时间有序
```

| 概念 | 是什么 | 类比（MySQL） |
|---|---|---|
| **PARTITION BY** | 数据按时间/字段**分文件**，查询只扫相关分区 | 类似"按月分表"，但自动 |
| **ORDER BY**（排序键） | 数据物理排序的依据，**决定查询快慢** | 类似"联合索引的顺序"，但更强（数据真有序） |
| **稀疏索引** | 每个数据块记一个"最小/最大排序键值"，查询**跳过无关块** | 类似索引，但不是每行一个，是每块一个（内存占用极小） |
| **主键（非必须）** | 可另设 PRIMARY KEY，用于去重（ReplacingMergeTree） | MySQL 主键唯一约束 |

> **新手理解**：MergeTree = "按分区存 + 按排序键有序 + 稀疏索引跳过块"。查询带排序键条件（如 `WHERE userId=1` 且排序键是 `(userId, timestamp)`）→ 直接跳到对应块，飞快；查询不带排序键 → 全表扫。

### 3.2 列式 + 压缩 + 向量化（为什么快的三件套）

1. **列式**：只读需要的列（§1 已讲）；
2. **压缩**：同一列值相似度高，压缩比 3~10 倍（数字列更夸张）——**读的字节少，IO 快**；
3. **向量化执行**：一次处理一整块数据（如 1000 个 userId 一起算），不是一行行循环——**CPU 利用率高**。

三者叠加 = 聚合查询比 MySQL 快 1~2 个数量级。

### 3.3 类 SQL + OLAP 函数

ClickHouse 语法**95% 和你会的 SQL 一样**，但加了很多分析函数：

```sql
-- 和你会的 SQL 一模一样
SELECT userId, count() AS cnt FROM behavior_log
WHERE timestamp > yesterday() GROUP BY userId ORDER BY cnt DESC LIMIT 10;

-- ClickHouse 特有的聚合（一行搞定"每用户最近 30 天活跃天数"）
SELECT userId, uniqExact(toDate(timestamp)) AS active_days
FROM behavior_log WHERE timestamp > now() - INTERVAL 30 DAY GROUP BY userId;
```

常用点：
- `count()` / `uniq()` / `sum()` / `avg()`（聚合）；
- `toDate()` / `toStartOfHour()`（时间分桶，日志分析高频）；
- `argMax` / `quantile` / `topK`（画像/评估指标）；
- **建表用 UInt64/Float64/String/DateTime**（不是 INT/VARCHAR/DATETIME）。

---

## 4. 新手最容易踩的坑（ClickHouse 的"与众不同"）

| 你以为（按 MySQL 习惯） | 实际上 ClickHouse 是 |
|---|---|
| 可以 UPDATE/DELETE | **可以但很贵**（Mutation 全表重写）；正确姿势：**只 insert，用版本/时间解决** |
| 支持事务（ACID） | **不支持传统事务**（append 无事务概念） |
| 主键唯一、会报错 | **默认允许重复**（普通 MergeTree 不去重）；要去重用 ReplacingMergeTree |
| 强一致，写完立刻读到 | **弱一致**：数据先落内存，后台异步合并到磁盘（合并前查不到最新） |
| 适合做业务主存储 | **不行**：点查（WHERE id=? 拿单行）反而慢，因为没 B+树单行索引 |
| 每个表都要主键索引 | **关键是 ORDER BY 排序键**，不是主键 |

> **一句话避坑**：**ClickHouse 是"写日志、做分析"的，不是"管业务数据"的**。业务主存储还是 MySQL，热点缓存还是 Redis，ClickHouse 只接"只增不改、量大、要聚合"的活。

---

## 5. 本项目怎么用 ClickHouse（紧扣 `ClickHouseBehaviorStore`）

### 5.1 行为日志 = 教科书级 OLAP 场景

本项目的行为日志（`BehaviorLog`：谁在什么时间对哪个帖子做了什么）：
- **只增不改**（点赞/浏览是一次性事件，从不变更）；
- **量大**（比帖子/用户表多一个数量级）；
- **查询都是聚合**（按用户聚合成画像、按帖子聚合热度、按时间切分评估）。

→ 完美契合 ClickHouse 的"列式 + append-only + 聚合快"。

### 5.2 数据流（Kafka Engine，一行导入代码都不用写）

```sql
-- ① MergeTree：最终查询表（按天分区，按 userId+时间有序）
CREATE TABLE behavior_log (...) ENGINE = MergeTree
  PARTITION BY toDate(timestamp) ORDER BY (userId, timestamp);

-- ② Kafka Engine：直接从 Kafka topic 消费（JSONEachRow），毫秒级摄入
CREATE TABLE behavior_log_kafka (...) ENGINE = Kafka('localhost:9092', 'behavior-log', 'mini-forum-clickhouse', 'JSONEachRow');

-- ③ 物化视图：Kafka 表的数据自动灌进 MergeTree
CREATE MATERIALIZED VIEW behavior_log_mv TO behavior_log AS SELECT * FROM behavior_log_kafka;
```

```
生产链路：
Kafka("behavior-log") ──→ ClickHouse Kafka Engine 表 ──→ 物化视图 ──→ MergeTree behavior_log
  行为打点（写）               自动消费，无需写消费代码                              ↓ 读
                                                               ClickHouseBehaviorStore（离线画像/ItemCF/评估）
```

**与 MySQL/Redis 的分工（本项目）**：
| 数据 | 存哪 | 为什么 |
|---|---|---|
| 用户/帖子/评论/点赞等**业务数据** | MySQL（行级表） | 需要事务/点查/强一致 |
| 关注图/feed/画像/特征/模型**热点** | Redis | 读多写少、低延迟 |
| **行为日志**（全量、只增、要聚合） | ClickHouse | 量大 + 离线聚合分析 |

### 5.3 为什么在线画像**不**切 ClickHouse

代码注释写得很清楚：`ClickHouseBehaviorStore` 只给**离线**画像/ItemCF/评估读；**在线**画像仍用内存/Redis。原因：
- **在线请求是毫秒级 SLA**，ClickHouse 的 JDBC 查询延迟（网络 + 弱一致合并）进不了请求路径；
- 离线任务（小时/天级）不在乎几十毫秒延迟，正好用 ClickHouse 的全量明细。

> **这是"轻在线重离线"架构的落地点**：在线用 Redis（快），离线用 ClickHouse（全）。

---

## 6. 什么时候选哪个（决策表）

| 你的需求 | 选 |
|---|---|
| 业务增删改查、事务、按 id 点查 | **MySQL** |
| 高频读、热点缓存、排行榜、会话 | **Redis** |
| 亿行日志/明细，`GROUP BY` 聚合分析，离线报表/画像 | **ClickHouse** |
| 亿行数据但要**点查单行**（如订单详情） | MySQL（+ 分库分表/其他，**不是** ClickHouse） |
| 亿行日志但**要实时**（流计算） | Kafka + Flink（ClickHouse 是结果落地，不是流引擎） |

---

## 7. 没做过 vs 做过的人，对 ClickHouse 的认知

| 没做过的人会想 | 做过的人会想 |
|---|---|
| "又一个数据库，功能类似 MySQL" | "**列式 OLAP，和 MySQL 是两类东西**：它只干'海量聚合分析'这一件事" |
| "ClickHouse 快，那业务表也放它" | "业务点查/事务/强一致还是 MySQL；CH 点查反而慢" |
| "有主键就不会重复" | "**默认允许重复**，要唯一语义用 ReplacingMergeTree/表设计去兜" |
| "写进去立刻能查到" | "**异步合并，弱一致**——刚写的数据可能查不到（日志场景可接受）" |
| "可以随便 UPDATE" | "**Mutation 全表重写很贵**；append-only + 时间版本才是正确姿势" |
| "会用 SQL 就一样" | "语法像 SQL，但**思维是'分析'不是'事务'**：排序键、分区、列裁剪、压缩比才是命门" |

---

## 8. 进阶理解：三个新手最容易卡住的问题（Q&A）

### Q1：读 `type` 和 `userId` 两列，拼出来的不也是"一行"吗？和 MySQL 的 `SELECT type,userId` 区别在哪？

**答案：查询结果都是"行"，但两个数据库为了拼出这一行，物理上读了完全不同的字节——存储布局 ≠ 查询结果。**

- **MySQL（行式）**：一行 8 个字段物理上挨在一起。你 `SELECT type,userId`，InnoDB 从主键页读数据时**还是把整行 8 个字段全读进内存**，然后才"只挑两列给你"——磁盘 IO 和 `SELECT *` 几乎一样，**读的是整行，只是返回时裁剪**。
- **ClickHouse（列式）**：`type` 所有值存在一个连续块、`userId` 另一个块。`SELECT type,userId` **只读这两个块**，其他 6 个列块物理上碰都不碰；"行"是取两列数组同一下标的值**在内存里拼出来**的。

```
MySQL（物理单元 = 整行）：[ id, userId, postId, type, ts, duration, scene, expId ] ← 8 字段全读，只用 2 个
ClickHouse（物理单元 = 列块）：type:[...] userId:[...]  其他 6 列块 0 IO，内存里按位置拼行
```

**档案袋类比**：行式 = 一摞人事档案袋，每个袋子装一个人全部信息，统计"姓名+电话"也要把每个袋子整个翻开；列式 = 三本簿子（姓名/电话/住址），统计"姓名+电话"只抽这两本，住址簿碰都不碰。

**数字对比**（1 亿行行为日志，每行宽 ~200B）：

| | 读 2 列时物理读的字节 |
|---|---|
| MySQL | 1 亿 × 200B = **20 GB**（2 列 + 6 列全读） |
| ClickHouse | 1 亿 × ~10B × 压缩(5x) ≈ **200 MB** |

**补充**：MySQL 想"只读两列"得靠**二级覆盖索引**（人为建一个只含这两列的索引），不是存储结构天然带来的；ClickHouse 是**存储结构本身就按列组织**，天然只读需要的列。

### Q2：ClickHouse 怎么实现"按位置拼行"的位置映射和管理？

**答案：ClickHouse 没有显式的行 id——"行"就是列数组里的位置（下标），位置映射是隐式的，由 MergeTree 引擎保证。**

| | 行怎么被唯一标识 |
|---|---|
| MySQL | **显式主键 id**，B+树叶子存整行，`WHERE id=5` 沿树直达；行是自包含的 |
| ClickHouse | **隐式位置**：行 = 每列数组的第 N 个元素，没有 row id、没有指针，**位置本身就是身份** |

**MergeTree 的三层物理管理**：

```
MergeTree 表
└── 分区（PARTITION BY toDate(timestamp)）→ 每天一个目录
    └── Part（不可变数据部分）→ 一批行
        ├── type.bin / userId.bin / timestamp.bin   ← 每列一个连续数组（压缩存储）
        └── primary.idx                             ← 稀疏索引：每个 granule（默认 8192 行）开头行的排序键值
```

四个机制保证对齐：
1. **Granule（粒度块）**：一个 Part 内按 `index_granularity`（默认 8192 行）切块，每块内所有列元素数严格相同——"行"能对齐的根基；
2. **写入时保证对齐**：插入 N 行，每个列缓冲各追加 N 个值，所以任何时刻各列数组长度 == 总行数，对齐是**写的时候保证的**，不是查出来的；
3. **Part 不可变 + 后台合并**：小 Part 后台合并成大 Part，合并时按排序键同步重排所有列，对齐永不破——这也是"只增不改"的根源（任何修改 = 重写列）；
4. **稀疏索引 + 标记文件**：`primary.idx` 记每个 granule 开头行的排序键值，`.mrk` 记每个 granule 在 .bin 里的字节偏移——查询"稀疏索引定 granule → 标记定字节偏移 → 块内按下标拼行"。

**一次查询的位置映射**：
```
SELECT type,userId FROM behavior_log WHERE userId=1 AND timestamp>=昨天
① 稀疏索引（排序键 userId）→ 二分找到含 userId=1 的 granule
② .mrk 标记文件 → 定位该 granule 在 type.bin/userId.bin 的字节偏移
③ 只读两个列文件在该 granule 的段 → 解压成数组
④ 内存按下标对齐拼行：type[0]&userId[0]、type[1]&userId[1]...
```

**这个设计的代价**（前面"三个坑"的根源）：① 无法高效点查（id 只是普通列，没有 id→地址映射）；② 更新/删除是灾难（改一行 = 相关列重写）；③ 弱一致（新数据先进插入区、异步合并落盘）。OLAP 查询永远是"整块扫 + 聚合"，从不"按 id 拿一行"，所以用"位置当身份"换极低开销 + 极高压缩，正是列式分析快的底层原因。

### Q3：那 ClickHouse 到底适合什么查询场景？

**答案：适合"大范围扫描 + 聚合分析"；不适合"点查单行 / 频繁更新"——边界可以从三个设计约束直接推导，不用背。**

| 设计约束 | 推导出"适合" | 推导出"不适合" |
|---|---|---|
| 列式（只读需要的列） | 查**几列**聚合 → 极快 | 查**整行**（SELECT * 大表）→ 没优势 |
| 位置即身份（无 id→地址映射） | **大范围扫**（块内顺序读飞快） | **点查单行**（WHERE id=5）→ 慢 |
| append-only（更新=重写列） | **只增不改**的数据 | **频繁 UPDATE/DELETE** → 灾难 |
| 排序键 + 稀疏索引 | **按排序键范围过滤**（时间/用户）→ 跳块 | 不按排序键过滤 → 全表扫 |
| 弱一致（异步合并） | **离线/近线分析**（不在乎几十 ms 延迟） | **实时读写强一致** → 读到旧数据 |

**适合的具体场景**（含本项目例子）：
1. **海量聚合**：`SELECT userId, count() FROM behavior_log WHERE type='LIKE' GROUP BY userId`（本项目画像聚合）；
2. **时间序列/日志**：按天分区、append-only、按时间过滤（本项目行为日志就是标准场景）；
3. **宽表只取几列**：1 亿行 × 20 列，查 3 列聚合，其他 17 列物理不碰；
4. **漏斗/留存/画像/离线评估**：`uniqExact(toDate(timestamp))` 等（本项目 ItemCF 构建、离线评估都从 ClickHouse 读全量）；
5. **高吞吐写入**：Kafka Engine 直接消费 topic 毫秒级入账（本项目 `behavior_log_kafka`）。

**判断 checklist**（一个查询来了，四问）：
```
① 扫很多行做统计 还是 拿一两行？   扫很多行 → 往下；拿一两行 → MySQL
② 数据只增不改吗？                 日志/行为/指标 → 适合；频繁改 → MySQL
③ 按时间/排序键范围过滤吗？         按天/按用户段 → 分区裁剪极快；乱查 → 全表扫
④ 在乎"写完立刻读到"吗？           离线/近线 → 适合；实时强一致 → MySQL/Redis
四个都"是" → 放 ClickHouse；任一"否" → 考虑 MySQL/Redis
```

**一句话收尾**：ClickHouse 是**"只读的分析引擎"**——把海量"只增不改"的数据灌进去，跑"按时间/用户切片的聚合查询"，极快；一旦要"按 id 拿一行、改一行、实时读"，就露怯。这正是本项目"**在线用 MySQL/Redis（点查+缓存），离线用 ClickHouse（全量聚合）**"分工的根本原因。

### Q4：范围查询的过程是什么样的？为什么快？

以本项目表（`PARTITION BY toDate(timestamp)` + `ORDER BY (userId, timestamp)`）为例：

```sql
SELECT userId, count() FROM behavior_log
WHERE timestamp >= '2026-08-01' AND userId BETWEEN 100 AND 200 GROUP BY userId;
```

**执行过程 = 四层"砍字节"**：

```
① 分区裁剪（partition pruning）
   PARTITION BY toDate(timestamp) → 只打开 8 月分区目录，其他月份物理不碰
   ↓
② 稀疏索引砍 granule（sort key = (userId,timestamp)）
   primary.idx 存每个 granule（8192 行）开头一行的排序键值
   → 二分找到 userId∈[100,200] 覆盖的 granule 区间 → 只读这几块
   ↓
③ 列裁剪
   只读 userId / timestamp / type 三个 .bin 列文件，其他列不碰
   ↓
④ 逐块解压 + 向量化
   只对②选中的 granule 解压 → 一次处理一整块数据过滤 + 分组
```

**为什么快**：每层都在"成倍砍掉要处理的字节/工作集"——①砍不相关时间、②砍不相关行块、③砍不相关列、④用更少的压缩字节喂向量化 CPU，**四层相乘**，1 亿行往往最后只处理几千行的字节。不是"单点快"，是"结构上把无关数据层层挡在门外"。

**关键对比**：MySQL 范围查询靠 B+树**定位**行再回表读整行；ClickHouse 靠稀疏索引**跳过整块**再只读需要的列段。一个是"精准定位但读整行"，一个是"粗定位跳块 + 只读列"。

### Q5：它那么擅长压缩，查询时怎么办？临时解压缩？

**是的，查询时"现用现解压"，但只解压"幸存的一小块"。**

```
压缩后的磁盘：.bin 里是压缩块
查询时：只把【通过分区+稀疏索引裁剪后幸存的那几个 granule】的【选中的列】
        → 解压到内存 → 过滤/聚合 → 用完即弃
```

- **不是整表解压**：幸存块往往只占全表千分之一；
- **不是全列解压**：没选中的列连碰都不碰；
- **解压很快**：默认 LZ4 是每秒 GB 级，而磁盘 IO（读压缩字节）才是瓶颈——**省 IO 远大于费解压 CPU**，净赚；
- **有缓存**：热 granule 解压后可能留在内存（`uncompressed_cache`），下次复用。

所以"压缩 + 临时解压"是**双重收益**：磁盘少读（压缩体积小）+ 内存少处理（只解需要的块）。

### Q6：压缩之后，位置还对得齐吗？

**对得齐——因为"对齐的单位"是 granule（固定行数），压缩是"每个列、每个块独立"做的，不破坏它。**

```
写入时（对齐由写入保证）：
  granule 3（8192 行）→ type 列 8192 个值 → 压缩成 type.bin 里一块
                       → userId 列 8192 个值 → 压缩成 userId.bin 里一块
  两块的【行数相同】（都是 8192），各自压缩、各自存储

查询时（对齐靠 mark 文件恢复）：
  .mrk 标记文件记录：每个 granule 在每个列的 .bin 里
     ① 压缩块的字节偏移（从哪开始读）
     ② 解压后的行偏移（该 granule 在块内从第几行起）
  → 读 type.bin 第 3 块 → 解压出 8192 个 type 值
  → 读 userId.bin 第 3 块 → 解压出 8192 个 userId 值
  → 按下标 0..8191 对齐拼行   ← 对齐 = "granule 号 + 块内行号"
```

**为什么压缩不破坏对齐**：
1. **压缩单位 = 列×granule**：每列独立压缩自己的 granule，压缩前后**行数不变**（8192 → 仍是 8192 个值），只是字节数变了；
2. **mark 文件记"压缩后的位置"**：压缩把字节挤小了、位置变了，但 mark 文件记录的字节偏移**跟着压缩后的布局走**——引擎永远知道"第 3 个 granule 的 type 数据在 type.bin 的第几个压缩块、解压后第几行开始"；
3. **对齐的语义 = "granule 号 + 块内行号"**：只要每列在同一个 granule 里都有 8192 个值，压缩成什么样都不影响"第 N 个值对齐第 N 个值"。

**一句话**：压缩改的是"**字节在哪**"，不改"**每块有多少行**"；mark 文件负责记住"字节在哪"，行对齐由"块内固定行数"天然保持。

**三连总结**：范围查询快 = **分区 + 稀疏索引 + 列裁剪 + 只解幸存块**，四层相乘砍字节；压缩与查询 = **现用现解压，只解幸存小块**，省 IO 远大于费解压 CPU；压缩与对齐 = **压缩按"列×granule"独立做，mark 文件管位置，块内固定行数管对齐**——压缩只改字节位置，不改行身份。

---

## 附：与其它文档的关系

- `docs/领域模型与实体关系.md`：BehaviorLog（行为日志）是本项目用 ClickHouse 承载的实体；
- `docs/高并发优化落地清单-带优先级.md`：P2-1 行为落 ClickHouse（`a3940cb`）；
- `docs/推荐系统微服务拆分方案.md`：⑧日志 ETL → 数仓（ClickHouse 即"数仓/行为事实源"）；
- `docs/搜广推-概念与架构.md`：搜广推共享底座的数据层（行为日志是搜广推共同的事实底座）。

**代码入口**：`forum-recommend-server/.../prod/clickhouse/ClickHouseBehaviorStore.java`（本项目唯一 ClickHouse 接触点：Kafka Engine 摄入 + MergeTree 读取）。
