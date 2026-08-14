package com.tkzou.miniforum.dto;

/**
 * 热搜词视图对象
 */
public class HotSearchVO {

    /** 热搜关键词（帖子标签） */
    private String keyword;

    /** 热度值（阅读量×1 + 点赞×2 + 评论×3，近 30 天时间衰减加权） */
    private long heat;

    /** 关联帖子数 */
    private long postCount;

    /** 排名（从 1 开始） */
    private int rank;

    public HotSearchVO() {
    }

    public HotSearchVO(String keyword, long heat, long postCount, int rank) {
        this.keyword = keyword;
        this.heat = heat;
        this.postCount = postCount;
        this.rank = rank;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getHeat() {
        return heat;
    }

    public void setHeat(long heat) {
        this.heat = heat;
    }

    public long getPostCount() {
        return postCount;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
