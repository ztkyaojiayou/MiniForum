package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Comment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论视图对象（返回给前端）
 * <p>
 * 在 {@link Comment} 基础上追加当前用户是否为作者、点赞数、楼中楼回复列表。
 */
public class CommentVO {

    private Long id;

    private Long postId;

    private String author;

    private String content;

    private LocalDateTime createdAt;

    /** 当前登录用户是否为该评论作者（是否可删除） */
    private boolean mine;

    /** 评论点赞数 */
    private long likeCount;

    /** 回复的父评论 ID（null = 根评论） */
    private Long parentId;

    /** 楼中楼回复列表（根评论才有） */
    private List<CommentVO> replies = new ArrayList<>();

    /** 回复条数 */
    private int replyCount;

    public CommentVO() {
    }

    public CommentVO(Comment comment, boolean mine) {
        this.id = comment.getId();
        this.postId = comment.getPostId();
        this.author = comment.getAuthor();
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
        this.mine = mine;
        this.likeCount = comment.getLikeCount();
        this.parentId = comment.getParentId();
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

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public List<CommentVO> getReplies() {
        return replies;
    }

    public void setReplies(List<CommentVO> replies) {
        this.replies = replies;
    }

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }
}
