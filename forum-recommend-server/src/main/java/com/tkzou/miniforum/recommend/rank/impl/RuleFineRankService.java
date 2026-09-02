package com.tkzou.miniforum.recommend.rank.impl;
import com.tkzou.miniforum.recommend.rank.ExploreProvider;
import com.tkzou.miniforum.recommend.rank.FineRankService;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.graph.SocialGraphService;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.model.ItemCfScorer;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则加权精排（排序第二阶段·弱训练侧默认实现，粗排之后、重排之前）
 * <p>
 * <b>数据流程</b>：{@code List<Candidate>（粗排后的候选）} → 对每个候选计算特征分构成 featureScores
 * → 加权求和 rankScore = (Σ w_f·f + explore) × recency → 排序 → {@code List<RankedItem>}（携带特征分+召回路来源+推荐理由），
 * 供重排层 {@code DiversifyRerankService} 消费。
 * <p>
 * 特征：interact(互动热度) / quality(互动率) / interest(兴趣匹配) / social(关注关系) /
 *       author(作者权重) / hot(热点) / realtime(实时特征)；权重来自 RecConfig.rankWeight，
 * 微博场景方向性经验值（见 docs/微博推荐调研.md）。
 */
@Component
public class RuleFineRankService implements FineRankService {

    /** 兴趣融合权重：话题重叠 × 0.6 + ItemCF 相似度 × 0.4（微博兴趣 = 内容语义 + 行为相似） */
    private static final double INTEREST_TOPIC_WEIGHT = 0.6;
    private static final double INTEREST_ITEMCF_WEIGHT = 0.4;
    /** 社交分构成：基础 1 + 关注作者 +1 + 我关注的人转发了 +0.5（微博二度关系） */
    private static final double SOCIAL_BASE = 1.0;
    private static final double SOCIAL_FOLLOWING_BONUS = 1.0;
    private static final double SOCIAL_FOLLOWED_REPOST_BONUS = 0.5;
    /** 可解释推荐理由判定阈值 */
    private static final double EXPLAIN_SOCIAL_THRESHOLD = 1.5;
    private static final double EXPLAIN_TOPIC_THRESHOLD = 0.1;
    private static final double EXPLAIN_INTEREST_THRESHOLD = 0.5;

    private final UserProfileService userProfileService;
    private final ItemFeatureService itemFeatureService;
    private final ItemCfModelStore itemCfModelStore;
    private final ItemCfScorer itemCfScorer;
    private final SocialGraphService socialGraphService;
    private final PostRepository postRepository;
    private final ConfigService configService;
    private final ExploreProvider exploreProvider;
    private final TrafficPool trafficPool;

    public RuleFineRankService(UserProfileService userProfileService,
                               ItemFeatureService itemFeatureService,
                               ItemCfModelStore itemCfModelStore,
                               ItemCfScorer itemCfScorer,
                               SocialGraphService socialGraphService,
                               PostRepository postRepository,
                               ConfigService configService,
                               ExploreProvider exploreProvider,
                               TrafficPool trafficPool) {
        this.userProfileService = userProfileService;
        this.itemFeatureService = itemFeatureService;
        this.itemCfModelStore = itemCfModelStore;
        this.itemCfScorer = itemCfScorer;
        this.socialGraphService = socialGraphService;
        this.postRepository = postRepository;
        this.configService = configService;
        this.exploreProvider = exploreProvider;
        this.trafficPool = trafficPool;
    }

