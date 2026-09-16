#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第④步后置闸 提取要素校验器 V1.0（validate_elements）。

flow-A 模板轨第④步（LLM 提取）与第⑤步（merge_nested 合并）之间的强制校验闸。
四层架构定位：LLM 只做"自然语言→结构化翻译"，本工具为纯确定性校验，逐字节可回归。

校验项（口径与 extract-prompt-template.md 输出校验表一致）：
1. 路径合法性     提取路径 ∈ 模板 schema 叶子集          → 非法路径剔除 + invalid_path 告警
2. 数值合法性     type=number 路径的值可转数值            → 不可转剔除 + invalid_number 告警
3. 枚举命中率     值 ∈ enum（宽松包含匹配）              → 未命中不改写，报 enum_violation（评审结论#4）；
                   说明型 enum（范围/约束描述）豁免（S3b 同款口径，双保险）
4. 是否类归一检查 x-label 含"是否"且 enum 为"是、否"形态  → 值含"允许/开通/支持"→提示归一"是"（不改写，仅提示）
5. 提取质量门禁   正文可提取命中率 = 可提取必填命中数 / 可提取必填总数
                   可提取必填 = 模板必填 − 不可提取白名单（effDate/expDate/编码类等需求单语料天然没有的字段）
                   低于阈值 → quality_gate FAIL（E31 中断语义：打回重跑 LLM 一次，仍低转人工）
6. 价格交叉核对   prcMonthFee/fixFee 提取值是否一致        → 不一致进告警清单（不阻断）

用法：
  python validate_elements.py --schema-file templates/familyBasePrc.schema.json \
      --elements-file 存量提取/_extract_900113046.json --mode legacy [--threshold 0.30]
