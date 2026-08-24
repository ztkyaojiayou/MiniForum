package com.tkzou.miniforum.recommend.feature;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时特征（单 key 窗口聚合结果）
 * <p>
 * 由 {@code stream.RealtimeFeatureWindow} 在事件窗口内聚合生成，写入 {@link RealtimeFeatureStore}（模拟 Redis）。
 * key 形如 "user:123" / "post:456"。用户侧携带近期点击过的话题分布（兴趣的实时投影），物品侧携带近期互动/曝光（热度爆发）。
 */
public class RealtimeFeature {

    /** key："user:{userId}" / "post:{postId}" */
    private String key;

    /** 窗口内深度互动数（点击/点赞/收藏/评论/转发） */
    private int clickCount;

    /** 窗口内曝光数 */
    private int exposeCount;

    /** 用户侧：窗口内点击过的帖子话题分布 */
    private Map<String, Integer> topicClicks = new LinkedHashMap<>();

    /** 窗口结束时间 */
    private LocalDateTime windowEnd;

    public RealtimeFeature() {
    }

    public RealtimeFeature(String key, LocalDateTime windowEnd) {
        this.key = key;
        this.windowEnd = windowEnd;
    }

    /** 深度互动率（点击/曝光，曝光为 0 时按 0） */
    public double ctr() {
        return exposeCount == 0 ? 0 : (double) clickCount / exposeCount;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getClickCount() {
        return clickCount;
    }

    public void setClickCount(int clickCount) {
        this.clickCount = clickCount;
    }

    public int getExposeCount() {
        return exposeCount;
    }

    public void setExposeCount(int exposeCount) {
        this.exposeCount = exposeCount;
    }

    public Map<String, Integer> getTopicClicks() {
        return topicClicks;
    }

    public void setTopicClicks(Map<String, Integer> topicClicks) {
        this.topicClicks = topicClicks;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public void addTopicClick(String topic) {
        topicClicks.merge(topic, 1, Integer::sum);
    }
}
