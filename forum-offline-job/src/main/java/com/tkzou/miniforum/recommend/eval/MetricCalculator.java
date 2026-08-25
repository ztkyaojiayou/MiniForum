package com.tkzou.miniforum.recommend.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 离线评估指标计算器
 * <p>
 * AUC / GAUC / Recall@K / NDCG@K，均为推荐系统标准定义（见 docs/推荐系统设计方案.md 第七节）。
 */
public class MetricCalculator {

    /** 带标签的排序得分（用于 AUC/GAUC） */
    public record LabeledScore(double score, int label) {
    }

    private MetricCalculator() {
    }

    /** AUC：正样本得分 > 负样本得分的概率。按 score 升序排秩（1=最低分），高分正样本得高秩 */
    public static double auc(List<Double> scores, List<Integer> labels) {
        if (scores.size() != labels.size() || scores.isEmpty()) {
            return 0.5;
        }
        List<LabeledScore> pairs = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            pairs.add(new LabeledScore(scores.get(i), labels.get(i)));
        }
        pairs.sort(Comparator.comparingDouble(LabeledScore::score));

        int posCount = (int) labels.stream().filter(l -> l == 1).count();
        int negCount = labels.size() - posCount;
        if (posCount == 0 || negCount == 0) {
            return 0.5;
        }
        long rankSum = 0;
        for (int i = 0; i < pairs.size(); i++) {
            if (pairs.get(i).label() == 1) {
                rankSum += (i + 1); // 升序排名，1 = 最低分
            }
        }
        return (rankSum - (long) posCount * (posCount + 1) / 2.0) / ((double) posCount * negCount);
    }

    /** GAUC：按用户分组的 AUC 加权平均，权重 = 该用户样本数（阿里 DIN 提出） */
    public static double gauc(Map<Long, List<LabeledScore>> byUser) {
        double weightedSum = 0;
        long totalSamples = 0;
        for (Map.Entry<Long, List<LabeledScore>> e : byUser.entrySet()) {
            List<LabeledScore> list = e.getValue();
            List<Double> scores = new ArrayList<>();
            List<Integer> labels = new ArrayList<>();
            for (LabeledScore s : list) {
                scores.add(s.score());
                labels.add(s.label());
            }
            double userAuc = auc(scores, labels);
            weightedSum += userAuc * list.size();
            totalSamples += list.size();
        }
        return totalSamples == 0 ? 0.5 : weightedSum / totalSamples;
    }

    /** Recall@K：TopK 中命中相关物品的比例 */
    public static double recallAtK(Set<Long> relevant, List<Long> ranking, int k) {
        if (relevant.isEmpty()) {
            return 0;
        }
        int hit = 0;
        int limit = Math.min(k, ranking.size());
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(ranking.get(i))) {
                hit++;
            }
        }
        return (double) hit / relevant.size();
    }

    /** NDCG@K：位置加权的相关性（命中=1），按理想排序归一化 */
    public static double ndcgAtK(Set<Long> relevant, List<Long> ranking, int k) {
        if (relevant.isEmpty()) {
            return 0;
        }
        double dcg = 0;
        int limit = Math.min(k, ranking.size());
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(ranking.get(i))) {
                dcg += 1.0 / log2(i + 2);
            }
        }
        int idealCount = Math.min(k, relevant.size());
        double idcg = 0;
        for (int i = 0; i < idealCount; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
