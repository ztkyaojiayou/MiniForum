package com.tkzou.miniforum.recommend.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.RecommendPostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.ab.AbExperimentService;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.coldstart.ColdStartService;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.rank.RankService;
import com.tkzou.miniforum.recommend.recall.RecallService;
import com.tkzou.miniforum.recommend.rerank.RerankService;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 推荐链路高并发降级测试（P0-3）
 * <p>
 * 主链路（召回/排序/重排）任一步异常 → 降级为全站热门兜底，接口不 500、只损失个性化。
 * 对齐大厂"降级牺牲功能、限流牺牲流量"。
 */
class RecommendServiceFallbackTest {

    @Test
    void recommend_fallsBackToHotPostsWhenFunnelThrows() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ItemFeatureService itemFeatureService = mock(ItemFeatureService.class);
        RecallService recallService = mock(RecallService.class);
        RankService rankService = mock(RankService.class);
        RerankService rerankService = mock(RerankService.class);
        ColdStartService coldStartService = mock(ColdStartService.class);
        ConfigService configService = mock(ConfigService.class);
        AbExperimentService abExperimentService = mock(AbExperimentService.class);
        BehaviorLogger behaviorLogger = mock(BehaviorLogger.class);
        PostAssembler postAssembler = mock(PostAssembler.class);
        PostRepository postRepository = mock(PostRepository.class);
        ItemCfModelStore itemCfModelStore = mock(ItemCfModelStore.class);

        when(abExperimentService.configFor(any(), anyLong())).thenReturn(RecConfig.defaults());
        // 召回通道全部故障：模拟主链路抛异常
        when(recallService.recall(any())).thenThrow(new IllegalStateException("召回通道全部故障"));

        Post hotPost = new Post();
        hotPost.setId(100L);
        hotPost.setTitle("热门帖");
        hotPost.setStatus(Post.STATUS_PUBLISHED);
        when(postRepository.findAll()).thenReturn(List.of(hotPost));
        ItemFeature hotFeature = new ItemFeature();
        hotFeature.setHotScore(100);
        when(itemFeatureService.itemFeature(100L)).thenReturn(hotFeature);
        when(postRepository.findById(100L)).thenReturn(Optional.of(hotPost));
        when(postAssembler.toVO(hotPost, "alice")).thenReturn(mock(PostVO.class));

        RecommendService service = new RecommendService(userProfileService, itemFeatureService, recallService, rankService, rerankService,
                coldStartService, configService, abExperimentService, behaviorLogger, postAssembler,
                postRepository, itemCfModelStore);

        List<RecommendPostVO> result = service.recommend(
                new RecommendContext(1L, "HOME", LocalDateTime.now(), 10), "alice", "rec-v1");

        assertFalse(result.isEmpty(), "主链路异常应降级为热门兜底，而非 500");
        assertEquals("推荐服务降级·热门兜底", result.get(0).getReason());
    }
}
