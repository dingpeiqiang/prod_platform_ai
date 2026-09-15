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
    # V2.9：remark_excluded 项从 fields_json 中剔除（该值已被用户备注声明非本字段语义，
    # 不应进入执行方案；剔除后由上游决定是否另立字段/仅入 need_summary）
    cleaned = [f for f in reason_fields
               if not any(x.get("field") == f.get("field") and x.get("action") == "remark_excluded"
                          for x in (data.get("fixed") or []) if isinstance(x, dict))]
    if len(cleaned) != len(reason_fields):
        data["fields_json"] = json.dumps(cleaned, ensure_ascii=False)
        data["remark_excluded_fields"] = [f.get("field") for f in reason_fields if f not in cleaned]
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

# K3 用例设计规范 V2.0 第4章：三大验证 31 条固定用例判定依据（出参映射），脚本化消除模型语义推断
# 测点/场景/比对数据的取值来源：test_result（tr）/ spec_audit（sp）/ billing_verify（fee）出参
def _scene_of(tr, nbr):
    for s in (tr.get("testScenes") or []):
        if s.get("testSceneNbr") == nbr:
            return s
    return None


def _point_ok(tr, nbr, point_nbr):
    scene = _scene_of(tr, nbr)
    if not scene:
        return None
    for p in (scene.get("testCasePointResults") or []):
        if p.get("testPointNbr") == point_nbr:
            return p.get("resultCode") == "0"
    return None


def _scene_pass(tr, nbr):
    scene = _scene_of(tr, nbr)
    if not scene:
        return None
    return scene.get("successTestCaseCount") == scene.get("testCaseCount")


def _compare_ok(fee, project):
    for c in (fee.get("compare_list") or []):
        if c.get("project_name") == project:
            return c.get("result") == "一致"
    return None


