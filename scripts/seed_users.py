# -*- coding: utf-8 -*-
"""
创建演示用户：aifan / techgeek / carboss / newsdaily（若不存在）
先以 admin 登录获取会话，再逐个调用 POST /api/users 创建。
用法: python seed_users.py
"""
import json
import http.client

BASE_HOST = "localhost"
BASE_PORT = 8090

ADMIN = {"username": "admin", "password": "admin123"}

# 演示用户：用户名 -> (邮箱, 密码, 年龄)
DEMO_USERS = {
    "aifan":     ("aifan@example.com",     "123456", 28),
    "techgeek":  ("techgeek@example.com",  "123456", 32),
    "carboss":   ("carboss@example.com",   "123456", 35),
    "newsdaily": ("newsdaily@example.com", "123456", 30),
}


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


def main():
    # 1) admin 登录
    status, txt = call("POST", "/api/auth/login", ADMIN)
    if status != 200:
        print(f"[FAIL] admin 登录失败 -> {status}: {txt}")
        return 1
    cookie = None
    # 从响应头拿 Set-Cookie
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps(ADMIN),
                 headers={"Content-Type": "application/json"})
    resp = conn.getresponse()
    sc = resp.getheader("Set-Cookie")
    if sc:
        cookie = sc.split(";")[0]
    resp.read()
    conn.close()
    if not cookie:
        print("[FAIL] 未获取到登录 Cookie")
        return 1
    print(f"[OK] admin 登录成功, cookie={cookie}")

    # 2) 查看已有用户，避免重复创建
    status, txt = call("GET", "/api/users", cookie=cookie)
    existing = set()
    if 200 <= status < 300:
        try:
            data = json.loads(txt).get("data", [])
            existing = {u.get("username") for u in data}
        except Exception as e:
            print(f"[WARN] 解析已有用户失败: {e}")
    print(f"当前已有用户: {sorted(existing) if existing else '无'}")

    # 3) 创建不存在的演示用户
    created = 0
    for username, (email, password, age) in DEMO_USERS.items():
        if username in existing:
            print(f"[SKIP] {username} 已存在")
            continue
        status, txt = call("POST", "/api/users",
                           {"username": username, "email": email,
                            "password": password, "age": age},
                           cookie=cookie)
        ok = "OK" if 200 <= status < 300 else "FAIL"
        print(f"[{ok}] 创建用户 {username} -> {status}")
        if ok != "OK":
            print("      " + txt)
        else:
            created += 1

    print(f"\n共创建 {created} 个演示用户")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
