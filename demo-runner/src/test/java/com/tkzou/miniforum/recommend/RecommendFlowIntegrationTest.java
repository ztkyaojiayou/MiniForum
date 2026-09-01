package com.tkzou.miniforum.recommend;

import com.tkzou.miniforum.dto.request.PostCreateDTO;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.RecommendPostVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.behavior.impl.InMemoryBehaviorLogger;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.eval.Metrics;
import com.tkzou.miniforum.recommend.eval.OfflineEvaluator;
import com.tkzou.miniforum.recommend.service.RecommendService;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐系统端到端集成测试（@SpringBootTest，验证真实 Spring 装配）
 * <p>
 * 关闭持久化（不读写 data/*.json），内存跑通：造数 → 多路召回 → 排序 → 重排 →
 * 推荐下发（带理由）→ 曝光日志 → 离线评估。
 */
@SpringBootTest(properties = "app.persistence.enabled=false")
class RecommendFlowIntegrationTest {

    @Autowired
    private RecommendService recommendService;
    @Autowired
    private PostService postService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BehaviorLogRepository behaviorLogRepository;
    @Autowired
    private InMemoryBehaviorLogger behaviorLogger; // 异步打点：断言曝光前需 flush 排空
    @Autowired
    private OfflineEvaluator offlineEvaluator;

    @Test
    void fullFunnel_shouldProduceRecommendationsAndMetrics() {
        // 1. 造用户
        User alice = createUser("alice");
        User bob = createUser("bob");
        User carol = createUser("carol");

        // 2. 造帖子（带话题与类目）
        PostVO p1 = postService.createPost(dto("AI 与大模型", "内容 #大模型# #AI#", "科技"), "alice", alice.getId());
        PostVO p2 = postService.createPost(dto("新能源汽车", "内容 #新能源# #汽车#", "汽车"), "bob", bob.getId());
        PostVO p3 = postService.createPost(dto("咖啡拉花教程", "内容 #咖啡# #生活#", "生活"), "carol", carol.getId());
        PostVO p4 = postService.createPost(dto("Java 并发实践", "内容 #Java# #编程#", "科技"), "alice", alice.getId());

        // 3. 造带时间跨度行为（train/test 切分用）：alice 喜欢科技类，bob 喜欢汽车
        LocalDateTime now = LocalDateTime.now();
        behaviorLogRepository.save(behavior(alice, p1.getId(), BehaviorType.LIKE, now.minusDays(4)));
        behaviorLogRepository.save(behavior(alice, p4.getId(), BehaviorType.LIKE, now.minusDays(3)));
        behaviorLogRepository.save(behavior(bob, p2.getId(), BehaviorType.FAVORITE, now.minusDays(2)));
        behaviorLogRepository.save(behavior(carol, p3.getId(), BehaviorType.COMMENT, now.minusDays(1)));
        behaviorLogRepository.save(behavior(alice, p2.getId(), BehaviorType.CLICK, now.minusHours(1)));

        // 4. 推荐流（完整漏斗）
        RecommendContext ctx = new RecommendContext(alice.getId(), "HOME", LocalDateTime.now(), 10);
        List<RecommendPostVO> feed = recommendService.recommend(ctx, "alice", "rec-v1");

        assertFalse(feed.isEmpty(), "推荐流不应为空");
        assertTrue(feed.size() <= 10, "推荐条数不应超过请求数");
        // 每条都带可解释理由与来源
        assertTrue(feed.stream().allMatch(v -> v.getReason() != null && !v.getReason().isBlank()),
                "每条推荐都应带理由");
        assertTrue(feed.stream().allMatch(v -> v.getSources() != null && !v.getSources().isEmpty()),
                "每条推荐都应带召回路来源");
        // 无重复
        assertEquals(feed.size(), feed.stream().map(RecommendPostVO::getPost).map(PostVO::getId).distinct().count(),
                "推荐流不应有重复帖子");

        // 5. 曝光日志已自动记录（异步打点 → 先 flush 排空，近线语义下断言前同步等待）
        behaviorLogger.flush();
        long exposes = behaviorLogRepository.findAll().stream()
                .filter(b -> b.getType() == BehaviorType.EXPOSE).count();
        assertTrue(exposes >= feed.size(), "推荐下发应自动记录曝光");

        // 6. 离线评估（时间切分 + 指标）
        Metrics metrics = offlineEvaluator.evaluate(0.7, 10, 100);
        assertTrue(metrics.getEvaluatedUsers() > 0, "离线评估应覆盖到有测试交互的用户");
        assertTrue(metrics.getAuc() >= 0 && metrics.getAuc() <= 1);
        assertTrue(metrics.getGauc() >= 0 && metrics.getGauc() <= 1);
        assertTrue(metrics.getRecallAtK() >= 0 && metrics.getRecallAtK() <= 1);
        assertTrue(metrics.getNdcgAtK() >= 0 && metrics.getNdcgAtK() <= 1);
        assertTrue(metrics.getCoverage() > 0 && metrics.getCoverage() <= 1);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("test123");
        user.setNickname(username);
        return userRepository.save(user);
    }

    private PostCreateDTO dto(String title, String content, String category) {
        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setCategory(category);
        dto.setPublish(true);
        return dto;
    }

    private BehaviorLog behavior(User user, Long postId, BehaviorType type, LocalDateTime ts) {
        BehaviorLog b = new BehaviorLog();
        b.setUserId(user.getId());
        b.setPostId(postId);
        b.setType(type);
        b.setTimestamp(ts);
        b.setScene("TEST");
        return b;
    }
}
