package com.tkzou.miniforum.recommend.service;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.RecommendPostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.ab.AbExperimentService;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.coldstart.ColdStartService;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.RankedItem;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.rank.RankService;
import com.tkzou.miniforum.recommend.recall.RecallService;
import com.tkzou.miniforum.recommend.rerank.RerankService;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sentinel 限流/熔断接入测试（P3-1/P3-2）
 * <p>
 * 验证三件事：
 * <ol>
 *   <li><b>无规则原样放行</b>：推荐漏斗正常执行（保证演示/未下发规则行为不变）；</li>
 *   <li><b>限流触发降级</b>：FlowRule count=0 → SphU.entry 抛 BlockException → 热门兜底，漏斗不执行；</li>
 *   <li><b>规则清理</b>：Sentinel 规则是 JVM 级全局静态，try/finally + @AfterEach 双清理防污染其它测试。</li>
 * </ol>
 */
class RecommendServiceSentinelTest {

    private UserProfileService userProfileService;
    private ItemFeatureService itemFeatureService;
    private RecallService recallService;
    private RankService rankService;
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
        rankService = mock(RankService.class);
        rerankService = mock(RerankService.class);
        coldStartService = mock(ColdStartService.class);
        configService = mock(ConfigService.class);
        abExperimentService = mock(AbExperimentService.class);
        behaviorLogger = mock(BehaviorLogger.class);
        postAssembler = mock(PostAssembler.class);
        postRepository = mock(PostRepository.class);
        itemCfModelStore = mock(ItemCfModelStore.class);

        when(abExperimentService.configFor(any(), anyLong())).thenReturn(RecConfig.defaults());
        // 完整漏斗桩：recall → rank → rerank → 冷启动（暖用户，不再补热门）→ 逐条 toVO
        UserProfile warm = new UserProfile();
        warm.setBehaviorCount(10); // 行为数 ≥ min-behavior-for-warm(5) → 非冷用户，冷启动兜底直接返回重排结果
        when(userProfileService.userProfile(anyLong())).thenReturn(warm);
        when(recallService.recall(any())).thenReturn(List.of());
        RankedItem ranked = new RankedItem(100L, 1.0, Map.of("hot", 1.0), List.of("hot"), "热门");
        when(rankService.rank(any(), any())).thenReturn(List.of(ranked));
        when(rerankService.rerank(any(), any(), anyInt())).thenReturn(List.of(ranked));
        // 热门兜底桩（BlockException 路径用）
        Post hotPost = new Post();
        hotPost.setId(100L);
        hotPost.setStatus(Post.STATUS_PUBLISHED);
        when(postRepository.findAll()).thenReturn(List.of(hotPost));
        when(postRepository.findById(100L)).thenReturn(Optional.of(hotPost));
        ItemFeature hotFeature = new ItemFeature();
        hotFeature.setHotScore(100);
        when(itemFeatureService.itemFeature(100L)).thenReturn(hotFeature);
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));

        service = new RecommendService(userProfileService, itemFeatureService, recallService, rankService, rerankService,
                coldStartService, configService, abExperimentService, behaviorLogger, postAssembler,
                postRepository, itemCfModelStore);
    }

    @AfterEach
    void cleanSentinelRules() {
        // Sentinel 规则是 JVM 级全局静态：兜底清空限流+熔断，防污染其它测试类（同 JVM 顺序执行）
        FlowRuleManager.loadRules(Collections.emptyList());
        DegradeRuleManager.loadRules(Collections.emptyList());
    }

    private RecommendContext ctx(Long userId, String username) {
        return new RecommendContext(userId, "HOME", LocalDateTime.now(), 10);
    }

    @Test
    void recommend_passesThrough_withoutRules() {
        // 未下发任何规则（演示/未配置形态）→ SphU.entry 原样放行，完整漏斗执行
        List<RecommendPostVO> result = service.recommend(ctx(1L, "alice"), "alice", "rec-v1");

        assertFalse(result.isEmpty(), "无规则时推荐漏斗应正常产出");
        verify(recallService, times(1)).recall(any());
        verify(rerankService, times(1)).rerank(any(), any(), anyInt());
    }

    @Test
    void recommend_fallsBackToHotPosts_whenFlowRuleBlocks() {
        // 1. 暖链一次：消除 Sentinel 首建 metric 的歧义（此后规则判定才确定）
        List<RecommendPostVO> warm = service.recommend(ctx(1L, "alice"), "alice", "rec-v1");
        assertFalse(warm.isEmpty(), "暖链请求应正常放行");

        // 2. 加载 count=0 的 QPS 限流规则 → 后续 entry 全部被 Block
        FlowRule block = new FlowRule(RecommendService.SENTINEL_RESOURCE_FEED);
        block.setGrade(RuleConstant.FLOW_GRADE_QPS);
        block.setCount(0);
        FlowRuleManager.loadRules(List.of(block));
        try {
            List<RecommendPostVO> result = service.recommend(ctx(2L, "bob"), "bob", "rec-v1");

            assertFalse(result.isEmpty(), "限流触发应降级热门兜底，而非 500");
            assertEquals("推荐服务降级·热门兜底", result.get(0).getReason());
            // 限流拦截发生在漏斗之前：recall 全程只被暖链那次调用，本请求未进入漏斗
            verify(recallService, times(1)).recall(any());
        } finally {
            FlowRuleManager.loadRules(Collections.emptyList());
        }
    }
}
