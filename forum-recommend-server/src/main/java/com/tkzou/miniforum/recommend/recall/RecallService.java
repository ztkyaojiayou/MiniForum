package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 召回编排服务
 * <p>
 * <b>数据流程</b>：{@link RecommendContext} → 依次运行全部 {@link RecallChannel}（Spring 注入的 6 路实现：
 * hot/topic/category/itemcf/newitem/follow，每路返回 {@code List<RecallHit>}）→ 汇总后交给
 * {@link MergeRecallService#merge} 做 rank 归一化+通道加权+去重 → 输出融合后的 {@link Candidate} 候选集，
 * 供排序层 {@code RuleRankService} 消费。
 * 通道权重来自 {@code RecConfig.channelWeight}，可配置。
 */
@Component
public class RecallService {

    private static final Logger log = LoggerFactory.getLogger(RecallService.class);

    private final List<RecallChannel> channels;
    private final MergeRecallService merger;
    private final ConfigService configService;

    /**
     * 召回并行线程池：六路召回互不依赖 → 并行执行（腾讯"并发化"方法论）。
     * 独立小池（而非占用 Tomcat 请求线程），避免慢召回通道拖住整条请求；daemon 线程不影响 JVM 退出。
     * 禁止 {@code Executors.newFixedThreadPool}（手册点名）：改显式 {@link ThreadPoolExecutor}，
     * core/max 与通道数绑定 + 有界队列 + CallerRuns（队满时请求线程内联执行，宁可拖慢单请求、不无界堆积 OOM）。
     */
    private final ExecutorService channelExecutor;

    public RecallService(List<RecallChannel> channels,
                         MergeRecallService merger,
                         ConfigService configService) {
        this.channels = channels;
        this.merger = merger;
        this.configService = configService;
        int poolSize = Math.max(1, channels.size()); // core/max 与通道数绑定（ThreadPoolExecutor 要求 max>=1）
        this.channelExecutor = new ThreadPoolExecutor(
                poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(poolSize),
                r -> {
                    Thread t = new Thread(r, "recall-channel");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    public void shutdown() {
        channelExecutor.shutdown();
        log.info("召回并行线程池已关闭");
    }

    /**
     * 多路召回 → 融合 → 返回候选集。
     * <p>
     * 高并发优化：六路并行发起；单路失败只丢弃该路（多路召回互为兜底，大厂容灾原则），
     * 不拖垮整次召回——召回耗时从"6 路耗时求和"降为"6 路耗时最大值"。
     */
    public List<Candidate> recall(RecommendContext ctx) {
        RecConfig cfg = configService.current();
        // 先一次性提交全部六路（stream 的 map 是惰性的，若边提交边 join 会退化成串行）；
        // 单路失败只丢弃该路（多路召回互为兜底，大厂容灾原则），不拖垮整次召回。
        List<CompletableFuture<List<RecallHit>>> futures = channels.stream()
                .map(channel -> CompletableFuture
                        .supplyAsync(() -> channel.recall(ctx, cfg.getRecallPerChannel()), channelExecutor)
                        .exceptionally(e -> {
                            log.warn("召回通道失败（丢弃该路，其余继续）：source={}", channel.name(), e);
                            return List.<RecallHit>of();
                        }))
                .collect(Collectors.toList());
        // 再统一等待：召回耗时 = 6 路耗时最大值（而非求和）
        List<RecallHit> hits = futures.stream()
                .flatMap(future -> future.join().stream())
                .collect(Collectors.toList());
        return merger.merge(hits, cfg, cfg.getMergeTopN());
    }

    /** 调试：各路召回命中数 */
    public Map<String, Integer> channelHitCount(RecommendContext ctx) {
        Map<String, Integer> counts = new HashMap<>();
        int perChannel = configService.current().getRecallPerChannel();
        for (RecallChannel channel : channels) {
            counts.put(channel.name(), channel.recall(ctx, perChannel).size());
        }
        return counts;
    }

    public List<RecallChannel> channels() {
        return channels;
    }
}
