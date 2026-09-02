package com.tkzou.miniforum.recommend.behavior;

/**
 * 行为来源场景（从 {@code BehaviorLog.scene} 的 String 裸值枚举化，魔法值治理）
 * <p>
 * 区分同一行为的产生入口，供画像/评估按来源分别看待：
 * {@link #POST}（业务动作）/ {@link #TRACK}（前端打点上报）/ {@link #RECOMMEND_FEED}（推荐流曝光）/
 * {@link #RECOMMEND_DETAIL}（详情相关推荐曝光）。{@link #DEFAULT} 为兜底，兼容历史数据与未知来源。
 * <p>
 * 序列化兼容：Jackson 按 {@code name()} 读写（值恰好是历史字符串），旧 behavior-log.json / Kafka / ClickHouse
 * 数据无缝读写；跨进程/外部回读用 {@link #fromString} 宽松解析。
 */
public enum BehaviorScene {

    /** 业务动作（点赞/评论/转发/搜索/关注…），用户主动触发 */
    POST,

    /** 前端打点上报（/api/recommend/track 的点击/负反馈/停留） */
    TRACK,

    /** 推荐流曝光（下发 feed 时记 EXPOSE） */
    RECOMMEND_FEED,

    /** 详情页相关推荐曝光 */
    RECOMMEND_DETAIL,

    /** 兜底（旧数据/未知来源，见历史 InMemoryBehaviorLogger 的 null 值回退） */
    DEFAULT;

    /** 宽松解析：null / 空白 / 未知字符串 → {@link #DEFAULT}（ClickHouse / 外部数据回读用） */
    public static BehaviorScene fromString(String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
