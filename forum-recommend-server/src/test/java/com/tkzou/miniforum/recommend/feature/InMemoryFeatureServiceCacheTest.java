package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 画像/物品特征短 TTL 缓存测试（P0-1 / P0-2）
 * <p>
 * ① TTL 内第二次读取命中缓存（免重复现算聚合）；② TTL≤0 时禁用缓存（测试/调试可关）。
 * 对齐高并发铁律"能预计算的不实时算"：现算只发生在缓存 miss 时。
 */
class InMemoryFeatureServiceCacheTest {

    private UserProfileAggregator aggregator;
    private PostRepository postRepository;
    private FollowRepository followRepository;
    private LikeRepository likeRepository;
    private CommentRepository commentRepository;
    private FavoriteRepository favoriteRepository;
    private RealtimeFeatureStore realtimeFeatureStore;
    private ConfigService configService;
    private BehaviorLogRepository behaviorLogRepository;
    private InMemoryFeatureService featureService;

    @BeforeEach
    void setUp() {
        aggregator = mock(UserProfileAggregator.class);
        postRepository = mock(PostRepository.class);
        followRepository = mock(FollowRepository.class);
        likeRepository = mock(LikeRepository.class);
        commentRepository = mock(CommentRepository.class);
        favoriteRepository = mock(FavoriteRepository.class);
        realtimeFeatureStore = mock(RealtimeFeatureStore.class);
        configService = mock(ConfigService.class);
        behaviorLogRepository = mock(BehaviorLogRepository.class);
        when(configService.current()).thenReturn(RecConfig.defaults());
        featureService = new InMemoryFeatureService(aggregator, postRepository, followRepository,
                likeRepository, commentRepository, favoriteRepository, realtimeFeatureStore,
                configService, behaviorLogRepository);
        // 默认启用缓存：画像 30s、物品特征 5s（对齐 application.yml 默认值）
        ReflectionTestUtils.setField(featureService, "profileCacheTtlMs", 30_000L);
        ReflectionTestUtils.setField(featureService, "itemFeatureCacheTtlMs", 5_000L);
    }

    @Test
    void userProfile_isCachedWithinTtl() {
        UserProfile p = mock(UserProfile.class);
        when(aggregator.build(1L)).thenReturn(p);
        assertSame(p, featureService.userProfile(1L), "首次应现算");
        assertSame(p, featureService.userProfile(1L), "TTL 内第二次应命中缓存（同一对象）");
        verify(aggregator, times(1)).build(1L);
    }

    @Test
    void userProfile_cacheDisabledWhenTtlZero() {
        ReflectionTestUtils.setField(featureService, "profileCacheTtlMs", 0L);
        when(aggregator.build(1L)).thenReturn(mock(UserProfile.class));
        featureService.userProfile(1L);
        featureService.userProfile(1L);
        verify(aggregator, times(2)).build(1L);
    }

    @Test
    void itemFeature_isCachedWithinTtl() {
        Post post = new Post();
        post.setId(1L);
        post.setAuthorId(7L);
        post.setCategory("科技");
        post.setTopics(List.of("AI"));
        post.setCreatedAt(LocalDateTime.now());
        post.setViewCount(10);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.findAll()).thenReturn(List.of()); // 转发计数=0
        when(behaviorLogRepository.findByPostId(1L)).thenReturn(List.of()); // 阅读时长=0
        when(followRepository.countByFolloweeId(anyLong())).thenReturn(100L);
        when(likeRepository.countByPostId(1L)).thenReturn(5L);
        when(commentRepository.countByPostId(1L)).thenReturn(0L);
        when(favoriteRepository.countByPostId(1L)).thenReturn(0L);

        ItemFeature f1 = featureService.itemFeature(1L);
        ItemFeature f2 = featureService.itemFeature(1L);
        assertSame(f1, f2, "TTL 内第二次应命中缓存（同一对象）");
        // 第二次命中缓存 → count 聚合只应执行一次
        verify(likeRepository, times(1)).countByPostId(1L);
    }
}
