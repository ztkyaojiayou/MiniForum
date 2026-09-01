package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.repository.impl.InMemoryCommentRepository;
import com.tkzou.miniforum.repository.impl.InMemoryFavoriteRepository;
import com.tkzou.miniforum.repository.impl.InMemoryLikeRepository;
import com.tkzou.miniforum.repository.impl.InMemoryNotificationRepository;
import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;
import com.tkzou.miniforum.repository.impl.InMemoryUserRepository;
import com.tkzou.miniforum.idempotency.IdempotencyStore;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.impl.InMemoryBehaviorLogger;
import com.tkzou.miniforum.recommend.mq.BehaviorEventQueue;
import com.tkzou.miniforum.recommend.mq.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 点赞并发正确性测试（P0-2/P0-3）
 * <p>
 * - P0-2 计数原子化：多用户并发点赞同一帖 → 最终计数 = 点赞数（computeIfPresent/原子自增不丢更新），
 *   并已落库（修复 like 从不 save 的 prod 不落库 bug）。
 * - P0-3 判重原子化：同一用户并发点赞 → 恰一个成功、其余抛"已点过赞"，计数 = 1。
 */
class PostServiceConcurrencyTest {

    private PostRepository postRepository;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = new InMemoryPostRepository();
        LikeRepository likeRepository = new InMemoryLikeRepository();
        CommentRepository commentRepository = new InMemoryCommentRepository();
        FavoriteRepository favoriteRepository = new InMemoryFavoriteRepository();
        UserRepository userRepository = new InMemoryUserRepository();
        NotificationService notificationService = new NotificationService(new InMemoryNotificationRepository());
        BehaviorLogger behaviorLogger = new InMemoryBehaviorLogger(new BehaviorLogRepository(), new BehaviorEventQueue());
        PostAssembler postAssembler = new PostAssembler(postRepository, likeRepository, commentRepository, favoriteRepository);
        postService = new PostService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository, behaviorLogger,
                mock(OutboxStore.class), // 并发点赞不涉及发帖 Outbox，用 mock 隔离 recommend-server 依赖
                postAssembler, mock(IdempotencyStore.class));
    }

    private PostVO createPublishedPost() {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle("并发点赞测试");
        dto.setContent("内容");
        dto.setCategory("科技");
        dto.setPublish(true);
        return postService.createPost(dto, "alice", 1L);
    }

    @Test
    void concurrentLikes_fromDifferentUsers_shouldNotLoseCount() throws Exception {
        PostVO post = createPublishedPost();
        int n = 20;
        long postId = post.getId();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String username = "u" + i;
            long uid = 100L + i;
            futures.add(pool.submit(() -> {
                start.await();
                postService.like(postId, username, uid);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();

        Post stored = postRepository.findById(postId).orElseThrow();
        assertEquals(n, stored.getLikeCount(), "并发点赞计数不得丢失（读-改-写原子）");
    }

    @Test
    void concurrentLikes_fromSameUser_shouldInsertOnce() throws Exception {
        PostVO post = createPublishedPost();
        long postId = post.getId();
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    postService.like(postId, "bob", 2L);
                    successes.incrementAndGet();
                } catch (BusinessException e) {
                    duplicates.incrementAndGet(); // "你已经点过赞了"
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();

        assertEquals(1, successes.get(), "同一用户并发点赞恰一次成功");
        assertEquals(n - 1, duplicates.get());
        Post stored = postRepository.findById(postId).orElseThrow();
        assertEquals(1L, stored.getLikeCount());
    }

    @Test
    void like_unlike_relike_shouldSucceed() {
        // 取消点赞需同步清理去重索引（byKey），否则重新点赞会被误判为"已点过赞"
        PostVO post = createPublishedPost();
        postService.like(post.getId(), "bob", 2L);
        postService.unlike(post.getId(), "bob", 2L);
        PostVO reliked = postService.like(post.getId(), "bob", 2L);
        assertEquals(1L, reliked.getLikeCount());
    }
}
