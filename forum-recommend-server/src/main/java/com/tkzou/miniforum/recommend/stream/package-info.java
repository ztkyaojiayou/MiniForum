/**
 * 事件与实时窗口（推荐侧实现）
 * <p>
 * 与 forum-core 的 recommend.stream（接口/事件件）对应，本包是<b>依赖 recommend 内部件的实现</b>：
 * <ul>
 *   <li>{@code InMemoryPostCreatedNotifier}（@Profile("!prod")）：内存发帖事件消费者——
 *       调 followFeedStore.fanout（关注流扇出）；生产由 KafkaPostCreatedConsumer 替代；</li>
 *   <li>{@code RealtimeFeatureWindow}（@Profile("!prod")）：模拟 Flink 的实时窗口聚合——
 *       订阅 BehaviorEventQueue，按近 N 分钟滑动窗口聚合互动/曝光，flush 到 RealtimeFeatureStore。
 *       prod 由独立 Flink 作业（forum-flink-nearline）替代。</li>
 * </ul>
 */
package com.tkzou.miniforum.recommend.stream;
