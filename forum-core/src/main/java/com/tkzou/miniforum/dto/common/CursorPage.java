package com.tkzou.miniforum.dto.common;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 游标分页结果
 * <p>
 * 用于稳定时间线（关注流等）的分页：{@code nextMaxId} 是本次返回页内最小帖 ID，
 * 下一次请求把它作为 {@code max_id} 参数即可取更早的一页；为 null 表示没有更多。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
