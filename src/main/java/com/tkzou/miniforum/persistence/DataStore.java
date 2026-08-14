package com.tkzou.miniforum.persistence;

import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.entity.Favorite;
import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Message;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.ConversationRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.MessageRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JSON 文件持久化
 * <p>
 * 将内存中的用户、帖子、评论、点赞、关注关系、通知、收藏、搜索词、私信会话与消息数据
 * 定时写入 <code>data/*.json</code> 文件，应用启动完成后从文件恢复，
 * 从而解决「重启后数据丢失」的问题。
 * <ul>
 *   <li>启动：{@link #loadAll()}（ApplicationRunner，在所有 Bean 初始化后执行）</li>
 *   <li>运行：{@link #saveAll()} 定时保存（默认 30 秒一次）</li>
 *   <li>关闭：{@link #saveAll()} 随 {@link PreDestroy} 触发</li>
 * </ul>
 */
@Component
public class DataStore implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataStore.class);

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final FavoriteRepository favoriteRepository;
    private final SearchRecordRepository searchRecordRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /** 数据文件目录 */
    @Value("${app.data-dir:./data}")
    private String dataDir;

    /** 是否启用持久化 */
    @Value("${app.persistence.enabled:true}")
    private boolean enabled;

    /**
     * 是否已完成启动加载。
     * <p>
     * 防止 {@link Scheduled} 定时保存早于 {@link #loadAll()} 执行：
     * 若保存先于加载运行，会用内存中的空数据覆盖磁盘上已有数据文件。
     */
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public DataStore(ObjectMapper objectMapper,
                     UserRepository userRepository,
                     PostRepository postRepository,
                     CommentRepository commentRepository,
                     LikeRepository likeRepository,
                     FollowRepository followRepository,
                     NotificationRepository notificationRepository,
                     FavoriteRepository favoriteRepository,
                     SearchRecordRepository searchRecordRepository,
                     ConversationRepository conversationRepository,
                     MessageRepository messageRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.favoriteRepository = favoriteRepository;
        this.searchRecordRepository = searchRecordRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadAll();
    }

    /** 从 data/*.json 恢复数据（幂等：文件不存在则跳过） */
    public synchronized void loadAll() {
        if (!enabled) {
            loaded.set(true);
            return;
        }
        try {
            loadUsers();
            loadPosts();
            loadComments();
            loadLikes();
            loadFollows();
            loadNotifications();
            loadFavorites();
            loadSearchRecords();
            loadConversations();
            loadMessages();
            log.info("数据持久化加载完成，目录: {}", dataDir);
        } catch (Exception e) {
            log.warn("数据持久化加载失败，将使用空数据启动: {}", e.getMessage());
        } finally {
            // 无论成败，加载阶段结束，后续定时保存才允许落盘
            loaded.set(true);
        }
    }

    /** 将全部数据写入 data/*.json */
    public synchronized void saveAll() {
        if (!enabled || !loaded.get()) {
            return;
        }
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            objectMapper.writeValue(Paths.get(dataDir, "users.json").toFile(), userRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "posts.json").toFile(), postRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "comments.json").toFile(), commentRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "likes.json").toFile(), likeRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "follows.json").toFile(), followRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "notifications.json").toFile(), notificationRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "favorites.json").toFile(), favoriteRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "search-records.json").toFile(), searchRecordRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "conversations.json").toFile(), conversationRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "messages.json").toFile(), messageRepository.exportAll());
        } catch (Exception e) {
            log.warn("数据持久化保存失败: {}", e.getMessage());
        }
    }

    /** 定时保存（默认每 30 秒，可通过 app.persistence.interval-ms 配置） */
    @Scheduled(fixedDelayString = "${app.persistence.interval-ms:30000}")
    public void scheduledSave() {
        saveAll();
    }

    /** 应用关闭时保存 */
    @PreDestroy
    public void shutdown() {
        saveAll();
        log.info("数据持久化已保存（关闭前）");
    }

    private void loadUsers() throws Exception {
        Path file = Paths.get(dataDir, "users.json");
        if (!Files.exists(file)) {
            return;
        }
        List<User> users = objectMapper.readValue(file.toFile(), new TypeReference<List<User>>() {});
        userRepository.importAll(users);
        users.stream().map(User::getId).max(Long::compareTo).ifPresent(User::resetIdGenerator);
    }

    private void loadPosts() throws Exception {
        Path file = Paths.get(dataDir, "posts.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Post> posts = objectMapper.readValue(file.toFile(), new TypeReference<List<Post>>() {});
        postRepository.importAll(posts);
        posts.stream().map(Post::getId).max(Long::compareTo).ifPresent(Post::resetIdGenerator);
    }

    private void loadComments() throws Exception {
        Path file = Paths.get(dataDir, "comments.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Comment> comments = objectMapper.readValue(file.toFile(), new TypeReference<List<Comment>>() {});
        commentRepository.importAll(comments);
        comments.stream().map(Comment::getId).max(Long::compareTo).ifPresent(Comment::resetIdGenerator);
    }

    private void loadLikes() throws Exception {
        Path file = Paths.get(dataDir, "likes.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Like> likes = objectMapper.readValue(file.toFile(), new TypeReference<List<Like>>() {});
        likeRepository.importAll(likes);
        likes.stream().map(Like::getId).max(Long::compareTo).ifPresent(Like::resetIdGenerator);
    }

    private void loadFollows() throws Exception {
        Path file = Paths.get(dataDir, "follows.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Follow> follows = objectMapper.readValue(file.toFile(), new TypeReference<List<Follow>>() {});
        followRepository.importAll(follows);
        follows.stream().map(Follow::getId).max(Long::compareTo).ifPresent(Follow::resetIdGenerator);
    }

    private void loadNotifications() throws Exception {
        Path file = Paths.get(dataDir, "notifications.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Notification> notifications = objectMapper.readValue(file.toFile(), new TypeReference<List<Notification>>() {});
        notificationRepository.importAll(notifications);
        notifications.stream().map(Notification::getId).max(Long::compareTo).ifPresent(Notification::resetIdGenerator);
    }

    private void loadFavorites() throws Exception {
        Path file = Paths.get(dataDir, "favorites.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Favorite> favorites = objectMapper.readValue(file.toFile(), new TypeReference<List<Favorite>>() {});
        favoriteRepository.importAll(favorites);
        favorites.stream().map(Favorite::getId).max(Long::compareTo).ifPresent(Favorite::resetIdGenerator);
    }

    private void loadSearchRecords() throws Exception {
        Path file = Paths.get(dataDir, "search-records.json");
        if (!Files.exists(file)) {
            return;
        }
        List<SearchRecord> records = objectMapper.readValue(file.toFile(), new TypeReference<List<SearchRecord>>() {});
        searchRecordRepository.importAll(records);
        records.stream().map(SearchRecord::getId).max(Long::compareTo).ifPresent(SearchRecord::resetIdGenerator);
    }

    private void loadConversations() throws Exception {
        Path file = Paths.get(dataDir, "conversations.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Conversation> conversations = objectMapper.readValue(file.toFile(), new TypeReference<List<Conversation>>() {});
        conversationRepository.importAll(conversations);
        conversations.stream().map(Conversation::getId).max(Long::compareTo).ifPresent(Conversation::resetIdGenerator);
    }

    private void loadMessages() throws Exception {
        Path file = Paths.get(dataDir, "messages.json");
        if (!Files.exists(file)) {
            return;
        }
        List<Message> messages = objectMapper.readValue(file.toFile(), new TypeReference<List<Message>>() {});
        messageRepository.importAll(messages);
        messages.stream().map(Message::getId).max(Long::compareTo).ifPresent(Message::resetIdGenerator);
    }
}
