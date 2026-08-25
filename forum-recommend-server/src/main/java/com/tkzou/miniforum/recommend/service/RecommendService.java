package com.tkzou.miniforum.recommend.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.RecommendPostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.ab.AbExperimentService;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.coldstart.ColdStartService;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.rank.RankService;
import com.tkzou.miniforum.recommend.recall.RecallService;
import com.tkzou.miniforum.recommend.rerank.RerankService;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐服务（漏斗编排核心）
 * <p>
 * <b>数据流程</b>：{@code RecommendContext(uid, scene, size)} → 画像 {@link FeatureService#userProfile}
 * → {@link RecallService#recall} 多路召回+融合(Candidate) → {@link RuleRankService#rank} 微博式排序(RankedItem+推荐理由)
 * → {@link DiversifyRerankService#rerank} 打散/MMR(TopN) → 冷启动兜底(新用户补热门) → 逐条记 EXPOSE 行为日志
 * → 组装带理由的 {@link RecommendPostVO} 下发。
 * <p>
 * related：详情页"看过这篇的人还看"，直接读 ItemCF 相似度模型 TopK。
 * 按 AB 实验分组走不同配置变体（{@code AbExperimentService.configFor}），行为日志携带 expId 便于离线归因。
 */
@Service
public class RecommendService {

    private final FeatureService featureService;
    private final RecallService recallService;
    private final RankService rankService;
    private final RerankService rerankService;
    private final ColdStartService coldStartService;
    private final ConfigService configService;
    private final AbExperimentService abExperimentService;
    private final BehaviorLogger behaviorLogger;
    private final PostAssembler postAssembler;
    private final PostRepository postRepository;
    private final ItemCfModelStore itemCfModelStore;

    public RecommendService(FeatureService featureService,
                            RecallService recallService,
                            RankService rankService,
                            RerankService rerankService,
                            ColdStartService coldStartService,
                            ConfigService configService,
                            AbExperimentService abExperimentService,
                            BehaviorLogger behaviorLogger,
                            PostAssembler postAssembler,
                            PostRepository postRepository,
                            ItemCfModelStore itemCfModelStore) {
        this.featureService = featureService;
        this.recallService = recallService;
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

    /** 推荐流：完整漏斗，返回带理由的 TopN */
    public List<RecommendPostVO> recommend(RecommendContext ctx, String username, String expId) {
        // AB 实验：实验组 B 走多样性变体配置
        RecConfig cfg = abExperimentService.configFor(expId, ctx.getUserId());
        int topN = Math.min(ctx.getSize() > 0 ? ctx.getSize() : cfg.getFinalTopN(), cfg.getFinalTopN());

        // 1. 多路召回 → 融合候选
        List<Candidate> candidates = recallService.recall(ctx);
        // 2. 规则排序
        List<RankedItem> ranked = rankService.rank(ctx, candidates);
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
        UserProfile profile = featureService.userProfile(ctx.getUserId());
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

    private List<Post> topHotPosts() {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .sorted(Comparator.comparingDouble((Post p) -> featureService.itemFeature(p.getId()).getHotScore()).reversed())
                .limit(50)
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
        return featureService.userProfile(userId);
    }
}
