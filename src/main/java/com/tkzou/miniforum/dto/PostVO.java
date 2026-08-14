package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 帖子视图对象（返回给前端）
 * <p>
 * 在 {@link Post} 基础上追加点赞数、当前用户是否已点赞、评论数等展示字段，
 * 避免将请求相关的状态写入实体。
 */
public class PostVO {

    private Long id;

    private String title;

    private String content;

    private String author;

    /** 作者用户 ID（用于个人主页跳转） */
    private Long authorId;

    private LocalDateTime createdAt;

    private List<String> tags = new ArrayList<>();

    /** 话题列表（内容中自动提取的 #话题#） */
    private List<String> topics = new ArrayList<>();

    /** 分类（固定分类之一，空值兜底为"其他"） */
    private String category;

    /** 状态：DRAFT / PUBLISHED */
    private String status;

    /** 点赞总数 */
    private long likeCount;

    /** 当前登录用户是否已点赞 */
    private boolean likedByMe;

    /** 当前登录用户是否已收藏 */
    private boolean favoritedByMe;

    /** 评论总数 */
    private long commentCount;

    /** 阅读量 */
    private long viewCount;

    /** 转发原帖 ID（null = 原创帖） */
    private Long originalPostId;

    /** 转发原帖作者用户名 */
    private String originalAuthor;

    /** 转发数（统计转发该帖的帖子数） */
    private long repostCount;

    public PostVO() {
    }

    public PostVO(Post post) {
        this.id = post.getId();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.author = post.getAuthor();
        this.authorId = post.getAuthorId();
        this.createdAt = post.getCreatedAt();
        this.tags = post.getTags() == null ? new ArrayList<>() : new ArrayList<>(post.getTags());
        this.topics = post.getTopics() == null ? new ArrayList<>() : new ArrayList<>(post.getTopics());
        this.status = post.getStatus();
        this.originalPostId = post.getOriginalPostId();
        this.originalAuthor = post.getOriginalAuthor();
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    public boolean isFavoritedByMe() {
        return favoritedByMe;
    }

    public void setFavoritedByMe(boolean favoritedByMe) {
        this.favoritedByMe = favoritedByMe;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public Long getOriginalPostId() {
        return originalPostId;
    }

    public void setOriginalPostId(Long originalPostId) {
        this.originalPostId = originalPostId;
    }

    public String getOriginalAuthor() {
        return originalAuthor;
    }

    public void setOriginalAuthor(String originalAuthor) {
        this.originalAuthor = originalAuthor;
    }

    public long getRepostCount() {
        return repostCount;
    }

    public void setRepostCount(long repostCount) {
        this.repostCount = repostCount;
    }
}
