package com.tkzou.miniforum.recommend.config;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推荐策略配置（不可变）
 * <p>
 * 承载召回权重、排序特征权重、冷启动比例、打散/MMR 参数、时效半衰期等全部可调参数。
 * 使用 Builder 构造；{@link #copy()} 可派生实验变体（AB 实验按分组走不同配置）。
 * 默认值对齐微博场景调研结论（见 docs/微博推荐调研.md）。
 */
// 样板 getter/setter 由 Lombok @Getter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter
public class RecConfig {

    /** 最终下发条数 */
    private final int finalTopN;
    /** 融合后候选上限 */
    private final int mergeTopN;
    /** 粗排候选上限（融合后缩到该数再进精排；默认=mergeTopN 即透传，架构对齐大厂"粗排千→百"） */
    private final int coarseTopN;
    /** 每路召回上限 */
    private final int recallPerChannel;
    /** 冷启动探索混入比例 */
    private final double coldStartRatio;
    /** 判定"老用户"的最小行为数（行为数低于此值视为冷用户） */
    private final int minBehaviorForWarm;
    /** 时效指数衰减半衰期（小时），微博推荐流约 4h */
    private final double halfLifeHours;
    /** 重排打散：同类话题/类目连续条数上限 */
    private final int categoryMaxCount;
    /** MMR 多样性系数（越大越强调与已选集合的差异） */
    private final double mmrLambda;
    /** MMR 滑动窗口大小（只与最近 N 个已选比较） */
    private final int mmrWindow;
    /** 新内容入池的时间窗口（小时） */
    private final int newItemAgeHours;
    /** 新内容入池的最大历史互动数（低于此值视为新/冷内容） */
    private final int newItemMinInteractions;
    /** 新用户探索权重 λ（Thompson bandit） */
    private final double exploreLambdaNewUser;
    /** 老用户探索权重 λ */
    private final double exploreLambdaWarmUser;
    /** 实时特征窗口（分钟） */
    private final int realtimeWindowMinutes;
    /** 实时特征窗口事件上限 */
    private final int realtimeWindowMaxEvents;

    /** 各召回通道融合权重：hot/topic/category/itemcf/newitem/follow */
    private final Map<String, Double> channelWeight;
    /** 各排序特征权重：interact/quality/interest/social/author/hot */
    private final Map<String, Double> rankWeight;

    private RecConfig(Builder b) {
        this.finalTopN = b.finalTopN;
        this.mergeTopN = b.mergeTopN;
        this.coarseTopN = b.coarseTopN;
        this.recallPerChannel = b.recallPerChannel;
        this.coldStartRatio = b.coldStartRatio;
        this.minBehaviorForWarm = b.minBehaviorForWarm;
        this.halfLifeHours = b.halfLifeHours;
        this.categoryMaxCount = b.categoryMaxCount;
        this.mmrLambda = b.mmrLambda;
        this.mmrWindow = b.mmrWindow;
        this.newItemAgeHours = b.newItemAgeHours;
        this.newItemMinInteractions = b.newItemMinInteractions;
        this.exploreLambdaNewUser = b.exploreLambdaNewUser;
        this.exploreLambdaWarmUser = b.exploreLambdaWarmUser;
        this.realtimeWindowMinutes = b.realtimeWindowMinutes;
        this.realtimeWindowMaxEvents = b.realtimeWindowMaxEvents;
        this.channelWeight = Map.copyOf(b.channelWeight);
        this.rankWeight = Map.copyOf(b.rankWeight);
    }

    /** 默认配置（微博场景方向性经验值） */
    public static RecConfig defaults() {
        return new Builder().build();
    }

    /** 复制一份，供派生 AB 实验变体 */
    public Builder copy() {
        return new Builder(this);
    }

    /** 查询某通道权重（缺失视为 0） */
    public double channelWeightOf(String channel) {
        return channelWeight.getOrDefault(channel, 0.0);
    }

    /** 查询某特征权重（缺失视为 0） */
    public double rankWeightOf(String feature) {
        return rankWeight.getOrDefault(feature, 0.0);
    }

    /** 构造器：不传字段用默认值，可链式覆盖 */
    public static class Builder {
        private int finalTopN = 20;
        private int mergeTopN = 200;
        private int coarseTopN = 200;
        private int recallPerChannel = 100;
        private double coldStartRatio = 0.15;
        private int minBehaviorForWarm = 5;
        private double halfLifeHours = 4.0;
        private int categoryMaxCount = 2;
        private double mmrLambda = 0.6;
        private int mmrWindow = 10;
        private int newItemAgeHours = 48;
        private int newItemMinInteractions = 5;
        private double exploreLambdaNewUser = 0.7;
        private double exploreLambdaWarmUser = 0.1;
        private int realtimeWindowMinutes = 5;
        private int realtimeWindowMaxEvents = 100;
        private Map<String, Double> channelWeight = new LinkedHashMap<>(Map.of(
                "hot", 1.0, "topic", 1.0, "category", 0.6,
                "itemcf", 1.2, "newitem", 0.5, "follow", 0.8));
        private Map<String, Double> rankWeight = new LinkedHashMap<>(Map.of(
                "interact", 0.30, "quality", 0.20, "interest", 0.30,
                "social", 0.15, "author", 0.10, "hot", 0.10, "realtime", 0.05));

        public Builder() {
        }

        private Builder(RecConfig src) {
            this.finalTopN = src.finalTopN;
            this.mergeTopN = src.mergeTopN;
            this.coarseTopN = src.coarseTopN;
            this.recallPerChannel = src.recallPerChannel;
            this.coldStartRatio = src.coldStartRatio;
            this.minBehaviorForWarm = src.minBehaviorForWarm;
            this.halfLifeHours = src.halfLifeHours;
            this.categoryMaxCount = src.categoryMaxCount;
            this.mmrLambda = src.mmrLambda;
            this.mmrWindow = src.mmrWindow;
            this.newItemAgeHours = src.newItemAgeHours;
            this.newItemMinInteractions = src.newItemMinInteractions;
            this.exploreLambdaNewUser = src.exploreLambdaNewUser;
            this.exploreLambdaWarmUser = src.exploreLambdaWarmUser;
            this.realtimeWindowMinutes = src.realtimeWindowMinutes;
            this.realtimeWindowMaxEvents = src.realtimeWindowMaxEvents;
            this.channelWeight = new LinkedHashMap<>(src.channelWeight);
            this.rankWeight = new LinkedHashMap<>(src.rankWeight);
        }

        public Builder finalTopN(int v) {
            this.finalTopN = v;
            return this;
        }

        public Builder mergeTopN(int v) {
            this.mergeTopN = v;
            return this;
        }

        public Builder coarseTopN(int v) {
            this.coarseTopN = v;
            return this;
        }

        public Builder recallPerChannel(int v) {
            this.recallPerChannel = v;
            return this;
        }

        public Builder coldStartRatio(double v) {
            this.coldStartRatio = v;
            return this;
        }

        public Builder minBehaviorForWarm(int v) {
            this.minBehaviorForWarm = v;
            return this;
        }

        public Builder halfLifeHours(double v) {
            this.halfLifeHours = v;
            return this;
        }

        public Builder categoryMaxCount(int v) {
            this.categoryMaxCount = v;
            return this;
        }

        public Builder mmrLambda(double v) {
            this.mmrLambda = v;
            return this;
        }

        public Builder mmrWindow(int v) {
            this.mmrWindow = v;
            return this;
        }

        public Builder newItemAgeHours(int v) {
            this.newItemAgeHours = v;
            return this;
        }

        public Builder newItemMinInteractions(int v) {
            this.newItemMinInteractions = v;
            return this;
        }

        public Builder exploreLambdaNewUser(double v) {
            this.exploreLambdaNewUser = v;
            return this;
        }

        public Builder exploreLambdaWarmUser(double v) {
            this.exploreLambdaWarmUser = v;
            return this;
        }

        public Builder realtimeWindowMinutes(int v) {
            this.realtimeWindowMinutes = v;
            return this;
        }

        public Builder realtimeWindowMaxEvents(int v) {
            this.realtimeWindowMaxEvents = v;
            return this;
        }

        public Builder channelWeight(Map<String, Double> v) {
            this.channelWeight = new LinkedHashMap<>(v);
            return this;
        }

        public Builder channelWeight(String channel, double weight) {
            this.channelWeight.put(channel, weight);
            return this;
        }

        public Builder rankWeight(Map<String, Double> v) {
            this.rankWeight = new LinkedHashMap<>(v);
            return this;
        }

        public Builder rankWeight(String feature, double weight) {
            this.rankWeight.put(feature, weight);
            return this;
        }

        public RecConfig build() {
            return new RecConfig(this);
        }
    }
}
