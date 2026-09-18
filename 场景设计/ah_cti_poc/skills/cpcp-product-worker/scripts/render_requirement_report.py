#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""需求提报单确定性渲染器 V1.0（环节1 需求提报，模板引擎替代 LLM 手工渲染）。

对应 requirements/requirement-report-template.md（需求提报文档模板）。
四层架构（与 flow-A 模板轨一致）：LLM 只做"自然语言→结构化需求字段"翻译（抽取为 JSON），
本脚本按模板结构做**确定性渲染**（业务可读中文文档），不参与业务决策、不臆造值。

输入（--elements-json-file）：LLM 抽取的需求字段，snake_case 平面 JSON，逐字引用用户描述：
  name            销售品名称
  product_type    产品类型（分支依据：单商品/融合品字段集不同）
  series          所属系列
  members         融合成员（逗号/顿号分隔的字符串；仅融合品产品类型参与渲染与待补充判定）
  price           套餐档位（月费，元/月）
  resources       套内资源（流量/语音/短信/副卡等逐项）
  out_price       套外资费
  billing_cycle   计费周期
  effective_way   生效方式
  validity        套餐有效期
  change_rule     变更规则
  cancel_rule     退订/拆机规则

系统注入（不来自 LLM）：req_id（需求单号）、reporter（提报人，可空）、日期（系统当前日期）、
need_summary（需求概述，1~2 句需求理解摘要）。

渲染纪律（与 requirement-report-template.md 的"禁止事项"一致）：
- 只填充用户明确给出的内容；未提及字段渲染为"待补充"并计入【待补充字段】清单；
- 禁止臆造/照搬价格与规则值（价格类字段待补充即整体待补充）；
- 输出为业务可读纯中文文档，无技术键名。

待补充判定口径（从宽，仅必要字段）：
- 仅【必要字段】缺失才判待补充：资费价格（套餐档位 price）+ 资费免费资源（套内资源 resources）；
- 其余字段（产品类型/所属系列/融合成员/套外资费/计费周期/生效方式/有效期/变更规则/退订规则）
  缺失**不判待补充**，仅按用户原话引用（缺则对应行不渲染）；
- 套外资费不判待补充，原文缺省即不渲染该行（不再以"待补充"催补）。

业务模板分支（单/融合商品模板不同，禁止混用）：
- 融合品产品类型（家庭基础套餐）字段集含"融合成员"，有值时渲染该行（缺失不判待补充，从宽）；
- 单商品产品类型（个人主套餐/个人附加资费/宽带主套餐/宽带附加资费/家庭附加资费）字段集不含"融合成员"，
  既不渲染该行、也不计入待补充清单（不再混用融合业务口径）。
- 分支依据=elements["product_type"]；product_type 缺失/未知时按单商品字段集渲染（不臆造成员）。

