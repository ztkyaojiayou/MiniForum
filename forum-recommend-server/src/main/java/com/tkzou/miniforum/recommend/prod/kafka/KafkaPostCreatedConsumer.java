package com.tkzou.miniforum.recommend.prod.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.stream.PostCreatedEventBus;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
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
 * Kafka 帖子创建事件消费者（生产适配，@Profile("prod") 激活）
 * <p>
 * <b>数据流程</b>：订阅 topic "post-created"（由 {@code KafkaPostCreatedProducer} 写入）→ 后台线程轮询消费
 * → 反序列化为 {@link PostCreatedEvent} → 广播到事件总线（冷启流量池/扇出/搜索索引等订阅者消费）。
 * 其余下游（搜索索引 / feed 扇出 / 内容管道）可按需在此扩展订阅。
 * 生产启用：-Pprod 构建 + spring.profiles.active=prod + 配置 app.rec.kafka.bootstrap-servers。
 */
@Component
@Profile("prod")
public class KafkaPostCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPostCreatedConsumer.class);

    private final PostCreatedEventBus eventBus;
    private final ObjectMapper objectMapper;

    @Value("${app.rec.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private volatile boolean running = true;
    private KafkaConsumer<String, String> consumer;
    private Thread thread;

    public KafkaPostCreatedConsumer(PostCreatedEventBus eventBus,
                                    ObjectMapper objectMapper) {
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
    }

    /** 启动后台消费线程 */
    @PostConstruct
    public void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "mini-forum-post-created");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("post-created"));
        thread = new Thread(this::consumeLoop, "kafka-post-created-consumer");
        thread.setDaemon(true);
        thread.start();
        log.info("Kafka 帖子创建事件消费者已启动，bootstrap={}, topic=post-created", bootstrapServers);
    }

    private void consumeLoop() {
        while (running) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        PostCreatedEvent event = objectMapper.readValue(record.value(), PostCreatedEvent.class);
                        // 广播到进程内事件总线：扇出/冷启流量池/搜索索引/内容管道等订阅者异步消费（避免发帖请求被拖慢）
                        eventBus.publish(event);
                    } catch (Exception e) {
                        // 异常必须带堆栈（手册硬性要求）；带 topic/offset 便于定位坏消息
                        log.warn("解析帖子创建事件失败：topic={} offset={}", record.topic(), record.offset(), e);
                    }
                }
            } catch (Exception e) {
                log.warn("Kafka 帖子创建事件消费异常", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(5000); // 等待消费循环退出（poll 最多阻塞 500ms），再关 consumer，保证有序停止
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (consumer != null) {
            consumer.close();
        }
        log.info("Kafka 帖子创建事件消费者已停止");
    }
}
