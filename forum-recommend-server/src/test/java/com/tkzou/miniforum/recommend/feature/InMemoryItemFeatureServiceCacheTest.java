package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物品特征短 TTL 缓存测试（P0-2，feature 域）
 * <p>
 * ① TTL 内第二次读取命中缓存（免重复 count 聚合）；② TTL≤0 时禁用缓存（测试/调试可关）。
 * 对齐高并发铁律"能预计算的不实时算"：现算只发生在缓存 miss 时。
 * 注：作者粉丝数（authorFollowers）已移至 graph 域（SocialGraphService），本域不再涉及 FollowRepository。
 */
class InMemoryItemFeatureServiceCacheTest {

    private PostRepository postRepository;
    private LikeRepository likeRepository;
    private CommentRepository commentRepository;
    private FavoriteRepository favoriteRepository;
    private RealtimeFeatureStore realtimeFeatureStore;
    private ConfigService configService;
    private BehaviorLogRepository behaviorLogRepository;
    private InMemoryItemFeatureService itemFeatureService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        likeRepository = mock(LikeRepository.class);
        commentRepository = mock(CommentRepository.class);
        favoriteRepository = mock(FavoriteRepository.class);
        realtimeFeatureStore = mock(RealtimeFeatureStore.class);
        configService = mock(ConfigService.class);
        behaviorLogRepository = mock(BehaviorLogRepository.class);
        when(configService.current()).thenReturn(RecConfig.defaults());
        itemFeatureService = new InMemoryItemFeatureService(postRepository, likeRepository, commentRepository,
                favoriteRepository, realtimeFeatureStore, configService, behaviorLogRepository);
        itemFeatureService.setItemFeatureCacheTtlMs(5_000L); // 默认启用：对齐 application.yml
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
        when(likeRepository.countByPostId(1L)).thenReturn(5L);
        when(commentRepository.countByPostId(1L)).thenReturn(0L);
        when(favoriteRepository.countByPostId(1L)).thenReturn(0L);

        ItemFeature f1 = itemFeatureService.itemFeature(1L);
        ItemFeature f2 = itemFeatureService.itemFeature(1L);
        assertSame(f1, f2, "TTL 内第二次应命中缓存（同一对象）");
        // 第二次命中缓存 → count 聚合只应执行一次
        verify(likeRepository, times(1)).countByPostId(1L);
    }

    @Test
    void itemFeature_cacheDisabledWhenTtlZero() {
        itemFeatureService.setItemFeatureCacheTtlMs(0L);
        Post post = new Post();
        post.setId(1L);
        post.setCategory("科技");
        post.setCreatedAt(LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.findAll()).thenReturn(List.of());
        when(behaviorLogRepository.findByPostId(1L)).thenReturn(List.of());

        itemFeatureService.itemFeature(1L);
        itemFeatureService.itemFeature(1L);
        verify(likeRepository, times(2)).countByPostId(1L); // ttl=0 每次现算
    }
}
