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

    public void deleteById(Long id) {
        storage.remove(id);
    }

    /** 按创建时间倒序返回所有帖子（最新在前） */
    public List<Post> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 按作者用户 ID 返回其全部帖子（最新在前） */
    public List<Post> findByAuthorId(Long authorId) {
        return storage.values().stream()
                .filter(p -> authorId.equals(p.getAuthorId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 导出全部帖子（用于持久化，按 ID 升序） */
    public List<Post> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Post> posts) {
        storage.clear();
        if (posts != null) {
            for (Post p : posts) {
                if (p != null && p.getId() != null) {
                    storage.put(p.getId(), p);
                }
            }
        }
    }

    public long count() {
        return storage.size();
    }
}
