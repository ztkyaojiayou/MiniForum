/**
 * 事件与实时窗口（推荐侧实现）——「内存版 MQ」的消费者/生产者实现
 * <p>
 * 与 forum-core 的 recommend.mq（接口/事件件）对应，本包是<b>依赖 recommend 内部件的实现</b>，按角色归子包：
 * <ul>
 *   <li><b>{@code mq/consumer/}</b>：{@code FanoutPostCreatedConsumer}（关注流扇出）/ {@code SearchIndexPostCreatedConsumer}（建搜索索引）/
 *       {@code TrafficPoolPostCreatedConsumer}（冷启池预热）——都实现共享域 {@code PostCreatedConsumer}，由
 *       {@code PostCreatedEventBus} 构造器用 Spring 的 {@code List<PostCreatedConsumer>} 自动收集并注册到内存总线；</li>
 *   <li><b>{@code mq/producer/}</b>：{@code InMemoryPostCreatedProducer}（@Profile("!prod")）——实现 {@code PostCreatedProducer}，把发帖事件直接发到
 *       {@code PostCreatedEventBus}（内存版 MQ）；生产由 {@code KafkaPostCreatedProducer} 发 Kafka topic "post-created" 替代；</li>
 *   <li>{@code RealtimeFeatureWindow}（@Profile("!prod")）：行为事件消费者——订阅 {@code BehaviorEventQueue}（内存版行为 MQ），
 *       按近 N 分钟滑动窗口聚合互动/曝光，flush 到 RealtimeFeatureStore。prod 由独立 Flink 作业（forum-flink-nearline）替代。</li>
 * </ul>
 */
package com.tkzou.miniforum.recommend.mq;
