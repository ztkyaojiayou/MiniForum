package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 新内容冷启动池（Thompson bandit 的"臂"集合）
 * <p>
 * 池内为冷启内容（新发布或互动过少）。每个 item 维护 [alpha, beta, 待惩罚曝光数]：
 * 深度互动 → α+=1；连续 EXPOSE_FAIL_THRESHOLD 次曝光无互动 → β+=1（"曝光无转化"惩罚）。
 * 探索分 = Thompson 采样（见 ColdStartService）。
 */
@Component
public class NewItemPool {

    /** 连续曝光多少次无互动记为一次负反馈 */
    private static final int EXPOSE_FAIL_THRESHOLD = 3;

    private final PostRepository postRepository;
    private final FeatureService featureService;
    private final Map<Long, double[]> alphaBeta = new ConcurrentHashMap<>();

    public NewItemPool(PostRepository postRepository, FeatureService featureService) {
        this.postRepository = postRepository;
        this.featureService = featureService;
    }

    /** 当前池内物品（冷启内容） */
    public List<Long> poolItems() {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .map(p -> featureService.itemFeature(p.getId()))
                .filter(ItemFeature::isInNewPool)
                .map(ItemFeature::getPostId)
                .collect(Collectors.toList());
    }

    /** Thompson 采样分 */
    public double sampleScore(long itemId) {
        double[] ab = alphaBeta.computeIfAbsent(itemId, k -> new double[]{1.0, 1.0, 0});
        return ThompsonBandit.sampleBeta(ab[0], ab[1]);
    }

    /** 期望互动率 α/(α+β) */
    public double expect(long itemId) {
        double[] ab = alphaBeta.computeIfAbsent(itemId, k -> new double[]{1.0, 1.0, 0});
        return ab[0] / (ab[0] + ab[1]);
    }

    /**
     * 反馈驱动后验更新
     *
     * @param success true=深度互动（α+1，清空待惩罚曝光）；false=一次曝光（累计阈值后 β+1）
     */
    public void recordOutcome(long itemId, boolean success) {
        double[] ab = alphaBeta.computeIfAbsent(itemId, k -> new double[]{1.0, 1.0, 0});
        synchronized (ab) {
            if (success) {
                ab[0] += 1;
                ab[2] = 0;
            } else {
                ab[2] += 1;
                if (ab[2] >= EXPOSE_FAIL_THRESHOLD) {
                    ab[1] += 1;
                    ab[2] = 0;
                }
            }
        }
    }

    /** 是否在池内（冷启内容） */
    public boolean contains(long itemId) {
        return alphaBeta.containsKey(itemId) || poolItems().contains(itemId);
    }
}
