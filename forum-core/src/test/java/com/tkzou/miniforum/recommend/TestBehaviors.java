package com.tkzou.miniforum.recommend;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorScene;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;

import java.time.LocalDateTime;

/**
 * 测试辅助：快速构造行为日志
 */
public final class TestBehaviors {

    private TestBehaviors() {
    }

    public static BehaviorLog behavior(Long userId, Long postId, BehaviorType type, LocalDateTime ts) {
        BehaviorLog b = new BehaviorLog();
        b.setUserId(userId);
        b.setPostId(postId);
        b.setType(type);
        b.setTimestamp(ts);
        b.setScene(BehaviorScene.POST);
        return b;
    }

    public static BehaviorLog behavior(Long userId, Long postId, BehaviorType type) {
        return behavior(userId, postId, type, LocalDateTime.now());
    }
}
