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

    /** 计算热搜榜（标签/话题 + 搜索词，按热度降序，默认 Top10，最多 50） */
    public List<HotSearchVO> getHotSearches(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(WINDOW_DAYS);
        // 标签/话题 -> [累计热度, 关联帖子数]
        Map<String, double[]> agg = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p)) {
                continue;
            }
            boolean inWindow = p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff);
            double ageDays = p.getCreatedAt() == null
                    ? 0
                    : Duration.between(p.getCreatedAt(), now).toMinutes() / 1440.0;
            double weight = inWindow ? 1.0 / (1.0 + Math.max(0, ageDays)) : 0;
            long commentCount = commentRepository.countByPostId(p.getId());
            double postHeat = (p.getViewCount() + p.getLikeCount() * 2 + commentCount * 3) * weight;
            // 标签
            if (p.getTags() != null) {
                for (String tag : p.getTags()) {
                    double[] v = agg.computeIfAbsent(tag, k -> new double[]{0, 0});
                    v[0] += postHeat;
                    v[1] += 1;
                }
            }
            // 内容话题 #话题#
            if (p.getTopics() != null) {
                for (String topic : p.getTopics()) {
                    double[] v = agg.computeIfAbsent(topic, k -> new double[]{0, 0});
                    v[0] += postHeat;
                    v[1] += 1;
                }
            }
        }
        final List<HotSearchVO> result = new ArrayList<>();
        agg.forEach((tag, v) -> {
            // 无阅读/点赞/评论时用帖子数兜底，避免全 0
            double heat = v[0] > 0 ? v[0] : v[1] * 10;
            result.add(new HotSearchVO(tag, Math.round(heat), (long) v[1], 0));
        });
        // 合并搜索词热度
        for (SearchRecord r : searchRecordRepository.findTopKeywords(50)) {
            result.add(new HotSearchVO(r.getKeyword(), r.getCount() * SEARCH_KEYWORD_WEIGHT, r.getCount(), 0));
        }
        result.sort((a, b) -> {
            int cmp = Long.compare(b.getHeat(), a.getHeat());
            return cmp != 0 ? cmp : Long.compare(b.getPostCount(), a.getPostCount());
        });
        List<HotSearchVO> topN = result.size() > safeLimit
                ? new ArrayList<>(result.subList(0, safeLimit))
                : result;
        for (int i = 0; i < topN.size(); i++) {
            topN.get(i).setRank(i + 1);
        }
        return topN;
    }

    /** 公开可见：已发布且未删除 */
    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
