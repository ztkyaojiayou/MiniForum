package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.recommend.coldstart.InMemoryTrafficPoolStore;
import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 冷启流量池订阅者测试（P3.3 内存冷启事件化）
 * <p>
 * 内存发帖（InMemoryPostCreatedNotifier）publish 到总线 → TrafficPoolOnPostCreated 消费 →
 * {@link TrafficPool#notifyCreated} 把新帖纳入流量池跟踪。生产 Kafka 消费同样 publish 到该总线，
 * 订阅者行为一致，故这里用内存路径验证（总线已统一）。
 */
class TrafficPoolOnPostCreatedTest {

    /** 可控的 FeatureService 桩：itemFeature 返回指定 inNewPool 标记 */
    private static FeatureService featureService(boolean inNewPool) {
        return new FeatureService() {
            @Override
            public UserProfile userProfile(Long userId) {
                return null;
            }

            @Override
            public ItemFeature itemFeature(Long postId) {
                ItemFeature f = new ItemFeature();
                f.setPostId(postId);
                f.setInNewPool(inNewPool);
                return f;
            }

            @Override
            public double realtimeMatch(Long userId, Long postId) {
                return 0;
            }
        };
    }

    /**
     * 构造 TrafficPool 并注入 @Value 配置（裸 new 时 Spring 不处理注解，
     * enabled 默认 false 会跳过入池），取值对齐 application.yml 默认：enabled=true、base-boost=0.3。
     * 存储用内存实现（P2-2 状态外置后 TrafficPool 依赖 store 接口）。
     */
    private static TrafficPool newTrafficPool(FeatureService featureService) {
        TrafficPool pool = new TrafficPool(featureService, new BehaviorEventQueue(), new InMemoryTrafficPoolStore());
        ReflectionTestUtils.setField(pool, "enabled", true);
        ReflectionTestUtils.setField(pool, "baseBoost", 0.3);
        return pool;
    }

    @Test
    void postCreatedEvent_pushesNewPostIntoTrafficPool() {
        TrafficPool trafficPool = newTrafficPool(featureService(true));
        PostCreatedEventBus eventBus = new PostCreatedEventBus();
        new TrafficPoolOnPostCreated(eventBus, trafficPool); // 构造器即订阅
        InMemoryPostCreatedNotifier notifier = new InMemoryPostCreatedNotifier(eventBus);

        notifier.notify(new PostCreatedEvent(1001L, 7L, "alice", "标题", "内容", "科技", List.of("AI")));

        assertEquals(1, trafficPool.size(), "冷启新帖应入流量池跟踪");
        // tier=0 试探期：baseBoost=0.3 保底加分，证明已进入流量池（非 0）
        assertEquals(0.3, trafficPool.tierBonus(1001L), 1e-9);
    }

    @Test
    void repeatedPostCreatedEvent_isDeduplicated() {
        TrafficPool trafficPool = newTrafficPool(featureService(true));
        PostCreatedEventBus eventBus = new PostCreatedEventBus();
        new TrafficPoolOnPostCreated(eventBus, trafficPool);

        PostCreatedEvent event = new PostCreatedEvent(1001L, 7L, "alice", "标题", "内容", "科技", List.of("AI"));
        eventBus.publish(event);
        eventBus.publish(event); // 重复事件不应重复入池

        assertEquals(1, trafficPool.size(), "同一 postId 重复事件应去重");
    }

    @Test
    void nonNewPoolPost_isIgnoredByTrafficPool() {
        TrafficPool trafficPool = newTrafficPool(featureService(false));
        PostCreatedEventBus eventBus = new PostCreatedEventBus();
        new TrafficPoolOnPostCreated(eventBus, trafficPool);

        eventBus.publish(new PostCreatedEvent(2002L, 8L, "bob", "标题", "内容", "科技", List.of()));

        assertEquals(0, trafficPool.size(), "非冷启新帖不应入流量池");
        assertEquals(0.0, trafficPool.tierBonus(2002L), 1e-9);
    }
}
