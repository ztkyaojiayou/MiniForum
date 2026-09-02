package com.tkzou.miniforum.recommend.rank.impl;
import com.tkzou.miniforum.recommend.rank.RerankService;

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

    /**
     * 多样性重排（贪心选 topN，第 11 章）：精排的有序列表 → 逐轮把"MMR 分最高"的候选加入最终下发列表。
     * <p>
     * <b>MMR 目标</b>：单条相关性与整体多样性的权衡——
     * <pre>mmr = λ·rankScore − (1−λ)·maxSim(候选, 已选集合)</pre>
     * - λ = mmrLambda（默认 0.6）：越接近 1 越看重单条相关（牺牲多样性），越接近 0 越强调"和已选不同"；
     * - 每轮<b>全局扫一遍剩余候选</b>取 mmr 最大者 → 贪心（非全局最优，但 O(n·topN) 快且够用）。
     * <p>
     * <b>两道约束共同生效</b>：
     * ① 硬约束打散：同话题/类目<b>连续</b>条数 ≤ categoryMaxCount（违反直接跳过——保证头几条不全是同一类）；
     * ② 软约束 MMR：用"与已选相似度"压低同质项——只与最近 mmrWindow 条比较（滑动窗口，省算力）。
     * 每轮 best==null（剩余候选全触发硬约束，如同类耗尽）→ 提前终止。
     */
    @Override
    public List<RankedItem> rerank(RecommendContext ctx, List<RankedItem> ranked, int topN) {
        RecConfig cfg = configService.current();
        List<RankedItem> candidates = new ArrayList<>(ranked);   // 剩余可选池（精排有序，会被逐一挑走）
        List<RankedItem> selected = new ArrayList<>();           // 已选最终列表（贪心结果）
        int hardLimit = cfg.getCategoryMaxCount();               // 打散硬约束：同类连续上限（默认 2）
        double lambda = cfg.getMmrLambda();                      // MMR 权衡系数：相关 vs 多样（默认 0.6）
        int window = cfg.getMmrWindow();                         // 相似度只与最近 window 条已选比较（默认 10）

        // 贪心主循环：每轮从剩余候选挑"MMR 分最高"者，直到凑满 topN 或无可选
        while (!candidates.isEmpty() && selected.size() < topN) {
            RankedItem best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIndex = -1;

            // 内层扫描：跳过触发硬约束的（同类连续超限），否则算 MMR 并记本轮最优
            for (int i = 0; i < candidates.size(); i++) {
                RankedItem cand = candidates.get(i);
                if (violatesHardLimit(cand, selected, hardLimit)) {
                    continue;                                    // 硬约束：同类连到上限 → 这轮先不选它
                }
                double maxSim = maxSimilarity(cand, selected, window);   // 与已选的最大相似度（窗口内）
                double mmr = lambda * cand.getRankScore() - (1 - lambda) * maxSim;  // ★ MMR：相关减"与已选太像"的惩罚
                if (mmr > bestMmr) {                             // 本轮扫完全部，留 mmr 最高者
                    bestMmr = mmr;
                    best = cand;
                    bestIndex = i;
                }
            }
            if (best == null) {
                break; // 剩余候选全部触发硬约束（如同类耗尽），提前终止——避免死循环
            }
            selected.add(best);          // 选走本轮最优
            candidates.remove(bestIndex); // 从可选池移除
        }
        return selected;   // 最终下发列表（≤ topN，顺序 = 贪心选取顺序）
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
