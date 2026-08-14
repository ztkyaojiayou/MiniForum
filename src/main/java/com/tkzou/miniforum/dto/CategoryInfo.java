package com.tkzou.miniforum.dto;

/**
 * 帖子分类信息（分类名 + 已发布帖子数 + 图标）
 */
public class CategoryInfo {

    /** 分类名（"全部动态"为虚拟分类，表示不过滤） */
    private String name;

    /** 该分类下已发布帖子数 */
    private long count;

    /** 分类图标（emoji） */
    private String icon;

    public CategoryInfo() {
    }

    public CategoryInfo(String name, long count, String icon) {
        this.name = name;
        this.count = count;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
