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

# 字段 → 分类（V3.0 24 字段注册表）。ontology_reason 出参会剥离 category，
# build_plan 据此确定性回填（否则 plan_md 模块列变空串）。单商品 on 语义。
FIELD_CATEGORY = {
    "套餐名称": "产品属性", "套餐编码": "产品属性", "套餐档位": "产品属性",
    "套餐属性": "产品属性", "计费周期": "产品属性",
    "套餐有效期": "生命周期", "到期处理方式": "生命周期",
    "适用用户": "销售属性", "销售渠道": "销售属性",
    "国内通用流量": "套餐内基础资源", "本地语音": "套餐内基础资源", "短信": "套餐内基础资源",
    "是否允许办理副卡": "套餐内权益配置",
    "套外流量-计费标准": "套外资费标准", "套外语音-国内通话": "套外资费标准",
    "套外短彩信-短/彩信": "套外资费标准",
    "新入网生效方式": "订购与生效", "老用户生效方式": "订购与生效", "过渡期资费规则": "订购与生效",
    "套餐变更范围": "变更/退订/拆机", "变更生效方式": "变更/退订/拆机", "退订规则": "变更/退订/拆机",
    "付费方式": "计费/支付/风控", "支付方式": "计费/支付/风控",
    "流量结转规则": "计费/支付/风控", "断网授权": "计费/支付/风控",
    # V4.0 融合组成员字段（复用分类口径；宽带/天翼高清/副卡/权益包等角色适用）
    "宽带速率": "套餐内基础资源", "宽带月功能费": "套外资费标准",
    "路数": "套餐内基础资源", "月功能费": "套外资费标准", "张数": "套餐内权益配置",
    "套外扣费": "套外资费标准",
}

# V4.0 融合组：成员角色封闭枚举（与 seed_offer_groups.json member_role_enum 一致）
MEMBER_ROLES = {"主卡套餐", "宽带", "天翼高清", "副卡功能费", "权益包", "其他"}
GROUP_ROLE_KEY = "member_role"  # 组结构入参中成员角色键（成员 fields 数组各元素可携带）

# 来源标注两态归一：原始需求 / AI补全（兼容历史 AI推理/本体推理 标注）
SOURCE_LABEL = {"原始需求": "原始需求", "AI推理": "AI补全", "本体推理": "AI补全", "AI补全": "AI补全"}

# V4.0 融合组结构键（fields_json 组结构入参形态：{offer_type, main_offer, member_offers, group_rules?}）
GROUP_KEYS = {"offer_type", "main_offer", "member_offers"}

# 价格类字段判别（套餐档位/各成员月功能费）——禁止从相似产品/跨成员照搬，未提取时留空交引擎兜底
PRICE_FIELD_KEYWORDS = ("档位", "月功能费", "功能费")


def _module_of(category):
    return CATEGORY_MODULE.get(category, category)


def _backfill_category(fields):
    """ontology_reason 出参剥离 category 后，build_plan 据 24 字段注册表确定性回填，
    保证 plan_md 模块/分类列与 plan_json 均携带正确分类（缺失才回填，不覆盖既有值）。"""
    for f in fields:
        if isinstance(f, dict) and not f.get("category"):
            f["category"] = FIELD_CATEGORY.get(f.get("field"), "")
    return fields


def _mark_price_pending(fields):
    """价格类字段（套餐档位/各成员月功能费等）被 ontology_reason 剥离为空的，确定性置"待补充"。
    价格禁止推理纪律延伸到组级：成员价格字段（宽带月功能费/月功能费等）引擎可能不置待补充，
    此处统一归一，保证 build_plan 出口A 待补充判定对单商品与各成员价格字段同样生效。"""
    for f in fields:
        if isinstance(f, dict) and _is_price_field(f.get("field", "")) and not _strip(f.get("value")):
            f["value"] = "待补充"
            if not f.get("source"):
                f["source"] = "AI补全"
    return fields


def _is_group_input(fields):
    """V4.0 融合组结构识别：dict 含 offer_type/main_offer/member_offers 任一组键即组结构；
    其余形态（扁平 fields 数组）= 单商品，行为保持 V2.7 逐字节不变。"""
    return isinstance(fields, dict) and bool(GROUP_KEYS & set(fields.keys()))


