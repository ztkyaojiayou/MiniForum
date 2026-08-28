# 单 Pod → 多 Pod 高并发演进：典型问题与主流解法

> 创建：2026-08-27
> 主题：① 单个 Pod（单 Java 进程）在高并发下会遇到哪些典型问题、主流怎么解；② 改造为多 Pod 后又会遇到哪些**新的**典型问题、主流怎么解。
> 配套：承接《C端推荐系统高并发应对调研》的"三层模型"（①能堆机器 / ②堆得起机器 / ③堆完的正确稳定）——本文是给这三层补"具体问题和解法"。
> 贯穿例子：大量使用 **MiniForum 自己的代码**当例子，对照着看最直观。
> 来源标注：**【源】** 附 URL（多为官方文档 / 一线团队复盘 / 开源压测实证）；个别数字为示意说明会标注【示意】。

---

## 0. 结论先行 + 演进全景

**一句话**：

> **单 Pod 的问题靠"榨干这一个进程"（效率层）；多 Pod 的问题靠"把进程内共享外置"（分布式层）。**
> 多 Pod 的问题几乎都源于同一个根因：单实例假定的"进程内共享"（内存、锁、会话、定时器、计数器、事件总线），在多个进程下**不再共享**——所有解法都是把共享点"外置"到分布式基础设施（Redis / MySQL / Kafka / 注册中心 / APM）。

**演进全景**（先有个总览，下面逐条讲）：

| | 单 Pod 高并发 | 改造多 Pod 后（新增问题） |
|---|---|---|
| 核心矛盾 | 一个进程扛不住吞吐 | 多个进程"不共享"，协调变难 |
| 典型问题 | 线程耗尽、连接池耗尽、GC 停顿、锁竞争、缓存三兄弟、每请求算力 | 内存态失效、会话丢失、定时任务重复、单机限流失效、分布式锁/幂等、缓存不一致、热点、重试风暴、不可观测 |
| 主流解法 | 超时/异步/虚拟线程、池调优、GC 调优、无锁、缓存+预聚合、单机限流 | 状态外置、Redis 会话、分布式调度/限流/锁、失效广播、熔断降级、链路追踪、优雅上下线 |
| 对应三层模型 | ② 单机效率 | ① 状态外置（前提）+ ③ 分布式正确与稳定 |

---

# Part 1　单 Pod 高并发：典型问题与主流解法

## 1.1 线程模型耗尽（Tomcat 默认 200 线程）——最典型的"一压就崩"

### 问题现象

Spring Boot 默认内嵌 **Tomcat，`maxThreads` 默认 200**【源：Tomcat 9 文档】。请求处理模型是"一个请求占一个线程"，线程在做阻塞 IO/慢计算时会一直占着不放。

**慢依赖 → 200 线程占满 → 排队 → 拒绝**的链路：

```
200 个请求线程全部阻塞在慢 SQL / 慢外部调用上
  → 新请求进 acceptCount 队列（默认 100）
  → 排满后新连接被拒绝 / 超时
  → 表象是"服务变慢、无报错、日志干净"，极难排查
```

**案例（电商大促复盘）**【源：华为云社区】：峰值 QPS 3000，`200 线程 × 平均 100ms RT` → 理论吞吐上限只有 **2000 QPS**，超出全部排队。只调了 maxThreads、其余参数留默认，大促第 3 天**平均响应从 200ms 飙到 8s**，上游全部超时熔断；且 `maxQueueSize` 默认是 `Integer.MAX_VALUE`（≈21 亿，无限排队），任务队列堆积到 200 万时触发 **Full GC 每次 8 秒**，最终 OOM。

**案例（dev.to 生产复盘）**【源】：Spring Boot 调第三方接口从 200ms 恶化到 25s，线程 dump 显示 **200 个线程全部 WAITING 在外部调用上**，接口 RT 爬到 30s 超时。修复（超时 + 慢依赖隔离到独立线程池）后**峰值活跃线程从 200 降到 <40**。

### 本项目对照

