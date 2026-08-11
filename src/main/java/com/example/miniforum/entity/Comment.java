package com.example.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 评论实体
 * <p>
 * 评论挂在某篇帖子下，记录作者、内容与时间。
 */
public class Comment {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 所属帖子 ID */
    private Long postId;

    /** 评论作者用户名 */
    private String author;

    /** 评论内容 */
    private String content;

    /** 评论时间 */
    private LocalDateTime createdAt;

    public Comment() {
    }

    public Comment(Long id, Long postId, String author, String content, LocalDateTime createdAt) {
        this.id = id;
        this.postId = postId;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
    }

    /** 生成下一个自增 ID */
    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
