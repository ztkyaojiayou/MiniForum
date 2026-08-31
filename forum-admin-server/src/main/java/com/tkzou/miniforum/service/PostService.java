package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.CategoryInfo;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.TagInfo;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.stream.OutboxStore;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tkzou.miniforum.search.SearchIndex;
import com.tkzou.miniforum.util.TtlCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 帖子服务
 * <p>
 * 负责发帖、查询、搜索、标签统计、点赞、个人主页、草稿管理等核心业务。
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 20;
    /** 回收站保留天数：删除超过该天数的帖子将被定时任务彻底清理 */
    private static final long RECYCLE_RETENTION_DAYS = 30;
    /** 管理员用户名（可编辑/删除任意帖子） */
    private static final String ADMIN_USERNAME = "admin";

    /** 固定分类（不含"全部动态"虚拟分类，按左栏展示顺序），常量定义在共享域 {@link PostAssembler} */
    public static final List<String> CATEGORIES = PostAssembler.CATEGORIES;
    /** 默认分类（分类为空或旧数据时的兜底值） */
    public static final String CATEGORY_DEFAULT = PostAssembler.CATEGORY_DEFAULT;
    /** 分类图标映射（展示用，业务侧维护） */
    private static final Map<String, String> CATEGORY_ICONS = createCategoryIcons();

    private static Map<String, String> createCategoryIcons() {
        Map<String, String> icons = new HashMap<>();
        icons.put("科技", "💻");
        icons.put("数码", "📱");
        icons.put("游戏", "🎮");
        icons.put("娱乐", "🎬");
        icons.put("体育", "⚽");
        icons.put("财经", "💰");
        icons.put("汽车", "🚗");
        icons.put("时事", "📰");
        icons.put("教育", "📚");
        icons.put("生活", "🏠");
        icons.put("其他", "✨");
        return icons;
    }

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BehaviorLogger behaviorLogger;
    /** 发帖事件 Outbox（演示同步发布 / 生产落表+Relayer 必达） */
    private final OutboxStore outboxStore;
    /** 帖子视图装配（共享域，admin 与 recommend 共用） */
    private final PostAssembler postAssembler;
    /** 帖子倒排索引（事件驱动；测试/未装配为 null → 搜索回退全表扫） */
    @Autowired(required = false)
    private SearchIndex searchIndex;

    /** 热门帖排序 postId 缓存：单 key 存 Top-100 排序列表，TTL 内命中（高并发"能预计算的不实时算"） */
    private static final String HOT_POST_KEY = "hot-posts";
    /** 热门帖缓存 TTL 打散幅度（ms） */
    private static final long HOT_POST_JITTER_MS = 1_000;
    private final TtlCache<String, List<Long>> hotPostIdsCache = new TtlCache<>(0, HOT_POST_JITTER_MS);

    /** 热门帖 postId 缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次现算） */
    @Value("${app.rec.hot-post-ids-ttl-ms:10000}")
    public void setHotPostIdsCacheTtlMs(long ttl) {
        hotPostIdsCache.setTtlMillis(ttl);
    }

    /** 单帖实体缓存：热点帖详情回源压力下降（P3-3 热点 key）；缓存实体引用，TTL 内接受轻微过期 */
    private static final long POST_CACHE_JITTER_MS = 1_000;
    private final TtlCache<Long, Post> postCache = new TtlCache<>(0, POST_CACHE_JITTER_MS);

    /** 单帖实体缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次回源） */
    @Value("${app.rec.post-cache-ttl-ms:5000}")
    public void setPostCacheTtlMs(long ttl) {
        postCache.setTtlMillis(ttl);
    }

    /** 调度模式：local=@Scheduled 自调度（演示默认）/ xxl=由 XXL-Job 派发（生产，@Scheduled 空转防双跑） */
    @Value("${app.scheduling.mode:local}")
    private String schedulingMode;

    public PostService(PostRepository postRepository,
                       LikeRepository likeRepository,
                       CommentRepository commentRepository,
                       FavoriteRepository favoriteRepository,
                       NotificationService notificationService,
                       UserRepository userRepository,
                       BehaviorLogger behaviorLogger,
                       OutboxStore outboxStore,
                       PostAssembler postAssembler) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.behaviorLogger = behaviorLogger;
        this.outboxStore = outboxStore;
        this.postAssembler = postAssembler;
    }

    /** 发帖（publish=false 时存为草稿） */
    public PostVO createPost(PostCreateDTO dto, String author, Long authorId) {
        Post post = new Post();
        post.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setAuthor(author);
        post.setAuthorId(authorId);
        post.setCreatedAt(LocalDateTime.now());
        post.setTags(normalizeTags(dto.getTags()));
        post.setCategory(normalizeCategory(dto.getCategory()));
        post.setTopics(extractTopics(post.getContent()));
        post.setStatus(dto.getPublish() ? Post.STATUS_PUBLISHED : Post.STATUS_DRAFT);
        Post saved = postRepository.save(post);
        if (Post.STATUS_PUBLISHED.equals(saved.getStatus())) {
            notifyMentions(saved, author, authorId);
            // 发帖事件入 Outbox（演示同步发布；生产落表 + Relayer 必达 Kafka：fanout/冷启/搜索）
            outboxStore.enqueue(toPostCreatedEvent(saved));
        }
        return toVO(saved, author);
    }

    /** 帖子实体 → 创建事件 */
    private PostCreatedEvent toPostCreatedEvent(Post post) {
        return new PostCreatedEvent(post.getId(), post.getAuthorId(), post.getAuthor(),
                post.getTitle(), post.getContent(), post.getCategory(), post.getTopics());
    }

    /** 规范化并校验分类：空值兜底为"其他"，非法分类抛异常 */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_DEFAULT;
        }
        String c = category.trim();
        if (!CATEGORIES.contains(c)) {
            throw new BusinessException("无效的分类：" + c);
        }
        return c;
    }

    /**
     * 规范化并校验标签：去空白、去重、最多 5 个、每个不超过 20 字符
     */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new BusinessException("单个标签不能超过 " + MAX_TAG_LENGTH + " 个字符");
            }
            if (!result.contains(tag)) {
                result.add(tag);
            }
        }
        if (result.size() > MAX_TAGS) {
            throw new BusinessException("最多添加 " + MAX_TAGS + " 个标签");
        }
        return result;
    }

    /** 从内容中自动提取 #话题#（最多 5 个，去重，话题最长 30 字符） */
    private List<String> extractTopics(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        List<String> topics = new ArrayList<>();
        Matcher m = Pattern.compile("#([^#\\s]{1,30})#").matcher(content);
        while (m.find() && topics.size() < 5) {
            String t = m.group(1).trim();
            if (!t.isEmpty() && !topics.contains(t)) {
                topics.add(t);
            }
        }
        return topics;
    }

    /** @提及 识别正则：匹配 @用户名（中英文、数字、下划线，1~20 字符） */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5]{1,20})");

    /** 从内容中提取被 @ 的用户名（去重） */
    private java.util.Set<String> extractMentions(String content) {
        java.util.Set<String> mentions = new java.util.LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return mentions;
        }
        Matcher m = MENTION_PATTERN.matcher(content);
        while (m.find()) {
            mentions.add(m.group(1));
        }
        return mentions;
    }

    /** 为被 @ 的用户生成 MENTION 通知（用户不存在时静默忽略；@ 自己由 NotificationService 去重） */
    private void notifyMentions(Post post, String actorUsername, Long actorId) {
        String title = (post.getTitle() == null || post.getTitle().isBlank()) ? "动态" : post.getTitle();
        for (String mentionName : extractMentions(post.getContent())) {
            userRepository.findByUsername(mentionName).ifPresent(u ->
                    notificationService.notify(u.getId(), actorId, actorUsername,
                            Notification.TYPE_MENTION, post.getId(), "在帖子《" + title + "》中提到了你"));
        }
    }

    /** 查看所有已发布帖子（最新在前，草稿与回收站中的帖子不出现） */
    public List<PostVO> getAllPosts(String username) {
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 查询帖子，不存在/已删除时抛出异常；草稿仅作者本人/管理员可见；查看已发布帖子时阅读量 +1
     * <p>
     * 热点 key 优化（P3-3）：读帖子走本地 {@link TtlCache}，热门帖详情回源压力下降；
     * 写路径（update/delete/restore/like/unlike）主动失效 + 短 TTL 兜底，接受轻微过期。
     */
    public PostVO getById(Long id, String username) {
        Post post = postCache.get(id, () -> getPostOrThrow(id));
        if (post.isDeleted()) {
            throw new ResourceNotFoundException("帖子不存在：id=" + id);
        }
        if (isDraft(post) && !isOwnerOrAdmin(post, username)) {
            throw new ResourceNotFoundException("帖子不存在：id=" + id);
        }
        if (isPublished(post)) {
            post.setViewCount(post.getViewCount() + 1);
            postRepository.save(post);
            // 记录浏览行为（供画像/推荐信号，生产形态进 Kafka）
            userRepository.findByUsername(username)
                    .ifPresent(u -> behaviorLogger.log(u.getId(), id, BehaviorType.VIEW, "POST", null));
        }
        return toVO(post, username);
    }

    /**
     * 热门排行：按阅读量降序（同阅读量按最新），默认取前 10。
     * 高并发优化：缓存"排序后的 postId 列表"（TTL 内免全表扫+排序），逐条回源 + 现算 toVO——
     * 因为 PostVO 带 likedByMe/favoritedByMe（用户相关），不能整页缓存 VO，只能缓存 ID 顺序。
     */
    public List<PostVO> getHotPosts(int limit, String username) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<Long> topIds = hotPostIdsCache.get(HOT_POST_KEY, this::computeHotPostIds);
        List<Long> slice = topIds.size() > safeLimit
                ? new ArrayList<>(topIds.subList(0, safeLimit)) : new ArrayList<>(topIds);
        return slice.stream()
                .map(postRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 现算热门 postId 排序（缓存 miss 时执行）：isVisible + viewCount 降序 + 取前 100 */
    private List<Long> computeHotPostIds() {
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getViewCount(), a.getViewCount());
                    return cmp != 0 ? cmp : b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(100)
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    /** 分页查询已发布帖子（最新在前），支持按标签、分类筛选 */
    public PageResult<PostVO> getPosts(int page, int size, String tag, String category, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll().stream()
                .filter(this::isVisible)
                .collect(Collectors.toList());
        if (tag != null && !tag.isBlank()) {
            String t = tag.trim();
            all = all.stream()
                    .filter(p -> p.getTags() != null && p.getTags().contains(t))
                    .collect(Collectors.toList());
        }
        if (category != null && !category.isBlank()) {
            String c = category.trim();
            all = all.stream()
                    .filter(p -> c.equals(resolveCategory(p)))
                    .collect(Collectors.toList());
        }
        return paginate(all, safePage, safeSize, username);
    }

    /** 解析帖子分类：旧数据/空分类兜底为"其他"（委托共享域 PostAssembler） */
    private String resolveCategory(Post p) {
        return postAssembler.resolveCategory(p);
    }

    /** 获取全部固定分类及各分类已发布帖子数（含"全部动态"虚拟分类，置顶） */
    public List<CategoryInfo> getAllCategories() {
        Map<String, Long> countMap = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p)) {
                continue;
            }
            String c = resolveCategory(p);
            countMap.merge(c, 1L, Long::sum);
        }
        List<CategoryInfo> result = new ArrayList<>();
        result.add(new CategoryInfo("全部动态", countMap.values().stream().mapToLong(Long::longValue).sum(), "🌐"));
        for (String name : CATEGORIES) {
            result.add(new CategoryInfo(name, countMap.getOrDefault(name, 0L), CATEGORY_ICONS.getOrDefault(name, "✨")));
        }
        return result;
    }

    /** 个人主页：某用户的全部已发布帖子（分页，最新在前） */
    public PageResult<PostVO> getPostsByAuthor(Long authorId, int page, int size, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findByAuthorId(authorId).stream()
                .filter(this::isVisible)
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 我的文章：当前用户自己的文章（默认全部，可按 status=DRAFT/PUBLISHED 过滤，分页） */
    public PageResult<PostVO> getMyPosts(String username, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll().stream()
                .filter(p -> p.getAuthor().equals(username))
                .filter(p -> !p.isDeleted())
                .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 更新帖子（仅作者本人/管理员；publish=true 发布，false 存为草稿） */
    public PostVO updatePost(Long id, PostCreateDTO dto, String username, boolean publish) {
        Post post = getPostOrThrow(id);
        if (!isOwnerOrAdmin(post, username)) {
            throw new BusinessException("只能编辑自己发布的帖子");
        }
        post.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setTags(normalizeTags(dto.getTags()));
        post.setCategory(normalizeCategory(dto.getCategory()));
        post.setTopics(extractTopics(post.getContent()));
        post.setStatus(publish ? Post.STATUS_PUBLISHED : Post.STATUS_DRAFT);
        if (Post.STATUS_PUBLISHED.equals(post.getStatus())) {
            Long actorId = userRepository.findByUsername(username)
                    .map(User::getId)
                    .orElse(post.getAuthorId());
            notifyMentions(post, username, actorId);
        }
        Post saved = postRepository.save(post);
        postCache.invalidate(id); // 帖子内容变更 → 踢单帖缓存
        return toVO(saved, username);
    }

    /**
     * 删除帖子（软删除，移入回收站；仅作者本人/管理员可操作）
     * <p>
     * 帖子进入回收站后不再出现在任何公开列表，可在回收站中恢复，
     * 超过 {@link #RECYCLE_RETENTION_DAYS} 天由定时任务彻底清理。
     */
    public void deletePost(Long id, String username) {
        Post post = getPostOrThrow(id);
        if (!isOwnerOrAdmin(post, username)) {
            throw new BusinessException("只能删除自己发布的帖子");
        }
        if (post.isDeleted()) {
            throw new BusinessException("帖子已在回收站中");
        }
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
        postCache.invalidate(id); // 软删除 → 踢单帖缓存
    }

    /** 恢复回收站中的帖子（仅作者本人/管理员可操作） */
    public PostVO restorePost(Long id, String username) {
        Post post = getPostOrThrow(id);
        if (!isOwnerOrAdmin(post, username)) {
            throw new BusinessException("只能恢复自己发布的帖子");
        }
        if (!post.isDeleted()) {
            throw new BusinessException("帖子不在回收站中");
        }
        post.setDeleted(false);
        post.setDeletedAt(null);
        Post saved = postRepository.save(post);
        postCache.invalidate(id); // 恢复 → 踢单帖缓存
        return toVO(saved, username);
    }

    /** 我的回收站：当前用户已删除的帖子（分页，按删除时间倒序） */
    public PageResult<PostVO> getRecycleBin(String username, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll().stream()
                .filter(p -> p.getAuthor().equals(username) || ADMIN_USERNAME.equals(username))
                .filter(Post::isDeleted)
                .sorted((a, b) -> {
                    if (a.getDeletedAt() == null) return 1;
                    if (b.getDeletedAt() == null) return -1;
                    return b.getDeletedAt().compareTo(a.getDeletedAt());
                })
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /**
     * 定时清理回收站：彻底删除删除时间超过 {@link #RECYCLE_RETENTION_DAYS} 天的帖子，
     * 并级联清理其评论、点赞、通知与收藏。默认每天凌晨 3 点执行一次。
     * 生产（mode=xxl）由 XXL-Job 派发 doPurgeExpiredPosts，此处空转防双跑。
     */
    @Scheduled(cron = "${app.recycle.clean-cron:0 0 3 * * ?}")
    public void purgeExpiredPosts() {
        if ("xxl".equals(schedulingMode)) {
            return;
        }
        doPurgeExpiredPosts();
    }

    /** 清理回收站业务逻辑（演示自调度与 XXL-Job handler 共用入口） */
    public void doPurgeExpiredPosts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECYCLE_RETENTION_DAYS);
        List<Post> expired = postRepository.findAll().stream()
                .filter(Post::isDeleted)
                .filter(p -> p.getDeletedAt() != null && p.getDeletedAt().isBefore(cutoff))
                .collect(Collectors.toList());
        for (Post post : expired) {
            postRepository.deleteById(post.getId());
            commentRepository.deleteByPostId(post.getId());
            likeRepository.deleteByPostId(post.getId());
            favoriteRepository.deleteByPostId(post.getId());
            notificationService.deleteByPostId(post.getId());
            postCache.invalidate(post.getId()); // 彻底删除 → 踢单帖缓存
        }
        if (!expired.isEmpty()) {
            log.info("回收站清理完成：已彻底删除 {} 篇过期帖子", expired.size());
        }
    }

    /** 关键字搜索（忽略大小写，标题命中优先于内容/标签命中，最新在前，不含草稿与回收站） */
    public List<PostVO> search(String keyword, String username) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        String kw = keyword.trim().toLowerCase();
        // 倒排索引候选（懒建全量 + 发帖事件增量）；无索引（测试/未装配）回退全表扫
        java.util.stream.Stream<Post> posts = searchIndex != null
                ? searchIndex.search(kw).stream()
                        .map(postRepository::findById).filter(Optional::isPresent).map(Optional::get)
                : postRepository.findAll().stream();
        return posts
                .filter(this::isVisible)
                .filter(p -> matchesKeyword(p, kw))
                .sorted((a, b) -> {
                    boolean aTitle = a.getTitle() != null && a.getTitle().toLowerCase().contains(kw);
                    boolean bTitle = b.getTitle() != null && b.getTitle().toLowerCase().contains(kw);
                    if (aTitle != bTitle) {
                        return aTitle ? -1 : 1;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 帖子是否命中关键词：标题 / 内容 / 标签 / 话题 任一命中即可 */
    private boolean matchesKeyword(Post p, String kw) {
        if (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw)) {
            return true;
        }
        if (p.getContent() != null && p.getContent().toLowerCase().contains(kw)) {
            return true;
        }
        if (p.getTags() != null && p.getTags().stream()
                .anyMatch(t -> t != null && t.toLowerCase().contains(kw))) {
            return true;
        }
        if (p.getTopics() != null && p.getTopics().stream()
                .anyMatch(t -> t != null && t.toLowerCase().contains(kw))) {
            return true;
        }
        return false;
    }

    /** 统计所有标签及其已发布帖子数（按帖子数降序） */
    public List<TagInfo> getAllTags() {
        Map<String, Long> counter = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p) || p.getTags() == null) {
                continue;
            }
            for (String tag : p.getTags()) {
                counter.merge(tag, 1L, Long::sum);
            }
        }
        return counter.entrySet().stream()
                .map(e -> new TagInfo(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /** 统计所有话题及其已发布帖子数（按帖子数降序） */
    public List<TagInfo> getAllTopics() {
        Map<String, Long> counter = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p) || p.getTopics() == null) {
                continue;
            }
            for (String topic : p.getTopics()) {
                counter.merge(topic, 1L, Long::sum);
            }
        }
        return counter.entrySet().stream()
                .map(e -> new TagInfo(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /** 按话题筛选已发布帖子（最新在前，不含草稿与回收站） */
    public List<PostVO> getPostsByTopic(String topic, String username) {
        if (topic == null || topic.isBlank()) {
            return new ArrayList<>();
        }
        String t = topic.trim();
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .filter(p -> p.getTopics() != null && p.getTopics().contains(t))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 点赞（同一用户对同一帖子只能点赞一次，草稿/回收站帖子不可点赞；点赞后通知作者） */
    public PostVO like(Long postId, String username, Long actorId) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post) || post.isDeleted()) {
            throw new BusinessException("草稿不能点赞");
        }
        if (likeRepository.findByPostIdAndUsername(postId, username).isPresent()) {
            throw new BusinessException("你已经点过赞了");
        }
        Like like = new Like();
        like.setPostId(postId);
        like.setUsername(username);
        like.setCreatedAt(LocalDateTime.now());
        likeRepository.save(like);
        post.setLikeCount(post.getLikeCount() + 1);
        behaviorLogger.log(actorId, postId, BehaviorType.LIKE, "POST", null);
        // 通知帖子作者（给自己点赞不通知）
        notificationService.notify(post.getAuthorId(), actorId, username,
                Notification.TYPE_LIKE, postId, "赞了你的帖子《" + post.getTitle() + "》");
        postCache.invalidate(postId); // 点赞数变化 → 踢单帖缓存
        return toVO(post, username);
    }

    /** 取消点赞（Like 表删行 = 状态归零；同时记 UNLIKE 行为事件，与点赞成对） */
    public PostVO unlike(Long postId, String username, Long actorId) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post) || post.isDeleted()) {
            throw new BusinessException("草稿不能点赞");
        }
        Like like = likeRepository.findByPostIdAndUsername(postId, username)
                .orElseThrow(() -> new BusinessException("你还没有点过赞"));
        likeRepository.delete(like);                        // ① 状态表删行（Like=当前状态，非历史）
        post.setLikeCount(Math.max(0, post.getLikeCount() - 1)); // ② 聚合快照 -1
        behaviorLogger.log(actorId, postId, BehaviorType.UNLIKE, "POST", null); // ③ 事件流（推荐感知"取消赞"）
        postCache.invalidate(postId); // 取消点赞 → 踢单帖缓存
        return toVO(post, username);
    }

    /**
     * 转发帖子：生成一条新的已发布帖子，内容携带转发评语与原帖摘要，
     * 并通知原帖作者。
     *
     * @param postId  被转发的原帖 ID
     * @param comment 转发评语（可为空）
     */
    public PostVO repost(Long postId, String comment, String username, Long actorId) {
        Post original = getPostOrThrow(postId);
        if (!isVisible(original)) {
            throw new BusinessException("原帖不存在或不可转发");
        }
        String originalTitle = original.getTitle() == null ? "" : original.getTitle();
        Post repost = new Post();
        repost.setTitle("转发：" + originalTitle);
        StringBuilder content = new StringBuilder();
        if (comment != null && !comment.isBlank()) {
            content.append(comment.trim()).append("\n\n");
        }
        content.append("// 转发自 @").append(original.getAuthor()).append("：")
                .append(original.getContent() == null ? "" : original.getContent());
        repost.setContent(content.toString());
        repost.setAuthor(username);
        repost.setAuthorId(actorId);
        repost.setCreatedAt(LocalDateTime.now());
        repost.setTags(new ArrayList<>());
        repost.setCategory(resolveCategory(original));
        repost.setTopics(extractTopics(repost.getContent()));
        repost.setStatus(Post.STATUS_PUBLISHED);
        repost.setOriginalPostId(original.getId());
        repost.setOriginalAuthorId(original.getAuthorId());
        repost.setOriginalAuthor(original.getAuthor());
        Post saved = postRepository.save(repost);
        // 转发也是一条新发布的帖子：事件入 Outbox（与发帖一致，扇出到转发者粉丝的关注流 inbox）
        outboxStore.enqueue(toPostCreatedEvent(saved));
        behaviorLogger.log(actorId, saved.getId(), BehaviorType.REPOST, "POST", null);
        // 通知原帖作者（转发自己的帖子不通知）
        String brief = originalTitle.isBlank() && original.getContent() != null
                ? original.getContent().substring(0, Math.min(20, original.getContent().length()))
                : originalTitle;
        notificationService.notify(original.getAuthorId(), actorId, username,
                Notification.TYPE_REPOST, original.getId(), "转发了你的帖子《" + brief + "》");
        return toVO(saved, username);
    }

    /** 统一分页逻辑 */
    private PageResult<PostVO> paginate(List<Post> all, int page, int size, String username) {
        long total = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        int fromIndex = Math.min((page - 1) * size, (int) total);
        int toIndex = Math.min(fromIndex + size, (int) total);
        List<PostVO> records = all.isEmpty() ? new ArrayList<>()
                : all.subList(fromIndex, toIndex).stream()
                        .map(p -> toVO(p, username))
                        .collect(Collectors.toList());
        return new PageResult<>(records, total, page, size);
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
    }

    private boolean isPublished(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus());
    }

    /** 公开可见：已发布且未删除（不在回收站） */
    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }

    private boolean isDraft(Post p) {
        return Post.STATUS_DRAFT.equals(p.getStatus());
    }

    private boolean isOwnerOrAdmin(Post p, String username) {
        return p.getAuthor().equals(username) || ADMIN_USERNAME.equals(username);
    }

    /** 转换为视图对象（委托共享域 {@link PostAssembler}，与推荐侧共用同一装配逻辑） */
    public PostVO toVO(Post post, String username) {
        return postAssembler.toVO(post, username);
    }

    /** 按 id 获取帖子视图（不增加阅读量、不记浏览行为——供幂等返回等只读场景） */
    public PostVO getPostVOQuietly(Long id, String username) {
        return toVO(getPostOrThrow(id), username);
    }
}
