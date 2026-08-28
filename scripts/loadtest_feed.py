# -*- coding: utf-8 -*-
"""
推荐 feed 接口容量测试脚本（P3-4，纯标准库，无第三方依赖）

用法示例：
  # 基础容量：50 个会话、20 并发、2000 请求、100 预热
  python scripts/loadtest_feed.py --base-url http://localhost:8090 --users 50 --concurrency 20 --total-requests 2000 --warmup 100

  # 专测入口限流：期望被 429（需 app.rec.rate-limit.enabled=true，默认开启）
  python scripts/loadtest_feed.py --concurrency 50 --total-requests 5000 --expect-status 429

  # 结果落盘
  python scripts/loadtest_feed.py --users 30 --total-requests 1500 --out results.json

重要提示：
  * 压"推荐漏斗"（找单机容量）前，先把 app.rec.rate-limit.enabled 设为 false——
    否则入口限流（100 请求/60s/IP）先把同一客户端 IP 打 429，测的是限流而不是漏斗。
  * 压"限流"本身：保持 rate-limit 开启，用 --expect-status 429 断言。
  * 结论看 p99 与错误率：扩容公式 机器数 = 峰值QPS ÷ 单机QPS（先摸清单机再谈扩容）。
"""
import argparse
import http.client
import json
import random
import statistics
import threading
import time
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed

# 演示用户（与 scripts/seed_users.py 对齐）：压测时按需循环复用，登录一次拿一个独立会话
KNOWN_USERS = [
    ("admin", "admin123"),
    ("aifan", "123456"),
    ("techgeek", "123456"),
    ("carboss", "123456"),
    ("newsdaily", "123456"),
]

_latency_lock = threading.Lock()
_latencies = []          # 每个请求耗时 ms
_status_counts = {}      # status -> 次数
_error_count = 0
_error_examples = []     # 最多留 3 个错误样例


def parse_base_url(base_url):
    u = urllib.parse.urlparse(base_url)
    scheme = u.scheme or "http"
    host = u.hostname or "localhost"
    port = u.port or (443 if scheme == "https" else 80)
    return scheme, host, port


def login(scheme, host, port, username, password):
    """登录拿 JSESSIONID（形式 'JSESSIONID=xxx'，可作 Cookie 头值）"""
    conn = http.client.HTTPConnection(host, port)
    try:
        conn.request("POST", "/api/auth/login",
                     body=json.dumps({"username": username, "password": password}),
                     headers={"Content-Type": "application/json"})
        r = conn.getresponse()
        cookie = None
        sc = r.getheader("Set-Cookie")
        if sc:
            cookie = sc.split(";")[0]
        r.read()
        conn.close()
        if r.status != 200 or not cookie:
            raise RuntimeError(f"登录失败 {username}: status={r.status}, cookie={cookie}")
        return cookie
    finally:
        conn.close()


def fire_request(scheme, host, port, path, cookie, expect_status):
    """发一次 feed 请求，返回 (status, latency_ms)；连接异常按错误计"""
    global _error_count
    t0 = time.time()
    try:
        conn = http.client.HTTPConnection(host, port)
        try:
            conn.request("GET", path, headers={"Cookie": cookie})
            r = conn.getresponse()
            status = r.status
            r.read()  # 读完响应体，释放连接
        finally:
            conn.close()
        latency = (time.time() - t0) * 1000
        with _latency_lock:
            _latencies.append(latency)
            _status_counts[status] = _status_counts.get(status, 0) + 1
            if status != expect_status and len(_error_examples) < 3:
                _error_examples.append(f"{status} @{path} (期望{expect_status})")
        return status
    except Exception as e:  # 连接失败/超时（http.client 无默认超时，可用 -t 参数控制）
        with _latency_lock:
            _error_count += 1
            if len(_error_examples) < 3:
                _error_examples.append(f"EXC {e!r}")
        return None


