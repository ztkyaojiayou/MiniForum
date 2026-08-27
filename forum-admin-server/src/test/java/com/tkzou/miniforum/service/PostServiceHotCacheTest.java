package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.stream.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热门帖 postId 缓存测试（P1-1）
 * <p>
 * TTL 内命中（全表扫只发生一次）、排序正确（viewCount 降序）、ttl=0 禁用、
 * 用户相关 VO 每请求现算（toVO 按 limit 调用）。
 */
class PostServiceHotCacheTest {

    private PostRepository postRepository;
    private PostAssembler postAssembler;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postAssembler = mock(PostAssembler.class);
        postService = new PostService(postRepository, mock(LikeRepository.class), mock(CommentRepository.class),
                mock(FavoriteRepository.class), mock(NotificationService.class), mock(UserRepository.class),
                mock(BehaviorLogger.class), mock(OutboxStore.class), postAssembler);
        postService.setHotPostIdsCacheTtlMs(10_000L); // 默认启用：对齐 application.yml
    }

    private Post post(long id, long viewCount, LocalDateTime createdAt) {
        Post p = new Post();
        p.setId(id);
        p.setViewCount(viewCount);
        p.setCreatedAt(createdAt);
        p.setStatus(Post.STATUS_PUBLISHED);
        return p;
    }

    @Test
    void getHotPosts_isCachedWithinTtl() {
        Post p1 = post(1L, 10, LocalDateTime.now());
        when(postRepository.findAll()).thenReturn(List.of(p1));
        when(postRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));

        postService.getHotPosts(10, "alice");
        postService.getHotPosts(10, "alice");
        verify(postRepository, times(1)).findAll(); // TTL 内全表扫只发生一次
        verify(postAssembler, times(2)).toVO(eq(p1), eq("alice")); // VO 每请求现算
    }

    @Test
    void getHotPosts_ordersByViewCountDesc() {
        LocalDateTime now = LocalDateTime.now();
        Post low = post(1L, 10, now);
        Post high = post(2L, 30, now);
        Post mid = post(3L, 20, now);
        when(postRepository.findAll()).thenReturn(List.of(low, high, mid));
        when(postRepository.findById(any())).thenAnswer(inv -> Optional.of(
                List.of(low, high, mid).stream().filter(p -> p.getId().equals(inv.getArgument(0))).findFirst().get()));
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));

        postService.getHotPosts(10, "alice");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postAssembler, times(3)).toVO(captor.capture(), eq("alice"));
        List<Post> ordered = captor.getAllValues();
        assertEquals(30L, ordered.get(0).getViewCount(), "阅读量最高应排第一");
        assertEquals(20L, ordered.get(1).getViewCount());
        assertEquals(10L, ordered.get(2).getViewCount());
    }

    @Test
    void getHotPosts_cacheDisabledWhenTtlZero() {
        Post p1 = post(1L, 10, LocalDateTime.now());
        when(postRepository.findAll()).thenReturn(List.of(p1));
        when(postRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));
        postService.setHotPostIdsCacheTtlMs(0L);

        postService.getHotPosts(10, "alice");
        postService.getHotPosts(10, "alice");
        verify(postRepository, times(2)).findAll(); // ttl=0 每次现算
    }
}
