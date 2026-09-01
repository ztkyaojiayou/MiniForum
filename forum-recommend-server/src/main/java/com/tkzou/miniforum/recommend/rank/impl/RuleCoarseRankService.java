package com.tkzou.miniforum.recommend.rank.impl;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.rank.CoarseRankService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 粗排规则默认实现（简化版）
 * <p>
 * 用<b>召回融合分 mergeScore</b>（融合阶段已算好，零额外算力）把候选截断到 {@code coarseTopN}。
 * 大厂粗排 = 轻模型把"千级缩到百级"控精排算力；本项目候选量小（融合后 ≤ mergeTopN=200），
 * 默认 {@code coarseTopN=200} 即"透传 + 保序"——链路结构完整但不过度计算。
 * 将来候选上量 / 精排换深度模型时：调低 {@code app.rec.coarse-top-n}，或替换本实现（LR/浅层模型），接口不变。
 */
@Component
public class RuleCoarseRankService implements CoarseRankService {

    private final ConfigService configService;

    public RuleCoarseRankService(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<Candidate> coarseRank(RecommendContext ctx, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        RecConfig cfg = configService.current();
        int coarseTopN = cfg.getCoarseTopN();
        // 融合已按 mergeScore 降序；这里再显式保序（幂等）+ 上限截断——简化粗排的唯一动作
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::getMergeScore).reversed())
                .limit(Math.max(1, coarseTopN))
                .collect(Collectors.toList());
    }
}
