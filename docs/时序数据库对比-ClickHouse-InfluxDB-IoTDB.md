# 时序数据库对比学习：ClickHouse vs InfluxDB vs IoTDB

> 创建：2026-08-28
> 读者：已理解 ClickHouse（列式 OLAP，见《ClickHouse新手学习》），接触过时序库 InfluxDB / IoTDB 的新手。
> 主题：三者都"列式 + 压缩"，但**定位、数据模型、时序能力**差异巨大——用对比讲透，落到本项目为什么选 ClickHouse。
> 核心一句话：**ClickHouse 是"能跑时序的通用 OLAP"，InfluxDB 是"监控指标时序库"，IoTDB 是"工业传感器时序库"**——列式是共性，模型和场景是分水岭。

---

## 0. 一句话全貌

```
三者共同点：列式存储 + 高压缩 + 高吞吐 append 写入 + 时间有序 —— 所以你都觉得"像"
三者根本区别：为"谁的数据、什么查询"而生
  ClickHouse  通用 OLAP 列式      → 任意大表聚合分析（日志/行为/指标都行），时序只是擅长场景之一
  InfluxDB    专用时序（监控）    → metric + tags + field，原生降采样/保留策略，运维监控的标配
  IoTDB       专用时序（工业 IoT）→ device + measurement + 对齐序列，高频传感器 + 边云一体
```

**回答你的第一反应**："它们好像也是列式存储？" —— **是的**，但三者的"列式"组织方式不同（§1），而且**列式不是它们的共同点中最关键的**，**数据模型和时序专用能力才是分水岭**（§3、§4）。

---

## 1. 都是列式，但"列式"怎么组织，各不相同

| 库 | 存储引擎 | "列式"的组织方式 |
|---|---|---|
| **ClickHouse** | MergeTree | 宽表 → 每列一个 `.bin` 文件 + granule（8192 行）独立压缩 + 稀疏索引 |
| **InfluxDB 1.x** | TSM | 字段按列存（field 列族），**tags 用倒排索引**（可搜索的维度元数据） |
| **InfluxDB 3.x (IOx)** | **Parquet + 对象存储**（Rust + DataFusion） | 真正的列式 Parquet，查询走 SQL/DataFusion，存对象存储（S3/minio） |
| **IoTDB** | **TsFile** | 列式文件 + **时序专用编码**（Gorilla / TS-2DIFF：差值+游程，对单调时间戳/缓变数值压得更狠） |

**共同点**：都是"同一列的值堆在一起，按列读 + 压缩 + 只读需要的列"——这就是你直觉里"它们像"的原因。
**差异**：ClickHouse 是"通用列式（任何字段都是列）"；InfluxDB/IoTDB 在列式之上还叠了**时序专用**的东西（tags 索引、对齐序列、专用编码），这让它们**更专但也更局限**。

---

## 2. 定位对比表

| 维度 | **ClickHouse** | **InfluxDB** | **IoTDB** |
|---|---|---|---|
| 定位 | 通用 OLAP 分析库 | 监控/指标时序库 | 工业物联网时序库 |
| 典型用户 | 大数据/推荐/日志分析 | 运维监控（SRE）、可观测性 | 工业 IoT、设备/传感器 |
| 数据模型 | 宽表（无强制 tag/field 之分） | **measurement + tags + fields + ts** | **database/device + measurement + aligned series** |
| 是否有 schema | 建表定列（强 schema） | 1.x 无 schema（写时即建）/ 3.x bucket | 建库/设备时注册 measurement（强 schema） |
| 时序专用能力 | 少（要自己用物化视图搭） | **多（原生）**：降采样/保留策略/连续查询 | **多（原生）**：对齐序列/编码器/TTL/降采样/插值 |
| 查询语言 | 标准 SQL（+ OLAP 函数） | InfluxQL / Flux / SQL（3.x） | 类 SQL（IOTDB-SQL） |
| 擅长 | 任意维度聚合、join、宽表明细 | 时间分桶聚合、标签维度筛选 | 高频传感器、多测点对齐、边云同步 |
| 不擅长 | 无原生降采样/保留策略 | 非监控场景、复杂 join | 非 IoT 场景、通用分析 |

---

## 3. 数据模型：三者的最大分水岭（用同一个例子讲透）

**例子**：存"一台服务器的 CPU/内存使用率"这个监控数据。

### InfluxDB（metric + tag + field —— 强制区分"维度"和"数值"）
```
Line Protocol：cpu_usage host="web-01" region="cn-beijing" value=0.85 1720000000000
   measurement=cpu_usage（表）
   tags = host, region（可索引的维度，用来筛选/分组）
   field = value（数值，参与聚合）
   timestamp（唯一主键）
```
**核心认知**：InfluxDB 逼你把"维度（tags）"和"数值（fields）"分开——因为它的查询模型（`WHERE host='web-01' GROUP BY region`）就是围绕 tags 倒排索引设计的。

