package com.example.miniforum.repository;

import com.example.miniforum.entity.Comment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的评论仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class CommentRepository {

    private final Map<Long, Comment> storage = new ConcurrentHashMap<>();

    public Comment save(Comment comment) {
        if (comment.getId() == null) {
            comment.setId(Comment.nextId());
        }
        storage.put(comment.getId(), comment);
        return comment;
    }

    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    /** 按时间正序返回某帖子的全部评论（早的在前） */
    public List<Comment> findByPostId(Long postId) {
        return storage.values().stream()
                .filter(c -> c.getPostId().equals(postId))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(c -> c.getPostId().equals(postId))
                .count();
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }
}
