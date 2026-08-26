package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Favorite;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 内存存储的收藏仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class FavoriteRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();


    private final Map<Long, Favorite> storage = new ConcurrentHashMap<>();

    public Favorite save(Favorite favorite) {
        if (favorite.getId() == null) {
            favorite.setId(idProvider.next("Favorite"));
        }
        storage.put(favorite.getId(), favorite);
        return favorite;
    }

    public Optional<Favorite> findByPostIdAndUsername(Long postId, String username) {
        return storage.values().stream()
                .filter(f -> f.getPostId().equals(postId) && f.getUsername().equals(username))
                .findFirst();
    }

    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(f -> f.getPostId().equals(postId))
                .count();
    }

    public void delete(Favorite favorite) {
        storage.remove(favorite.getId());
    }

    /** 删除某帖子下的全部收藏（帖子被彻底删除时级联清理） */
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    /** 某用户收藏的全部帖子 ID（最新收藏在前） */
    public List<Long> findPostIdsByUsername(String username) {
        return storage.values().stream()
                .filter(f -> f.getUsername().equals(username))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(Favorite::getPostId)
                .collect(Collectors.toList());
    }

    public List<Favorite> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 导出全部收藏记录（用于持久化，按 ID 升序） */
    public List<Favorite> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Favorite> favorites) {
        storage.clear();
        if (favorites != null) {
            for (Favorite f : favorites) {
                if (f != null && f.getId() != null) {
                    storage.put(f.getId(), f);
                }
            }
        }
    }
}
