package com.tkzou.miniforum.feed;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.repository.InMemoryFollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关注流 inbox 内存实现单元测试
 * <p>
 * 覆盖：fanout 扇出（只写已建流用户）/ onFollow 回填建流 / 游标读取（maxId/sinceId 排他边界）/ 封顶淘汰。
 */
class FollowFeedStoreTest {

    private InMemoryFollowRepository followRepository;
    private InMemoryFollowFeedStore store;

    @BeforeEach
    void setUp() {
        followRepository = new InMemoryFollowRepository();
        store = new InMemoryFollowFeedStore(followRepository, 5); // 小 cap 便于测封顶
    }

    private void follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setFollowerId(followerId);
        f.setFolloweeId(followeeId);
        followRepository.save(f);
    }

    @Test
    void fanout_shouldSkipNotBuiltFollower() {
        follow(1L, 100L); // 用户 1 关注作者 100
        store.fanout(100L, 200L);
        // 未建流时 fanout 被跳过：不建"半成品流"
        assertFalse(store.isBuilt(1L));
        assertTrue(store.getInbox(1L, null, 10).isEmpty());
    }

    @Test
    void fanout_shouldWriteToBuiltFollower() {
        follow(1L, 100L);
        store.onFollow(1L, List.of()); // 空列表也标记已建
        assertTrue(store.isBuilt(1L));
        store.fanout(100L, 200L);
        assertEquals(List.of(200L), store.getInbox(1L, null, 10));
    }

    @Test
    void onFollow_shouldBuildAndMerge() {
        store.onFollow(1L, List.of(10L, 20L));
        store.onFollow(1L, List.of(30L)); // 幂等合并
        List<Long> inbox = store.getInbox(1L, null, 10);
        assertEquals(3, inbox.size());
        assertEquals(30L, inbox.get(0)); // 最新（最大 postId）在前
        assertTrue(inbox.containsAll(List.of(10L, 20L, 30L)));
    }

    @Test
    void getInbox_shouldRespectMaxCount() {
        store.onFollow(1L, List.of(1L, 2L, 3L));
        assertEquals(List.of(3L, 2L), store.getInbox(1L, null, 2)); // 最新在前且截断
    }

    @Test
    void getInbox_shouldRespectMaxIdCursorExclusive() {
        store.onFollow(1L, List.of(1L, 2L, 3L, 4L, 5L));
        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), store.getInbox(1L, null, 10)); // 从头：最新在前
        assertEquals(List.of(3L, 2L, 1L), store.getInbox(1L, 4L, 10)); // 严格 < 4，不含 4
        assertEquals(List.of(1L), store.getInbox(1L, 2L, 10));
        assertTrue(store.getInbox(1L, 1L, 10).isEmpty()); // 无 < 1
        assertTrue(store.getInbox(1L, null, 0).isEmpty());
    }

    @Test
    void getInboxAfter_shouldRespectSinceIdExclusive() {
        store.onFollow(1L, List.of(1L, 2L, 3L, 4L, 5L));
        assertEquals(List.of(5L, 4L, 3L, 2L), store.getInboxAfter(1L, 1L, 10)); // 严格 > 1，不含 1
        assertEquals(List.of(5L), store.getInboxAfter(1L, 4L, 10));
        assertTrue(store.getInboxAfter(1L, 5L, 10).isEmpty()); // 无 > 5
        assertTrue(store.getInboxAfter(1L, null, 10).isEmpty()); // null since → 空
    }

    @Test
    void getInbox_shouldReturnEmptyWhenNotBuilt() {
        assertTrue(store.getInbox(1L, null, 10).isEmpty());
        assertTrue(store.getInboxAfter(1L, 5L, 10).isEmpty());
    }

    @Test
    void onFollow_shouldTrimOldestWhenOverCap() {
        List<Long> ids = LongStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
        store.onFollow(1L, ids);
        List<Long> inbox = store.getInbox(1L, null, 10);
        assertEquals(5, inbox.size()); // cap=5，最旧 5 条被淘汰
        assertEquals(List.of(10L, 9L, 8L, 7L, 6L), inbox);
    }

    @Test
    void isBuilt_shouldReflectBuildState() {
        assertFalse(store.isBuilt(1L));
        store.onFollow(1L, List.of());
        assertTrue(store.isBuilt(1L));
    }
}