MiniForum 的推荐请求是**同步串行**的：`RecommendService` 里现算画像 → 6 路召回 → 排序 → 重排，全程占着 Tomcat 线程。虽然单实例量级下没问题，但**"一个请求占一个线程做完整漏斗"**和"慢依赖占线程"是同一个结构——QPS 一上来，线程就是第一道瓶颈。

### 主流解法

1. **所有外部调用设超时**（connect + read timeout）——无超时 = 线程可以无限阻塞，是最常见根因；
2. **线程池隔离（Bulkhead）**：慢/不可靠依赖放独立线程池，别占主请求线程；
3. **异步化 / 非阻塞**：`CompletableFuture` 编排（如 6 路召回并行）、WebFlux（Netty event-loop-per-core）；
4. **Java 21 虚拟线程**（JEP 444）：阻塞式写法 + 虚拟线程，等效"响应式规模"——**但本项目是 Java 17，要升级才能用**；且虚拟线程不是免费午餐，`synchronized` 内做阻塞 IO 会发生 **pinning** 钉死平台线程【源：Netflix 虚拟线程案例】；
5. **调参只是缓冲不是根治**：经验公式 `maxThreads ≈ 峰值QPS × 平均RT × 1.5`；`acceptCount`/`maxQueueSize` 别设无限大，应 **fail-fast**。

> 一个反直觉点（真实压测）【源：loom-webflux-benchmarks】：60k 并发压测下，**虚拟线程方案 P99 反而优于 WebFlux**（虚拟线程 ~757ms vs WebFlux ~3.3s，且 WebFlux 堆 ~99%、GC 108s）——"响应式一定更快"是误区，关键看场景。

## 1.2 数据库连接池耗尽（HikariCP 默认 10）

### 问题现象

Spring Boot 默认连接池 **HikariCP，`maximumPoolSize` 默认 10**【源：HikariCP README】。**慢 SQL / 长事务**占满少量连接 → 其余线程阻塞在"等连接" → 请求堆积 → 接口变慢/超时。

**关键认知：池大 ≠ 好。** HikariCP 官方 pool sizing 论证【源：HikariCP Wiki】：

- 场景 10,000 并发用户 / 20,000 TPS，问题不是池要多大而是**要多小**；
- Oracle 实测：仅把连接池从 **2048 降到 96**（其他不改），RT 从 **~100ms 降到 ~2ms，约 50 倍改善**；
- 原因：活跃线程数 > CPU 核数后，加线程只会因上下文切换更慢；
- 参考公式（PostgreSQL）：`connections = (core_count × 2) + disk_count`。

### 本项目对照

`prod` 下 `MySqlPostRepository` 的 `save` 用 `INSERT ... ON DUPLICATE KEY UPDATE`、物品特征 `itemFeature` 每次现算要做多次 `countByPostId`——**每个推荐请求可能串起多条 SQL**。慢 SQL（缺索引）会直接占住连接池，这是多实例/高并发下必踩的坑。

### 主流解法

1. 池大小按公式调（不要拍脑袋放大）；小池 + 线程在池外等待是设计意图；
2. **慢 SQL 治理**（索引、执行计划、批处理）——减少单连接占用时长；
3. 读写分离 / 多池（长事务池 + 短实时查询池分离）；
4. 设 `connectionTimeout`，避免无限等连接。

## 1.3 GC 停顿（G1 STW → ZGC）

### 问题现象

JDK 官方口径【源：JEP 439】：**G1（默认 GC）暂停从毫秒到秒级；ZGC 暂停稳定在微秒级、不超过 1ms，与堆大小无关**。交易撮合场景案例【源】：日常 P99 5ms，市场波动时 G1 的 STW 产生**数十毫秒**停顿，P99.9 飙到 50–100ms+；换 ZGC 后三次 STW 均在 0.1–0.3ms。压测实证【源】：GC 总时间 108s 时 P99 涨到 3.3s，GC 12s 时 P99 仅 757ms——**GC 直接决定尾延迟**。

### 本项目对照

