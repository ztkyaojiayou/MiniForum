package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * 内存存储的帖子仓库
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全，并维护一个「按作者分桶」的二级索引
 * （authorId → 该作者帖子 SortedSet，createdAt 倒序），使 findByAuthorId 从全表扫描降为 O(K)。
 * 索引在 save / deleteById / importAll 三处同步维护。
 */
@Repository
public class PostRepository {

    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    private final Map<Long, Post> storage = new ConcurrentHashMap<>();

    /**
     * 按作者分桶索引：authorId → 该作者的帖子（createdAt 倒序、id 倒序兜底）。
     * Post 的 authorId / createdAt 在创建后不被修改，因此持引用安全；
     * status / deleted / likeCount / viewCount 的原地变更不影响排序且自动可见。
     */
    private final Map<Long, ConcurrentSkipListSet<Post>> postsByAuthor = new ConcurrentHashMap<>();

    /** createdAt 倒序（null 排最后），id 倒序兜底（id 唯一 → 不同帖比较器恒非 0） */
    private static final Comparator<Post> BY_CREATED_DESC = (a, b) -> {
        LocalDateTime ca = a.getCreatedAt() == null ? LocalDateTime.MIN : a.getCreatedAt();
        LocalDateTime cb = b.getCreatedAt() == null ? LocalDateTime.MIN : b.getCreatedAt();
        int cmp = cb.compareTo(ca);
        return cmp != 0 ? cmp : Long.compare(b.getId(), a.getId());
    };

    public Post save(Post post) {
        if (post.getId() == null) {
            post.setId(idProvider.next("Post"));
        }
        storage.put(post.getId(), post);
        Long authorId = post.getAuthorId();
        if (authorId != null) {
            ConcurrentSkipListSet<Post> bucket =
                    postsByAuthor.computeIfAbsent(authorId, k -> new ConcurrentSkipListSet<>(BY_CREATED_DESC));
            // 先移除同 id 再 add（幂等：覆盖 authorId 变更 / importAll 与 save 交错的重复）
            bucket.removeIf(p -> post.getId().equals(p.getId()));
            bucket.add(post);
        }
        return post;
    }

    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public void deleteById(Long id) {
        Post removed = storage.remove(id);
        if (removed != null && removed.getAuthorId() != null) {
            ConcurrentSkipListSet<Post> bucket = postsByAuthor.get(removed.getAuthorId());
            if (bucket != null) {
                bucket.removeIf(p -> id.equals(p.getId()));
                if (bucket.isEmpty()) {
                    postsByAuthor.remove(removed.getAuthorId());
                }
            }
        }
    }

    /** 按创建时间倒序返回所有帖子（最新在前） */
    public List<Post> findAll() {
        return storage.values().stream()
                .sorted(BY_CREATED_DESC)
                .collect(Collectors.toList());
    }

    /** 按作者用户 ID 返回其全部帖子（最新在前，走二级索引 O(K)） */
    public List<Post> findByAuthorId(Long authorId) {
        ConcurrentSkipListSet<Post> bucket = postsByAuthor.get(authorId);
        if (bucket == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(bucket); // 已按 createdAt 倒序
    }

    /** 导出全部帖子（用于持久化，按 ID 升序） */
    public List<Post> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复），同步重建索引 */
    public void importAll(List<Post> posts) {
        storage.clear();
        postsByAuthor.clear();
        if (posts != null) {
            for (Post p : posts) {
                if (p != null && p.getId() != null) {
                    storage.put(p.getId(), p);
                    Long authorId = p.getAuthorId();
                    if (authorId != null) {
                        postsByAuthor.computeIfAbsent(authorId, k -> new ConcurrentSkipListSet<>(BY_CREATED_DESC))
                                .add(p);
                    }
                }
            }
        }
    }

    public long count() {
        return storage.size();
    }
}
