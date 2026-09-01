package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.common.PageResult;
import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.CategoryVO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.TagVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.idempotency.IdempotencyStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.mq.OutboxStore;
import com.tkzou.miniforum.search.SearchIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子服务门面（P2-21 拆分：PostService 上帝类 → 读写分离）
 * <p>
 * 逻辑已移入 {@link PostCommandService}（写：发帖/编辑/删/恢复/点赞/转发/回收站清理）与
 * {@link PostQueryService}（读：列表/详情/搜索/标签/话题/分类/热榜/回收站）。本类仅做路由，
 * 对外 API 保持不变，Controller / Service / 测试调用方零改动。
 * 共享 {@link PostQueryCache} 保证写失效传播到读缓存。
 */
@Service
public class PostService {

    /** 固定分类（不含"全部动态"虚拟分类，按左栏展示顺序），常量定义在共享域 {@link PostAssembler} */
    public static final List<String> CATEGORIES = PostAssembler.CATEGORIES;
    /** 默认分类（分类为空或旧数据时的兜底值） */
    public static final String CATEGORY_DEFAULT = PostAssembler.CATEGORY_DEFAULT;

    /** 共享单帖缓存（读服务读取，写服务失效） */
    private final PostQueryCache postQueryCache;
    /** 写服务（发帖/编辑/点赞/转发/回收站清理） */
    private final PostCommandService command;
    /** 读服务（列表/详情/搜索/标签/热榜） */
    private final PostQueryService query;

    public PostService(PostRepository postRepository,
                       LikeRepository likeRepository,
                       CommentRepository commentRepository,
                       FavoriteRepository favoriteRepository,
                       NotificationService notificationService,
                       UserRepository userRepository,
                       BehaviorLogger behaviorLogger,
                       OutboxStore outboxStore,
                       PostAssembler postAssembler,
                       IdempotencyStore idempotencyStore) {
        this.postQueryCache = new PostQueryCache();
        this.command = new PostCommandService(postRepository, likeRepository, commentRepository,
                favoriteRepository, notificationService, userRepository, behaviorLogger,
                outboxStore, idempotencyStore, postAssembler, postQueryCache);
        this.query = new PostQueryService(postRepository, userRepository, behaviorLogger,
                postAssembler, postQueryCache);
    }

    /** 单帖实体缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次回源） */
    @Value("${app.rec.post-cache-ttl-ms:5000}")
    public void setPostCacheTtlMs(long ttl) {
        postQueryCache.setPostCacheTtlMs(ttl);
    }

    /** 热门帖 postId 缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次现算） */
    @Value("${app.rec.hot-post-ids-ttl-ms:10000}")
    public void setHotPostIdsCacheTtlMs(long ttl) {
        query.setHotPostIdsCacheTtlMs(ttl);
    }

    /** 调度模式：local=@Scheduled 自调度（演示默认）/ xxl=由 XXL-Job 派发（生产，@Scheduled 空转防双跑） */
    @Value("${app.scheduling.mode:local}")
    public void setSchedulingMode(String mode) {
        command.setSchedulingMode(mode);
    }

    /** 帖子倒排索引（事件驱动；测试/未装配为 null → 搜索回退全表扫） */
    @Autowired(required = false)
    public void setSearchIndex(SearchIndex searchIndex) {
        query.setSearchIndex(searchIndex);
    }

    // ---------- 写路径（委托 PostCommandService） ----------

    public PostVO createPost(PostCreateDTO dto, String author, Long authorId) {
        return command.createPost(dto, author, authorId);
    }

    public PostCommandService.CreateResult createPost(PostCreateDTO dto, String author, Long authorId, String idempotencyKey) {
        return command.createPost(dto, author, authorId, idempotencyKey);
    }

    public PostVO updatePost(Long id, PostCreateDTO dto, String username, boolean publish) {
        return command.updatePost(id, dto, username, publish);
    }

    public void deletePost(Long id, String username) {
        command.deletePost(id, username);
    }

    public PostVO restorePost(Long id, String username) {
        return command.restorePost(id, username);
    }

    /** 定时清理回收站（Spring 管理本门面 → @Scheduled 在此生效；逻辑在 Command 服务） */
    @Scheduled(cron = "${app.recycle.clean-cron:0 0 3 * * ?}")
    public void purgeExpiredPosts() {
        command.purgeExpiredPosts();
    }

    public void doPurgeExpiredPosts() {
        command.doPurgeExpiredPosts();
    }

    public PostVO like(Long postId, String username, Long actorId) {
        return command.like(postId, username, actorId);
    }

    public PostVO unlike(Long postId, String username, Long actorId) {
        return command.unlike(postId, username, actorId);
    }

    public PostVO repost(Long postId, String comment, String username, Long actorId) {
        return command.repost(postId, comment, username, actorId);
    }

    // ---------- 读路径（委托 PostQueryService） ----------

    public List<PostVO> getAllPosts(String username) {
        return query.getAllPosts(username);
    }

    public PostVO getById(Long id, String username) {
        return query.getById(id, username);
    }

    public List<PostVO> getHotPosts(int limit, String username) {
        return query.getHotPosts(limit, username);
    }

    public PageResult<PostVO> getPosts(int page, int size, String tag, String category, String username) {
        return query.getPosts(page, size, tag, category, username);
    }

    public List<CategoryVO> getAllCategories() {
        return query.getAllCategories();
    }

    public PageResult<PostVO> getPostsByAuthor(Long authorId, int page, int size, String username) {
        return query.getPostsByAuthor(authorId, page, size, username);
    }

    public PageResult<PostVO> getMyPosts(String username, String status, int page, int size) {
        return query.getMyPosts(username, status, page, size);
    }

    public PageResult<PostVO> getRecycleBin(String username, int page, int size) {
        return query.getRecycleBin(username, page, size);
    }

    public List<PostVO> search(String keyword, String username) {
        return query.search(keyword, username);
    }

    public List<TagVO> getAllTags() {
        return query.getAllTags();
    }

    public List<TagVO> getAllTopics() {
        return query.getAllTopics();
    }

    public List<PostVO> getPostsByTopic(String topic, String username) {
        return query.getPostsByTopic(topic, username);
    }

    public PostVO toVO(Post post, String username) {
        return query.toVO(post, username);
    }

    public PostVO getPostVOQuietly(Long id, String username) {
        return query.getPostVOQuietly(id, username);
    }
}
