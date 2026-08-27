package com.tkzou.miniforum.recommend.coldstart;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Thompson 后验存储（默认实现，@Profile("!prod")）
 * <p>
 * 原 NewItemPool 的 ConcurrentHashMap&lt;Long, double[]&gt; 内聚到这里（用 {@link AlphaBeta} 值类），
 * 单实例行为与改造前一致。
 */
@Component
@Profile("!prod")
public class InMemoryNewItemPoolStore implements NewItemPoolStore {

    private final Map<Long, AlphaBeta> alphaBeta = new ConcurrentHashMap<>();

    @Override
    public Optional<AlphaBeta> get(Long itemId) {
        return Optional.ofNullable(alphaBeta.get(itemId));
    }

    @Override
    public void put(Long itemId, AlphaBeta ab) {
        alphaBeta.put(itemId, ab);
    }

    @Override
    public boolean putIfAbsent(Long itemId, AlphaBeta ab, long ttlSeconds) {
        return alphaBeta.putIfAbsent(itemId, ab) == null;
    }

    @Override
    public void remove(Long itemId) {
        alphaBeta.remove(itemId);
    }

    @Override
    public boolean containsKey(Long itemId) {
        return alphaBeta.containsKey(itemId);
    }
}
