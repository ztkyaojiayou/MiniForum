package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final List<RecallChannel> channels;
    private final MergeRecallService merger;
    private final ConfigService configService;

    public RecallService(List<RecallChannel> channels,
                         MergeRecallService merger,
                         ConfigService configService) {
        this.channels = channels;
        this.merger = merger;
        this.configService = configService;
    }

    /** 多路召回 → 融合 → 返回候选集 */
    public List<Candidate> recall(RecommendContext ctx) {
        RecConfig cfg = configService.current();
        List<com.tkzou.miniforum.recommend.domain.RecallHit> hits = new ArrayList<>();
        for (RecallChannel channel : channels) {
            hits.addAll(channel.recall(ctx, cfg.getRecallPerChannel()));
        }
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
