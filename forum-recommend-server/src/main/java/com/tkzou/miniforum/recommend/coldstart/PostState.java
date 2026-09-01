package com.tkzou.miniforum.recommend.coldstart;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 流量池单帖状态（可序列化值类，供 TrafficPoolStore 的 InMemory/Redis 实现共享）
 * <p>
 * 由 TrafficPool 读-改-写：onBehavior 更新 exposures/successes 并判晋级；stoppedAt 用于 7 天清理。
 * 抽成 public POJO 而非 TrafficPool 内部类，是为了能被 Jackson 序列化到 Redis（jsr310 由 starter-web 提供）。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class PostState {

    /** 当前档位下标（volatile：tierBonus 并发读，晋级写入要可见） */
    private volatile int tier;
    /** 当前档位内曝光数（volatile：读-改-写在 TrafficPool 持锁串行化） */
    private volatile int exposures;
    /** 当前档位内深度互动数（volatile，同上） */
    private volatile int successes;
    /** 是否停止探索（未达标）（volatile：请求线程 tierBonus 读） */
    private volatile boolean stopped;
    /** 停止时间（用于清理） */
    private volatile LocalDateTime stoppedAt;

    public PostState() {
    }

}
