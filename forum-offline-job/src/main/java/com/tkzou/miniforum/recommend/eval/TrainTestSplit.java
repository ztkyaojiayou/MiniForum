package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;

import java.util.List;

/**
 * 时间切分结果（训练集 / 测试集）
 */
public record TrainTestSplit(List<BehaviorLog> train, List<BehaviorLog> test) {

    public int trainSize() {
        return train.size();
    }

    public int testSize() {
        return test.size();
    }
}
