package com.tkzou.miniforum.recommend.model;

import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ItemCF 模型存储（启动/行为增长时重建）
 * <p>
 * 弱训练侧的"离线模型"：以行为日志为源构建相似度表并缓存。
 * 行为数量变化时自动重建（对比已构建时的行为条数），保证新反馈尽快进模型。
 */
@Component
public class ItemCfModelStore {

    private static final Logger log = LoggerFactory.getLogger(ItemCfModelStore.class);
    private static final int TOP_K = 50;

    private final BehaviorLogRepository behaviorLogRepository;
    private final ItemCfBuilder builder = new ItemCfBuilder();

    private volatile ItemCfModel model = ItemCfModel.empty();
    private volatile long builtAtBehaviorCount = -1;

    public ItemCfModelStore(BehaviorLogRepository behaviorLogRepository) {
        this.behaviorLogRepository = behaviorLogRepository;
    }

    /** 获取当前模型（行为数变化时自动重建） */
    public ItemCfModel get() {
        long count = behaviorLogRepository.count();
        if (count != builtAtBehaviorCount) {
            rebuild();
        }
        return model;
    }

    /** 强制重建 */
    public synchronized void rebuild() {
        this.model = builder.build(behaviorLogRepository.findAll(), TOP_K);
        this.builtAtBehaviorCount = behaviorLogRepository.count();
        log.info("ItemCF 模型已重建，物品数={}，行为数={}", model.size(), builtAtBehaviorCount);
    }
}
