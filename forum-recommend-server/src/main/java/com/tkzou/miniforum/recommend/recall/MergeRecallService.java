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

    /** rank 归一化分母偏移（避免第一名的 1/(0+60) 与第二名差异过大） */
    private static final int RANK_OFFSET = 60;

    public List<Candidate> merge(List<RecallHit> hits, RecConfig cfg, int targetSize) {
        // 1. 按通道分组，通道内按分降序，计算 rank 归一化分
        Map<String, List<RecallHit>> byChannel = hits.stream()
                .collect(Collectors.groupingBy(RecallHit::getSource));
        Map<RecallHit, Double> normalized = new HashMap<>();
        for (List<RecallHit> channelHits : byChannel.values()) {
            channelHits.sort(Comparator.comparingDouble(RecallHit::getScore).reversed());
            for (int i = 0; i < channelHits.size(); i++) {
                normalized.put(channelHits.get(i), 1.0 / (i + 1 + RANK_OFFSET));
            }
        }

        // 2. 按 itemId 聚合各路得分
        Map<Long, Map<String, Double>> byItem = new LinkedHashMap<>();
        for (RecallHit hit : hits) {
            Double norm = normalized.get(hit);
            if (norm == null) {
                continue;
            }
            byItem.computeIfAbsent(hit.getItemId(), k -> new HashMap<>()).put(hit.getSource(), norm);
        }

        // 3. 加权融合分
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : byItem.entrySet()) {
            double mergeScore = 0;
            for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                mergeScore += cfg.channelWeightOf(c.getKey()) * c.getValue();
            }
            candidates.add(new Candidate(e.getKey(), e.getValue(), mergeScore));
        }

        candidates.sort(Comparator.comparingDouble(Candidate::getMergeScore).reversed());
        if (candidates.size() > targetSize) {
            return new ArrayList<>(candidates.subList(0, targetSize));
        }
        return candidates;
    }
}
