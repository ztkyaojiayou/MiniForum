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

    /** 删除某帖子下的全部评论（帖子被删除时级联清理） */
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    /** 导出全部评论（用于持久化，按 ID 升序） */
    public List<Comment> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Comment> comments) {
        storage.clear();
        if (comments != null) {
            for (Comment c : comments) {
                if (c != null && c.getId() != null) {
                    storage.put(c.getId(), c);
                }
            }
        }
    }
}
