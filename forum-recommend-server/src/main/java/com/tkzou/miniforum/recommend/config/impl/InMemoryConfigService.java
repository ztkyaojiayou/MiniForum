package com.tkzou.miniforum.recommend.config.impl;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.config.ConfigService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存配置中心（默认实现）
 * <p>
 * 启动时从 application.yml 的 app.rec.* 加载默认值构建 {@link RecConfig}；
 * 运行时可经 {@link #update} 热更新（AB 实验/灰度调参）。
 * 生产形态见 prod.nacos.NacosConfigService（@Profile("prod")，激活 prod 时本实现不加载）。
 */
@Component
@Profile("!prod")
public class InMemoryConfigService implements ConfigService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConfigService.class);

    private final AtomicLong version = new AtomicLong(1);
    private volatile RecConfig current;
    private final ObjectMapper objectMapper;

    public InMemoryConfigService(
            @Value("${app.rec.final-top-n:20}") int finalTopN,
            @Value("${app.rec.merge-top-n:200}") int mergeTopN,
            @Value("${app.rec.coarse-top-n:200}") int coarseTopN,
            @Value("${app.rec.recall-per-channel:100}") int recallPerChannel,
            @Value("${app.rec.cold-start-ratio:0.15}") double coldStartRatio,
            @Value("${app.rec.min-behavior-for-warm:5}") int minBehaviorForWarm,
            @Value("${app.rec.half-life-hours:4.0}") double halfLifeHours,
            @Value("${app.rec.category-max-count:2}") int categoryMaxCount,
            @Value("${app.rec.mmr-lambda:0.6}") double mmrLambda,
            @Value("${app.rec.mmr-window:10}") int mmrWindow,
            @Value("${app.rec.new-item-age-hours:48}") int newItemAgeHours,
            @Value("${app.rec.new-item-min-interactions:5}") int newItemMinInteractions,
            @Value("${app.rec.explore-lambda-new-user:0.7}") double exploreLambdaNewUser,
            @Value("${app.rec.explore-lambda-warm-user:0.1}") double exploreLambdaWarmUser,
            @Value("${app.rec.realtime-window-minutes:5}") int realtimeWindowMinutes,
            @Value("${app.rec.realtime-window-max-events:100}") int realtimeWindowMaxEvents,
            @Value("${app.rec.channel-weight:{\"hot\":1.0,\"topic\":1.0,\"category\":0.6,\"itemcf\":1.2,\"newitem\":0.5,\"follow\":0.8}}") String channelWeightJson,
            @Value("${app.rec.rank-weight:{\"interact\":0.30,\"quality\":0.20,\"interest\":0.30,\"social\":0.15,\"author\":0.10,\"hot\":0.10,\"realtime\":0.05}}") String rankWeightJson,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        RecConfig.Builder b = RecConfig.defaults().copy()
                .finalTopN(finalTopN)
                .mergeTopN(mergeTopN)
                .coarseTopN(coarseTopN)
                .recallPerChannel(recallPerChannel)
                .coldStartRatio(coldStartRatio)
                .minBehaviorForWarm(minBehaviorForWarm)
                .halfLifeHours(halfLifeHours)
                .categoryMaxCount(categoryMaxCount)
                .mmrLambda(mmrLambda)
                .mmrWindow(mmrWindow)
                .newItemAgeHours(newItemAgeHours)
                .newItemMinInteractions(newItemMinInteractions)
                .exploreLambdaNewUser(exploreLambdaNewUser)
                .exploreLambdaWarmUser(exploreLambdaWarmUser)
                .realtimeWindowMinutes(realtimeWindowMinutes)
                .realtimeWindowMaxEvents(realtimeWindowMaxEvents);
        b.channelWeight(parseMap(channelWeightJson));
        b.rankWeight(parseMap(rankWeightJson));
        this.current = b.build();
        log.info("推荐配置已加载，version={}", version.get());
    }

    /** 解析 JSON 字符串为 Map（权重配置），解析失败回退默认 */
    private Map<String, Double> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            log.warn("解析权重配置失败，使用默认值", e);
            return Map.of();
        }
    }

    @Override
    public RecConfig current() {
        return current;
    }

    @Override
    public void update(RecConfig config) {
        this.current = config;
        version.incrementAndGet();
        log.info("推荐配置已更新，version={}", version.get());
    }

    @Override
    public long version() {
        return version.get();
    }
}
