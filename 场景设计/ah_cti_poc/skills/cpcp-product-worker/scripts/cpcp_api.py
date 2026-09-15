#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""产销品数字员工统一 API 客户端（Skills 技能包版）。

封装原自研插件集 V1.6 的 14 个工具调用（含超时重试、错误码归一），
子命令与《skills/references/tools-contract.md》契约一一对应。

用法示例：
  python cpcp_api.py similar_offer --desc "5G-A 单品套餐 月费199元 30G流量"
  python cpcp_api.py save_node_result --req-id PLAN20260913143025087 --node requirement --result-json-file plan.json
  python cpcp_api.py build_plan --fields-json-file fields.json
"""
import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

BASE_URL = os.environ.get("CPCP_BASE_URL", "http://10.86.13.201:31281")
TIMEOUT_SYNC = 60
TIMEOUT_ASYNC = 30
# 传输层重试次数（不含首次）：RETRY=2 → 共尝试 3 次，仍失败则判定为 E29 网络异常（终止询问）
RETRY = 2

PLAN_PREFIX = "PLAN"


def _force_utf8_stdio():
    """Windows GBK 控制台编码自愈：中文路径/中文报文场景下 stdout/stderr
    默认 GBK 编码会触发 UnicodeEncodeError（静默丢输出）。
    强制重绑为 UTF-8（errors=replace 兜底），保证出参 JSON 永远可打印。"""
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


_force_utf8_stdio()

# 分类 → 模块归并（V3.0 新 24 字段，分类名与后端 FieldOntologyService 注册表一致）
CATEGORY_MODULE = {
    "产品属性": "基础信息", "生命周期": "基础信息", "销售属性": "基础信息",
    "套餐内基础资源": "资源配置", "套餐内权益配置": "资源配置", "套外资费标准": "资源配置",
    "订购与生效": "业务规则", "变更/退订/拆机": "业务规则", "计费/支付/风控": "业务规则",
}

# 来源标注两态归一：原始需求 / AI补全（兼容历史 AI推理/本体推理 标注）
SOURCE_LABEL = {"原始需求": "原始需求", "AI推理": "AI补全", "本体推理": "AI补全", "AI补全": "AI补全"}


def _module_of(category):
    return CATEGORY_MODULE.get(category, category)


def _now(fmt="%Y%m%d%H%M%S"):
    return datetime.now().strftime(fmt)


def _err(code, msg):
    # 错误信息 ASCII 安全降级：即使编码自愈失败也不让错误输出本身抛异常
    try:
        print(json.dumps({"resultCode": code, "resultMsg": msg}, ensure_ascii=False))
    except UnicodeEncodeError:
        safe = msg.encode("ascii", "replace").decode("ascii")
        print(json.dumps({"resultCode": code, "resultMsg": safe}, ensure_ascii=True))
    sys.exit(2)


def _http(method, path, payload=None, timeout=TIMEOUT_SYNC, retries=RETRY):
    url = BASE_URL.rstrip("/") + path
    if method == "GET" and payload:
        url += "?" + urllib.parse.urlencode(payload)
    body = None
    if method == "POST":
        body = json.dumps(payload or {}, ensure_ascii=False).encode("utf-8")
    last_err = None
    for i in range(retries + 1):
        try:
            req = urllib.request.Request(url, data=body, method=method,
                                         headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            return {"resultCode": "HTTP_%d" % e.code, "resultMsg": str(e.reason)}
        except (urllib.error.URLError, TimeoutError, socket_timeout()) as e:
            last_err = e
            if i >= retries:
                return {"resultCode": "NET_ERROR", "resultMsg": str(e)}
            time.sleep(1)
    return {"resultCode": "NET_ERROR", "resultMsg": str(last_err)}


def socket_timeout():
    import socket
    return socket.timeout


def _read_arg(args, name, file_suffix):
    """支持 --xxx 内联或 --xxx-file 文件读取（大报文推荐文件方式）。"""
    inline = getattr(args, name, None)
    fpath = getattr(args, name + "_file", None)
    if fpath:
        with open(fpath, "r", encoding="utf-8-sig") as f:
            return f.read()
    return inline


# ---------------- 需求分析与稽核类 ----------------

def cmd_similar_offer(args):
    if not args.desc:
        _err("PARAM_MISSING", "缺少业务需求描述，请提供需求原文或需求文档摘要")
    out = _http("POST", "/api/v1/appstore/similar/offer/query",
                {"businessDesc": args.desc[:5000]})
    print(json.dumps(out_result(out := _unwrap(out)), ensure_ascii=False))


def cmd_spec_audit(args):
    config_json = _read_arg(args, "config_json", "_json_file")
    if not args.offer_id or not config_json:
        _err("PARAM_MISSING", "缺少销售品ID或落地配置JSON，请先完成配置落地")
    out = _http("POST", "/api/v1/appstore/audit/realtime",
                {"offer_id": args.offer_id, "config_json": config_json,
                 "audit_scene": args.audit_scene or "all"})
    print(json.dumps(out, ensure_ascii=False))


def cmd_ontology_reason(args):
    fields_raw = _read_arg(args, "fields_json", "_json_file")
    if not fields_raw:
        _err("PARAM_MISSING", "缺少待推理字段数组 fields_json")
    try:
        fields = json.loads(fields_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "fields_json 不是合法 JSON，请检查内容或改用 --fields-json-file")
    if isinstance(fields, dict):
        fields = fields.get("fields", [])
    if not isinstance(fields, list) or not fields:
        _err("PARAM_MISSING", "待推理字段数组为空，请先完成需求要素提取（18 字段）")
    out = _http("POST", "/api/v1/appstore/ontology/fields",
                {"action": "reason", "fields": fields})
    data = _unwrap(out)
    reason_fields = None
    if isinstance(data, dict):
        raw = data.get("fields_json")
        if isinstance(raw, str):
            try:
                reason_fields = json.loads(raw)
            except json.JSONDecodeError:
                reason_fields = None
        elif isinstance(raw, list):
            reason_fields = raw
    if not reason_fields:
        _err("ONTOLOGY_EMPTY",
             "字段本体推理引擎返回空结果（fields_json 为空），无法作为方案唯一数据源；"
             "请检查后端 FieldOntologyService 实现或重试；禁止跳过本步骤直接组装方案")
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 自动测试类 ----------------

def cmd_offer_test(args):
    if not args.offer_id:
        _err("PARAM_MISSING", "缺少销售品ID，请提供被测销售品ID")
    out = _http("POST", "/api/v1/appstore/test/offer/start",
                {"offerId": args.offer_id}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


def cmd_test_scenes(args):
    if not args.global_id:
        _err("PARAM_MISSING", "缺少测试流水号，请先发起测试")
    out = _http("POST", "/api/v1/appstore/test/offer/scenes",
                {"globalId": args.global_id}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


def cmd_test_progress(args):
    if not args.global_id:
        _err("PARAM_MISSING", "缺少测试流水号，请先发起测试")
    out = _http("POST", "/api/v1/appstore/test/offer/progress",
                {"globalId": args.global_id}, timeout=TIMEOUT_ASYNC, retries=0)
    print(json.dumps(out, ensure_ascii=False))


def cmd_test_result(args):
    if not args.global_id:
        _err("PARAM_MISSING", "缺少测试流水号，请先发起测试")
    out = _http("POST", "/api/v1/appstore/test/offer/result",
                {"globalId": args.global_id}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 资费/审批/运维类 ----------------

def cmd_save_product_config(args):
    plan_json = _read_arg(args, "plan_json", "_json_file")
    if not args.req_id or not plan_json:
        _err("PARAM_MISSING", "缺少执行方案key或执行方案JSON，请先完成需求分析并确认")
    out = _http("POST", "/api/v1/appstore/product/config/save",
                {"req_id": args.req_id, "plan_json": plan_json,
                 "operator": args.operator or "", "confirmed": True})
    print(json.dumps(out, ensure_ascii=False))


def cmd_billing_verify(args):
    config_json = _read_arg(args, "config_json", "_json_file")
    if not config_json:
        _err("PARAM_MISSING", "缺少落地配置JSON，请先完成配置落地")
    out = _http("POST", "/api/v1/appstore/billing/rules/verify",
                {"config_json": config_json, "check_scene": args.check_scene or "all"})
    print(json.dumps(out, ensure_ascii=False))


def cmd_submit_approval(args):
    report = _read_arg(args, "report_url", "_file")
    if not args.req_id or not args.product_id or not report:
        _err("PARAM_MISSING", "缺少执行方案key/产品ID/上线报告，请先完成执行主干")
    out = _http("POST", "/api/v1/appstore/approval/submit",
                {"req_id": args.req_id, "product_id": args.product_id,
                 "report_url": report, "approval_flow": args.approval_flow or "standard",
                 "approve_confirmed": True},
                timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


def cmd_query_monitor(args):
    if not args.product_id:
        _err("PARAM_MISSING", "缺少产品ID，请提供要查询的销售品")
    out = _http("GET", "/api/v1/appstore/product/monitor",
                {"product_id": args.product_id, "date_range": args.date_range or "",
                 "metric": args.metric or "all"}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


def cmd_send_alert(args):
    if not args.product_id or not args.alarm_level or not args.content:
        _err("PARAM_MISSING", "缺少产品ID/告警级别/告警内容")
    if args.alarm_level not in ("high", "middle", "low"):
        _err("PARAM_MISSING", "告警级别枚举非法（high/middle/low）")
    out = _http("POST", "/api/v1/appstore/alert/send",
                {"product_id": args.product_id, "alarm_level": args.alarm_level,
                 "content": args.content}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


def cmd_download_launch_script(args):
    if not args.product_id:
        _err("PARAM_MISSING", "缺少产品ID，请先完成配置落地并取出参 product_id")
    save_path = args.save_path or ("launch_%s.sql" % args.product_id)
    # 复用 _http 会把响应体按 JSON 解析，脚本为 text/plain，这里独立发起下载
    url = BASE_URL.rstrip("/") + "/api/v1/appstore/product/config/script?" + \
        urllib.parse.urlencode({"product_id": args.product_id})
    last_err = None
    for i in range(RETRY + 1):
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=TIMEOUT_SYNC) as resp:
                if resp.status != 200:
                    _err("HTTP_%d" % resp.status, "脚本下载失败，请确认 product_id 已完成配置落地")
                content = resp.read()
            with open(save_path, "wb") as f:
                f.write(content)
            print(json.dumps({"resultCode": "0", "resultMsg": "success",
                              "saved_path": os.path.abspath(save_path),
                              "file_size": len(content)}, ensure_ascii=False))
            return
        except urllib.error.HTTPError as e:
            detail = e.reason or ("未落地" if e.code == 404 else "HTTP %d" % e.code)
            _err("HTTP_%d" % e.code, "脚本下载失败（%s），请确认 product_id 已完成配置落地" % detail)
        except (urllib.error.URLError, TimeoutError, socket_timeout()) as e:
            last_err = e
            if i >= RETRY:
                _err("NET_ERROR", "脚本下载网络异常：%s" % e)
            time.sleep(1)


def cmd_download_test_report(args):
    if not args.global_id:
        _err("PARAM_MISSING", "缺少测试流水号，请先完成自动测试（环节4）并取出参 globalId")
    save_path = args.save_path or ("test_report_%s.md" % args.global_id)
    # 响应体为 text/markdown，不能按 JSON 解析，独立发起下载（与 download_launch_script 同模式）
    url = BASE_URL.rstrip("/") + "/api/v1/appstore/test/offer/report?" + \
        urllib.parse.urlencode({"global_id": args.global_id})
    last_err = None
    for i in range(RETRY + 1):
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=TIMEOUT_SYNC) as resp:
                if resp.status != 200:
                    _err("HTTP_%d" % resp.status, "测试报告下载失败，请确认 globalId 对应测试已完成（test_result 已回传 report_url）")
                content = resp.read()
            with open(save_path, "wb") as f:
                f.write(content)
            print(json.dumps({"resultCode": "0", "resultMsg": "success",
                              "saved_path": os.path.abspath(save_path),
                              "file_size": len(content)}, ensure_ascii=False))
            return
        except urllib.error.HTTPError as e:
            detail = e.reason or ("报告未归档" if e.code == 404 else "HTTP %d" % e.code)
            _err("HTTP_%d" % e.code, "测试报告下载失败（%s），请确认 globalId 对应测试已完成" % detail)
        except (urllib.error.URLError, TimeoutError, socket_timeout()) as e:
            last_err = e
            if i >= RETRY:
                _err("NET_ERROR", "测试报告下载网络异常：%s" % e)
            time.sleep(1)


def cmd_approval_status(args):
    if not args.approval_id and not args.product_id:
        _err("PARAM_MISSING", "请提供审批单号或销售品ID，以便查询审批进度")
    params = {}
    if args.approval_id:
        params["approval_id"] = args.approval_id
    if args.product_id:
        params["product_id"] = args.product_id
    out = _http("GET", "/api/v1/appstore/approval/status", params, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 节点结果存储查询 ----------------

def cmd_save_node_result(args):
    result_json = _read_arg(args, "result_json", "_json_file")
    if not args.req_id or not args.node or not result_json:
        _err("PARAM_MISSING", "缺少 req_id/node_name/result_json")
    if len(result_json.encode("utf-8")) > 64 * 1024:
        _err("PARAM_MISSING", "result_json 超 64KB（5004），请压缩后重试")
    out = _http("POST", "/api/v1/appstore/result/save",
                {"req_id": args.req_id, "node_name": args.node,
                 "result_json": result_json, "status": "ok"})
    print(json.dumps(out, ensure_ascii=False))


def cmd_query_node_result(args):
    if not args.req_id:
        _err("PARAM_MISSING", "缺少执行方案key req_id")
    out = _http("GET", "/api/v1/appstore/result/query",
                {"req_id": args.req_id, "node_name": args.node or "",
                 "latest_only": args.latest_only}, timeout=TIMEOUT_ASYNC)
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 本地代码节点逻辑 ----------------

def _unwrap(out):
    """兼容 contractRoot 包裹 / requestObject 包裹 / 裸报文三种返回。"""
    if isinstance(out, dict):
        if "contractRoot" in out:
            svc = out["contractRoot"].get("svcCont", {})
            return svc.get("responseObject", svc.get("response", out))
        if "resultObject" in out:
            merged = dict(out)
            merged.update(out["resultObject"] if isinstance(out["resultObject"], dict) else {})
            return merged
    return out


def out_result(obj):
    return obj


def cmd_extract_record(args):
    """等价原 CODE_EXTRACT_RECORD 代码节点：从 query_node_result 出参提取 list[0].result_json。"""
    raw = _read_arg(args, "query_json", "_json_file")
    if not raw:
        _err("PARAM_MISSING", "缺少 query_node_result 出参 JSON")
    data = json.loads(raw)
    lst = data.get("list") or []
    if not lst:
        _err("PARAM_MISSING", "查无环节结果记录（total=0），请确认 req_id 与 node_name")
    print(json.dumps({"record_json": lst[0].get("result_json", "")}, ensure_ascii=False))


def cmd_build_plan(args):
    """等价原 wf_sub_01 拆分代码节点 004a：req_id 系统生成 + plan_json/plan_md/pending_fields 组装（V3.0 五列表格）。"""
    fields_raw = _read_arg(args, "fields_json", "_json_file")
    if not fields_raw:
        _err("PARAM_MISSING", "缺少推理后字段数组 fields_json")
    try:
        fields = json.loads(fields_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "fields_json 不是合法 JSON，请检查内容或改用 --fields-json-file")
    if isinstance(fields, dict):
        fields = fields.get("fields", [])
    if not isinstance(fields, list) or not fields:
        _err("PARAM_MISSING",
             "推理后字段数组为空（正常应含 24 字段）；请先完成字段本体推理"
             "（ontology_reason），禁止以空字段组装执行方案")
    for f in fields:
        if isinstance(f, dict) and f.get("source") in SOURCE_LABEL:
            f["source"] = SOURCE_LABEL[f["source"]]
    req_id = PLAN_PREFIX + _now() + "%03d" % random.randint(0, 999)
    pending = [f["field"] for f in fields if f.get("value") == "待补充"]
    plan_json = {"req_id": req_id, "fields": fields, "pending_fields": pending}

    lines = ["| 模块 | 分类 | 字段名称 | 字段值 | 备注 |", "| :--- | :--- | :--- | :--- | :--- |"]
    last_module = last_cat = None
    for f in fields:
        category = f.get("category", "")
        module = _module_of(category)
        module_cell = "**%s**" % module if module != last_module else ""
        cat_cell = category if category != last_cat else ""
        last_module, last_cat = module, category
        lines.append("| %s | %s | %s | %s | 【%s】 |" % (
            module_cell, cat_cell, f.get("field", ""), f.get("value", ""), f.get("source", "")))
    plan_md = "\n".join(lines)

    print(json.dumps({"req_id": req_id, "plan_json": json.dumps(plan_json, ensure_ascii=False),
                      "plan_md": plan_md, "pending_fields": pending}, ensure_ascii=False))


# ---------------- CLI ----------------

def main():
    _force_utf8_stdio()
    p = argparse.ArgumentParser(description="产销品数字员工统一 API 客户端")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("similar_offer"); s.add_argument("--desc", required=True); s.set_defaults(fn=cmd_similar_offer)
    s = sub.add_parser("spec_audit")
    s.add_argument("--offer-id", required=True)
    s.add_argument("--config-json"); s.add_argument("--config-json-file")
    s.add_argument("--audit-scene", default="all"); s.set_defaults(fn=cmd_spec_audit)
    s = sub.add_parser("ontology_reason")
    s.add_argument("--fields-json"); s.add_argument("--fields-json-file")
    s.set_defaults(fn=cmd_ontology_reason)
    s = sub.add_parser("offer_test"); s.add_argument("--offer-id", required=True); s.set_defaults(fn=cmd_offer_test)
    s = sub.add_parser("test_scenes"); s.add_argument("--global-id", required=True); s.set_defaults(fn=cmd_test_scenes)
    s = sub.add_parser("test_progress"); s.add_argument("--global-id", required=True); s.set_defaults(fn=cmd_test_progress)
    s = sub.add_parser("test_result"); s.add_argument("--global-id", required=True); s.set_defaults(fn=cmd_test_result)
    s = sub.add_parser("save_product_config")
    s.add_argument("--req-id", required=True)
    s.add_argument("--plan-json"); s.add_argument("--plan-json-file")
    s.add_argument("--operator", default=""); s.set_defaults(fn=cmd_save_product_config)
    s = sub.add_parser("billing_verify")
    s.add_argument("--config-json"); s.add_argument("--config-json-file")
    s.add_argument("--check-scene", default="all"); s.set_defaults(fn=cmd_billing_verify)
    s = sub.add_parser("submit_approval")
    s.add_argument("--req-id", required=True); s.add_argument("--product-id", required=True)
    s.add_argument("--report-url"); s.add_argument("--report-file")
    s.add_argument("--approval-flow", default="standard"); s.set_defaults(fn=cmd_submit_approval)
    s = sub.add_parser("query_monitor")
    s.add_argument("--product-id", required=True); s.add_argument("--date-range", default="")
    s.add_argument("--metric", default="all"); s.set_defaults(fn=cmd_query_monitor)
    s = sub.add_parser("send_alert")
    s.add_argument("--product-id", required=True); s.add_argument("--alarm-level", required=True)
    s.add_argument("--content", required=True); s.set_defaults(fn=cmd_send_alert)
    s = sub.add_parser("download_launch_script")
    s.add_argument("--product-id", required=True)
    s.add_argument("--save-path", default=""); s.set_defaults(fn=cmd_download_launch_script)
    s = sub.add_parser("download_test_report")
    s.add_argument("--global-id", required=True)
    s.add_argument("--save-path", default=""); s.set_defaults(fn=cmd_download_test_report)
    s = sub.add_parser("approval_status")
    s.add_argument("--approval-id", default=""); s.add_argument("--product-id", default="")
    s.set_defaults(fn=cmd_approval_status)
    s = sub.add_parser("save_node_result")
    s.add_argument("--req-id", required=True); s.add_argument("--node", required=True)
    s.add_argument("--result-json"); s.add_argument("--result-json-file")
    s.set_defaults(fn=cmd_save_node_result)
    s = sub.add_parser("query_node_result")
    s.add_argument("--req-id", required=True); s.add_argument("--node", default="")
    s.add_argument("--latest-only", default="1"); s.set_defaults(fn=cmd_query_node_result)
    s = sub.add_parser("extract_record")
    s.add_argument("--query-json"); s.add_argument("--query-json-file")
    s.set_defaults(fn=cmd_extract_record)
    s = sub.add_parser("build_plan")
    s.add_argument("--fields-json"); s.add_argument("--fields-json-file")
    s.set_defaults(fn=cmd_build_plan)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