全内存仓库（`ConcurrentHashMap`）意味着对象全部在堆里，每 30s JSON 快照还会造大量对象；量级小暂时没问题，但"高并发 + 大堆"是 P99 毛刺的主要来源。

### 主流解法

1. 先调 G1（`-XX:MaxGCPauseMillis`、新生代大小、字符串去重）；仍不满足再迁 ZGC（JDK 21 起 generational ZGC）；
2. **减少分配**（对象池、避免热路径 new 大对象）——从源头降 STW 频率；
3. ZGC 的代价：读屏障带来约 **5%–15% 吞吐损失**、新风险 **Allocation Stall**，需监控。

## 1.4 单 JVM 锁竞争与伪共享

### 问题现象与解法

- 临界区过大/锁内做 IO → 等待线程排队；热点锁最严重；
- **伪共享**：多线程写同一 CPU 缓存行上的不同变量 → MESI 缓存一致性反复失效。JDK 内部用 `@Contended` 填充规避（`LongAdder`、`ConcurrentHashMap.CounterCell`）；
- `ConcurrentHashMap` 演进（1.7→1.8）：`Segment+ReentrantLock` → **CAS + synchronized（只锁桶头）**，锁粒度更细【源】。

### 关键：单 JVM 锁 vs 多进程锁的本质区别（这是通向 Part 2 的桥）

- **同一 JVM 内**：多线程共享同一块堆内存，锁就是内存里的一个对象（Monitor / CAS / LongAdder），**纳秒~微秒级、无网络参与**；
- **多进程**：不共享内存，必须靠外部协调者（Redis Lua / 数据库 / ZooKeeper）做分布式锁，**每次抢锁有网络 RTT、有故障与一致性问题**。

> 一句话：**单实例的锁是免费的，多实例的锁是分布式系统问题**。这也是为什么 `TrafficPool.notifyCreated` 里用 `states.containsKey` 判重（进程内原子）在单 pod 是对的，多 pod 就失效了（见 2.5）。

## 1.5 缓存三兄弟 + 热点 key（单实例同样存在）

- **穿透**：查不存在的数据，缓存和 DB 都 miss → 每次都打存储层（恶意爬虫放大）→ 布隆过滤器 / 空值短 TTL 缓存；
- **击穿**：**热点 key** 过期瞬间，大量请求同时发现无缓存、全打 DB → 互斥重建（单实例用 JVM 锁即可，分布式用 Redis `SETNX`）/ 逻辑过期；
- **雪崩**：大量 key 同时失效或缓存层故障，流量全压 DB → **TTL 打散 + 随机值** / 缓存预热 / 限流降级 / Redis 高可用；
- **热点 key + bigkey 放大**【源】：一个 **1MB 的 bigkey 每秒访问 1000 次 → 每秒 1000MB 流量**，远超千兆网卡（128MB/s），直接网络拥塞。

## 1.6 每请求算力成本（现算聚合）——本项目单 Pod 最大的隐性瓶颈

### 问题现象与案例

每个请求都现算聚合/重复拉同一份数据 → CPU、网络被重复浪费，**性能看似正常、一压就崩**。

- Netflix Druid【源】：引入**区间感知缓存**后，**84% 的查询直接命中缓存**，不再重复算同样的时间区间聚合；
- 华为云量化回测【源】：接口访问日志分析，**超七成请求是重复的静态历史行情拉取**，靠分层缓存消除。

### 本项目对照

- `UserProfileAggregator.build(userId)`：**每次推荐请求**都把该用户**全部历史行为**扫一遍做权重×衰减聚合；
- `InMemoryItemFeatureService.itemFeature(postId)`：每次现算聚合点赞/评论/收藏/转发/浏览/时长。

这正是高并发第一铁律"**能预计算的不实时算**"的违反——QPS 一上来，CPU 全耗在重复计算上。**这是本项目单 pod 阶段最值得先做的优化**（把画像/物品特征近线预聚合，在线只读）。

### 主流解法

