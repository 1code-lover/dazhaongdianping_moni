#!/usr/bin/env python3
"""
秒杀 keep-alive 压测（连接复用版，测服务端真实容量）
- 每线程一个 Session 复用 TCP 连接，消除客户端建连开销
- 输出：入口 RPS / 延迟分布 / 成功数
- 消费落库速度需配合轮询 DB 观察（脚本外）

用法：
  python3 scripts/loadtest_seckill_keepalive.py --tokens-file /tmp/tokens3.txt --voucher 20 --workers 64 --count 6000
"""
from __future__ import annotations

import argparse
import threading
import time
from concurrent.futures import ThreadPoolExecutor

import requests


def load_tokens(path: str) -> list[str]:
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            t = line.strip()
            if t and not t.startswith("#"):
                out.append(t)
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description="秒杀 keep-alive 压测")
    ap.add_argument("--base", default="http://127.0.0.1:8081")
    ap.add_argument("--voucher", type=int, required=True)
    ap.add_argument("--workers", type=int, default=64)
    ap.add_argument("--count", type=int, default=6000)
    ap.add_argument("--tokens-file", required=True)
    args = ap.parse_args()

    tokens = load_tokens(args.tokens_file)
    url = f"{args.base.rstrip('/')}/voucher-order/seckill/{args.voucher}"
    n_tokens = len(tokens)

    lat: list[float] = []
    counts = {"ok": 0, "biz": 0, "http": 0}
    lock = threading.Lock()
    local = threading.local()

    def task(i: int) -> None:
        sess = getattr(local, "sess", None)
        if sess is None:
            sess = local.sess = requests.Session()
        tok = tokens[i % n_tokens]
        t0 = time.perf_counter()
        try:
            r = sess.post(url, headers={"authorization": tok}, timeout=30)
            dt = (time.perf_counter() - t0) * 1000
            with lock:
                lat.append(dt)
                if r.status_code != 200:
                    counts["http"] += 1
                elif r.json().get("success"):
                    counts["ok"] += 1
                else:
                    counts["biz"] += 1
        except Exception:
            with lock:
                counts["http"] += 1

    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        list(ex.map(task, range(args.count)))
    dt = time.perf_counter() - t0

    lat.sort()
    n = len(lat)
    print(f"mode: keep-alive workers={args.workers}")
    print(f"duration_sec: {dt:.2f}")
    print(f"total: {args.count}")
    print(f"success(下单受理): {counts['ok']}")
    print(f"biz_reject(重复/库存): {counts['biz']}")
    print(f"http_error: {counts['http']}")
    print(f"entry_rps: {args.count / dt:.0f}")
    if n:
        print(f"latency avg={sum(lat)/n:.1f}ms p95={lat[int(n*0.95)]:.1f}ms max={lat[-1]:.1f}ms")


if __name__ == "__main__":
    main()
