package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.SearchRecord;

import com.tkzou.miniforum.repository.impl.InMemorySearchRecordRepository;
import java.util.List;
import java.util.Optional;

/**
 * 搜索词记录仓库接口
 * <p>
 * 双实现：{@link InMemorySearchRecordRepository}（!prod 内存）/ MySqlSearchRecordRepository（prod 行级表 search_records，uk_keyword + 原子累加）。
 */
public interface SearchRecordRepository {

    /** 按关键词查询（用于累加搜索次数） */
    Optional<SearchRecord> findByKeyword(String keyword);

    SearchRecord save(SearchRecord record);

    /**
     * 原子累加一次搜索次数（不存在则新建 count=1）。
     * 生产用 INSERT ... ON DUPLICATE KEY UPDATE count=count+1，多实例下热搜计数不丢不重。
     */
    void incrementKeyword(String keyword);

    /** 搜索次数最多的关键词（按次数降序，最多 limit 个） */
    List<SearchRecord> findTopKeywords(int limit);

    /** 导出全部记录（持久化用，按 ID 升序） */
    List<SearchRecord> exportAll();

    /** 清空并批量导入（从持久化恢复） */
    void importAll(List<SearchRecord> records);

    long count();
}
