# MiniForum API 接口文档

> MiniForum（迷你微博论坛系统）REST API 文档。
> 基础路径：`http://localhost:8090` ｜ 认证方式：Session Cookie ｜ 响应统一为 `Result<T>` 结构
>
> ```json
> { "code": 0, "message": "success", "data": { } }
> ```
> `code = 0` 表示成功，非 0 表示失败（`message` 为错误信息）。

---

## 目录

- [1. 认证 Auth](#1-认证-auth)
- [2. 用户 User](#2-用户-user)
- [3. 帖子 Post](#3-帖子-post)
- [4. 评论 Comment](#4-评论-comment)
- [5. 关注 Follow](#5-关注-follow)
- [6. 通知 Notification](#6-通知-notification)
- [7. 收藏 Favorite](#7-收藏-favorite)
- [8. 分类 Category](#8-分类-category)
- [9. 标签与话题 Tag / Topic](#9-标签与话题-tag--topic)
- [10. 热搜 HotSearch](#10-热搜-hotsearch)
- [11. 搜索 Search](#11-搜索-search)
- [12. 私信 Message](#12-私信-message)
- [13. 数据看板 Dashboard](#13-数据看板-dashboard)
- [14. 趣味与系统](#14-趣味与系统)

---

## 1. 认证 Auth

### POST /api/auth/register — 注册

```json
{ "username": "alice", "password": "123456", "email": "alice@test.com", "age": 25 }
```

### POST /api/auth/login — 登录

```json
{ "username": "alice", "password": "123456" }
```
成功后写入 Session，返回用户信息。

### POST /api/auth/logout — 退出登录

### GET /api/auth/me — 当前登录用户

---

## 2. 用户 User

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users` | 新增用户（body: UserCreateDTO） |
| GET | `/api/users` | 全部用户列表 |
| GET | `/api/users/{id}` | 查询单个用户 |
| GET | `/api/users/by-username/{username}` | 按用户名查询（@提及跳转用） |
| GET | `/api/users/{id}/profile` | 个人主页聚合：资料 + 粉丝数 + 关注数 + 帖子数 |
| GET | `/api/users/{id}/posts?page=&size=` | 某用户的已发布帖子（分页） |
| PUT | `/api/users/{id}` | 修改用户（body: UserUpdateDTO） |
| PUT | `/api/users/{id}/profile` | 修改资料：昵称/简介/头像/邮箱/年龄（ProfileUpdateDTO，仅本人/管理员） |
| PUT | `/api/users/{id}/password` | 修改密码（ChangePasswordDTO：oldPassword/newPassword，仅本人） |
| DELETE | `/api/users/{id}` | 删除用户 |

---

## 3. 帖子 Post

### POST /api/posts — 发帖

```json
{ "title": "标题", "content": "内容（支持 Markdown 与 #话题#、@用户名）",
  "tags": ["标签1", "标签2"], "category": "科技", "publish": true }
```
- `publish=false` 存为草稿；`category` 为空兜底"其他"；
- 内容中 `#话题#` 自动提取为话题，`@用户名` 自动通知被提及者。

### GET /api/posts — 帖子列表（分页 + 筛选）

| 参数 | 说明 |
|------|------|
| `page` / `size` | 分页（size 1~100，默认 10） |
| `tag` | 按标签筛选 |
| `category` | 按分类筛选 |
| `topic` | 按话题筛选 |
| `status=DRAFT` | 查看自己的草稿 |

### GET /api/posts/my — 我的帖子（可按 status 过滤，分页）

### GET /api/posts/{id} — 帖子详情（阅读量 +1）

### GET /api/posts/search?keyword= — 帖子关键字搜索（标题命中优先）

### GET /api/posts/hot?limit= — 热门帖子（按阅读量降序）

### PUT /api/posts/{id} — 编辑帖子（仅作者/管理员，body 同发帖）

### DELETE /api/posts/{id} — 删除帖子（软删除进回收站）

### POST /api/posts/{id}/restore — 恢复回收站帖子

### GET /api/posts/recycle — 我的回收站（分页）

### POST /api/posts/{id}/like — 点赞

### DELETE /api/posts/{id}/like — 取消点赞

### POST /api/posts/{id}/repost — 转发

```json
{ "content": "转发评语（可空）" }
```
生成转发帖（带原帖摘要），通知原帖作者。

---

## 4. 评论 Comment

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/posts/{postId}/comments` | 评论列表（时间正序） |
| POST | `/api/posts/{postId}/comments` | 发表评论（`{ "content": "..." }`，支持 @用户名） |
| DELETE | `/api/comments/{commentId}` | 删除评论（仅作者/管理员） |

---

## 5. 关注 Follow

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/follows/{userId}` | 关注用户 |
| DELETE | `/api/follows/{userId}` | 取消关注 |
| GET | `/api/follows/status/{userId}` | 是否已关注 |
| GET | `/api/follows/following` | 我的关注列表 |
| GET | `/api/follows/followers` | 我的粉丝列表 |
| GET | `/api/follows/feed?page=&size=` | 关注流（只看关注的人的帖子） |

---

## 6. 通知 Notification

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications` | 我的通知列表（最新在前） |
| GET | `/api/notifications/unread-count` | 未读通知数 |
| PUT | `/api/notifications/{id}/read` | 标记单条已读 |
| PUT | `/api/notifications/read-all` | 全部标记已读 |

通知类型：`LIKE`（点赞）/ `COMMENT`（评论）/ `FOLLOW`（关注）/ `REPOST`（转发）/ `MENTION`（@提及）。

---

## 7. 收藏 Favorite

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/favorites/{postId}` | 收藏帖子 |
| DELETE | `/api/favorites/{postId}` | 取消收藏 |
| GET | `/api/favorites/status/{postId}` | 收藏状态 |
| GET | `/api/favorites/my` | 我的收藏列表 |

---

## 8. 分类 Category

### GET /api/categories — 分类列表（含图标与帖子数）

```json
[ { "name": "科技", "icon": "💻", "count": 5 }, ... ]
```

固定 12 类：科技/数码/游戏/娱乐/体育/财经/汽车/时事/教育/生活/美食/其他。

---

## 9. 标签与话题 Tag / Topic

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tags` | 标签列表（含帖子数，按帖子数降序） |
| GET | `/api/tags/topics` | 话题榜（#话题#，含帖子数） |

---

## 10. 热搜 HotSearch

### GET /api/hot/search?limit=10 — 热搜榜

热度 = 阅读量×1 + 点赞×2 + 评论×3（近 30 天时间衰减），来源 = 标签 ∪ 话题 ∪ 搜索词。

```json
[ { "keyword": "AI", "heat": 3210, "postCount": 12, "rank": 1, "trend": 1 }, ... ]
```
`trend`：`1`=上升，`0`=持平，`-1`=下降，`2`=新上榜。`limit` 最大 50。

---

## 11. 搜索 Search

### GET /api/search?keyword=xxx — 综合搜索

```json
{ "posts": [ PostVO... ], "users": [ { "id": 1, "username": "alice" } ] }
```
- 帖子：标题/内容/标签/话题命中；用户：用户名/昵称命中；
- 搜索词自动计入热搜（次数×50 热度权重）。

---

## 12. 私信 Message

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/messages/conversations` | 会话列表（含对方信息/最后消息/未读数） |
| GET | `/api/messages/conversations/{peerId}` | 打开与某用户的会话（不存在则创建） |
| GET | `/api/messages/conversations/{conversationId}/messages` | 聊天记录（时间正序，同时标记已读） |
| POST | `/api/messages` | 发送私信（`{ "toUsername": "bob", "content": "你好" }`） |
| GET | `/api/messages/unread-count` | 私信未读总数（前端轮询） |

---

## 13. 数据看板 Dashboard

### GET /api/dashboard/stats — 系统统计

```json
{ "userCount": 5, "postCount": 28, "commentCount": 10, "likeCount": 17, "todayPosts": 2, "draftCount": 1 }
```

---

## 14. 趣味与系统

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/quotes/random` | 随机灵感便签 |
| GET | `/api/quotes/count` | 灵感条数 |
| POST | `/api/decide` | 决策转盘（`{ "options": ["a","b"] }` 返回随机一项） |
| GET | `/api/health` | 健康检查（内存/线程等） |
| GET | `/api/health/ping` | 存活探针 |

---

## 页面路由

| 路径 | 说明 |
|------|------|
| `/` | 首页（已登录跳 index.html，未登录跳 login.html） |
| `/category/{key}` | 分类页独立路由（tech/digital/game/... 自动激活分类） |
| `/index.html` | 首页（三栏：左分类 / 中发帖+信息流 / 右热搜） |
| `/login.html` | 登录/注册 |
| `/post.html` | 发帖页 |
| `/detail.html?id=` | 帖子详情 |
| `/my.html` | 个人中心 |
| `/user.html?id= 或 ?username=` | 他人主页 |
| `/notification.html` | 通知中心 |
| `/message.html` | 私信聊天 |
| `/hot.html` | 热搜榜详情页（Top50） |
| `/quote.html` | 灵感便签 |
| `/wheel.html` | 决策转盘 |

---

*文档版本：v1.0　|　最后更新：2026-08-14*
