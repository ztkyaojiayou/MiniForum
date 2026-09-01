package com.tkzou.miniforum.recommend.prod;

import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;
import com.tkzou.miniforum.recommend.mq.PostCreatedEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内容管道（生产适配，@Profile("prod")）：审核/embedding 异步化
 * <p>
 * 订阅 {@link PostCreatedEventBus}（发帖事件总线）→ <b>简单审核</b>（敏感词/内容长度校验）→
 * 记录审核结果（日志 + 预留审核状态标记）。embedding 向量化预留接口（真实模型留后续）。
 * <b>演示（@!prod）不启动管道</b>，发帖请求只做快处理（校验/提取），慢处理异步消费。
 */
@Component
@Profile("prod")
public class ContentPipeline {

    private static final Logger log = LoggerFactory.getLogger(ContentPipeline.class);

    /** 演示敏感词（真实生产接词库/模型） */
    private static final List<String> SENSITIVE_WORDS = List.of("赌博", "色情", "赌博", "广告");
    /** 内容长度上限（超长拦截） */
    private static final int MAX_LENGTH = 20000;

    public ContentPipeline(PostCreatedEventBus eventBus) {
        eventBus.subscribe(this::process);
    }

    private void process(PostCreatedEvent event) {
        String text = (event.getTitle() == null ? "" : event.getTitle())
                + " " + (event.getContent() == null ? "" : event.getContent());
        boolean blocked = SENSITIVE_WORDS.stream().anyMatch(text::contains);
        boolean tooLong = text.length() > MAX_LENGTH;
        if (blocked || tooLong) {
            log.warn("【内容审核】帖子 postId={} 被拦截：敏感词={} 超长={}", event.getPostId(), blocked, tooLong);
            // 预留：写回审核状态到帖子（auditStatus 字段，后续）
        } else {
            log.debug("【内容审核】帖子 postId={} 通过", event.getPostId());
            // 预留：embedding 向量化（真实模型后续）
        }
    }
}