输出：JSON 报告（result: PASS / PASS_WITH_WARNINGS / FAIL）
"""
import argparse
import io
import json
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 不可提取必填白名单（P0 复盘：K4 md 是需求单语料，编码/日期/短信文案类字段正文天然没有）
# 命名形态匹配：effDate/expDate 生效失效日期、*RuleId 规则编码、*Id 平台编码、groupId 群组、
# sysNote* 系统短信文案、acctItem 科目、powerCode 权益编码、familyCata 目录编码
NON_EXTRACTABLE_PATTERNS = (
    r"(effDate|expDate)$",
    r"(effRuleId|cancelRuleId)$",
    r"(prodId|pricingId|prodPrcId|inProdPrcId|outProdPrcId|billPrcId|conditionCode|groupId)$",
    r"orderNo$",
    r"groupIdMessage$",
    r"subBillPrcIds$",
    r"^optionalInfo\.prcSmsCfg\.",
    r"acctItem$",
    r"powerCode$",
    r"familyCata$",
    r"splitRate(9|6)$",
    r"(favValidityVAlue|fixValidityVAlue)$",
)

# 是否类字段的正语义词 → 建议归一"是"（只提示不改写，评审结论#4 精神）
YES_HINT_WORDS = ("允许", "开通", "支持", "可以", "可办")


def is_descriptive_enum(enum):
    """说明型 enum 判别（与 excel_to_schema / merge_nested 同口径，双保险）。"""
    if not enum:
        return False
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


def is_non_extractable(path):
    """不可提取必填白名单命中判定。"""
    return any(re.search(p, path) for p in NON_EXTRACTABLE_PATTERNS)


def collect_leaves(schema):
    """schema → {jsonpath: leaf_prop}。"""
    leaves = {}

    def walk(node, prefix=""):
        props = node.get("properties") if isinstance(node, dict) else None
        if not isinstance(props, dict):
            return
        for k, v in props.items():
            p2 = (prefix + "." + k) if prefix else k
            if isinstance(v, dict) and v.get("type") == "object":
                walk(v, p2)
            else:
                leaves[p2] = v or {}

    walk(schema)
    return leaves


def flatten_elements(node, prefix="", out=None):
    """提取要素（嵌套或扁平）→ {jsonpath: value}。"""
    if out is None:
        out = {}
    if not isinstance(node, dict):
        return out
    for k, v in node.items():
        path = (prefix + "." + k) if prefix else k
        if isinstance(v, dict):
            flatten_elements(v, path, out)
        else:
            out[path] = v
    return out


def to_number(v):
    """宽松转数值：int/float 直过；"199元"→199；"1-9"区间等非数值失败。"""
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return v
    s = str(v).strip()
    if not s:
        return None
    try:
        return float(s) if "." in s else int(s)
    except ValueError:
        m = re.match(r"^(\d+(?:\.\d+)?)\s*元?$", s)
        return float(m.group(1)) if m else None


def validate(schema, elements, mode="legacy", threshold=0.30):
    """执行六项校验，返回报告 dict。"""
    leaves = collect_leaves(schema)
    flat = flatten_elements(elements)
    warnings, removed = [], []
    enum_violations, yes_norm_hints, price_cross = [], [], []

    valid_paths = {}
    for path, val in flat.items():
        prop = leaves.get(path)
        if prop is None:
            removed.append({"path": path, "value": val, "reason": "invalid_path"})
            continue
        valid_paths[path] = (val, prop)

    # 数值合法性
    for path, (val, prop) in list(valid_paths.items()):
        if prop.get("type") == "number":
            if to_number(val) is None:
                removed.append({"path": path, "value": val, "reason": "invalid_number"})
                del valid_paths[path]

    # 枚举命中率（宽松包含；说明型 enum 豁免）
    for path, (val, prop) in valid_paths.items():
        enum = prop.get("enum")
        if not enum or is_descriptive_enum(enum):
            continue
        sval = str(val)
        if sval in [str(x) for x in enum]:
            continue
        if any(sval in str(x) or str(x) in sval for x in enum):
            continue
        enum_violations.append({"path": path, "value": val, "enum": enum[:6]})

    # 是否类归一检查（只提示不改写）
    for path, (val, prop) in valid_paths.items():
        label = prop.get("x-label", "")
        enum = prop.get("enum") or []
        is_yes_no = ("是否" in label) or any(
            e in ("是、否", "是、否、默认") or e in ("是", "否") for e in enum
        )
        if is_yes_no and isinstance(val, str) and any(w in val for w in YES_HINT_WORDS):
            yes_norm_hints.append({"path": path, "value": val, "suggest": "是"})

    # 提取质量门禁：正文可提取命中率
    extractable_required = [p for p, prop in leaves.items()
                            if prop.get("x-required") and not is_non_extractable(p)]
    hit = [p for p in extractable_required if p in valid_paths]
    rate = (len(hit) / len(extractable_required)) if extractable_required else 1.0
    gate = "PASS" if rate >= threshold else "FAIL"

    # 价格交叉核对：prcMonthFee 与 fixFee 同为数值时一致性
    pmf = to_number(valid_paths.get("optionalInfo.printContent.prcMonthFee", (None,))[0]) \
        if "optionalInfo.printContent.prcMonthFee" in valid_paths else None
    ff = to_number(valid_paths.get("optionalInfo.acctMonth.fixFee", (None,))[0]) \
        if "optionalInfo.acctMonth.fixFee" in valid_paths else None
    if pmf is not None and ff is not None and pmf != ff:
        price_cross.append({"paths": ["optionalInfo.printContent.prcMonthFee",
                                      "optionalInfo.acctMonth.fixFee"],
                            "values": [pmf, ff],
                            "note": "套餐月费与固定费不一致，请人工确认（不阻断）"})

    result = "FAIL" if gate == "FAIL" else ("PASS_WITH_WARNINGS" if (removed or enum_violations) else "PASS")
    return {
        "resultCode": "0",
        "result": result,
        "mode": mode,
        "stats": {
            "extracted_total": len(flat),
            "valid_total": len(valid_paths),
            "removed_total": len(removed),
            "enum_violation_total": len(enum_violations),
            "required_total": sum(1 for p in leaves.values() if p.get("x-required")),
            "extractable_required_total": len(extractable_required),
            "extractable_required_hit": len(hit),
            "extractable_hit_rate": round(rate, 4),
            "threshold": threshold,
        },
        "quality_gate": gate,
        "removed": removed,
        "enum_violations": enum_violations,
        "yes_norm_hints": yes_norm_hints,
        "price_cross_check": price_cross,
    }


def main():
    p = argparse.ArgumentParser(description="第④步后置闸 提取要素校验器（纯确定性，六项校验）")
    p.add_argument("--schema-file", required=True, help="模板 schema 文件")
    p.add_argument("--elements-json", default="", help="LLM 提取要素 JSON 文本（嵌套或扁平）")
    p.add_argument("--elements-file", default="", help="同上，从文件读")
    p.add_argument("--mode", choices=("normal", "legacy"), default="legacy",
                   help="normal=新需求；legacy=存量实例化（质量门禁用可提取必填口径）")
    p.add_argument("--threshold", type=float, default=0.30, help="可提取命中率阈值（P0=0.30）")
    args = p.parse_args()

    if args.elements_json:
        raw = args.elements_json
    elif args.elements_file:
        with open(args.elements_file, "r", encoding="utf-8") as f:
            raw = f.read()
    else:
        sys.stderr.write("缺少 --elements-json / --elements-file\n")
        sys.exit(1)
    try:
        elements = json.loads(raw)
    except json.JSONDecodeError as e:
        sys.stderr.write("PARSE_ERROR elements 不是合法 JSON：%s\n" % e)
        sys.exit(1)

    with open(args.schema_file, "r", encoding="utf-8") as f:
        schema = json.load(f)

    report = validate(schema, elements, mode=args.mode, threshold=args.threshold)
    print(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
