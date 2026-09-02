/**
 * 事件与实时窗口（共享域部分）——「内存版事件总线」概念映射
 * <p>
 * 本包在 <b>forum-core（共享域）</b>中只保留<b>接口与纯事件件</b>——业务侧（admin）依赖它们完成解耦。
 * 对标 Kafka 的组件角色，这里用三个概念组织（这就是本项目"零中间件版事件总线"）：
 * <ul>
 *   <li><b>Producer（生产者）</b>：{@link com.tkzou.miniforum.recommend.mq.producer.PostCreatedProducer} —— 发帖事件发布<b>接口</b>（业务 service 只依赖它；
 *       实现 = 内存同步 {@code InMemoryPostCreatedProducer} / Kafka 异步 {@code KafkaPostCreatedProducer}，由 demo 装配注入）；
 *       行为侧对应 {@code BehaviorLogger}（行为打点接口）。</li>
 *   <li><b>Bus（内存版事件总线）</b>：{@link PostCreatedEventBus}（帖子事件）/{@link BehaviorEventQueue}（行为事件）——
 *       进程内<b>同步发布-订阅</b>总线（观察者模式，无队列缓冲）；生产由真实 Kafka topic 替代。</li>
 *   <li><b>Consumer（消费者）</b>：{@link com.tkzou.miniforum.recommend.mq.consumer.PostCreatedConsumer} —— 发帖事件消费接口（实现：关注流扇出 / 搜索索引 / 冷启池预热）；
 *       行为侧由订阅方直接以 {@code Consumer<BehaviorLog>} 注册（实时窗口 / 冷启动反馈 / 流量池 / 热搜）。</li>
 * </ul>
 * <p>
 * <b>与真实 MQ 的差异</b>：总线没有队列缓冲——{@code publish} 在调用线程内同步回调全部订阅者（慢消费者会拖住发布者），
 * 也无 offset / 重试 / 背压。能平移给 Kafka 的只有<b>发布侧解耦</b>（生产者只依赖总线接口，不依赖任何消费者）；
 * 异步、缓冲、offset、重试等队列语义必须由真实 Kafka 提供。
 *
 * <b>数据流转</b>：
 * <pre>
 * 发帖落库 → OutboxStore.enqueue → Relayer → PostCreatedProducer.publish(PostCreatedEvent)
 *     ├ 演示：InMemoryPostCreatedProducer → PostCreatedEventBus → PostCreatedConsumer（fanout + 搜索 + 冷启）
 *     └ 生产：KafkaPostCreatedProducer → "post-created" → KafkaPostCreatedConsumer → PostCreatedEventBus → 同上
 * </pre>
 *
 * 完整的事件消费者（FanoutPostCreatedConsumer / SearchIndexPostCreatedConsumer / TrafficPoolPostCreatedConsumer）在 forum-recommend-server 的
 * {@code recommend/mq/consumer/} 下——它们依赖 recommend 内部的 TrafficPool / RealtimeFeatureStore / SearchIndex，故不放共享域。
 * 共享域按角色归子包：{@code mq/producer/}（{@code PostCreatedProducer} 接口）、{@code mq/consumer/}（{@code PostCreatedConsumer} 接口）。
 */
package com.tkzou.miniforum.recommend.mq;
