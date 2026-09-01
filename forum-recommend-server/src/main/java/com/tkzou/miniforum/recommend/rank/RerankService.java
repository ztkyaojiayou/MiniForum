package com.tkzou.miniforum.recommend.rank;

import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;

import java.util.List;

/**
 * 重排服务接口（排序第三阶段）
 * <p>
 * 精排是 point-wise（逐条打分，看不到列表上下文），重排负责 list-wise 优化：
 * 去重、类目/话题打散、多样性（MMR）、业务规则、广告混排。
 */
public interface RerankService extends RankService {

    /** 对精排结果重排，返回最终下发的 TopN（可能少于输入） */
    List<RankedItem> rerank(RecommendContext ctx, List<RankedItem> ranked, int topN);
}
