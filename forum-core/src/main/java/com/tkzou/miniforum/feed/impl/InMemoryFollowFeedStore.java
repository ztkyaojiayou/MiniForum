package com.tkzou.miniforum.feed.impl;
import com.tkzou.miniforum.feed.FollowFeedStore;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.repository.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
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

    private static final Logger log = LoggerFactory.getLogger(InMemoryFollowFeedStore.class);

    /** 大V分流阈值默认值：10 万粉丝，演示数据永不触发（纯预留开关） */
    static final int DEFAULT_BIG_V_THRESHOLD = 100000;

    private final FollowRepository followRepository;

    private final int cap;

    /** 大V分流阈值：粉丝数 ≥ 此值时 shouldSkipFanout 返回 true（走拉） */
    private final int bigVThreshold;

    /** userId → inbox（postId 升序；最新 = 最大 id） */
    private final Map<Long, ConcurrentSkipListSet<Long>> inboxes = new ConcurrentHashMap<>();

    /** 便捷构造（测试/默认）：bigVThreshold 用默认 10 万，永不触发 */
    public InMemoryFollowFeedStore(FollowRepository followRepository, int cap) {
        this(followRepository, cap, DEFAULT_BIG_V_THRESHOLD);
    }

    @Autowired
    public InMemoryFollowFeedStore(FollowRepository followRepository,
                                   @Value("${app.rec.feed.cap:500}") int cap,
                                   @Value("${app.rec.feed.big-v-fan-threshold:100000}") int bigVThreshold) {
        this.followRepository = followRepository;
        this.cap = cap;
        this.bigVThreshold = bigVThreshold;
    }

    @Override
    public void fanout(Long authorId, Long postId) {
        // 大V分流预留：粉丝超阈值跳过扇出（走拉）。
        // ⚠ 激活前必须先实现读侧 pull 合并（outbox + 读者拉取，见 docs §5/§2.5），否则大V新帖不会进粉丝 inbox。
        if (shouldSkipFanout(authorId)) {
            log.warn("跳过扇出：作者 {} 粉丝数超阈值（走拉，pull 路径待实现）", authorId);
            return;
        }
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
    public List<Long> getInbox(Long userId, Long maxId, int maxCount) {
        ConcurrentSkipListSet<Long> inbox = inboxes.get(userId);
        if (inbox == null) {
            return List.of();
        }
        // maxId 为 null 从头取；否则严格小于 maxId（开区间），保证下一页不重复不丢帖
        NavigableSet<Long> window = maxId == null ? inbox : inbox.headSet(maxId, false);
        List<Long> result = new ArrayList<>();
        Iterator<Long> it = window.descendingIterator(); // 最新（最大 id）在前
        while (it.hasNext() && result.size() < maxCount) {
            result.add(it.next());
        }
        return result;
    }

    @Override
    public List<Long> getInboxAfter(Long userId, Long sinceId, int maxCount) {
        ConcurrentSkipListSet<Long> inbox = inboxes.get(userId);
        if (inbox == null || sinceId == null) {
            return List.of();
        }
        // 严格大于 sinceId（开区间），最新在前，用于增量刷新
        NavigableSet<Long> window = inbox.tailSet(sinceId, false);
        List<Long> result = new ArrayList<>();
        Iterator<Long> it = window.descendingIterator();
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

    @Override
    public boolean shouldSkipFanout(Long userId) {
        // 大V分流预留：粉丝数 ≥ 阈值 → 跳过扇出（写放大 O(粉丝数) 爆炸，见 docs §2.5）
        return followRepository.countByFolloweeId(userId) >= bigVThreshold;
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
