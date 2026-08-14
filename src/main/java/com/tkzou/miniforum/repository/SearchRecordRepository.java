package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.SearchRecord;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的搜索词记录仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class SearchRecordRepository {

    private final Map<Long, SearchRecord> storage = new ConcurrentHashMap<>();

    /** 按关键词查询（用于累加搜索次数） */
    public Optional<SearchRecord> findByKeyword(String keyword) {
        return storage.values().stream()
                .filter(r -> r.getKeyword().equals(keyword))
                .findFirst();
    }

    public SearchRecord save(SearchRecord record) {
        if (record.getId() == null) {
            record.setId(SearchRecord.nextId());
        }
        storage.put(record.getId(), record);
        return record;
    }

    /** 搜索次数最多的关键词（按次数降序，最多 limit 个） */
    public List<SearchRecord> findTopKeywords(int limit) {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(SearchRecord::getCount).reversed()
                        .thenComparing(Comparator.comparing(SearchRecord::getLastSearchedAt).reversed()))
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());
    }

    /** 导出全部记录（用于持久化，按 ID 升序） */
    public List<SearchRecord> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(SearchRecord::getId))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
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

    public long count() {
        return storage.size();
    }
}
