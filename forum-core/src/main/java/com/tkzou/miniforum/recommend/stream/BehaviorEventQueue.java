package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 行为事件队列（模拟 Kafka）
 * <p>
 * <b>数据流程</b>：{@code InMemoryBehaviorLogger} 写行为时 {@link #publish} 广播给所有订阅者
 * （实时特征窗口 {@code RealtimeFeatureWindow}、冷启动反馈 {@code ColdStartFeedbackListener} 等近线消费者），
 * 并记入历史供测试/回放。生产形态替换为 KafkaProducer/Consumer。无缓冲队列语义，事件实时派发。
 */
@Component
public class BehaviorEventQueue {

    private final List<Consumer<BehaviorLog>> subscribers = new CopyOnWriteArrayList<>();
    private final List<BehaviorLog> history = new CopyOnWriteArrayList<>();

    /** 发布一条行为事件：广播给全部订阅者，并记入历史（供测试/离线回放） */
    public void publish(BehaviorLog behavior) {
        history.add(behavior);
        for (Consumer<BehaviorLog> subscriber : subscribers) {
            subscriber.accept(behavior);
        }
    }

    /** 订阅（如 RealtimeFeatureWindow 启动时订阅） */
    public void subscribe(Consumer<BehaviorLog> subscriber) {
        subscribers.add(subscriber);
    }

    /** 当前已发布事件数 */
    public int size() {
        return history.size();
    }

    /** 历史事件快照（供测试/离线评估） */
    public List<BehaviorLog> history() {
        return new ArrayList<>(history);
    }

    /** 清空历史（测试用） */
    public void clearHistory() {
        history.clear();
    }
}
