#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输出前程序化校验脚本（SKILL.md 纪律1 强制兜底，validate_output V1.0）。

在环节1~4 正式输出给用户前运行，核对输出内容与出参工件的一致性：
  - 标题头形态封闭（仅 ✅ 执行成功 / ❌ 执行失败 两种，禁止自造"执行中断"等）；
  - 禁止虚构全局统计值（跨场景加总"10/10 通过"等出参不存在的数字）；
  - E26 中断场景禁止输出通过性明细（用例统计/三大验证/受理凭证/上线结论/汇总表格）；
  - 异常中断场景禁止输出汇总表格；
  - 环节4 场景行数=出参 testScenes 数（禁止虚构场景行/合并行）；
  - 环节3 比对表行数与空值行省略规则核对。

用法：
  python validate_output.py --node test --result-file result_test_PLANxxx.json --output-file draft.md [--plan-file plan_json_PLANxxx.json]
  python validate_output.py --node fee  --result-file result_fee_PLANxxx.json  --output-file draft.md

出参：
  {"resultCode": "0", "checklist": [...], "errors": []}       全部通过
  {"resultCode": "VALIDATE_FAIL", "checklist": [...], "errors": [问题清单]}   存在问题须修正
  {"resultCode": "PARAM_MISSING"|"FILE_ERROR", ...}           参数/文件异常（不中断主干，按 E24 输出失败说明）

