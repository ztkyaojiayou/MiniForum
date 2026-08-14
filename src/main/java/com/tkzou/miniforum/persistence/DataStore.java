package com.tkzou.miniforum.persistence;

import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
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

/**
 * JSON 文件持久化（R9）
 * <p>
 * 将内存中的用户、帖子、评论、点赞数据定时写入 <code>data/*.json</code> 文件，
 * 应用启动完成后从文件恢复，从而解决「重启后数据丢失」的问题。
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

    /** 数据文件目录 */
    @Value("${app.data-dir:./data}")
    private String dataDir;

    /** 是否启用持久化 */
    @Value("${app.persistence.enabled:true}")
    private boolean enabled;

    public DataStore(ObjectMapper objectMapper,
                     UserRepository userRepository,
                     PostRepository postRepository,
                     CommentRepository commentRepository,
                     LikeRepository likeRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadAll();
    }

    /** 从 data/*.json 恢复数据（幂等：文件不存在则跳过） */
    public synchronized void loadAll() {
        if (!enabled) {
            return;
        }
        try {
            loadUsers();
            loadPosts();
            loadComments();
            loadLikes();
            log.info("数据持久化加载完成，目录: {}", dataDir);
        } catch (Exception e) {
            log.warn("数据持久化加载失败，将使用空数据启动: {}", e.getMessage());
        }
    }

    /** 将全部数据写入 data/*.json */
    public synchronized void saveAll() {
        if (!enabled) {
            return;
        }
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            objectMapper.writeValue(Paths.get(dataDir, "users.json").toFile(), userRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "posts.json").toFile(), postRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "comments.json").toFile(), commentRepository.exportAll());
            objectMapper.writeValue(Paths.get(dataDir, "likes.json").toFile(), likeRepository.exportAll());
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
}
