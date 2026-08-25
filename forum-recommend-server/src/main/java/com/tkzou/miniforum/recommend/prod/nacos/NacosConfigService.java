package com.tkzou.miniforum.recommend.prod.nacos;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nacos 配置中心（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * <b>数据流程</b>：启动拉取 dataId="rec-config" 的配置 JSON → 解析为 {@link RecConfig}；
 * 监听配置变更（Listener）实时刷新，供灰度调参与 AB 分组下发。与内存实现（InMemoryConfigService）实现同一接口。
 */
@Component
@Profile("prod")
public class NacosConfigService implements ConfigService {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigService.class);
    private static final String DATA_ID = "rec-config";
    private static final String GROUP = "DEFAULT_GROUP";

    private final ObjectMapper objectMapper;
    private final AtomicLong version = new AtomicLong(1);
    private volatile RecConfig current = RecConfig.defaults();

    public NacosConfigService(
            @Value("${app.rec.nacos.server-addr:localhost:8848}") String serverAddr,
            ObjectMapper objectMapper) throws NacosException {
        this.objectMapper = objectMapper;
        Properties props = new Properties();
        props.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        com.alibaba.nacos.api.config.ConfigService nacos = NacosFactory.createConfigService(props);

        String content = nacos.getConfig(DATA_ID, GROUP, 5000);
        if (content != null && !content.isBlank()) {
            this.current = parse(content);
            log.info("Nacos 推荐配置已加载，version={}", version.get());
        }
        nacos.addListener(DATA_ID, GROUP, new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                if (configInfo == null || configInfo.isBlank()) {
                    return;
                }
                current = parse(configInfo);
                version.incrementAndGet();
                log.info("Nacos 推荐配置已刷新，version={}", version.get());
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        });
    }

    /** 解析 Nacos 配置 JSON 为 RecConfig（含数值字段与权重 map） */
    private RecConfig parse(String json) {
        try {
            RecConfigJson dto = objectMapper.readValue(json, RecConfigJson.class);
            RecConfig.Builder b = RecConfig.defaults().copy()
                    .finalTopN(orDefault(dto.finalTopN, 20))
                    .mergeTopN(orDefault(dto.mergeTopN, 200))
                    .coldStartRatio(orDefault(dto.coldStartRatio, 0.15))
                    .halfLifeHours(orDefault(dto.halfLifeHours, 4.0))
                    .mmrLambda(orDefault(dto.mmrLambda, 0.6))
                    .categoryMaxCount(orDefault(dto.categoryMaxCount, 2));
            if (dto.channelWeight != null) {
                b.channelWeight(dto.channelWeight);
            }
            if (dto.rankWeight != null) {
                b.rankWeight(dto.rankWeight);
            }
            return b.build();
        } catch (Exception e) {
            log.warn("解析 Nacos 推荐配置失败，使用默认值：{}", e.getMessage());
            return RecConfig.defaults();
        }
    }

    private int orDefault(Integer v, int d) {
        return v == null ? d : v;
    }

    private double orDefault(Double v, double d) {
        return v == null ? d : v;
    }

    @Override
    public RecConfig current() {
        return current;
    }

    @Override
    public void update(RecConfig config) {
        // 生产形态：更新应发布到 Nacos 由服务端广播，这里仅本地生效占位
        this.current = config;
        version.incrementAndGet();
    }

    @Override
    public long version() {
        return version.get();
    }

    /** Nacos 配置 JSON 结构 */
    public static class RecConfigJson {
        public Integer finalTopN;
        public Integer mergeTopN;
        public Double coldStartRatio;
        public Double halfLifeHours;
        public Double mmrLambda;
        public Integer categoryMaxCount;
        public Map<String, Double> channelWeight;
        public Map<String, Double> rankWeight;
    }
}
