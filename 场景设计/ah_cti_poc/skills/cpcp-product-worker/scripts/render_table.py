#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逻辑模型报文 → 业务可读分节表格 渲染器 V3.0。

对应需求分析流程第⑥步：json 结合逻辑模型模板，生成**业务人员能看懂的结构化文本**，
不是 JSON。确定性渲染（四层架构：结果组装代码化，禁止 LLM 渲染）。

V3.0（字段取值来源）：表格新增「取值来源」列，逐字段标注取值来自
【原始需求提取】/【复用相似产品】/【本体推理】/【默认值】（由 merge_nested 出参 _meta
逐叶子 source 溯源 + validate_nested defaulted 补充，render_table 仅做确定性映射展示，不写值）。

V2.1 形态（按业务模块分节多表，替代 V1.1 单一大表）：
- 顶层容器（baseInfo/releaseInfo/…）= 独立小节，节标题「N. 中文名」（加粗标题，非表格行）；
- 顶层容器下的直接子对象（如 可选配置→免填单/月租/账务优惠）= 同样提升为独立小节，与 发布信息 同级；
- 更深层容器 = 小节内分组子标题（加粗缩进行）；
- 字段行 = 「字段名称 | 字段值 | 取值来源 | 备注」四列业务表格（无技术键名、无层级标记列）；
- 概览卡片：正文之前先输出套餐概览（资费名称/套餐月费/包含资源/待补充数）；
- 仅渲染有值的段/字段；必填缺失进文末【待补充字段】；同输入输出逐字节稳定（可回归）。

输入：
  --schema-file  模板 schema（templates/<templateId>.schema.json）
  --json-file    实例化逻辑模型报文（按模板嵌套结构填写，payload 本体）
  --meta-file    merge_nested 出参 _meta 溯源（path→{source}），可选；提供时逐叶子标注取值来源
