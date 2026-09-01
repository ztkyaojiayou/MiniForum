package com.tkzou.miniforum.recommend.rerank.impl;
import com.tkzou.miniforum.recommend.rerank.RerankService;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 多样性重排（打散 + MMR）
 * <p>
 * <b>数据流程</b>：{@code List<RankedItem>（精排有序列表）} → 贪心选择 topN 个：
 * ①<b>硬约束打散</b>——同话题/类目连续条数不超过 categoryMaxCount（违反则跳过）；
 * ②<b>MMR</b>——每次从剩余候选中选 [λ·rankScore − (1−λ)·与已选集合最大相似度] 最大者，
 * 相似度只与最近 mmrWindow 条比较（滑动窗口）→ {@code List<RankedItem>} 最终下发列表，
 * 由 {@code RecommendService} 组装响应并记曝光。
 */
@Component
public class DiversifyRerankService implements RerankService {

    private final ItemFeatureService itemFeatureService;
    private final ConfigService configService;

    public DiversifyRerankService(ItemFeatureService itemFeatureService, ConfigService configService) {
        this.itemFeatureService = itemFeatureService;
        this.configService = configService;
    }

    @Override
    public List<RankedItem> rerank(RecommendContext ctx, List<RankedItem> ranked, int topN) {
        RecConfig cfg = configService.current();
        List<RankedItem> candidates = new ArrayList<>(ranked);
        List<RankedItem> selected = new ArrayList<>();
        int hardLimit = cfg.getCategoryMaxCount();
        double lambda = cfg.getMmrLambda();
        int window = cfg.getMmrWindow();

        while (!candidates.isEmpty() && selected.size() < topN) {
            RankedItem best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIndex = -1;

            for (int i = 0; i < candidates.size(); i++) {
                RankedItem cand = candidates.get(i);
                if (violatesHardLimit(cand, selected, hardLimit)) {
                    continue;
                }
                double maxSim = maxSimilarity(cand, selected, window);
                double mmr = lambda * cand.getRankScore() - (1 - lambda) * maxSim;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = cand;
                    bestIndex = i;
                }
            }
            if (best == null) {
                break; // 剩余候选全部触发硬约束（如同类耗尽），提前终止
            }
            selected.add(best);
            candidates.remove(bestIndex);
        }
        return selected;
    }

    /** 硬约束：若已选末尾连续 maxCount 条与候选同类/话题，则不允许加入 */
    private boolean violatesHardLimit(RankedItem cand, List<RankedItem> selected, int maxCount) {
        if (maxCount <= 1 || selected.isEmpty()) {
            return false;
        }
        String cat = categoryOf(cand);
        int consecutive = 0;
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (categoryOf(selected.get(i)).equals(cat)) {
                consecutive++;
            } else {
                break;
            }
        }
        return consecutive >= maxCount;
    }

    /** 与已选集合（滑动窗口内）的最大相似度，相似=同类目或共享话题 */
    private double maxSimilarity(RankedItem cand, List<RankedItem> selected, int window) {
        double max = 0;
        int start = Math.max(0, selected.size() - window);
        for (int i = start; i < selected.size(); i++) {
            if (similar(cand, selected.get(i))) {
                max = Math.max(max, 1.0);
            }
        }
        return max;
    }

    private boolean similar(RankedItem a, RankedItem b) {
        ItemFeature fa = itemFeatureService.itemFeature(a.getItemId());
        ItemFeature fb = itemFeatureService.itemFeature(b.getItemId());
        if (fa.getCategory() != null && fa.getCategory().equals(fb.getCategory())) {
            return true;
        }
        for (String t : fa.getTopics()) {
            if (fb.getTopics().contains(t)) {
                return true;
            }
        }
        return false;
    }

    private String categoryOf(RankedItem item) {
        String cat = itemFeatureService.itemFeature(item.getItemId()).getCategory();
        return cat == null || cat.isBlank() ? "其他" : cat;
    }
}
