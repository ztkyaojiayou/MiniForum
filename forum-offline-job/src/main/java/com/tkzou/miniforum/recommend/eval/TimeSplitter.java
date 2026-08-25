package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间切分器
 * <p>
 * 按行为时间戳全局升序，前 trainRatio 为训练、余下为测试。
 * 严禁随机切分（会引入时间泄漏，导致离线指标虚高、上线打折）。
 */
public class TimeSplitter {

    private TimeSplitter() {
    }

    public static TrainTestSplit splitByTime(List<BehaviorLog> behaviors, double trainRatio) {
        List<BehaviorLog> sorted = new ArrayList<>(behaviors);
        sorted.sort(Comparator.comparing(BehaviorLog::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        int split = (int) Math.floor(sorted.size() * trainRatio);
        return new TrainTestSplit(new ArrayList<>(sorted.subList(0, split)),
                new ArrayList<>(sorted.subList(split, sorted.size())));
    }
}
