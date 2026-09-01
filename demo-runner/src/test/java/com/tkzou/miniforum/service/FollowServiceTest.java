package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.common.CursorPage;
import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.RecommendUserVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.feed.FollowFeedStore;
import com.tkzou.miniforum.feed.impl.InMemoryFollowFeedStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.impl.InMemoryFollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.impl.InMemoryNotificationRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.impl.InMemoryBehaviorLogger;
import com.tkzou.miniforum.idempotency.impl.InMemoryIdempotencyStore;
import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import com.tkzou.miniforum.recommend.stream.impl.InMemoryOutboxStore;
import com.tkzou.miniforum.recommend.stream.impl.InMemoryPostCreatedProducer;
import com.tkzou.miniforum.recommend.stream.OutboxStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tkzou.miniforum.repository.impl.InMemoryCommentRepository;
import com.tkzou.miniforum.repository.impl.InMemoryFavoriteRepository;
import com.tkzou.miniforum.repository.impl.InMemoryLikeRepository;
import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;
import com.tkzou.miniforum.repository.impl.InMemoryUserRepository;
import com.tkzou.miniforum.recommend.stream.impl.FanoutPostCreatedConsumer;
import com.tkzou.miniforum.recommend.stream.PostCreatedEventBus;
import com.tkzou.miniforum.recommend.stream.PostCreatedConsumerRegistrar;

/**
 * 关注流推模式 + 游标分页闭环单元测试
 * <p>
 * 覆盖：首读建流 / 建流后 fanout / 关注回填 / 取关读取兜底 / 空流 / 游标翻页不丢不重 / since 增量刷新。
 */
class FollowServiceTest {

    private PostRepository postRepository;
    private InMemoryFollowRepository followRepository;
    private UserRepository userRepository;
    private FollowFeedStore followFeedStore;
    private PostService postService;
    private FollowService followService;

