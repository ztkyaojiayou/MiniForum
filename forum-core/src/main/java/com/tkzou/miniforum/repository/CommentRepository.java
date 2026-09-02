package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.repository.impl.InMemoryCommentRepository;
import java.util.List;
import java.util.Optional;

/**
 * 评论仓库接口
 * <p>
 * 双实现：内存 {@link InMemoryCommentRepository}（@Profile("!prod")，演示）/
 * MySQL {@code MySqlCommentRepository}（@Profile("prod")，demo-runner/src/prod，行级表 comments）。
 */
public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(Long id);

    List<Comment> findByPostId(Long postId);

    long countByPostId(Long postId);

    void deleteById(Long id);

    void deleteByPostId(Long postId);

    List<Comment> findAll();

    long count();

    List<Comment> exportAll();

    void importAll(List<Comment> comments);
}