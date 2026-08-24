# -*- coding: utf-8 -*-
"""
为推荐系统造演示数据：30 用户 / 150 帖 / 点赞·收藏·评论·转发·关注·搜索
用法：先启动服务（mvn spring-boot:run），再运行本脚本：
    python scripts/seed_recsys_data.py
数据通过 HTTP API 写入，会同步生成推荐所需的行为日志（behavior-log.json）。
"""
import json
import http.client
import random
from urllib.parse import quote

HOST = "localhost"
PORT = 8090
# 统一密码：与 admin 一致
PASSWORD = "admin123"

CATEGORIES = ["科技", "数码", "游戏", "娱乐", "体育", "财经", "汽车", "时事", "教育", "生活", "其他"]

# 每个类目下的话题池（发帖时 #话题# 自动提取，话题=微博兴趣载体）
TOPICS = {
    "科技": ["大模型", "AI编程", "开源", "智能体", "芯片"],
    "数码": ["手机", "电脑", "智能家居", "耳机", "摄影"],
    "游戏": ["端游", "手游", "电竞", "独立游戏", "怀旧游戏"],
    "娱乐": ["电影", "音乐", "综艺", "明星", "旅行"],
    "体育": ["足球", "篮球", "跑步", "健身", "乒乓球"],
    "财经": ["股票", "基金", "理财", "房价", "消费"],
    "汽车": ["新能源", "油车", "自动驾驶", "改装", "二手车"],
    "时事": ["科技新闻", "社会热点", "国际", "政策", "辟谣"],
    "教育": ["考研", "编程学习", "英语", "育儿", "高考"],
    "生活": ["咖啡", "美食", "家居", "穿搭", "宠物"],
    "其他": ["心情", "随笔", "求助", "分享", "冷知识"],
}

TITLES = {
    "科技": ["AI 大模型这一年", "我用大模型做副业", "开源社区的春天", "智能体入门指南", "芯片行业的冷思考"],
    "数码": ["换了新手机", "桌面改造分享", "降噪耳机横评", "智能家居踩坑", "摄影入门三个月"],
    "游戏": ["最近在玩什么", "电竞决赛观后感", "独立游戏推荐", "老游戏重玩记", "手游氪金反思"],
    "娱乐": ["周末看了部电影", "喜欢的歌手发歌了", "综艺观后感", "旅行碎片记录", "追星日常"],
    "体育": ["昨晚那场比赛", "跑步第 100 天", "健身三个月变化", "篮球战术分析", "坚持运动的理由"],
    "财经": ["年轻人的第一只基金", "存钱计划", "买房还是租房", "理性消费记录", "工资到账怎么分配"],
    "汽车": ["新能源驾驶体验", "第一次自驾游", "自动驾驶实测", "改装清单分享", "二手车避坑指南"],
    "时事": ["今天的热点", "政策解读", "科技圈大新闻", "社会话题讨论", "信息辟谣"],
    "教育": ["考研上岸经验", "自学编程路线", "英语学习打卡", "育儿心得", "高考志愿参考"],
    "生活": ["手冲咖啡入门", "今天做了什么菜", "出租屋改造", "通勤穿搭", "我家猫又拆家了"],
    "其他": ["今日心情", "随便写写", "求推荐", "分享一件小事", "冷知识一则"],
}

USERS = 30
POSTS_PER_USER = 5
INTERACTIONS_PER_USER = 8
FOLLOWS_PER_USER = 4


def call(method, path, body=None, cookie=None):
    conn = http.client.HTTPConnection(HOST, PORT)
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    conn.request(method, path, body=json.dumps(body) if body is not None else None, headers=headers)
    r = conn.getresponse()
    txt = r.read().decode("utf-8")
    conn.close()
    return r.status, txt


def login_raw(username, password, body=None):
    """登录并返回 session cookie（失败返回 None）"""
    conn = http.client.HTTPConnection(HOST, PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps(body or {"username": username, "password": password}),
                 headers={"Content-Type": "application/json"})
    r = conn.getresponse()
    sc = None
    if r.getheader("Set-Cookie"):
        sc = r.getheader("Set-Cookie").split(";")[0]
    r.read()
    conn.close()
    return sc