FIXED_CASES = [
    # (用例ID, 用例名称, 等级, 维度, 判定函数(tr, sp, fee) -> True/False/None(未覆盖))
    ("ACC-001", "销售品基础准入规则校验", "P0", "ACC", lambda tr, sp, fee: _scene_pass(tr, "S_O_TC")),
    ("ACC-002", "产品互斥规则校验", "P0", "ACC", lambda tr, sp, fee: _point_ok(tr, "S_O_TC", "P_MUTEX_REL")),
    ("ACC-003", "产品依赖规则校验", "P0", "ACC", lambda tr, sp, fee: _point_ok(tr, "S_O_TC", "P_RELY_REL")),
    ("ACC-004", "订购操作能力校验", "P0", "ACC", lambda tr, sp, fee: (
        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(
            _scene_pass(tr, "S_O_TC"), _point_ok(tr, "S_O_TC", "P_STATUS")))),
    ("ACC-005", "变更操作能力校验", "P1", "ACC", lambda tr, sp, fee: None),
    ("ACC-006", "退订操作能力校验", "P0", "ACC", lambda tr, sp, fee: _scene_pass(tr, "S_U_TC")),
    ("ACC-007", "受理表单必填字段完整性", "P0", "ACC", lambda tr, sp, fee: (
        None if None in (_point_ok(tr, "S_O_TC", "P_OFFER_NAME"), _point_ok(tr, "S_O_TC", "P_OFFER_TYPE"),
                         _point_ok(tr, "S_O_TC", "P_PAY_MODE"))
        else all((_point_ok(tr, "S_O_TC", p) for p in ("P_OFFER_NAME", "P_OFFER_TYPE", "P_PAY_MODE"))))),
    ("ACC-008", "限购数量规则校验", "P1", "ACC", lambda tr, sp, fee: _point_ok(tr, "S_O_TC", "P_ORD_CNT")),
    ("ACC-009", "地域受理范围校验", "P1", "ACC", lambda tr, sp, fee: None),
    ("ACC-010", "受理时段生效校验", "P1", "ACC", lambda tr, sp, fee: _point_ok(tr, "S_O_TC", "P_EFF_DATE")),
    ("ACC-011", "模拟订购接口预测试", "P0", "ACC", lambda tr, sp, fee: (
        True if tr.get("orderId") and tr.get("offerInstId") else
        (None if not any((tr.get("testScenes") or [])) else False))),
    ("ACC-012", "模拟退订接口预测试", "P0", "ACC", lambda tr, sp, fee: (
        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(
            _scene_pass(tr, "S_U_TC"), _point_ok(tr, "S_U_TC", "P_STATUS")))),
    ("BILL-001", "基础资费金额合法性校验", "P0", "BILL", lambda tr, sp, fee: _compare_ok(fee, "套餐月租")),
    ("BILL-002", "计费周期类型校验", "P0", "BILL", lambda tr, sp, fee: (
        None if fee is None or not fee.get("compare_list") or not tr.get("plan_billing_cycle")
        else tr.get("plan_billing_cycle") == "自然月")),
    ("BILL-003", "计费起算时间规则校验", "P0", "BILL", lambda tr, sp, fee: (
        None if fee is None or not fee.get("compare_list") else all(
            c.get("result") == "一致" for c in fee["compare_list"] if c.get("project_name") == "套餐月租"))),
    ("BILL-004", "资源扣减规则校验", "P0", "BILL", lambda tr, sp, fee: (
        None if fee is None or not fee.get("compare_list") else (lambda rs: None if None in rs else all(rs))(
            [_compare_ok(fee, p) for p in ("流量赠送量", "语音赠送量", "短信赠送量")]))),
    ("BILL-005", "阶梯/按量批价规则校验", "P1", "BILL", lambda tr, sp, fee: (
        None if fee is None or not fee.get("compare_list") else (lambda rs: None if None in rs else all(rs))(
            [_compare_ok(fee, p) for p in ("流量超出资费", "语音超出资费", "短信超出资费")]))),
    ("BILL-006", "优惠叠加/捆绑减免校验", "P1", "BILL", lambda tr, sp, fee: (
        None if fee is None or "risk_list" not in fee else not fee.get("risk_list"))),
    ("BILL-007", "账单展示项配置校验", "P1", "BILL", lambda tr, sp, fee: None),
    ("BILL-008", "模拟订购账单试算", "P0", "BILL", lambda tr, sp, fee: None),
    ("BILL-009", "退订费用结算试算", "P1", "BILL", lambda tr, sp, fee: None),
    ("BILL-010", "资费生效失效联动校验", "P0", "BILL", lambda tr, sp, fee: (
        None if None in (_point_ok(tr, "S_O_TC", "P_EFF_DATE"), _point_ok(tr, "S_O_TC", "P_EXP_DATE"))
        else (_point_ok(tr, "S_O_TC", "P_EFF_DATE") and _point_ok(tr, "S_O_TC", "P_EXP_DATE")))),
    ("CUST-001", "客服产品基础视图完整性", "P0", "CUST", lambda tr, sp, fee: (
        None if None in (_point_ok(tr, "S_O_TC", "P_OFFER_NAME"), _point_ok(tr, "S_O_TC", "P_OFFER_TYPE"))
        else (_point_ok(tr, "S_O_TC", "P_OFFER_NAME") and _point_ok(tr, "S_O_TC", "P_OFFER_TYPE")))),
    ("CUST-002", "客户订单查询能力校验", "P0", "CUST", lambda tr, sp, fee: (
        True if tr.get("offerInstId") else
        (None if not any((tr.get("testScenes") or [])) else False))),
    ("CUST-003", "客服侧产品操作权限校验", "P1", "CUST", lambda tr, sp, fee: None),
    ("CUST-004", "产品资费对外说明话术校验", "P0", "CUST", lambda tr, sp, fee: (
        None if fee is None or not fee.get("compare_list") else (lambda rs: None if None in rs else all(rs))(
            [c.get("result") == "一致" for c in fee["compare_list"]]))),
    ("CUST-005", "产品生效失效规则话术校验", "P1", "CUST", lambda tr, sp, fee: (
        None if None in (_point_ok(tr, "S_O_TC", "P_EFF_DATE"), _point_ok(tr, "S_O_TC", "P_EXP_DATE"))
        else (_point_ok(tr, "S_O_TC", "P_EFF_DATE") and _point_ok(tr, "S_O_TC", "P_EXP_DATE")))),
    ("CUST-006", "产品退订规则话术校验", "P1", "CUST", lambda tr, sp, fee: _scene_pass(tr, "S_U_TC")),
    ("CUST-007", "产品限制规则话术校验", "P1", "CUST", lambda tr, sp, fee: (
        None if None in (_point_ok(tr, "S_O_TC", "P_MUTEX_REL"), _point_ok(tr, "S_O_TC", "P_RELY_REL"),
                         _point_ok(tr, "S_O_TC", "P_ORD_CNT"))
        else all((_point_ok(tr, "S_O_TC", p) for p in ("P_MUTEX_REL", "P_RELY_REL", "P_ORD_CNT"))))),
    ("CUST-008", "对外展示信息合规校验", "P0", "CUST", lambda tr, sp, fee: (
        None if sp is None or "error_list" not in sp else not sp.get("error_list"))),
    ("CUST-009", "客服常见问题FAQ完备性", "P1", "CUST", lambda tr, sp, fee: None),
]


