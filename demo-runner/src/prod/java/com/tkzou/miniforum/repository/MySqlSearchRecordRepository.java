package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 行级搜索词记录仓库（生产适配，@Profile("prod")）
 * <p>
 * 行级表 search_records，uk_keyword 唯一约束；{@link #incrementKeyword} 用
 * INSERT ... ON DUPLICATE KEY UPDATE count=count+1 —— **原子累加**，多实例下搜索热度不丢不重。
 * 启用：-Pprod + prod profile + spring.datasource.*。
 */
@Repository
@Profile("prod")
public class MySqlSearchRecordRepository implements SearchRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlSearchRecordRepository.class);

    private final JdbcTemplate jdbcTemplate;
    /** ID 生成器（生产 = Snowflake） */
    private final IdProvider idProvider;

    public MySqlSearchRecordRepository(JdbcTemplate jdbcTemplate, IdProvider idProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.idProvider = idProvider;
        log.info("MySQL 搜索词记录仓库初始化（行级表 search_records）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS search_records ("
                + "id BIGINT PRIMARY KEY,"
                + "keyword VARCHAR(100) NOT NULL,"
                + "count BIGINT NOT NULL DEFAULT 0,"
                + "last_searched_at DATETIME,"
                + "UNIQUE KEY uk_keyword (keyword)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Optional<SearchRecord> findByKeyword(String keyword) {
        return jdbcTemplate.query("SELECT id, keyword, count, last_searched_at FROM search_records WHERE keyword=?", this::mapSearchRecord, keyword)
                .stream().findFirst();
    }

    @Override
    public SearchRecord save(SearchRecord record) {
        if (record.getId() == null) {
            record.setId(idProvider.next("SearchRecord"));
        }
        jdbcTemplate.update("INSERT INTO search_records(id,keyword,count,last_searched_at) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE count=VALUES(count),last_searched_at=VALUES(last_searched_at)",
                record.getId(), record.getKeyword(), record.getCount(), record.getLastSearchedAt());
        return record;
    }

    @Override
    public void incrementKeyword(String keyword) {
        // 原子累加：uk_keyword 冲突时 count=count+1（多实例安全，热搜计数不丢）
        jdbcTemplate.update("INSERT INTO search_records(id,keyword,count,last_searched_at) VALUES(?,?,1,?) "
                        + "ON DUPLICATE KEY UPDATE count=count+1,last_searched_at=VALUES(last_searched_at)",
                idProvider.next("SearchRecord"), keyword, LocalDateTime.now());
    }

    @Override
    public List<SearchRecord> findTopKeywords(int limit) {
        return jdbcTemplate.query("SELECT id, keyword, count, last_searched_at FROM search_records ORDER BY count DESC, last_searched_at DESC LIMIT ?",
                this::mapSearchRecord, Math.max(limit, 1));
    }

    @Override
    public List<SearchRecord> exportAll() {
        return jdbcTemplate.query("SELECT id, keyword, count, last_searched_at FROM search_records ORDER BY id", this::mapSearchRecord);
    }

    @Override
    @Transactional
    public void importAll(List<SearchRecord> records) {
        jdbcTemplate.update("DELETE FROM search_records");
        if (records == null) {
            return;
        }
        for (SearchRecord r : records) {
            if (r != null && r.getId() != null) {
                save(r);
            }
        }
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_records", Integer.class);
        return n == null ? 0 : n;
    }

    private SearchRecord mapSearchRecord(ResultSet rs, int rowNum) throws SQLException {
        SearchRecord r = new SearchRecord();
        r.setId(rs.getLong("id"));
        r.setKeyword(rs.getString("keyword"));
        r.setCount(rs.getLong("count"));
        r.setLastSearchedAt(rs.getTimestamp("last_searched_at") == null ? null : rs.getTimestamp("last_searched_at").toLocalDateTime());
        return r;
    }
}
