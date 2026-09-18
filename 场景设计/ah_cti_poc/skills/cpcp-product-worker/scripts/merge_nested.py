#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第⑤步 嵌套报文合并器 V1.0（merge_nested）。

需求分析流程第⑤步：相似产品配置逻辑模型报文 与 LLM 第④步提取的配置要素 做整合。

四层架构定位（LLM 只做自然语言→结构化翻译，确定性逻辑全部代码化）：
- 输入一：--schema-file    模板 schema（templates/<templateId>.schema.json），提供报文骨架
- 输入二：--elements-json  LLM 第④步按模板提取的配置要素（嵌套形态，与模板同构；也兼容扁平 key:value）
- 输入三：--offer-json     相似产品完整逻辑模型报文（嵌套形态；也兼容 {templateId:{...}} 包裹）
- 输出：  按模板嵌套结构实例化的逻辑模型报文 JSON（供第⑥步 render_table 渲染）

合并规则（确定性，逐字节可回归）：
1. 以 schema 为骨架递归：schema 有而两输入皆无的叶子 → 仍建键，值为 ""（交引擎/待补充）；
2. 对位按 JSONPath（如 optionalInfo.acctMonth.fixFee），不做字段名模糊匹配；
3. 需求要素有值 → source=原始需求；
4. 无值且非价格 → 取相似产品同路径值 → source=AI补全；
5. 皆缺失或价格字段（价格禁止从相似产品照搬，主套餐档位/宽带月功能费/副卡月功能费各自独立判定）→ 留空；
6. 待补充标记（"待补充"/"系统待生成"）视为空值，不参与合并；
7. 输出附 _meta：每个叶子路径的 source 标注 + 待补充必填清单；
8. **V9.2 枚举全放开为自由文本**：schema 枚举（enum）仅作展示/参考，不做命中校验、不产 enum_violation，
   提取到的任意原文原样入库（含 5G-A 阶梯计费 3元/1GB 等非模板枚举的合法值）；
9. **V10.1 同源派生**：同一业务参数不同表达（套餐月费 prcMonthFee ↔ 套餐固定费 fixFee）视为
   同一参数、仅表达形式不同（R-C06 按数值从两处派生 fixedFeeAmount，要求两处同为数值且一致），
   merge 后双向确定性回填，消除本参数的重复确认。

