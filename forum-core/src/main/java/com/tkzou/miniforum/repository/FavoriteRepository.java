package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Favorite;
import java.util.List;
import java.util.Optional;

/**
 * 收藏仓库接口
 * <p>
 * 双实现：内存 {@link InMemoryFavoriteRepository}（@Profile("!prod")，演示）/
 * MySQL {@code MySqlFavoriteRepository}（@Profile("prod")，demo-runner/src/prod，行级表 favorites）。
 */
public interface FavoriteRepository {

    Favorite save(Favorite favorite);

    Optional<Favorite> findByPostIdAndUsername(Long postId, String username);

    long countByPostId(Long postId);

    void delete(Favorite favorite);

    void deleteByPostId(Long postId);

    List<Long> findPostIdsByUsername(String username);

    List<Favorite> findAll();

    List<Favorite> exportAll();

    void importAll(List<Favorite> favorites);
}