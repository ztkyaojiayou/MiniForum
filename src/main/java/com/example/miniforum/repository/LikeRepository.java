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

    public List<Like> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }
}
