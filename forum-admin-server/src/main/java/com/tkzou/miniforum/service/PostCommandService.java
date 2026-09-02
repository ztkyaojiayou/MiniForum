package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PostAssembler;
import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.idempotency.IdempotencyStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorScene;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.mq.OutboxStore;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 帖子写服务（P2-21 拆分：PostService 上帝类 → 读写分离）
 * <p>
 * 负责写操作：发帖（含幂等）/ 编辑 / 软删 / 恢复 / 点赞 / 取消点赞 / 转发 / 回收站定时清理。
 * 读操作见 {@link PostQueryService}；两者通过共享 {@link PostQueryCache} 保持缓存失效语义一致。
 * 对外仍由 {@link PostService} 门面统一暴露，Controller/Service 调用方零改动。
 */
public class PostCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 20;
    /** 自动提取话题上限（内容中 #话题# 最多取该数） */
    private static final int MAX_TOPICS = 5;
    /** 转发摘要截断长度（转发通知/泡展示原帖摘要） */
    private static final int REPOST_BRIEF_MAX_LEN = 20;
    /** 回收站保留天数：删除超过该天数的帖子将被定时任务彻底清理 */
    private static final long RECYCLE_RETENTION_DAYS = 30;
    /** 管理员用户名（可编辑/删除任意帖子） */
    private static final String ADMIN_USERNAME = "admin";
    /** 固定分类（不含"全部动态"虚拟分类，常量定义在共享域 {@link PostAssembler}） */
    private static final List<String> CATEGORIES = PostAssembler.CATEGORIES;
    /** 默认分类（分类为空或旧数据时的兜底值） */
    private static final String CATEGORY_DEFAULT = PostAssembler.CATEGORY_DEFAULT;

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BehaviorLogger behaviorLogger;
    private final OutboxStore outboxStore;
    private final IdempotencyStore idempotencyStore;
    private final PostAssembler postAssembler;
    /** 共享单帖缓存（写失效传播给读服务） */
    private final PostQueryCache postQueryCache;

    /** 调度模式：local=@Scheduled 自调度（演示默认）/ xxl=由 XXL-Job 派发（生产，@Scheduled 空转防双跑）。由 PostService 门面 @Value 注入 */
    private String schedulingMode = "local";

    public PostCommandService(PostRepository postRepository,
                              LikeRepository likeRepository,
                              CommentRepository commentRepository,
                              FavoriteRepository favoriteRepository,
                              NotificationService notificationService,
                              UserRepository userRepository,
                              BehaviorLogger behaviorLogger,
                              OutboxStore outboxStore,
                              IdempotencyStore idempotencyStore,
                              PostAssembler postAssembler,
                              PostQueryCache postQueryCache) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.behaviorLogger = behaviorLogger;
        this.outboxStore = outboxStore;
        this.idempotencyStore = idempotencyStore;
        this.postAssembler = postAssembler;
        this.postQueryCache = postQueryCache;
    }

    public void setSchedulingMode(String schedulingMode) {
        this.schedulingMode = schedulingMode;
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
        return postAssembler.toVO(saved, author);
    }

    /**
     * 发帖（幂等版，P1-18）：带 {@code Idempotency-Key} 时编排"查完成→acquire→创建→complete→失败释放"，
     * 下沉到 Service，Controller 保持薄（不再直接操作 IdempotencyStore）。
     *
     * @return 创建结果（replayed=true 表示命中已完成 key，返回首次结果且不重复发帖）
     */
    public CreateResult createPost(PostCreateDTO dto, String author, Long authorId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new CreateResult(createPost(dto, author, authorId), false);
        }
        // 同 key 已发过 → 返回首次结果（不增加阅读量、不重复发帖）
        Optional<Long> existing = idempotencyStore.getCompleted(idempotencyKey);
        if (existing.isPresent()) {
            return new CreateResult(getPostVOQuietly(existing.get(), author), true);
        }
        // 同 key 正在处理 → 拒绝重复提交（acquire 原子：并发下只有一个成功）
        if (!idempotencyStore.acquire(idempotencyKey)) {
            throw new BusinessException("正在提交，请勿重复操作");
        }
        try {
            PostVO created = createPost(dto, author, authorId);
            idempotencyStore.complete(idempotencyKey, created.getId());
            return new CreateResult(created, false);
        } catch (Exception e) {
            idempotencyStore.release(idempotencyKey); // 创建失败释放 key，允许重试
            throw e;
        }
    }

    /** 发帖幂等结果：vo + 是否命中已完成 key（供 Controller 区分 200 重放 / 201 新建） */
    public static class CreateResult {
        private final PostVO vo;
        private final boolean replayed;

        public CreateResult(PostVO vo, boolean replayed) {
            this.vo = vo;
            this.replayed = replayed;
        }

        public PostVO getVo() {
            return vo;
        }

        public boolean isReplayed() {
            return replayed;
        }
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
        postQueryCache.invalidate(id); // 帖子内容变更 → 踢单帖缓存
        return postAssembler.toVO(saved, username);
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
        postQueryCache.invalidate(id); // 软删除 → 踢单帖缓存
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
        postQueryCache.invalidate(id); // 恢复 → 踢单帖缓存
        return postAssembler.toVO(saved, username);
    }

    /**
     * 清理回收站入口（定时由 {@link PostService} 门面的 @Scheduled 触发——本服务手动构造非 Spring bean，不自行调度；
     * 生产（mode=xxl）由 XXL-Job 派发 doPurgeExpiredPosts，此处空转防双跑）。
     */
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
                .collect(java.util.stream.Collectors.toList());
        for (Post post : expired) {
            postRepository.deleteById(post.getId());
            commentRepository.deleteByPostId(post.getId());
            likeRepository.deleteByPostId(post.getId());
            favoriteRepository.deleteByPostId(post.getId());
            notificationService.deleteByPostId(post.getId());
            postQueryCache.invalidate(post.getId()); // 彻底删除 → 踢单帖缓存
        }
        if (!expired.isEmpty()) {
            log.info("回收站清理完成：已彻底删除 {} 篇过期帖子", expired.size());
        }
    }

    /** 点赞（同一用户对同一帖子只能点赞一次，草稿/回收站帖子不可点赞；点赞后通知作者） */
    public PostVO like(Long postId, String username, Long actorId) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post) || post.isDeleted()) {
            throw new BusinessException("草稿不能点赞");
        }
        Like like = new Like();
        like.setPostId(postId);
        like.setUsername(username);
        like.setCreatedAt(LocalDateTime.now());
        // 原子"判重+插入"：InMemory putIfAbsent / MySql 唯一索引+DuplicateKeyException，杜绝并发重复点赞
        if (!likeRepository.trySaveIfAbsent(like)) {
            throw new BusinessException("你已经点过赞了");
        }
        long newLikeCount = postRepository.incrementLikeCount(postId, 1);
        post.setLikeCount(newLikeCount); // 原子自增并回写本地对象（内存共享引用幂等）
        behaviorLogger.log(actorId, postId, BehaviorType.LIKE, BehaviorScene.POST, null);
        // 通知帖子作者（给自己点赞不通知）
        notificationService.notify(post.getAuthorId(), actorId, username,
                Notification.TYPE_LIKE, postId, "赞了你的帖子《" + post.getTitle() + "》");
        postQueryCache.invalidate(postId); // 点赞数变化 → 踢单帖缓存
        return postAssembler.toVO(post, username);
    }

    /** 取消点赞（Like 表删行 = 状态归零；同时记 UNLIKE 行为事件，与点赞成对） */
    public PostVO unlike(Long postId, String username, Long actorId) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post) || post.isDeleted()) {
            throw new BusinessException("草稿不能点赞");
        }
        Like like = likeRepository.findByPostIdAndUsername(postId, username)
                .orElseThrow(() -> new BusinessException("你还没有点过赞"));
        likeRepository.delete(like);                                        // ① 状态表删行（Like=当前状态，非历史）
        long newLikeCount = postRepository.incrementLikeCount(postId, -1);   // ② 聚合快照原子 -1（不小于 0）
        post.setLikeCount(newLikeCount);
        behaviorLogger.log(actorId, postId, BehaviorType.UNLIKE, BehaviorScene.POST, null); // ③ 事件流（推荐感知"取消赞"）
        postQueryCache.invalidate(postId); // 取消点赞 → 踢单帖缓存
        return postAssembler.toVO(post, username);
    }

    /**
     * 转发帖子：生成一条新的已发布帖子，内容携带转发评语与原帖摘要，并通知原帖作者。
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
        repost.setCategory(postAssembler.resolveCategory(original));
        repost.setTopics(extractTopics(repost.getContent()));
        repost.setStatus(Post.STATUS_PUBLISHED);
        repost.setOriginalPostId(original.getId());
        repost.setOriginalAuthorId(original.getAuthorId());
        repost.setOriginalAuthor(original.getAuthor());
        Post saved = postRepository.save(repost);
        // 转发也是一条新发布的帖子：事件入 Outbox（与发帖一致，扇出到转发者粉丝的关注流 inbox）
        outboxStore.enqueue(toPostCreatedEvent(saved));
        behaviorLogger.log(actorId, saved.getId(), BehaviorType.REPOST, BehaviorScene.POST, null);
        // 通知原帖作者（转发自己的帖子不通知）
        String brief = originalTitle.isBlank() && original.getContent() != null
                ? original.getContent().substring(0, Math.min(REPOST_BRIEF_MAX_LEN, original.getContent().length()))
                : originalTitle;
        notificationService.notify(original.getAuthorId(), actorId, username,
                Notification.TYPE_REPOST, original.getId(), "转发了你的帖子《" + brief + "》");
        return postAssembler.toVO(saved, username);
    }

    /** 按 id 获取帖子视图（不增加阅读量、不记浏览行为——供幂等返回等只读场景） */
    private PostVO getPostVOQuietly(Long id, String username) {
        return postAssembler.toVO(getPostOrThrow(id), username);
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

    /** 规范化并校验标签：去空白、去重、最多 5 个、每个不超过 20 字符 */
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
        while (m.find() && topics.size() < MAX_TOPICS) {
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

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
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
