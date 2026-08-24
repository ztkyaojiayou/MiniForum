package com.tkzou.miniforum.recommend.prod.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * Kafka 行为消费者（生产适配，@Profile("prod") 激活）
 * <p>
 * <b>数据流程</b>：订阅 topic "behavior-log"（由 {@code KafkaBehaviorLogger} 写入）→ 后台线程轮询消费
 * → 反序列化为 {@link BehaviorLog} → ①保存到 {@link BehaviorLogRepository}（喂给画像/ItemCF/离线评估）；
 * ②发布到 {@link BehaviorEventQueue}（喂给冷启动反馈等近线消费者）。
 * 与 Flink 作业（近线实时特征）构成"一份行为、两处消费"：Flink 做实时特征，本消费者做离线侧落库。
 * 生产启用：-Pprod 构建 + spring.profiles.active=prod + 配置 app.rec.kafka.bootstrap-servers。
 */
@Component
@Profile("prod")
public class KafkaBehaviorConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaBehaviorConsumer.class);

    private final BehaviorLogRepository behaviorLogRepository;
    private final BehaviorEventQueue eventQueue;
    private final ObjectMapper objectMapper;

    @Value("${app.rec.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private volatile boolean running = true;
    private KafkaConsumer<String, String> consumer;
    private Thread thread;

    public KafkaBehaviorConsumer(BehaviorLogRepository behaviorLogRepository,
                                 BehaviorEventQueue eventQueue,
                                 ObjectMapper objectMapper) {
        this.behaviorLogRepository = behaviorLogRepository;
        this.eventQueue = eventQueue;
        this.objectMapper = objectMapper;
    }

    /** 启动后台消费线程 */
    @PostConstruct
    public void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "mini-forum-offline");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("behavior-log"));
        thread = new Thread(this::consumeLoop, "kafka-behavior-consumer");
        thread.setDaemon(true);
        thread.start();
        log.info("Kafka 行为消费者已启动，bootstrap={}, topic=behavior-log", bootstrapServers);
    }

    private void consumeLoop() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        BehaviorLog behavior = objectMapper.readValue(record.value(), BehaviorLog.class);
                        behaviorLogRepository.save(behavior);
                        eventQueue.publish(behavior);
                    } catch (Exception e) {
                        log.warn("解析行为消息失败：{}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Kafka 消费异常：{}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumer != null) {
            consumer.close();
        }
        log.info("Kafka 行为消费者已停止");
    }
}
