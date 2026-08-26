package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Like;
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

    Optional<Like> findByPostIdAndUsername(Long postId, String username);

    long countByPostId(Long postId);

    void delete(Like like);

    void deleteByPostId(Long postId);

    List<Like> findAll();

    List<Like> exportAll();

    void importAll(List<Like> likes);
}