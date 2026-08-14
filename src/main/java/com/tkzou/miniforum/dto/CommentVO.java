package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Comment;

import java.time.LocalDateTime;

/**
 * 评论视图对象（返回给前端）
 * <p>
 * 在 {@link Comment} 基础上追加当前用户是否为作者（用于前端展示删除按钮）。
 */
public class CommentVO {

    private Long id;

    private Long postId;

    private String author;

    private String content;

    private LocalDateTime createdAt;

    /** 当前登录用户是否为该评论作者（是否可删除） */
    private boolean mine;

    public CommentVO() {
    }

    public CommentVO(Comment comment, boolean mine) {
        this.id = comment.getId();
        this.postId = comment.getPostId();
        this.author = comment.getAuthor();
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
        this.mine = mine;
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

    public boolean isMine() {
        return mine;
    }

    public void setMine(boolean mine) {
        this.mine = mine;
    }
}
