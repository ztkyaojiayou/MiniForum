package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.model.ItemCfBuilder;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.prod.clickhouse.ClickHouseBehaviorStore;
import com.tkzou.miniforum.recommend.prod.redis.ItemCfModelRedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ItemCF 模型发布器（P2-3 离线构建 → 发布）
 * <p>
 * 从行为全量（生产 ClickHouse / 演示内存）构建 ItemCF → 发布 Redis "itemcf:latest"，
 * 供在线多实例读取（ItemCfModelStore 的 Redis 优先路径，免各实例本地重建）。
 * 由 XXL-Job handler "itemcf-publish"（XxlJobOfflineConfig）触发。
 */
@Component
public class ItemCfModelPublisher {

    private static final Logger log = LoggerFactory.getLogger(ItemCfModelPublisher.class);
    private static final int TOP_K = 50;

    private final BehaviorLogRepository behaviorLogRepository;
    private final ItemCfBuilder builder = new ItemCfBuilder();
    /** 生产：从 ClickHouse 数仓读行为全量（离线侧事实源）；演示为 null → 内存仓库 */
    @Autowired(required = false)
    private ClickHouseBehaviorStore clickHouseBehaviorStore;
    /** 生产：Redis 模型存取（@Profile("prod")）；演示为 null → 跳过发布 */
    @Autowired(required = false)
    private ItemCfModelRedisStore itemCfModelRedisStore;

    public ItemCfModelPublisher(BehaviorLogRepository behaviorLogRepository) {
        this.behaviorLogRepository = behaviorLogRepository;
    }

    /** 构建并发布模型到 Redis（在线 ItemCfModelStore 读取）；未装配 Redis 时跳过（演示安全） */
    public void publish() {
        if (itemCfModelRedisStore == null) {
            log.warn("ItemCF 发布跳过：未装配 Redis 模型存储（演示 profile 不发布）");
            return;
        }
        List<BehaviorLog> all = clickHouseBehaviorStore != null
                ? clickHouseBehaviorStore.findAll()   // 生产：数仓全量
                : behaviorLogRepository.findAll();    // 演示：内存
        ItemCfModel model = builder.build(all, TOP_K);
        itemCfModelRedisStore.publish(model);
        log.info("ItemCF 模型已发布：物品数={}，行为数={}", model.size(), all.size());
    }
}
