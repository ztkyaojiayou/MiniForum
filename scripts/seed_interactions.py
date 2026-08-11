# -*- coding: utf-8 -*-
"""
为演示帖子补充点赞与评论，让社区更有生气
"""
import json
import http.client

BASE_HOST = "localhost"
BASE_PORT = 8090

# 用户名 -> 密码
USERS = {
    "admin": "admin123",
    "aifan": "123456",
    "techgeek": "123456",
    "carboss": "123456",
    "newsdaily": "123456",
}


def login(username):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps({"username": username, "password": USERS[username]}),
                 headers={"Content-Type": "application/json"})
    r = conn.getresponse()
    cookie = None
    sc = r.getheader("Set-Cookie")
    if sc:
        cookie = sc.split(";")[0]
    r.read()
    conn.close()
    return cookie


def call(method, path, body=None, cookie=None):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    conn.request(method, path, body=json.dumps(body) if body is not None else None, headers=headers)
    r = conn.getresponse()
    txt = r.read().decode("utf-8")
    conn.close()
    return r.status, txt


# 计划：谁 对 哪些帖子 做什么
ACTIONS = [
    # 点赞
    ("aifan", "like", 4),
    ("aifan", "like", 7),
    ("aifan", "like", 10),
    ("techgeek", "like", 1),
    ("techgeek", "like", 10),
    ("techgeek", "like", 13),
    ("carboss", "like", 1),
    ("carboss", "like", 3),
    ("carboss", "like", 13),
    ("newsdaily", "like", 1),
    ("newsdaily", "like", 2),
    ("newsdaily", "like", 8),
    ("newsdaily", "like", 13),
    ("admin", "like", 2),
    ("admin", "like", 7),
    ("admin", "like", 8),
    ("admin", "like", 10),
    # 评论
    ("aifan", "comment", 4, "文章写得不错！芯片确实是长周期赛道，持续投入才是王道。"),
    ("aifan", "comment", 10, "这周确实信息量很大，期待下期。"),
    ("techgeek", "comment", 1, "智能体这块深有同感，最近接了个客服智能体的项目，效果比想象中好。"),
    ("techgeek", "comment", 7, "新能源渗透率 55% 太夸张了，不过身边确实越来越多人在换电车。"),
    ("carboss", "comment", 1, "多模态能力确实是刚需，希望国内模型再卷一卷，把价格打下来。"),
    ("carboss", "comment", 2, "实测过图文的准确率，确实比一年前强太多。"),
    ("newsdaily", "comment", 8, "端到端智驾体验过，城市里确实能打，就是偶尔还是会有小失误。"),
    ("admin", "comment", 1, "欢迎常来交流 AI 心得！"),
    ("admin", "comment", 3, "开源生态确实起来了，这个观察很到位。"),
    ("admin", "comment", 9, "价格战的下半场，价值竞争才是出路，说得在理。"),
]

ok = 0
for act in ACTIONS:
    user, op, pid = act[0], act[1], act[2]
    content = act[3] if len(act) > 3 else None
    cookie = login(user)
    if op == "like":
        status, txt = call("POST", f"/api/posts/{pid}/like", cookie=cookie)
        label = f"{user} 点赞帖{pid}"
    else:
        status, txt = call("POST", f"/api/posts/{pid}/comments",
                           {"content": content}, cookie=cookie)
        label = f"{user} 评论帖{pid}"
    okflag = "OK" if 200 <= status < 300 else "FAIL"
    if okflag == "OK":
        ok += 1
    print(f"[{okflag}] {label} -> {status}")
    if okflag == "FAIL":
        print("      " + txt)

print(f"\n点赞/评论操作成功 {ok}/{len(ACTIONS)} 项")
