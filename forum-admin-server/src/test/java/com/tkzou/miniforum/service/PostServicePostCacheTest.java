package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.stream.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单帖详情本地缓存测试（P3-3 热点 key）
 * <p>
 * TTL 内命中（回源只发生一次）、ttl=0 禁用、viewCount 每次读 +1（缓存实体引用）、
 * 写路径失效（updatePost 踢缓存）、异常不毒化缓存。
 * 风格对齐 PostServiceHotCacheTest（mock 仓储，演示实现零中间件）。
 */
class PostServicePostCacheTest {

    private PostRepository postRepository;
    private PostAssembler postAssembler;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postAssembler = mock(PostAssembler.class);
        UserRepository userRepository = mock(UserRepository.class);
        postService = new PostService(postRepository, mock(LikeRepository.class), mock(CommentRepository.class),
                mock(FavoriteRepository.class), mock(NotificationService.class), userRepository,
                mock(BehaviorLogger.class), mock(OutboxStore.class), postAssembler);
        postService.setPostCacheTtlMs(5_000L); // 默认启用：对齐 application.yml
        // getById 记 VIEW 行为会查 userRepository → stub 空 Optional（Mockito 默认返回 null 会 NPE）
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    }

    private Post publishedPost(long id) {
        Post p = new Post();
        p.setId(id);
        p.setStatus(Post.STATUS_PUBLISHED);
        p.setViewCount(0L);
        p.setAuthor("alice");
        p.setTitle("标题" + id);
        p.setContent("内容" + id);
        return p;
    }

    private void stubReadOf(Post p) {
        when(postRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(postRepository.save(any())).thenReturn(p);
        when(postAssembler.toVO(any(), any())).thenReturn(mock(PostVO.class));
    }

    @Test
    void getById_isCachedWithinTtl() {
        Post p = publishedPost(1L);
        stubReadOf(p);

        postService.getById(1L, "alice");
        postService.getById(1L, "alice");
        verify(postRepository, times(1)).findById(1L); // TTL 内只回源一次
    }

    @Test
    void getById_cacheDisabledWhenTtlZero() {
        postService.setPostCacheTtlMs(0L);
        Post p = publishedPost(1L);
        stubReadOf(p);

        postService.getById(1L, "alice");
        postService.getById(1L, "alice");
        verify(postRepository, times(2)).findById(1L); // ttl=0 每次回源
    }

    @Test
    void getById_viewCountIncrementsOnEachRead() {
        // 缓存的是实体引用：两次读同一实例，viewCount 各自原子 +1（走 incrementViewCount 落库，返回新计数回写本地对象）
        Post p = publishedPost(1L);
        stubReadOf(p);
        when(postRepository.incrementViewCount(1L, 1)).thenReturn(1L, 2L);

        postService.getById(1L, "alice");
        postService.getById(1L, "alice");
        verify(postRepository, times(2)).incrementViewCount(1L, 1); // 每次读 viewCount 原子 +1
        assertEquals(2L, p.getViewCount());
    }

    @Test
    void getById_invalidatedOnUpdatePost() {
        Post p = publishedPost(1L);
        stubReadOf(p);

        postService.getById(1L, "alice");
        // 写路径失效：updatePost（admin 权限）踢掉缓存 → 下一次 getById 重新回源
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle("改标题");
        dto.setContent("改内容");
        dto.setCategory("其他");
        dto.setPublish(true);
        postService.updatePost(1L, dto, "admin", true);
        postService.getById(1L, "alice");

        // 3 次回源 = ①首次 getById miss ②updatePost 自身 getPostOrThrow（写路径读新值，不经缓存）③失效后 getById 重新 miss。
        // 若 updatePost 未失效缓存，第③次会命中，总数只有 2——3 次恰好证明"写路径踢了缓存"。
        verify(postRepository, times(3)).findById(1L);
    }

    @Test
    void getById_missingPostNotCached() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> postService.getById(99L, "alice"));
        assertThrows(ResourceNotFoundException.class, () -> postService.getById(99L, "alice"));
        verify(postRepository, times(2)).findById(99L); // 异常不毒化缓存：每次都重新回源
    }
}
