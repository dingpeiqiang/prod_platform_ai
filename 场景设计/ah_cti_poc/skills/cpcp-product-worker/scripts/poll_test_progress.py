#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试进度轮询脚本（等价原 wf_sub_04 代码节点 0304）。

轮询 get_test_progress，间隔 5s，最多 360 次（30 分钟超时），
连续 5 次查询失败终止转人工（保留 globalId）。

用法：
  python poll_test_progress.py --global-id 50202608252017364447983718 [--interval 5] [--max-retry 360]
"""
import argparse
import json
import subprocess
import sys
import time

SCRIPT_DIR = __file__


def query_progress(global_id):
    """调用 cpcp_api.py test_progress 单次查询，返回出参 dict 或 None（查询失败）。"""
    try:
        r = subprocess.run(
            [sys.executable, "cpcp_api.py", "test_progress", "--global-id", global_id],
            capture_output=True, text=True, timeout=40,
            cwd=None,
        )
        return json.loads(r.stdout)
    except Exception:
        return None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--global-id", required=True)
    p.add_argument("--interval", type=int, default=5)
    p.add_argument("--max-retry", type=int, default=360)
    p.add_argument("--max-consecutive-fail", type=int, default=5)
    args = p.parse_args()

    consecutive_fail = 0
    for i in range(args.max_retry):
        resp = query_progress(args.global_id)
        if resp is None or resp.get("resultCode") not in (None, "0", 0):
            consecutive_fail += 1
            if consecutive_fail >= args.max_consecutive_fail:
                print(json.dumps({"done": False, "failed": True,
                                  "fail_reason": "连续查询失败，转人工（保留 globalId %s）" % args.global_id},
                                 ensure_ascii=False))
                sys.exit(2)
            time.sleep(args.interval)
            continue
        consecutive_fail = 0

        if resp.get("done") is True:
            print(json.dumps({"done": True, "failed": False}, ensure_ascii=False))
            return
        if resp.get("failed") is True:
            print(json.dumps({"done": False, "failed": True, "failIndex": resp.get("failIndex", -1),
                              "fail_reason": "测试失败/中止，仍取完整结果供报告定位失败原因"},
                             ensure_ascii=False))
            sys.exit(1)
        time.sleep(args.interval)

    print(json.dumps({"done": False, "failed": True,
                      "fail_reason": "测试超时（30 分钟），请凭 globalId %s 人工续查" % args.global_id},
                     ensure_ascii=False))
    sys.exit(3)


if __name__ == "__main__":
    main()
