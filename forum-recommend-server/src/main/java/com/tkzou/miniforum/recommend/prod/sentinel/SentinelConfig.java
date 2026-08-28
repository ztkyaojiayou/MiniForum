package com.tkzou.miniforum.recommend.prod.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.tkzou.miniforum.recommend.service.RecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 流量治理配置（P3-1 限流 + P3-2 熔断，@Profile("prod") 激活）
 * <p>
 * <b>作用</b>：生产 profile 下给推荐主链路资源 {@code recommend-feed}
 * （{@link RecommendService#SENTINEL_RESOURCE_FEED}）下发两条规则：
 * <ul>
 *   <li><b>限流（FlowRule）</b>：单机 QPS 阈值——每 pod 本地计数（形态 B，非集群流控；集群留生产部署文档项）；</li>
 *   <li><b>熔断（DegradeRule）</b>：异常比例——推荐链路异常率超阈值即熔断（触发后 entry 抛 DegradeException→BlockException，
 *       由 {@link RecommendService#recommend} 降级热门兜底）。</li>
 * </ul>
 * <b>时序保证</b>：规则在 {@code @PostConstruct} 加载——Spring 先跑完所有 bean 初始化再对外收请求，
 * 无"请求先到、规则未加载"窗口。
 * <p>
 * <b>演示（!prod）</b>：本类不加载 → 无规则 → {@code SphU.entry} 原样放行，行为与接入前一致。
 * 总开关 {@code app.rec.sentinel.enabled=false} 时同样只埋点不下发规则。
 */
@Component
@Profile("prod")
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    /** 是否下发 Sentinel 规则（总开关：false 时仅埋点、不拦截） */
    @Value("${app.rec.sentinel.enabled:true}")
    private boolean enabled;

    /** 推荐 feed 单机 QPS 阈值（每 pod 本地计数） */
    @Value("${app.rec.sentinel.feed.flow-qps:100}")
    private double flowQps;

    /** 熔断：异常比例阈值（0~1，推荐链路异常率超过则熔断） */
    @Value("${app.rec.sentinel.feed.degrade.ratio:0.5}")
    private double degradeRatio;

    /** 熔断：最小请求数（样本不足不判定，防一两个失败误熔断） */
    @Value("${app.rec.sentinel.feed.degrade.min-requests:10}")
    private int degradeMinRequests;

    /** 熔断：熔断打开后的等待窗口（秒），过后进入半开试探（成功→恢复，失败→再熔断） */
    @Value("${app.rec.sentinel.feed.degrade.time-window-seconds:10}")
    private int degradeTimeWindowSeconds;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Sentinel 规则下发已禁用（app.rec.sentinel.enabled=false）：仅埋点，不拦截流量");
            return;
        }
        // 1. QPS 限流规则：单机模式（每 pod 各自计数，配额=单机容量）
        List<FlowRule> flowRules = new ArrayList<>();
        FlowRule feedFlow = new FlowRule(RecommendService.SENTINEL_RESOURCE_FEED);
        feedFlow.setGrade(RuleConstant.FLOW_GRADE_QPS);
        feedFlow.setCount(flowQps);
        flowRules.add(feedFlow);
        FlowRuleManager.loadRules(flowRules);

        // 2. 异常比例熔断规则：推荐链路异常率超阈值即熔断，窗口期后自动半开试探恢复
        List<DegradeRule> degradeRules = new ArrayList<>();
        DegradeRule feedDegrade = new DegradeRule(RecommendService.SENTINEL_RESOURCE_FEED);
        feedDegrade.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        feedDegrade.setCount(degradeRatio);
        feedDegrade.setMinRequestAmount(degradeMinRequests);
        feedDegrade.setTimeWindow(degradeTimeWindowSeconds);
        feedDegrade.setStatIntervalMs(10_000);
        degradeRules.add(feedDegrade);
        DegradeRuleManager.loadRules(degradeRules);

        log.info("Sentinel 规则已下发：resource={}, flowQps={}（单机QPS）, degradeRatio={}, minRequests={}, timeWindow={}s",
                RecommendService.SENTINEL_RESOURCE_FEED, flowQps, degradeRatio, degradeMinRequests, degradeTimeWindowSeconds);
    }
}
