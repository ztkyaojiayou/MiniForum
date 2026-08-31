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

    /** 当前档位下标 */
    private int tier;
    /** 当前档位内曝光数 */
    private int exposures;
    /** 当前档位内深度互动数 */
    private int successes;
    /** 是否停止探索（未达标） */
    private boolean stopped;
    /** 停止时间（用于清理） */
    private LocalDateTime stoppedAt;

    public PostState() {
    }

}
