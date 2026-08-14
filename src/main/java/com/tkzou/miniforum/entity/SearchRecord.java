package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜索词记录实体
 * <p>
 * 记录用户搜索过的关键词及次数，用于将搜索词热度并入热搜榜。
 * 纯内存存储，可 JSON 持久化。
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
