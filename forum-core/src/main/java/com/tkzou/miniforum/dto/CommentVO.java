package com.tkzou.miniforum.dto;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.Comment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论视图对象（返回给前端）
 * <p>
 * 在 {@link Comment} 基础上追加当前用户是否为作者、点赞数、楼中楼回复列表。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
