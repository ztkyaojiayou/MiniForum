package com.tkzou.miniforum.feed.impl;

import com.tkzou.miniforum.feed.FollowFeedStore;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * 内存关注流 inbox（默认实现，@Profile("!prod")）
 * <p>
 * 单机演示级：每个用户一个 {@link ConcurrentSkipListSet}&lt;Long&gt;（postId 升序 = 时间序）。
 * 大 V 分流：全局 {@code bigVs} 集合（粉丝数 ≥ 阈值），懒扫描存量关注关系一次初始化 + {@link #refreshBigV} 事件驱动增量维护；
 * 大 V 的 outbox 天然在 Post 表（作者自己的时间线），读时直接查，无需额外结构。
 * 生产 profile 由 {@link RedisFollowFeedStore} 替代。
 */
@Component
@Profile("!prod")
public class InMemoryFollowFeedStore implements FollowFeedStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFollowFeedStore.class);

    /** 大V分流阈值默认值：10 万粉丝，演示数据永不触发（纯预留开关） */
    static final int DEFAULT_BIG_V_THRESHOLD = 100000;

    private final FollowRepository followRepository;
    private final PostRepository postRepository;

    private final int cap;

    /** 大V分流阈值：粉丝数 ≥ 此值时 isBigV 返回 true（走拉） */
    private final int bigVThreshold;

    /** userId → inbox（postId 升序；最新 = 最大 id） */
    private final Map<Long, ConcurrentSkipListSet<Long>> inboxes = new ConcurrentHashMap<>();

    /** 全局大V集合（成员 = 粉丝数 ≥ 阈值的作者 id）；懒扫描一次 + 事件驱动增量维护 */
    private final Set<Long> bigVs = ConcurrentHashMap.newKeySet();

    /** 大V集合是否已从存量关注关系初始化 */
    private volatile boolean bigVsScanned = false;

    public InMemoryFollowFeedStore(FollowRepository followRepository, PostRepository postRepository, int cap) {
        this(followRepository, postRepository, cap, DEFAULT_BIG_V_THRESHOLD);
    }

    @Autowired
    public InMemoryFollowFeedStore(FollowRepository followRepository,
                                   PostRepository postRepository,
                                   @Value("${app.rec.feed.cap:500}") int cap,
                                   @Value("${app.rec.feed.big-v-fan-threshold:100000}") int bigVThreshold) {
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.cap = cap;
        this.bigVThreshold = bigVThreshold;
    }

    @Override
    public void fanout(Long authorId, Long postId) {
        // 大V分流：粉丝超阈值跳过扇出（走拉，新帖只写自己的 outbox）
        if (isBigV(authorId)) {
            log.warn("跳过扇出：作者 {} 粉丝数超阈值（走拉，新帖进自己的 outbox）", authorId);
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
    public boolean isBigV(Long authorId) {
        ensureBigVsScanned();
        return bigVs.contains(authorId);
    }

    @Override
    public Set<Long> bigVIds() {
        ensureBigVsScanned();
        return new HashSet<>(bigVs);
    }

    @Override
    public void refreshBigV(Long authorId) {
        // 事件驱动：粉丝数只在关注/取关/删用户时变化，调用方改动关系边后对本作者重数一次
        if (followRepository.countByFolloweeId(authorId) >= bigVThreshold) {
            bigVs.add(authorId);
        } else {
            bigVs.remove(authorId);
        }
    }

    @Override
    public List<Long> getAuthorTimeline(Long authorId, Long maxId, int maxCount) {
        // outbox 天然在 Post 表（作者自己的时间线），无需额外结构；读时过滤可见 + 按 maxId 截断
        return postRepository.findByAuthorId(authorId).stream()
                .filter(p -> maxId == null || p.getId() < maxId)
                .filter(this::isVisiblePost)
                .sorted(Comparator.comparing(Post::getId).reversed())
                .limit(maxCount)
                .map(Post::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void writeOutbox(Long authorId, Long postId) {
        // no-op：内存实现读时直接查 Post 表（发帖已落库），无需独立 outbox
    }

    /** 懒扫描：首次判定大V时用存量关注关系初始化全局集合（一次性）；之后靠 refreshBigV 增量维护 */
    private void ensureBigVsScanned() {
        if (bigVsScanned) {
            return;
        }
        synchronized (this) {
            if (bigVsScanned) {
                return;
            }
            Map<Long, Long> counts = new HashMap<>();
            for (Follow f : followRepository.exportAll()) {
                counts.merge(f.getFolloweeId(), 1L, Long::sum);
            }
            counts.forEach((authorId, cnt) -> {
                if (cnt >= bigVThreshold) {
                    bigVs.add(authorId);
                }
            });
            bigVsScanned = true;
            if (!bigVs.isEmpty()) {
                log.info("大V集合初始化：{} 位作者粉丝数 ≥ 阈值 {}（走拉）", bigVs.size(), bigVThreshold);
            }
        }
    }

    /** 公开可见：已发布且未删除 */
    private boolean isVisiblePost(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
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
