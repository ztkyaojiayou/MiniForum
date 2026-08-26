/**
 * Flink 实时特征作业（近线层，forum-flink-nearline）
 * <p>
 * {@link com.tkzou.miniforum.recommend.prod.flink.FlinkRealtimeWindow}：独立 Flink 作业
 * （main()，flink run 提交，非 Spring Bean）——Kafka "behavior-log" → 滑动窗口(5min/1min) 聚合
 * 互动/曝光 → 写 Redis realtime:*（TTL 60s）。与内存版 RealtimeFeatureWindow 聚合口径一致。
 * 演示缺位（默认构建不含本模块，-Pprod 才编译）。
 */
package com.tkzou.miniforum.recommend.prod.flink;
