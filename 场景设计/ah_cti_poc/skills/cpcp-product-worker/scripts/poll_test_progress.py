#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试进度轮询脚本（等价原 wf_sub_04 代码节点 0304）。

轮询 get_test_progress，间隔 5s，最多 360 次（30 分钟超时），
连续 5 次查询失败终止转人工（保留 globalId）。

V12.1 进程内直调优化：默认 import cpcp_api 直接调用其 `_http`（read-only，
不触发 _err 的 sys.exit），**消除每轮询一次派生 cpcp_api.py 子进程的开销**
（Windows 上数百次 Python 进程启动是纯次生耗时，与被测平台无关）。
import 失败时回退到原子进程方式（保持兼容/健壮）。

用法：
  python poll_test_progress.py --global-id 50202608252017364447983718 [--interval 5] [--max-retry 360]
"""
import argparse
import importlib.util
import json
import os
import sys
import time

SCRIPT_DIR = __file__

_PROGRESS = True


def _heartbeat(round_idx, interval, global_id, resp):
    """向 stderr 打印进度心跳并 flush（仅当开启 --progress）。

    目的：run_pipeline 单次调用期间最长需轮询约 30 分钟，若全程无标准输出，
    Agent 的 bash 工具会判定命令卡死而转后台，导致模型误报"等待通知"。
    定期心跳让命令持续可见，避免被误判为后台长任务。
    心跳走 stderr，不污染 stdout 的最终结果 JSON 契约。
    """
    if not _PROGRESS:
        return
    elapsed = round_idx * interval
    done_cnt = str((resp or {}).get("doneCount", "")).strip()
    total = str((resp or {}).get("testCaseCount", "")).strip()
    prog = ("%s/%s" % (done_cnt, total)) if (done_cnt or total) else "?"
    sys.stderr.write("[poll] 第 %d 轮，已等待 %ds，进度 %s（globalId %s）\n"
                     % (round_idx + 1, elapsed, prog, global_id))
    sys.stderr.flush()


def _load_cpcp_api():
    """优先进程内 import cpcp_api（同目录）。返回模块对象或 None。"""
    try:
        spec = importlib.util.spec_from_file_location(
            "cpcp_api_mod", os.path.join(os.path.dirname(os.path.abspath(__file__)), "cpcp_api.py"))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod
    except Exception:
        return None


def query_progress(global_id, cpcp_mod):
    """一次进度查询：进程内直调 _http（read-only），import 失败回退子进程方式。
    返回出参 dict 或 None（查询失败）。"""
    if cpcp_mod is not None:
        try:
            return cpcp_mod._http("POST", "/api/v1/appstore/test/offer/progress",
                                  {"globalId": global_id},
                                  timeout=cpcp_mod.TIMEOUT_ASYNC, retries=0)
        except Exception:
            return None
    # 回退：子进程调用 cpcp_api.py test_progress
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        import subprocess
        r = subprocess.run(
            [sys.executable, os.path.join(script_dir, "cpcp_api.py"), "test_progress", "--global-id", global_id],
            capture_output=True, text=True, timeout=40,
            cwd=script_dir,
        )
        return json.loads(r.stdout)
    except Exception:
        return None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--global-id", required=True)
    p.add_argument("--interval", type=int, default=5)
    p.add_argument("--max-retry", type=int, default=360)
    p.add_argument("--max-consecutive-fail", type=int, default=2)
    p.add_argument("--no-progress", action="store_true",
                   help="关闭 stderr 进度心跳（默认开启，供 run_pipeline 防后台误判）")
    args = p.parse_args()

    global _PROGRESS
    _PROGRESS = not args.no_progress

    cpcp_mod = _load_cpcp_api()

    consecutive_fail = 0
    for i in range(args.max_retry):
        resp = query_progress(args.global_id, cpcp_mod)
        if resp is None or resp.get("resultCode") not in (None, "0", 0):
            consecutive_fail += 1
            if consecutive_fail >= args.max_consecutive_fail:
                print(json.dumps({"done": False, "failed": True,
                                  "fail_reason": "连续查询失败，转人工（保留 globalId %s）" % args.global_id},
                                 ensure_ascii=False))
                sys.exit(2)
            _heartbeat(i, args.interval, args.global_id, resp)
            time.sleep(args.interval)
            continue
        consecutive_fail = 0

        # 后端出参均为字符串（"true"/"false"），须按真值解析而非严格 is True
        done_flag = str(resp.get("done")).lower() == "true"
        failed_flag = str(resp.get("failed")).lower() == "true"

        if done_flag:
            print(json.dumps({"done": True, "failed": False}, ensure_ascii=False))
            return
        if failed_flag:
            print(json.dumps({"done": False, "failed": True, "failIndex": resp.get("failIndex", -1),
                              "fail_reason": "测试失败/中止，仍取完整结果供报告定位失败原因"},
                             ensure_ascii=False))
            sys.exit(1)
        _heartbeat(i, args.interval, args.global_id, resp)
        time.sleep(args.interval)

    print(json.dumps({"done": False, "failed": True,
                      "fail_reason": "测试超时（30 分钟），请凭 globalId %s 人工续查" % args.global_id},
                     ensure_ascii=False))
    sys.exit(3)


if __name__ == "__main__":
    main()
