package com.tkzou.miniforum.recommend.feature;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品（帖子）特征
 * <p>
 * 排序层输入的内容侧特征：互动热度（微博式权重）、时效新鲜度、作者权重、是否冷启内容。
 */
public class ItemFeature {

    private Long postId;

    /** 话题列表（微博兴趣载体） */
    private List<String> topics = new ArrayList<>();

    /** 类目 */
    private String category;

    private int repostCount;
    private int commentCount;
    private int likeCount;
    private int favoriteCount;
    private int viewCount;

    /** 阅读停留总时长（秒，DWELL 求和；仿抖音"观看时长"） */
    private double readTimeSec;

    /** 互动热度分 = 3·转发 + 2·评论 + 1·点赞 + 1.5·收藏 + 0.02·浏览 + 0.05·阅读时长（微博信号权重） */
    private double hotScore;

    /** 发布时间距今小时数 */
    private double ageHours;

    /** 时效新鲜度 = exp(-ln2·ageHours/halfLife) */
    private double freshness;

    /** 是否冷启内容（新发布或互动过少） */
    private boolean inNewPool;

    public ItemFeature() {
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getRepostCount() {
        return repostCount;
    }

    public void setRepostCount(int repostCount) {
        this.repostCount = repostCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(int favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public double getHotScore() {
        return hotScore;
    }

    public void setHotScore(double hotScore) {
        this.hotScore = hotScore;
    }

    public double getReadTimeSec() {
        return readTimeSec;
    }

    public void setReadTimeSec(double readTimeSec) {
        this.readTimeSec = readTimeSec;
    }

    public double getAgeHours() {
        return ageHours;
    }

    public void setAgeHours(double ageHours) {
        this.ageHours = ageHours;
    }

    public double getFreshness() {
        return freshness;
    }

    public void setFreshness(double freshness) {
        this.freshness = freshness;
    }

    public boolean isInNewPool() {
        return inNewPool;
    }

    public void setInNewPool(boolean inNewPool) {
        this.inNewPool = inNewPool;
    }
}
