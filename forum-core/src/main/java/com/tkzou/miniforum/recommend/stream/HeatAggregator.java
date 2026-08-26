package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热搜热度聚合器（订阅行为事件队列，消费者化）
 * <p>
 * <b>数据流程</b>：行为事件（VIEW/LIKE/COMMENT/REPOST/CLICK/FAVORITE）→ 回源帖子标签/话题 →
 * 增量累加热度（VIEW+1/LIKE+5/COMMENT+10/REPOST+8/CLICK+3/FAVORITE+5）与关联帖数。
 * 订阅 {@link BehaviorEventQueue}：@!prod 直接订阅，@prod 经 KafkaBehaviorConsumer 回灌队列后同样收到
 * ——一份代码 @!prod/@prod 通吃（热搜从读时全表扫改为事件驱动聚合）。
 * <p>
 * 热搜主榜仍保留原"帖子计数热度 + 搜索词"逻辑，本聚合器的行为热度作为<b>补充信号</b>并入（见 HotSearchService）。
 */
@Component
public class HeatAggregator {

    /** 标签/话题 → 热度统计（heat, postCount） */
    private final Map<String, HeatStat> agg = new ConcurrentHashMap<>();
    private final PostRepository postRepository;

    public HeatAggregator(BehaviorEventQueue eventQueue, PostRepository postRepository) {
        this.postRepository = postRepository;
        eventQueue.subscribe(this::onBehavior);
    }

    private void onBehavior(BehaviorLog b) {
        if (b.getPostId() == null) {
            return;
        }
        double weight = heatWeight(b.getType());
        if (weight <= 0) {
            return;
        }
        postRepository.findById(b.getPostId()).ifPresent(p -> {
            if (!isVisible(p)) {
                return;
            }
            if (p.getTags() != null) {
                for (String tag : p.getTags()) {
                    if (tag != null && !tag.isBlank()) {
                        accumulate(tag, weight, p.getId());
                    }
                }
            }
            if (p.getTopics() != null) {
                for (String topic : p.getTopics()) {
                    if (topic != null && !topic.isBlank()) {
                        accumulate(topic, weight, p.getId());
                    }
                }
            }
        });
    }

    private void accumulate(String key, double heat, Long postId) {
        agg.compute(key, (k, cur) -> {
            HeatStat s = cur == null ? new HeatStat() : cur;
            s.heat += heat;
            if (s.postIds.add(postId)) {
                s.postCount++;
            }
            return s;
        });
    }

    /** 快照：标签/话题 → [累计热度, 关联帖数]（供热搜榜并入） */
    public Map<String, double[]> snapshot() {
        Map<String, double[]> out = new ConcurrentHashMap<>();
        agg.forEach((k, s) -> out.put(k, new double[]{s.heat, s.postCount}));
        return out;
    }

    private double heatWeight(BehaviorType type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case VIEW:
                return 1;
            case CLICK:
                return 3;
            case LIKE:
            case FAVORITE:
                return 5;
            case REPOST:
                return 8;
            case COMMENT:
                return 10;
            default:
                return 0;
        }
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }

    private static final class HeatStat {
        double heat;
        int postCount;
        final Set<Long> postIds = ConcurrentHashMap.newKeySet();
    }
}
