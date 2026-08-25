package com.tkzou.miniforum.recommend.rank;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.model.ItemCfScorer;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规则加权排序（弱训练侧默认排序器）
 * <p>
 * <b>数据流程</b>：{@code List<Candidate>（融合候选）} → 对每个候选计算特征分构成 featureScores
 * → 加权求和 rankScore = (Σ w_f·f + explore) × recency → 排序 → {@code List<RankedItem>}（携带特征分+召回路来源+推荐理由），
 * 供重排层 {@code DiversifyRerankService} 消费。
 * <p>
 * 特征：interact(互动热度) / quality(互动率) / interest(兴趣匹配) / social(关注关系) /
 *       author(作者权重) / hot(热点) / realtime(实时特征)；权重来自 RecConfig.rankWeight，
 * 微博场景方向性经验值（见 docs/微博推荐调研.md）。
 */
@Component
public class RuleRankService implements RankService {

    private final FeatureService featureService;
    private final ItemCfModelStore itemCfModelStore;
    private final ItemCfScorer itemCfScorer;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final ConfigService configService;
    private final ExploreProvider exploreProvider;
    private final TrafficPool trafficPool;

    public RuleRankService(FeatureService featureService,
                           ItemCfModelStore itemCfModelStore,
                           ItemCfScorer itemCfScorer,
                           FollowRepository followRepository,
                           PostRepository postRepository,
                           ConfigService configService,
                           ExploreProvider exploreProvider,
                           TrafficPool trafficPool) {
        this.featureService = featureService;
        this.itemCfModelStore = itemCfModelStore;
        this.itemCfScorer = itemCfScorer;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.configService = configService;
        this.exploreProvider = exploreProvider;
        this.trafficPool = trafficPool;
    }

    @Override
    public List<RankedItem> rank(RecommendContext ctx, List<Candidate> candidates) {
        RecConfig cfg = configService.current();
        UserProfile profile = featureService.userProfile(ctx.getUserId());
        List<Long> history = profile.getRecentItemIds();
        ItemCfModel model = itemCfModelStore.get();
        Set<Long> followedRepostedIds = computeFollowedRepostedIds(ctx.getUserId());

        // 候选内最大互动热度（用于 hot 特征归一化）
        double maxInteract = 1;
        for (Candidate c : candidates) {
            maxInteract = Math.max(maxInteract, Math.log1p(featureService.itemFeature(c.getItemId()).getHotScore()));
        }

        Map<Long, Double> itemCfCache = new HashMap<>();
        List<RankedItem> ranked = new ArrayList<>();
        for (Candidate c : candidates) {
            ItemFeature f = featureService.itemFeature(c.getItemId());
            Map<String, Double> feats = new LinkedHashMap<>();

            double interact = Math.log1p(f.getHotScore());
            double quality = quality(f);
            double interest = interest(profile, f, model, history, itemCfCache);
            double social = social(ctx.getUserId(), f, followedRepostedIds);
            double author = f.getAuthorFollowers();
            double hot = interact / Math.max(1, maxInteract);
            double realtime = featureService.realtimeMatch(ctx.getUserId(), c.getItemId());

            feats.put("interact", interact);
            feats.put("quality", quality);
            feats.put("interest", interest);
            feats.put("social", social);
            feats.put("author", author);
            feats.put("hot", hot);
            feats.put("realtime", realtime);

            double weighted = 0;
            for (Map.Entry<String, Double> e : feats.entrySet()) {
                weighted += cfg.rankWeightOf(e.getKey()) * e.getValue();
            }
            double explore = exploreProvider.exploreBonus(ctx.getUserId(), c.getItemId());
            weighted += explore;
            // 流量池加分：新帖探索保底 + 已验证档位加权（仿抖音赛马）
            weighted += trafficPool.tierBonus(c.getItemId());

            double rankScore = weighted * f.getFreshness();

            List<String> sources = new ArrayList<>(c.getChannelScores().keySet());
            String explain = buildExplain(f, profile, social, interest, sources);
            ranked.add(new RankedItem(c.getItemId(), rankScore, feats, sources, explain));
        }

        ranked.sort(Comparator.comparingDouble(RankedItem::getRankScore).reversed());
        return ranked;
    }

    /** 互动率（质量）：log1p(深度互动/曝光)，曝光用浏览数代理，防大V马太效应 */
    private double quality(ItemFeature f) {
        int deep = f.getRepostCount() + f.getCommentCount() + f.getLikeCount() + f.getFavoriteCount();
        int exposure = Math.max(1, f.getViewCount());
        return Math.log1p((double) deep / exposure);
    }

    /** 兴趣匹配：话题重叠 × 0.6 + ItemCF 相似度 × 0.4 */
    private double interest(UserProfile profile, ItemFeature f, ItemCfModel model,
                            List<Long> history, Map<Long, Double> cache) {
        double topicOverlap = 0;
        for (String t : f.getTopics()) {
            topicOverlap += profile.getTopicWeight().getOrDefault(t, 0.0);
        }
        double itemcf = cache.computeIfAbsent(f.getPostId(),
                pid -> itemCfScorer.score(pid, model, history));
        return 0.6 * topicOverlap + 0.4 * itemcf;
    }

    /** 关注关系：基础 1 + 作者被我关注 +1 + 我关注的人转发了 +0.5（微博二度关系） */
    private double social(Long userId, ItemFeature f, Set<Long> followedRepostedIds) {
        double score = 1.0;
        Post post = postRepository.findById(f.getPostId()).orElse(null);
        if (userId != null && post != null && post.getAuthorId() != null
                && followRepository.exists(userId, post.getAuthorId())) {
            score += 1.0;
        }
        if (followedRepostedIds.contains(f.getPostId())) {
            score += 0.5;
        }
        return score;
    }

    /** 我关注的人转发过的帖子 ID 集合（二度关系信号，一次请求内预计算） */
    private Set<Long> computeFollowedRepostedIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> following = followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toSet());
        if (following.isEmpty()) {
            return Set.of();
        }
        return postRepository.findAll().stream()
                .filter(p -> p.getOriginalPostId() != null && p.getOriginalAuthorId() != null)
                .filter(p -> following.contains(p.getOriginalAuthorId()))
                .map(Post::getOriginalPostId)
                .collect(Collectors.toSet());
    }

    /** 可解释推荐理由（微博式：关注/话题/互动/热点/新内容） */
    private String buildExplain(ItemFeature f, UserProfile profile, double social,
                                double interest, List<String> sources) {
        if (social > 1.5) {
            return "你关注的人发布了/转发了";
        }
        for (String topic : f.getTopics()) {
            if (profile.getTopicWeight().getOrDefault(topic, 0.0) > 0.1) {
                return "因为你看过 #" + topic + "#";
            }
        }
        if (interest > 0.5) {
            return "和你互动过的帖子相似";
        }
        if (sources.contains("hot")) {
            return "大家都在看";
        }
        if (f.isInNewPool()) {
            return "新内容推荐";
        }
        return "为你精选";
    }
}