1. 预计算/预聚合（写时/近线算好，读时直接取）；
2. 结果缓存（相同入参→相同结果，Caffeine/Redis）；
3. 漏斗削减（高频重复计算移到缓存层/预计算层）。

## 1.7 单机限流（单 Pod 的第一道防线）

### 问题现象

突发流量超过实例能力，被动硬扛会排队→超时→线程/连接池占满→雪崩；限流是**主动拒绝超出部分，牺牲少量请求保整体**。

### 算法与边界案例【源：JavaGuide】

- **固定窗口**：有**边界突刺**——限 1 分钟 1000 次，第 1 分钟最后 1 秒进 1000、第 2 分钟第 1 秒再进 1000，2 秒内放行 2000，等效 QPS 远超 16.7；
- **滑动窗口**：切小格子，缓解突刺；
- **漏桶**：恒定速率输出，无法应对突发；
- **令牌桶**：限制平均速率 + 允许桶容量内突发，适合大多数业务（Guava `RateLimiter` / Sentinel 单机版）。

**单机 vs 分布式限流**：单机本地计数、零网络开销；**若总配额可按实例数均分（1000 QPS ÷ 10 实例 = 各 100），单机限流更优**——这是先做单机限流的最重要理由。

---

# Part 2　改造多 Pod 后：新典型问题与主流解法

> **总根因**：单实例假定的"进程内共享"（内存、锁、会话、定时器、计数器、事件总线）在多实例下**不复存在**。下面每条都是"把某个进程内共享外置"。

## 2.1 进程内内存失效：事件总线 + 状态（最根本）

### 问题现象

单实例里进程内的东西，多实例后每实例各持一份：写请求打到 A 实例，B 实例读不到；发到 A 的事件，B 完全收不到。

### 本项目对照（最生动的例子：`PostCreatedEventBus`）

```java
// forum-core .../stream/PostCreatedEventBus.java
private final List<Consumer<PostCreatedEvent>> subscribers = new CopyOnWriteArrayList<>();
public void publish(PostCreatedEvent event) {
    for (Consumer<PostCreatedEvent> consumer : subscribers) consumer.accept(event);
}
```

这是**进程内**广播（`CopyOnWriteArrayList`）。**单 pod 完全正确**：发帖 → 扇出/搜索索引/流量池同步消费。但**多 pod 就失效**：用户在 pod A 发帖，`PostCreatedEventBus.publish` 只广播了 pod A 进程内的订阅者——**pod B 的 `FanoutOnPostCreated`/`SearchIndexUpdater`/`TrafficPoolOnPostCreated` 一个都收不到**，关注流漏扇出、搜索漏索引、流量池漏入池。

**解法**：生产侧已经做对了——`PostCreatedEventBus` 是"模拟 Kafka"的进程内版；真正的多实例形态就是走 Kafka（`KafkaPostCreatedProducer → topic post-created → KafkaPostCreatedConsumer → 各实例的 bus`）。**"进程内总线"只在单实例成立，多实例必须升级为消息队列**。

同样地：内存仓库（`ConcurrentHashMap`）、内存画像、`NewItemPool`/`TrafficPool` 冷启动池、`ItemCfModelStore`、`SearchIndex` 倒排、`HeatAggregator` 热搜——**全部是每实例各一份**，多实例必然数据不一致。

### 主流解法

状态外置到 Redis/MySQL（唯一数据源）；事件从进程内总线升级为消息队列（Kafka，一份事件多路消费）；本地缓存只放可容忍不一致的弱一致数据 + 失效广播。

## 2.2 会话/登录态丢失（HttpSession）

### 问题现象

默认 `HttpSession` 存 **Tomcat JVM 内存**。多实例 + 负载均衡后，用户登录落在 pod A，下次请求被轮询到 pod B → B 没有该 Session → **登录态随机丢失、被要求重新登录**。

### 本项目对照

本项目**所有**控制器都直接用 `HttpSession`（`AuthController` 写 `userId/username`，`AuthInterceptor` 读）——这是教科书级的"多实例必踩坑"：**只要水平扩展，用户就反复掉登录**。

