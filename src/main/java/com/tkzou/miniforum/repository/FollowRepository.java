package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Follow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的关注关系仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class FollowRepository {

    private final Map<Long, Follow> storage = new ConcurrentHashMap<>();

    public Follow save(Follow follow) {
        if (follow.getId() == null) {
            follow.setId(Follow.nextId());
        }
        storage.put(follow.getId(), follow);
        return follow;
    }

    public Optional<Follow> findByFollowerAndFollowee(Long followerId, Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId) && f.getFolloweeId().equals(followeeId))
                .findFirst();
    }

    public boolean exists(Long followerId, Long followeeId) {
        return findByFollowerAndFollowee(followerId, followeeId).isPresent();
    }

    public void delete(Follow follow) {
        storage.remove(follow.getId());
    }

    /** 我关注的人（按关注时间倒序） */
    public List<Follow> findByFollowerId(Long followerId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 我的粉丝（按关注时间倒序） */
    public List<Follow> findByFolloweeId(Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFolloweeId().equals(followeeId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 我关注的人数 */
    public long countByFollowerId(Long followerId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId))
                .count();
    }

    /** 我的粉丝数 */
    public long countByFolloweeId(Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFolloweeId().equals(followeeId))
                .count();
    }

    /** 删除用户相关的全部关注关系（用户被删除时级联清理） */
    public void deleteByUserId(Long userId) {
        storage.entrySet().removeIf(e ->
                e.getValue().getFollowerId().equals(userId) || e.getValue().getFolloweeId().equals(userId));
    }

    /** 导出全部关注关系（用于持久化，按 ID 升序） */
    public List<Follow> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Follow> follows) {
        storage.clear();
        if (follows != null) {
            for (Follow f : follows) {
                if (f != null && f.getId() != null) {
                    storage.put(f.getId(), f);
                }
            }
        }
    }
}
