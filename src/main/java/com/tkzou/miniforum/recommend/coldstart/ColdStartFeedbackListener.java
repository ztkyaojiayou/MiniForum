package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import org.springframework.stereotype.Component;

/**
 * 冷启动反馈监听器
 * <p>
 * <b>数据流程</b>：订阅 {@code BehaviorEventQueue} → 池内新内容的深度互动(点击/点赞/收藏/评论/转发)记为成功、
 * 曝光记为一次待惩罚 → 回灌 {@link NewItemPool#recordOutcome} 更新 Thompson 后验
 * （模拟生产"曝光→点击→回灌 bandit"的在线学习闭环）。
 */
@Component
public class ColdStartFeedbackListener {

    private final NewItemPool newItemPool;

    public ColdStartFeedbackListener(NewItemPool newItemPool, BehaviorEventQueue eventQueue) {
        this.newItemPool = newItemPool;
        eventQueue.subscribe(this::onEvent);
    }

    private void onEvent(BehaviorLog behavior) {
        if (behavior.getPostId() == null) {
            return;
        }
        if (isSuccess(behavior.getType())) {
            newItemPool.recordOutcome(behavior.getPostId(), true);
        } else if (behavior.getType() == BehaviorType.EXPOSE) {
            newItemPool.recordOutcome(behavior.getPostId(), false);
        }
    }

    private boolean isSuccess(BehaviorType type) {
        return type == BehaviorType.CLICK || type == BehaviorType.LIKE || type == BehaviorType.FAVORITE
                || type == BehaviorType.COMMENT || type == BehaviorType.REPOST;
    }
}
