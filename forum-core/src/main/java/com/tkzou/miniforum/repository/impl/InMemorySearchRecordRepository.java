package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.SearchRecordRepository;

import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存搜索词记录仓库（默认实现，@Profile("!prod")）
 * 使用 ConcurrentHashMap 保证线程安全。
 */
@Repository
@Profile("!prod")
public class InMemorySearchRecordRepository implements SearchRecordRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    private final Map<Long, SearchRecord> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<SearchRecord> findByKeyword(String keyword) {
        return storage.values().stream()
                .filter(r -> r.getKeyword().equals(keyword))
                .findFirst();
    }

    @Override
    public SearchRecord save(SearchRecord record) {
        if (record.getId() == null) {
            record.setId(idProvider.next("SearchRecord"));
        }
        storage.put(record.getId(), record);
        return record;
    }

    @Override
    public void incrementKeyword(String keyword) {
        SearchRecord record = findByKeyword(keyword).orElseGet(() -> new SearchRecord(keyword));
        if (record.getId() == null) {
            save(record);
        } else {
            record.setCount(record.getCount() + 1);
            record.setLastSearchedAt(java.time.LocalDateTime.now());
            save(record);
        }
    }

    @Override
    public List<SearchRecord> findTopKeywords(int limit) {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(SearchRecord::getCount).reversed()
                        .thenComparing(Comparator.comparing(SearchRecord::getLastSearchedAt).reversed()))
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchRecord> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(SearchRecord::getId))
                .collect(Collectors.toList());
    }

    @Override
    public void importAll(List<SearchRecord> records) {
        storage.clear();
        if (records != null) {
            for (SearchRecord r : records) {
                if (r != null && r.getId() != null) {
                    storage.put(r.getId(), r);
                }
            }
        }
    }

    @Override
    public long count() {
        return storage.size();
    }
}
