package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.FavoriteRepository;

import com.tkzou.miniforum.entity.Favorite;
import org.springframework.context.annotation.Profile;
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
@Profile("!prod")
public class InMemoryFavoriteRepository implements FavoriteRepository {
    /** ID 生成器（构造器注入，P2-26）：Spring 按 profile 注入 EntityIdProvider(!prod) / SnowflakeIdProvider(prod)；测试直构走无参默认 */
    private final IdProvider idProvider;

    /** 测试/默认构造：EntityIdProvider（演示默认） */
    public InMemoryFavoriteRepository() {
        this(new EntityIdProvider());
    }

    /** 构造器注入：避免 @Autowired(required=false) 字段注入掩盖注入失败 */
    @Autowired
    public InMemoryFavoriteRepository(IdProvider idProvider) {
        this.idProvider = idProvider;
    }


    private final Map<Long, Favorite> storage = new ConcurrentHashMap<>();

    @Override
    public Favorite save(Favorite favorite) {
        if (favorite.getId() == null) {
            favorite.setId(idProvider.next("Favorite"));
        }
        storage.put(favorite.getId(), favorite);
        return favorite;
    }

    @Override
    public Optional<Favorite> findByPostIdAndUsername(Long postId, String username) {
        return storage.values().stream()
                .filter(f -> f.getPostId().equals(postId) && f.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(f -> f.getPostId().equals(postId))
                .count();
    }

    @Override
    public void delete(Favorite favorite) {
        storage.remove(favorite.getId());
    }

    /** 删除某帖子下的全部收藏（帖子被彻底删除时级联清理） */
    @Override
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    /** 某用户收藏的全部帖子 ID（最新收藏在前） */
    @Override
    public List<Long> findPostIdsByUsername(String username) {
        return storage.values().stream()
                .filter(f -> f.getUsername().equals(username))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(Favorite::getPostId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Favorite> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 导出全部收藏记录（用于持久化，按 ID 升序） */
    @Override
    public List<Favorite> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    @Override
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
