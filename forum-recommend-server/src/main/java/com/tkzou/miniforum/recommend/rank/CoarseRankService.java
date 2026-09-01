package com.tkzou.miniforum.recommend.rank;

import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecommendContext;

import java.util.List;

/**
 * 粗排服务接口（排序第一阶段）
 * <p>
 * 大厂流水线：召回 → <b>粗排</b>（轻模型把千级缩到百级，控精排算力）→ 精排（重模型）→ 重排。
 * 本接口把"排序"这个架构环节留在链路上：默认实现 {@code RuleCoarseRankService} 用零算力的融合分截断
 * （简化版，coarseTopN=200 即透传）；将来候选上量 / 精排换深度模型时，只换此接口的实现，链路不缺这一环。
 */
public interface CoarseRankService extends RankService {

    /** 粗排：从融合候选里筛出一批给精排打分（返回 ≤ coarseTopN 条，保持 mergeScore 降序） */
    List<Candidate> coarseRank(RecommendContext ctx, List<Candidate> candidates);
}
