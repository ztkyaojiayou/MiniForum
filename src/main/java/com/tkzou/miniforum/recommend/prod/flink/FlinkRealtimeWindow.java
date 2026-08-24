package com.tkzou.miniforum.recommend.prod.flink;

/**
 * Flink 实时特征窗口（生产骨架，不引入 Flink 依赖）
 * <p>
 * 生产形态：用 Flink DataStream API 实现与 {@code recommend.stream.RealtimeFeatureWindow}
 * 完全一致的聚合逻辑（同一套计算口径，避免在离线/在线特征不一致），再写回 Redis。
 *
 * <pre>
 * // 生产 Flink 装配（示意，需要引入 flink-streaming-java）：
 * DataStream&lt;BehaviorLog&gt; stream = env.addSource(new KafkaSource&lt;&gt;("behavior-log"));
 *
 * // 用户维度实时特征（近 5 分钟点击过的话题分布）
 * stream.keyBy(b -&gt; "user:" + b.getUserId())
 *       .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
 *       .process(new RealtimeFeatureAggregator())
 *       .addSink(new RedisRealtimeSink());
 *
 * // 物品维度实时特征（近 5 分钟互动/曝光 → 热度爆发与实时 CTR）
 * stream.keyBy(b -&gt; "post:" + b.getPostId())
 *       .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
 *       .process(new RealtimeFeatureAggregator())
 *       .addSink(new RedisRealtimeSink());
 * </pre>
 *
 * 聚合逻辑与内存版 RealtimeFeatureWindow.flush() 相同：用户侧记 clickCount/exposeCount/topicClicks，
 * 物品侧记 clickCount/exposeCount，结果写 RealtimeFeatureStore（Redis）。此处保留聚合函数骨架占位。
 */
public class FlinkRealtimeWindow {

    private FlinkRealtimeWindow() {
    }

    /**
     * 窗口聚合函数骨架（生产替换为 ProcessWindowFunction）。
     * 核心逻辑与 RealtimeFeatureWindow.flush() 一致，仅为可编译占位。
     */
    public static class RealtimeFeatureAggregator {

        /**
         * 窗口内聚合一条行为（生产由 Flink 回调；此处供参考实现）。
         *
         * @param windowKey   "user:{id}" 或 "post:{id}"
         * @param clickCount  深度互动数
         * @param exposeCount 曝光数
         */
        public void aggregate(String windowKey, int clickCount, int exposeCount) {
            // 生产：构造 RealtimeFeature → redisSink（见 prod.redis.RedisRealtimeFeatureStore）
            // RealtimeFeature f = new RealtimeFeature(windowKey, windowEnd);
            // f.setClickCount(clickCount); f.setExposeCount(exposeCount);
            // redisStore.put(windowKey, f);
        }
    }
}
