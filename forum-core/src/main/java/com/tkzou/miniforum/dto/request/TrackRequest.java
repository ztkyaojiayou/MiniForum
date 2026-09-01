package com.tkzou.miniforum.dto.request;
import lombok.Getter;
import lombok.Setter;

/**
 * 推荐行为上报请求（前端点击/负反馈打点）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