### 主流解法

1. **Spring Session + Redis**：Session 数据外置 Redis，所有实例读同一份，与负载均衡无关【源：Spring Session / 阿里云】；
2. **JWT 无状态 token**：服务端无状态，天然支持任意实例；代价是吊销/踢人难，需双 token + 刷新；
3. **Sticky session（会话亲和）**：让负载均衡把同一用户固定转发到同一实例。**缺点（业界公认）**：破坏弹性伸缩、滚动发布时亲和被打破、单实例故障时该批用户全断【源：StackHarbor"sticky session 是在解决问题还是在掩盖 bug"】。

> 结论：**无状态（Redis 会话 / JWT）是正解，sticky 只是兜底**。

## 2.3 定时任务重复执行（@Scheduled）

### 问题现象

`@Scheduled` 由每个 JVM 自己计时触发。N 个实例部署后，**同一个任务被触发 N 次** → 重复清理、重复通知、重复对账。

### 本项目对照

项目里共有 **6 处 `@Scheduled`**，多 pod 下全部会重复跑：

| 任务 | 频率 | 多 pod 后果 |
|---|---|---|
| `DataStore.scheduledSave`（JSON 落盘） | 30s | 多实例各存各的、互相覆盖 |
| `RealtimeFeatureWindow.scheduledFlush` | 5s | 重复聚合，重复写特征 |
| `TrafficPool.cleanup` | 1h | 重复清理（无害但浪费） |
| `SimulatedActivityService.simulate` | 15min | **重复造帖/造互动**（双倍甚至 N 倍模拟数据！） |
| `PostService.purgeExpiredPosts` | 每天 3 点 | 重复清回收站（无害但浪费） |
| `OfflineEvalScheduler.runEval` | 30min | 重复评估、重复写 eval-report |

### 主流解法

1. **ShedLock**：给 `@Scheduled` 加分布式锁（`@SchedulerLock`），集群中同一任务同一时刻最多一个节点执行；注意 `lockAtMostFor`/`lockAtLeastFor` 两个参数；
2. **Quartz 集群模式**：JDBC `JobStore` + `isClustered=true`，靠数据库行锁保证单节点触发；
3. **XXL-Job**：独立调度中心统一触达执行器，任务不在业务进程内自触发，天然避免重复，支持故障转移/分片广播。

## 2.4 单机限流失效（→ 分布式限流）

### 问题现象

单机限流是进程内计数。多实例后每个实例各自限流：**10 个实例 × 1000 QPS = 名义 1 万 QPS，但全局真实容量可能只有 1 万** → 总量超出后端承受能力【源：双 11 洪峰避坑】。

### 主流解法

- **Redis + Lua 原子限流**：计数放 Redis，Lua 脚本保证"读-判-写"原子，所有实例共享同一计数器（业界最普遍）；
- **Sentinel 集群流控**：token-server / token-client 角色，token server 统一发号；
- **实践组合**：单机限流（兜底本机，零开销）+ 分布式限流（全局控制）双层；分布式限流有网络开销，Redis 故障时要降级为本地限流。

## 2.5 分布式锁与幂等

### 问题现象

多进程并发写同一资源，单实例的 `synchronized`/`Lock` 失效；MQ "at-least-once" 下重复投递 → 重复处理。

### 本项目对照（一好一坑）

- ✅ **已经做对的**：`RedisIdempotencyStore` 用 **`SET key PROCESSING NX EX` 原子占位**——这是**分布式安全**的幂等（所有实例共用一个 Redis），发帖防重在多 pod 下依然正确；
- ❌ **会踩的坑**：`TrafficPool.notifyCreated` 用 `states.containsKey(postId)` 判重——这是**进程内**判重，多 pod 下每个实例的 TrafficPool 各一份，**同一个新帖会在 N 个 pod 各入池一次**。

### 主流解法

