package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.HotSearchVO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热搜榜服务
 * <p>
 * 基于帖子标签与内容话题聚合热度：对每个标签/话题统计关联帖子在近 {@value #WINDOW_DAYS} 天窗口内的
 * 阅读量×1 + 点赞×2 + 评论×3，并按时间衰减加权（越新权重越高）。
 * 同时将用户搜索词热度（搜索次数 × {@value #SEARCH_KEYWORD_WEIGHT}）并入榜单。
 * 榜单附带趋势（上升/下降/持平/新上榜），通过对比上一统计窗口的排名得出。
 * 无新增帖子相关实体，纯 Service 层聚合计算。
 */
@Service
public class HotSearchService {

    /** 热度统计窗口天数 */
    private static final long WINDOW_DAYS = 30;
    /** 搜索词热度权重：搜索次数 × 该值，使高频搜索能进入热搜榜 */
    private static final long SEARCH_KEYWORD_WEIGHT = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SearchRecordRepository searchRecordRepository;

    public HotSearchService(PostRepository postRepository,
                            CommentRepository commentRepository,
                            SearchRecordRepository searchRecordRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.searchRecordRepository = searchRecordRepository;
    }

    /** 计算热搜榜（标签/话题 + 搜索词，按热度降序，默认 Top10，最多 50，附排名趋势） */
    public List<HotSearchVO> getHotSearches(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        LocalDateTime now = LocalDateTime.now();
        // 当前窗口（近 WINDOW_DAYS 天）与上一窗口（WINDOW_DAYS~2*WINDOW_DAYS 天前）的热度聚合
        Map<String, double[]> cur = aggregateHeat(now.minusDays(WINDOW_DAYS), now);
        Map<String, double[]> prev = aggregateHeat(now.minusDays(WINDOW_DAYS * 2), now.minusDays(WINDOW_DAYS));
        // 合并搜索词热度（搜索词只计当前窗口，趋势按新上榜处理）
        for (SearchRecord r : searchRecordRepository.findTopKeywords(50)) {
            double[] v = cur.computeIfAbsent(r.getKeyword(), k -> new double[]{0, 0});
            v[0] += r.getCount() * SEARCH_KEYWORD_WEIGHT;
            v[1] += r.getCount();
        }
        // 组装 VO 并按热度降序、帖子数降序排序
        List<HotSearchVO> result = new ArrayList<>();
        cur.forEach((tag, v) -> {
            double heat = v[0] > 0 ? v[0] : v[1] * 10;
            result.add(new HotSearchVO(tag, Math.round(heat), (long) v[1], 0));
        });
        result.sort((a, b) -> {
            int cmp = Long.compare(b.getHeat(), a.getHeat());
            return cmp != 0 ? cmp : Long.compare(b.getPostCount(), a.getPostCount());
        });
        // 上一窗口排序列表（用于趋势对比）
        List<String> prevRanked = new ArrayList<>(prev.keySet());
        prevRanked.sort((x, y) -> Double.compare(
                prev.get(y)[0] > 0 ? prev.get(y)[0] : prev.get(y)[1] * 10,
                prev.get(x)[0] > 0 ? prev.get(x)[0] : prev.get(x)[1] * 10));
        List<HotSearchVO> topN = result.size() > safeLimit
                ? new ArrayList<>(result.subList(0, safeLimit))
                : result;
        for (int i = 0; i < topN.size(); i++) {
            HotSearchVO vo = topN.get(i);
            vo.setRank(i + 1);
            int prevRank = prevRanked.indexOf(vo.getKeyword());
            if (prevRank < 0) {
                vo.setTrend(2); // 新上榜
            } else if (prevRank < i) {
                vo.setTrend(1); // 排名上升
            } else if (prevRank > i) {
                vo.setTrend(-1); // 排名下降
            } else {
                vo.setTrend(0); // 持平
            }
        }
        // 热度等级（仿微博爆/沸/热/新标签）
        long maxHeat = topN.isEmpty() ? 0 : topN.get(0).getHeat();
        for (HotSearchVO vo : topN) {
            vo.setLevel(computeLevel(vo, maxHeat));
        }
        return topN;
    }

    /**
     * 热度等级：爆(≥90%榜首热度) / 沸(≥60%) / 热(其余)；新上榜标"新"。
     */
    private String computeLevel(HotSearchVO vo, long maxHeat) {
        if (vo.getTrend() == 2) {
            return "新";
        }
        if (maxHeat <= 0) {
            return "热";
        }
        double ratio = (double) vo.getHeat() / maxHeat;
        if (ratio >= 0.9) {
            return "爆";
        }
        if (ratio >= 0.6) {
            return "沸";
        }
        return "热";
    }

    /**
     * 聚合 [from, to) 时间窗口内的标签/话题热度：每个词累计 [热度, 关联帖子数]。
     * 热度 = (阅读量 + 点赞×2 + 评论×3) × 时间衰减权重（越新越高）。
     */
    private Map<String, double[]> aggregateHeat(LocalDateTime from, LocalDateTime to) {
        Map<String, double[]> agg = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p)) {
                continue;
            }
            LocalDateTime createdAt = p.getCreatedAt();
            if (createdAt == null || createdAt.isBefore(from) || !createdAt.isBefore(to)) {
                continue;
            }
            double ageDays = Duration.between(createdAt, now).toMinutes() / 1440.0;
            double weight = 1.0 / (1.0 + Math.max(0, ageDays));
            long commentCount = commentRepository.countByPostId(p.getId());
            double postHeat = (p.getViewCount() + p.getLikeCount() * 2 + commentCount * 3) * weight;
            if (p.getTags() != null) {
                for (String tag : p.getTags()) {
                    double[] v = agg.computeIfAbsent(tag, k -> new double[]{0, 0});
                    v[0] += postHeat;
                    v[1] += 1;
                }
            }
            if (p.getTopics() != null) {
                for (String topic : p.getTopics()) {
                    double[] v = agg.computeIfAbsent(topic, k -> new double[]{0, 0});
                    v[0] += postHeat;
                    v[1] += 1;
                }
            }
        }
        return agg;
    }

    /** 公开可见：已发布且未删除 */
    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