输出文件（--output-file）：《销售品需求提报单》markdown 文本；同时落盘工件
requirement_report_<req_id>.json（含需求单号/渲染文本/待补充清单，供需求工单审批与后续流程引用）。
同输入输出逐字节稳定（幂等可回归）。
"""
import argparse
import datetime
import io
import json
import random
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 空值占位（与 merge_nested 口径一致）
EMPTY_MARKS = ("", "待补充", "系统待生成", "无", "暂无")

# 融合品产品类型：天然承载融合成员构成（宽/手机/高清/副卡等），按成员缺失独立判定待补充。
# 单一事实源=templates/ 各 schema 顶层 x-product-type 与顶层成员块（phoneMbrInfo/broadBandMbrInfo）。
# 仅 familyBasePrc（家庭基础套餐）顶层含成员块，属融合品；familyAddPrc（家庭附加资费）无成员块，
# 属单商品（附加资费，不承载融合成员）——故 FUSION_PRODUCT_TYPES 仅含"家庭基础套餐"。
FUSION_PRODUCT_TYPES = ("家庭基础套餐",)

# 需求字段定义：键（snake_case JSON 字段）→ 业务展示标签。
# 单商品与融合品业务模板不同，字段集分离——"融合成员"仅融合品渲染参与待补充判定。
BASE_FIELDS = (
    ("name", "销售品名称"),
    ("product_type", "产品类型"),
    ("series", "所属系列"),
    ("price", "套餐档位（月费）"),
    ("resources", "套内资源"),
    ("out_price", "套外资费"),
    ("billing_cycle", "计费周期"),
    ("effective_way", "生效方式"),
    ("validity", "套餐有效期"),
    ("change_rule", "变更规则"),
    ("cancel_rule", "退订/拆机规则"),
)

FUSION_EXTRA_FIELDS = (("members", "融合成员"),)

# 价格类字段（待补充即整体待补充，禁止照搬）
PRICE_FIELDS = ("price",)

# 必要字段（缺失才判待补充，其余字段缺失不提示）：资费价格 + 资费免费资源。
# 需求提报环节口径从宽——只有必要的资费价格与免费资源缺失才提示补充，其余按原话引用不催补。
REQUIRED_FIELDS = ("price", "resources")


def fields_for(product_type):
    """按产品类型分支返回字段集：单商品不含"融合成员"；融合品追加该字段。"""
    if _norm(product_type) in FUSION_PRODUCT_TYPES:
        return BASE_FIELDS + FUSION_EXTRA_FIELDS
    return BASE_FIELDS


def _norm(v):
    """归一值：None/占位 → ""，否则去首尾空白。"""
    if v is None:
        return ""
    if isinstance(v, bool):
        return "是" if v else "否"
    if isinstance(v, (int, float)):
        return v
    s = str(v).strip()
    return "" if s in EMPTY_MARKS else s


def _gen_req_id():
    """需求单号：PLAN + 14位时间戳 + 3位数字随机（与 cpcp_api.build_plan 规则一致）。

    后端 /api/v1/appstore/result/save 硬校验格式为 PLAN+yyyyMMddHHmmss+3位随机数，
    随机段必须是数字；此前用 uuid hex（6位、含字母）会导致 save_node_result 5002、
    四环节结果无法落库、上线审批被拒。"""
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    rand = "%03d" % random.randint(0, 999)
    return "PLAN%s%s" % (ts, rand)


def render(elements, req_id="", reporter="", need_summary="", today=None):
    """渲染《销售品需求提报单》。elements 为 LLM 抽取的需求字段 dict。

    返回 (text, pending_list, data)：text=需求提报单 markdown；pending_list=待补充字段业务标签；
    data=落盘工件 dict（结构见模块 docstring）。
    """
    req_id = req_id or _gen_req_id()
    today = today or datetime.date.today().strftime("%Y-%m-%d")
    fmt = lambda k: _norm(elements.get(k))  # noqa: E731

    # 按产品类型分支选字段集（单商品不含"融合成员"，融合品含之）
    fields = fields_for(elements.get("product_type"))

    # 逐字段归一并收集渲染值；仅必要字段（资费价格/免费资源）缺失判待补充，其余从宽不催补
    values = {}
    pending = []
    for key, label in fields:
        val = fmt(key)
        values[key] = val
        if key in REQUIRED_FIELDS and (val == "" or (key in PRICE_FIELDS and val in (0, "0", "0元"))):
            pending.append(label)

    # ---- 渲染：分节表格（requirement-report-template.md 一分节~四分节 + 待补充）----
    def cell(v):
        return "待补充" if v == "" else str(v)

    # 第一节 需求基本信息
    lines = []
    lines.append("## 销售品需求提报单")
    base_rows = [("需求单号", req_id), ("提报日期", today)]
    if reporter:
        base_rows.append(("提报人", reporter))
    if need_summary:
        base_rows.append(("需求概述", need_summary))

    # 第二节 产品/销售品信息
    prod_rows = [("销售品名称", cell(values["name"]))]
    prod_rows.append(("产品类型", cell(values["product_type"])))
    if values["series"]:
        prod_rows.append(("所属系列", values["series"]))
    if values.get("members"):
        prod_rows.append(("融合成员", values["members"]))

    # 第三节 资费方案要点
    fee_rows = [
        ("套餐档位（月费）", (values["price"] + " 元/月") if values["price"] != "" else "待补充"),
    ]
    if values["resources"]:
        fee_rows.append(("套内资源", values["resources"]))
    if values["out_price"]:
        fee_rows.append(("套外资费", values["out_price"]))
    if values["billing_cycle"]:
        fee_rows.append(("计费周期", values["billing_cycle"]))

    # 第四节 订购/变更/退订规则
    order_rows = []
    if values["effective_way"]:
        order_rows.append(("生效方式", values["effective_way"]))
    if values["validity"]:
        order_rows.append(("套餐有效期", values["validity"]))
    if values["change_rule"]:
        order_rows.append(("变更规则", values["change_rule"]))
    if values["cancel_rule"]:
        order_rows.append(("退订/拆机规则", values["cancel_rule"]))

    def table(rows):
        if not rows:
            return []
        out = ["| 字段 | 内容 |", "| --- | --- |"]
        out += ["| %s | %s |" % (k, v) for k, v in rows]
        return out

    lines.append("### 1. 需求基本信息")
    lines += table(base_rows)
    lines.append("")
    lines.append("### 2. 产品/销售品信息")
    lines += table(prod_rows)
    lines.append("")
    lines.append("### 3. 资费方案要点")
    lines += table(fee_rows)
    lines.append("")
    lines.append("### 4. 订购/变更/退订规则")
    if order_rows:
        lines += table(order_rows)
    else:
        lines.append("（未提及，从宽不催补）")
    lines.append("")
    lines.append("### 5. 待补充字段")
    lines.append("、".join(pending) if pending else "无")
    # 移除尾部多余空行
    while lines and lines[-1] == "":
        lines.pop()
    text = "\n".join(lines)

    # ---- 落盘工件（供需求工单审批与后续流程引用）----
    data = {
        "req_id": req_id,
        "report_date": today,
        "reporter": reporter,
        "need_summary": need_summary,
        "fields": values,
        "pending_fields": pending,
        "report_text": text,
    }
    return text, pending, data


def main():
    p = argparse.ArgumentParser(description="需求提报单确定性渲染器 V1.0（环节1 需求提报）")
    p.add_argument("--req-id", default="", help="需求单号（缺省按 PLAN+时间戳+随机 生成）")
    p.add_argument("--elements-json-file", default="",
                   help="LLM 抽取的需求字段 JSON 文件（snake_case 平面，逐字引用用户描述）")
    p.add_argument("--elements-json", default="", help="同上，直接传 JSON 文本")
    p.add_argument("--reporter", default="", help="提报人（会话用户，未提供留空）")
    p.add_argument("--need-summary", default="", help="需求概述（1~2 句需求理解摘要）")
    p.add_argument("--output-file", default="", help="《销售品需求提报单》markdown 输出路径")
    p.add_argument("--workdir", default="", help="会话可写目录，用于落盘 requirement_report_<req_id>.json")
    args = p.parse_args()

    # 读取需求字段
    elements = {}
    if args.elements_json_file:
        with open(args.elements_json_file, "r", encoding="utf-8-sig") as f:
            elements = json.load(f)
    elif args.elements_json:
        try:
            elements = json.loads(args.elements_json)
        except json.JSONDecodeError as e:
            sys.stderr.write("PARSE_ERROR elements 不是合法 JSON：%s\n" % e)
            sys.exit(1)

    text, pending, data = render(
        elements,
        req_id=args.req_id,
        reporter=args.reporter,
        need_summary=args.need_summary,
    )

    # 落盘工件（供需求工单审批引用）
    if args.workdir:
        import os
        artifact = os.path.join(args.workdir, "requirement_report_%s.json" % data["req_id"])
        with open(artifact, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)

    # 写《销售品需求提报单》markdown
    if args.output_file:
        with open(args.output_file, "w", encoding="utf-8") as f:
            f.write(text)

    # 出参 JSON（供外层流程逐字引用，不加工）
    out = {
        "resultCode": "0",
        "resultMsg": "success",
        "req_id": data["req_id"],
        "report_date": data["report_date"],
        "pending_fields": pending,
        "pending_count": len(pending),
        "report_text": text,
        "artifact": "" if not args.workdir else artifact,
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
