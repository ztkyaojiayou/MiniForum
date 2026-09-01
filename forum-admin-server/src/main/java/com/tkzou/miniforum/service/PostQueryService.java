package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.common.PageResult;
import com.tkzou.miniforum.dto.response.CategoryVO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.TagVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.search.SearchIndex;
import com.tkzou.miniforum.util.TtlCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 帖子读服务（P2-21 拆分：PostService 上帝类 → 读写分离）
 * <p>
 * 负责读操作：列表 / 详情（含阅读量自增）/ 热门 / 搜索 / 标签 / 话题 / 分类 / 回收站列表 / 分页。
 * 写操作见 {@link PostCommandService}；两者通过共享 {@link PostQueryCache} 保持缓存失效语义一致。
 * 对外仍由 {@link PostService} 门面统一暴露，Controller/Service 调用方零改动。
 */
public class PostQueryService {

    /** 热门帖排序 postId 缓存：单 key 存 Top-100 排序列表，TTL 内命中（高并发"能预计算的不实时算"） */
    private static final String HOT_POST_KEY = "hot-posts";
    /** 热门帖缓存 TTL 打散幅度（ms） */
    private static final long HOT_POST_JITTER_MS = 1_000;
    /** 分页每页上限（page size 安全钳制） */
    private static final int MAX_PAGE_SIZE = 100;
    /** 管理员用户名（可查看任意用户回收站） */
    private static final String ADMIN_USERNAME = "admin";
    /** 固定分类（不含"全部动态"虚拟分类，常量定义在共享域 {@link PostAssembler}） */
    private static final List<String> CATEGORIES = PostAssembler.CATEGORIES;
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
    private final UserRepository userRepository;
    private final BehaviorLogger behaviorLogger;
    private final PostAssembler postAssembler;
    /** 共享单帖缓存（读服务读取；写服务失效） */
    private final PostQueryCache postQueryCache;
    /** 帖子倒排索引（事件驱动；测试/未装配为 null → 搜索回退全表扫）。由 PostService 门面 @Autowired 注入传播 */
    private SearchIndex searchIndex;

    /** 热门帖 postId 缓存（读侧专用，TTL 自过期无需写失效） */
    private final TtlCache<String, List<Long>> hotPostIdsCache = new TtlCache<>(0, HOT_POST_JITTER_MS);

