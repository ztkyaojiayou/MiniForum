package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Follow;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存关注关系仓库（默认实现，@Profile("!prod")）
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全；生产 profile 由 {@link RedisFollowRepository} 替代。
 */
@Repository
@Profile("!prod")
public class InMemoryFollowRepository implements FollowRepository {

    private final Map<Long, Follow> storage = new ConcurrentHashMap<>();

    @Override
    public Follow save(Follow follow) {
        if (follow.getId() == null) {
            follow.setId(Follow.nextId());
        }
        storage.put(follow.getId(), follow);
        return follow;
    }

    @Override
    public Optional<Follow> findByFollowerAndFollowee(Long followerId, Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId) && f.getFolloweeId().equals(followeeId))
                .findFirst();
    }

    @Override
    public boolean exists(Long followerId, Long followeeId) {
        return findByFollowerAndFollowee(followerId, followeeId).isPresent();
    }

    @Override
    public void delete(Follow follow) {
        storage.remove(follow.getId());
    }

    @Override
    public List<Follow> findByFollowerId(Long followerId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Follow> findByFolloweeId(Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFolloweeId().equals(followeeId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public long countByFollowerId(Long followerId) {
        return storage.values().stream()
                .filter(f -> f.getFollowerId().equals(followerId))
                .count();
    }

    @Override
    public long countByFolloweeId(Long followeeId) {
        return storage.values().stream()
                .filter(f -> f.getFolloweeId().equals(followeeId))
                .count();
    }

    @Override
    public void deleteByUserId(Long userId) {
        storage.entrySet().removeIf(e ->
                e.getValue().getFollowerId().equals(userId) || e.getValue().getFolloweeId().equals(userId));
    }

    @Override
    public List<Follow> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    @Override
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
