package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜索词记录实体 —— 【全局 keyword 维度，非用户维度】
 * <p>
 * 记录"某关键词全站被搜索的累计次数"，用于将搜索词热度并入热搜榜。
 * 纯内存存储，可 JSON 持久化。
 *
 * <h3>两个维度的分工（别混淆）</h3>
 * <ul>
 *   <li><b>本表 = keyword 维度（全站聚合）</b>：{@code keyword} 唯一（uk_keyword），{@code count} 是<b>全站所有用户</b>
 *       搜这个词的总次数，<b>不含 userId</b>——回答"大家都在搜什么"（热搜榜）；{@link #incrementKeyword} 用
 *       {@code ON DUPLICATE KEY UPDATE count=count+1} 原子累加，多实例下热搜计数不丢不重；</li>
 *   <li><b>用户维度（某用户搜了什么）</b>：在 {@code BehaviorLog}（{@code BehaviorType.SEARCH}，带 userId）——
 *       供画像/行为分析（SEARCH 权重 0.5），不在本表。</li>
 * </ul>
 * 搜索时两路同时记：{@code searchRecordRepository.incrementKeyword(keyword)}（喂热搜）+ {@code behaviorLogger.log(SEARCH)}（喂画像）。
 */
public class SearchRecord {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 搜索关键词 */
    private String keyword;

    /** 累计搜索次数 */
    private long count;

    /** 最近一次搜索时间 */
    private LocalDateTime lastSearchedAt;

    public SearchRecord() {
    }

    public SearchRecord(String keyword) {
        this.keyword = keyword;
        this.count = 1;
        this.lastSearchedAt = LocalDateTime.now();
    }

    /** 生成下一个自增 ID */
    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    /** 将 ID 生成器推进到指定最小值之后（用于从持久化数据恢复，避免 ID 冲突） */
    public static synchronized void resetIdGenerator(long minId) {
        ID_GENERATOR.set(Math.max(ID_GENERATOR.get(), minId + 1));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public LocalDateTime getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(LocalDateTime lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }
}
