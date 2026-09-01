package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 帖子实体 —— 【主实体 + 当前状态快照】
 * <p>
 * 仅承载数据，纯内存存储，不依赖任何第三方中间件。
 *
 * <h3>与 Like/Favorite/Follow 的层次区别</h3>
 * <ul>
 *   <li><b>Post 是"事实本体"</b>：内容/作者/时间/状态是权威记录，不是由用户动作派生的；
 *       编辑=覆盖字段、删除=软删除（{@code deleted}），不保留编辑历史——所以它是"当前状态"而非"编辑日志"；</li>
 *   <li><b>Post 里的计数是"聚合快照"</b>：{@code likeCount/viewCount} 由 Like 表 / 浏览事件<b>派生</b>，
 *       是缓存性质的近似值，可能与实际事实短暂不一致（异步/缓存权衡）——真实验证"谁赞过"看 Like 表；
 *       Like/Favorite/Follow 才是纯"关系状态表"（点赞/取消 = INSERT/DELETE，历史走 BehaviorLog）。</li>
 * </ul>
 * <pre>
 *   层次：Like/Favorite/Follow（关系状态，可增删）  ↔  Post（内容本体 + 计数快照）  ↔  BehaviorLog（事件历史）
 * </pre>
 */
public class Post {

    /** 草稿状态 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 已发布状态 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 作者用户名 */
    private String author;

    /** 作者用户 ID（这条帖子的作者，恒非 null；个人主页跳转 / 作者过滤 / 社交召回"我关注的人发的" authorId∈关注集合） */
    private Long authorId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 标签列表（可为空） */
    private List<String> tags = new ArrayList<>();

    /** 话题列表（从内容中自动提取的 #话题#，可为空） */
    private List<String> topics = new ArrayList<>();

    /** 分类（固定分类之一，空值/旧数据兜底为"其他"） */
    private String category;

    /** 状态：DRAFT=草稿 / PUBLISHED=已发布（默认） */
    private String status = STATUS_PUBLISHED;

    /** 点赞总数（volatile：P0-2 由仓储原子自增，读侧（缓存/排序/装配）要求可见性，读-改-写仍在仓储串行化） */
    private volatile long likeCount;

    /** 阅读量（进入详情页时自增；同上 volatile） */
    private volatile long viewCount;

    /** 是否已删除（回收站软删除标记） */
    private boolean deleted;

    /** 删除时间（软删除时记录，用于 30 天自动清理） */
    private LocalDateTime deletedAt;

    /**
     * 转发：被转发的【直接原帖】ID（null = 原创帖）。
     * 转发 = 生成一个新 Post 指向原帖（Post 自关联，转发链 A←B←C，每条转发指向它【直接】转的那条，不折叠到根帖）。
     * 用途：① 转发计数 count(帖子 where originalPostId==本帖)（【直接转发数】，不含链式折叠） ② 转发泡点击跳转原帖详情 ③ 判别转发帖。
     */
    private Long originalPostId;

    /**
     * 转发：直接原帖的【作者】ID（冗余存储，配合 {@link #originalAuthor} 一次 IO 拿到"谁的原帖"，免查原帖表）。
     * 用途：二度转发信号——"我关注的人转发了"（originalAuthorId ∈ 我的关注集合），排序 social +0.5 / 社交召回命中；
     * 跳转原帖作者主页。
     */
    private Long originalAuthorId;

    /** 转发：原帖作者用户名（冗余字段，前端转发泡展示"🔁 @alice 的原微博"，免 join 原帖表） */
    private String originalAuthor;

    public Post() {
    }

    public Post(Long id, String title, String content, String author, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.author = author;
        this.createdAt = createdAt;
    }

    /** 生成下一个自增 ID */
    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    /**
     * 将 ID 生成器推进到指定最小值之后（用于从持久化数据恢复，避免 ID 冲突）
     */
    public static synchronized void resetIdGenerator(long minId) {
        ID_GENERATOR.set(Math.max(ID_GENERATOR.get(), minId + 1));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getOriginalPostId() {
        return originalPostId;
    }

    public void setOriginalPostId(Long originalPostId) {
        this.originalPostId = originalPostId;
    }

    public Long getOriginalAuthorId() {
        return originalAuthorId;
    }

    public void setOriginalAuthorId(Long originalAuthorId) {
        this.originalAuthorId = originalAuthorId;
    }

    public String getOriginalAuthor() {
        return originalAuthor;
    }

    public void setOriginalAuthor(String originalAuthor) {
        this.originalAuthor = originalAuthor;
    }
}
