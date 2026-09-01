package com.tkzou.miniforum.recommend.mq;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 行为事件总线（进程内同步发布-订阅——扮演生产环境 Kafka 的角色）
 * <p>
 * <b>数据流程</b>：{@code InMemoryBehaviorLogger} 写行为时 {@link #publish} 广播给所有订阅者
 * （实时特征窗口 {@code RealtimeFeatureWindow}、冷启动反馈 {@code ColdStartFeedbackListener} 等近线消费者），
 * 并记入历史供测试/回放。生产形态替换为 KafkaProducer/Consumer。无缓冲队列语义，事件实时派发。
 * <p>
 * 历史使用<b>有界环形缓冲</b>（默认上限 5000，超过丢弃最旧）：行为事件是高频写入，
 * 无界 {@code CopyOnWriteArrayList} 长跑即 OOM（P0-4）。
 */
@Component
public class BehaviorEventQueue {

    private final List<Consumer<BehaviorLog>> subscribers = new CopyOnWriteArrayList<>();

    /** 有界历史（仅测试/离线回放用）：ArrayDeque + 锁，写入 O(1)，超过 {@link #historyCap} 丢弃最旧 */
    private final ArrayDeque<BehaviorLog> history = new ArrayDeque<>();

    /** 历史最大条数（Spring 注入，默认 5000；测试直构时用字段默认值） */
    private int historyCap = 5000;

    @Value("${app.behavior.history-cap:5000}")
    public void setHistoryCap(int historyCap) {
        this.historyCap = Math.max(1, historyCap);
    }

    /** 发布一条行为事件：广播给全部订阅者，并记入有界历史（供测试/离线回放） */
    public void publish(BehaviorLog behavior) {
        synchronized (history) {
            if (history.size() >= historyCap) {
                history.pollFirst(); // 有界：丢弃最旧
            }
            history.addLast(behavior);
        }
        for (Consumer<BehaviorLog> subscriber : subscribers) {
            subscriber.accept(behavior);
        }
    }

    /** 订阅（如 RealtimeFeatureWindow 启动时订阅） */
    public void subscribe(Consumer<BehaviorLog> subscriber) {
        subscribers.add(subscriber);
    }

    /** 当前已发布事件数（未超过上限时） */
    public int size() {
        synchronized (history) {
            return history.size();
        }
    }

    /** 历史事件快照（供测试/离线评估） */
    public List<BehaviorLog> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /** 清空历史（测试用） */
    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }
}
