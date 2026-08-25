package com.tkzou.miniforum.recommend.behavior;

import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 内存行为采集器（默认实现）
 * <p>
 * <b>数据流程</b>：{@link #log(userId, postId, type, scene, expId)} → ①存 {@code BehaviorLogRepository}
 * （JSON 落盘，作为画像/评估的事实源）；②发布到 {@code BehaviorEventQueue}（模拟 Kafka），
 * 由实时特征窗口与冷启动反馈消费。生产形态见 prod.kafka.KafkaBehaviorLogger（@Profile("prod")，激活 prod 时本实现不加载）。
 */
@Component
@Profile("!prod")
public class InMemoryBehaviorLogger implements BehaviorLogger {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBehaviorLogger.class);

    private final BehaviorLogRepository repository;
    private final BehaviorEventQueue eventQueue;

    public InMemoryBehaviorLogger(BehaviorLogRepository repository,
                                  BehaviorEventQueue eventQueue) {
        this.repository = repository;
        this.eventQueue = eventQueue;
    }

    @Override
    public void log(Long userId, Long postId, BehaviorType type, String scene, String expId) {
        log(userId, postId, type, scene, expId, null);
    }

    @Override
    public void log(Long userId, Long postId, BehaviorType type, String scene, String expId, Double durationSec) {
        if (userId == null) {
            return;
        }
        BehaviorLog behavior = new BehaviorLog();
        behavior.setUserId(userId);
        behavior.setPostId(postId);
        behavior.setType(type);
        behavior.setTimestamp(LocalDateTime.now());
        behavior.setDurationSec(durationSec);
        behavior.setScene(scene == null || scene.isBlank() ? "DEFAULT" : scene);
        behavior.setExpId(expId);
        repository.save(behavior);
        eventQueue.publish(behavior);
    }
}