1. **Redis `SET NX EX`**（单命令原子加锁，避免 `SETNX`+`EXPIRE` 分两步的死锁坑）；
2. **Redisson 分布式锁**：可重入（hash 计数）、**看门狗自动续期**（默认 30s，任务没跑完自动续，避免"锁先过期另一进程抢到 → 双写"）【源：Redisson 官方】；
3. **Redlock 争议（重要坑）**【源】：Redis 主从切换时锁只写在旧主、从没同步 → 两个客户端同时持锁。Redlock（写 N 个独立 Redis 过半成功）为缓解此问题，但业界有著名争论（Kleppmann vs antirez），**不是银弹**，更严格替代是 Zookeeper/etcd 线性一致锁；
4. **DB 唯一约束兜底**（最终防线）：Redis 幂等表 + DB 唯一索引双保险【源：阿里云"Redis 做幂等是否绝对安全"】。

## 2.6 本地缓存一致性（L1 / 多级缓存）

### 问题现象

给每实例加 Caffeine L1 缓存后，写操作只作用在收到请求的实例，其它实例的 L1 还是旧值 → **各实例读到不同数据**。纯短 TTL 只是缩小不一致窗口。

### 主流解法【源：Canal/MQ 实践】

1. **短 TTL + 定时刷新**：最简单，接受最终一致；
2. **失效广播**：写操作后通过 Redis pub/sub 或 MQ 广播"key 失效"，所有实例收到后删本地缓存；
3. **Binlog 订阅（Canal）**：Canal 伪装 MySQL 从库订阅 binlog → 变更事件推 MQ → 各实例统一失效/刷新——**与业务写路径解耦**，多实例同步（常见组合 `MySQL + Canal + Kafka + Redis + Caffeine`）。

## 2.7 热点 key（缓存单点 / 击穿）

### 问题现象

多实例把流量汇聚到同一个 Redis key（爆款详情、热搜词）。单个 key 落在某一节点成为单点，超过单节点能力 → Redis 卡顿、击穿 DB（详见前文 1.5）。**加机器没用**——所有 pod 都去挤同一个单点。

### 主流解法

- **本地缓存挡热点**：热点数据放进程内缓存（代价是回到 2.6 的一致性）；
- **key 复制/分散**：热 key 拆多个带后缀 key（`key#1…#N`）分摊到不同节点；
- **JD-hotkey**【源】：各 worker 进程内滑动窗口计数 → dashboard 汇总 → 毫秒级识别热 key 并**推送到所有 worker 本地内存**，实现"热点自动本地缓存"（新版单机 QPS 可达 35 万）。

## 2.8 部分失败与重试风暴（雪崩）

### 问题现象（强案例）

分布式下调用变跨进程/跨网络，一个下游变慢，调用方线程被占住；**超时后的重试不做退避与限制，会形成重试风暴**，流量指数放大，把能自愈的下游打挂并向上游级联。

- **GitHub 2026-08 大规模宕机 8 小时官方复盘**【源：The Register 等】：根因之一是 **VS Code 重试风暴**——客户端/边缘持续重试，Copilot 令牌流量被放大**约 10 倍**，叠加自动扩缩容盲区，形成级联故障；
- 腾讯云 Feign 重试故障复盘【源】：线上 Feign 默认重试在多实例/网关环境下放大故障；
- JavaGuide 经典雪崩链路【源】：服务 C 变慢 → 调 C 的线程一直等待 → 线程池耗尽 → 服务 B 也被拖垮。

### 主流解法

合理**超时**（连接/读超时分级）→ **重试退避**（指数退避 + 抖动 jitter，限制最大次数）→ **熔断**（Sentinel 慢调用/异常比例，半开状态探活）→ **线程池隔离/舱壁**（不同下游独立线程池）→ **降级/兜底**（返回缓存或默认值）。**降级顺序本身要设计**（先功能降级、再数据降级、最后静态页），防止雪崩正循环。

## 2.9 可观测性（跨实例排障）

### 问题现象

单实例看一份日志就能追踪一次请求；多实例后一次请求的日志散落在多个 pod，按"关键字+时间"串不起来，报错无法定位是哪个实例哪一段。

### 主流解法

