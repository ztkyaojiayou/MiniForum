package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户画像聚合器
 * <p>
 * <b>数据流程</b>：BehaviorLogRepository.findByUserId(uid)（统一行为时间线）→ 逐条按行为权重
 * （微博：转发3/评论2/收藏1.5/点赞1/点击1/搜索0.5/浏览0.02）× 兴趣时间衰减(0.7^天数) → 按帖子话题/类目累加
 * → 归一化 → {@link UserProfile}（topicWeight/categoryWeight/最近交互序列/活跃度），
 * 供召回（topic/category/itemcf）与排序（interest）消费。
 */
@Component
public class UserProfileAggregator {

    /** 兴趣时间衰减：每过一天权重衰减为前一天的 0.7 倍（近 7 天衰减到约 8%） */
    private static final double INTEREST_DECAY_PER_DAY = 0.7;
    /** 最近交互序列保留上限 */
    private static final int MAX_RECENT_ITEMS = 50;

    private final BehaviorLogRepository behaviorLogRepository;
    private final PostRepository postRepository;

    public UserProfileAggregator(BehaviorLogRepository behaviorLogRepository,
                                 PostRepository postRepository) {
        this.behaviorLogRepository = behaviorLogRepository;
        this.postRepository = postRepository;
    }

    /** 构建用户画像 */
    public UserProfile build(Long userId) {
        List<BehaviorLog> behaviors = behaviorLogRepository.findByUserId(userId);
        Map<String, Double> topicWeight = new HashMap<>();
        Map<String, Double> categoryWeight = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (BehaviorLog b : behaviors) {
            if (b.getPostId() == null) {
                continue;
            }
            double w = weightOf(b.getType());
            if (w <= 0) {
                continue;
            }
            Post post = postRepository.findById(b.getPostId()).orElse(null);
            if (post == null || !Post.STATUS_PUBLISHED.equals(post.getStatus()) || post.isDeleted()) {
                continue;
            }
            double effective = w * interestDecay(b.getTimestamp(), now);
            if (post.getTopics() != null) {
                for (String topic : post.getTopics()) {
                    topicWeight.merge(topic, effective, Double::sum);
                }
            }
            String cat = post.getCategory() == null || post.getCategory().isBlank() ? "其他" : post.getCategory();
            categoryWeight.merge(cat, effective, Double::sum);
        }

        normalize(topicWeight);
        normalize(categoryWeight);

        // 最近交互序列：去重、最近在前（行为时间升序，从后往前收集）
        List<Long> recent = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (int i = behaviors.size() - 1; i >= 0; i--) {
            BehaviorLog b = behaviors.get(i);
            if (b.getPostId() == null) {
                continue;
            }
            if (seen.add(b.getPostId())) {
                recent.add(b.getPostId());
            }
            if (recent.size() >= MAX_RECENT_ITEMS) {
                break;
            }
        }

        double activeLevel = Math.log1p(behaviors.size());
        return new UserProfile(userId, topicWeight, categoryWeight, recent, behaviors.size(), activeLevel);
    }

    /** 行为信号权重（微博场景：转发最高、浏览最低） */
    private double weightOf(BehaviorType type) {
        switch (type) {
            case REPOST:
                return 3.0;
            case COMMENT:
                return 2.0;
            case FAVORITE:
                return 1.5;
            case LIKE:
                return 1.0;
            case CLICK:
                return 1.0;
            case SEARCH:
                return 0.5;
            case VIEW:
                return 0.02;
            default:
                return 0;
        }
    }

    /** 兴趣时间衰减：0.7^(天数) */
    private double interestDecay(LocalDateTime ts, LocalDateTime now) {
        if (ts == null) {
            return 0;
        }
        double days = Duration.between(ts, now).toMinutes() / 1440.0;
        return Math.pow(INTEREST_DECAY_PER_DAY, Math.max(0, days));
    }

    /** 归一化：使所有值之和为 1（空 map 保持空） */
    private void normalize(Map<String, Double> map) {
        if (map.isEmpty()) {
            return;
        }
        double sum = map.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return;
        }
        map.replaceAll((k, v) -> v / sum);
    }
}
