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
"""
import argparse
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


def _api(args_list):
    """调用 cpcp_api.py 子命令，返回 (ok, out_dict)。出参恒为 dict（解析失败也归一）。"""
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
    _api(["save_node_result", "--req-id", req_id, "--node", node,
          "--result-json-file", result_file])


def step_spec(state, workdir, req_id, offer_id, messages):
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


def step_test(state, workdir, req_id, offer_id, messages):
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
    # 轮询（poll_test_progress.py 长驻命令，5s 循环、连续 2 次失败转人工、30 分钟超时）
    poll = subprocess.run(
        [sys.executable, os.path.join(SCRIPT_DIR, "poll_test_progress.py"),
         "--global-id", global_id, "--max-consecutive-fail", "2"],
        capture_output=True, text=True, timeout=2000, cwd=SCRIPT_DIR,
        encoding="utf-8", errors="replace")
    try:
        poll_out = json.loads((poll.stdout or "{}").strip() or "{}")
    except json.JSONDecodeError:
        poll_out = {"done": False, "failed": True, "fail_reason": "轮询输出解析失败"}
    if not poll_out.get("done"):
        state["fail_node"] = "test"
        save_state(workdir, state)
        ecode = "E13" if "超时" in str(poll_out.get("fail_reason", "")) else "E12"
        _emit({"resultCode": "VALIDATE_FAIL", "req_id": req_id, "fail_node": "test",
               "e_code": ecode, "next_action": "RESUME", "globalId": global_id,
               "messages": messages + [str(poll_out.get("fail_reason", "轮询未完成"))]}, 1)
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
    args = p.parse_args()

    if not args.confirmed:
        _emit({"resultCode": "NOT_CONFIRMED", "e_code": "E3",
               "resultMsg": "未识别到用户确认类回复，禁止执行智能配置（确认门禁）"}, 2)

    req_id, workdir = args.req_id, os.path.abspath(args.workdir)
    os.makedirs(workdir, exist_ok=True)
    state = load_state(workdir, req_id)

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
