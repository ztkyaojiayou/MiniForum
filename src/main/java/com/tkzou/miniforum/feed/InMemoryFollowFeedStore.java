package com.tkzou.miniforum.feed;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.repository.FollowRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 内存关注流 inbox（默认实现，@Profile("!prod")）
 * <p>
 * 单机演示级：每个用户一个 {@link ConcurrentSkipListSet}&lt;Long&gt;（postId 升序 = 时间序）。
 * 生产 profile 由 {@link RedisFollowFeedStore} 替代。
 */
@Component
@Profile("!prod")
public class InMemoryFollowFeedStore implements FollowFeedStore {

    private final FollowRepository followRepository;

    private final int cap;

    /** userId → inbox（postId 升序；最新 = 最大 id） */
    private final Map<Long, ConcurrentSkipListSet<Long>> inboxes = new ConcurrentHashMap<>();

    public InMemoryFollowFeedStore(FollowRepository followRepository,
                                   @Value("${app.rec.feed.cap:500}") int cap) {
        this.followRepository = followRepository;
        this.cap = cap;
    }

    @Override
    public void fanout(Long authorId, Long postId) {
        List<Follow> followers = followRepository.findByFolloweeId(authorId);
        for (Follow f : followers) {
            Long followerId = f.getFollowerId();
            // 只写给已建流的粉丝：未建流用户的首次读取会用完整关注集合回填，避免建成"半成品流"导致历史帖丢失
            if (!isBuilt(followerId)) {
                continue;
            }
            inboxes.get(followerId).add(postId);
            trim(followerId);
        }
    }

    @Override
    public List<Long> getInbox(Long userId, int maxCount) {
        ConcurrentSkipListSet<Long> inbox = inboxes.get(userId);
        if (inbox == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        Iterator<Long> it = inbox.descendingIterator(); // 最新（最大 id）在前
        while (it.hasNext() && result.size() < maxCount) {
            result.add(it.next());
        }
        return result;
    }

    @Override
    public void onFollow(Long followerId, List<Long> recentPostIds) {
        // 无论是否有历史帖都要建立（可为空）inbox 并标记已建流；否则空流用户每次读取都回退全表扫描
        ConcurrentSkipListSet<Long> inbox = inboxes.computeIfAbsent(followerId, k -> new ConcurrentSkipListSet<>());
        if (recentPostIds != null) {
            inbox.addAll(recentPostIds);
        }
        trim(followerId);
    }

    @Override
    public boolean isBuilt(Long userId) {
        return inboxes.containsKey(userId);
    }

    /** 封顶：超过 cap 时移除最旧的（最小 id） */
    private void trim(Long userId) {
        ConcurrentSkipListSet<Long> inbox = inboxes.get(userId);
        if (inbox == null) {
            return;
        }
        while (inbox.size() > cap) {
            Long oldest = inbox.first();
            if (oldest == null) {
                break;
            }
            inbox.remove(oldest);
        }
    }

    /** 当前跟踪的用户数（测试/监控） */
    public int size() {
        return inboxes.size();
    }
}
