package com.tkzou.miniforum.entity;

/**
 * 通知类型枚举（P1-20 类型 String 裸值 → 枚举化）
 * <p>
 * 落库仍存 String（{@link #name()}）；{@link #from} 兼容旧数据（null/未知 → null）。
 */
public enum NotificationType {

    LIKE,
    COMMENT,
    FOLLOW,
    REPOST,
    MENTION;

    /** 从数据库/JSON 字符串解析（null/未知 → null） */
    public static NotificationType from(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