def _normalize_group(g):
    """组结构入参归一：main_offer/member_offers 各成员 fields 数组做来源两态归一，
    成员角色缺省补 GROUP_ROLE_KEY（main_offer 缺省=主卡套餐），非法角色按"其他"兜底。"""
    main = g.get("main_offer") if isinstance(g.get("main_offer"), dict) else {}
    members_in = g.get("member_offers") if isinstance(g.get("member_offers"), list) else []
    main_out = {"role": main.get("role") or "主卡套餐", "fields": main.get("fields") or []}
    members_out = []
    for m in members_in:
        if not isinstance(m, dict):
            continue
        role = m.get("role") or m.get(GROUP_ROLE_KEY) or "其他"
        if role not in MEMBER_ROLES:
            role = "其他"
        members_out.append({"role": role, "fields": m.get("fields") or []})
    return {"offer_type": g.get("offer_type") or "融合", "main_offer": main_out,
            "member_offers": members_out, "group_rules": g.get("group_rules") or {}}


def _pending_of_group(g):
    """组结构待补充判定：逐成员独立，pending_fields 每项携带 role 定位（方案 §3.2）。"""
    pending = []
    for part in [g["main_offer"]] + g["member_offers"]:
        for f in part["fields"]:
            if isinstance(f, dict) and f.get("value") == "待补充":
                pending.append({"role": part["role"], "field": f.get("field", "")})
    return pending


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


def _reason_flat(fields):
    """单商品字段数组推理（复用现有引擎调用 + remark_excluded 剔除），返回 (data, reason_fields)。"""
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
    return out, data


def cmd_ontology_reason(args):
    fields_raw = _read_arg(args, "fields_json", "_json_file")
    if not fields_raw:
        _err("PARAM_MISSING", "缺少待推理字段数组 fields_json")
    try:
        fields = json.loads(fields_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "fields_json 不是合法 JSON，请检查内容或改用 --fields-json-file")
    # V4.0 融合组结构：逐成员推理（成员内复用单商品逻辑）+ 组级校验（action=group_check），
    # 出参新增 group_violations[]；单商品扁平入参行为不变。
    if isinstance(fields, dict) and GROUP_KEYS & set(fields.keys()):
        g = _normalize_group(fields)
        out_main, data_main = _reason_flat(g["main_offer"]["fields"])
        member_results = []
        for m in g["member_offers"]:
            out_m, data_m = _reason_flat(m["fields"])
            member_results.append({"role": m["role"], "out": out_m, "data": data_m})
        violations = []
        if g["member_offers"]:
            gv = _http("POST", "/api/v1/appstore/ontology/fields",
                       {"action": "group_check", "offer_type": g["offer_type"],
                        "main_offer": {"role": g["main_offer"]["role"], "fields": data_main.get("fields_json")},
                        "member_offers": [{"role": m["role"], "fields": m["data"].get("fields_json")}
                                          for m in member_results],
                        "group_rules": g["group_rules"]})
            gv_data = _unwrap(gv)
            if isinstance(gv_data, dict) and isinstance(gv_data.get("group_violations"), list):
                violations = gv_data["group_violations"]
        group_out = {"main_offer": data_main,
                     "member_offers": [{"role": m["role"], "reason": m["out"]} for m in member_results]}
        if violations:
            group_out["group_violations"] = violations
        print(json.dumps({"resultCode": "0", "resultMsg": "success（融合组逐成员推理）",
                          "offer_type": g["offer_type"], "group": group_out}, ensure_ascii=False))
        return
    if isinstance(fields, dict):
        fields = fields.get("fields", [])
    if not isinstance(fields, list) or not fields:
        _err("PARAM_MISSING", "待推理字段数组为空，请先完成需求要素提取（18 字段）")
    out, data = _reason_flat(fields)
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 同构键值合并（flow-A 步骤3 确定性工具） ----------------

def _strip(v):
    """值空白归一：None/空白串 → ""（"待补充"/"系统待生成"等引擎兜底标记视为空值不参与合并）。"""
    if v is None:
        return ""
    s = str(v).strip()
    if s in ("", "待补充", "系统待生成"):
        return ""
    return s


def _is_price_field(field):
    """价格类字段判别（套餐档位 / 宽带月功能费 / 副卡月功能费等）：
    价格禁止推理、禁止从相似产品照搬、禁止跨成员照搬——需求未提供时留空交引擎维持'待补充'。"""
    return any(k in (field or "") for k in PRICE_FIELD_KEYWORDS)


