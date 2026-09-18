# -*- coding: utf-8 -*-
"""run_pipeline / poll_test_progress 进程内直调优化自测（V12.1）。

背景：原实现在 Windows 上每次 API 调用与每次测试轮询都派生一个 cpcp_api.py
子进程，叠加成百上千次 Python 进程启动（纯次生耗时）。V12.1 改为 import cpcp_api
进程内直调其 cmd_* / _http，消除该开销。

覆盖（全部离线，不依赖后端与 LLM）：
  1. run_pipeline 进程内加载 cpcp_api 模块成功（_cpcp_api 非 None）；
  2. _build_ns 正确映射 --xxx 与 --xxx-file 到属性（_read_arg 同判）；
  3. _api 进程内调用只读子命令在不可达后端下返回错误 dict（NET_ERROR），不崩溃；
  4. run_pipeline 编译与主入口参数解析不报错（--confirmed 门禁路径）；
  5. poll_test_progress 进程内加载 cpcp_api 模块成功。
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable


def main():
    ok = 0

    def check(name, cond, detail=""):
        nonlocal ok
        assert cond, "%s -> %s" % (name, detail)
        print("OK  %s" % name)
        ok += 1

    import run_pipeline as rp
    import poll_test_progress as ptp

    # 1. run_pipeline 进程内加载 cpcp_api 成功
    check("run_pipeline 进程内加载 cpcp_api", rp._cpcp_api is not None)
    check("cpcp_api 暴露 _http", hasattr(rp._cpcp_api, "_http") if rp._cpcp_api else False)

    # 2. _build_ns 映射 --xxx / --xxx-file
    ns = rp._build_ns(["--req-id", "PLANX", "--plan-json-file", "C:/a/b.json", "--offer-id", "9001"])
    check("_build_ns 映射普通键", getattr(ns, "req_id", None) == "PLANX")
    check("_build_ns 映射 -file 后缀键", getattr(ns, "plan_json_file", None) == "C:/a/b.json")

    # 3. _api 进程内只读调用 + 不可达后端 → 返回错误 dict，不崩溃
    os.environ["CPCP_BASE_URL"] = "http://127.0.0.1:1"
    try:
        ok2, out = rp._api(["query_node_result", "--req-id", "PLANX", "--node", "config"])
    finally:
        os.environ.pop("CPCP_BASE_URL", None)
    check("_api 进程内只读调用返回 dict", isinstance(out, dict))
    check("_api 进程内错误归一 NET_ERROR", str(out.get("resultCode", "")) == "NET_ERROR")

    # 4. run_pipeline 主入口：未确认（无 --confirmed）→ 门禁拒绝，不崩溃
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "run_pipeline.py"),
                        "--req-id", "PLANX", "--workdir", HERE],
                       capture_output=True, text=True, encoding="utf-8")
    outj = json.loads(r.stdout or "{}")
    check("run_pipeline 无 --confirmed 返回 NOT_CONFIRMED", outj.get("resultCode") == "NOT_CONFIRMED", r.stdout)

    # 5. poll_test_progress 进程内加载 cpcp_api 成功
    mod = ptp._load_cpcp_api()
    check("poll_test_progress 进程内加载 cpcp_api", mod is not None)
    check("poll 所用 _http 可用", hasattr(mod, "_http") and hasattr(mod, "TIMEOUT_ASYNC"))

    # 6. 进度心跳：poll 默认向 stderr 输出心跳，防 bash 长静默被判后台（V12.2）
    import io
    import contextlib
    buf = io.StringIO()
    with contextlib.redirect_stderr(buf):
        ptp._PROGRESS = True
        ptp._heartbeat(0, 5, "GIDX", {"doneCount": "3", "testCaseCount": "30"})
    hb = buf.getvalue()
    check("poll 心跳输出到 stderr 且含进度", "[poll]" in hb and "3/30" in hb, hb)
    # --no-progress 时静默
    buf2 = io.StringIO()
    with contextlib.redirect_stderr(buf2):
        ptp._PROGRESS = False
        ptp._heartbeat(0, 5, "GIDX", {})
    check("poll --no-progress 静默", buf2.getvalue() == "")
    ptp._PROGRESS = True
    # run_pipeline 阶段心跳走 stderr（不污染 stdout 结果 JSON 契约）
    buf3 = io.StringIO()
    with contextlib.redirect_stderr(buf3):
        rp._stage_progress("环节3/9 销售品智能配置：开始执行")
    check("run_pipeline 阶段心跳含环节标记", "[pipeline]" in buf3.getvalue())
    # poll 调用使用 Popen 流式转发（stderr 不捕获），确保轮询期外部持续可见
    with open(os.path.join(HERE, "run_pipeline.py"), "r", encoding="utf-8") as f:
        src = f.read()
    check("run_pipeline 轮询用 Popen 流式转发心跳",
          "subprocess.Popen(" in src and "stderr=None" in src)

    # 7. V12.3 分段执行（--stage prepare/poll）秒级返回，规避长命令被工具转后台
    import tempfile
    tmp = tempfile.mkdtemp(prefix="rp_stage_")
    # prepare 缺 plan_json → E5（不进长流程）
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "run_pipeline.py"),
                        "--req-id", "PLANX", "--workdir", tmp, "--confirmed", "--stage", "prepare"],
                       capture_output=True, text=True, encoding="utf-8")
    oj = json.loads(r.stdout or "{}")
    check("--stage prepare 缺 plan_json → E5", oj.get("e_code") == "E5", r.stdout)
    # poll 无 test_global_id → E18
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "run_pipeline.py"),
                        "--req-id", "PLANX", "--workdir", tmp, "--confirmed", "--stage", "poll"],
                       capture_output=True, text=True, encoding="utf-8")
    oj = json.loads(r.stdout or "{}")
    check("--stage poll 缺 globalId → E18", oj.get("e_code") == "E18", r.stdout)
    # poll 后端不可达 → E12（归一，不崩溃、秒级返回）
    with open(os.path.join(tmp, "pipeline_state_PLANX.json"), "w", encoding="utf-8") as f:
        json.dump({"req_id": "PLANX", "fail_node": None, "nodes": {}, "test_global_id": "GIDX"}, f)
    os.environ["CPCP_BASE_URL"] = "http://127.0.0.1:1"
    try:
        r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "run_pipeline.py"),
                            "--req-id", "PLANX", "--workdir", tmp, "--confirmed", "--stage", "poll"],
                           capture_output=True, text=True, encoding="utf-8")
    finally:
        os.environ.pop("CPCP_BASE_URL", None)
    oj = json.loads(r.stdout or "{}")
    check("--stage poll 后端不可达 → E12", oj.get("e_code") == "E12", r.stdout)
    check("--stage 存在 prepare/poll 选项",
          "choices=[\"all\", \"prepare\", \"poll\"]" in src)

    print("\n%s checks passed" % ok)


if __name__ == "__main__":
    main()
