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
 * 记录行为时：①持久化到 BehaviorLogRepository（JSON 落盘，供画像/评估）；②发布到
 * BehaviorEventQueue（模拟 Kafka），由 RealtimeFeatureWindow 消费生成实时特征。
 * 生产形态见 prod.kafka.KafkaBehaviorLogger（@Profile("prod")，激活 prod 时本实现不加载）。
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
        if (userId == null) {
            return;
        }
        BehaviorLog behavior = new BehaviorLog();
        behavior.setUserId(userId);
        behavior.setPostId(postId);
        behavior.setType(type);
        behavior.setTimestamp(LocalDateTime.now());
        behavior.setScene(scene == null || scene.isBlank() ? "DEFAULT" : scene);
        behavior.setExpId(expId);
        repository.save(behavior);
        eventQueue.publish(behavior);
    }
}
