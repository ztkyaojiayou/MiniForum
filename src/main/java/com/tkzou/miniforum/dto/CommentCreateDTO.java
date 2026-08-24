package com.tkzou.miniforum.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 发表评论请求 DTO
 */
public class CommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论不能超过 500 个字符")
    private String content;

    /** 回复的父评论 ID（null = 发表根评论，非 null = 楼中楼回复） */
    private Long parentId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
