package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

/**
 * 帖子分类信息（分类名 + 已发布帖子数 + 图标）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class CategoryVO {

    /** 分类名（"全部动态"为虚拟分类，表示不过滤） */
    private String name;

    /** 该分类下已发布帖子数 */
    private long count;

    /** 分类图标（emoji） */
    private String icon;

    public CategoryVO() {
    }

    public CategoryVO(String name, long count, String icon) {
        this.name = name;
        this.count = count;
        this.icon = icon;
    }

}