    public PostQueryService(PostRepository postRepository,
                            UserRepository userRepository,
                            BehaviorLogger behaviorLogger,
                            PostAssembler postAssembler,
                            PostQueryCache postQueryCache) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.behaviorLogger = behaviorLogger;
        this.postAssembler = postAssembler;
        this.postQueryCache = postQueryCache;
    }

    /** 热门帖 postId 缓存 TTL（ms），Spring 注入；>0 启用，≤0 禁用（每次现算） */
    public void setHotPostIdsCacheTtlMs(long ttl) {
        hotPostIdsCache.setTtlMillis(ttl);
    }

    public void setSearchIndex(SearchIndex searchIndex) {
        this.searchIndex = searchIndex;
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
     * 热点 key 优化（P3-3）：读帖子走共享 {@link PostQueryCache}（防御性拷贝，P1-15）；
     * 写路径（update/delete/restore/like/unlike）由 Command 服务主动失效 + 短 TTL 兜底。
     */
    public PostVO getById(Long id, String username) {
        Post post = postQueryCache.get(id, () -> copyPost(getPostOrThrow(id))); // 防御性拷贝：缓存与存储引用分离
        if (post.isDeleted()) {
            throw new ResourceNotFoundException("帖子不存在：id=" + id);
        }
        if (isDraft(post) && !isOwnerOrAdmin(post, username)) {
            throw new ResourceNotFoundException("帖子不存在：id=" + id);
        }
        if (isPublished(post)) {
            long newViewCount = postRepository.incrementViewCount(id, 1);
            post.setViewCount(newViewCount); // 原子自增并回写本地对象（内存共享引用幂等）
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
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
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
                .limit(MAX_PAGE_SIZE)
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    /** 分页查询已发布帖子（最新在前），支持按标签、分类筛选 */
    public PageResult<PostVO> getPosts(int page, int size, String tag, String category, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
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
    public List<CategoryVO> getAllCategories() {
        Map<String, Long> countMap = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p)) {
                continue;
            }
            String c = resolveCategory(p);
            countMap.merge(c, 1L, Long::sum);
        }
        List<CategoryVO> result = new ArrayList<>();
        result.add(new CategoryVO("全部动态", countMap.values().stream().mapToLong(Long::longValue).sum(), "🌐"));
        for (String name : CATEGORIES) {
            result.add(new CategoryVO(name, countMap.getOrDefault(name, 0L), CATEGORY_ICONS.getOrDefault(name, "✨")));
        }
        return result;
    }

    /** 个人主页：某用户的全部已发布帖子（分页，最新在前） */
    public PageResult<PostVO> getPostsByAuthor(Long authorId, int page, int size, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Post> all = postRepository.findByAuthorId(authorId).stream()
                .filter(this::isVisible)
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 我的文章：当前用户自己的文章（默认全部，可按 status=DRAFT/PUBLISHED 过滤，分页） */
    public PageResult<PostVO> getMyPosts(String username, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Post> all = postRepository.findAll().stream()
                .filter(p -> p.getAuthor().equals(username))
                .filter(p -> !p.isDeleted())
                .filter(p -> status == null || status.isBlank()
                        || (p.getStatus() != null && status.equalsIgnoreCase(p.getStatus().name())))
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 我的回收站：当前用户已删除的帖子（分页，按删除时间倒序） */
    public PageResult<PostVO> getRecycleBin(String username, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
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
    public List<TagVO> getAllTags() {
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
                .map(e -> new TagVO(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /** 统计所有话题及其已发布帖子数（按帖子数降序） */
    public List<TagVO> getAllTopics() {
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
                .map(e -> new TagVO(e.getKey(), e.getValue()))
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

    /** 转换为视图对象（委托共享域 {@link PostAssembler}，与推荐侧共用同一装配逻辑） */
    public PostVO toVO(Post post, String username) {
        return postAssembler.toVO(post, username);
    }

    /** 按 id 获取帖子视图（不增加阅读量、不记浏览行为——供幂等返回等只读场景） */
    public PostVO getPostVOQuietly(Long id, String username) {
        return toVO(getPostOrThrow(id), username);
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

    /**
     * 深拷贝帖子（单帖缓存用）：缓存与存储引用分离。
     * 避免读缓存线程看到写路径（update/delete 等原地改字段）的半更新实体；tags/topics 列表也拷贝，杜绝共享可变引用。
     */
    private Post copyPost(Post src) {
        Post copy = new Post();
        copy.setId(src.getId());
        copy.setTitle(src.getTitle());
        copy.setContent(src.getContent());
        copy.setAuthor(src.getAuthor());
        copy.setAuthorId(src.getAuthorId());
        copy.setCreatedAt(src.getCreatedAt());
        copy.setTags(src.getTags() == null ? null : new ArrayList<>(src.getTags()));
        copy.setTopics(src.getTopics() == null ? null : new ArrayList<>(src.getTopics()));
        copy.setCategory(src.getCategory());
        copy.setStatus(src.getStatus());
        copy.setLikeCount(src.getLikeCount());
        copy.setViewCount(src.getViewCount());
        copy.setDeleted(src.isDeleted());
        copy.setDeletedAt(src.getDeletedAt());
        copy.setOriginalPostId(src.getOriginalPostId());
        copy.setOriginalAuthorId(src.getOriginalAuthorId());
        copy.setOriginalAuthor(src.getOriginalAuthor());
        return copy;
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
}