说明：本脚本为静态一致性校验，不访问网络；--output-file 为拟输出的文本（对话正文草稿）。
"""
import argparse
import json
import re
import sys


def _force_utf8_stdio():
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


_force_utf8_stdio()

# 合法标题头形态（flow-B 输出结构总纪律：封闭两种）
HEADER_OK = [
    "【环节1/4·智能配置】✅ 执行成功", "【环节1/4·智能配置】❌ 执行失败",
    "【环节2/4·配置规格稽核】✅ 执行成功", "【环节2/4·配置规格稽核】❌ 执行失败",
    "【环节3/4·资费校准】✅ 执行成功", "【环节3/4·资费校准】❌ 执行失败",
    "【环节4/4·销售品自动测试（含受理验证）】✅ 执行成功",
    "【环节4/4·销售品自动测试（含受理验证）】❌ 执行失败",
]

# E26 中断场景禁止出现的通过性明细特征（环节4 标题头为 ❌ 时）
E26_FORBIDDEN_PATTERNS = [
    (r"建议上线|评估风险后上线|禁止上线", "整体上线结论"),
    (r"受理凭证|orderId|offerInstId", "受理凭证明细"),
    (r"ACC-\d{3}|BILL-\d{3}|CUST-\d{3}", "三大验证分项用例"),
    (r"4\.1|4\.2|4\.3", "三大验证分项编号"),
]

# 任何异常中断场景（❌ 标题头）禁止输出汇总表格：出现"| 环节 |"或"| :--- |"表头即视为汇总表
SUMMARY_TABLE_PATTERNS = [
    r"\|\s*环节\s*\|\s*执行结果\s*\|",
    r"【执行主干全部完成】",
    r"【执行主干中断】",
]

# 自造标题形态（第三形态黑名单）
HEADER_BLACKLIST = ["执行中断", "执行异常", "部分中断", "主干中断】"]


def _read_json(path):
    # utf-8-sig 兼容 PowerShell Set-Content 等工具产生的 BOM 头（E24 编码自愈同源）
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def _read_text(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return f.read()


def _truthy(v):
    return str(v).lower() == "true"


def check_header(node, text, errors, checklist):
    """标题头形态封闭：必须以合法标题头开头，禁止黑名单第三形态。"""
    lines = [l.strip() for l in text.splitlines() if l.strip().startswith("【环节")]
    if not lines:
        errors.append("缺少环节标题头（【环节N/4·环节名】），禁止无标题头裸段落输出")
        return
    for line in lines:
        if line not in HEADER_OK:
            errors.append("标题头形态非法（仅允许 ✅ 执行成功 / ❌ 执行失败）：%s" % line)
    for bad in HEADER_BLACKLIST:
        if bad in text:
            errors.append("出现自造标题形态黑名单词：%s" % bad)
    checklist.append("标题头形态封闭")


def check_no_summary_table_when_failed(node, text, errors, checklist):
    """异常中断（任一环节 ❌）时禁止输出汇总表格/【执行主干全部完成】块。"""
    if "❌ 执行失败" not in text:
        checklist.append("无失败环节，汇总块放行")
        return
    for pat in SUMMARY_TABLE_PATTERNS:
        if re.search(pat, text):
            errors.append("存在失败环节却输出汇总表格/汇总块（命中规则 %s），异常中断一律走【异常】统一模板" % pat)
    checklist.append("失败场景无汇总表格")


def check_no_invented_stats(node, text, result, errors, checklist):
    """禁止虚构出参不存在的全局统计值（环节4 专属，其余环节跳过）。"""
    if node != "test":
        checklist.append("非环节4，跳过统计值校验")
        return
    scenes = result.get("testScenes") or []
    scene_counts = []
    for s in scenes:
        try:
            scene_counts.append(int(s.get("testCaseCount", 0)))
        except (TypeError, ValueError):
            scene_counts.append(0)
    total_declared = sum(scene_counts)
    # 抓取"N/M 用例通过""N/M条"这类聚合统计，N+M 必须可由场景级字段核对
    for m in re.finditer(r"(\d+)\s*/\s*(\d+)\s*(?:用例|条)", text):
        got, total = int(m.group(1)), int(m.group(2))
        if total != total_declared:
            errors.append(
                "聚合统计 %d/%d 与出参场景级 testCaseCount 合计 %d 不符（禁止虚构全局统计值）"
                % (got, total, total_declared))
        elif got > total:
            errors.append("聚合统计 %d/%d 通过数大于总数" % (got, total))
    if re.search(r"用例总数", text) and "（各场景合计）" not in text and scenes:
        errors.append("输出含'用例总数'但未注明'（各场景合计）'")
    checklist.append("无虚构聚合统计值")


def check_scene_rows(node, text, result, errors, checklist):
    """环节4 场景行数=出参 testScenes 数，场景名逐字引用。仅在输出含通过性结果表（含场景名表格或
    受理验证小节）时核对场景行存在性；E26 等异常中断输出（无结果表）不核对，避免误报。"""
    if node != "test":
        checklist.append("非环节4，跳过场景行校验")
        return
    scenes = result.get("testScenes") or []
    names = [s.get("testSceneName", "") for s in scenes]
    has_result_table = bool(re.search(r"\|\s*测试类型\s*\|", text)) or bool(re.search(r"\*\*受理验证[：:]\*\*", text))
    if has_result_table:
        for name in names:
            if name and name not in text:
                errors.append("出参场景 %s 未在输出场景表中出现（禁止虚构/合并行）" % name)
    checklist.append("场景行与出参 testScenes 一致")


def check_e26_mute(node, text, result, plan, errors, checklist):
    """E26 预校验：offerName 与 plan_json 套餐名称不一致 → 输出必须为中断模板（无通过性明细）。"""
    if node != "test":
        checklist.append("非环节4，跳过 E26 校验")
        return
    offer_name = str(result.get("offerName", ""))
    plan_name = ""
    if plan:
        fields = plan.get("fields") or []
        for f in fields:
            fname = str(f.get("field_name", "") or f.get("fieldName", ""))
            if fname in ("产品名称", "套餐名称", "销售品名称"):
                plan_name = str(f.get("field_value", "") or f.get("fieldValue", ""))
                break
    e26_hit = bool(plan_name and offer_name and plan_name != offer_name and plan_name not in offer_name)
    if not e26_hit:
        checklist.append("E26 预校验一致（offerName 与 plan 套餐名称相符）")
        return
    # E26 命中：输出必须为 ❌ 且无通过性明细
    if "❌ 执行失败" not in text:
        errors.append("E26 命中（出参 offerName=%s 与被测配置=%s 不一致）但输出非 ❌ 执行失败标题头" % (offer_name, plan_name))
    for pat, label in E26_FORBIDDEN_PATTERNS:
        if re.search(pat, text):
            errors.append("E26 命中但输出包含通过性明细（%s），禁止基于不一致数据输出结果" % label)
    if "被测一致性" not in text or "【异常】" not in text:
        errors.append("E26 命中但缺少【异常】统一模板/被测一致性原因说明")
    checklist.append("E26 中断输出形态（仅 report_url 行 + 【异常】模板）")


def check_fee_rows(node, text, result, errors, checklist):
    """环节3 比对表：空值行省略（两侧皆空不输出）、有值行存在、行数与省略说明一致。"""
    if node != "fee":
        checklist.append("非环节3，跳过比对表校验")
        return
    compare_list = result.get("compare_list") or []
    if not compare_list:
        checklist.append("compare_list 为空，跳过")
        return
    both_empty = 0
    valued_rows = []
    for item in compare_list:
        req = str(item.get("requirement_desc", "") or "").strip()
        bill = str(item.get("billing_desc", "") or "").strip()
        if not req and not bill:
            both_empty += 1
        else:
            valued_rows.append(str(item.get("project_name", "")))
    # 两侧皆空行禁止出现占位
    for item in compare_list:
        req = str(item.get("requirement_desc", "") or "").strip()
        bill = str(item.get("billing_desc", "") or "").strip()
        if not req and not bill and str(item.get("project_name", "")) in text:
            # 项目名出现在输出中但该行两侧皆空 → 可能输出了空值行
            line = [l for l in text.splitlines() if str(item.get("project_name", "")) in l]
            for l in line:
                if l.strip().startswith("|") and not l.strip().endswith("|"):
                    continue
            # 仅提示级：项目名出现即核对是否为空值行
            errors.append(
                "比对项 %s 两侧皆空（应整行省略），但输出中出现了该行，违反空值行省略规则"
                % item.get("project_name", ""))
    # E27 判定与固定文案核对
    half = (len(compare_list) + 1) // 2
    if both_empty >= half:
        if "系统侧未返回完整比对明细" not in text or "billing_verify" not in text:
            errors.append("触发 E27（%d/%d 两侧皆空）但校准结论缺少 E27 固定文案" % (both_empty, len(compare_list)))
        m = re.search(r"（\s*(\d+)\s*/\s*(\d+)项为空\s*）", text)
        if m and (int(m.group(1)) != both_empty or int(m.group(2)) != len(compare_list)):
            errors.append("E27 文案空行数 %s/%s 与实际 %d/%d 不符" % (m.group(1), m.group(2), both_empty, len(compare_list)))
    # 有值行存在性：一侧有值的项目须出现在输出（除 E27 外，E27 也要求输出有值行）
    for name in valued_rows:
        if name and name not in text:
            errors.append("比对项 %s 一侧有值（应照常输出），但输出中缺失" % name)
    checklist.append("比对表空值行省略 + E27 文案核对")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--node", required=True, choices=["config", "spec", "fee", "test"])
    p.add_argument("--result-file", required=True, help="本环节出参工件路径")
    p.add_argument("--output-file", required=True, help="拟输出给用户的文本草稿路径")
    p.add_argument("--plan-file", default="", help="plan_json 工件路径（环节4 E26 校验用）")
    args = p.parse_args()

    errors, checklist = [], []
    try:
        result = _read_json(args.result_file)
    except Exception as e:
        print(json.dumps({"resultCode": "FILE_ERROR", "resultMsg": "出参工件读取失败：%s" % e}, ensure_ascii=False))
        sys.exit(2)
    try:
        text = _read_text(args.output_file)
    except Exception as e:
        print(json.dumps({"resultCode": "FILE_ERROR", "resultMsg": "输出草稿读取失败：%s" % e}, ensure_ascii=False))
        sys.exit(2)
    plan = None
    if args.plan_file:
        try:
            plan = _read_json(args.plan_file)
        except Exception:
            plan = None  # plan 缺失时跳过 E26 深校验，仅做形态校验

    check_header(args.node, text, errors, checklist)
    check_no_summary_table_when_failed(args.node, text, errors, checklist)
    check_no_invented_stats(args.node, text, result, errors, checklist)
    check_scene_rows(args.node, text, result, errors, checklist)
    check_fee_rows(args.node, text, result, errors, checklist)
    check_e26_mute(args.node, text, result, plan, errors, checklist)

    if errors:
        print(json.dumps({"resultCode": "VALIDATE_FAIL", "checklist": checklist, "errors": errors},
                         ensure_ascii=False, indent=2))
        sys.exit(1)
    print(json.dumps({"resultCode": "0", "resultMsg": "校验通过", "checklist": checklist},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