输出：markdown 分节多表（业务可读）
"""
import argparse
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

SKIP_KEYS = ("templateId", "prodId", "prodPrcId", "pricingId", "opType")  # 技术字段不出现在业务表格
SYSTEM_GEN_KEYS = ("orderNo",)  # 系统自动生成字段（智能配置环节生成），不计入待补充

# 取值来源展示映射（merge _meta source → 业务列展示；三态对应三标签）
SOURCE_MAP = {
    "原始需求": "原始需求提取",
    "AI补全": "复用相似产品",
    "本体推理": "本体推理",
    "默认值": "默认值",
}

INDENT_UNIT = "　"  # 全角空格缩进

# 概览卡片字段路径（存在才展示，按序）
OVERVIEW_PATHS = (
    ("baseInfo.prodPrcName", "资费名称"),
    ("optionalInfo.printContent.prcMonthFee", "套餐月费"),
    ("optionalInfo.printContent.containResource", "包含资源"),
)


def has_business_value(sub_schema, sub_data):
    """容器是否含业务值（递归到叶子）；纯空容器（全叶子为空/无值）不渲染。

    注：空容器中 x-required 子字段的缺失已由 collect() 计入【待补充字段】，
    正文不需要为它们渲染空分组标题。"""
    if isinstance(sub_data, dict):
        for v in sub_data.values():
            if v not in ("", None, [], {}):
                return True
        return False
    return False


def fmt_value(v):
    if isinstance(v, bool):
        return "是" if v else "否"
    if isinstance(v, list):
        return "、".join(str(x) for x in v)
    return str(v)


def collect(schema, data, pending, meta=None):
    """遍历 schema 收集渲染节点。返回节列表：[{title, level, rows:[(depth,label,val,source,note)], order}]。"""
    sections = []
    meta = meta or {}

    def walk(obj_schema, obj_data, depth, path, sec):
        for key, sub in obj_schema.get("properties", {}).items():
            label = sub.get("x-label", key)
            cur = (path + "." + key) if path else key
            if sub.get("type") == "object":
                sub_data = obj_data.get(key, {}) if isinstance(obj_data, dict) else {}
                if not has_business_value(sub, sub_data):
                    continue
                if depth <= 1:
                    # 顶层容器及其直接子对象 = 独立小节（V2.1：可选配置下的组件如 免填单/月租 与 发布信息 同级）
                    sec2 = {"title": label, "sub": "", "rows": []}
                    sections.append(sec2)
                    walk(sub, sub_data, depth + 1, cur, sec2)
                else:
                    # 更深容器 = 小节内分组子标题行
                    sec["rows"].append(("§", depth, label, "", "", ""))
                    walk(sub, sub_data, depth + 1, cur, sec)
            else:
                if key in SKIP_KEYS or not isinstance(obj_data, dict):
                    continue
                if key not in obj_data or obj_data[key] in ("", None):
                    if sub.get("x-required") and key not in SYSTEM_GEN_KEYS:
                        pending.append((INDENT_UNIT * depth) + label)
                    continue
                val = fmt_value(obj_data[key])
                note_parts = []
                cond = sub.get("x-show-when")
                if cond:
                    note_parts.append("满足条件时展示：" + cond.rstrip("才展示展示"))
                entry = meta.get(cur) or {}
                source_raw = entry.get("source", "") if isinstance(entry, dict) else ""
                source = SOURCE_MAP.get(source_raw, source_raw)
                sec["rows"].append(("|", depth, label, val, source, "；".join(note_parts)))

    top = {"title": "", "sub": "", "rows": []}
    sections.append(top)
    walk(schema, data, 0, "", top)
    return [s for s in sections if s["rows"]]


def get_path(data, dotted):
    node = data
    for k in dotted.split("."):
        if not isinstance(node, dict):
            return None
        node = node.get(k)
    return node if node not in ("", None) else None


def render(schema, data, title="", meta=None):
    pending = []
    sections = collect(schema, data, pending, meta)

    lines = []
    if title:
        lines.append("## " + title)
        lines.append("")

    # ---- 概览卡片 ----
    overview = []
    for dotted, cn in OVERVIEW_PATHS:
        node = data
        for k in dotted.split("."):
            node = node.get(k) if isinstance(node, dict) else None
        if node not in ("", None):
            overview.append("**%s**：%s" % (cn, fmt_value(node)))
    if overview:
        lines.append("> **套餐概览**　|　" + "　|　".join(overview))
        lines.append("")

    # ---- 分节多表 ----
    n = 0
    for sec in sections:
        if not sec["rows"]:
            continue
        n += 1
        title_txt = "%d. %s" % (n, sec["title"]) if sec["title"] else "%d. 配置明细" % n
        lines.append("**%s**" % title_txt)
        lines.append("")
        lines.append("| 字段名称 | 字段值 | 取值来源 | 备注 |")
        lines.append("| :--- | :--- | :--- | :--- |")
        last_group = None
        for kind, depth, label, val, source, note in sec["rows"]:
            if kind == "§":
                # 深层分组：小节内子标题行（加粗，独立一行）
                lines.append("| **%s%s** |  |  |  |" % (INDENT_UNIT * (depth - 1), label))
                last_group = label
            else:
                pad = INDENT_UNIT * (depth - 1) if depth > 0 else ""
                lines.append("| %s%s | %s | %s | %s |" % (pad, label, val, source, note))
        lines.append("")

    # ---- 待补充 ----
    if pending:
        lines.append("**【待补充字段】**（%d 项，补充后可进入配置）：" % len(pending))
        lines.append("、".join(pending))
        lines.append("")
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description="逻辑模型报文→业务分节表格渲染器 V3.0")
    p.add_argument("--schema-file", required=True)
    p.add_argument("--json-file", required=True)
    p.add_argument("--meta-file", default="")
    p.add_argument("--title", default="")
    args = p.parse_args()

    with open(args.schema_file, "r", encoding="utf-8") as f:
        schema = json.load(f)
    with open(args.json_file, "r", encoding="utf-8") as f:
        payload = json.load(f)
    data = payload.get(schema["x-template"], payload)

    meta = {}
    if args.meta_file:
        with open(args.meta_file, "r", encoding="utf-8") as f:
            meta = json.load(f)

    print(render(schema, data, args.title, meta))


if __name__ == "__main__":
    main()
