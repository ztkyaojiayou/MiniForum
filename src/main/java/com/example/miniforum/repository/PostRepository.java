package com.example.miniforum.repository;

import com.example.miniforum.entity.Post;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的帖子仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class PostRepository {

    private final Map<Long, Post> storage = new ConcurrentHashMap<>();

    public Post save(Post post) {
        if (post.getId() == null) {
            post.setId(Post.nextId());
        }
        storage.put(post.getId(), post);
        return post;
    }

    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    /** 按创建时间倒序返回所有帖子（最新在前） */
    public List<Post> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public long count() {
        return storage.size();
    }
}
