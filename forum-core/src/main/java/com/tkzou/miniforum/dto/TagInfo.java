package com.tkzou.miniforum.dto;

/**
 * 标签统计信息（标签名 + 帖子数）
 */
public class TagInfo {

    private String name;

    private long count;

    public TagInfo() {
    }

    public TagInfo(String name, long count) {
        this.name = name;
        this.count = count;
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
}
