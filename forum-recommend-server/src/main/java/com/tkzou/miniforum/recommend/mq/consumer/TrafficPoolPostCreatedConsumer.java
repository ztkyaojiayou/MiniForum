package com.tkzou.miniforum.recommend.mq.consumer;
import com.tkzou.miniforum.recommend.mq.consumer.PostCreatedConsumer;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;

import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.mq.PostCreatedEventBus;
import org.springframework.stereotype.Component;

/**
 * 冷启动流量池订阅者（实现 {@link PostCreatedConsumer}，由 {@link PostCreatedEventBus} 构造器自动注册）
 * <p>
 * 收到发帖事件 → {@link TrafficPool#notifyCreated} 把新帖加入流量池/赛马跟踪，
 * 与生产 Kafka 消费行为对齐（演示从懒加载改为<b>事件驱动</b>）。与 fanout/搜索索引是总线的一路
 * 并行消费者（见 {@link PostCreatedEventBus}），@!prod/@prod 通吃
 * （生产不再在 KafkaPostCreatedConsumer 里直接调，统一走总线）。
 */
@Component
public class TrafficPoolPostCreatedConsumer implements PostCreatedConsumer {

    private final TrafficPool trafficPool;

    public TrafficPoolPostCreatedConsumer(TrafficPool trafficPool) {
        this.trafficPool = trafficPool;
    }

    @Override
    public String name() {
        return "traffic-pool";
    }

    @Override
    public void consume(PostCreatedEvent event) {
        trafficPool.notifyCreated(event.getPostId());
    }
}