**分布式链路追踪**：为每个请求生成全局 **traceId**，跨实例通过 HTTP 头透传（SkyWalking `sw8` / W3C `traceparent`），日志带 traceId 写入 MDC，按 traceId 把一次请求跨 pod 串起来。工具：**SkyWalking**（javaagent 字节码增强，对 Spring MVC 等无侵入埋点）、**Micrometer Tracing**（Spring Boot 3 内置）。落地组合：APM + 日志聚合（ELK/Loki）+ traceId 关联。

## 2.10 负载均衡与优雅上下线

### 问题现象

负载均衡（K8s Service / Nginx / Gateway）把请求散到各 pod，是"多实例"的入口，也带来两个问题：① 有状态应用（内存 session）在流量被拆散后失效 → **无状态化是前提**；② 滚动发布/优雅下线没做好，正在摘除的旧 pod 仍被转发流量 → 请求失败/连接中断【源：ingress-nginx sticky 在滚动更新时路由错乱的 issue】。

### 主流解法

- **应用无状态化**（2.1/2.2 的解）——任意 pod 可被替换，是水平扩展与滚动发布的前提；
- **K8s 就绪探针（readinessProbe）+ 终止宽限期（terminationGracePeriod）**：新 pod 就绪才接流量，旧 pod 收到 SIGTERM 先摘流量（preStop + 延迟）再退出 → 零中断滚动【源：阿里云官方】；
- **注册中心优雅上下线**（Nacos）：上线先注册再就绪、下线先摘流量再销毁。

---

# Part 3　一条主线串起来

**为什么"多 Pod 的问题几乎都源于同一个根因"？**

```
单实例隐含假设：进程内共享（内存 / 锁 / 会话 / 定时器 / 计数器 / 事件总线）
多实例后：每实例各持一份，互不共享
解法：把共享点"外置"到分布式基础设施
```

| 单实例的"进程内共享" | 多实例失效 | 外置到 |
|---|---|---|
| `PostCreatedEventBus` 内存广播 | 事件漏消费 | Kafka |
| `HttpSession` 内存 | 登录态丢失 | Redis（Spring Session）/ JWT |
| `@Scheduled` 本地计时 | 任务重复执行 | 分布式调度（ShedLock/XXL-Job） |
| 单机限流本地计数 | 总量超限 | Redis+Lua / Sentinel 集群 |
| `synchronized`/本地判重 | 并发重复 | 分布式锁（Redisson）/ DB 约束 |
| `ConcurrentHashMap` 状态/缓存 | 各实例不一致 | Redis / 失效广播 / Canal |
| 进程内冷启动池/画像/ItemCF | 各算各的 | Redis / 离线共享 |
| 本地日志 | 查不了跨实例请求 | 链路追踪（traceId/SkyWalking） |

**分层兜底贯穿始终**：本地缓存(L1)+Redis(L2)+DB、单机限流+分布式限流、Redis 幂等表+DB 唯一约束——**分布式方案都有成本与窗口，最终防线通常是 DB 约束**。

---

# Part 4　落到本项目：演进路径清单

结合三层模型（①能堆机器 / ②堆得起机器 / ③堆完的正确稳定）与本文两类问题，给 MiniForum 的演进路径：

| 阶段 | 动作 | 属于 |
|---|---|---|
| **单 pod 先做（性价比最高）** | 画像/物品特征**近线预聚合**（消灭 1.6 的每次现算） | ② |
| | 入口**单机限流**（Guava/Sentinel 单机，按实例均分配额） | ②+③ |
| | 推荐失败**热门兜底**降级 | ③ |
| | 连接池/线程池参数与超时治理 | ② |
| **多 pod 前的资格（必须先做）** | 行为事实源落 ClickHouse、冷启动池/画像/ItemCF 状态落 Redis——**拿到"能堆机器"的资格** | ① |
| | `PostCreatedEventBus` 正式切换 Kafka 跨实例 | ① |
| | `HttpSession` → Spring Session Redis / JWT | ① |
| | 6 处 `@Scheduled` 加 ShedLock 或迁 XXL-Job | ③ |
| | 单机限流升级为"单机+分布式"双层 | ③ |
| | `TrafficPool.notifyCreated` 判重从进程内改 Redis 幂等/去重 | ③ |
| | L1 缓存一致性（失效广播 / Canal） | ③ |
| **多 pod 之后** | traceId + 链路追踪、熔断降级、优雅上下线 | ③ |

