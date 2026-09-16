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
7. 输出附 _meta：每个叶子路径的 source 标注 + 待补充必填清单。

向后兼容：--flat-elements 旧扁平字段数组（[{field,value}]，field=x-label）仍可合并（按 x-label 对位）。
"""
import argparse
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 价格字段判别关键词（与 cpcp_api._is_price_field 口径一致，嵌套语境按 x-label/路径尾段判定）
PRICE_KEYWORDS = ("档位", "月功能费", "月租", "月费", "固定费", "费用")
# 含这些词的 label 非资费金额（月租有效期/资源周期时长/收费间隔等时间维度字段），允许相似品补全
PRICE_EXCLUDE_MARKS = ("有效期", "周期", "时长", "间隔")
# 视为空值的占位标记
EMPTY_MARKS = ("", "待补充", "系统待生成")
# 技术字段（业务表格不展示，但报文保留）
SKIP_KEYS = ("templateId", "prodId", "prodPrcId", "pricingId", "opType")
# 系统自动生成字段（智能配置环节生成，需求/相似品均无值，不计入待补充清单）
SYSTEM_GEN_KEYS = ("orderNo",)


def is_descriptive_enum(enum):
    """说明型 enum 判别（S3b，与 excel_to_schema.is_descriptive_enum 口径一致）：
    范围/约束/格式描述（"1-9之间正整数"、"按资费表字符长度限制"）非封闭枚举，
    命中则豁免 enum_violation 校验（P0 复盘确认 6/9 误报源于此）。"""
    if not enum:
        return False
    import re
    DESC_PATTERNS = (
        r"^\d+-\d+之间",
        r"^(正整数|整数|数字)$",
        r"^(按|根据).*(限制|规则|模型|长度)",
        r"^置灰",
        r"^默认",
        r"^\d+个?字符",
        r"以内$",
        r"长度限制$",
    )
    if any(re.search(p, s) for s in enum for p in DESC_PATTERNS):
        return True
    if len(enum) == 1 and any(w in enum[0] for w in ("限制", "之间", "以内", "长度", "规则", "说明")):
        return True
    return False


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
        # 3) 兜底：schema default
        if val is None and sub.get("default") is not None:
            val, source = sub["default"], "默认值"
        # 4) 仍缺：留空，必填进待补充
        if val is None:
            val, source = "", ""
            if sub.get("x-required") and key not in SKIP_KEYS and key not in SYSTEM_GEN_KEYS:
                pending.append(cur)
        # 枚举校验不改写（评审结论#4）：提取值 ∉ enum → 标记 enum_violation
        # S3b：说明型 enum（非封闭值列举）豁免校验，避免误报
        if val != "" and sub.get("enum") and not is_descriptive_enum(sub["enum"]):
            if str(val) not in [str(x) for x in sub["enum"]]:
                hit = any(str(val) in str(x) or str(x) in str(val) for x in sub["enum"])
                if not hit:
                    meta_key = cur + ".enum_violation"
                    meta[meta_key] = {"value": val, "enum": sub["enum"][:6]}
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

    result = {"resultCode": "0",
              "resultMsg": "success（存量实例化）" if args.mode == "legacy" else "success",
              "template": template_id,
              "payload": merged,
              "_meta": meta,
              "pending_required": pending}
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
