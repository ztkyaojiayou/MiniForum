package com.tkzou.miniforum.recommend.domain;

import java.time.LocalDateTime;

/**
 * 推荐请求上下文
 * <p>
 * 贯穿召回→排序→重排→下发的不可变上下文对象，承载用户、场景、请求时间与目标条数。
 */
public class RecommendContext {

    /** 用户 ID（个性化维度） */
    private final Long userId;

    /** 场景（HOME=首页推荐流 / DETAIL=详情相关推荐 / NEW_USER=新用户） */
    private final String scene;

    /** 请求时间（用于时效计算，可注入假时钟便于测试） */
    private final LocalDateTime requestTime;

    /** 期望返回条数 */
    private final int size;

    public RecommendContext(Long userId, String scene, LocalDateTime requestTime, int size) {
        this.userId = userId;
        this.scene = scene;
        this.requestTime = requestTime;
        this.size = size;
    }

    public Long getUserId() {
        return userId;
    }

    public String getScene() {
        return scene;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "RecommendContext{userId=" + userId + ", scene='" + scene + "', requestTime=" + requestTime
                + ", size=" + size + '}';
    }
}
