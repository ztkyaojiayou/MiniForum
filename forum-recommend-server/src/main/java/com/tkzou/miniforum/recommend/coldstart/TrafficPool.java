package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流量池 / 赛马机制（仿抖音新内容渐进式曝光）
 * <p>
 * <b>数据流程</b>：订阅行为事件队列 → 对冷启新帖按档位累计 曝光(EXPOSE) 与 深度互动(LIKE/FAVORITE/COMMENT/REPOST/CLICK/DWELL)
 * → 当当前档位曝光量达到配额 → 用 <b>Wilson 置信区间下界</b> 评估互动率，≥ 基线则晋级放大曝光、否则停止探索
 * → {@link #tierBonus} 给排序层提供"探索保底 + 已验证档位加权"。
 * <p>
 * 关键点：
 * <ul>
 *   <li><b>保底曝光</b>：试探期新帖经 tierBonus 保证进入信息流，跑完档位配额；</li>
 *   <li><b>置信度防抖</b>：Wilson 下界在小样本下收敛到接近真实值，避免"运气好的几条"误判爆款；</li>
 *   <li><b>档位加权</b>：晋级越高的内容被验证越充分，排序加分越多（利用）；失败则回正常排序（不加分）。</li>
 * </ul>
 * 纯规则+统计实现，弱训练侧可完全落地。基线暂用全局可配值，可按类目细化。
 */
@Component
public class TrafficPool {

    private static final Logger log = LoggerFactory.getLogger(TrafficPool.class);

    /** 单帖流量池状态 */
    private static class PostState {
        int tier;                 // 当前档位下标
        int exposures;            // 当前档位内曝光数
        int successes;            // 当前档位内深度互动数
        boolean stopped;          // 是否停止探索（未达标）
        LocalDateTime stoppedAt;  // 停止时间（用于清理）
    }

    private final FeatureService featureService;
    private final BehaviorEventQueue eventQueue;
    private final Map<Long, PostState> states = new ConcurrentHashMap<>();

    @Value("${app.rec.traffic-pool.enabled:true}")
    private boolean enabled;

    @Value("${app.rec.traffic-pool.base-boost:0.3}")
    private double baseBoost;

    @Value("${app.rec.traffic-pool.tier-step:0.2}")
    private double tierStep;

    @Value("${app.rec.traffic-pool.baseline:0.05}")
    private double baseline;

    @Value("${app.rec.traffic-pool.z:1.96}")
    private double z;

    @Value("${app.rec.traffic-pool.tiers:50,500,5000,50000}")
    private String tiersCsv;

    /** 调度模式：local=@Scheduled 自调度（演示默认）/ xxl=由 XXL-Job 派发（生产，@Scheduled 空转防双跑） */
    @Value("${app.scheduling.mode:local}")
    private String schedulingMode;

    public TrafficPool(FeatureService featureService, BehaviorEventQueue eventQueue) {
        this.featureService = featureService;
        this.eventQueue = eventQueue;
        this.eventQueue.subscribe(this::onBehavior);
    }

    /** 行为事件 → 累计曝光/互动 → 达标时评估晋级 */
    public void onBehavior(BehaviorLog b) {
        if (!enabled || b.getPostId() == null) {
            return;
        }
        PostState st = states.get(b.getPostId());
        if (st == null) {
            // 只跟踪冷启新帖
            if (!featureService.itemFeature(b.getPostId()).isInNewPool()) {
                return;
            }
            st = states.computeIfAbsent(b.getPostId(), k -> new PostState());
        }
        if (st.stopped) {
            return;
        }
        if (b.getType() == BehaviorType.EXPOSE) {
            st.exposures++;
        } else if (isDeepInteraction(b.getType())) {
            st.successes++;
        }
        maybePromote(b.getPostId(), st);
    }

    /** 帖子创建事件：若为冷启新帖且未跟踪，则从创建起就进入流量池（prod 由 Kafka 消费者触发） */
    public void notifyCreated(Long postId) {
        if (!enabled || postId == null) {
            return;
        }
        if (states.containsKey(postId)) {
            return;
        }
        if (!featureService.itemFeature(postId).isInNewPool()) {
            return;
        }
        states.put(postId, new PostState());
    }

    /** 当前档位曝光达标 → Wilson 下界 vs 基线 判定晋级/停止 */
    private void maybePromote(Long postId, PostState st) {
        int[] tiers = tiers();
        if (st.tier >= tiers.length) {
            return; // 已到最高档
        }
        int quota = tiers[st.tier];
        if (st.exposures < quota) {
            return;
        }
        double phat = (double) st.successes / Math.max(1, st.exposures);
        double wilson = wilsonLower(phat, st.exposures, z);
        if (wilson >= baseline) {
            st.tier++;
            st.exposures = 0;
            st.successes = 0;
            log.info("流量池晋级：post={} → tier={}", postId, st.tier);
        } else {
            st.stopped = true;
            st.stoppedAt = LocalDateTime.now();
            log.info("流量池停止：post={}，tier={}，wilson={} < baseline={}", postId, st.tier, wilson, baseline);
        }
    }

    /**
     * 排序层的流量池加分：
     * <ul>
     *   <li>试探期新帖：baseBoost + tierStep·tier（保底进入曝光，且晋级越高加分越多）；</li>
     *   <li>已停止/未跟踪：0（回正常排序，由热度/兴趣等特征接管）。</li>
     * </ul>
     */
    public double tierBonus(Long postId) {
        if (!enabled) {
            return 0;
        }
        PostState st = states.get(postId);
        if (st == null || st.stopped) {
            return 0;
        }
        return baseBoost + tierStep * st.tier;
    }

    /** Wilson 置信区间下界：p̂ 是互动率，n 是样本量，z 是置信系数（95%→1.96） */
    public static double wilsonLower(double phat, long n, double z) {
        if (n <= 0) {
            return 0;
        }
        double z2 = z * z;
        double center = phat + z2 / (2 * n);
        double margin = z * Math.sqrt(phat * (1 - phat) / n + z2 / (4 * n * n));
        return (center - margin) / (1 + z2 / n);
    }

    private int[] tiers() {
        String[] parts = tiersCsv == null ? new String[0] : tiersCsv.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    private boolean isDeepInteraction(BehaviorType type) {
        return type == BehaviorType.CLICK || type == BehaviorType.LIKE || type == BehaviorType.FAVORITE
                || type == BehaviorType.COMMENT || type == BehaviorType.REPOST || type == BehaviorType.DWELL;
    }

    /** 定时清理已停止的过期状态（默认每小时）。生产（mode=xxl）由 XXL-Job 派发 doCleanup，此处空转防双跑 */
    @Scheduled(fixedDelayString = "${app.rec.traffic-pool.cleanup-ms:3600000}")
    public void cleanup() {
        if ("xxl".equals(schedulingMode)) {
            return;
        }
        doCleanup();
    }

    /** 清理已停止的过期状态，防止无界增长（演示自调度与 XXL-Job handler 共用入口） */
    public void doCleanup() {
        if (states.isEmpty()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Long> toRemove = new ArrayList<>();
        states.forEach((postId, st) -> {
            if (st.stopped && st.stoppedAt != null && st.stoppedAt.isBefore(cutoff)) {
                toRemove.add(postId);
            }
        });
        toRemove.forEach(states::remove);
    }

    /** 当前跟踪的帖子数（测试/监控） */
    public int size() {
        return states.size();
    }
}
