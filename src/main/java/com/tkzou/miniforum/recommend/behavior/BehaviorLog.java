package com.tkzou.miniforum.recommend.behavior;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 行为日志实体
 * <p>
 * 推荐系统的统一行为时间线：记录用户对帖子的每次曝光/浏览/点击/点赞/收藏/评论/转发/搜索/关注/负反馈，
 * 是用户画像聚合与离线评估的唯一事实源（生产形态经 Kafka 进入数仓，本项目持久化到 behavior-log.json）。
 */
public class BehaviorLog {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 帖子 ID（搜索/关注等无帖子事件为 null） */
    private Long postId;

    /** 行为类型 */
    private BehaviorType type;

    /** 行为时间 */
    private LocalDateTime timestamp;

    /** 来源场景：POST（业务动作）/ TRACK（前端上报）/ RECOMMEND_FEED（推荐流曝光）/ RECOMMEND_DETAIL */
    private String scene;

    /** AB 实验组 ID（记录本次推荐所属实验，用于离线归因） */
    private String expId;

    public BehaviorLog() {
    }

    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    public static synchronized void resetIdGenerator(long minId) {
        ID_GENERATOR.set(Math.max(ID_GENERATOR.get(), minId + 1));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public BehaviorType getType() {
        return type;
    }

    public void setType(BehaviorType type) {
        this.type = type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getExpId() {
        return expId;
    }

    public void setExpId(String expId) {
        this.expId = expId;
    }
}
