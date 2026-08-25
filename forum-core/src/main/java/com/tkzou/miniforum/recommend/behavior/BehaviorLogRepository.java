package com.tkzou.miniforum.recommend.behavior;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 行为日志仓库（内存存储）
 * <p>
 * 与项目其它仓库一致：ConcurrentHashMap + {@link BehaviorLog#nextId()} 自增 ID，
 * 经 {@link #exportAll()}/{@link #importAll(List)} 接入 DataStore 的 JSON 持久化。
 */
@Repository
public class BehaviorLogRepository {

    private final Map<Long, BehaviorLog> storage = new ConcurrentHashMap<>();

    public BehaviorLog save(BehaviorLog behavior) {
        if (behavior.getId() == null) {
            behavior.setId(BehaviorLog.nextId());
        }
        storage.put(behavior.getId(), behavior);
        return behavior;
    }

    public Optional<BehaviorLog> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<BehaviorLog> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(BehaviorLog::getId))
                .collect(Collectors.toList());
    }

    /** 某用户全部行为（时间升序，供画像聚合与评估） */
    public List<BehaviorLog> findByUserId(Long userId) {
        return storage.values().stream()
                .filter(b -> userId.equals(b.getUserId()))
                .sorted(Comparator.comparing(BehaviorLog::getTimestamp))
                .collect(Collectors.toList());
    }

    /** 某帖子全部行为（供互动统计与相关性） */
    public List<BehaviorLog> findByPostId(Long postId) {
        return storage.values().stream()
                .filter(b -> postId.equals(b.getPostId()))
                .sorted(Comparator.comparing(BehaviorLog::getTimestamp))
                .collect(Collectors.toList());
    }

    /** 某类型全部行为（如全部 CLICK） */
    public List<BehaviorLog> findByType(BehaviorType type) {
        return storage.values().stream()
                .filter(b -> type == b.getType())
                .sorted(Comparator.comparing(BehaviorLog::getTimestamp))
                .collect(Collectors.toList());
    }

    public long count() {
        return storage.size();
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }

    public List<BehaviorLog> exportAll() {
        return findAll();
    }

    /** 从持久化文件整体导入（先清空再写入，ID 由调用方负责复位） */
    public void importAll(List<BehaviorLog> behaviors) {
        storage.clear();
        if (behaviors == null) {
            return;
        }
        for (BehaviorLog b : behaviors) {
            if (b != null && b.getId() != null) {
                storage.put(b.getId(), b);
            }
        }
    }
}
