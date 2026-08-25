package com.tkzou.miniforum.recommend.feature;

import java.util.Optional;

/**
 * 实时特征存储接口（模拟 Redis）
 * <p>
 * 近线层（Flink 窗口）把聚合结果写入这里，在线层（排序特征）按 key 读取。
 * 生产形态由 Redis 实现（见 prod.redis.RedisRealtimeFeatureStore，@Profile("prod")）。
 */
public interface RealtimeFeatureStore {

    /** 写入（覆盖）某 key 的实时特征 */
    void put(String key, RealtimeFeature feature);

    /** 读取某 key 的实时特征 */
    Optional<RealtimeFeature> get(String key);

    /** 读取某用户（"user:{userId}"）的实时特征 */
    default Optional<RealtimeFeature> getForUser(Long userId) {
        return get("user:" + userId);
    }

    /** 读取某帖子（"post:{postId}"）的实时特征 */
    default Optional<RealtimeFeature> getForPost(Long postId) {
        return get("post:" + postId);
    }
}
