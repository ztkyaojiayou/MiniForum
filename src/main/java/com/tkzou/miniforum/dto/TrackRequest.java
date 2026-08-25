package com.tkzou.miniforum.dto;

/**
 * 推荐行为上报请求（前端点击/负反馈打点）
 */
public class TrackRequest {

    /** 帖子 ID */
    private Long postId;

    /** 行为类型，取 BehaviorType 枚举名：CLICK / DISLIKE / DWELL 等 */
    private String action;

    /** 阅读停留时长（秒，仅 DWELL 行为使用） */
    private Double durationSec;

    public TrackRequest() {
    }

    public TrackRequest(Long postId, String action) {
        this.postId = postId;
        this.action = action;
    }

    public Double getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Double durationSec) {
        this.durationSec = durationSec;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
