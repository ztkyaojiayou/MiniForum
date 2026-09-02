package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多路召回融合器
 * <p>
 * <b>数据流程</b>：{@code List<RecallHit>（各路通道原始命中）} → 按通道分组 → 通道内按分降序做
 * <b>rank-based 归一化</b>（norm = 1/(rankInChannel + 60)，比 min-max 更稳健）→ 按 itemId 聚合各路归一化分
 * → 按通道权重加权求和（{@code RecConfig.channelWeight}）→ 去重 + 截断 → {@code List<Candidate>} 供排序层。
 * Candidate 保留 channelScores 构成，用于推荐理由与可解释。
 */
@Component
public class MergeRecallService {

    /**
     * rank 归一化分母偏移（+60 = 融合的"形状旋钮"）：
     * 让第 1 名只比第 2 名高 ~2%（无偏移时是 2 倍）——压平顶部陡坡，避免单路"第一名"碾压一切；
     * 越大曲线越平（名次越不重要）、越小越看重头部。这是跨通道分数可比的根基（见 {@link #merge}）。
     */
    private static final int RANK_OFFSET = 60;

    /**
     * 多路融合——推荐漏斗核心之一（"别漏"的收口）：把 6 路 RecallHit 融成去重后的候选集。
     * <p>
     * <b>为什么用 rank 归一化而不是直接加各路分</b>：每路打分的"尺子"不同（Hot 热度 8.0 / Topic 兴趣 0.8 /
     * ItemCF 相似 0.6），直接相加会让数值大的通道淹没一切。能跨通道比的只有<b>通道内排名</b>——每路的
     * "第 1 名"都是"该角度下最相关"，同一含义。故只取名次：norm = 1/(rankInChannel + {@link #RANK_OFFSET})。
     * <p>
     * <b>跨路累加 = 被多路命中的帖子加分</b>：同一 itemId 被 topic + follow 都命中时两份 norm 相加——
     * 多路都认为相关 = 信号互相印证 = 更可信，这正是"多路召回"的价值。
     */
    public List<Candidate> merge(List<RecallHit> hits, RecConfig cfg, int targetSize) {
        // 1. 按通道分组
        Map<String, List<RecallHit>> byChannel = hits.stream()
                .collect(Collectors.groupingBy(RecallHit::getSource));
        // 2. 通道内按分降序，边算 rank 归一化边聚合——一步完成，无需"以业务对象为 key 的临时表"
        //    （RecallHit 未重写 equals/hashCode、靠对象同一性；若另建 side-map 依赖"put/get 同引用"，重构易踩坑）
        //    归一化 norm = 1/(rank+60) 抹平量纲只留顺序：第 1 名→1/61≈0.016，第 100 名→1/160≈0.006
        Map<Long, Map<String, Double>> byItem = new LinkedHashMap<>();
        for (List<RecallHit> channelHits : byChannel.values()) {
            channelHits.sort(Comparator.comparingDouble(RecallHit::getScore).reversed());
            for (int i = 0; i < channelHits.size(); i++) {
                RecallHit hit = channelHits.get(i);
                // 每路内部 itemId 唯一 → (itemId, source) 是稳定键：同一帖被多路命中时各通道各记一份贡献
                byItem.computeIfAbsent(hit.getItemId(), k -> new HashMap<>())
                        .put(hit.getSource(), 1.0 / (i + 1 + RANK_OFFSET));
            }
        }

        // 3. 通道加权求和：mergeScore = Σ 通道权重 × 归一化分（hot 1.0 / topic 1.0 / itemcf 1.2 / follow 0.8...）
        //    被多路命中的帖子各份 norm 累加 → 融合分更高（多路印证 = 真相关）
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : byItem.entrySet()) {
            double mergeScore = 0;
            for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                mergeScore += cfg.channelWeightOf(c.getKey()) * c.getValue();
            }
            candidates.add(new Candidate(e.getKey(), e.getValue(), mergeScore));
        }

        // 4. 融合分降序 + 截断到 targetSize（mergeTopN=200）：6 路最多 600 毛命中 → 精筛 ≤200 进排序
        candidates.sort(Comparator.comparingDouble(Candidate::getMergeScore).reversed());
        if (candidates.size() > targetSize) {
            return new ArrayList<>(candidates.subList(0, targetSize));
        }
        return candidates;
    }
}
