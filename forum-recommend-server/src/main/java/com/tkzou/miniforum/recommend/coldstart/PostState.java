package com.tkzou.miniforum.recommend.coldstart;

import java.time.LocalDateTime;

/**
 * 流量池单帖状态（可序列化值类，供 TrafficPoolStore 的 InMemory/Redis 实现共享）
 * <p>
 * 由 TrafficPool 读-改-写：onBehavior 更新 exposures/successes 并判晋级；stoppedAt 用于 7 天清理。
 * 抽成 public POJO 而非 TrafficPool 内部类，是为了能被 Jackson 序列化到 Redis（jsr310 由 starter-web 提供）。
 */
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

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getExposures() {
        return exposures;
    }

    public void setExposures(int exposures) {
        this.exposures = exposures;
    }

    public int getSuccesses() {
        return successes;
    }

    public void setSuccesses(int successes) {
        this.successes = successes;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
