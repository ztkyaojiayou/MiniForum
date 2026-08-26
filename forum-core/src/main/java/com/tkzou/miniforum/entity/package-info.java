/**
 * 实体（领域对象）
 * <p>
 * 纯 POJO，与存储结构一一对应，id 由各实体静态 {@code nextId()}（AtomicLong 单调）分配，
 * 持久化恢复时用 {@code resetIdGenerator} 推进到历史最大 id+1。
 *
 * <b>关键实体与约定</b>：
 * <ul>
 *   <li>{@link com.tkzou.miniforum.entity.Post}：帖子。status（PUBLISHED/DRAFT）+ deleted（软删）双标记；
 *       authorId/createdAt 创建后不变（可安全用于索引/排序）；originalPostId/originalAuthorId 记录转发关系；
 *       likeCount/viewCount 是展示用反范式计数（运行期原地改，索引持有引用安全）。</li>
 *   <li>{@link com.tkzou.miniforum.entity.Follow}：关注关系（followerId→followeeId）。</li>
 *   <li>{@link com.tkzou.miniforum.entity.User}：用户（含 nickname/avatar/bio 展示字段）。</li>
 *   <li>Comment / Like / Favorite / Notification / Conversation / Message / SearchRecord：各业务数据。</li>
 * </ul>
 *
 * 与 DTO 的关系：实体是"存储/领域形态"，DTO 是"接口传输形态"（见 dto 包，PostVO 等由
 * {@code PostAssembler} 装配）。
 */
package com.tkzou.miniforum.entity;