def _merge_flat(elements, offer_fields):
    """单商品同构键值合并：需求有值→原始需求；无值→offerInfo 同名字段(AI推理)；皆缺失/价格→留空交引擎。
    以 elements 为 24 字段骨架，offer-only 字段（非价格且有值）追加挂 AI推理。"""
    offer_map = {f.get("field"): f for f in (offer_fields or []) if isinstance(f, dict)}
    seen, out = set(), []
    for e in elements:
        if not isinstance(e, dict):
            continue
        field = e.get("field", "")
        if not field or field in seen:
            continue
        seen.add(field)
        cat = e.get("category", "")
        val = _strip(e.get("value"))
        if val:
            out.append({"field": field, "category": cat, "value": val, "source": "原始需求"})
        elif _is_price_field(field):
            out.append({"field": field, "category": cat, "value": "", "source": ""})
        else:
            of = offer_map.get(field)
            oval = _strip(of.get("value")) if of else ""
            out.append({"field": field, "category": cat, "value": oval,
                        "source": "AI推理" if oval else ""})
    for of in (offer_fields or []):
        if not isinstance(of, dict):
            continue
        field = of.get("field", "")
        if not field or field in seen:
            continue
        seen.add(field)
        oval = _strip(of.get("value"))
        if _is_price_field(field) or not oval:
            continue
        out.append({"field": field, "category": of.get("category", ""), "value": oval, "source": "AI推理"})
    return out


def _offer_fields_of(offer, role):
    """从相似产品出参侧提取目标角色 fields 数组（与 _normalize_group 同形对位）。
    支持形态：
      - similar_offer 完整出参 {similarOffer:{offerInfo:{fields}}, offer_group:{members[].preset}}
        → 主商品取 offerInfo.fields，成员取 offer_group.members[].preset
      - 扁平 fields 数组 / {fields:[...]} → 主商品
      - 原始 offer_group {members:[{role,...,preset}]}
      - 已归一组结构 {main_offer, member_offers}"""
    if isinstance(offer, list):
        return offer
    if not isinstance(offer, dict):
        return []
    if "similarOffer" in offer:
        inner = offer["similarOffer"]
        if isinstance(inner, dict):
            oi = inner.get("offerInfo")
            if isinstance(oi, dict) and role == "主卡套餐" and isinstance(oi.get("fields"), list):
                return oi["fields"]
    group = offer.get("offer_group") if "offer_group" in offer else (offer if "members" in offer else None)
    if isinstance(group, dict) and isinstance(group.get("members"), list):
        for m in group["members"]:
            if isinstance(m, dict) and m.get("role") == role:
                preset = m.get("preset")
                if isinstance(preset, list):
                    return preset
                if isinstance(preset, dict):
                    return [{"field": k, "value": v, "source": "AI推理"} for k, v in preset.items()]
        return []
    if "fields" in offer and isinstance(offer["fields"], list):
        return offer["fields"]
    for part in ([offer.get("main_offer")] if "main_offer" in offer else []) + \
            (offer.get("member_offers") or []):
        if isinstance(part, dict) and (part.get("role") == role or
                                       (role == "主卡套餐" and part.get("role") in (None, "主卡套餐"))):
            return part.get("fields") or []
    return []


