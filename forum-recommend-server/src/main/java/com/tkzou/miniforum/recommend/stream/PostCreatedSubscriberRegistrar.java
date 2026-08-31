package com.tkzou.miniforum.recommend.stream;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 帖子创建事件订阅者装配器（统一注册入口）
 * <p>
 * 利用 Spring 的 {@code List<接口>} 自动收集机制，注入容器内全部 {@link PostCreatedSubscriber}
 * 实现并逐个注册到 {@link PostCreatedEventBus}。新增一键下游消费：新建 {@code @Component}
 * 实现 {@code PostCreatedSubscriber} 即自动接入总线，无需改动任何现有代码（开闭原则）。
 * <p>
 * <b>订阅关系全景</b>（一处可查）：{@code FanoutOnPostCreated}（关注流扇出）/
 * {@code SearchIndexUpdater}（搜索索引增量）/ {@code TrafficPoolOnPostCreated}（冷启流量池）。
 * 放置在本模块而非 core：接口在 core、装配逻辑属于业务侧，避免 core 依赖 recommend 实现类。
 */
@Component
public class PostCreatedSubscriberRegistrar {

    public PostCreatedSubscriberRegistrar(PostCreatedEventBus eventBus,
                                          List<PostCreatedSubscriber> subscribers) {
        for (PostCreatedSubscriber subscriber : subscribers) {
            eventBus.subscribe(event -> subscriber.onPostCreated(event));
        }
    }
}