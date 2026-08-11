package com.example.miniforum.repository;

import com.example.miniforum.entity.Like;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的点赞仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class LikeRepository {

    private final Map<Long, Like> storage = new ConcurrentHashMap<>();

    public Like save(Like like) {
        if (like.getId() == null) {
            like.setId(Like.nextId());
        }
        storage.put(like.getId(), like);
        return like;
    }

    public Optional<Like> findByPostIdAndUsername(Long postId, String username) {
        return storage.values().stream()
                .filter(l -> l.getPostId().equals(postId) && l.getUsername().equals(username))
                .findFirst();
    }

    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(l -> l.getPostId().equals(postId))
                .count();
    }

    public void delete(Like like) {
        storage.remove(like.getId());
    }

    /** 删除某帖子下的全部点赞（帖子被删除时级联清理） */
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    public List<Like> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 导出全部点赞记录（用于持久化，按 ID 升序） */
    public List<Like> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
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
