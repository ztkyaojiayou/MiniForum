package com.tkzou.miniforum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.dto.request.UserCreateDTO;
import com.tkzou.miniforum.dto.request.UserUpdateDTO;
import com.tkzou.miniforum.dto.response.UserVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.DuplicateUsernameException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.feed.impl.InMemoryFollowFeedStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.impl.InMemoryFollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.impl.InMemoryNotificationRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.impl.InMemoryBehaviorLogger;
import com.tkzou.miniforum.recommend.mq.BehaviorEventQueue;
import com.tkzou.miniforum.util.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tkzou.miniforum.repository.impl.InMemoryCommentRepository;
import com.tkzou.miniforum.repository.impl.InMemoryFavoriteRepository;
import com.tkzou.miniforum.repository.impl.InMemoryLikeRepository;
import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;
import com.tkzou.miniforum.repository.impl.InMemoryUserRepository;

/**
 * 用户服务单元测试
 */
class UserServiceTest {

    private UserService userService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        PostRepository postRepository = new InMemoryPostRepository();
        FollowRepository followRepository = new InMemoryFollowRepository();
        LikeRepository likeRepository = new InMemoryLikeRepository();
        CommentRepository commentRepository = new InMemoryCommentRepository();
        NotificationRepository notificationRepository = new InMemoryNotificationRepository();
        FavoriteRepository favoriteRepository = new InMemoryFavoriteRepository();
        NotificationService notificationService = new NotificationService(notificationRepository);
        BehaviorLogger behaviorLogger = new InMemoryBehaviorLogger(new BehaviorLogRepository(), new BehaviorEventQueue());
        FollowService followService = new FollowService(followRepository, userRepository, postRepository,
                likeRepository, commentRepository, favoriteRepository, notificationService, behaviorLogger,
                new InMemoryFollowFeedStore(followRepository, 500), 500);
        userService = new UserService(userRepository, postRepository, followService);
    }

    private UserCreateDTO createDTO(String username, String email, String password, Integer age) {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername(username);
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setAge(age);
        return dto;
    }

    @Test
    void createUser_shouldPersistAndEncodePassword() {
        UserVO user = userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));

        assertNotNull(user.getId());
        assertEquals("zhangsan", user.getUsername());
        // 密码加密存储于存储层（服务返回的 UserVO 不含 password）
        User stored = userRepository.findByUsername("zhangsan").orElseThrow();
        assertFalse("password123".equals(stored.getPassword()));
        assertTrue(PasswordEncoder.matches("password123", stored.getPassword()));
    }

    @Test
    void userVO_shouldNotExposePassword() throws Exception {
        UserVO user = userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));

        // API 边界脱敏：序列化结果不含 password / 密码散列
        String json = new ObjectMapper().writeValueAsString(user);
        assertFalse(json.contains("password"));
        assertFalse(json.contains(PasswordEncoder.encode("password123")));
    }

    @Test
    void createUser_shouldThrowWhenUsernameExists() {
        userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));
        assertThrows(DuplicateUsernameException.class,
                () -> userService.createUser(createDTO("zhangsan", "zs2@test.com", "password456", 30)));
    }

    @Test
    void getUserById_shouldReturnUser() {
        UserVO created = userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));
        UserVO found = userService.getUserById(created.getId());
        assertEquals("zhangsan", found.getUsername());
    }

    @Test
    void getUserById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999L));
    }

    @Test
    void updateUser_shouldUpdateFields() {
        UserVO created = userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));

        UserUpdateDTO update = new UserUpdateDTO();
        update.setUsername("zhangsan");
        update.setEmail("new@test.com");
        update.setPassword("newpass123");
        update.setAge(26);

        UserVO updated = userService.updateUser(created.getId(), update);
        assertEquals("new@test.com", updated.getEmail());
        assertEquals(26, updated.getAge());
        // 新密码加密存储于存储层
        User stored = userRepository.findByUsername("zhangsan").orElseThrow();
        assertTrue(PasswordEncoder.matches("newpass123", stored.getPassword()));
    }

    @Test
    void deleteUser_shouldRemoveUser() {
        UserVO created = userService.createUser(createDTO("zhangsan", "zs@test.com", "password123", 25));
        userService.deleteUser(created.getId());
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(created.getId()));
    }
}