def cmd_merge_fields(args):
    """[Deprecated] flow-A 旧轨（24 字段本体驱动）同构键值合并。
    @deprecated S4 起由模板轨 merge_nested（schema 骨架 + JSONPath 对位）替代，
    本命令仅为过渡期向后兼容保留，新需求分析一律走模板轨。"""
    # DEPRECATED_MARKER: 迁移完成前保留旧轨行为，禁止新增依赖
    fields_raw = _read_arg(args, "fields_json", "_json_file")
    offer_raw = _read_arg(args, "offer_json", "_json_file")
    if not fields_raw:
        _err("PARAM_MISSING", "缺少需求要素 fields_json（步骤1 产出）")
    if not offer_raw:
        _err("PARAM_MISSING", "缺少相似产品出参 offer_json（步骤2 similar_offer 出参）")
    try:
        elements = json.loads(fields_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "fields_json 不是合法 JSON，请检查内容或改用 --fields-json-file")
    try:
        offer = json.loads(offer_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "offer_json 不是合法 JSON，请检查内容或改用 --offer-json-file")

    # ---- V4.0 融合组分支：逐成员独立合并 ----
    if _is_group_input(elements):
        g = _normalize_group(elements)
        main_fields = _merge_flat(g["main_offer"]["fields"],
                                  _offer_fields_of(offer, "主卡套餐"))
        members_out = []
        for m in g["member_offers"]:
            mfields = _merge_flat(m["fields"], _offer_fields_of(offer, m["role"]))
            members_out.append({"role": m["role"], "fields": mfields})
        # group_rules 以 offer_group 出参为准（纪律9 逐字引用），elements 仅占位留空
        og = offer.get("offer_group") if isinstance(offer, dict) else None
        gr = og.get("group_rules") if isinstance(og, dict) and isinstance(og.get("group_rules"), dict) \
            else (g["group_rules"] if isinstance(g.get("group_rules"), dict) else {})
        merged = {"offer_type": g["offer_type"],
                  "main_offer": {"role": "主卡套餐", "fields": main_fields},
                  "member_offers": members_out, "group_rules": gr}
        print(json.dumps({"resultCode": "0", "resultMsg": "success（融合组逐成员合并）",
                          "offer_type": g["offer_type"], "group": merged}, ensure_ascii=False))
        return

    # ---- V3.0 单商品分支 ----
    if isinstance(elements, dict):
        elements = elements.get("fields", [])
    if not isinstance(elements, list):
        _err("PARSE_ERROR", "fields_json 应为扁平字段数组或融合组结构")
    merged = _merge_flat(elements, _offer_fields_of(offer, "主卡套餐"))
    print(json.dumps({"resultCode": "0", "resultMsg": "success",
                      "fields": merged}, ensure_ascii=False))


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
    """等价原 wf_sub_01 拆分代码节点 004a：req_id 系统生成 + plan_json/plan_md/pending_fields 组装。
    V3.0 单商品：五列表格（模块/分类/字段名称/字段值/备注）。
    V4.0 融合组：组结构入参 → 六列表格（商品/模块/...）+ 主商品行加粗 + pending_fields 携带 role；
    扁平 fields 数组入参行为保持 V2.7 逐字节不变（向后兼容铁律）。"""
    fields_raw = _read_arg(args, "fields_json", "_json_file")
    if not fields_raw:
        _err("PARAM_MISSING", "缺少推理后字段数组 fields_json")
    try:
        fields = json.loads(fields_raw)
    except json.JSONDecodeError:
        _err("PARSE_ERROR", "fields_json 不是合法 JSON，请检查内容或改用 --fields-json-file")

    # ---- V4.0 融合组结构分支 ----
    if _is_group_input(fields):
        g = _normalize_group(fields)
        for part in [g["main_offer"]] + g["member_offers"]:
            _backfill_category(part["fields"])
            _mark_price_pending(part["fields"])
            for f in part["fields"]:
                if isinstance(f, dict) and f.get("source") in SOURCE_LABEL:
                    f["source"] = SOURCE_LABEL[f["source"]]
        req_id = PLAN_PREFIX + _now() + "%03d" % random.randint(0, 999)
        pending = _pending_of_group(g)
        plan_json = {"req_id": req_id, "offer_type": g["offer_type"],
                     "main_offer": g["main_offer"], "member_offers": g["member_offers"],
                     "group_rules": g["group_rules"], "pending_fields": pending}

        lines = ["| 商品 | 模块 | 分类 | 字段名称 | 字段值 | 备注 |",
                 "| :--- | :--- | :--- | :--- | :--- | :--- |"]
        # 主商品块（角色加粗 + 商品名加粗，模块/分类跨行合并仅块内生效）
        parts = [("main", g["main_offer"])] + [("member", m) for m in g["member_offers"]]
        for kind, part in parts:
            is_main = kind == "main"
            role_cell = ("**%s**" % part["role"]) if is_main else part["role"]
            main_label = ""
            if is_main:
                name = next((f.get("value", "") for f in part["fields"]
                             if isinstance(f, dict) and f.get("field") in ("产品名称", "套餐名称", "销售品名称") and f.get("value")), "")
                main_label = "（%s）" % name if name else ""
            last_module = last_cat = None
            block_started = False
            for f in part["fields"]:
                if not isinstance(f, dict):
                    continue
                category = f.get("category", "")
                module = _module_of(category)
                module_cell = module if module != last_module else ""
                cat_cell = category if category != last_cat else ""
                last_module, last_cat = module, category
                commodity_cell = "%s%s" % (role_cell, main_label) if not block_started else ""
                block_started = True
                value_cell = "**%s**" % f.get("value", "") if is_main else f.get("value", "")
                lines.append("| %s | %s | %s | %s | %s | 【%s】 |" % (
                    commodity_cell, module_cell, cat_cell, f.get("field", ""),
                    value_cell, f.get("source", "")))
        plan_md = "\n".join(lines)
        print(json.dumps({"req_id": req_id, "offer_type": g["offer_type"],
                          "plan_json": json.dumps(plan_json, ensure_ascii=False),
                          "plan_md": plan_md, "pending_fields": pending}, ensure_ascii=False))
        return

    # ---- V3.0 单商品分支（行为保持 V2.7 逐字节） ----
    if isinstance(fields, dict):
        fields = fields.get("fields", [])
    if not isinstance(fields, list) or not fields:
        _err("PARAM_MISSING",
             "推理后字段数组为空（正常应含 24 字段）；请先完成字段本体推理"
             "（ontology_reason），禁止以空字段组装执行方案")
    for f in fields:
        if isinstance(f, dict) and f.get("source") in SOURCE_LABEL:
            f["source"] = SOURCE_LABEL[f["source"]]
    _backfill_category(fields)
    _mark_price_pending(fields)
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


