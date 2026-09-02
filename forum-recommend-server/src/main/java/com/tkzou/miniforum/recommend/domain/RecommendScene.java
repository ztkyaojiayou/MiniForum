package com.tkzou.miniforum.recommend.domain;

/**
 * 推荐场景（请求来自哪个页面/形态）
 * <p>
 * 从 {@code RecommendContext.scene} 的 String 裸值枚举化（魔法值治理）：编译期校验拼写、
 * 无"非法值兜底分支"、IDE 自动补全。当前只有 {@link #HOME} 被真正触发（首页推荐流），
 * {@link #DETAIL} / {@link #NEW_USER} 为已规划场景（详情相关推荐 / 新用户冷启动推荐）。
 */
public enum RecommendScene {

    /** 首页推荐流 */
    HOME,

    /** 详情页相关推荐 */
    DETAIL,

    /** 新用户推荐（无画像时的兜底形态） */
    NEW_USER
}
