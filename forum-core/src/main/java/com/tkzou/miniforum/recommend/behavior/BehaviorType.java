package com.tkzou.miniforum.recommend.behavior;

/**
 * 行为类型
 * <p>
 * 统一行为日志的事件类型，覆盖推荐链路需要的全部信号。
 * 权重认知（微博场景，见 docs/微博推荐调研.md）：转发 &gt; 评论 &gt; 收藏 &gt; 点赞 &gt; 点击 &gt; 浏览 &gt; 曝光。
 */
public enum BehaviorType {
    /** 曝光（推荐流下发时服务端记录） */
    EXPOSE,
    /** 查看详情页（浏览） */
    VIEW,
    /** 阅读停留（详情页停留时长，前端 pagehide 上报，携带 durationSec；仿抖音"观看时长"） */
    DWELL,
    /** 点击（从推荐流/相关推荐进入详情） */
    CLICK,
    /** 点赞 */
    LIKE,
    /** 取消点赞 */
    UNLIKE,
    /** 收藏 */
    FAVORITE,
    /** 取消收藏 */
    UNFAVORITE,
    /** 评论 */
    COMMENT,
    /** 转发 */
    REPOST,
    /** 搜索 */
    SEARCH,
    /** 关注 */
    FOLLOW,
    /** 取关 */
    UNFOLLOW,
    /** 负反馈（不感兴趣） */
    DISLIKE
}
