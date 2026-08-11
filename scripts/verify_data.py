# -*- coding: utf-8 -*-
"""
验证（补充）：评论计数 + 评论列表 API
"""
import json
import http.client
from urllib.parse import quote

BASE_HOST = "localhost"
BASE_PORT = 8090


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


def login(username, password):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps({"username": username, "password": password}),
                 headers={"Content-Type": "application/json"})
    r = conn.getresponse()
    sc = r.getheader("Set-Cookie")
    cookie = sc.split(";")[0] if sc else None
    r.read()
    conn.close()
    return cookie


def main():
    out = []
    cookie = login("admin", "admin123")

    # 1) 帖子列表中的 commentCount
    status, txt = call("GET", "/api/posts", cookie=cookie)
    posts = json.loads(txt).get("data", [])
    out.append("[帖子列表 commentCount]")
    total_comments = 0
    for p in posts:
        total_comments += p.get("commentCount", 0)
        out.append(f"  #{p['id']} commentCount={p.get('commentCount')} likeCount={p.get('likeCount')} | {p['title']}")
    out.append(f"  评论总数(列表统计) = {total_comments}")

    # 2) 评论列表 API（帖1 应有 3 条）
    status, txt = call("GET", "/api/posts/1/comments", cookie=cookie)
    comments = json.loads(txt).get("data", [])
    out.append(f"\n[帖1 评论列表] HTTP {status}，共 {len(comments)} 条")
    for c in comments:
        out.append(f"  - #{c['id']} {c['author']}: {c['content']}")

    # 3) 点赞明细 API（帖1 应 3 个用户）—— 通过 Detail/like 状态体现
    status, txt = call("GET", "/api/posts/1", cookie=cookie)
    detail = json.loads(txt).get("data", {})
    out.append(f"\n[帖1 详情] HTTP {status} | likeCount={detail.get('likeCount')} commentCount={detail.get('commentCount')} likedByMe={detail.get('likedByMe')}")

    # 4) 搜索验证
    status, txt = call("GET", "/api/posts/search?keyword=" + quote("新能源"), cookie=cookie)
    hits = json.loads(txt).get("data", [])
    out.append(f"\n[搜索 '新能源'] HTTP {status}，命中 {len(hits)} 条:")
    for h in hits:
        out.append(f"  #{h['id']} {h['title']}")

    # 5) 标签接口
    status, txt = call("GET", "/api/tags", cookie=cookie)
    tags = json.loads(txt).get("data", [])
    out.append(f"\n[标签统计] HTTP {status}，共 {len(tags)} 个，Top5: {[t['name']+'(x'+str(t['count'])+')' for t in tags[:5]]}")

    with open("verify_report.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print("验证完成，报告已写入 verify_report.txt")


if __name__ == "__main__":
    main()
