package com.tkzou.miniforum.recommend.model;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.prod.clickhouse.ClickHouseBehaviorStore;
import com.tkzou.miniforum.recommend.prod.redis.ItemCfModelRedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ItemCF 模型存储（启动/行为增长时重建；P2-3 支持离线发布 → Redis → 在线读取）
 * <p>
 * <b>数据流程</b>：行为全量 → {@link ItemCfBuilder#build} 构建物品共现相似度表 → 缓存为 {@link ItemCfModel}。
 * <ul>
 *   <li><b>生产（redisStore 非空）</b>：优先读 Redis 已发布模型（离线构建→发布→在线读取，多实例共享，
 *       免每请求 count()）；未发布时本地重建兜底并从 ClickHouse 读全量，重建后回写 Redis。</li>
 *   <li><b>演示（redisStore 为 null）</b>：按行为数变化本地重建（内存 count 便宜，行为不变）。</li>
 * </ul>
 * 被 {@code ItemCfRecall} 与详情"相关推荐"读取。
 */
@Component
public class ItemCfModelStore {

    private static final Logger log = LoggerFactory.getLogger(ItemCfModelStore.class);
    private static final int TOP_K = 50;

    private final BehaviorLogRepository behaviorLogRepository;
    private final ItemCfBuilder builder = new ItemCfBuilder();
    /** 生产：Redis 已发布模型（P2-3，多实例共享）；演示为 null → 本地重建 */
    @Autowired(required = false)
    private ItemCfModelRedisStore redisStore;
    /** 生产：从 ClickHouse 数仓读行为全量（离线侧事实源）；演示为 null → 内存仓库 */
    @Autowired(required = false)
    private ClickHouseBehaviorStore clickHouseBehaviorStore;

    private volatile ItemCfModel model = ItemCfModel.empty();
    private volatile long builtAtBehaviorCount = -1;

    public ItemCfModelStore(BehaviorLogRepository behaviorLogRepository) {
        this.behaviorLogRepository = behaviorLogRepository;
    }

    /**
     * 获取当前模型：
     * <ul>
     *   <li>生产：读 Redis 已发布模型（命中即返回，不 rebuild 不 count）；未发布才本地重建兜底；</li>
     *   <li>演示：按行为数变化本地重建（现状）。</li>
     * </ul>
     */
    public ItemCfModel get() {
        if (redisStore != null) {
            return redisStore.get().orElseGet(() -> {
                rebuild();
                return model;
            });
        }
        long count = behaviorLogRepository.count();
        if (count != builtAtBehaviorCount) {
            rebuild();
        }
        return model;
    }

    /** 强制重建：prod 从 ClickHouse 读全量 / 演示从内存；重建后回写 Redis 供多实例共享 */
    public synchronized void rebuild() {
        List<BehaviorLog> all = clickHouseBehaviorStore != null
                ? clickHouseBehaviorStore.findAll()   // 生产：数仓全量（P2-1 行为读切换）
                : behaviorLogRepository.findAll();    // 演示：内存
        this.model = builder.build(all, TOP_K);
        // 演示路径 get() 用 behaviorLogRepository.count() 判变化，故须与之一致；prod 走 Redis 路径不使用
        this.builtAtBehaviorCount = clickHouseBehaviorStore != null
                ? all.size()
                : behaviorLogRepository.count();
        if (redisStore != null) {
            redisStore.publish(model);  // 回写 Redis：首个重建实例把模型共享出去
        }
        log.info("ItemCF 模型已重建，物品数={}，行为数={}", model.size(), builtAtBehaviorCount);
    }
}
