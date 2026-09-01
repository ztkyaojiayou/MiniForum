package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.response.HotSearchVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 热搜整榜缓存测试（P1-1）
 * <p>
 * TTL 内命中（全表扫只发生一次 computeTop50 = 两窗口各扫一次）、ttl=0 禁用、
 * 按 limit 切片（rank 在完整榜上计算）、返回 list 防御拷贝。
 */
class HotSearchServiceCacheTest {

    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private SearchRecordRepository searchRecordRepository;
    private HotSearchService hotSearchService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        searchRecordRepository = mock(SearchRecordRepository.class);
        when(commentRepository.countByPostId(anyLong())).thenReturn(0L);
        when(searchRecordRepository.findTopKeywords(50)).thenReturn(List.of());
        hotSearchService = new HotSearchService(postRepository, commentRepository, searchRecordRepository);
        hotSearchService.setHotBoardCacheTtlMs(30_000L); // 默认启用：对齐 application.yml
    }

    private Post tagPost(long id, String tag, long viewCount) {
        Post p = new Post();
        p.setId(id);
        p.setTags(List.of(tag));
        // 用"5 分钟前"而非 now()：避开 aggregateHeat 窗口边界 !createdAt.isBefore(to) 的微妙时序，保证确定性
        p.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        p.setViewCount(viewCount);
        p.setStatus(Post.STATUS_PUBLISHED);
        return p;
    }

    @Test
    void getHotSearches_isCachedWithinTtl() {
        when(postRepository.findAll()).thenReturn(List.of(tagPost(1L, "科技", 100)));
        hotSearchService.getHotSearches(10);
        hotSearchService.getHotSearches(10);
        // 一次 computeTop50 = 两次 aggregateHeat（当前窗口 + 上一窗口），各扫一次全表；第二次命中缓存
        verify(postRepository, times(2)).findAll();
    }

    @Test
    void getHotSearches_cacheDisabledWhenTtlZero() {
        hotSearchService.setHotBoardCacheTtlMs(0L);
        when(postRepository.findAll()).thenReturn(List.of(tagPost(1L, "科技", 100)));
        hotSearchService.getHotSearches(10);
        hotSearchService.getHotSearches(10);
        verify(postRepository, times(4)).findAll(); // 两次现算 × 两窗口
    }

    @Test
    void getHotSearches_slicesByLimitWithGlobalRank() {
        List<Post> posts = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            posts.add(tagPost(i, "tag" + i, i * 10));
        }
        when(postRepository.findAll()).thenReturn(posts);

        List<HotSearchVO> top5 = hotSearchService.getHotSearches(5);
        assertEquals(5, top5.size());
        assertEquals(1, top5.get(0).getRank());
        assertEquals(5, top5.get(4).getRank());
        assertEquals("tag20", top5.get(0).getKeyword()); // viewCount 最高优先

        List<HotSearchVO> all = hotSearchService.getHotSearches(50);
        assertEquals(20, all.size());
    }

    @Test
    void getHotSearches_returnsDefensiveCopy() {
        when(postRepository.findAll()).thenReturn(List.of(tagPost(1L, "科技", 100)));
        List<HotSearchVO> first = hotSearchService.getHotSearches(10);
        first.clear(); // 修改返回 list（列表级防御拷贝：不影响缓存）
        List<HotSearchVO> second = hotSearchService.getHotSearches(10);
        assertFalse(second.isEmpty(), "返回 list 的修改不应影响缓存");
    }
}
