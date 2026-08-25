package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.feed.FollowFeedStore;
import com.tkzou.miniforum.feed.InMemoryFollowFeedStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.InMemoryFollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.InMemoryBehaviorLogger;
import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import com.tkzou.miniforum.recommend.stream.InMemoryPostCreatedNotifier;
import com.tkzou.miniforum.recommend.stream.PostCreatedNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关注流推模式闭环单元测试
 * <p>
 * 覆盖：首次读取回退+建流 / 建流后新帖 fanout 进 inbox / 关注回填 / 取关读取兜底 / 无关注空流。
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
        postRepository = new PostRepository();
        followRepository = new InMemoryFollowRepository();
        userRepository = new UserRepository();
        followFeedStore = new InMemoryFollowFeedStore(followRepository, 500);
        LikeRepository likeRepository = new LikeRepository();
        CommentRepository commentRepository = new CommentRepository();
        FavoriteRepository favoriteRepository = new FavoriteRepository();
        NotificationRepository notificationRepository = new NotificationRepository();
        NotificationService notificationService = new NotificationService(notificationRepository);
        BehaviorLogger behaviorLogger = new InMemoryBehaviorLogger(new BehaviorLogRepository(), new BehaviorEventQueue());
        PostCreatedNotifier notifier = new InMemoryPostCreatedNotifier(followFeedStore);
        postService = new PostService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository, behaviorLogger, notifier);
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
    void getFollowFeed_shouldFallbackAndBuildOnFirstRead() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("bob 的帖子", "内容"), "bob", bobId);

        PageResult<PostVO> feed = followService.getFollowFeed(followerId, 1, 10, "alice");
        assertEquals(1, feed.getTotal());
        assertEquals("bob 的帖子", feed.getRecords().get(0).getTitle());
        // 首次读取后 inbox 已建流
        assertTrue(followFeedStore.isBuilt(followerId));
    }

    @Test
    void getFollowFeed_shouldFanoutNewPostsToBuiltInbox() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("旧帖", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, 1, 10, "alice"); // 建流

        postService.createPost(dto("新帖", "内容2"), "bob", bobId); // 建流后 fanout 生效
        PageResult<PostVO> feed = followService.getFollowFeed(followerId, 1, 10, "alice");
        assertEquals(2, feed.getTotal());
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
        followService.getFollowFeed(followerId, 1, 10, "alice"); // 建流

        postService.createPost(dto("carol 旧帖", "内容"), "carol", carolId);
        followService.follow(followerId, carolId, "alice"); // inbox 已建 → 回填 carol 近期帖
        PageResult<PostVO> feed = followService.getFollowFeed(followerId, 1, 10, "alice");
        assertEquals(2, feed.getTotal()); // bob 帖 + carol 旧帖
    }

    @Test
    void getFollowFeed_shouldHideUnfollowedAuthorPosts() {
        Long followerId = 1L;
        Long bobId = 2L;
        createUser("alice", followerId);
        createUser("bob", bobId);
        followService.follow(followerId, bobId, "alice");
        postService.createPost(dto("bob 的帖子", "内容"), "bob", bobId);
        followService.getFollowFeed(followerId, 1, 10, "alice"); // 建流

        followService.unfollow(followerId, bobId);
        PageResult<PostVO> feed = followService.getFollowFeed(followerId, 1, 10, "alice");
        assertEquals(0, feed.getTotal()); // 读取兜底过滤掉取关作者
    }

    @Test
    void getFollowFeed_shouldReturnEmptyWhenNotFollowingAnyone() {
        Long followerId = 1L;
        createUser("alice", followerId);
        PageResult<PostVO> feed = followService.getFollowFeed(followerId, 1, 10, "alice");
        assertEquals(0, feed.getTotal());
        assertTrue(feed.getRecords().isEmpty());
    }
}
