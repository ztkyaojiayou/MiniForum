package com.tkzou.miniforum.recommend.service;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.RecommendPostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.ab.AbExperimentService;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.coldstart.impl.ColdStartService;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.rank.CoarseRankService;
import com.tkzou.miniforum.recommend.rank.RankService;
import com.tkzou.miniforum.recommend.recall.RecallService;
import com.tkzou.miniforum.recommend.rerank.RerankService;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.util.TtlCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐服务（漏斗编排核心）
 * <p>
 * <b>数据流程</b>：{@code RecommendContext(uid, scene, size)} → 画像 {@link UserProfileService#userProfile}
 * → {@link RecallService#recall} 多路召回+融合(Candidate) → {@link RuleRankService#rank} 微博式排序(RankedItem+推荐理由)
 * → {@link DiversifyRerankService#rerank} 打散/MMR(TopN) → 冷启动兜底(新用户补热门) → 逐条记 EXPOSE 行为日志
 * → 组装带理由的 {@link RecommendPostVO} 下发。
 * <p>
 * related：详情页"看过这篇的人还看"，直接读 ItemCF 相似度模型 TopK。
 * 按 AB 实验分组走不同配置变体（{@code AbExperimentService.configFor}），行为日志携带 expId 便于离线归因。
 */
@Service
public class RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);

    /** Sentinel 资源名：推荐 feed 主链路（P3-1 限流 + P3-2 熔断共用）。prod 由 {@code SentinelConfig} 下发规则，演示无规则原样放行 */
    public static final String SENTINEL_RESOURCE_FEED = "recommend-feed";

    /** 兜底/冷启动热门 postId 缓存：单 key 存 Top-50 排序列表，TTL 内命中（复用 app.rec.hot-post-ids-ttl-ms） */
    private static final String TOP_HOT_KEY = "top-hot-posts";
    /** 热门缓存 TTL 打散幅度（ms） */
    private static final long TOP_HOT_JITTER_MS = 1_000;
    private final TtlCache<String, List<Long>> topHotCache = new TtlCache<>(0, TOP_HOT_JITTER_MS);

    /** 热门 postId 缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次现算） */
    @Value("${app.rec.hot-post-ids-ttl-ms:10000}")
    public void setTopHotCacheTtlMs(long ttl) {
        topHotCache.setTtlMillis(ttl);
    }

    private final UserProfileService userProfileService;
    private final ItemFeatureService itemFeatureService;
    private final RecallService recallService;
    private final CoarseRankService coarseRankService;
    private final RankService rankService;
    private final RerankService rerankService;
    private final ColdStartService coldStartService;
    private final ConfigService configService;
    private final AbExperimentService abExperimentService;
    private final BehaviorLogger behaviorLogger;
    private final PostAssembler postAssembler;
    private final PostRepository postRepository;
    private final ItemCfModelStore itemCfModelStore;

    public RecommendService(UserProfileService userProfileService,
                            ItemFeatureService itemFeatureService,
                            RecallService recallService,
                            CoarseRankService coarseRankService,
                            RankService rankService,
                            RerankService rerankService,
                            ColdStartService coldStartService,
                            ConfigService configService,
                            AbExperimentService abExperimentService,
                            BehaviorLogger behaviorLogger,
                            PostAssembler postAssembler,
                            PostRepository postRepository,
                            ItemCfModelStore itemCfModelStore) {
        this.userProfileService = userProfileService;
        this.itemFeatureService = itemFeatureService;
        this.recallService = recallService;
        this.coarseRankService = coarseRankService;
        this.rankService = rankService;
        this.rerankService = rerankService;
        this.coldStartService = coldStartService;
        this.configService = configService;
        this.abExperimentService = abExperimentService;
        this.behaviorLogger = behaviorLogger;
        this.postAssembler = postAssembler;
        this.postRepository = postRepository;
        this.itemCfModelStore = itemCfModelStore;
    }

    /**
     * 推荐流：完整漏斗，返回带理由的 TopN。
     * <p>
     * 高并发降级保护（P3-1/P3-2 Sentinel 接入点，prod 生效、演示无规则放行）：
     * <ul>
     *   <li><b>限流/熔断</b>：{@link SphU#entry} 对资源 {@value #SENTINEL_RESOURCE_FEED} 做 QPS 限流 + 异常比例熔断。
     *       触发（{@link BlockException}）→ 直接降级全站热门兜底（"限流牺牲流量、降级牺牲功能"）；</li>
     *   <li><b>业务异常兜底</b>：主链路（召回/排序/重排）任一步异常 → {@link Tracer#trace} 给异常比例熔断供数
     *       + 降级热门兜底，接口不 500、只损失个性化。</li>
     * </ul>
     */
    public List<RecommendPostVO> recommend(RecommendContext ctx, String username, String expId) {
        int topN = RecConfig.defaults().getFinalTopN(); // 兜底默认条数（降级路径也要有界）
        Entry entry = null;
        try {
            // Sentinel 埋点：prod 有规则则做 QPS 限流 + 异常比例熔断；无规则原样放行（演示行为不变）。
            // 放最前面：超限/熔断的请求在进入漏斗前就被拦截（不占昂贵算力）。
            entry = SphU.entry(SENTINEL_RESOURCE_FEED);
            // AB 实验：实验组 B 走多样性变体配置
            RecConfig cfg = abExperimentService.configFor(expId, ctx.getUserId());
            topN = Math.min(ctx.getSize() > 0 ? ctx.getSize() : cfg.getFinalTopN(), cfg.getFinalTopN());
            return doRecommend(ctx, username, expId, cfg, topN);
        } catch (BlockException e) {
            // 限流/熔断触发：BlockException 是 checked 异常，必须最先 catch（它是 Exception 子类）。
            // 不计入异常比例熔断（被限流是"预期拒绝"，不是业务故障）——直接降级热门。
            log.warn("推荐入口被 Sentinel 限流/熔断，降级热门兜底：userId={}, expId={}, rule={}",
                    ctx.getUserId(), expId, e.getRuleLimitApp());
            return hotFallback(ctx, username, topN);
        } catch (Exception e) {
            // 业务异常：Tracer.trace 给异常比例熔断规则（DEGRADE_GRADE_EXCEPTION_RATIO）供数，
            // 异常率超阈值后 Sentinel 自动熔断（后续 entry 抛 DegradeException → 走上面的 BlockException 分支）。
            Tracer.trace(e);
            log.warn("推荐链路异常，降级为热门兜底：userId={}, expId={}", ctx.getUserId(), expId, e);
            return hotFallback(ctx, username, topN);
        } finally {
            if (entry != null) {
                entry.exit(); // 与 SphU.entry 成对：只有成功拿到 entry 才需要 exit（含异常路径）
            }
        }
    }

    /** 完整推荐漏斗（单独抽出，便于 {@link #recommend} 整体降级） */
    private List<RecommendPostVO> doRecommend(RecommendContext ctx, String username, String expId,
                                              RecConfig cfg, int topN) {
        // 1. 多路召回 → 融合候选
        List<Candidate> candidates = recallService.recall(ctx);
        // 1.5 粗排（架构对齐大厂"召回→粗排→精排"；简化实现按融合分截断到 coarseTopN，默认透传）
        List<Candidate> coarse = coarseRankService.coarseRank(ctx, candidates);
        // 2. 规则排序（精排）
        List<RankedItem> ranked = rankService.rank(ctx, coarse);
        // 3. 重排（打散 + MMR）
        List<RankedItem> reranked = rerankService.rerank(ctx, ranked, topN);
        // 4. 冷启动兜底（新用户补热门）
        List<RankedItem> finalList = coldStartFallback(ctx, reranked, topN, cfg);

        // 5. 曝光日志（服务端自动记录，前端无需打点）
        for (RankedItem item : finalList) {
            behaviorLogger.log(ctx.getUserId(), item.getItemId(), BehaviorType.EXPOSE, "RECOMMEND_FEED", expId);
        }

        // 6. 组装 VO
        return finalList.stream()
                .map(r -> toVO(r, username))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 热门兜底：主链路降级时返回全站热度 TopN；最坏情况（兜底也失败）返回空列表，绝不向上抛异常 */
    private List<RecommendPostVO> hotFallback(RecommendContext ctx, String username, int topN) {
        try {
            return topHotPosts().stream()
                    .limit(topN)
                    .map(p -> toVO(new RankedItem(p.getId(), 0.5, Map.of("hot", 1.0),
                            List.of("hot"), "推荐服务降级·热门兜底"), username))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("热门兜底也失败：userId={}", ctx.getUserId(), ex);
            return List.of();
        }
    }

    /** 详情页相关推荐：ItemCF 相似帖（"看过这篇的人还看"） */
    public List<RecommendPostVO> related(Long postId, String username) {
        ItemCfModel model = itemCfModelStore.get();
        if (model.size() == 0) {
            return List.of();
        }
        Set<Long> visibleIds = postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .map(Post::getId)
                .collect(Collectors.toSet());

        return model.topSimilar(postId, 20).stream()
                .filter(s -> visibleIds.contains(s.itemId()))
                .map(s -> new RankedItem(s.itemId(), s.similarity(),
                        Map.of("itemcf", s.similarity()), List.of("itemcf"), "看过这篇的人还看"))
                .sorted(Comparator.comparingDouble(RankedItem::getRankScore).reversed())
                .limit(5)
                .map(r -> toVO(r, username))
                .collect(Collectors.toList());
    }

    /** 冷用户热门兜底：保证新用户能看到"大家都在看"（热搜/热门池） */
    private List<RankedItem> coldStartFallback(RecommendContext ctx, List<RankedItem> reranked,
                                               int topN, RecConfig cfg) {
        UserProfile profile = userProfileService.userProfile(ctx.getUserId());
        if (!profile.isCold(cfg.getMinBehaviorForWarm()) || reranked.isEmpty()) {
            return reranked;
        }
        Set<Long> ids = reranked.stream().map(RankedItem::getItemId).collect(Collectors.toSet());
        List<RankedItem> result = new ArrayList<>(reranked);
        for (Post p : topHotPosts()) {
            if (result.size() >= topN) {
                break;
            }
            if (!ids.contains(p.getId())) {
                result.add(new RankedItem(p.getId(), 0.5, Map.of("hot", 1.0),
                        List.of("hot"), "新用户热门推荐"));
            }
        }
        return result;
    }

    /** 全站热门 Top-50（兜底/冷启动路径用）：缓存排序 postId 列表，避免每请求全表扫 + 逐帖 itemFeature 排序 */
    private List<Post> topHotPosts() {
        List<Long> ids = topHotCache.get(TOP_HOT_KEY, this::computeTopHotPostIds);
        return ids.stream()
                .map(postRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /** 现算热门 postId 排序（缓存 miss 时执行）：itemFeature.hotScore 降序取 50（内部 itemFeature 已有 5s 缓存） */
    private List<Long> computeTopHotPostIds() {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .sorted(Comparator.comparingDouble((Post p) -> itemFeatureService.itemFeature(p.getId()).getHotScore()).reversed())
                .limit(50)
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    private RecommendPostVO toVO(RankedItem ranked, String username) {
        Post post = postRepository.findById(ranked.getItemId()).orElse(null);
        if (post == null) {
            return null;
        }
        PostVO postVO = postAssembler.toVO(post, username);
        return new RecommendPostVO(postVO, ranked.getRankScore(), ranked.getExplain(), ranked.getSources());
    }

    /** 调试：当前用户画像快照（供测试/排障） */
    public UserProfile profileOf(Long userId) {
        return userProfileService.userProfile(userId);
    }
}