    @BeforeEach
    void setUp() {
        postRepository = new InMemoryPostRepository();
        followRepository = new InMemoryFollowRepository();
        userRepository = new InMemoryUserRepository();
        followFeedStore = new InMemoryFollowFeedStore(followRepository, 500);
        LikeRepository likeRepository = new InMemoryLikeRepository();
        CommentRepository commentRepository = new InMemoryCommentRepository();
        FavoriteRepository favoriteRepository = new InMemoryFavoriteRepository();
        NotificationRepository notificationRepository = new InMemoryNotificationRepository();
        NotificationService notificationService = new NotificationService(notificationRepository);
        BehaviorLogger behaviorLogger = new InMemoryBehaviorLogger(new BehaviorLogRepository(), new BehaviorEventQueue());
        PostCreatedEventBus eventBus = new PostCreatedEventBus();
        new PostCreatedConsumerRegistrar(eventBus, List.of(new FanoutPostCreatedConsumer(followFeedStore))); // 关注流扇出订阅（Registrar 统一注册）
        OutboxStore outboxStore = new InMemoryOutboxStore(new InMemoryPostCreatedProducer(eventBus));
        PostAssembler postAssembler = new PostAssembler(postRepository, likeRepository, commentRepository, favoriteRepository);
        postService = new PostService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository, behaviorLogger, outboxStore, postAssembler,
                new InMemoryIdempotencyStore(300_000L));
        followService = new FollowService(followRepository, userRepository, postRepository,
                likeRepository, commentRepository, favoriteRepository, notificationService, behaviorLogger,
                followFeedStore, 500);
    }

    private void createUser(String username, Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        userRepository.save(user);
    }

    private PostCreateDTO dto(String title, String content) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setCategory("科技");
        dto.setPublish(true);
        return dto;
    }

    @Test
    void getFollowFeed_shouldBuildOnFirstReadAndReturnCursorPage() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        PostVO post = postService.createPost(dto("bob 的帖子", "内容"), "bob", bobId);

        CursorPage<PostVO> feed = followService.getFollowFeed(followerId, null, 10, "alice");
        assertEquals(1, feed.getRecords().size());
        assertEquals("bob 的帖子", feed.getRecords().get(0).getTitle());
        assertEquals(post.getId(), feed.getNextMaxId());
        assertFalse(feed.isHasMore());
        // 首次读取后 inbox 已建流
        assertTrue(followFeedStore.isBuilt(followerId));

        // 再从末位游标取：无更早帖 → 空
        CursorPage<PostVO> next = followService.getFollowFeed(followerId, post.getId(), 10, "alice");
        assertTrue(next.getRecords().isEmpty());
        assertNull(next.getNextMaxId());
        assertFalse(next.isHasMore());
    }

    @Test
    void getFollowFeed_shouldFanoutNewPostsToBuiltInbox() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("旧帖", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, null, 10, "alice"); // 建流

        postService.createPost(dto("新帖", "内容2"), "bob", bobId); // 建流后 fanout 生效
        CursorPage<PostVO> feed = followService.getFollowFeed(followerId, null, 10, "alice");
        assertEquals(2, feed.getRecords().size());
        assertEquals("新帖", feed.getRecords().get(0).getTitle()); // 最新在前
    }

    @Test
    void follow_shouldBackfillRecentPostsWhenInboxBuilt() {
        Long followerId = 1L;
        Long bobId = 2L;
        Long carolId = 3L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        createUser("carol", carolId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("bob 帖", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, null, 10, "alice"); // 建流

        postService.createPost(dto("carol 旧帖", "内容"), "carol", carolId);
        followService.follow(followerId, carolId, "alice"); // inbox 已建 → 回填 carol 近期帖
        CursorPage<PostVO> feed = followService.getFollowFeed(followerId, null, 10, "alice");
        assertEquals(2, feed.getRecords().size()); // bob 帖 + carol 旧帖
    }

    @Test
    void getFollowFeed_shouldHideUnfollowedAuthorPosts() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("bob 的帖子", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, null, 10, "alice"); // 建流

        followService.unfollow(followerId, bobId);
        CursorPage<PostVO> feed = followService.getFollowFeed(followerId, null, 10, "alice");
        assertTrue(feed.getRecords().isEmpty()); // 读取兜底过滤掉取关作者
        assertFalse(feed.isHasMore());
    }

    @Test
    void getFollowFeed_shouldReturnEmptyWhenNotFollowingAnyone() {
        Long followerId = 1L;
        createUser("alice", followerId);
        CursorPage<PostVO> feed = followService.getFollowFeed(followerId, null, 10, "alice");
        assertTrue(feed.getRecords().isEmpty());
        assertNull(feed.getNextMaxId());
        assertFalse(feed.isHasMore());
    }

    @Test
    void getFollowFeed_shouldPageWithCursorWithoutDupOrMiss() {
        // 多作者 + 部分取关：过滤后 hasMore/游标仍正确，不丢不重（游标"过滤后计算"回归）
        Long followerId = 1L;
        Long bobId = 2L;
        Long carolId = 3L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        createUser("carol", carolId);
        followService.follow(followerId, bobId, "alice");
        followService.follow(followerId, carolId, "alice");
        postService.createPost(dto("bob1", "内容"), "bob", bobId);
        postService.createPost(dto("bob2", "内容"), "bob", bobId);
        postService.createPost(dto("bob3", "内容"), "bob", bobId);
        postService.createPost(dto("carol1", "内容"), "carol", carolId);
        postService.createPost(dto("carol2", "内容"), "carol", carolId);
        followService.getFollowFeed(followerId, null, 10, "alice"); // 建流

        // 取关 carol → 读取时过滤掉 carol 的帖，但 bob 的 3 帖应完整可翻
        followService.unfollow(followerId, carolId);

        CursorPage<PostVO> page1 = followService.getFollowFeed(followerId, null, 2, "alice");
        assertEquals(List.of("bob3", "bob2"),
                page1.getRecords().stream().map(PostVO::getTitle).collect(Collectors.toList()));
        assertTrue(page1.isHasMore());
        assertTrue(page1.getNextMaxId() != null);

        CursorPage<PostVO> page2 = followService.getFollowFeed(followerId, page1.getNextMaxId(), 2, "alice");
        assertEquals(List.of("bob1"),
                page2.getRecords().stream().map(PostVO::getTitle).collect(Collectors.toList()));
        assertFalse(page2.isHasMore()); // 末页：hasMore=false 是停止信号
        assertTrue(page2.getNextMaxId() != null); // 末页仍有 lastId 游标（仅当页空才为 null）
    }

    @Test
    void getFollowFeedSince_shouldReturnNewPostsAfterSinceId() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        PostVO old = postService.createPost(dto("旧帖", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, null, 10, "alice"); // 建流

        postService.createPost(dto("新帖", "内容2"), "bob", bobId); // 建流后 fanout 生效
        List<PostVO> since = followService.getFollowFeedSince(followerId, old.getId(), 10, "alice");
        assertEquals(1, since.size());
        assertEquals("新帖", since.get(0).getTitle()); // 不含 == sinceId 的旧帖
    }

    @Test
    void getFollowFeedSince_shouldBuildFirstWhenNeverOpened() {
        // 用户从未打开关注 Tab（未建流）就轮询 since：应先建流再增量
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        PostVO post = postService.createPost(dto("bob 帖", "内容"), "bob", bobId);

        List<PostVO> since = followService.getFollowFeedSince(followerId, 0L, 10, "alice");
        assertEquals(1, since.size()); // 建流后增量，返回全部比 sinceId=0 新的帖
        assertEquals("bob 帖", since.get(0).getTitle());
        assertTrue(followFeedStore.isBuilt(followerId));
    }

    // ---------- 推荐关注（社交卡） ----------

    @Test
    void suggestFollows_shouldReturnSecondDegreeByCommonCount() {
        Long me = 1L, bob = 2L, carol = 3L, dave = 4L;
        createUser("alice", me);
        createUser("bob", bob);
        createUser("carol", carol);
        createUser("dave", dave);
        followService.follow(me, bob, "alice");
        followService.follow(me, carol, "alice");
        followService.follow(bob, dave, "bob");
        followService.follow(carol, dave, "carol"); // dave 被 bob、carol 共同关注

        List<RecommendUserVO> recs = followService.suggestFollows(me, 10);
        assertEquals(1, recs.size());
        assertEquals(dave, recs.get(0).getId());
        assertEquals(2, recs.get(0).getCommonFollowCount());
        assertEquals("2 位你关注的人关注了 TA", recs.get(0).getReason());
        assertFalse(recs.get(0).isFollowed());
    }

    @Test
    void suggestFollows_shouldExcludeSelfAndAlreadyFollowed() {
        Long me = 1L, bob = 2L, carol = 3L;
        createUser("alice", me);
        createUser("bob", bob);
        createUser("carol", carol);
        followService.follow(me, bob, "alice");
        followService.follow(bob, me, "bob");     // bob 关注 alice（自己）→ 排除
        followService.follow(bob, carol, "bob");  // bob 关注 carol → 推荐
        List<RecommendUserVO> recs = followService.suggestFollows(me, 10);
        assertEquals(1, recs.size());
        assertEquals(carol, recs.get(0).getId());
    }

    @Test
    void suggestFollows_shouldReturnEmptyWhenNotFollowingAnyone() {
        Long me = 1L;
        createUser("alice", me);
        assertTrue(followService.suggestFollows(me, 10).isEmpty());
    }

    @Test
    void suggestFollows_shouldOrderByCommonCountDesc() {
        Long me = 1L, bob = 2L, carol = 3L, dave = 4L, eve = 5L;
        createUser("alice", me);
        createUser("bob", bob);
        createUser("carol", carol);
        createUser("dave", dave);
        createUser("eve", eve);
        followService.follow(me, bob, "alice");
        followService.follow(me, carol, "alice");
        followService.follow(bob, dave, "bob");
        followService.follow(bob, eve, "bob");
        followService.follow(carol, eve, "carol"); // eve 共同 2 > dave 共同 1

        List<RecommendUserVO> recs = followService.suggestFollows(me, 10);
        assertEquals(2, recs.size());
        assertEquals(eve, recs.get(0).getId());
        assertEquals(dave, recs.get(1).getId());
    }

    @Test
    void suggestFollows_shouldTransmitProfileFieldsAndClampLimit() {
        Long me = 1L, bob = 2L, daveId = 4L;
        createUser("alice", me);
        createUser("bob", bob);
        User dave = new User();
        dave.setId(daveId);
        dave.setUsername("dave");
        dave.setNickname("戴夫");
        dave.setAvatar("👨");
        userRepository.save(dave);
        followService.follow(me, bob, "alice");
        followService.follow(bob, daveId, "bob");

        List<RecommendUserVO> recs = followService.suggestFollows(me, 9999); // limit 超范围 → clamp，不抛异常
        assertEquals(1, recs.size());
        assertEquals(daveId, recs.get(0).getId());
        assertEquals("戴夫", recs.get(0).getNickname());
        assertEquals("👨", recs.get(0).getAvatar());
    }
}
