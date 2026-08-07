package com.example.usermanagement.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 帖子实体
 * <p>
 * 仅承载数据，纯内存存储，不依赖任何第三方中间件。
 */
public class Post {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 作者用户名 */
    private String author;

    /** 创建时间 */
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
