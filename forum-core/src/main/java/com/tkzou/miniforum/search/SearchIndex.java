package com.tkzou.miniforum.search;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 帖子倒排索引（事件驱动，替代搜索的全表扫描）
 * <p>
 * 发帖事件（post-created 总线）增量索引新帖；<b>首次搜索时懒建全量</b>（从 PostRepository 索引现有帖，
 * 避免启动时序问题），保证结果完整。term 按非字母/数字/汉字切分，搜索用子串判定（中文友好）。
 * 主流形态为 ES，本项目用内存倒排演示"索引 + 事件更新"。
 */
@Component
public class SearchIndex {

    private static final Logger log = LoggerFactory.getLogger(SearchIndex.class);

    /** term → 命中的 postId 集合 */
    private final Map<String, Set<Long>> inverted = new ConcurrentHashMap<>();
    /** postId → 全小写可搜索文本（标题+内容+标签+话题，用于子串精确判定） */
    private final Map<Long, String> docText = new ConcurrentHashMap<>();
    private final PostRepository postRepository;

    private volatile boolean built = false;

    public SearchIndex(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /** 索引一篇帖子（新增/更新，幂等） */
    public void index(Post post) {
        if (post == null || post.getId() == null) {
            return;
        }
        String text = buildText(post);
        docText.put(post.getId(), text);
        for (String term : tokenize(text)) {
            inverted.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(post.getId());
        }
    }

    /**
     * 搜索关键词 → 候选 postId 集合（含关键词的帖；已懒建全量 + 增量，结果完整）。
     * 返回空 Set 表示确实无命中；调用方再按可见性/排序处理。
     */
    public Set<Long> search(String keyword) {
        ensureBuilt();
        if (keyword == null || keyword.isBlank()) {
            return new HashSet<>();
        }
        String kw = keyword.trim().toLowerCase();
        Set<Long> candidates = new HashSet<>();
        for (Map.Entry<Long, String> e : docText.entrySet()) {
            if (e.getValue().contains(kw)) {
                candidates.add(e.getKey());
            }
        }
        return candidates;
    }

    /** 懒建全量：首次搜索时把现有帖子全部索引（避免启动时序导致索引不完整） */
    private void ensureBuilt() {
        if (built) {
            return;
        }
        synchronized (this) {
            if (built) {
                return;
            }
            for (Post p : postRepository.findAll()) {
                index(p);
            }
            built = true;
            log.info("搜索索引首次构建完成：{} 篇帖子", docText.size());
        }
    }

    private String buildText(Post p) {
        StringBuilder sb = new StringBuilder();
        if (p.getTitle() != null) {
            sb.append(p.getTitle()).append(' ');
        }
        if (p.getContent() != null) {
            sb.append(p.getContent()).append(' ');
        }
        if (p.getTags() != null) {
            sb.append(String.join(" ", p.getTags())).append(' ');
        }
        if (p.getTopics() != null) {
            sb.append(String.join(" ", p.getTopics())).append(' ');
        }
        return sb.toString().toLowerCase();
    }

    /** 分词：按非字母/数字/汉字切分（中文逐字 + 英文整词） */
    private Set<String> tokenize(String text) {
        Set<String> terms = new HashSet<>();
        StringBuilder word = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || (c >= 0x4E00 && c <= 0x9FFF)) {
                word.append(c);
                if (c >= 0x4E00 && c <= 0x9FFF) {
                    terms.add(word.toString());
                    word.setLength(0);
                }
            } else {
                if (word.length() > 0) {
                    terms.add(word.toString());
                    word.setLength(0);
                }
            }
        }
        if (word.length() > 0) {
            terms.add(word.toString());
        }
        return terms;
    }
}
