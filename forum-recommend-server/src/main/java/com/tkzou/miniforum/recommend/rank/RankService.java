package com.tkzou.miniforum.recommend.rank;

/**
 * 排序服务（顶级接口：粗排 / 精排 / 重排 三阶段的统一抽象）
 * <p>
 * 推荐漏斗里"排序"不是一个方法，而是三个串行的阶段，各有独立契约与实现：
 * <ul>
 *   <li>{@link CoarseRankService} 粗排：把融合候选从"千"缩到"百"（控精排算力）；</li>
 *   <li>{@link FineRankService} 精排：对缩后的候选逐条打分排序（point-wise）；</li>
 *   <li>{@link RerankService} 重排：对精排结果做 list-wise 打散 + 多样性（MMR）。</li>
 * </ul>
 * 三个子接口继承本接口，表达"它们都是排序阶段"；实现类各自实现自己的阶段契约
 * （{@code RuleCoarseRankService} / {@code RuleFineRankService} / {@code DiversifyRerankService}）。
 * 生产形态：精排可为 LR/双塔精排（Python 训练 ONNX，Java 推理），本项目弱训练侧默认规则加权。
 */
public interface RankService {
}