向后兼容：--flat-elements 旧扁平字段数组（[{field,value}]，field=x-label）仍可合并（按 x-label 对位）。
"""
import argparse
import io
import json
import sys

if not (isinstance(sys.stdout, io.TextIOWrapper) and getattr(sys.stdout, "encoding", "") and
        "utf" in sys.stdout.encoding.lower()):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 价格字段判别关键词（与 cpcp_api._is_price_field 口径一致，嵌套语境按 x-label/路径尾段判定）
PRICE_KEYWORDS = ("档位", "月功能费", "月租", "月费", "固定费", "费用")
# 含这些词的 label 非资费金额（月租有效期/资源周期时长/收费间隔等时间维度字段），允许相似品补全
PRICE_EXCLUDE_MARKS = ("有效期", "周期", "时长", "间隔")
# V9.2 必填字段兜底判定：枚举已全放开为自由文本，但 LLM 可能把计费原文写入同体系"计费说明型"字段
# （如超套收费标准 chargeStandard）而非枚举字段（如套外计费标准 outChargeMode）。此时该枚举字段即使
# 本身为空也判为已覆盖（原文见说明字段），酌情不进待补充。
CHARGE_DESC_KEYWORDS = ("超套", "套外", "资费", "计费", "收费")
# 视为空值的占位标记
EMPTY_MARKS = ("", "待补充", "系统待生成")
# 技术字段（业务表格不展示，但报文保留）
SKIP_KEYS = ("templateId", "prodId", "prodPrcId", "pricingId", "opType")
# 系统自动生成字段（智能配置环节生成，需求/相似品均无值，不计入待补充清单）
SYSTEM_GEN_KEYS = ("orderNo",)
# 同一业务参数不同表达（V10.1，同源派生对）：(路径A, 路径B, A业务标签, B业务标签)。
# R-C06 按数值从两处派生 fixedFeeAmount，要求两处同为数值且一致；二者视为同一参数、仅表达形式不同，
# merge 后双向回填，消除本参数的重复确认。
SAME_PARAMETER_PAIRS = (
    ("optionalInfo.printContent.prcMonthFee", "optionalInfo.acctMonth.fixFee",
     "套餐月费", "套餐固定费"),
)


def _today_str():
    """系统默认规则用的当前日期（YYYY-MM-DD）。"""
    import datetime
    return datetime.date.today().strftime("%Y-%m-%d")


def is_price(label, key):
    """价格字段判定：优先 x-label（档位/月功能费/月租…），路径尾段名兜底。
    含"有效期/周期/时长/间隔"的字段是时间维度非资费金额，不按价格拦截。"""
    for text in (label, key):
        t = text or ""
        if any(m in t for m in PRICE_EXCLUDE_MARKS):
            continue
        if any(kw in t for kw in PRICE_KEYWORDS):
            return True
    return False


def strip(v):
    """空白/占位归一：None、"待补充"、"系统待生成" → ""（空值不参与合并）。"""
    if v is None:
        return ""
    if isinstance(v, bool):
        return "是" if v else "否"
    if isinstance(v, (int, float)):
        return v
    s = str(v).strip()
    return "" if s in EMPTY_MARKS else s


def _charge_desc_covers(prop, cur, elements_map):
    """V9.2 放松必填枚举判定：必填字段为空时，若其为枚举字段，且需求已在同体系
    '计费说明型'自由文本字段（超套/套外/资费/计费/收费关键词命中）写入原文，判为已覆盖，
    不进待补充。返回 True 表示已覆盖（应跳过 pending）。"""
    if not prop.get("enum"):
        return False
    label = str(prop.get("x-label", "") or "") + cur
    hit_key = [w for w in CHARGE_DESC_KEYWORDS if w in label]
    if not hit_key:
        return False
    for epath, evalue in elements_map.items():
        if not epath.endswith("chargeStandard"):
            continue
        if any(w in str(evalue) for w in CHARGE_DESC_KEYWORDS):
            return True
    return False


def _num(v):
    """数值归一：数字/可转数字字符串 → float；否则 None。"""
    if isinstance(v, bool) or v is None:
        return None
    try:
        return float(str(v).strip())
    except (TypeError, ValueError):
        return None


def _src(meta, path):
    m = meta.get(path) or {}
    return m.get("source", "")


def _path_get(node, path):
    for part in path.split("."):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def _path_set(node, path, value):
    parts = path.split(".")
    cur = node
    for part in parts[:-1]:
        if not isinstance(cur, dict):
            return
        cur = cur.setdefault(part, {})
    if isinstance(cur, dict):
        cur[parts[-1]] = value


def reconcile_same_parameter(merged, meta, pending):
    """同源派生（V10.1）：同一业务参数不同表达，merge 后确定性双向回填。

    套餐月费 prcMonthFee（免填单/宣传展示）与 套餐固定费 fixFee（月租计费固定费）本质是
    同一业务参数、仅表达形式不同；后端 R-C06 按数值从两处派生 fixedFeeAmount，要求两处
    同为数值且一致。本函数消除该参数的重复确认：
    - 一侧有值、另一侧空 → 用有值侧派生出空侧（source=同源派生(权威侧标签)），并从 pending 移除；
    - 两侧有值但不一致 → 以 source=原始需求/存量提取 侧为权威，弱侧对齐到同一金额；
    - 两侧有值且一致 → 不动。
    返回是否发生派生/回填。"""
    changed = False
    for path_a, path_b, label_a, label_b in SAME_PARAMETER_PAIRS:
        na, nb = _num(_path_get(merged, path_a)), _num(_path_get(merged, path_b))
        src_a, src_b = _src(meta, path_a), _src(meta, path_b)
        # 权威侧：原始需求/存量提取优先，其次任一有值侧；两侧均有值则取权威源更高者破平。
        def _authority():
            if na is None and nb is None:
                return None
            pri = {"原始需求": 0, "存量提取": 0, "AI补全": 1, "同源派生": 2, "默认值": 2}
            rank_a, rank_b = pri.get(str(src_a), 2), pri.get(str(src_b), 2)
            if na is not None and nb is not None:
                if na == nb:
                    return None
                return (na, path_a, label_a) if (rank_a, path_a) <= (rank_b, path_b) else (nb, path_b, label_b)
            return (na, path_a, label_a) if na is not None else (nb, path_b, label_b)
        auth = _authority()
        if auth is None:
            continue
        value, apath, alabel = auth
        for path, label in ((path_a, label_a), (path_b, label_b)):
            if _num(_path_get(merged, path)) != value:
                _path_set(merged, path, value)
                meta[path] = {"label": label, "value": value, "source": "同源派生(%s)" % alabel}
                if path in pending:
                    pending.remove(path)
                changed = True
    return changed


def flatten_elements(node, prefix="", out=None):
    """把 LLM 第④步提取的配置要素归一为 {jsonpath: value} 平面表。
    支持两种形态：
      - 嵌套对象（与模板同构）：{"baseInfo": {"prodPrcName": "…"}}
      - 扁平 key:value：{"prodPrcName": "…", "optionalInfo.acctMonth.fixFee": 40}
    """
    if out is None:
        out = {}
    if not isinstance(node, dict):
        return out
    for k, v in node.items():
        path = (prefix + "." + k) if prefix else k
        if isinstance(v, dict):
            flatten_elements(v, path, out)
        else:
            val = strip(v)
            if val != "":
                out[path] = val
    return out


def flatten_offer(node, prefix="", out=None):
    """相似产品报文 → {jsonpath: value} 平面表（列表取 [i] 段）。"""
    if out is None:
        out = {}
    if isinstance(node, dict):
        for k, v in node.items():
            path = (prefix + "." + k) if prefix else k
            flatten_offer(v, path, out)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            flatten_offer(v, "%s[%d]" % (prefix, i), out)
    else:
        val = strip(node)
        if val != "":
            out[prefix] = val
    return out


def merge(schema, elements_map, offer_map, meta, path="", pending=None, mode="normal"):
    """以 schema 为骨架递归合并，产出嵌套报文 + _meta 溯源表。
    mode=normal：新需求链路（价格字段禁止从相似产品照搬，source=原始需求/AI补全/默认值）；
    mode=legacy：存量实例化（无相似品输入，价格正常提取，source=存量提取/默认值）。"""
    if pending is None:
        pending = []
    out = {}
    props = schema.get("properties") or {}
    for key, sub in props.items():
        cur = (path + "." + key) if path else key
        label = sub.get("x-label", key)
        if sub.get("type") == "object":
            out[key] = merge(sub, elements_map, offer_map, meta, cur, pending, mode)
            continue
        # ---- 叶子 ----
        # 1) 需求要素优先（原始需求/存量提取）
        src_primary = "存量提取" if mode == "legacy" else "原始需求"
        val = elements_map.get(cur)
        source = src_primary if val is not None else ""
        # 2) 相似产品同路径补全（价格字段禁止照搬；存量模式无相似品输入，天然跳过）
        if val is None and not is_price(label, key):
            oval = offer_map.get(cur)
            if oval is not None:
                val, source = oval, "AI补全"
        # 3) 兜底：schema default → x-default-rule（系统默认规则：effDate=系统时间、expDate=2099-12-31）
        if val is None and sub.get("default") is not None:
            val, source = sub["default"], "默认值"
        if val is None and sub.get("x-default-rule"):
            rule = sub["x-default-rule"]
            val = _today_str() if rule == "system_date" else rule
            source = "默认值"
        # 4) 仍缺：留空，必填进待补充（V9.2：必填枚举字段若需求已把计费方式写入同体系
        #    说明型字段（超套收费标准等），判为已覆盖，不进待补充——放松枚举判定）
        if val is None:
            val, source = "", ""
            if sub.get("x-required") and key not in SKIP_KEYS and key not in SYSTEM_GEN_KEYS:
                if not _charge_desc_covers(sub, cur, elements_map):
                    pending.append(cur)
        # V9.2 枚举全放开为自由文本：schema 枚举仅作展示/参考，不做命中校验、不产出 enum_violation，
        # 提取到的任意原文原样入库（含 5G-A 阶梯计费 3元/1GB 等非模板枚举的合法值）。
        out[key] = val
        if source:
            meta[cur] = {"label": label, "value": val, "source": source}
    return out


def main():
    p = argparse.ArgumentParser(description="第⑤步 嵌套报文合并器（schema 骨架 + 需求要素 + 相似产品报文）")
    p.add_argument("--schema-file", required=True, help="模板 schema 文件")
    p.add_argument("--elements-json", default="", help="LLM 提取的配置要素（嵌套或扁平 JSON 文本）")
    p.add_argument("--elements-json-file", dest="elements_json_file", default="", help="同上，从文件读")
    p.add_argument("--offer-json", default="", help="相似产品逻辑模型报文（嵌套 JSON 文本）")
    p.add_argument("--offer-json-file", dest="offer_json_file", default="", help="同上，从文件读")
    p.add_argument("--template", default="", help="相似产品报文外层模板名包裹（如 familyBasePrc）时用于解包")
    p.add_argument("--mode", choices=("normal", "legacy"), default="normal",
                   help="normal=新需求链路；legacy=存量实例化（价格正常提取，source=存量提取）")
    args = p.parse_args()

    def _read(text, path, what):
        if text:
            raw = text
        elif path:
            with open(path, "r", encoding="utf-8") as f:
                raw = f.read()
        else:
            return {}
        try:
            return json.loads(raw)
        except json.JSONDecodeError as e:
            sys.stderr.write("PARSE_ERROR %s 不是合法 JSON：%s\n" % (what, e))
            sys.exit(1)

    elements = _read(args.elements_json, args.elements_json_file, "elements")
    offer = _read(args.offer_json, args.offer_json_file, "offer")

    with open(args.schema_file, "r", encoding="utf-8") as f:
        schema = json.load(f)
    template_id = schema.get("x-template", "")

    # 相似产品报文解包：{templateId:{...}} 包裹（xlsx 样例形态）
    if isinstance(offer, dict) and template_id in offer:
        offer = offer[template_id]

    elements_map = flatten_elements(elements)
    offer_map = {} if args.mode == "legacy" else flatten_offer(offer)
    meta, pending = {}, []
    merged = merge(schema, elements_map, offer_map, meta, pending=pending, mode=args.mode)
    # V10.1 同源派生：同一业务参数不同表达（套餐月费↔套餐固定费）双向回填，消除重复确认
    reconcile_same_parameter(merged, meta, pending)

    result = {"resultCode": "0",
              "resultMsg": "success（存量实例化）" if args.mode == "legacy" else "success",
              "template": template_id,
              "payload": merged,
              "_meta": meta,
              "pending_required": pending}
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
