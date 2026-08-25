package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.feed.InMemoryFollowFeedStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帖子服务单元测试：覆盖话题提取 / @提及通知 / 转发 / 分类筛选 / 点赞通知
 */
class PostServiceTest {

    private PostRepository postRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private PostService postService;

    @BeforeEach
    void setUp() {
        postRepository = new PostRepository();
        LikeRepository likeRepository = new LikeRepository();
        CommentRepository commentRepository = new CommentRepository();
        FavoriteRepository favoriteRepository = new FavoriteRepository();
        notificationRepository = new NotificationRepository();
        userRepository = new UserRepository();
        NotificationService notificationService = new NotificationService(notificationRepository);
        BehaviorLogger behaviorLogger = new InMemoryBehaviorLogger(new BehaviorLogRepository(), new BehaviorEventQueue());
        PostAssembler postAssembler = new PostAssembler(postRepository, likeRepository, commentRepository, favoriteRepository);
        postService = new PostService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository, behaviorLogger,
                new InMemoryPostCreatedNotifier(new InMemoryFollowFeedStore(new InMemoryFollowRepository(), 500)),
                postAssembler);
    }

    private User createUser(String username, Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return userRepository.save(user);
    }

    private PostCreateDTO dto(String title, String content, String category) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setCategory(category);
        dto.setPublish(true);
        return dto;
    }

    @Test
    void createPost_shouldExtractTopicsFromContent() {
        PostVO vo = postService.createPost(dto("标题", "今天聊聊 #AI编程# 和 #Java#", "科技"), "alice", 1L);
        assertNotNull(vo);
        // 通过仓储断言话题已提取
        Post saved = postRepository.findById(vo.getId()).orElseThrow();
        assertTrue(saved.getTopics().contains("AI编程"));
        assertTrue(saved.getTopics().contains("Java"));
    }

    @Test
    void createPost_shouldNotifyMentionedUser() {
        createUser("bob", 2L);
        PostVO vo = postService.createPost(dto("标题", "你好 @bob 这是测试", "其他"), "alice", 1L);
        List<Notification> notifications = notificationRepository.findByRecipientId(2L);
        assertFalse(notifications.isEmpty());
        Notification n = notifications.get(0);
        assertEquals(Notification.TYPE_MENTION, n.getType());
        assertEquals("alice", n.getActorUsername());
    }

    @Test
    void createPost_shouldIgnoreMentionOfUnknownUser() {
        PostVO vo = postService.createPost(dto("标题", "你好 @nobody 测试", "其他"), "alice", 1L);
        List<Notification> notifications = notificationRepository.findByRecipientId(2L);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void createPost_shouldNotNotifySelfMention() {
        createUser("alice", 1L);
        postService.createPost(dto("标题", "自己 @alice 自己", "其他"), "alice", 1L);
        // 给自己发通知会被 NotificationService 过滤
        List<Notification> notifications = notificationRepository.findByRecipientId(1L);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void repost_shouldCreateRepostAndNotifyOriginalAuthor() {
        PostVO original = postService.createPost(dto("原帖", "原始内容", "科技"), "bob", 2L);
        PostVO repost = postService.repost(original.getId(), "说得好", "alice", 1L);
        assertNotNull(repost);
        Post saved = postRepository.findById(repost.getId()).orElseThrow();
        assertEquals(original.getId(), saved.getOriginalPostId());
        assertEquals("bob", saved.getOriginalAuthor());
        // 原帖作者收到转发通知
        List<Notification> notifications = notificationRepository.findByRecipientId(2L);
        assertFalse(notifications.isEmpty());
        assertEquals(Notification.TYPE_REPOST, notifications.get(0).getType());
    }

    @Test
    void getPosts_shouldFilterByCategory() {
        postService.createPost(dto("科技帖", "内容A", "科技"), "alice", 1L);
        postService.createPost(dto("汽车帖", "内容B", "汽车"), "alice", 1L);
        PageResult<PostVO> tech = postService.getPosts(1, 10, null, "科技", "alice");
        assertEquals(1, tech.getTotal());
        assertEquals("科技帖", tech.getRecords().get(0).getTitle());
    }

    @Test
    void like_shouldNotifyPostAuthor() {
        PostVO post = postService.createPost(dto("帖子", "内容", "科技"), "bob", 2L);
        postService.like(post.getId(), "alice", 1L);
        List<Notification> notifications = notificationRepository.findByRecipientId(2L);
        assertFalse(notifications.isEmpty());
        assertEquals(Notification.TYPE_LIKE, notifications.get(0).getType());
    }
}
