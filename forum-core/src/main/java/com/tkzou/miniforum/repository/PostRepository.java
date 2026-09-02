package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Post;

import com.tkzou.miniforum.repository.impl.InMemoryPostRepository;
import java.util.List;
import java.util.Optional;

/**
 * 帖子仓库接口
 * <p>
 * 双实现：内存 {@link InMemoryPostRepository}（@Profile("!prod")，演示，含按作者分桶二级索引）/
 * MySQL {@code MySqlPostRepository}（@Profile("prod")，demo-runner/src/prod，行级表 posts，WHERE author_id 索引）。
 */
public interface PostRepository {

    /** 保存（新增或更新，id 为空时分配） */
    Post save(Post post);

    Optional<Post> findById(Long id);

    void deleteById(Long id);

    /** 按创建时间倒序返回所有帖子（最新在前） */
    List<Post> findAll();

    /** 按作者用户 ID 返回其全部帖子（最新在前） */
    List<Post> findByAuthorId(Long authorId);

    /** 导出全部帖子（用于持久化，按 ID 升序） */
    List<Post> exportAll();

    /** 清空并批量导入（用于从持久化数据恢复） */
    void importAll(List<Post> posts);

    /**
     * 原子自增点赞数（并发安全，读-改-写合一），返回新计数；delta 可为负，结果不小于 0。
     * 由存储层保证原子性：内存 {@code ConcurrentHashMap.computeIfPresent} / MySQL {@code UPDATE ... SET like_count=like_count+?}。
     */
    long incrementLikeCount(Long postId, int delta);

    /** 原子自增阅读量（并发安全，读-改-写合一），返回新计数 */
    long incrementViewCount(Long postId, int delta);

    long count();
}
