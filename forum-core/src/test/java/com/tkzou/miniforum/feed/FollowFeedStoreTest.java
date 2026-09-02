package com.tkzou.miniforum.feed;
import com.tkzou.miniforum.feed.impl.InMemoryFollowFeedStore;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.impl.InMemoryFollowRepository;
import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;
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
 * 覆盖：fanout 扇出（只写已建流用户）/ onFollow 回填建流 / 游标读取（maxId/sinceId 排他边界）/ 封顶淘汰 /
 * 大V分流（isBigV 阈值判定 + 懒扫描初始化）/ 大V outbox 读取（getAuthorTimeline）。
 */
class FollowFeedStoreTest {

    private InMemoryFollowRepository followRepository;
    private InMemoryPostRepository postRepository;
    private InMemoryFollowFeedStore store;

    @BeforeEach
    void setUp() {
        followRepository = new InMemoryFollowRepository();
        postRepository = new InMemoryPostRepository();
        store = new InMemoryFollowFeedStore(followRepository, postRepository, 5); // 小 cap 便于测封顶
    }

    private void follow(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setFollowerId(followerId);
        f.setFolloweeId(followeeId);
        followRepository.save(f);
    }

    /** 发一帖（作者已发布、未删除），id 显式给定 */
    private void publishPost(Long id, Long authorId) {
        Post p = new Post();
        p.setId(id);
        p.setAuthorId(authorId);
        p.setStatus(Post.STATUS_PUBLISHED);
        postRepository.save(p);
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

    // ---------- 大V分流（isBigV / bigVIds / refreshBigV / getAuthorTimeline / writeOutbox） ----------

    @Test
    void isBigV_shouldFlipAtThreshold() {
        InMemoryFollowFeedStore lowThreshold = new InMemoryFollowFeedStore(followRepository, postRepository, 5, 2);
        follow(1L, 100L); // 作者 100 有 1 个粉丝 → 低于阈值 2
        assertFalse(lowThreshold.isBigV(100L));
        follow(2L, 100L); // 2 个粉丝 → 达到阈值 → 大V
        lowThreshold.refreshBigV(100L); // 事件驱动：关注/取关后由 FollowService 调用维护集合
        assertTrue(lowThreshold.isBigV(100L));
    }

    @Test
    void isBigV_defaultThresholdNeverSkips() {
        follow(1L, 100L);
        assertFalse(store.isBigV(100L)); // 2 参构造默认阈值 10 万
    }

    @Test
    void fanout_shouldSkipForBigV() {
        InMemoryFollowFeedStore lowThreshold = new InMemoryFollowFeedStore(followRepository, postRepository, 5, 1);
        follow(1L, 100L); // 作者 100 有 1 个粉丝 → 达到阈值 1 → 大V
        lowThreshold.onFollow(1L, List.of()); // 建流
        lowThreshold.fanout(100L, 200L); // 大V跳过扇出
        assertTrue(lowThreshold.getInbox(1L, null, 10).isEmpty());
    }

    @Test
    void refreshBigV_shouldAddThenRemoveByThreshold() {
        InMemoryFollowFeedStore lowThreshold = new InMemoryFollowFeedStore(followRepository, postRepository, 5, 2);
        follow(1L, 100L);
        assertFalse(lowThreshold.isBigV(100L));
        follow(2L, 100L); // 2 粉丝 → 跨阈值
        lowThreshold.refreshBigV(100L);
        assertTrue(lowThreshold.isBigV(100L));
        followRepository.deleteByUserId(2L); // 删掉一个粉丝 → 掉出阈值
        lowThreshold.refreshBigV(100L);
        assertFalse(lowThreshold.isBigV(100L));
    }

    @Test
    void bigVIds_shouldReturnOnlyBigVs() {
        InMemoryFollowFeedStore lowThreshold = new InMemoryFollowFeedStore(followRepository, postRepository, 5, 2);
        follow(1L, 100L);
        follow(2L, 100L); // 100 → 2 粉丝 ≥ 2 → 大V
        follow(3L, 200L); // 200 → 1 粉丝 < 2 → 普通
        assertEquals(List.of(100L), lowThreshold.bigVIds().stream().sorted().collect(Collectors.toList()));
    }

    @Test
    void getAuthorTimeline_shouldReturnNewestBeforeMaxId() {
        // 作者 100 发帖 id 1..6（最新在前），maxId 排他 + maxCount 截断 + 只读已发布可见帖
        publishPost(1L, 100L);
        publishPost(2L, 100L);
        publishPost(3L, 100L);
        publishPost(4L, 100L);
        publishPost(5L, 100L);
        Post deleted = new Post();
        deleted.setId(6L);
        deleted.setAuthorId(100L);
        deleted.setDeleted(true); // 已删除 → 拉流时过滤
        postRepository.save(deleted);

        assertEquals(List.of(5L, 4L, 3L), store.getAuthorTimeline(100L, null, 3)); // 最新在前 + 截断
        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), store.getAuthorTimeline(100L, null, 10)); // 不含已删 6
        assertEquals(List.of(3L, 2L, 1L), store.getAuthorTimeline(100L, 4L, 10)); // 严格 < 4
        assertTrue(store.getAuthorTimeline(200L, null, 10).isEmpty()); // 无帖作者 → 空
    }

    @Test
    void writeOutbox_shouldBeNoOpInMemory() {
        // 内存实现 outbox = Post 表本身：writeOutbox 是 no-op，getAuthorTimeline 直接从 Post 读
        store.writeOutbox(100L, 7L);
        assertTrue(store.getAuthorTimeline(100L, null, 10).isEmpty()); // 没有落 Post 就不存在
    }
}
