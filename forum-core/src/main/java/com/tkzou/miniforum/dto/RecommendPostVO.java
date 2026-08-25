package com.tkzou.miniforum.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐流视图对象：帖子 + 推荐理由 + 来源 + 排序分（可解释推荐）
 */
public class RecommendPostVO {

    private PostVO post;

    /** 排序分（微博式 rankScore） */
    private double score;

    /** 可读推荐理由：如 "因为你看过 #科技#" / "你关注的人发布了" / "大家都在看" */
    private String reason;

    /** 命中的召回路来源：hot/topic/itemcf/follow... */
    private List<String> sources = new ArrayList<>();

    public RecommendPostVO() {
    }

    public RecommendPostVO(PostVO post, double score, String reason, List<String> sources) {
        this.post = post;
        this.score = score;
        this.reason = reason;
        this.sources = new ArrayList<>(sources);
    }

    public PostVO getPost() {
        return post;
    }

    public void setPost(PostVO post) {
        this.post = post;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }
}
