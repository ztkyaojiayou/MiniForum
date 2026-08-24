package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.CommentCreateDTO;
import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 模拟活动服务
 * <p>
 * 定时产生少量新帖与互动，让系统像真实社区一样持续"转起来"
 * （新内容进入信息流 → 互动写入行为日志 → 画像/ItemCF/热搜随之演进）。
 * 节奏与数据量均可配置，默认保持克制（每 15 分钟 1~2 帖 + 少量互动）。
 */
@Component
public class SimulatedActivityService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedActivityService.class);

    /** 类目 + 话题池（仿 seed 脚本，保证发帖带 #话题# 与分类） */
    private static final String[][] TOPICS_BY_CATEGORY = {
            {"科技", "大模型", "AI编程", "开源", "智能体", "芯片"},
            {"数码", "手机", "电脑", "智能家居", "耳机", "摄影"},
            {"游戏", "端游", "手游", "电竞", "独立游戏", "怀旧游戏"},
            {"娱乐", "电影", "音乐", "综艺", "明星", "旅行"},
            {"体育", "足球", "篮球", "跑步", "健身", "乒乓球"},
            {"财经", "股票", "基金", "理财", "房价", "消费"},
            {"汽车", "新能源", "油车", "自动驾驶", "改装", "二手车"},
            {"时事", "科技新闻", "社会热点", "国际", "政策", "辟谣"},
            {"教育", "考研", "编程学习", "英语", "育儿", "高考"},
            {"生活", "咖啡", "美食", "家居", "穿搭", "宠物"},
            {"其他", "心情", "随笔", "求助", "分享", "冷知识"}
    };

    /** 帖子内容模板（%s 填充话题） */
    private static final String[] TEMPLATES = {
            "今天聊聊 #%s#，刚看到相关消息，有点想法想记录一下。",
            "#%s# 最近挺火，大家怎么看？",
            "分享一个 #%s# 的小心得，欢迎交流～",
            "关于 #%s# 的一点思考，随手记下。",
            "最近在关注 #%s#，有同好一起聊聊吗？",
            "路过 #%s# 板块，潜水久了冒个泡。",
            "#%s# 的新动态，蹲个后续。"
    };

    /** 偶发的标题池（约 30% 帖子带标题，其余纯动态） */
    private static final String[] TITLES = {
            "随手记", "今日份思考", "记录一下", "碎片分享", "小观察", "闲聊两句"
    };

    private static final String COMMENT_TEXT = "这个话题有意思，路过看看～";

    private final PostService postService;
    private final CommentService commentService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final Random random = new Random();

    @Value("${app.sim.enabled:true}")
    private boolean enabled;

    @Value("${app.sim.posts-per-tick:2}")
    private int postsPerTick;

    @Value("${app.sim.interactions-per-tick:2}")
    private int interactionsPerTick;

    public SimulatedActivityService(PostService postService,
                                    CommentService commentService,
                                    UserRepository userRepository,
                                    PostRepository postRepository) {
        this.postService = postService;
        this.commentService = commentService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    /** 定时模拟一轮活动（默认每 15 分钟，可通过 app.sim.interval-ms 调整） */
    @Scheduled(fixedDelayString = "${app.sim.interval-ms:900000}")
    public void simulate() {
        if (!enabled) {
            return;
        }
        List<User> authors = userRepository.findAll();
        if (authors.isEmpty()) {
            return;
        }
        try {
            int posts = 1 + random.nextInt(Math.max(1, postsPerTick));
            int created = 0;
            for (int i = 0; i < posts; i++) {
                createPost(authors);
                created++;
            }
            int interacted = simulateInteractions(authors, Math.max(0, interactionsPerTick));
            if (created > 0 || interacted > 0) {
                log.info("模拟活动一轮：新建 {} 帖，互动 {} 次", created, interacted);
            }
        } catch (Exception e) {
            log.warn("模拟活动异常：{}", e.getMessage());
        }
    }

    /** 随机生成并发布一条动态 */
    private void createPost(List<User> authors) {
        User author = authors.get(random.nextInt(authors.size()));
        String[] cat = TOPICS_BY_CATEGORY[random.nextInt(TOPICS_BY_CATEGORY.length)];
        String category = cat[0];
        String topic1 = cat[1 + random.nextInt(cat.length - 1)];
        String topic2 = cat[1 + random.nextInt(cat.length - 1)];
        String template = TEMPLATES[random.nextInt(TEMPLATES.length)];
        String content = String.format(template, topic1) + " #" + topic2 + "#";

        PostCreateDTO dto = new PostCreateDTO();
        dto.setTitle(random.nextInt(100) < 30 ? TITLES[random.nextInt(TITLES.length)] : null);
        dto.setContent(content);
        dto.setCategory(category);
        dto.setTags(List.of(topic1));
        dto.setPublish(true);
        postService.createPost(dto, author.getUsername(), author.getId());
    }

    /** 对最近帖子做少量互动（点赞/评论），让行为日志与推荐持续演进 */
    private int simulateInteractions(List<User> authors, int max) {
        List<Post> recent = postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());
        if (recent.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (int i = 0; i < max; i++) {
            Post post = recent.get(random.nextInt(recent.size()));
            User actor = authors.get(random.nextInt(authors.size()));
            if (post.getAuthorId() != null && post.getAuthorId().equals(actor.getId())) {
                continue; // 不互动自己的帖子
            }
            try {
                if (random.nextBoolean()) {
                    postService.like(post.getId(), actor.getUsername(), actor.getId());
                } else {
                    CommentCreateDTO comment = new CommentCreateDTO();
                    comment.setContent(COMMENT_TEXT);
                    commentService.addComment(post.getId(), comment, actor.getUsername(), actor.getId(), null);
                }
                done++;
            } catch (Exception e) {
                // 已点过赞等业务冲突，跳过
            }
        }
        return done;
    }
}
