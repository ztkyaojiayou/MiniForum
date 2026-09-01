package com.tkzou.miniforum.recommend.rank;

import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;

import java.util.List;

/**
 * 精排服务接口（排序第二阶段）
 * <p>
 * 对粗排缩后的候选集逐条打分排序（point-wise，看不到列表上下文）。
 * 生产形态可为 LR/双塔精排（Python 训练 ONNX，Java 推理），本项目弱训练侧默认
 * 规则加权排序（微博式 rankScore，实现见 {@code RuleFineRankService}）。
 */
public interface FineRankService extends RankService {

    /** 输入融合候选，输出按 rankScore 降序的有序列表（携带特征分构成与推荐理由） */
    List<RankedItem> rank(RecommendContext ctx, List<Candidate> candidates);
}
