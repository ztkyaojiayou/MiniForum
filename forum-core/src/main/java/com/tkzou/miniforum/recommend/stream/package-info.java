/**
 * 事件与实时窗口（共享域部分）
 * <p>
 * 本包在 <b>forum-core（共享域）</b>中只保留<b>接口与纯事件件</b>——业务侧（admin）依赖它们完成解耦：
 * <ul>
 *   <li>{@link com.tkzou.miniforum.recommend.stream.PostCreatedEvent} —— 帖子创建事件负载
 *       （postId/authorId/author/title/content/category/topics，负载最小化，内容按 id 回源）；</li>
 *   <li>{@link com.tkzou.miniforum.recommend.stream.PostCreatedNotifier} —— 发帖事件发布器<b>接口</b>，
 *       业务 service 只依赖它；实现（内存同步 / Kafka 异步）在 recommend 侧，由 demo 装配注入；</li>
 *   <li>{@link com.tkzou.miniforum.recommend.stream.BehaviorEventQueue} —— 行为事件队列（模拟 Kafka），
 *       被行为采集器写入、被实时窗口/冷启动监听订阅。</li>
 * </ul>
 *
 * <b>数据流转</b>：
 * <pre>
 * 发帖落库 → PostCreatedNotifier.notify(PostCreatedEvent)
 *     ├ 演示：InMemoryPostCreatedNotifier（recommend 侧）→ followFeedStore.fanout
 *     └ 生产：KafkaPostCreatedProducer → "post-created" → KafkaPostCreatedConsumer → fanout + 冷启池
 * </pre>
 *
 * 完整的事件消费者（InMemoryPostCreatedNotifier / RealtimeFeatureWindow）在 forum-recommend-server 的
 * 同名包下——它们依赖 recommend 内部的 TrafficPool / RealtimeFeatureStore，故不放共享域。
 */
package com.tkzou.miniforum.recommend.stream;
