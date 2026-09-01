/**
 * 事件与实时窗口（共享域部分）——「内存版 MQ」概念映射
 * <p>
 * 本包在 <b>forum-core（共享域）</b>中只保留<b>接口与纯事件件</b>——业务侧（admin）依赖它们完成解耦。
 * 对标 Kafka 的消息语义，这里用三个概念组织（这就是本项目"零中间件版 MQ"）：
 * <ul>
 *   <li><b>Producer（生产者）</b>：{@link PostCreatedProducer} —— 发帖事件发布<b>接口</b>（业务 service 只依赖它；
 *       实现 = 内存同步 {@code InMemoryPostCreatedProducer} / Kafka 异步 {@code KafkaPostCreatedProducer}，由 demo 装配注入）；
 *       行为侧对应 {@code BehaviorLogger}（行为打点接口）。</li>
 *   <li><b>Bus（内存版 MQ）</b>：{@link PostCreatedEventBus}（帖子事件）/{@link BehaviorEventQueue}（行为事件）——
 *       进程内发布-订阅总线，等价于"内存版 Kafka"；生产由真实 Kafka topic 替代。</li>
 *   <li><b>Consumer（消费者）</b>：{@link PostCreatedConsumer} —— 发帖事件消费接口（实现：关注流扇出 / 搜索索引 / 冷启池预热）；
 *       行为侧由订阅方直接以 {@code Consumer<BehaviorLog>} 注册（实时窗口 / 冷启动反馈 / 流量池 / 热搜）。</li>
 * </ul>
 *
 * <b>数据流转</b>：
 * <pre>
 * 发帖落库 → OutboxStore.enqueue → Relayer → PostCreatedProducer.publish(PostCreatedEvent)
 *     ├ 演示：InMemoryPostCreatedProducer → PostCreatedEventBus → PostCreatedConsumer（fanout + 搜索 + 冷启）
 *     └ 生产：KafkaPostCreatedProducer → "post-created" → KafkaPostCreatedConsumer → PostCreatedEventBus → 同上
 * </pre>
 *
 * 完整的事件消费者（FanoutOnPostCreated / SearchIndexUpdater / TrafficPoolOnPostCreated）在 forum-recommend-server 的
 * 同名包下——它们依赖 recommend 内部的 TrafficPool / RealtimeFeatureStore / SearchIndex，故不放共享域。
 */
package com.tkzou.miniforum.recommend.stream;