# ---------------- 模板轨（flow-A 模板驱动重构，S3e 新增子命令） ----------------

# 模板 schema 目录（skill 内相对定位）
TEMPLATE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates")

# 技术字段（配置报文骨架隔离，不计业务字段，derive_flat24 自动派生时跳过）
SKIP_KEYS = ("templateId", "prodId", "prodPrcId", "pricingId", "opType")
# 系统自动生成字段（智能配置环节生成，需求/相似品均无值，不计入待补充与自动派生）
SYSTEM_GEN_KEYS = ("orderNo",)



def _template_path(template_id):
    """templateId → schema 文件路径；不存在时报 PARAM_MISSING。"""
    path = os.path.join(TEMPLATE_DIR, "%s.schema.json" % template_id)
    if not os.path.exists(path):
        _err("PARAM_MISSING",
             "模板不存在：%s（可用模板为 scripts/templates/ 下各 *.schema.json，其顶层 x-template/x-product-type 声明）" % template_id)
    return path


def _load_json_arg(raw, what):
    try:
        return json.loads(raw) if raw else {}
    except json.JSONDecodeError as e:
        _err("PARSE_ERROR", "%s 不是合法 JSON：%s" % (what, e))


def _template_product_types():
    """扫描 templates/ 下全部 schema 的顶层 x-product-type，作为产品类型合法枚举。

    低代码化（评审结论）：新增配置场景=投放一个 *.schema.json 并声明顶层 x-product-type，
    即可自动进入 identify_products 合法枚举，无需再改本文件的 VALID_TYPES 硬编码元组。
    """
    types, seen = {}, set()
    if os.path.isdir(TEMPLATE_DIR):
        for name in sorted(os.listdir(TEMPLATE_DIR)):
            if not name.endswith(".schema.json"):
                continue
            tid = name[: -len(".schema.json")]
            try:
                with open(os.path.join(TEMPLATE_DIR, name), "r", encoding="utf-8") as f:
                    schema = json.load(f)
                pt = schema.get("x-product-type")
            except Exception:
                pt = None
            if pt and pt not in seen:
                seen.add(pt)
                types[tid] = pt
    return types


def cmd_identify_products(args):
    """步骤① 产品列表识别出参校验（LLM 环节的确定性后置闸）：
    校验 products 数组形态与产品类型枚举（枚举以 templates/ 下 schema 顶层 x-product-type 为准），
    输出归一报告。"""
    data = _load_json_arg(_read_arg(args, "products_json", "_json_file"), "products_json")
    products = data.get("products") if isinstance(data, dict) else data
    if not isinstance(products, list) or not products:
        _err("PARAM_MISSING", "products_json 应含非空 products 数组（步骤① LLM 识别产出）")
    valid_types = _template_product_types()
    ok, bad = [], []
    for p in products:
        if isinstance(p, dict) and p.get("prodName") and p.get("prodType") in valid_types.values():
            ok.append(p)
        else:
            bad.append(p)
    print(json.dumps({"resultCode": "0", "resultMsg": "success",
                      "valid_products": ok, "invalid_products": bad,
                      "total": len(products),
                      "valid_product_types": sorted(valid_types.values()),
                      "template_product_types": valid_types}, ensure_ascii=False))


