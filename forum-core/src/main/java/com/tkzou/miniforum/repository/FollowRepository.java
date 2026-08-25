package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Follow;

import java.util.List;
import java.util.Optional;

/**
 * 关注关系仓库接口
 * <p>
 * 生产级关注关系常用 Redis 承载高频读写（isFollowing 判断、关注/粉丝列表、关注流 fanout）。
 * 默认使用内存实现（{@link InMemoryFollowRepository}，@Profile("!prod")），
 * 生产 profile 使用 Redis 实现（{@link RedisFollowRepository}，@Profile("prod")）。
 */
public interface FollowRepository {

    Follow save(Follow follow);

    Optional<Follow> findByFollowerAndFollowee(Long followerId, Long followeeId);

    boolean exists(Long followerId, Long followeeId);

    void delete(Follow follow);

    /** 我关注的人（按关注时间倒序） */
    List<Follow> findByFollowerId(Long followerId);

    /** 我的粉丝（按关注时间倒序） */
    List<Follow> findByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);

    /** 删除用户相关的全部关注关系（用户被删除时级联清理） */
    void deleteByUserId(Long userId);

    /** 导出全部关注关系（用于持久化，按 ID 升序） */
    List<Follow> exportAll();

    /** 清空并批量导入（用于从持久化数据恢复） */
    void importAll(List<Follow> follows);
}
