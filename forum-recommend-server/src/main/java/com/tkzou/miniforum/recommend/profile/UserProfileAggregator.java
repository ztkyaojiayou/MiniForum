package com.tkzou.miniforum.recommend.profile;

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

    /**
     * 构建用户画像（画像域核心）：把行为日志中"每条行为"折算成一份兴趣贡献分，按帖子话题/类目摊进桶、累加、归一化。
     * <p>
     * <b>一条行为 → 一份贡献分 effective</b>：
     * <pre>effective = 行为权重(type) × 时长系数(DWELL) × 时间衰减(0.7^天数)</pre>
     * - 行为权重：转发3 > 评论2 > 收藏1.5 > 赞1=点击1 > 搜索0.5 > 浏览0.02（互动越深越说明"喜欢"）；
     * - 时间衰减：老行为权重指数下降——画像反映"最近"兴趣，不是一辈子总和；
     * - 时长系数：DWELL 停留越久兴趣越强（30s 封顶 3 倍，仿抖音观看时长）。
     * <p>
     * 然后把 effective "摊"进该帖子的每个话题/类目桶（merge 累加）→ 归一化(Σ=1) 变"兴趣占比"，供召回与排序消费。
     */
    public UserProfile build(Long userId) {
        List<BehaviorLog> behaviors = behaviorLogRepository.findByUserId(userId);
        Map<String, Double> topicWeight = new HashMap<>();
        Map<String, Double> categoryWeight = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (BehaviorLog b : behaviors) {
            if (b.getPostId() == null) {
                continue;
            }
            double w = weightOf(b.getType());   // ① 行为基础权重（转发3/评论2/…/浏览0.02；曝光/取关=0）
            if (w <= 0) {
                continue;                       //    权重 0（EXPOSE/UNLIKE/UNFOLLOW 等非"正兴趣"行为）直接跳过
            }
            // ② 阅读停留（DWELL）时长加成：权重 × 时长系数，停留越久兴趣越强（30s 封顶 3 倍；仿抖音"观看时长"）
            if (b.getType() == BehaviorType.DWELL && b.getDurationSec() != null) {
                w *= Math.min(1 + b.getDurationSec() / 10.0, 3.0);
            }
            Post post = postRepository.findById(b.getPostId()).orElse(null);
            if (post == null || !Post.STATUS_PUBLISHED.equals(post.getStatus()) || post.isDeleted()) {
                continue;                       //    帖子没了/不可见 → 行为无意义，跳过
            }
            double effective = w * interestDecay(b.getTimestamp(), now);   // ③ 实际贡献分 = 权重×时长 × 时间衰减(0.7^天)
            if (post.getTopics() != null) {
                for (String topic : post.getTopics()) {
                    // ④ 把这份贡献"摊"进帖子的每个话题桶：merge(topic, effective, Double::sum)
                    //    = topicWeight[topic] += effective（话题缺席即置为 effective）
                    //    同话题被多帖多次行为命中 → 累加 → 归一化后即"我对该话题的兴趣占比"
                    topicWeight.merge(topic, effective, Double::sum);
                }
            }
            String cat = post.getCategory() == null || post.getCategory().isBlank() ? "其他" : post.getCategory();
            categoryWeight.merge(cat, effective, Double::sum);              // 同上，按类目摊
        }

        normalize(topicWeight);     // ⑤ 归一化(Σ=1)：兴趣从"绝对累加分"变"相对占比"——跨用户可比，
        normalize(categoryWeight);  //    防行为多的用户兴趣虚高；空/全 0 保持不动

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

        // 活跃度 = log1p(行为总数) = ln(1+n)：把"行为条数"（长尾，0~几十万）压成 0~12 的连续活跃度，
        // 使"重度用户 10 万条"与"普通用户 100 条"的差距从 1000 倍缩到 2.5 倍（log 压缩长尾，同 hotScore 套路）。
        // log1p 而非 log：0 行为时 log1p(0)=0（log(0)=-∞ 会炸）。⚠ 当前未被消费——画像里"算好备用"，
        // 供"活跃度"作排序/AB 信号扩展；真正生效的是上面 raw 行为数驱动的 isCold 冷启动判定（UserProfile）。
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
            case DWELL:
                return 0.05; // 基础权重，时长系数在 build 中叠加
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
