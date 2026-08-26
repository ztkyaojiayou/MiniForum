package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Like;
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
 * 内存存储的点赞仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
@Profile("!prod")
public class InMemoryLikeRepository implements LikeRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();


    private final Map<Long, Like> storage = new ConcurrentHashMap<>();

    @Override
    public Like save(Like like) {
        if (like.getId() == null) {
            like.setId(idProvider.next("Like"));
        }
        storage.put(like.getId(), like);
        return like;
    }

    @Override
    public Optional<Like> findByPostIdAndUsername(Long postId, String username) {
        return storage.values().stream()
                .filter(l -> l.getPostId().equals(postId) && l.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(l -> l.getPostId().equals(postId))
                .count();
    }

    @Override
    public void delete(Like like) {
        storage.remove(like.getId());
    }

    /** 删除某帖子下的全部点赞（帖子被删除时级联清理） */
    @Override
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    @Override
    public List<Like> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 导出全部点赞记录（用于持久化，按 ID 升序） */
    @Override
    public List<Like> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    @Override
    public void importAll(List<Like> likes) {
        storage.clear();
        if (likes != null) {
            for (Like l : likes) {
                if (l != null && l.getId() != null) {
                    storage.put(l.getId(), l);
                }
            }
        }
    }
}
