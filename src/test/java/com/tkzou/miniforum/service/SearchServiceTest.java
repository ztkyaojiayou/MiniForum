package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.SearchResultVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 综合搜索服务单元测试：帖子（标题/内容/标签/话题）+ 用户（用户名/昵称）
 */
class SearchServiceTest {

    private SearchService searchService;
    private PostService postService;
    private UserRepository userRepository;
    private SearchRecordRepository searchRecordRepository;

    @BeforeEach
    void setUp() {
        PostRepository postRepository = new PostRepository();
        LikeRepository likeRepository = new LikeRepository();
        CommentRepository commentRepository = new CommentRepository();
        FavoriteRepository favoriteRepository = new FavoriteRepository();
        NotificationRepository notificationRepository = new NotificationRepository();
        userRepository = new UserRepository();
        searchRecordRepository = new SearchRecordRepository();
        NotificationService notificationService = new NotificationService(notificationRepository);
        postService = new PostService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository);
        searchService = new SearchService(postService, userRepository, searchRecordRepository);
    }

    private User createUser(String username, String nickname, Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        return userRepository.save(user);
    }

    private PostCreateDTO dto(String title, String content) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setPublish(true);
        return dto;
    }

    @Test
    void search_shouldFindPostsByTitle() {
        postService.createPost(dto("AI 入门指南", "内容"), "alice", 1L);
        SearchResultVO result = searchService.search("AI", "alice");
        assertFalse(result.getPosts().isEmpty());
        assertEquals("AI 入门指南", result.getPosts().get(0).getTitle());
    }

    @Test
    void search_shouldFindPostsByTag() {
        PostCreateDTO dto = dto("测试帖子", "内容");
        dto.setTags(java.util.List.of("Spring", "Java"));
        postService.createPost(dto, "alice", 1L);
        SearchResultVO result = searchService.search("Spring", "alice");
        assertFalse(result.getPosts().isEmpty());
    }

    @Test
    void search_shouldFindUsersByUsername() {
        createUser("zhangsan", "张三", 2L);
        createUser("lisi", "李四", 3L);
        SearchResultVO result = searchService.search("zhang", "alice");
        assertFalse(result.getUsers().isEmpty());
        assertEquals("zhangsan", result.getUsers().get(0).getUsername());
    }

    @Test
    void search_shouldFindUsersByNickname() {
        createUser("zhangsan", "张三", 2L);
        SearchResultVO result = searchService.search("张三", "alice");
        assertFalse(result.getUsers().isEmpty());
    }

    @Test
    void search_shouldRecordKeyword() {
        searchService.search("AI", "alice");
        searchService.search("AI", "bob");
        assertTrue(searchRecordRepository.findByKeyword("AI").isPresent());
        assertEquals(2, searchRecordRepository.findByKeyword("AI").orElseThrow().getCount());
    }

    @Test
    void search_shouldReturnEmptyForBlankKeyword() {
        SearchResultVO result = searchService.search("   ", "alice");
        assertNotNull(result);
        assertTrue(result.getPosts().isEmpty());
        assertTrue(result.getUsers().isEmpty());
    }
}
