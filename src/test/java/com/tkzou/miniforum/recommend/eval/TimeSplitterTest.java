package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.recommend.TestBehaviors;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间切分器单元测试：必须按时间升序切分（严禁随机切分导致时间泄漏）。
 */
class TimeSplitterTest {

    @Test
    void splitByTime_shouldRespectTimestampOrder() {
        LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 10, 0);
        List<BehaviorLog> behaviors = List.of(
                TestBehaviors.behavior(1L, 1L, BehaviorType.LIKE, t0),
                TestBehaviors.behavior(1L, 2L, BehaviorType.LIKE, t0.plusHours(1)),
                TestBehaviors.behavior(1L, 3L, BehaviorType.LIKE, t0.plusHours(2)),
                TestBehaviors.behavior(1L, 4L, BehaviorType.LIKE, t0.plusHours(3)));

        TrainTestSplit split = TimeSplitter.splitByTime(behaviors, 0.5);
        assertEquals(2, split.trainSize());
        assertEquals(2, split.testSize());
        // 训练集最后一条的时间不晚于测试集第一条（保证时间顺序不被破坏）
        BehaviorLog lastTrain = split.train().get(split.trainSize() - 1);
        BehaviorLog firstTest = split.test().get(0);
        assertTrue(!lastTrain.getTimestamp().isAfter(firstTest.getTimestamp()));
    }
}
