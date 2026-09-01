package com.tkzou.miniforum.repository;
import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;

import com.tkzou.miniforum.entity.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostRepository 按作者分桶索引单元测试
 * <p>
 * 覆盖：findByAuthorId 倒序 / 索引随 save 维护 / deleteById 移出 / importAll 重建 / 同 id 不重复 / null createdAt。
 */
class PostRepositoryTest {

    private PostRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPostRepository();
    }

    private Post savePost(Long authorId, String title, LocalDateTime createdAt) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setTitle(title);
        p.setStatus(Post.STATUS_PUBLISHED);
        if (createdAt != null) {
            p.setCreatedAt(createdAt);
        }
        return repository.save(p);
    }

    @Test
    void findByAuthorId_shouldReturnAuthorsPostsNewestFirst() {
        savePost(1L, "a1", LocalDateTime.of(2026, 1, 1, 10, 0));
        savePost(2L, "b1", LocalDateTime.of(2026, 1, 1, 11, 0));
        savePost(1L, "a2", LocalDateTime.of(2026, 1, 2, 10, 0));

        List<Post> byA = repository.findByAuthorId(1L);
        assertEquals(2, byA.size());
        assertEquals("a2", byA.get(0).getTitle()); // 最新在前
        assertEquals("a1", byA.get(1).getTitle());
    }

    @Test
    void findByAuthorId_shouldReturnEmptyForUnknownAuthor() {
        assertTrue(repository.findByAuthorId(99L).isEmpty());
    }

    @Test
    void save_shouldNotDuplicateSameId() {
        Post p = savePost(1L, "t", LocalDateTime.now());
        repository.save(p); // 同 id 重复存（updatePost/like 等回写路径）
        assertEquals(1, repository.findByAuthorId(1L).size());
        assertEquals(1, repository.count());
    }

    @Test
    void deleteById_shouldRemoveFromIndexAndStorage() {
        Post p = savePost(1L, "t", LocalDateTime.now());
        repository.deleteById(p.getId());
        assertTrue(repository.findByAuthorId(1L).isEmpty());
        assertTrue(repository.findById(p.getId()).isEmpty());
        assertEquals(0, repository.count());
    }

    @Test
    void deleteById_shouldCleanEmptyBucketKey() {
        Post p = savePost(1L, "t", LocalDateTime.now());
        repository.deleteById(p.getId());
        // 桶已删 key：再次 findByAuthorId 返回空且不残留空桶
        assertTrue(repository.findByAuthorId(1L).isEmpty());
    }

    @Test
    void importAll_shouldRebuildIndex() {
        Post p1 = savePost(1L, "a", LocalDateTime.now());
        savePost(2L, "b", LocalDateTime.now());
        repository.importAll(List.of(p1)); // 重建后只剩 p1
        assertEquals(1, repository.findByAuthorId(1L).size());
        assertTrue(repository.findByAuthorId(2L).isEmpty());
        assertEquals(1, repository.count());
    }

    @Test
    void findByAuthorId_shouldHandleNullCreatedAt() {
        savePost(1L, "t", null); // createdAt 为 null 不 NPE
        List<Post> byA = repository.findByAuthorId(1L);
        assertEquals(1, byA.size());
        assertEquals("t", byA.get(0).getTitle());
    }

    @Test
    void incrementLikeCount_shouldReturnNewCountAndClampAtZero() {
        Post p = savePost(1L, "t", LocalDateTime.now());
        assertEquals(1L, repository.incrementLikeCount(p.getId(), 1));
        assertEquals(2L, repository.incrementLikeCount(p.getId(), 1));
        assertEquals(1L, repository.incrementLikeCount(p.getId(), -1));
        assertEquals(0L, repository.incrementLikeCount(p.getId(), -5)); // 不为负
        Post stored = repository.findById(p.getId()).orElseThrow();
        assertEquals(0L, stored.getLikeCount());
    }

    @Test
    void incrementViewCount_shouldIncrementAndMutateSharedReference() {
        Post p = savePost(1L, "t", LocalDateTime.now());
        assertEquals(1L, repository.incrementViewCount(p.getId(), 1));
        assertEquals(1L, p.getViewCount()); // 原地更新共享引用（缓存/读取方持同一对象自动可见）
    }

    @Test
    void concurrentIncrements_shouldNotLoseUpdates() throws Exception {
        Post p = savePost(1L, "t", LocalDateTime.now());
        int n = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return repository.incrementLikeCount(p.getId(), 1);
            }));
        }
        start.countDown();
        for (Future<Long> f : futures) {
            f.get();
        }
        pool.shutdownNow();
        Post stored = repository.findById(p.getId()).orElseThrow();
        assertEquals(n, stored.getLikeCount()); // computeIfPresent 原子累加：并发读-改-写不丢更新
    }
}