def cmd_get_template(args):
    """步骤③ 模板获取：templateId → schema JSON（含叶子清单摘要供提示词注入）。"""
    with open(_template_path(args.template), "r", encoding="utf-8") as f:
        schema = json.load(f)
    print(json.dumps({"resultCode": "0", "resultMsg": "success",
                      "template": schema.get("x-template", args.template),
                      "schema": schema}, ensure_ascii=False))


def cmd_merge_nested(args):
    """步骤⑤ 嵌套报文合并（转发 merge_nested.py，args 同名透传）。"""
    import merge_nested  # noqa: 与本脚本同目录
    sys.argv = ["merge_nested.py"] + [a for a in sys.argv[2:]]
    merge_nested.main()


def cmd_render_table(args):
    """步骤⑥ 业务分节表格渲染（转发 render_table.py）。"""
    import render_table
    sys.argv = ["render_table.py"] + [a for a in sys.argv[2:]]
    render_table.main()


def cmd_validate_elements(args):
    """第④步后置闸 提取要素校验（转发 validate_elements.py）。"""
    import validate_elements
    sys.argv = ["validate_elements.py"] + [a for a in sys.argv[2:]]
    validate_elements.main()


def cmd_derive_flat24(args):
    """下游过渡兼容层：模板轨嵌套报文 → V3.0 flat24 字段数组（评审结论#1）。
    环节2/3 后端仍按 24 字段校验，派生 plan_json 继续入库。"""
    payload_raw = _read_arg(args, "payload_json", "_json_file")
    data = _load_json_arg(payload_raw, "payload_json")
    data = data.get("payload", data)
    with open(_template_path(args.template), "r", encoding="utf-8") as f:
        schema = json.load(f)
    mapping_raw = args.mapping_file or os.path.join(
        os.path.dirname(os.path.dirname(TEMPLATE_DIR)), "references", "ontology-fields.json")
    mapping = {}
    if os.path.exists(mapping_raw):
        with open(mapping_raw, "r", encoding="utf-8") as f:
            mapping = json.load(f)
    # 确定性提取：扁平 walk 嵌套报文，按 ontology-fields 映射表对位 flat24 字段名
    flat = {}

    def walk(node, prefix=""):
        if not isinstance(node, dict):
            return
        for k, v in node.items():
            p2 = (prefix + "." + k) if prefix else k
            if isinstance(v, dict):
                walk(v, p2)
            elif v not in ("", None):
                flat[p2] = v
    walk(data)
    # schema x-label 逐路径收集（自动派生兜底：映射表未覆盖的模板路径不静默丢失）
    label_map = {}

    def collect_label(sub, prefix=""):
        for k, sub in (sub.get("properties") or {}).items():
            p2 = (prefix + "." + k) if prefix else k
            lbl = sub.get("x-label")
            if lbl:
                label_map[p2] = lbl
            if sub.get("type") == "object":
                collect_label(sub, p2)
    collect_label(schema)
    fields = []
    used = set()
    for path, field_name in (mapping.get("path_to_field") or {}).items():
        val = flat.get(path)
        if val is None:
            continue
        fields.append({"field": field_name, "value": str(val), "source": "模板轨派生"})
        used.add(field_name)
    # 自动派生：映射表未覆盖但报文有值的路径，按 schema x-label（缺则用路径尾段）生成字段名，
    # 避免新增模板字段被静默丢弃（低代码化目标）；已用字段名去重，抑制技术键扩散。
    seen = set()
    for path, val in flat.items():
        if path in (mapping.get("path_to_field") or {}):
            continue
        if path in SYSTEM_GEN_KEYS or path.split(".")[-1] in SKIP_KEYS:
            continue
        name = label_map.get(path) or path.split(".")[-1]
        if not name or name in used or name in seen:
            continue
        seen.add(name)
        fields.append({"field": name, "value": str(val), "source": "模板轨派生(自动)"})
    print(json.dumps({"resultCode": "0", "resultMsg": "success（下游过渡兼容层）",
                      "template": args.template,
                      "fields": fields,
                      "note": "仅作环节2/3 后端 24 字段校验过渡，模板轨唯一事实源为嵌套报文；"
                              "映射表未覆盖路径按 schema x-label 自动派生，不静默丢弃"},
                     ensure_ascii=False))


