package com.example.miniforum.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 发帖请求 DTO
 */
public class PostCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容不能超过 5000 个字符")
    private String content;

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
}
