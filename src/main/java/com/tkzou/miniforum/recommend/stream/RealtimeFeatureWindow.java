package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.feature.RealtimeFeature;
import com.tkzou.miniforum.recommend.feature.RealtimeFeatureStore;
import com.tkzou.miniforum.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时特征窗口（模拟 Flink 滑动窗口）
 * <p>
 * <b>数据流程</b>：订阅 {@link BehaviorEventQueue}（模拟 Kafka）→ 维护最近 N 条行为 → {@link #flush}
 * 按时间窗口（近 realtimeWindowMinutes 分钟）聚合：用户侧记"user:{id}"的点击数/曝光/点击过的话题分布，
 * 物品侧记"post:{id}"的互动/曝光 → 写入 {@link RealtimeFeatureStore}（模拟 Redis）→ 在线排序特征 realtime 读取。
 * 生产形态为 Flink 窗口算子（见 prod.flink.FlinkRealtimeWindow 骨架，聚合逻辑一致）。
 */
@Component
public class RealtimeFeatureWindow {

    private static final Logger log = LoggerFactory.getLogger(RealtimeFeatureWindow.class);

    private final BehaviorEventQueue eventQueue;
    private final RealtimeFeatureStore store;
    private final PostRepository postRepository;
    private final ConfigService configService;

    /** 最近事件（有界，防无限增长；时间窗口过滤靠 flush 时的 cutoff） */
    private final Deque<BehaviorLog> recentEvents = new ArrayDeque<>();

    public RealtimeFeatureWindow(BehaviorEventQueue eventQueue,
                                 RealtimeFeatureStore store,
                                 PostRepository postRepository,
                                 ConfigService configService) {
        this.eventQueue = eventQueue;
        this.store = store;
        this.postRepository = postRepository;
        this.configService = configService;
        this.eventQueue.subscribe(this::onEvent);
    }

    /** 事件入窗口（保留最近 maxEvents 条） */
    public void onEvent(BehaviorLog behavior) {
        synchronized (recentEvents) {
            recentEvents.addLast(behavior);
            int max = configService.current().getRealtimeWindowMaxEvents();
            while (recentEvents.size() > max) {
                recentEvents.removeFirst();
            }
        }
    }

    /** 聚合窗口内事件写回存储（滑动窗口，不清空；下次 flush 仍按时间 cutoff 过滤） */
    public synchronized void flush() {
        RecConfig cfg = configService.current();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(cfg.getRealtimeWindowMinutes());

        List<BehaviorLog> snapshot;
        synchronized (recentEvents) {
            snapshot = new ArrayList<>(recentEvents);
        }

        Map<String, RealtimeFeature> aggregated = new HashMap<>();
        for (BehaviorLog b : snapshot) {
            if (b.getTimestamp() == null || b.getTimestamp().isBefore(cutoff)) {
                continue;
            }
            boolean deep = isDeepInteraction(b.getType());

            if (b.getUserId() != null) {
                String userKey = "user:" + b.getUserId();
                RealtimeFeature u = aggregated.computeIfAbsent(userKey, k -> new RealtimeFeature(userKey, now));
                u.setExposeCount(u.getExposeCount() + 1);
                if (deep && b.getPostId() != null) {
                    u.setClickCount(u.getClickCount() + 1);
                    postRepository.findById(b.getPostId()).ifPresent(p -> {
                        if (p.getTopics() != null) {
                            for (String topic : p.getTopics()) {
                                u.addTopicClick(topic);
                            }
                        }
                    });
                }
            }
            if (b.getPostId() != null) {
                String postKey = "post:" + b.getPostId();
                RealtimeFeature p = aggregated.computeIfAbsent(postKey, k -> new RealtimeFeature(postKey, now));
                p.setExposeCount(p.getExposeCount() + 1);
                if (deep) {
                    p.setClickCount(p.getClickCount() + 1);
                }
            }
        }

        for (Map.Entry<String, RealtimeFeature> e : aggregated.entrySet()) {
            store.put(e.getKey(), e.getValue());
        }
    }

    /** 深度互动：点击/点赞/收藏/评论/转发 */
    private boolean isDeepInteraction(BehaviorType type) {
        return type == BehaviorType.CLICK || type == BehaviorType.LIKE || type == BehaviorType.FAVORITE
                || type == BehaviorType.COMMENT || type == BehaviorType.REPOST;
    }

    /** 定时 flush（生产形态由 Flink 窗口触发，此处用 Spring 定时模拟） */
    @Scheduled(fixedDelayString = "${app.rec.realtime-flush-ms:5000}")
    public void scheduledFlush() {
        flush();
    }

    /** 当前窗口事件数（测试/监控） */
    public int windowSize() {
        synchronized (recentEvents) {
            return recentEvents.size();
        }
    }
}
