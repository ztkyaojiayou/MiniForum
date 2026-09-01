/**
 * 数据传输对象（按角色分包子包）
 * <p>
 * <ul>
 *   <li>{@code request/}：请求入参（*DTO + TrackRequest，承载接口入参 + JSR-303 校验注解）；</li>
 *   <li>{@code response/}：响应出参（*VO + CategoryVO/TagVO，承载返回给前端的数据形态）；</li>
 *   <li>{@code common/}：通用包装（PageResult offset 分页 / CursorPage 游标分页）。</li>
 * </ul>
 *
 * <b>关键装配件</b>：
 * {@link com.tkzou.miniforum.dto.PostAssembler} 把 {@code Post} 装配为 {@code PostVO}（点赞/收藏/评论/转发数、
 * 分类、转发泡），并从原 PostService 抽出放入共享域——业务侧（admin）与推荐侧（recommend）共用同一装配逻辑，
 * <b>这是消除 recommend→admin 依赖环的关键一步</b>。
 */
package com.tkzou.miniforum.dto;
