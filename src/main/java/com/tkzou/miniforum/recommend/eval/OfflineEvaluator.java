package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.model.ItemCfBuilder;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 离线评估器
 * <p>
 * 以行为日志做时间切分：训练集构建 ItemCF + 热门信号，为测试用户生成 TopK 排序，
 * 对比测试集真实互动计算 AUC/GAUC/Recall@K/NDCG@K/Coverage/Diversity/Freshness。
 * 离线只做初筛，最终以线上 AB 为准（调研铁律）。
 */
@Component
public class OfflineEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvaluator.class);

    private final BehaviorLogRepository behaviorLogRepository;
    private final PostRepository postRepository;
    private final FeatureService featureService;
    private final ConfigService configService;

    public OfflineEvaluator(BehaviorLogRepository behaviorLogRepository,
                            PostRepository postRepository,
                            FeatureService featureService,
                            ConfigService configService) {
        this.behaviorLogRepository = behaviorLogRepository;
        this.postRepository = postRepository;
        this.featureService = featureService;
        this.configService = configService;
    }

    public Metrics evaluate(double trainRatio, int topK, int maxUsers) {
        // 离线评估只处理"反馈信号"行为（曝光/负反馈不计入 train/test）
        List<BehaviorLog> signalBehaviors = behaviorLogRepository.findAll().stream()
                .filter(OfflineEvaluator::isFeedbackSignal)
                .collect(Collectors.toList());
        TrainTestSplit split = TimeSplitter.splitByTime(signalBehaviors, trainRatio);
        ItemCfModel model = new ItemCfBuilder().build(split.train(), 50);
        LocalDateTime now = LocalDateTime.now();
        double halfLife = configService.current().getHalfLifeHours();

        Map<Long, List<BehaviorLog>> trainByUser = split.train().stream()
                .collect(Collectors.groupingBy(BehaviorLog::getUserId));
        Map<Long, Set<Long>> testRelevant = new HashMap<>();
        for (BehaviorLog b : split.test()) {
            if (b.getPostId() != null && isDeep(b.getType())) {
                testRelevant.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(b.getPostId());
            }
        }

        List<Double> allScores = new ArrayList<>();
        List<Integer> allLabels = new ArrayList<>();
        Map<Long, List<MetricCalculator.LabeledScore>> byUser = new HashMap<>();
        double sumRecall = 0;
        double sumNdcg = 0;
        int evaluated = 0;
        Set<Long> recommendedItems = new HashSet<>();
        List<Double> listDiversities = new ArrayList<>();
        List<Double> freshnessList = new ArrayList<>();

        for (Map.Entry<Long, List<BehaviorLog>> e : trainByUser.entrySet()) {
            if (evaluated >= maxUsers) {
                break;
            }
            Long userId = e.getKey();
            Set<Long> relevant = testRelevant.get(userId);
            if (relevant == null || relevant.isEmpty()) {
                continue;
            }
            List<Long> history = e.getValue().stream()
                    .map(BehaviorLog::getPostId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // 候选打分：ItemCF 相似度 + 热门兜底
            Map<Long, Double> scores = new HashMap<>();
            for (Long h : history) {
                for (ItemCfModel.SimilarItem s : model.topSimilar(h, 30)) {
                    if (history.contains(s.itemId())) {
                        continue;
                    }
                    scores.merge(s.itemId(), s.similarity(), Double::sum);
                }
            }
            for (Post p : visiblePosts()) {
                scores.merge(p.getId(),
                        0.05 * Math.log1p(featureService.itemFeature(p.getId()).getHotScore()), Double::sum);
            }

            List<Long> ranking = scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (ranking.isEmpty()) {
                continue;
            }

            sumRecall += MetricCalculator.recallAtK(relevant, ranking, topK);
            sumNdcg += MetricCalculator.ndcgAtK(relevant, ranking, topK);
            recommendedItems.addAll(ranking);

            for (Map.Entry<Long, Double> sc : scores.entrySet()) {
                if (ranking.contains(sc.getKey())) {
                    int label = relevant.contains(sc.getKey()) ? 1 : 0;
                    allScores.add(sc.getValue());
                    allLabels.add(label);
                    byUser.computeIfAbsent(userId, k -> new ArrayList<>())
                            .add(new MetricCalculator.LabeledScore(sc.getValue(), label));
                }
            }

            Set<String> cats = new HashSet<>();
            for (Long id : ranking) {
                cats.add(categoryOf(id));
            }
            listDiversities.add((double) cats.size() / Math.max(1, ranking.size()));
            for (Long id : ranking) {
                freshnessList.add(freshnessOf(id, now, halfLife));
            }
            evaluated++;
        }

        Metrics m = new Metrics();
        m.setEvaluatedUsers(evaluated);
        m.setTopK(topK);
        m.setAuc(evaluated > 0 ? MetricCalculator.auc(allScores, allLabels) : 0.5);
        m.setGauc(evaluated > 0 ? MetricCalculator.gauc(byUser) : 0.5);
        m.setRecallAtK(evaluated > 0 ? sumRecall / evaluated : 0);
        m.setNdcgAtK(evaluated > 0 ? sumNdcg / evaluated : 0);
        int totalVisible = visiblePosts().size();
        m.setCoverage(totalVisible > 0 ? (double) recommendedItems.size() / totalVisible : 0);
        m.setDiversity(listDiversities.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        m.setFreshness(freshnessList.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        log.info("离线评估完成：用户={}, AUC={}, GAUC={}, Recall@{}={}", evaluated, m.getAuc(), m.getGauc(), topK, m.getRecallAtK());
        return m;
    }

    private boolean isDeep(BehaviorType type) {
        return type == BehaviorType.REPOST || type == BehaviorType.COMMENT || type == BehaviorType.FAVORITE
                || type == BehaviorType.LIKE || type == BehaviorType.CLICK;
    }

    /** 是否反馈信号（曝光/负反馈/取消类不算，避免污染 train/test） */
    private static boolean isFeedbackSignal(BehaviorLog b) {
        return b.getType() != BehaviorType.EXPOSE
                && b.getType() != BehaviorType.DISLIKE
                && b.getType() != BehaviorType.UNLIKE
                && b.getType() != BehaviorType.UNFAVORITE
                && b.getType() != BehaviorType.UNFOLLOW;
    }

    private List<Post> visiblePosts() {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .collect(Collectors.toList());
    }

    private String categoryOf(Long postId) {
        String cat = featureService.itemFeature(postId).getCategory();
        return cat == null || cat.isBlank() ? "其他" : cat;
    }

    private double freshnessOf(Long postId, LocalDateTime now, double halfLife) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getCreatedAt() == null) {
            return 0;
        }
        double ageHours = Duration.between(post.getCreatedAt(), now).toMinutes() / 60.0;
        return Math.exp(-Math.log(2) * Math.max(0, ageHours) / halfLife);
    }
}
