package com.tkzou.miniforum.recommend.prod.kafka;

import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka 行为采集器（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * <b>数据流程</b>：{@link #log} → Jackson 序列化行为事件 → {@code KafkaProducer} 写入 topic "behavior-log"
 * → 供 Flink 实时特征与离线数仓消费。与内存实现（InMemoryBehaviorLogger）实现同一 {@link BehaviorLogger} 接口，可平滑替换。
 */
@Component
@Profile("prod")
public class KafkaBehaviorLogger implements BehaviorLogger {

    private static final Logger log = LoggerFactory.getLogger(KafkaBehaviorLogger.class);

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaBehaviorLogger(
            @Value("${app.rec.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        this.producer = new KafkaProducer<>(props);
        log.info("Kafka 行为采集器初始化完成，bootstrap={}", bootstrapServers);
    }

    @Override
    public void log(Long userId, Long postId, BehaviorType type, String scene, String expId) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("userId", userId);
            event.put("postId", postId);
            event.put("type", type.name());
            event.put("scene", scene);
            event.put("expId", expId);
            event.put("timestamp", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(event);
            producer.send(new ProducerRecord<>("behavior-log", String.valueOf(userId), json));
        } catch (Exception e) {
            log.warn("行为写入 Kafka 失败：{}", e.getMessage());
        }
    }

    /** 关闭时刷盘并关闭 producer（生产由 Spring 生命周期管理） */
    public void close() {
        producer.flush();
        producer.close();
    }
}