**本项目已经"对"的（多 pod 下依然正确）**：`RedisIdempotencyStore`（分布式幂等）、Outbox→Kafka（跨实例事件必达）、Snowflake ID（全局唯一）、Redis 实时特征/关注流/画像适配、离线/近线独立模块。

---

## 附录 · 参考来源

**官方 / 一手**
- Tomcat 9 线程默认值：https://tomcat.apache.org/tomcat-9.0-doc/config/http.html
- JDK JEP 439（G1 vs ZGC 暂停口径）：https://openjdk.org/jeps/439
- HikariCP README / 池大小论证：https://github.com/brettwooldridge/HikariCP
- Redisson 分布式锁官方：https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers
- Spring Session 官方：https://docs.spring.io/spring-session/reference/
- Quartz 集群模式：https://www.quartz-scheduler.org/documentation/
- SkyWalking 探针：https://apache.googlesource.com/skywalking/
- Sentinel 熔断降级：https://sentinelguard.io/zh-cn/docs/circuit-breaking.html

**一线团队 / 真实复盘**
- 华为云线程池 8 参数事故复盘：https://bbs.huaweicloud.com/blogs/482119
- dev.to Spring Boot 线程耗尽复盘：https://dev.to/gaurikatara/our-spring-boot-api-froze-under-load-heres-exactly-how-we-fixed-it-3ecn
- Netflix Zuul 1→2（阻塞→非阻塞）：https://netflixtechblog.com/open-sourcing-zuul-2-82ea476cb2b3
- Netflix 虚拟线程踩坑：https://www.infoq.com/news/2024/08/netflix-performance-case-study/
- Netflix Druid 区间缓存（84% 命中）：https://netflixtechblog.com/stop-answering-the-same-question-twice-interval-aware-caching-for-druid-at-netflix-scale-22fadc9b840e
- GitHub 2026-08 宕机复盘（重试风暴）：https://www.theregister.com/saas/2026/08/19/github-blames-8-hour-outage-on-autoscaling-fail-and-vs-code-retry-storm/
- 美团线程池实践（隔离）：https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html
- 阿里云零中断滚动部署：https://help.aliyun.com/zh/ack/
- 华为云量化分层缓存（重复请求案例）：https://bbs.huaweicloud.com/blogs/484127

**真实压测 / 开源实证**
- loom-webflux-benchmarks（虚拟线程 vs WebFlux / GC / 连接数）：https://github.com/chrisgleissner/loom-webflux-benchmarks

**工程社区（标注使用）**
- JavaGuide 限流算法与熔断降级：https://github.com/Snailclimb/JavaGuide
- 缓存穿透/击穿/雪崩（阿里云社区）：https://developer.aliyun.com/article/1642537
- Redis 缓存设计与性能优化：https://developer.aliyun.com/article/1656571
- JD-hotkey 热点探测：https://blog.csdn.net/gold/article/details/153426528
- 本地缓存一致性策略（Canal/MQ）：https://juejin.cn/post/7169593294152794125
- Redis 做幂等是否绝对安全（阿里云社区）：https://developer.aliyun.com/article/1288084
- Redlock 安全之争：https://www.nebula-graph.com.cn/posts/redlock-safety-debate-analysis
- sticky session 的缺点视角：https://stackharbor.com/en/knowledge-base/sticky-session-when-affinity-hides-bug/

**项目内配套文档**
- `docs/C端推荐系统高并发应对调研与MiniForum现状差距.md`（三层模型 0.1 节）
- `docs/推荐系统微服务拆分方案.md`、`docs/数据存储矩阵.md`、`docs/生产化落地开发清单.md`
