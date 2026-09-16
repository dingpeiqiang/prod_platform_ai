#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逻辑模型报文 → 业务可读分节表格 渲染器 V2.0。

对应需求分析流程第⑥步：json 结合逻辑模型模板，生成**业务人员能看懂的结构化文本**，
不是 JSON。确定性渲染（四层架构：结果组装代码化，禁止 LLM 渲染）。

V2.0 形态（按业务模块分节多表，替代 V1.1 单一大表）：
- 顶层容器（baseInfo/optionalInfo/…）= 独立小节，节标题「N. 中文名」（加粗标题，非表格行）；
- 节内二级容器（如 可选配置→月租/账务优惠）= 小节内分组子标题；
- 字段行 = 「字段名称 | 字段值 | 备注」三列业务表格（无技术键名、无层级标记列）；
- 概览卡片：正文之前先输出套餐概览（资费名称/套餐月费/包含资源/待补充数）；
- 仅渲染有值的段/字段；必填缺失进文末【待补充字段】；同输入输出逐字节稳定（可回归）。

输入：
  --schema-file  模板 schema（templates/<templateId>.schema.json）
  --json-file    实例化逻辑模型报文（按模板嵌套结构填写）
输出：markdown 分节多表（业务可读）
"""
import argparse
import io
import json
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

SKIP_KEYS = ("templateId", "prodId", "prodPrcId", "pricingId", "opType")  # 技术字段不出现在业务表格
SYSTEM_GEN_KEYS = ("orderNo",)  # 系统自动生成字段（智能配置环节生成），不计入待补充

INDENT_UNIT = "　"  # 全角空格缩进

# 概览卡片字段路径（存在才展示，按序）
OVERVIEW_PATHS = (
    ("baseInfo.prodPrcName", "资费名称"),
    ("optionalInfo.printContent.prcMonthFee", "套餐月费"),
    ("optionalInfo.printContent.containResource", "包含资源"),
)


def has_business_value(sub_schema, sub_data):
    """容器是否含业务值（含子容器递归）；纯未填写段不渲染。"""
    if isinstance(sub_data, dict) and sub_data:
        return True
    for sub in sub_schema.get("properties", {}).values():
        if sub.get("type") == "object" and has_business_value(sub, {}):
            return True
    return False


def fmt_value(v):
    if isinstance(v, bool):
        return "是" if v else "否"
    if isinstance(v, list):
        return "、".join(str(x) for x in v)
    return str(v)


def collect(schema, data, pending):
    """遍历 schema 收集渲染节点。返回节列表：[{title, level, rows:[(depth,label,val,note)], order}]。"""
    sections = []

    def walk(obj_schema, obj_data, depth, path, sec):
        for key, sub in obj_schema.get("properties", {}).items():
            label = sub.get("x-label", key)
            cur = (path + "." + key) if path else key
            if sub.get("type") == "object":
                sub_data = obj_data.get(key, {}) if isinstance(obj_data, dict) else {}
                if not has_business_value(sub, sub_data):
                    continue
                if depth == 0:
                    # 顶层容器 = 独立小节
                    sec2 = {"title": label, "sub": "", "rows": []}
                    sections.append(sec2)
                    walk(sub, sub_data, depth + 1, cur, sec2)
                else:
                    # 深层容器 = 小节内分组子标题行
                    sec["rows"].append(("§", depth, label, "", ""))
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
                default = sub.get("default")
                if default and str(obj_data[key]) == str(default):
                    note_parts.append("默认值")
                sec["rows"].append(("|", depth, label, val, "；".join(note_parts)))

    top = {"title": "", "sub": "", "rows": []}
    sections.append(top)
    walk(schema, data, 0, "", top)
    return [s for s in sections if s["rows"] or s["title"]]


def get_path(data, dotted):
    node = data
    for k in dotted.split("."):
        if not isinstance(node, dict):
            return None
        node = node.get(k)
    return node if node not in ("", None) else None


def render(schema, data, title=""):
    pending = []
    sections = collect(schema, data, pending)

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
        lines.append("| 字段名称 | 字段值 | 备注 |")
        lines.append("| :--- | :--- | :--- |")
        last_group = None
        for kind, depth, label, val, note in sec["rows"]:
            if kind == "§":
                # 深层分组：小节内子标题行（加粗，独立一行）
                lines.append("| **%s%s** |  |  |" % (INDENT_UNIT * (depth - 1), label))
                last_group = label
            else:
                pad = INDENT_UNIT * (depth - 1) if depth > 0 else ""
                lines.append("| %s%s | %s | %s |" % (pad, label, val, note))
        lines.append("")

    # ---- 待补充 ----
    if pending:
        lines.append("**【待补充字段】**（%d 项，补充后可进入配置）：" % len(pending))
        lines.append("、".join(pending))
        lines.append("")
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description="逻辑模型报文→业务分节表格渲染器 V2.0")
    p.add_argument("--schema-file", required=True)
    p.add_argument("--json-file", required=True)
    p.add_argument("--title", default="")
    args = p.parse_args()

    with open(args.schema_file, "r", encoding="utf-8") as f:
        schema = json.load(f)
    with open(args.json_file, "r", encoding="utf-8") as f:
        payload = json.load(f)
    data = payload.get(schema["x-template"], payload)

    print(render(schema, data, args.title))


if __name__ == "__main__":
    main()