def cmd_validate_nested(args):
    payload = _load_json_arg(_read_arg(args, "payload_json", "_json_file"), "payload_json")
    if not payload:
        _err("PARAM_MISSING", "payload_json must be provided (merge_nested nested packet, use --payload-json-file)")
    body = {"payload": payload}
    if args.template:
        body["template"] = args.template
    if args.similar_offer_file:
        with open(args.similar_offer_file, "r", encoding="utf-8-sig") as f:
            body["similar_offer"] = json.load(f)
    elif args.similar_offer:
        try:
            body["similar_offer"] = json.loads(args.similar_offer)
        except json.JSONDecodeError:
            body["similar_offer"] = {"raw": args.similar_offer}
    out = _http("POST", "/api/v1/product-ontology/config/validate-nested", body)
    print(json.dumps(out, ensure_ascii=False))


def cmd_explain_nested(args):
    if not args.trace_id:
        _err("PARAM_MISSING", "trace_id must be provided (from validate_nested output)")
    if args.field:
        from urllib.parse import quote
        field_path = quote(args.field, safe="")
        out = _http("GET", "/api/v1/product-ontology/config/provenance/" + field_path)
        print(json.dumps(out, ensure_ascii=False))
        return
    body = {"trace_id": args.trace_id, "audience": args.audience or "business"}
    out = _http("POST", "/api/v1/product-ontology/config/explain", body)
    print(json.dumps(out, ensure_ascii=False))


# ---------------- 监控运营闭环（V8.1：异动根因本体推理 + 优化工单闭环） ----------------
# 转发现有 CPCP 本体推理平台（backend-app Java，与 validate_nested/explain_nested 同一基址），
# 禁止新造本体：复用具 product-ops.ttl（产商品运营归因与风险本体）+ ops_rules.json（R-A01~A06）

def cmd_ops_root_cause(args):
    """异动根因分析（R-A01 异动确认 → R-A02~A05 渠道/促销/竞品/行为归因 → R-A06 持续下滑）。
    出参含推理链 swrlFiredRules[]/appliedRules[]/paths[]/evidenceTriples[] + 优化 actionList[]/workOrder.draft。"""
    if not args.offering_id and not args.product_id and not args.text:
        _err("PARAM_MISSING", "缺少商品ID或异动描述，请提供 --offering-id/--product-id 或 --text")
    body = {}
    oid = args.offering_id or args.product_id
    if oid:
        body["offeringId"] = oid
    if args.text:
        body["text"] = args.text
    out = _http("POST", "/api/v1/product-ontology/ops/root-cause", body)
    print(json.dumps(out, ensure_ascii=False))


def cmd_create_work_order(args):
    """创建处置工单（告警→根因→方案→工单，持续闭环）。出参 workOrder.workOrderId 为工单号。"""
    if not args.offering_id and not args.product_id:
        _err("PARAM_MISSING", "缺少商品ID，请提供 --offering-id/--product-id")
    body = {"offeringId": args.offering_id or args.product_id}
    if args.source:
        body["source"] = args.source
    if args.session_id:
        body["sessionId"] = args.session_id
    if args.title:
        body["title"] = args.title
    if args.summary:
        body["summary"] = args.summary
    if args.actions:
        try:
            body["actions"] = json.loads(args.actions)
        except json.JSONDecodeError:
            _err("PARSE_ERROR", "actions 不是合法 JSON，请用 JSON 数组或省略")
    if args.root_causes:
        try:
            body["rootCauses"] = json.loads(args.root_causes)
        except json.JSONDecodeError:
            _err("PARSE_ERROR", "root_causes 不是合法 JSON，请用 JSON 数组或省略")
    if args.offering_name:
        body["offeringName"] = args.offering_name
    out = _http("POST", "/api/v1/product-ontology/ops/work-orders", body)
    print(json.dumps(out, ensure_ascii=False))


def cmd_query_work_order(args):
    """查询处置工单（按状态/会话/关键词，分页）。"""
    params = {}
    if args.status:
        params["status"] = args.status
    if args.session_id:
        params["session_id"] = args.session_id
    if args.q:
        params["q"] = args.q
    if args.page:
        params["page"] = str(args.page)
    if args.size:
        params["size"] = str(args.size)
    out = _http("GET", "/api/v1/product-ontology/ops/work-orders", params or None)
    print(json.dumps(out, ensure_ascii=False))


