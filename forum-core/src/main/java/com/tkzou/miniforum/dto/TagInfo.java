package com.tkzou.miniforum.dto;
import lombok.Getter;
import lombok.Setter;

/**
 * 标签统计信息（标签名 + 帖子数）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class TagInfo {

    private String name;

    private long count;

    public TagInfo() {
    }

    public TagInfo(String name, long count) {
        this.name = name;
        this.count = count;
    }

}
