package com.tkzou.miniforum.recommend.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.ab.AbExperimentService;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.coldstart.impl.ColdStartService;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.domain.RecommendScene;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.rank.CoarseRankService;
import com.tkzou.miniforum.recommend.rank.FineRankService;
import com.tkzou.miniforum.recommend.recall.RecallService;
import com.tkzou.miniforum.recommend.rank.RerankService;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 兜底/冷启动热门 postId 缓存测试（P1-1）
 * <p>
 * 经 recommend 的降级路径（召回抛异常 → 热门兜底）验证：TTL 内 findAll 只现算一次、
 * 热门排序按 itemFeature.hotScore 降序、ttl=0 禁用。
 */
class TopHotCacheTest {

    private UserProfileService userProfileService;
    private ItemFeatureService itemFeatureService;
    private RecallService recallService;
    private CoarseRankService coarseRankService;
    private FineRankService rankService;
    private RerankService rerankService;
    private ColdStartService coldStartService;
    private ConfigService configService;
    private AbExperimentService abExperimentService;
    private BehaviorLogger behaviorLogger;
    private PostAssembler postAssembler;
    private PostRepository postRepository;
    private ItemCfModelStore itemCfModelStore;
    private RecommendService service;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);
        itemFeatureService = mock(ItemFeatureService.class);
        recallService = mock(RecallService.class);
        coarseRankService = mock(CoarseRankService.class);
        rankService = mock(FineRankService.class);
        rerankService = mock(RerankService.class);
        coldStartService = mock(ColdStartService.class);
        configService = mock(ConfigService.class);
        abExperimentService = mock(AbExperimentService.class);
        behaviorLogger = mock(BehaviorLogger.class);
        postAssembler = mock(PostAssembler.class);
        postRepository = mock(PostRepository.class);
        itemCfModelStore = mock(ItemCfModelStore.class);
        when(abExperimentService.configFor(any(), anyLong())).thenReturn(RecConfig.defaults());
        // 粗排简化实现：默认透传候选
        when(coarseRankService.coarseRank(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        // 主链路抛异常 → 走热门兜底（本测试验证的就是兜底路径的热门缓存）
        when(recallService.recall(any())).thenThrow(new IllegalStateException("召回通道故障"));
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));
        service = new RecommendService(userProfileService, itemFeatureService, recallService, coarseRankService, rankService, rerankService,
                coldStartService, configService, abExperimentService, behaviorLogger, postAssembler,
                postRepository, itemCfModelStore);
        service.setTopHotCacheTtlMs(10_000L); // 默认启用：对齐 application.yml
    }

    private Post post(long id, long hotScore) {
        ItemFeature f = new ItemFeature();
        f.setHotScore(hotScore);
        when(itemFeatureService.itemFeature(id)).thenReturn(f);
        Post p = new Post();
        p.setId(id);
        p.setStatus(Post.STATUS_PUBLISHED);
        when(postRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void hotFallback_topHotPostsIsCached() {
        List<Post> posts = List.of(post(100L, 100), post(200L, 200));
        when(postRepository.findAll()).thenReturn(posts);
        service.recommend(new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), "alice", "rec-v1");
        service.recommend(new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), "alice", "rec-v1");
        verify(postRepository, times(1)).findAll(); // 第二次命中热门缓存，全表扫只发生一次
    }

    @Test
    void hotFallback_ordersHotPostsByHotScoreDesc() {
        List<Post> posts = List.of(post(100L, 100), post(200L, 200));
        when(postRepository.findAll()).thenReturn(posts);
        service.recommend(new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), "alice", "rec-v1");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postAssembler, times(2)).toVO(captor.capture(), eq("alice"));
        assertEquals(200L, captor.getAllValues().get(0).getId(), "hotScore 最高的帖子应排第一");
        assertEquals(100L, captor.getAllValues().get(1).getId());
    }

    @Test
    void hotFallback_cacheDisabledWhenTtlZero() {
        service.setTopHotCacheTtlMs(0L);
        List<Post> posts = List.of(post(100L, 100));
        when(postRepository.findAll()).thenReturn(posts);
        service.recommend(new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), "alice", "rec-v1");
        service.recommend(new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), "alice", "rec-v1");
        verify(postRepository, times(2)).findAll(); // ttl=0 每次现算
    }
}
