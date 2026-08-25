package com.tkzou.miniforum.dto;

import java.util.List;

/**
 * 游标分页结果
 * <p>
 * 用于稳定时间线（关注流等）的分页：{@code nextMaxId} 是本次返回页内最小帖 ID，
 * 下一次请求把它作为 {@code max_id} 参数即可取更早的一页；为 null 表示没有更多。
 */
public class CursorPage<T> {

    /** 当前页数据（最新在前） */
    private List<T> records;

    /** 下一页游标（本次返回页内最小 postId）；null 表示没有更多 */
    private Long nextMaxId;

    /** 是否还有更早的帖子 */
    private boolean hasMore;

    public CursorPage() {
    }

    public CursorPage(List<T> records, Long nextMaxId, boolean hasMore) {
        this.records = records;
        this.nextMaxId = nextMaxId;
        this.hasMore = hasMore;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getNextMaxId() {
        return nextMaxId;
    }

    public void setNextMaxId(Long nextMaxId) {
        this.nextMaxId = nextMaxId;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
