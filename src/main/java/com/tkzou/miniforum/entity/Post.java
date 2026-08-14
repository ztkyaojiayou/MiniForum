package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 帖子实体
 * <p>
 * 仅承载数据，纯内存存储，不依赖任何第三方中间件。
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

    /** 作者用户 ID（用于个人主页跳转） */
    private Long authorId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 标签列表（可为空） */
    private List<String> tags = new ArrayList<>();

    /** 状态：DRAFT=草稿 / PUBLISHED=已发布（默认） */
    private String status = STATUS_PUBLISHED;

    /** 点赞总数 */
    private long likeCount;

    /** 阅读量（进入详情页时自增） */
    private long viewCount;

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
}