### IoTDB（device + measurement + 对齐序列）
```
CREATE ALIGNED TIMESERIES root.sg1.web01(cpu_usage FLOAT, mem_usage FLOAT);
INSERT INTO root.sg1.web01(timestamp, cpu_usage, mem_usage) VALUES(1720000000, 0.85, 0.6);
   device = root.sg1.web01（一个采集设备）
   measurement = cpu_usage / mem_usage（测点，**同时间戳对齐存储**）
```
**核心认知**：IoTDB 的杀手锏是**对齐序列**——一个设备的多个测点**共享同一时间戳、按列组存**，查"这台设备某时刻所有测点"一次 IO 全出；这是工业场景"一个设备几十个传感器"的刚需。

### ClickHouse（宽表 —— 不区分 tag/field，都是普通列）
```sql
CREATE TABLE metrics (ts DateTime, host String, region String, cpu Float64, mem Float64)
ENGINE = MergeTree ORDER BY (host, ts);
INSERT INTO metrics VALUES(now(), 'web-01', 'cn-beijing', 0.85, 0.6);
```
**核心认知**：ClickHouse 没有"tag/field"概念——`host/region` 就是普通列。你要"按 host 分组"就 `GROUP BY host`，"按时间过滤"就 `WHERE ts >= ...`（排序键是 `(host, ts)`）。**自由，但一切都得自己建**。

### 三者建模同一数据的对比总结

| | 维度怎么表示 | 数值怎么表示 | 时间怎么表示 |
|---|---|---|---|
| ClickHouse | 普通列 | 普通列 | 普通列（常放排序键首位） |
| InfluxDB | **tags（倒排索引）** | **fields** | **强制，且是主键** |
| IoTDB | device/measurement 层级 | measurement | 强制，且是主键 |

> **一句话**：ClickHouse 把一切当"列"（自由但 DIY）；InfluxDB/IoTDB 把时间当"主键"、把维度/测点结构化（专精但受限）。**选型 = 你的数据是不是天生"一张表一个固定指标、按时间采集"**。

---

## 4. 时序专用能力对比（ClickHouse 明显"缺"的地方）

| 能力 | ClickHouse | InfluxDB | IoTDB |
|---|---|---|---|
| **保留策略/TTL**（数据自动过期） | 有 `TTL` 语法但要点排查 | **原生**（retention policy / bucket） | **原生**（TTL per series） |
| **降采样 / 连续查询**（把高频聚成低频） | 自己用物化视图搭 | **原生**（Continuous Query / 任务） | **原生**（view / 降采样） |
| **时间分桶聚合** | `toStartOfHour()` 等手动 | `GROUP BY time(1h)` 原生语法 | `GROUP BY ... every(1h)` |
| **对齐序列**（多测点同时间戳） | 无（宽表天然对齐） | 无 | **原生**（aligned series） |
| **插值 / 补点**（缺失时间戳） | 无（SQL 自己写） | 部分（fill） | **原生**（FILL 语法） |

**结论**：如果你的需求是"**监控指标 + 自动过期 + 自动降采样 + 告警**"，InfluxDB 开箱即用、ClickHouse 要手工搭；如果你的数据是"**海量事件明细 + 任意维度聚合分析**"，ClickHouse 的通用 SQL + join 更强，InfluxDB/IoTDB 反而被模型束缚。

---

## 5. 查询语言对比（一个"每 1 小时平均 CPU"的查询）

```sql
-- ClickHouse：标准 SQL，时间分桶函数
SELECT toStartOfHour(ts) AS h, avg(cpu) FROM metrics
WHERE host='web-01' GROUP BY h ORDER BY h;

-- InfluxDB（InfluxQL，或 3.x SQL）：time() 是原生语法
SELECT mean(cpu) FROM cpu_usage
WHERE host='web-01' GROUP BY time(1h);

-- IoTDB：类 SQL + FILL/对齐
SELECT avg(cpu_usage) FROM root.sg1.web01 GROUP BY ([0, now()), 1h);
```

**你会 SQL 就能上手三者**（语法都类 SQL），区别在细节：InfluxDB 的 `time()` 分桶、IoTDB 的 `FILL` 插值、ClickHouse 的 `toStartOfXxx`——都是时序/分析的心智。

---

## 6. 写入模型对比（都是高吞吐 append，但入口不同）

| 库 | 写入入口 | 特点 |
|---|---|---|
| ClickHouse | `INSERT` 批量 / Kafka Engine | 攒批 + 批量落盘；毫秒级自动消费 topic |
| InfluxDB | **Line Protocol**（HTTP/Telegraf） | 一行一个点，Telegraf 采集器生态 |
| IoTDB | 会话批量写入 / 客户端 | 批量 + 对齐序列优化 |

