package com.tkzou.miniforum.recommend.behavior;

import com.tkzou.miniforum.recommend.stream.BehaviorEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 内存行为采集器（默认实现）
 * <p>
 * <b>数据流程</b>：{@link #log(...)} 只把行为投入<b>异步队列</b>（微秒级返回，不阻塞请求线程），
 * 由后台单线程按序消费：①存 {@code BehaviorLogRepository}（JSON 落盘，画像/评估的事实源）；
 * ②发布到 {@code BehaviorEventQueue}（模拟 Kafka），供近线消费者（实时特征/冷启动反馈/热搜/赛马）处理。
 * <p>
 * <b>为什么异步</b>：曝光/点击是最频繁的写操作，同步落库+同步广播会随 QPS 线性吃掉请求线程。
 * 对齐生产形态——prod 下本就走 Kafka 异步，这里把内存版也统一为"近线"语义（行为最终一致）。
 * 队列满时丢弃并告警（丢打点优于拖垮请求）。
 * <p>
 * 测试/优雅停机：{@link #flush()} 同步排空已入队的打点（近线语义下断言前先 flush，保证确定性）。
 * 生产形态见 prod.kafka.KafkaBehaviorLogger（@Profile("prod")，激活 prod 时本实现不加载）。
 */
@Component
@Profile("!prod")
public class InMemoryBehaviorLogger implements BehaviorLogger {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBehaviorLogger.class);

    /** 异步队列容量（有界防 OOM；满时走拒绝策略丢弃并告警） */
    private static final int QUEUE_CAPACITY = 100_000;

    private final BehaviorLogRepository repository;
    private final BehaviorEventQueue eventQueue;

    /**
     * 异步打点执行器：单线程保证 FIFO 顺序（行为近线处理），daemon 线程不阻塞 JVM 退出。
     * 核心线程 1 / 队列有界；拒绝策略只告警不抛异常（log() 永不因队列满而失败）。
     */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "behavior-log-async");
                t.setDaemon(true);
                return t;
            },
            (r, e) -> log.warn("行为打点队列已满，丢弃一条（近线信号允许最终一致）"));

    public InMemoryBehaviorLogger(BehaviorLogRepository repository,
                                  BehaviorEventQueue eventQueue) {
        this.repository = repository;
        this.eventQueue = eventQueue;
    }

    @Override
    public void log(Long userId, Long postId, BehaviorType type, String scene, String expId) {
        log(userId, postId, type, scene, expId, null);
    }

    @Override
    public void log(Long userId, Long postId, BehaviorType type, String scene, String expId, Double durationSec) {
        if (userId == null) {
            return;
        }
        BehaviorLog behavior = new BehaviorLog();
        behavior.setUserId(userId);
        behavior.setPostId(postId);
        behavior.setType(type);
        behavior.setTimestamp(LocalDateTime.now());
        behavior.setDurationSec(durationSec);
        behavior.setScene(scene == null || scene.isBlank() ? "DEFAULT" : scene);
        behavior.setExpId(expId);
        // 只入队（非阻塞）；落库 + 广播交给后台线程，把最频繁的写从请求路径摘掉
        executor.execute(() -> {
            repository.save(behavior);
            eventQueue.publish(behavior);
        });
    }

    /**
     * 同步排空：等已入队的打点全部落库 + 广播后返回（近线语义下测试断言前/优雅停机时调用）。
     * 原理：单线程 FIFO，屏障任务（latch.countDown）必在前序所有打点之后执行。
     */
    public void flush() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            executor.execute(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("行为打点 flush 被中断：{}", e.getMessage());
        } catch (Exception e) {
            log.warn("行为打点 flush 异常：{}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