    /**
     * 精排（排序核心打分）：把粗排后的候选逐条算排序分，降序输出给重排层。
     * <p>
     * <b>核心公式</b>：
     * <pre>
     * rankScore = ( Σ_{f∈7特征} rankWeight_f × feature_f + exploreBonus + trafficTierBonus ) × freshness
     * </pre>
     * - <b>Σ 加权求和</b>：7 个特征各乘 {@code RecConfig.rankWeight}（interact/quality/interest/social/author/hot/realtime）
     *   ——"哪个信号重要"就是调这张权重表（第 10 章，AB 变体可配）；
     * - <b>+ explore</b>：探索加分（Thompson/新用户 λ，第 12 章）——不加则新内容永无出头日；
     * - <b>+ trafficTierBonus</b>：流量池档位加分（已验证档位加权，仿抖音赛马，第 12 章）；
     * - <b>× freshness</b>：最后整体<b>乘</b>时效衰减（不是加）——同分下新帖优先，且随时间指数退场（半衰期 4h）。
     * <p>
     * 7 特征按信号意图分三组：内容吸引力（interact 热度 / quality 互动率 / hot 相对热度）、
     * 个性化匹配（interest 兴趣 / social 社交 / realtime 实时）、作者权威（author 作者粉丝数）。
     */
    @Override
    public List<RankedItem> rank(RecommendContext ctx, List<Candidate> candidates) {
        // ═══ 前置①：一次请求拉齐"所有候选共享"的上下文/依赖——必须提在循环外，否则 N 个候选各查一遍 ═══
        //   这 5 个值来自三域底座（第 08 章）：cfg=策略 / profile+history=画像 / model=模型 / followedReposted=图谱。
        //   它们与本请求的用户绑定、与"哪个候选"无关 → 只需拉一次，循环内直接复用。
        RecConfig cfg = configService.current();                                // ① 排序权重配置（AB 变体已由 configFor 解析，第 10 章）
        UserProfile profile = userProfileService.userProfile(ctx.getUserId());  // ② 画像：用户话题/类目兴趣 —— interest 特征 + 推荐理由的数据源（域内缓存）
        List<Long> history = profile.getRecentItemIds();                        // ③ 我最近交互过的帖 id 序列 → ItemCF"行为相似"的输入（interest 特征 itemcf 侧）
        ItemCfModel model = itemCfModelStore.get();                             // ④ ItemCF 相似度模型（帖→相似帖 TopK，离线每 30min 重建，第 13 章）
        Set<Long> followedRepostedIds = socialGraphService.followedRepostedIds(ctx.getUserId()); // ⑤ 我关注的人转发的帖集合 —— social 特征"+0.5（二度）"的依据（图谱域一次查）

        // 预扫描一轮：取本批候选的最大互动热度（log1p 压缩量级），供 hot 特征做"批内相对归一化"——
        // hot = 本候选热度 / 批内最高 ∈(0,1]，区分"这批里谁更热"，而非比全局绝对值（各批尺度不一）
        double maxInteract = 1;
        for (Candidate c : candidates) {
            maxInteract = Math.max(maxInteract, Math.log1p(itemFeatureService.itemFeature(c.getItemId()).getHotScore()));
        }

        Map<Long, Double> itemCfCache = new HashMap<>();
        List<RankedItem> ranked = new ArrayList<>();
        for (Candidate c : candidates) {
            ItemFeature f = itemFeatureService.itemFeature(c.getItemId());
            // 作者信息（社交/作者特征需要）：与 social() 原逻辑一致，每候选一次回源
            Post post = postRepository.findById(c.getItemId()).orElse(null);
            Long authorId = post != null ? post.getAuthorId() : null;
            Map<String, Double> feats = new LinkedHashMap<>();

            // 7 特征（内容/个性化/作者三组意图）——每个候选现算，见类 Javadoc 与下方各子方法
            double interact = Math.log1p(f.getHotScore());
            double quality = quality(f);
            double interest = interest(profile, f, model, history, itemCfCache);
            double social = social(ctx.getUserId(), authorId, c.getItemId(), followedRepostedIds);
            double author = authorId != null ? socialGraphService.authorFollowers(authorId) : 0;
            // hot：批内相对热度 = 本候选 interact ÷ 批内最大 ∈(0,1]——区分"这批候选里谁更热"（依赖上方 maxInteract 预扫描，非全局绝对值）
            double hot = interact / Math.max(1, maxInteract);
            // realtime：近线实时信号 = 用户近 N 分钟话题点击投影 + 帖子近 N 分钟互动爆发（读 RealtimeFeatureStore）
            //    ——近线数据 demo 由 RealtimeFeatureWindow 内存每 5s flush、prod 由 Flink 独立作业写 Redis 产出（第 08 章 §3.6）
            double realtime = itemFeatureService.realtimeMatch(ctx.getUserId(), c.getItemId());

            feats.put("interact", interact);
            feats.put("quality", quality);
            feats.put("interest", interest);
            feats.put("social", social);
            feats.put("author", author);
            feats.put("hot", hot);
            feats.put("realtime", realtime);

            // Σ 加权求和：weighted = Σ rankWeight_f × feature_f（7 特征各乘 RecConfig.rankWeight）——
            //   权重表决定"哪个信号重要"，是调排序策略的旋钮（AB 变体可配，第 10 章）
            double weighted = 0;
            for (Map.Entry<String, Double> e : feats.entrySet()) {
                weighted += cfg.rankWeightOf(e.getKey()) * e.getValue();
            }
            // + explore：探索加分（第 12 章 Thompson λ × 探索度）——不加则新内容/新兴趣永无出头日
            double explore = exploreProvider.exploreBonus(ctx.getUserId(), c.getItemId());
            weighted += explore;
            // 流量池加分：新帖探索保底 + 已验证档位加权（仿抖音赛马）
            weighted += trafficPool.tierBonus(c.getItemId());

            // rankScore = (加权和 + 探索 + 流量池档位) × 时效衰减 —— 见方法 Javadoc 的公式
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
        return INTEREST_TOPIC_WEIGHT * topicOverlap + INTEREST_ITEMCF_WEIGHT * itemcf;
    }

    /** 关注关系：基础分 + 作者被我关注加分 + 我关注的人转发加分（微博二度关系；社交信号取自图域 SocialGraphService） */
    private double social(Long userId, Long authorId, Long postId, Set<Long> followedRepostedIds) {
        double score = SOCIAL_BASE;
        if (socialGraphService.isFollowing(userId, authorId)) {
            score += SOCIAL_FOLLOWING_BONUS;
        }
        if (followedRepostedIds.contains(postId)) {
            score += SOCIAL_FOLLOWED_REPOST_BONUS;
        }
        return score;
    }

    /** 可解释推荐理由（微博式：关注/话题/互动/热点/新内容） */
    private String buildExplain(ItemFeature f, UserProfile profile, double social,
                                double interest, List<String> sources) {
        if (social > EXPLAIN_SOCIAL_THRESHOLD) {
            return "你关注的人发布了/转发了";
        }
        for (String topic : f.getTopics()) {
            if (profile.getTopicWeight().getOrDefault(topic, 0.0) > EXPLAIN_TOPIC_THRESHOLD) {
                return "因为你看过 #" + topic + "#";
            }
        }
        if (interest > EXPLAIN_INTEREST_THRESHOLD) {
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
