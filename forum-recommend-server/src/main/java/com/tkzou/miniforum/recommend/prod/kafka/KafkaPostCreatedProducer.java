package com.tkzou.miniforum.recommend.prod.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
import com.tkzou.miniforum.recommend.stream.PostCreatedProducer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Kafka 帖子创建事件生产者（生产适配，@Profile("prod") 激活）
 * <p>
 * <b>数据流程</b>：{@link #publish} → Jackson 序列化 {@link PostCreatedEvent} → KafkaProducer 写入
 * topic "post-created" → 下游消费者（搜索索引 / feed 扇出 / 内容管道 / 推荐冷启动）。
 * 与内存实现（InMemoryPostCreatedProducer）实现同一 {@link PostCreatedProducer} 接口，可平滑替换。
 */
@Component
@Profile("prod")
public class KafkaPostCreatedProducer implements PostCreatedProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPostCreatedProducer.class);

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaPostCreatedProducer(
            @Value("${app.rec.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        this.producer = new KafkaProducer<>(props);
        log.info("Kafka 帖子创建事件生产者初始化完成，bootstrap={}", bootstrapServers);
    }

    @Override
    public void publish(PostCreatedEvent event) {
        try {
            producer.send(new ProducerRecord<>("post-created",
                    String.valueOf(event.getPostId()), objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.warn("帖子创建事件写入 Kafka 失败", e);
        }
    }
}
