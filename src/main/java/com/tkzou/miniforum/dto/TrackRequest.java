package com.tkzou.miniforum.dto;

/**
 * 推荐行为上报请求（前端点击/负反馈打点）
 */
public class TrackRequest {

    /** 帖子 ID */
    private Long postId;

    /** 行为类型，取 BehaviorType 枚举名：CLICK / DISLIKE 等 */
    private String action;

    public TrackRequest() {
    }

    public TrackRequest(Long postId, String action) {
        this.postId = postId;
        this.action = action;
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
