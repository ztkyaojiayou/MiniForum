package com.tkzou.miniforum.dto.request;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 发表评论请求 DTO
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class CommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论不能超过 500 个字符")
    private String content;

    /** 回复的父评论 ID（null = 发表根评论，非 null = 楼中楼回复） */
    private Long parentId;

}
