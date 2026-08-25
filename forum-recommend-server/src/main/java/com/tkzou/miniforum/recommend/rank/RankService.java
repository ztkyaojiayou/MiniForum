package com.tkzou.miniforum.recommend.rank;

import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;

import java.util.List;

/**
 * 排序服务接口
 * <p>
 * 对融合后的候选集打分排序。生产形态可为 LR/双塔精排（Python 训练 ONNX，Java 推理），
 * 本项目弱训练侧默认使用规则加权排序（微博式 rankScore）。
 */
public interface RankService {

    /** 输入融合候选，输出按 rankScore 降序的有序列表（携带特征分构成与推荐理由） */
    List<RankedItem> rank(RecommendContext ctx, List<Candidate> candidates);
}
