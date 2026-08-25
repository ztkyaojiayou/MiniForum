package com.tkzou.miniforum.recommend.prod.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.feature.RealtimeFeature;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;

import java.time.LocalDateTime;

/**
 * Flink 实时特征作业（生产，`-Pprod` 编译；与内存版 {@code RealtimeFeatureWindow} 聚合口径一致）
 * <p>
 * <b>数据流程</b>：Kafka topic "behavior-log"（由 {@code prod.kafka.KafkaBehaviorLogger} 写入）
 * → 反序列化为 BehaviorLog → 按 用户("user:{id}") 与 物品("post:{id}") 双流
 * → {@link SlidingProcessingTimeWindows 滑动窗口(5min/1min)} 聚合 互动/曝光
 * → 写 Redis（key "realtime:{key}"，TTL 60s）→ 在线服务实时特征 realtime 读取。
 * <p>
 * 说明：
 * <ol>
 *   <li>本作业为<b>独立进程</b>（Flink 集群提交），不是 Spring Bean；与内存版二选一部署。</li>
 *   <li>用户侧 topicClicks（点击过的话题分布）需"帖子维度 join"，生产可用 broadcast 维度表，
 *       此处先聚合计数（实时 CTR / 热度爆发），话题投影留待维度 join 扩展。</li>
 *   <li>用 processing time 简化（避免水位线），窗口口径与内存版"近 N 分钟"一致。</li>
 * </ol>
 * 运行：{@code flink run -c com.tkzou.miniforum.recommend.prod.flink.FlinkRealtimeWindow jar [kafkaAddr] [redisHost] [redisPort]}
 */
public class FlinkRealtimeWindow {

    public static void main(String[] args) throws Exception {
        String bootstrap = args.length > 0 ? args[0] : "localhost:9092";
        String redisHost = args.length > 1 ? args[1] : "localhost";
        int redisPort = args.length > 2 ? Integer.parseInt(args[2]) : 6379;

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Kafka 行为流
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics("behavior-log")
                .setGroupId("mini-forum-realtime")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<BehaviorLog> behaviors = env.fromSource(source, WatermarkStrategy.noWatermarks(), "behavior-log")
                .map(new JsonToBehaviorLog());

        // 用户维度实时特征（近 5 分钟互动/曝光）
        behaviors
                .keyBy(new UserKeySelector())
                .window(SlidingProcessingTimeWindows.of(Time.minutes(5), Time.minutes(1)))
                .process(new RealtimeFeatureAggregator())
                .addSink(new RedisFeatureSink(redisHost, redisPort))
                .name("user-realtime-feature");

        // 物品维度实时特征（近 5 分钟互动/曝光 → 热度爆发/实时 CTR）
        behaviors
                .keyBy(new PostKeySelector())
                .window(SlidingProcessingTimeWindows.of(Time.minutes(5), Time.minutes(1)))
                .process(new RealtimeFeatureAggregator())
                .addSink(new RedisFeatureSink(redisHost, redisPort))
                .name("post-realtime-feature");

        env.execute("MiniForum-RealtimeFeature");
    }

    /** Kafka JSON → BehaviorLog */
    private static class JsonToBehaviorLog implements MapFunction<String, BehaviorLog> {
        private static final long serialVersionUID = 1L;
        private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        @Override
        public BehaviorLog map(String json) throws Exception {
            return mapper.readValue(json, BehaviorLog.class);
        }
    }

    private static class UserKeySelector implements KeySelector<BehaviorLog, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String getKey(BehaviorLog b) {
            return "user:" + (b.getUserId() == null ? "null" : b.getUserId());
        }
    }

    private static class PostKeySelector implements KeySelector<BehaviorLog, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String getKey(BehaviorLog b) {
            return "post:" + (b.getPostId() == null ? "null" : b.getPostId());
        }
    }

    /** 滑动窗口聚合：计数互动/曝光（与内存版 RealtimeFeatureWindow.flush 口径一致） */
    private static class RealtimeFeatureAggregator
            extends ProcessWindowFunction<BehaviorLog, RealtimeFeature, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, Context ctx, Iterable<BehaviorLog> elements, Collector<RealtimeFeature> out) {
            RealtimeFeature feature = new RealtimeFeature(key, LocalDateTime.now());
            for (BehaviorLog b : elements) {
                feature.setExposeCount(feature.getExposeCount() + 1);
                if (isDeepInteraction(b.getType())) {
                    feature.setClickCount(feature.getClickCount() + 1);
                }
            }
            out.collect(feature);
        }

        private boolean isDeepInteraction(BehaviorType type) {
            return type == BehaviorType.CLICK || type == BehaviorType.LIKE || type == BehaviorType.FAVORITE
                    || type == BehaviorType.COMMENT || type == BehaviorType.REPOST;
        }
    }

    /** Redis 汇：写 key "realtime:{key}"，TTL 60s（与 prod.redis.RedisRealtimeFeatureStore 同口径） */
    private static class RedisFeatureSink extends RichSinkFunction<RealtimeFeature> {
        private static final long serialVersionUID = 1L;
        private final String host;
        private final int port;
        private transient Jedis jedis;
        private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        public RedisFeatureSink(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void open(Configuration parameters) {
            this.jedis = new Jedis(host, port);
        }

        @Override
        public void invoke(RealtimeFeature value, SinkFunction.Context context) throws Exception {
            String key = "realtime:" + value.getKey();
            jedis.set(key, mapper.writeValueAsString(value));
            jedis.expire(key, 60);
        }

        @Override
        public void close() {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
