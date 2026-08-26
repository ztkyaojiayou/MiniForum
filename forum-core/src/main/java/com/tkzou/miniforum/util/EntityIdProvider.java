package com.tkzou.miniforum.util;

import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.entity.Favorite;
import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Message;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 实体 ID 生成器（默认实现，@Profile("!prod")）
 * <p>
 * 按实体名委托各实体静态 nextId()/resetIdGenerator()——<b>演示行为与改造前完全一致</b>
 * （每实体独立 AtomicLong，从 1 起步）。也是各 repository 字段默认值（无 Spring 时兜底）。
 */
@Component
@Profile("!prod")
public class EntityIdProvider implements IdProvider {

    private final Map<String, Supplier<Long>> nextFns = new ConcurrentHashMap<>();
    private final Map<String, LongConsumer> resetFns = new ConcurrentHashMap<>();

    public EntityIdProvider() {
        register("Post", Post::nextId, Post::resetIdGenerator);
        register("User", User::nextId, User::resetIdGenerator);
        register("Comment", Comment::nextId, Comment::resetIdGenerator);
        register("Like", Like::nextId, Like::resetIdGenerator);
        register("Follow", Follow::nextId, Follow::resetIdGenerator);
        register("Favorite", Favorite::nextId, Favorite::resetIdGenerator);
        register("Notification", Notification::nextId, Notification::resetIdGenerator);
        register("Message", Message::nextId, Message::resetIdGenerator);
        register("Conversation", Conversation::nextId, Conversation::resetIdGenerator);
        register("SearchRecord", SearchRecord::nextId, SearchRecord::resetIdGenerator);
        register("BehaviorLog", BehaviorLog::nextId, BehaviorLog::resetIdGenerator);
    }

    private void register(String entity, Supplier<Long> next, LongConsumer reset) {
        nextFns.put(entity, next);
        resetFns.put(entity, reset);
    }

    @Override
    public long next(String entity) {
        Supplier<Long> fn = nextFns.get(entity);
        if (fn == null) {
            throw new IllegalArgumentException("未知实体 ID 生成器：" + entity);
        }
        return fn.get();
    }

    @Override
    public void reset(String entity, long min) {
        LongConsumer fn = resetFns.get(entity);
        if (fn != null) {
            fn.accept(min);
        }
    }
}
