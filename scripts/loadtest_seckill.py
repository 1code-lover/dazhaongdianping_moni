#!/usr/bin/env python3
"""
批量压测：POST /voucher-order/seckill/{voucher_id}
- 单 token：仍可用环境变量 SECKILL_TOKEN（并发会变成大量「重复下单」，适合只测入口吞吐）
- 多用户：使用 --tokens-file，每行一个 authorization（# 开头为注释），按请求轮询使用

依赖：pip install requests

示例：
  # 单 token
  set SECKILL_TOKEN=xxx
  python scripts/loadtest_seckill.py --count 600

  # 多 token（推荐，更接近真实抢券）
  python scripts/loadtest_seckill.py --tokens-file scripts/tokens.txt --voucher 10 --workers 32 --count 2000
"""
from __future__ import annotations

import argparse
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import requests
except ImportError:
    raise SystemExit("请先安装: pip install requests")


def load_tokens(path: str) -> list[str]:
    out: list[str] = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            t = line.strip()
            if not t or t.startswith("#"):
                continue
            out.append(t)
    return out


def one_request(sess: requests.Session, base: str, voucher: int, token: str) -> str:
    url = f"{base.rstrip('/')}/voucher-order/seckill/{voucher}"
    r = sess.post(url, headers={"authorization": token}, timeout=30)
    if r.status_code != 200:
        return "http"
    try:
        j = r.json()
    except Exception:
        return "http"
    if j.get("success") is True:
        return "ok"
    return "biz"


def main() -> None:
    ap = argparse.ArgumentParser(description="秒杀 HTTP 压测（支持多 token 轮询）")
    ap.add_argument("--base", default=os.environ.get("SECKILL_BASE", "http://127.0.0.1:8081"))
    ap.add_argument("--voucher", type=int, default=10)
    ap.add_argument("--workers", type=int, default=20)
    ap.add_argument("--count", type=int, default=400, help="总请求数")
    ap.add_argument(
        "--tokens-file",
        default=os.environ.get("SECKILL_TOKENS_FILE", ""),
        help="每行一个 authorization；若不设则用 SECKILL_TOKEN 单用户",
    )
    args = ap.parse_args()

    tokens: list[str] = []
    if args.tokens_file:
        tokens = load_tokens(args.tokens_file)
        if not tokens:
            raise SystemExit(f"tokens file empty or missing: {args.tokens_file}")
    else:
        single = os.environ.get("SECKILL_TOKEN", "").strip()
        if not single:
            raise SystemExit(
                "请设置 --tokens-file 指向多行 token 文件，或环境变量 SECKILL_TOKEN 单 token 测试"
            )
        tokens = [single]

    mode = f"multi_token_lines={len(tokens)}" if len(tokens) > 1 else "single_token"
    print("mode:", mode)

    counts = {"ok": 0, "biz": 0, "http": 0}
    lock = threading.Lock()
    n_tokens = len(tokens)

    def task(req_index: int) -> str:
        tok = tokens[req_index % n_tokens]
        with requests.Session() as sess:
            return one_request(sess, args.base, args.voucher, tok)

    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(task, i) for i in range(args.count)]
        for fu in as_completed(futs):
            k = fu.result()
            with lock:
                counts[k] = counts.get(k, 0) + 1
    dt = time.perf_counter() - t0

    print("duration_sec:", round(dt, 3))
    print("total:", args.count)
    print("success_true~:", counts.get("ok", 0))
    print("success_false (库存/重复等):", counts.get("biz", 0))
    print("http/error:", counts.get("http", 0))
    print("approx_rps:", round(args.count / dt, 1))


if __name__ == "__main__":
    main()
