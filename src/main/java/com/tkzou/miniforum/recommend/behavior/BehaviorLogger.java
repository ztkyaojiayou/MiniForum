package com.tkzou.miniforum.recommend.behavior;

/**
 * 行为采集器接口
 * <p>
 * 生产形态将行为写入 Kafka（见 prod.kafka.KafkaBehaviorLogger），默认使用内存实现
 * （InMemoryBehaviorLogger）：既持久化到 BehaviorLogRepository，又发布到行为事件队列
 * 供实时特征窗口消费，模拟"埋点 → Kafka → Flink"的完整链路。
 */
public interface BehaviorLogger {

    /**
     * 记录一次用户行为
     *
     * @param userId 用户 ID
     * @param postId 帖子 ID（无帖子的行为如关注为 null）
     * @param type   行为类型
     * @param scene  来源场景（POST/TRACK/RECOMMEND_FEED...）
     * @param expId  AB 实验组 ID（可为 null）
     */
    void log(Long userId, Long postId, BehaviorType type, String scene, String expId);

    /**
     * 记录一次带时长的行为（如 DWELL 阅读停留）。
     * 默认忽略时长，由具体实现决定是否记录（内存/生产实现会写入 durationSec）。
     */
    default void log(Long userId, Long postId, BehaviorType type, String scene, String expId, Double durationSec) {
        log(userId, postId, type, scene, expId);
    }
}
