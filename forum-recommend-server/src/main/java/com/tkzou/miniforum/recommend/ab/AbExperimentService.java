package com.tkzou.miniforum.recommend.ab;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AB 实验服务（分层正交思路）
 * <p>
 * <b>数据流程</b>：{@code RecommendService} 按 {@link #configFor(expId, uid)} 取配置 → 对照组 A 用全局配置、
 * 实验组 B 用多样性变体 → 行为日志携带 expId → 离线评估按 expId 归因对比。
 * 分桶：bucket = floorMod(hash(uid:salt), 100)，同一用户同 salt 稳定落同一桶；
 * 不同 layer 用不同 salt 实现"层内互斥、层间正交"（Google Overlapping Experiment）。
 */
@Component
public class AbExperimentService {

    /** 实验：expId → 流量百分比 */
    private final Map<String, Integer> experiments = new ConcurrentHashMap<>();
    private final ConfigService configService;

    public AbExperimentService(ConfigService configService) {
        this.configService = configService;
        // 默认注册一个实验：rec-v1，50% 流量进实验组 B
        register("rec-v1", 50);
    }

    /** 注册实验（生产由配置中心下发） */
    public void register(String expId, int percent) {
        experiments.put(expId, Math.min(Math.max(percent, 0), 100));
    }

    /** 分桶：hash(uid:salt) % 100 */
    public int bucket(Long userId, String salt) {
        String key = userId + ":" + (salt == null ? "default" : salt);
        int hash = key.hashCode() & Integer.MAX_VALUE;
        return hash % 100;
    }

    /** 用户是否命中某实验（实验组 B） */
    public boolean inExperiment(Long userId, String expId) {
        Integer percent = experiments.get(expId);
        if (percent == null) {
            return false;
        }
        return bucket(userId, expId) < percent;
    }

    /** 分层正交：不同 layer 用不同 salt，返回层桶名（示例） */
    public String layerBucket(Long userId, String layer) {
        return "L" + bucket(userId, layer) % 10;
    }

    /**
     * 按实验组返回配置变体：实验组 B 走多样性变体，对照组 A 走当前全局配置。
     * 演示"不同分组走不同 RecConfig"的 AB 玩法。
     */
    public RecConfig configFor(String expId, Long userId) {
        RecConfig base = configService.current();
        if (expId == null || !inExperiment(userId, expId)) {
            return base;
        }
        // 实验组 B：多样性变体（MMR 更分散、兴趣权重提升）
        return base.copy()
                .mmrLambda(0.3)
                .rankWeight("interest", 0.35)
                .rankWeight("hot", 0.05)
                .build();
    }

    /** 当前实验的组名（A 对照组 / B 实验组） */
    public String groupOf(Long userId, String expId) {
        return inExperiment(userId, expId) ? "B" : "A";
    }
}
