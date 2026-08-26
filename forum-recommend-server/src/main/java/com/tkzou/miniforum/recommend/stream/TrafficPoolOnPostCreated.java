package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import org.springframework.stereotype.Component;

/**
 * 冷启动流量池订阅者（订阅 {@link PostCreatedEventBus}）
 * <p>
 * 收到发帖事件 → {@link TrafficPool#notifyCreated} 把新帖加入流量池/赛马跟踪，
 * 与生产 Kafka 消费行为对齐（演示从懒加载改为<b>事件驱动</b>）。与 fanout/搜索索引一样是总线的一路消费者，
 * @!prod/@prod 通吃（生产不再在 KafkaPostCreatedConsumer 里直接调，统一走总线）。
 */
@Component
public class TrafficPoolOnPostCreated {

    public TrafficPoolOnPostCreated(PostCreatedEventBus eventBus, TrafficPool trafficPool) {
        eventBus.subscribe(event -> trafficPool.notifyCreated(event.getPostId()));
    }
}
