package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 新内容冷启动池（Thompson bandit 的"臂"集合）
 * <p>
 * <b>数据流程</b>：池内为冷启内容（新发布或互动过少）。每个 item 维护 [alpha, beta, 待惩罚曝光数]：
 * 深度互动 → α+=1；连续 EXPOSE_FAIL_THRESHOLD 次曝光无互动 → β+=1（"曝光无转化"惩罚）。
 * {@link #sampleScore} 供排序层取 Thompson 采样分（探索加分），后验由 {@code ColdStartFeedbackListener} 回灌。
 * <p>
 * 状态经 {@link NewItemPoolStore} 存取：演示 InMemory（单实例）/ 生产 Redis（多实例共享，见 RedisNewItemPoolStore）。
 * 读改写跨 pod 非原子，局限与升级路径见 RedisNewItemPoolStore 注释。
 */
@Component
public class NewItemPool {

    /** 连续曝光多少次无互动记为一次负反馈 */
    private static final int EXPOSE_FAIL_THRESHOLD = 3;

    /** 状态 TTL（秒）：Redis 默认 30 天（InMemory 忽略 TTL） */
    private static final long DEFAULT_TTL_SECONDS = 2592000;

    private final PostRepository postRepository;
    private final ItemFeatureService itemFeatureService;
    private final NewItemPoolStore store;

    public NewItemPool(PostRepository postRepository, ItemFeatureService itemFeatureService, NewItemPoolStore store) {
        this.postRepository = postRepository;
        this.itemFeatureService = itemFeatureService;
        this.store = store;
    }

    /** 当前池内物品（冷启内容） */
    public List<Long> poolItems() {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .map(p -> itemFeatureService.itemFeature(p.getId()))
                .filter(ItemFeature::isInNewPool)
                .map(ItemFeature::getPostId)
                .collect(Collectors.toList());
    }

    /** 读取或原子创建后验参数（语义对齐原 computeIfAbsent：读缺失时以默认 {1,1,0} 入池） */
    private AlphaBeta getOrCreate(long itemId) {
        return store.get(itemId).orElseGet(() -> {
            AlphaBeta fresh = new AlphaBeta();
            store.putIfAbsent(itemId, fresh, DEFAULT_TTL_SECONDS);
            return store.get(itemId).orElse(fresh);
        });
    }

    /** Thompson 采样分 */
    public double sampleScore(long itemId) {
        AlphaBeta ab = getOrCreate(itemId);
        return ThompsonBandit.sampleBeta(ab.getAlpha(), ab.getBeta());
    }

    /** 期望互动率 α/(α+β) */
    public double expect(long itemId) {
        AlphaBeta ab = getOrCreate(itemId);
        return ab.getAlpha() / (ab.getAlpha() + ab.getBeta());
    }

    /**
     * 反馈驱动后验更新
     *
     * @param success true=深度互动（α+1，清空待惩罚曝光）；false=一次曝光（累计阈值后 β+1）
     */
    public void recordOutcome(long itemId, boolean success) {
        AlphaBeta ab = getOrCreate(itemId);
        // 按 item 持锁串行化读-改-写（单 JVM 内原子：α/β/待惩罚曝光不丢更新）；
        // 跨 pod 需走 Redis Hash + HINCRBY / Lua（见 RedisNewItemPoolStore 注释）
        synchronized (ab) {
            if (success) {
                ab.setAlpha(ab.getAlpha() + 1);
                ab.setPendingExposures(0);
            } else {
                ab.setPendingExposures(ab.getPendingExposures() + 1);
                if (ab.getPendingExposures() >= EXPOSE_FAIL_THRESHOLD) {
                    ab.setBeta(ab.getBeta() + 1);
                    ab.setPendingExposures(0);
                }
            }
        }
        store.put(itemId, ab);
    }

    /** 是否在池内（冷启内容） */
    public boolean contains(long itemId) {
        return store.containsKey(itemId) || poolItems().contains(itemId);
    }
}