def percentile(data, p):
    if not data:
        return 0.0
    s = sorted(data)
    k = (len(s) - 1) * p / 100
    lo = int(k)
    hi = min(lo + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def main():
    ap = argparse.ArgumentParser(description="推荐 feed 接口容量测试（纯标准库）")
    ap.add_argument("--base-url", default="http://localhost:8090", help="服务地址（默认 http://localhost:8090）")
    ap.add_argument("--users", type=int, default=10, help="登录会话数（复用演示用户，各拿独立 JSESSIONID，默认 10）")
    ap.add_argument("--concurrency", type=int, default=10, help="并发线程数（默认 10）")
    ap.add_argument("--total-requests", type=int, default=1000, help="总请求数（默认 1000）")
    ap.add_argument("--warmup", type=int, default=100, help="预热请求数（串行，默认 100，用于填充画像/热门缓存）")
    ap.add_argument("--size", type=int, default=10, help="feed 分页 size（默认 10）")
    ap.add_argument("--expect-status", type=int, default=200, help="期望响应码，其它视为异常（默认 200；测限流传 429）")
    ap.add_argument("--out", default=None, help="结果 JSON 落盘路径（可选）")
    args = ap.parse_args()

    scheme, host, port = parse_base_url(args.base_url)
    feed_path = f"/api/recommend/feed?page=1&size={args.size}"
    print(f"目标: {scheme}://{host}:{port}{feed_path}")

    # 1. 建会话：循环复用演示用户，每个登录拿独立 JSESSIONID
    cookies = []
    for i in range(args.users):
        username, password = KNOWN_USERS[i % len(KNOWN_USERS)]
        cookies.append(login(scheme, host, port, username, password))
    print(f"登录完成: {len(cookies)} 个会话（复用 {len(KNOWN_USERS)} 个演示账号）")

    # 2. 预热：串行打一批，填缓存，避免把"冷启动现算"算进容量数据
    for i in range(args.warmup):
        cookie = random.choice(cookies)
        fire_request(scheme, host, port, feed_path, cookie, args.expect_status)
    # 清空预热计时数据，只统计正式请求
    _latencies.clear()
    _status_counts.clear()
    global _error_count
    _error_count = 0
    print(f"预热完成: {args.warmup} 个请求（不计入结果）")

    # 3. 正式压测：固定并发
    t_start = time.time()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futs = [pool.submit(fire_request, scheme, host, port, feed_path, random.choice(cookies), args.expect_status)
                for _ in range(args.total_requests)]
        for _ in as_completed(futs):
            pass  # 结果已在线程内累计
    elapsed = time.time() - t_start

    # 4. 汇总
    n = len(_latencies)
    ok = _status_counts.get(args.expect_status, 0)
    qps = n / elapsed if elapsed > 0 else 0.0
    summary = {
        "base_url": args.base_url,
        "requests": args.total_requests,
        "concurrency": args.concurrency,
        "elapsed_sec": round(elapsed, 3),
        "qps": round(qps, 1),
        "expected_status": args.expect_status,
        "expected_hits": ok,
        "other_status_counts": dict(sorted(_status_counts.items())),
        "error_count": _error_count,
        "error_examples": _error_examples,
        "latency_ms": {
            "min": round(min(_latencies), 1) if _latencies else 0,
            "p50": round(percentile(_latencies, 50), 1),
            "p90": round(percentile(_latencies, 90), 1),
            "p95": round(percentile(_latencies, 95), 1),
            "p99": round(percentile(_latencies, 99), 1),
            "max": round(max(_latencies), 1) if _latencies else 0,
        },
    }
    print("\n===== 结果 =====")
    print(f"总请求 {n}，耗时 {elapsed:.2f}s，QPS ≈ {qps:.1f}")
    print(f"期望状态 {args.expect_status} 命中 {ok}，其它状态 {summary['other_status_counts']}，连接错误 {_error_count}")
    if _error_examples:
        print(f"错误样例: {_error_examples}")
    print(f"延迟(ms): p50={summary['latency_ms']['p50']}  p90={summary['latency_ms']['p90']}  "
          f"p95={summary['latency_ms']['p95']}  p99={summary['latency_ms']['p99']}  "
          f"max={summary['latency_ms']['max']}")
    print("扩容参考: 机器数 = 峰值QPS ÷ 单机QPS（本脚本给的是单机 QPS，留 30~50% 冗余）")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        print(f"结果已写入 {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
