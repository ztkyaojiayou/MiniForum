package com.tkzou.miniforum.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 发帖请求 DTO
 */
public class PostCreateDTO {

    /** 标题（可选：首页"发动态"可不填，仅内容；"发文章"需填写，由前端约束） */
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容不能超过 5000 个字符")
    private String content;

    /** 可选标签，最多 5 个（每个不超过 20 字符，在 Service 层校验） */
    private List<String> tags;

    /** 分类（固定分类之一，可选，空值兜底为"其他"） */
    private String category;

    /** 是否立即发布（true=发布，false=存为草稿），默认 true */
    private Boolean publish = true;

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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getPublish() {
        return publish == null ? Boolean.TRUE : publish;
    }

    public void setPublish(Boolean publish) {
        this.publish = publish;
    }
}