def cmd_map_fixed_cases(args):
    """31 条固定用例逐条映射（K3 规范第4章判定依据），输出用例级结论供环节4 直接引用。
    V2.9：test_result 出参已含后端确定性生成的 testCases[]（31 条用例级结论），
    存在时优先逐字引用，脚本侧判定仅作兼容回退（旧版后端无 testCases 时）。"""
    tr_raw = _read_arg(args, "test_result", "_file")
    sp_raw = _read_arg(args, "spec_result", "_file")
    fee_raw = _read_arg(args, "fee_result", "_file")
    if not tr_raw:
        _err("PARAM_MISSING", "缺少 test_result 出参 JSON（--test-result / --test-result-file）")
    try:
        tr = json.loads(tr_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "test_result 出参不是合法 JSON")
    sp = None
    if sp_raw:
        try:
            sp = json.loads(sp_raw)
        except json.JSONDecodeError:
            _err("PARSE_ERROR", "spec_audit 出参不是合法 JSON")
    fee = None
    if fee_raw:
        try:
            fee = json.loads(fee_raw)
        except json.JSONDecodeError:
            _err("PARSE_ERROR", "billing_verify 出参不是合法 JSON")
    # 优先：后端 testCases[] 原样透出（后端为用例级结论唯一事实源）
    server_cases = tr.get("testCases")
    if isinstance(server_cases, list) and server_cases:
        rows = [{"caseId": c.get("caseId"), "caseName": c.get("caseName"),
                 "level": c.get("level"), "dimension": str(c.get("caseId", "")).split("-")[0],
                 "result": c.get("result")} for c in server_cases]
        counts = {"ACC": [0, 0], "BILL": [0, 0], "CUST": [0, 0]}
        for r in rows:
            dim = r["dimension"]
            if dim in counts and r["result"] in ("✅", "❌"):
                counts[dim][0 if r["result"] == "✅" else 1] += 1
        conclusion = tr.get("overallConclusion") or _conclude(rows, fee)
        print(json.dumps({"resultCode": "0", "resultMsg": "success（数据源=后端 testCases 出参）",
                          "cases": rows,
                          "dimensionSummary": {d: {"pass": v[0], "fail": v[1]} for d, v in counts.items()},
                          "overallConclusion": conclusion}, ensure_ascii=False))
        return
    # 兼容回退：本地映射（旧版后端出参无 testCases）
    rows, counts = [], {"ACC": [0, 0], "BILL": [0, 0], "CUST": [0, 0]}
    for case_id, name, level, dim, judge in FIXED_CASES:
        verdict = judge(tr, sp, fee)
        if verdict is True:
            result = "✅"
        elif verdict is False:
            result = "❌"
        else:
            result = "本销售品未覆盖"
        if result in ("✅", "❌"):
            counts[dim][0 if result == "✅" else 1] += 1
        rows.append({"caseId": case_id, "caseName": name, "level": level, "dimension": dim, "result": result})
    # 整体上线结论（K3 规范第6章，判定唯一依据=本脚本映射结果）
    print(json.dumps({"resultCode": "0", "resultMsg": "success", "cases": rows,
                      "dimensionSummary": {d: {"pass": v[0], "fail": v[1]} for d, v in counts.items()},
                      "overallConclusion": _conclude(rows, fee)}, ensure_ascii=False))


def _conclude(rows, fee):
    p0_fail = any(r["level"] == "P0" and r["result"] == "❌" for r in rows)
    p1_fail = any(r["level"] == "P1" and r["result"] == "❌" for r in rows)
    risk_nonempty = bool(fee and fee.get("risk_list"))
    if p0_fail:
        return "❌ 禁止上线"
    if p1_fail or risk_nonempty:
        return "⚠️ 评估风险后上线"
    return "✅ 建议上线"

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
    s = sub.add_parser("map_fixed_cases")
    s.add_argument("--test-result"); s.add_argument("--test-result-file")
    s.add_argument("--spec-result"); s.add_argument("--spec-result-file")
    s.add_argument("--fee-result"); s.add_argument("--fee-result-file")
    s.set_defaults(fn=cmd_map_fixed_cases)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
