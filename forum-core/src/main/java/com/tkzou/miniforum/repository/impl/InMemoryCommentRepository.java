package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.CommentRepository;

import com.tkzou.miniforum.entity.Comment;
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
 * 内存存储的评论仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
@Profile("!prod")
public class InMemoryCommentRepository implements CommentRepository {
    /** ID 生成器（构造器注入，P2-26）：Spring 按 profile 注入 EntityIdProvider(!prod) / SnowflakeIdProvider(prod)；测试直构走无参默认 */
    private final IdProvider idProvider;

    /** 测试/默认构造：EntityIdProvider（演示默认） */
    public InMemoryCommentRepository() {
        this(new EntityIdProvider());
    }

    /** 构造器注入：避免 @Autowired(required=false) 字段注入掩盖注入失败 */
    @Autowired
    public InMemoryCommentRepository(IdProvider idProvider) {
        this.idProvider = idProvider;
    }


    private final Map<Long, Comment> storage = new ConcurrentHashMap<>();

    @Override
    public Comment save(Comment comment) {
        if (comment.getId() == null) {
            comment.setId(idProvider.next("Comment"));
        }
        storage.put(comment.getId(), comment);
        return comment;
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    /** 按时间正序返回某帖子的全部评论（早的在前） */
    @Override
    public List<Comment> findByPostId(Long postId) {
        return storage.values().stream()
                .filter(c -> c.getPostId().equals(postId))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public long countByPostId(Long postId) {
        return storage.values().stream()
                .filter(c -> c.getPostId().equals(postId))
                .count();
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }

    /** 删除某帖子下的全部评论（帖子被删除时级联清理） */
    @Override
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> e.getValue().getPostId().equals(postId));
    }

    /** 按时间倒序返回全部评论（最新在前） */
    @Override
    public List<Comment> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 评论总数 */
    @Override
    public long count() {
        return storage.size();
    }

    /** 导出全部评论（用于持久化，按 ID 升序） */
    @Override
    public List<Comment> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    @Override
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
