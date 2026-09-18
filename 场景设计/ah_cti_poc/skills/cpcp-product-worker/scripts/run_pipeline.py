#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""执行主干确定性状态机（run_pipeline V1.0）。

背景：SKILL.md/flow-B 曾把"串行编排/判定/存储/续跑"全部交给模型按文字纪律执行，
导致固定流程依赖 LLM 服从度、行为漂移。本脚本把执行主干四环节
（智能配置 config → 配置规格稽核 spec → 资费校准 fee → 自动测试 test 含受理验证）
收敛为**确定性代码**：串行顺序、成功判定、存储落盘、续跑回放、失败码归一全部脚本化，
模型只负责：意图识别、确认语义门禁、以及最终按模板把 state 出参解读给用户。

核心保证（对应原 SKILL.md 纪律 2/3/8/9 与 flow-B 通用步骤模式）：
1. 串行铁律：config→spec→fee→test 严格按序，任何环节失败/异常立即停止（exit 码区分），
   不重试、不跳步、不降级；传输层重试仍由 cpcp_api.py 内置（共 3 次尝试）。
2. 确认门禁：--confirmed 未显式传入时拒绝执行（对应"未识别到确认类回复不得执行智能配置"，
   门禁在模型侧识别确认语义后传 --confirmed，脚本再兜底）。
3. 续跑回放：--resume 按 fail_node 从失败环节续跑；已成功环节凭本地 state/存储记录回放，
   不重复调用写接口。
4. 工件落盘：所有出参原文写工作区工件（plan_json_<req_id>.json / config_result_<req_id>.json /
   result_<node>_<req_id>.json），大报文一律经文件传递，命令行不内联 >1KB 报文。
5. 判定唯一数据源=出参字段：config=status、spec/fee=pass、test=test_passed 推导
   （全部场景 successTestCaseCount==testCaseCount）；融合组另看 offer_group_check。

出参（stdout 单行 JSON，模型逐字引用，禁止加工）：
  {"resultCode":"0","state_file":...,"req_id":...,"fail_node":null|config|spec|fee|test,
   "nodes":[{"node":"config","status":"SUCCESS|PARTIAL|FAIL|REPLAYED|SKIPPED","summary":{...}},...],
   "next_action":"APPROVAL_GATE"|"RESUME"|"FIX_PLAN"|"CHECK_PLATFORM"|"NETWORK_RETRY",
   "e_code":null|"E6"|"E8"|...,"messages":[...]}

用法：
  python run_pipeline.py --req-id PLANxxx --workdir <会话可写目录> --confirmed
  python run_pipeline.py --req-id PLANxxx --workdir <会话可写目录> --resume --fail-node STAGE3_FEE

同步调用契约（勿当后台任务，防"等通知"陷阱）：
  - V12.3 分段执行（推荐，根治长命令被工具转后台）：
      ① python run_pipeline.py --req-id <id> --workdir <dir> --confirmed --stage prepare
         → config→spec→fee→发起测试后【立即返回】（秒级），next_action=POLL, globalId=...
      ② python run_pipeline.py --req-id <id> --workdir <dir> --confirmed --stage poll
         → 单次查询进度（秒级）：未完成 next_action=POLL（模型再调一次）；完成则续跑
           结果判定+报告下载，next_action=APPROVAL_GATE（或失败码）。
      每条命令都是秒级返回，不会触发工具的"长命令转后台"，模型只需循环②直到 next_action != POLL。
  - --stage all（默认，兼容旧调用）：一次调用串行跑完 config→spec→fee→test（含内部整段轮询，
    最长约 33 分钟），仅在【结束】时 print 一行最终 stdout JSON 并 sys.exit；
    调用方须【阻塞等待返回并读取该最终 stdout JSON】，不得半途终止/转后台。
  - 两种模式出参均为 stdout 单行 JSON；完成信号 = next_action（APPROVAL_GATE / POLL / 异常码）。
  - 期间落盘的 config_result_<req_id>.json/result_<node>_<req_id>.json/pipeline_state_<req_id>.json
    只是内部中间状态，【不等于完成信号】。
