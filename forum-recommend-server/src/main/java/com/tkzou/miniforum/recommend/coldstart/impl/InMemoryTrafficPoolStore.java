package com.tkzou.miniforum.recommend.coldstart.impl;
import com.tkzou.miniforum.recommend.coldstart.PostState;
import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.coldstart.TrafficPoolStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存流量池状态存储（默认实现，@Profile("!prod")）
 * <p>
 * 原 TrafficPool 的 ConcurrentHashMap&lt;Long, PostState&gt; 内聚到这里；单实例行为与改造前一致。
 */
@Component
@Profile("!prod")
public class InMemoryTrafficPoolStore implements TrafficPoolStore {

    private final Map<Long, PostState> states = new ConcurrentHashMap<>();

    @Override
    public Optional<PostState> get(Long postId) {
        return Optional.ofNullable(states.get(postId));
    }

    @Override
    public void put(Long postId, PostState state) {
        states.put(postId, state);
    }

    @Override
    public boolean putIfAbsent(Long postId, PostState state, long ttlSeconds) {
        return states.putIfAbsent(postId, state) == null;
    }

    @Override
    public void remove(Long postId) {
        states.remove(postId);
    }

    @Override
    public Map<Long, PostState> all() {
        return states;
    }

    @Override
    public int size() {
        return states.size();
    }
}