**共同点**：都"只增不改"、都靠"批量 + 压缩"扛写入吞吐。
**差异**：InfluxDB 有 Telegraf（采集器生态，监控标配），IoTDB 面向设备接入（边缘网关/协议），ClickHouse 面向"已有数据（Kafka/离线）灌入"。

---

## 7. 各自最强场景 + 选型决策表

| 你的场景 | 选 |
|---|---|
| **行为/事件日志、任意维度聚合分析、要 SQL + join（推荐画像/报表）** | **ClickHouse** |
| **监控指标**（CPU/内存/延迟，每 5 秒采样），要保留策略/降采样/告警 | **InfluxDB**（+ Prometheus 生态） |
| **工业 IoT**（千万传感器高频采集、设备建模、对齐序列、边云同步） | **IoTDB** |
| 只是"时间序列"但数据量小、想简单 | 三者都行，选你最熟的 |
| 大数据量 + 时间序列 + 还要复杂分析/join | ClickHouse（通用性换时序便捷性） |

**口诀**：**要监控选 InfluxDB，要工业选 IoTDB，要通用分析选 ClickHouse。**

---

## 8. 落到本项目：为什么用 ClickHouse 存行为日志，而不是 InfluxDB/IoTDB？

| 本项目的需求 | 为什么 ClickHouse 合适 |
|---|---|
| 行为日志是**事件明细**（谁/何时/对哪个帖/做了什么），不是固定频率采样指标 | ClickHouse 宽表 + 任意维度列，InfluxDB 的 tag/field 模型反而别扭（"行为"不是固定数值指标） |
| 要**按用户/帖子/时间切片聚合**（画像、ItemCF、热度、离线评估） | 标准 SQL + 列裁剪 + join，和推荐模型数据打通；InfluxDB/IoTDB 的分析面向时间分桶 |
| 要和**其他数据**（帖子/用户/推荐结果）做关联分析 | ClickHouse 支持 join、子查询；InfluxDB/IoTDB 的 join 很弱 |
| 数据来自 Kafka（行为打点 → Kafka → ClickHouse） | Kafka Engine 原生消费，一行导入代码不写 |

**反向确认**：如果本项目是"**每 5 秒采集一批服务器指标，要保留 30 天后自动过期 + 自动降采样出日/周聚合 + 告警**"——那 InfluxDB 会更省事（原生能力全包）。但我们是"**事件 + 分析 + 打通推荐系统**"，所以 ClickHouse。

> **选型跟着场景走，不是"谁更高级"**——三兄弟各司其职：ClickHouse 管"分析"，InfluxDB 管"监控"，IoTDB 管"工业"。

---

## 9. 没做过 vs 做过的人，对这三个库的认知

| 没做过的人会想 | 做过的人会想 |
|---|---|
| "都是列式时序库，差不多" | "**列式是共性，模型和时序能力是分水岭**——监控选 InfluxDB，工业选 IoTDB，分析选 ClickHouse" |
| "ClickHouse 能存时序，就够用了" | "没原生降采样/保留策略，监控场景要自己搭一套" |
| "InfluxDB 什么都能存" | "**模型逼你区分 tag/field**，非监控数据（事件/明细）塞进去很别扭" |
| "IoTDB 只是又一个时序库" | "**对齐序列 + 工业编码**是它的杀手锏，多测点同时间戳查询一次 IO" |
| "时序 = 按时间排序的表" | "时序的难点是**高频写入 + 降采样 + 保留策略 + 对齐**，不是'按时间排'本身" |

---

## 附：与其它文档的关系

- `docs/ClickHouse新手学习-对比MySQL与Redis.md`：ClickHouse 的底层机制（本文的底座，理解了三者"为什么都列式快"）；
- `docs/领域模型与实体关系.md`：本项目行为日志（BehaviorLog）——用 ClickHouse 承载的实体；
- `docs/搜广推-概念与架构.md`：行为数据是搜广推共享底座，ClickHouse 是其中的"分析层"。

**来源**：[InfluxDB 3 (IOx) Deep Dive](https://pipecode.ai/blogs/influxdb-3-iox-rust-datafusion-parquet)、[主流时序数据库（TSDB）及对比分析](https://nullthought.net/?p=6343)、[IoTDB 时序数据库技术内幕](https://blog.csdn.net/beautifulmemory/article/details/161077178)、[2025 时序数据库选型](https://zeeklog.com/2025shi-xu-shu-ju-ku-xuan-xing-cong-jia-gou-ji-yin-dao-aifu-neng-lai-jie-xi-5/)、[从 InfluxDB 到 Apache IoTDB 选型避坑指南](https://www.vps345.com/26646.html)。