"""
import argparse
import importlib.util
import io
import json
import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

FORCE_UTF8 = True
NODES = ["config", "spec", "fee", "test"]
# 续跑 fail_node 映射（flow-B 触发条件口径）
FAIL_NODE_MAP = {"STAGE1_CONFIG": "config", "STAGE2_AUDIT": "spec",
                 "STAGE3_FEE": "fee", "STAGE4_TEST": "test"}


def _load_cpcp_api():
    """优先进程内 import cpcp_api（同目录）。返回模块对象或 None。
    cpcp_api 的 cmd_* 出参经 print 写 stdout，_err 内部 sys.exit(2) 抛 SystemExit，
    均可由调用方统一捕获隔离，无需每次派生 Python 子进程（消除次生子进程启动开销）。"""
    try:
        spec = importlib.util.spec_from_file_location(
            "cp_pipeline_cpcp_api", os.path.join(SCRIPT_DIR, "cpcp_api.py"))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod
    except Exception:
        return None


_cpcp_api = _load_cpcp_api()


def _build_ns(args_list):
    """把 ['--key', value, ...] 解析为 argparse.Namespace（cpcp_api cmd_* 入参）。
    支持 --xxx 与 --xxx-file 两类文件名后缀（_read_arg 同判）。"""
    ns = argparse.Namespace()
    if not args_list:
        return ns
    keys = args_list[::2]
    vals = args_list[1::2]
    for i, k in enumerate(keys):
        name = k.lstrip("-").replace("-", "_")
        v = vals[i] if i < len(vals) else ""
        setattr(ns, name, v)
    return ns


def _api(args_list):
    """调用 cpcp_api.py 子命令，返回 (ok, out_dict)。出参恒为 dict（解析失败也归一）。
    V12.1 进程内直调：不走 subprocess 派生，捕获 cmd_* 打印到 stdout 的 JSON 与
    _err 抛出的 SystemExit；import 失败或执行异常时回退子进程方式。"""
    fn = None
    cpcp_mod = _cpcp_api
    if cpcp_mod is not None and args_list:
        fn = getattr(cpcp_mod, "cmd_" + args_list[0], None)
    if fn is not None:
        try:
            ns = _build_ns(args_list[1:])
            buf = io.StringIO()
            old_out, old_err = sys.stdout, sys.stderr
            sys.stdout, sys.stderr = buf, buf
            try:
                fn(ns)
            except SystemExit:
                pass  # _err 内部 sys.exit：其 JSON 已写入 buf
            finally:
                sys.stdout, sys.stderr = old_out, old_err
            raw = buf.getvalue().strip()
            if not raw:
                return False, {"resultCode": "SCRIPT_ERROR",
                               "resultMsg": "cpcp_api cmd_%s 无输出" % args_list[0]}
            return True, json.loads(raw)
        except json.JSONDecodeError:
            return False, {"resultCode": "PARSE_ERROR",
                           "resultMsg": "cpcp_api 出参不是合法 JSON"}
        except Exception as e:  # E24 口径：环境异常不静默，回退子进程
            pass
    # 回退：子进程方式（保持原语义/边界隔离）
    cmd = [sys.executable, os.path.join(SCRIPT_DIR, "cpcp_api.py")] + args_list
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=180,
                           cwd=SCRIPT_DIR, encoding="utf-8", errors="replace")
        raw = (r.stdout or "").strip()
        if not raw:
            return False, {"resultCode": "SCRIPT_ERROR",
                           "resultMsg": "cpcp_api 无输出（stderr=%s）" % (r.stderr or "")[:200]}
        return True, json.loads(raw)
    except subprocess.TimeoutExpired:
        return False, {"resultCode": "TIMEOUT", "resultMsg": "cpcp_api 调用超时"}
    except json.JSONDecodeError:
        return False, {"resultCode": "PARSE_ERROR", "resultMsg": "cpcp_api 出参不是合法 JSON"}
    except Exception as e:  # E24 口径：环境异常不静默
        return False, {"resultCode": "SCRIPT_ERROR", "resultMsg": str(e)}


def _force_utf8_stdio():
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


def _emit(obj, exit_code=0):
    print(json.dumps(obj, ensure_ascii=False))
    sys.exit(exit_code)


def _stage_progress(text):
    """阶段进度心跳，写 stderr 并 flush。

    单次 run_pipeline 调用最长需在测试环节轮询约 30 分钟，若期间 stdout/stderr
    全程静默，Agent 的 bash 工具会把命令判定为卡死并转后台，模型随之误报
    "等待系统通知"。阶段切换与轮询期间持续输出心跳可避免该误判；
    心跳走 stderr，最终结果 JSON 仍独占 stdout，契约不变。
    """
    try:
        sys.stderr.write("[pipeline] %s\n" % text)
        sys.stderr.flush()
    except Exception:
        pass


def _read_json(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def _write_text(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def _write_json(path, obj):
    _write_text(path, json.dumps(obj, ensure_ascii=False, indent=2))


def _state_path(workdir, req_id):
    return os.path.join(workdir, "pipeline_state_%s.json" % req_id)


def load_state(workdir, req_id):
    p = _state_path(workdir, req_id)
    if os.path.exists(p):
        try:
            return _read_json(p)
        except Exception:
            pass
    return {"req_id": req_id, "fail_node": None, "nodes": {}}


def save_state(workdir, state):
    _write_json(_state_path(workdir, state["req_id"]), state)


def node_record(state, node):
    return state["nodes"].get(node) or {}


def summary_of(node, out):
    """从环节出参提取输出模板所需关键字段（逐字引用，模型解读时仍以出参工件为准）。"""
    if node == "config":
        return {"status": out.get("status"), "product_id": out.get("product_id"),
                "offer_id": out.get("offer_id"), "script_url": out.get("script_url"),
                "group": out.get("group")}
    if node == "spec":
        return {"pass": out.get("pass"), "error_count": len(out.get("error_list") or [])}
    if node == "fee":
        return {"pass": out.get("pass"), "risk_count": len(out.get("risk_list") or []),
                "compare_total": len(out.get("compare_list") or [])}
    if node == "test":
        scenes = out.get("testScenes") or []
        return {"test_passed": out.get("test_passed"), "globalId": out.get("globalId"),
                "offerName": out.get("offerName"), "orderId": out.get("orderId"),
                "offerInstId": out.get("offerInstId"), "report_url": out.get("report_url"),
                "scene_count": len(scenes),
                "case_total": sum(int(s.get("testCaseCount") or 0) for s in scenes)}
    return {}


def judge(node, out):
    """环节成功判定（唯一依据=出参字段）。返回 (ok, e_code)。"""
    rc = str(out.get("resultCode", "0"))
    if rc not in ("0", "None", "") and rc != "0":
        # 传输层/脚本层错误（NET_ERROR/TIMEOUT/HTTP_xxx/PARAM_MISSING...）
        return False, "E29" if rc in ("NET_ERROR", "TIMEOUT") else "E20"
    if node == "config":
        st = out.get("status")
        return (st in ("SUCCESS", "PARTIAL"), None if st in ("SUCCESS", "PARTIAL") else "E6")
    if node == "spec":
        ok = out.get("pass") == 1 or out.get("pass") == "1"
        return ok, None if ok else "E8"
    if node == "fee":
        ok = out.get("pass") == 1 or out.get("pass") == "1"
        return ok, None if ok else "E9"
    if node == "test":
        scenes = out.get("testScenes") or []
        all_pass = bool(scenes) and all(
            s.get("successTestCaseCount") == s.get("testCaseCount") for s in scenes)
        voucher_ok = bool(out.get("orderId") and out.get("offerInstId"))
        ok = all_pass and voucher_ok
        return ok, None if ok else ("E11" if not scenes else "E14")
    return False, "E20"


def upstream_ok(state, node):
    idx = NODES.index(node)
    return all(node_record(state, n).get("status") in ("SUCCESS", "PARTIAL", "REPLAYED")
               for n in NODES[:idx])


def step_config(state, workdir, req_id, messages):
    _stage_progress("环节3/9 销售品智能配置：开始执行")
    plan_file = os.path.join(workdir, "plan_json_%s.json" % req_id)
    if not os.path.exists(plan_file):
        _emit({"resultCode": "PARAM_MISSING", "e_code": "E5",
               "resultMsg": "缺少会话工件 plan_json_%s.json，请先完成需求分析（程序A）" % req_id}, 2)
    plan_json = _read_json(plan_file)
    if plan_json.get("pending_fields"):
        _emit({"resultCode": "VALIDATE_FAIL", "e_code": "E5",
               "resultMsg": "plan_json 仍存在待补充字段，禁止进入智能配置（出口A 口径）"}, 2)
    ok, out = _api(["save_product_config", "--req-id", req_id,
                    "--plan-json-file", plan_file])
    result_file = os.path.join(workdir, "config_result_%s.json" % req_id)
    _write_json(result_file, out)  # 失败/超时也存储（供报告定位与续跑判定）
    _save_node(req_id, "config", result_file)
    okj, ecode = judge("config", out)
    state["nodes"]["config"] = {"status": "SUCCESS" if okj else "FAIL",
                                "result_file": result_file,
                                "summary": summary_of("config", out)}
    if not okj:
        state["fail_node"] = "config"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "config",
               "e_code": ecode, "next_action": "RESUME",
               "result_file": result_file, "messages": ["环节1 智能配置失败，主干中断"]}, 1)
    state["fail_node"] = None
    save_state(workdir, state)
    return {"node": "config", "status": "SUCCESS", "result_file": result_file,
            "summary": summary_of("config", out)}


def _save_node(req_id, node, result_file):
    """将环节结果写回后端存储；落库失败即中断（E6）。

    此前静默忽略出参，导致 req_id 格式非法（5002）时四环节结果未落库却仍报
    "主干成功"，最终上线审批被后端"四环节结果缺失"拒绝。现改为非 0 即中断，
    使落库失败在发生环节即暴露。"""
    ok, out = _api(["save_node_result", "--req-id", req_id, "--node", node,
                    "--result-json-file", result_file])
    code = str((out or {}).get("code", ""))
    if not ok or code not in ("0", ""):
        _emit({"resultCode": "SAVE_FAIL", "e_code": "E6", "req_id": req_id,
               "fail_node": node,
               "resultMsg": "环节 %s 结果落库失败（save_node_result code=%s）：%s"
                            % (node, code, (out or {}).get("msg", ""))}, 3)


def step_spec(state, workdir, req_id, offer_id, messages):
    _stage_progress("环节4/9 配置规格稽核：开始执行")
    plan_file = os.path.join(workdir, "plan_json_%s.json" % req_id)
    ok, out = _api(["spec_audit", "--offer-id", offer_id,
                    "--config-json-file", plan_file, "--audit-scene", "all"])
    result_file = os.path.join(workdir, "result_spec_%s.json" % req_id)
    _write_json(result_file, out)
    _save_node(req_id, "spec", result_file)
    okj, ecode = judge("spec", out)
    rec = {"node": "spec", "result_file": result_file, "summary": summary_of("spec", out)}
    rec["status"] = "SUCCESS" if okj else "FAIL"
    if not okj:
        state["fail_node"] = "spec"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "spec",
               "e_code": ecode, "next_action": "RESUME",
               "result_file": result_file,
               "messages": messages + ["环节2 配置规格稽核未通过（error_list 见出参工件）"]}, 1)
    state["fail_node"] = None
    save_state(workdir, state)
    return rec


def step_fee(state, workdir, req_id, messages):
    _stage_progress("环节5/9 资费校准：开始执行")
    plan_file = os.path.join(workdir, "plan_json_%s.json" % req_id)
    ok, out = _api(["billing_verify", "--config-json-file", plan_file, "--check-scene", "all"])
    result_file = os.path.join(workdir, "result_fee_%s.json" % req_id)
    _write_json(result_file, out)
    _save_node(req_id, "fee", result_file)
    okj, ecode = judge("fee", out)
    rec = {"node": "fee", "result_file": result_file, "summary": summary_of("fee", out)}
    rec["status"] = "SUCCESS" if okj else "FAIL"
    if not okj:
        state["fail_node"] = "fee"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "fee",
               "e_code": ecode, "next_action": "FIX_PLAN",
               "result_file": result_file,
               "messages": messages + ["环节3 资费校准未通过（risk_list 见出参工件）"]}, 1)
    state["fail_node"] = None
    save_state(workdir, state)
    return rec


def test_start(state, workdir, req_id, offer_id, messages):
    """环节4 第一阶段：发起测试 + 取场景清单（秒级）。返回 globalId。失败即 _emit 中断。"""
    _stage_progress("环节6/9 销售品自动测试：发起测试")
    ok, out = _api(["offer_test", "--offer-id", offer_id])
    if not ok or str(out.get("resultCode", "1")) != "0" or not out.get("globalId"):
        state["fail_node"] = "test"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": "E10", "next_action": "RESUME",
               "messages": messages + ["测试发起失败：" + str(out.get("resultMsg", ""))]}, 1)
    global_id = out.get("globalId")
    ok, sc = _api(["test_scenes", "--global-id", global_id])
    scenes = sc.get("testScenes") or [] if ok else []
    if ok and str(sc.get("resultCode", "0")) == "0" and not scenes:
        state["fail_node"] = "test"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": "E11", "next_action": "FIX_PLAN",
               "messages": messages + ["该销售品未匹配到测试场景，请检查配置"]}, 1)
    state["test_global_id"] = global_id
    save_state(workdir, state)
    return global_id


def _test_poll_once(cpcp_mod, global_id):
    """单次进度查询（秒级，进程内直调 _http；import 失败回退子进程）。返回 (done, failed, info)。"""
    if cpcp_mod is not None:
        try:
            resp = cpcp_mod._http("POST", "/api/v1/appstore/test/offer/progress",
                                  {"globalId": global_id},
                                  timeout=cpcp_mod.TIMEOUT_ASYNC, retries=0)
        except Exception:
            resp = None
    else:
        ok, resp = _api(["test_progress", "--global-id", global_id])
        resp = resp if ok else None
    if not resp or resp.get("resultCode") not in (None, "0", 0):
        return False, True, {"fail_reason": "查询失败"}
    done = str(resp.get("done")).lower() == "true"
    failed = str(resp.get("failed")).lower() == "true"
    return done, failed, resp


def test_finish(state, workdir, req_id, global_id, messages):
    """环节4 第三阶段：done 后取完整结果 + E26 预校验 + 判定 + 报告下载（秒级）。"""
    ok, out = _api(["test_result", "--global-id", global_id])
    result_file = os.path.join(workdir, "result_test_%s.json" % req_id)
    _write_json(result_file, out)
    _save_node(req_id, "test", result_file)
    # E26 被测一致性预校验（offerName/offerId 与环节1 一致性）
    plan = _read_json(os.path.join(workdir, "plan_json_%s.json" % req_id))
    plan_name = _plan_offer_name(plan)
    tr_name = str(out.get("offerName", ""))
    e26 = bool(plan_name and tr_name and plan_name != tr_name and plan_name not in tr_name)
    if e26:
        state["fail_node"] = "test"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": "E26", "next_action": "CHECK_PLATFORM",
               "result_file": result_file, "globalId": global_id,
               "messages": messages + ["被测一致性预校验失败：测试平台返回 %s 与被测配置 %s 不一致"
                                       % (tr_name, plan_name)]}, 1)
    okj, ecode = judge("test", out)
    if not okj:
        state["fail_node"] = "test"
        save_state(workdir, state)
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": ecode, "next_action": "RESUME", "result_file": result_file,
               "globalId": global_id,
               "messages": messages + ["环节4 自动测试未通过（测点/场景明细见出参工件）"]}, 1)
    state["fail_node"] = None
    save_state(workdir, state)
    rec = {"node": "test", "status": "SUCCESS", "result_file": result_file,
           "globalId": global_id, "summary": summary_of("test", out)}
    # 报告下载（不中断主干，E28 提示型保留项）
    save_path = os.path.join(workdir, "test_report_%s.md" % global_id)
    dok, dout = _api(["download_test_report", "--global-id", global_id, "--save-path", save_path])
    rec["report_download"] = dout if dok else {"resultCode": "SKIP", "resultMsg": "报告下载失败，链接仍可用"}
    return rec


def step_test(state, workdir, req_id, offer_id, messages):
    """--stage all 兼容路径：发起→内部整段轮询→结果判定（单次调用内串行跑完）。"""
    global_id = test_start(state, workdir, req_id, offer_id, messages)
    # 轮询（poll_test_progress.py，5s 循环、连续 2 次失败转人工、30 分钟超时）
    # 单进程同步阻塞，仅用于兼容 --stage all；推荐 --stage prepare/poll 分段规避长命令。
    _stage_progress("环节6/9 销售品自动测试：已发起，正在轮询测试进度（globalId %s）" % global_id)
    poll = subprocess.Popen(
        [sys.executable, os.path.join(SCRIPT_DIR, "poll_test_progress.py"),
         "--global-id", global_id, "--max-consecutive-fail", "2"],
        stdout=subprocess.PIPE, stderr=None, text=True, cwd=SCRIPT_DIR,
        encoding="utf-8", errors="replace")
    poll_stdout, _ = poll.communicate(timeout=2100)
    try:
        poll_out = json.loads((poll_stdout or "{}").strip() or "{}")
    except json.JSONDecodeError:
        poll_out = {"done": False, "failed": True, "fail_reason": "轮询输出解析失败"}
    if not poll_out.get("done"):
        state["fail_node"] = "test"
        save_state(workdir, state)
        ecode = "E13" if "超时" in str(poll_out.get("fail_reason", "")) else "E12"
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": ecode, "next_action": "RESUME", "globalId": global_id,
               "messages": messages + [str(poll_out.get("fail_reason", "轮询未完成"))]}, 1)
    return test_finish(state, workdir, req_id, global_id, messages)


def _plan_offer_name(plan):
    fields = []
    if isinstance(plan.get("main_offer"), dict):
        fields = plan["main_offer"].get("fields") or []
    else:
        fields = plan.get("fields") or []
    for f in fields:
        if isinstance(f, dict) and f.get("field") in ("产品名称", "套餐名称", "销售品名称") and f.get("value"):
            return str(f.get("value"))
    return ""


def main():
    _force_utf8_stdio()
    p = argparse.ArgumentParser(description="执行主干确定性状态机（config→spec→fee→test）")
    p.add_argument("--req-id", required=True)
    p.add_argument("--workdir", required=True, help="会话可写目录（工件与状态落盘）")
    p.add_argument("--confirmed", action="store_true",
                   help="用户已明确回复确认类语句后由模型传入（确认门禁兜底）")
    p.add_argument("--resume", action="store_true", help="从失败环节续跑（已成功环节回放）")
    p.add_argument("--fail-node", default="", help="STAGE1_CONFIG~STAGE4_TEST（续跑映射）")
    p.add_argument("--operator", default="")
    p.add_argument("--stage", default="all",
                   choices=["all", "prepare", "poll"],
                   help="执行分段（V12.3，规避长命令被工具转后台）："
                        "all=一次跑完四环节（默认/兼容）；"
                        "prepare=跑 config→spec→fee→发起测试后立即返回 next_action=POLL；"
                        "poll=单次查询测试进度，未完成返回 POLL，完成则续跑结果判定后返回 APPROVAL_GATE")
    args = p.parse_args()

    if not args.confirmed:
        _emit({"resultCode": "NOT_CONFIRMED", "e_code": "E3",
               "resultMsg": "未识别到用户确认类回复，禁止执行智能配置（确认门禁）"}, 2)

    req_id, workdir = args.req_id, os.path.abspath(args.workdir)
    os.makedirs(workdir, exist_ok=True)
    state = load_state(workdir, req_id)

    # --stage poll：单次查询测试进度（秒级返回，规避长命令被工具转后台）。
    # done=false → 返回 next_action=POLL 让模型再调一次；done=true → 续跑结果判定+报告。
    if args.stage == "poll":
        global_id = state.get("test_global_id") or ""
        if not global_id:
            _emit({"resultCode": "PARAM_MISSING", "e_code": "E18",
                   "resultMsg": "缺少 test_global_id（请先执行 --stage prepare）"}, 2)
        done, failed, info = _test_poll_once(_cpcp_api, global_id)
        if failed:
            state["fail_node"] = "test"
            save_state(workdir, state)
            _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
                   "e_code": "E12", "next_action": "RESUME", "globalId": global_id,
                   "messages": [str(info.get("fail_reason", "测试进度查询失败"))]}, 1)
        if not done:
            _emit({"resultCode": "0", "req_id": req_id, "fail_node": None,
                   "next_action": "POLL", "globalId": global_id,
                   "progress": {"done": False,
                                "doneCount": info.get("doneCount"),
                                "testCaseCount": info.get("testCaseCount")},
                   "messages": ["测试进行中，请稍后再次执行 --stage poll"]}, 0)
        rec = test_finish(state, workdir, req_id, global_id, [])
        state["nodes"]["test"] = rec
        save_state(workdir, state)
        _emit({"resultCode": "0", "req_id": req_id, "fail_node": None,
               "next_action": "APPROVAL_GATE",
               "nodes": [{"node": n, "status": node_record(state, n).get("status"),
                          "result_file": node_record(state, n).get("result_file"),
                          "summary": node_record(state, n).get("summary")} for n in NODES],
               "messages": ["四环节串行执行全部成功，等待用户明确发起上线审批"]}, 0)

    # 续跑：定位起始环节，已成功环节标记 REPLAYED（不重复调用写接口）
    start = 0
    if args.resume:
        if args.fail_node:
            fn = FAIL_NODE_MAP.get(args.fail_node)
            if not fn:
                _emit({"resultCode": "PARAM_MISSING", "e_code": "E18",
                       "resultMsg": "fail_node 非法：%s" % args.fail_node}, 2)
            start = NODES.index(fn)
        elif state.get("fail_node"):
            start = NODES.index(state["fail_node"])
    else:
        # 非续跑且本机已有成功状态 → 幂等回放，防重复写
        if all(node_record(state, n).get("status") in ("SUCCESS", "PARTIAL", "REPLAYED")
               for n in NODES):
            for n in NODES:
                state["nodes"][n]["status"] = "REPLAYED"
            save_state(workdir, state)
            _emit({"resultCode": "0", "req_id": req_id, "fail_node": None,
                   "next_action": "APPROVAL_GATE",
                   "nodes": [{"node": n, "status": "REPLAYED",
                              "result_file": state["nodes"][n].get("result_file"),
                              "summary": state["nodes"][n].get("summary")} for n in NODES],
                   "messages": ["四环节已全部成功（回放存储记录，未重复调用写接口）"]}, 0)

    executed, messages = [], []
    offer_id = node_record(state, "config").get("summary", {}).get("offer_id") or ""
    for i, node in enumerate(NODES):
        if i < start:
            rec = node_record(state, node)
            if rec.get("status") in ("SUCCESS", "PARTIAL"):
                state["nodes"][node]["status"] = "REPLAYED"
                executed.append({"node": node, "status": "REPLAYED",
                                 "result_file": rec.get("result_file"),
                                 "summary": rec.get("summary")})
                continue
            # 状态缺失却要求续跑到该环节之后 → 状态不一致（本地状态可能被清理），
            # 按存储记录（save_node_result 落库）回放补救，仍无记录才按 E18 从头执行
            ok, q = _api(["query_node_result", "--req-id", req_id, "--node", node])
            stored = q if isinstance(q, dict) else {}
            lst = stored.get("list") or []
            total = stored.get("total", 0)
            if ok and str(total) not in ("0", "") and lst:
                rec = {"node": node, "status": "REPLAYED",
                       "result_file": os.path.join(workdir, "result_%s_%s.json" % (node, req_id)),
                       "summary": {"replayed_from_storage": True,
                                   "result_json": lst[0].get("result_json", "")}}
                state["nodes"][node] = rec
                executed.append(rec)
                continue
            state["fail_node"] = None
            save_state(workdir, state)
            start = 0  # E18：无任何可回放记录，按首次执行处理（E18 恢复方式）
            break
        if node == "config":
            rec = step_config(state, workdir, req_id, args.operator)
        else:
            if node == "spec":
                offer_id = executed[0]["summary"].get("offer_id") or offer_id
            if not offer_id:
                _emit({"resultCode": "PARAM_MISSING", "e_code": "E18",
                       "resultMsg": "缺少 offer_id（环节1 出参），禁止跳步调用 %s" % node}, 2)
            if node == "spec":
                rec = step_spec(state, workdir, req_id, offer_id, messages)
            elif node == "fee":
                rec = step_fee(state, workdir, req_id, messages)
            elif args.stage == "prepare":
                # 分段模式：发起测试后立即返回，交模型循环 --stage poll（防长命令被转后台）
                global_id = test_start(state, workdir, req_id, offer_id, messages)
                state["nodes"]["test"] = {"status": "PENDING", "globalId": global_id}
                save_state(workdir, state)
                _emit({"resultCode": "0", "req_id": req_id, "fail_node": None,
                       "next_action": "POLL", "globalId": global_id,
                       "nodes": [{"node": n["node"], "status": n["status"],
                                  "result_file": n.get("result_file"), "summary": n.get("summary")}
                                 for n in executed],
                       "messages": ["环节3/4/5 已完成，测试已发起，请执行 --stage poll 查询进度"]}, 0)
            else:
                rec = step_test(state, workdir, req_id, offer_id, messages)
        executed.append(rec)
        state["nodes"][node] = rec
        if node == "config":
            offer_id = rec["summary"].get("offer_id") or ""

    save_state(workdir, state)
    _emit({"resultCode": "0", "req_id": req_id, "fail_node": None,
           "next_action": "APPROVAL_GATE",
           "nodes": [{"node": n["node"], "status": n["status"],
                      "result_file": n.get("result_file"), "summary": n.get("summary")}
                     for n in executed],
           "messages": ["四环节串行执行全部成功，等待用户明确发起上线审批"]}, 0)


if __name__ == "__main__":
    main()