def create_and_login(username, admin_cookie):
    """用 admin 会话创建用户（已存在则忽略），再登录拿该用户 cookie"""
    status, _ = call("POST", "/api/users",
                     {"username": username, "email": username + "@t.com",
                      "password": PASSWORD, "age": 20 + random.randint(0, 25)},
                     admin_cookie)
    return login_raw(username, PASSWORD)


def create_post(cookie, category, topics, author):
    title = random.choice(TITLES[category])
    content = (title + "：今天聊聊 #" + random.choice(TOPICS[category]) + "# "
               + "和 #" + random.choice(TOPICS[category]) + "#。"
               + "这是来自 " + author + " 的动态，欢迎交流。")
    status, txt = call("POST", "/api/posts",
                       {"title": title, "content": content, "category": category, "publish": True},
                       cookie)
    if 200 <= status < 300:
        try:
            return json.loads(txt)["data"]["id"]
        except Exception:
            return None
    return None


def main():
    random.seed(42)
    admin_cookie = login_raw("admin", "admin123")
    if not admin_cookie:
        print("!! 无法以 admin 登录（默认账号 admin/admin123），请先启动服务并确保默认账号存在")
        return
    users = []
    for i in range(1, USERS + 1):
        u = "user%02d" % i
        cookie = create_and_login(u, admin_cookie)
        if cookie:
            users.append((u, cookie))
    print("已就绪用户：%d" % len(users))

    # 发帖：每个用户在自己偏好的类目发若干帖
    all_posts = []
    for u, cookie in users:
        fav_cat = CATEGORIES[users.index((u, cookie)) % len(CATEGORIES)]
        for _ in range(POSTS_PER_USER):
            cat = fav_cat if random.random() < 0.6 else random.choice(CATEGORIES)
            pid = create_post(cookie, cat, TOPICS[cat], u)
            if pid:
                all_posts.append((u, pid, cat))
    print("已发布帖子：%d" % len(all_posts))

    # 互动：点赞/收藏/评论/转发（偏好与发帖类目相关的帖子）
    ok = 0
    for u, cookie in users:
        for _ in range(INTERACTIONS_PER_USER):
            if not all_posts:
                break
            author, pid, cat = random.choice(all_posts)
            if author == u:
                continue
            action = random.choice(["like", "favorite", "comment", "repost"])
            if action == "like":
                status, _ = call("POST", "/api/posts/%d/like" % pid, cookie=cookie)
            elif action == "favorite":
                status, _ = call("POST", "/api/favorites/%d" % pid, cookie=cookie)
            elif action == "comment":
                status, _ = call("POST", "/api/posts/%d/comments" % pid,
                                 {"content": "写得不错，学习了！"}, cookie)
            elif action == "repost":
                status, _ = call("POST", "/api/posts/%d/repost" % pid,
                                 {"comment": "转发学习"}, cookie)
            if 200 <= status < 300:
                ok += 1
    print("互动成功：%d 项" % ok)

    # 关注：每个用户关注若干随机用户（先取一次用户名→id 映射）
    fok = 0
    user_ids = {}
    status, txt = call("GET", "/api/users", cookie=admin_cookie)
    if status == 200:
        for x in json.loads(txt)["data"]:
            user_ids[x["username"]] = x["id"]
    for u, cookie in users:
        targets = random.sample([t for t in users if t[0] != u], min(FOLLOWS_PER_USER, len(users) - 1))
        for tu, _ in targets:
            tid = user_ids.get(tu)
            if tid:
                s2, _ = call("POST", "/api/follows/%d" % tid, cookie=cookie)
                if 200 <= s2 < 300:
                    fok += 1
    print("关注成功：%d 项" % fok)

    # 搜索：每用户搜几个词（生成搜索行为与热搜）
    sok = 0
    for u, cookie in users:
        for _ in range(3):
            kw = random.choice([t for v in TOPICS.values() for t in v])
            status, _ = call("GET", "/api/search?keyword=" + quote(kw), cookie=cookie)
            if 200 <= status < 300:
                sok += 1
    print("搜索成功：%d 项" % sok)

    print("\n造数完成：%d 用户 / %d 帖子 / 交互+关注+搜索均已写入行为日志" % (len(users), len(all_posts)))
    print("现在可登录任意 user01~user%02d（密码 %s）体验推荐流" % (USERS, PASSWORD))


if __name__ == "__main__":
    main()
