package com.tkzou.miniforum.entity;

/**
 * 帖子状态枚举（P1-20 状态 String 裸值 → 枚举化）
 * <p>
 * 落库/落盘仍存 String（{@link #name()}），枚举只在 Java 侧提供类型安全。
 * {@link #from} 兼容旧数据（null/未知 → PUBLISHED 兜底）。
 */
public enum PostStatus {

    DRAFT,
    PUBLISHED;

    /** 从数据库/JSON 字符串解析（null/未知 → PUBLISHED 兜底，兼容旧数据） */
    public static PostStatus from(String s) {
        if (s == null || s.isBlank()) {
            return PUBLISHED;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PUBLISHED;
        }
    }
}
