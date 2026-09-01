package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.repository.impl.InMemoryLikeRepository;

import java.util.List;
import java.util.Optional;

/**
 * 点赞仓库接口
 * <p>
 * 双实现：内存 {@link InMemoryLikeRepository}（@Profile("!prod")，演示）/
 * MySQL {@code MySqlLikeRepository}（@Profile("prod")，demo-runner/src/prod，行级表 likes）。
 */
public interface LikeRepository {

    Like save(Like like);

    /**
     * 原子"判重+插入"：同一 (postId, username) 已存在时返回 false（不插入），否则插入并返回 true。
     * 内存实现 {@code putIfAbsent} / MySQL 依赖 uk_like 唯一索引 + {@code DuplicateKeyException}，
     * 把 check-then-act 合成单步，杜绝并发重复点赞。
     */
    boolean trySaveIfAbsent(Like like);

    Optional<Like> findByPostIdAndUsername(Long postId, String username);

    long countByPostId(Long postId);

    void delete(Like like);

    void deleteByPostId(Long postId);

    List<Like> findAll();

    List<Like> exportAll();

    void importAll(List<Like> likes);
}