def cmd_update_work_order(args):
    """处置工单状态流转：open → in_progress → done / cancelled（回检闭环）。"""
    if not args.work_order_id:
        _err("PARAM_MISSING", "缺少工单号，请提供 --work-order-id")
    if args.status not in ("open", "in_progress", "done", "cancelled"):
        _err("PARAM_MISSING", "工单状态枚举非法（open/in_progress/done/cancelled）")
    body = {"status": args.status}
    if args.remark:
        body["remark"] = args.remark
    out = _http("PUT", "/api/v1/product-ontology/ops/work-orders/" + args.work_order_id, body)
    print(json.dumps(out, ensure_ascii=False))


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
    # ---- 旧轨（24 字段本体驱动）：@deprecated，S4 起由模板轨子命令替代 ----
    s = sub.add_parser("merge_fields")
    s.add_argument("--fields-json"); s.add_argument("--fields-json-file")
    s.add_argument("--offer-json"); s.add_argument("--offer-json-file")
    s.set_defaults(fn=cmd_merge_fields)

    # ---- 模板轨子命令（S3e，flow-A 模板驱动重构）----
    s = sub.add_parser("identify_products")
    s.add_argument("--products-json"); s.add_argument("--products-json-file")
    s.set_defaults(fn=cmd_identify_products)
    s = sub.add_parser("get_template")
    s.add_argument("--template", required=True)
    s.set_defaults(fn=cmd_get_template)
    s = sub.add_parser("validate_elements")
    s.add_argument("--schema-file", required=True)
    s.add_argument("--elements-json"); s.add_argument("--elements-file")
    s.add_argument("--mode", choices=("normal", "legacy"), default="legacy")
    s.add_argument("--threshold", type=float, default=0.30)
    s.set_defaults(fn=cmd_validate_elements)
    s = sub.add_parser("merge_nested")
    s.add_argument("--schema-file", required=True)
    s.add_argument("--elements-json"); s.add_argument("--elements-json-file")
    s.add_argument("--offer-json"); s.add_argument("--offer-json-file")
    s.add_argument("--template", default="")
    s.add_argument("--mode", choices=("normal", "legacy"), default="normal")
    s.set_defaults(fn=cmd_merge_nested)
    s = sub.add_parser("render_table")
    s.add_argument("--schema-file", required=True)
    s.add_argument("--json-file", required=True)
    s.add_argument("--meta-file", default="")
    s.add_argument("--title", default="")
    s.set_defaults(fn=cmd_render_table)
    s = sub.add_parser("derive_flat24")
    s.add_argument("--template", required=True)
    s.add_argument("--payload-json"); s.add_argument("--payload-json-file")
    s.add_argument("--mapping-file", default="")
    s.set_defaults(fn=cmd_derive_flat24)
    s = sub.add_parser("validate_nested")
    s.add_argument("--template", default="")
    s.add_argument("--payload-json"); s.add_argument("--payload-json-file")
    s.add_argument("--similar-offer"); s.add_argument("--similar-offer-file")
    s.set_defaults(fn=cmd_validate_nested)
    s = sub.add_parser("explain_nested")
    s.add_argument("--trace-id", default="")
    s.add_argument("--audience", default="business")
    s.add_argument("--field", default="")
    s.set_defaults(fn=cmd_explain_nested)

    # V8.1 监控运营闭环：异动根因本体推理 + 优化工单
    s = sub.add_parser("ops_root_cause")
    s.add_argument("--offering-id", default="")
    s.add_argument("--product-id", default="")
    s.add_argument("--text", default="")
    s.set_defaults(fn=cmd_ops_root_cause)
    s = sub.add_parser("create_work_order")
    s.add_argument("--offering-id", default="")
    s.add_argument("--product-id", default="")
    s.add_argument("--source", default="ops_assistant")
    s.add_argument("--session-id", default="")
    s.add_argument("--title", default="")
    s.add_argument("--summary", default="")
    s.add_argument("--actions", default="")
    s.add_argument("--root-causes", default="")
    s.add_argument("--offering-name", default="")
    s.set_defaults(fn=cmd_create_work_order)
    s = sub.add_parser("query_work_order")
    s.add_argument("--status", default="")
    s.add_argument("--session-id", default="")
    s.add_argument("--q", default="")
    s.add_argument("--page", default="")
    s.add_argument("--size", default="")
    s.set_defaults(fn=cmd_query_work_order)
    s = sub.add_parser("update_work_order")
    s.add_argument("--work-order-id", required=True)
    s.add_argument("--status", required=True)
    s.add_argument("--remark", default="")
    s.set_defaults(fn=cmd_update_work_order)

    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
