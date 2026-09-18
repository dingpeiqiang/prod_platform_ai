#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""输出前程序化校验脚本（SKILL.md 纪律1 强制兜底，validate_output V1.2）。

在环节3~7 正式输出给用户前运行，核对输出内容与出参工件的一致性：
  - 标题头形态封闭（仅 ✅ 执行成功 / ❌ 执行失败 两种，禁止自造"执行中断"等；
    V1.2 对齐九环节全局编号 3/4/5/6/7）；
  - 禁止虚构全局统计值（跨场景加总"10/10 通过"等出参不存在的数字）；
  - E26 中断场景禁止输出通过性明细（用例统计/三大验证/受理凭证/上线结论/汇总表格）；
  - 异常中断场景禁止输出汇总表格；
  - 环节4 场景行数=出参 testScenes 数（禁止虚构场景行/合并行）；
  - 环节3 比对表行数与空值行省略规则核对；
  - V1.1 融合组扩展：六列 plan_md 表头白名单；compare_list 按 member_role 分组核对，
    E27 阈值逐成员内计算；E26 组核对（主 offerName + 成员角色集合与 plan_json 组结构一致）。

用法（V1.2 单次全量校验 --node all，执行主干四环节跑完后一次性渲染全文并一次校验，
不再逐环节独立渲染/校验，减少 LLM 渲染与校验子进程调用）：
  python validate_output.py --node all --workdir <会话可写目录> --req-id PLANxxx --output-file draft.md
单环节兼容（错误定位/细分场景，V1.1 行为不变）：
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
import os
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

# 合法标题头形态（SKILL.md 九环节总表 + flow-B 输出结构总纪律：封闭两种）
# V1.2 对齐九环节全局编号：环节3/4/5/6/7（旧"环节1/4"短名头已废弃）
HEADER_OK = [
    "【环节3/9·销售品智能配置】✅ 执行成功", "【环节3/9·销售品智能配置】❌ 执行失败",
    "【环节4/9·配置规格稽核】✅ 执行成功", "【环节4/9·配置规格稽核】❌ 执行失败",
    "【环节5/9·资费校准】✅ 执行成功", "【环节5/9·资费校准】❌ 执行失败",
    "【环节6/9·销售品自动测试】✅ 执行成功", "【环节6/9·销售品自动测试】❌ 执行失败",
    "【环节7/9·受理验证】✅ 执行成功", "【环节7/9·受理验证】❌ 执行失败",
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

# V1.1 融合组：合法 plan_md 表头白名单（五列=单商品 V3.0，六列=融合组 V4.0 主商品行加粗）
PLAN_HEADER_OK = [
    "| 模块 | 分类 | 字段名称 | 字段值 | 备注 |",
    "| 商品 | 模块 | 分类 | 字段名称 | 字段值 | 备注 |",
]


def check_plan_header(node, text, errors, checklist):
    """plan_md 表头白名单核对：出口B 输出含执行方案表格时，表头必须是白名单两种之一。"""
    # 六列优先判定（六列表头含"| 模块 |"子串，必须先于五列匹配）
    if PLAN_HEADER_OK[1] in text:
        checklist.append("plan_md 表头合法（六列融合组）")
        return
    if PLAN_HEADER_OK[0] in text:
        checklist.append("plan_md 表头合法（五列单商品）")
        return
    if re.search(r"\|\s*模块\s*\|", text) or re.search(r"\|\s*商品\s*\|\s*模块\s*\|", text):
        errors.append("执行方案表头非法（仅允许五列单商品/六列融合组白名单表头）")
    else:
        checklist.append("无执行方案表格，跳过表头校验")


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


# V12.1 全量文档结构化完整性：full-success 输出必须包含全部 5 个结构化环节标题头（3/4/5/6/7），
# 防"一次性渲染"被误读为合并/平铺而丢失各环节结构化分块。
EXPECTED_SUCCESS_HEADERS = [
    "【环节3/9·销售品智能配置】✅ 执行成功",
    "【环节4/9·配置规格稽核】✅ 执行成功",
    "【环节5/9·资费校准】✅ 执行成功",
    "【环节6/9·销售品自动测试】✅ 执行成功",
    "【环节7/9·受理验证】✅ 执行成功",
]


def check_full_structure_all(text, errors, checklist):
    """仅用于 --node all 且为 full-success 场景：必须完整呈现 5 个环节标题头。
    任一环节 ❌（走【异常】模板，无汇总块）或非全量输出时不强制。"""
    is_full_success = ("【执行主干全部完成】" in text
                       and "❌ 执行失败" not in text
                       and "✅ 执行成功" in text)
    if not is_full_success:
        checklist.append("非 full-success 场景，跳过全环节结构完整性校验")
        return
    missing = [h for h in EXPECTED_SUCCESS_HEADERS if h not in text]
    if missing:
        names = "、".join(h.split("】")[0] + "】" for h in missing)
        errors.append("full-success 输出缺少结构化环节标题头（防环节结构化丢失）：%s" % names)
    else:
        checklist.append("全环节结构化标题头完整（环节3/4/5/6/7）")



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
    """E26 预校验：offerName 与 plan_json 套餐名称不一致 → 输出必须为中断模板（无通过性明细）。
    V1.1 融合组扩展：plan_json 为组结构时，出参成员角色（offer_group_check.members[].role，
    缺席时回退 offer_group）与 plan_json member_offers 角色集合须一致，不一致 → E26 中断。"""
    if node != "test":
        checklist.append("非环节4，跳过 E26 校验")
        return
    offer_name = str(result.get("offerName", ""))
    plan_name = ""
    plan_member_roles = None
    if plan:
        # V4.0 组结构 plan_json：逐成员提取
        if isinstance(plan.get("main_offer"), dict):
            plan_member_roles = [str(m.get("role", "")) for m in (plan.get("member_offers") or [])
                                 if isinstance(m, dict)]
            fields = plan["main_offer"].get("fields") or []
        else:
            fields = plan.get("fields") or []
        for f in fields:
            fname = str(f.get("field_name", "") or f.get("fieldName", ""))
            if fname in ("产品名称", "套餐名称", "销售品名称"):
                plan_name = str(f.get("field_value", "") or f.get("fieldValue", ""))
                break
    e26_hit = bool(plan_name and offer_name and plan_name != offer_name and plan_name not in offer_name)
    # 组核对（仅组结构 plan 且主名一致时进一步核对成员角色集合）
    group_mismatch = False
    if not e26_hit and plan_member_roles is not None:
        group_check = result.get("offer_group_check") or result.get("offer_group") or {}
        out_roles = []
        if isinstance(group_check, dict):
            members = group_check.get("members")
            if isinstance(members, list):
                out_roles = [str(m.get("role", "")) for m in members if isinstance(m, dict)]
        if out_roles and sorted(r for r in out_roles if r) != sorted(r for r in plan_member_roles if r):
            group_mismatch = True
            errors.append(
                "E26 组核对：出参成员角色 %s 与 plan_json member_offers 角色 %s 不一致（成员构成以数据源为准，"
                "禁止基于不一致数据输出结果）" % (out_roles, plan_member_roles))
    if not e26_hit and not group_mismatch:
        checklist.append("E26 预校验一致（offerName 与 plan 套餐名称相符" +
                         ("，组角色集合一致）" if plan_member_roles is not None else "）"))
        return
    # E26 命中：输出必须为 ❌ 且无通过性明细
    if "❌ 执行失败" not in text:
        if e26_hit:
            errors.append("E26 命中（出参 offerName=%s 与被测配置=%s 不一致）但输出非 ❌ 执行失败标题头" % (offer_name, plan_name))
        else:
            errors.append("E26 组核对命中但输出非 ❌ 执行失败标题头")
    for pat, label in E26_FORBIDDEN_PATTERNS:
        if re.search(pat, text):
            errors.append("E26 命中但输出包含通过性明细（%s），禁止基于不一致数据输出结果" % label)
    if "被测一致性" not in text or "【异常】" not in text:
        errors.append("E26 命中但缺少【异常】统一模板/被测一致性原因说明")
    checklist.append("E26 中断输出形态（仅 report_url 行 + 【异常】模板）")


def check_fee_rows(node, text, result, errors, checklist):
    """环节3 比对表：空值行省略（两侧皆空不输出）、有值行存在、行数与省略说明一致。
    V1.1 融合组：compare_list 项含 member_role 键时逐成员分组核对，E27 阈值逐成员内计算
    （防多成员稀释误判）；单商品（无 member_role 或全部缺省）行为与 V1.0 一致。"""
    if node != "fee":
        checklist.append("非环节3，跳过比对表校验")
        return
    compare_list = result.get("compare_list") or []
    if not compare_list:
        checklist.append("compare_list 为空，跳过")
        return
    # 按成员分组（V1.1）：member_role 缺省统一归入"主卡套餐"（与方案 §4.2 缺省口径一致）
    grouped = {}
    for item in compare_list:
        grouped.setdefault(str(item.get("member_role") or "主卡套餐"), []).append(item)
    multi_member = len(grouped) > 1 or "主卡套餐" not in grouped
    for role, items in sorted(grouped.items()):
        if multi_member:
            # 融合组：每个成员的比对行须在该成员的小节/分组行中存在（成员名出现在行内）
            valued = [str(i.get("project_name", "")) for i in items
                      if str(i.get("requirement_desc", "") or "").strip() or str(i.get("billing_desc", "") or "").strip()]
            missing = [n for n in valued if n and n not in text]
            if missing:
                errors.append("成员 %s 有值比对项 %s 未在输出中出现（逐成员输出纪律）" % (role, "、".join(missing)))
            _check_e27_per_member(text, role, items, errors)
        else:
            _check_fee_rows_flat(text, items, errors)
    checklist.append("比对表空值行省略 + E27 文案核对" + ("（逐成员）" if multi_member else ""))


def _check_fee_rows_flat(text, compare_list, errors):
    """V1.0 单商品比对表核对逻辑原样保留（组结构缺席时行为零变化）。"""
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


def _check_e27_per_member(text, role, items, errors):
    """V1.1：E27 阈值逐成员内计算——某成员空值行达该成员半数及以上才按成员维度要求 E27 文案
    （文案须含成员定位），禁止跨成员加总稀释或误判。"""
    both_empty = sum(1 for i in items
                     if not str(i.get("requirement_desc", "") or "").strip()
                     and not str(i.get("billing_desc", "") or "").strip())
    if both_empty < (len(items) + 1) // 2:
        return
    m = re.search(r"（\s*(\d+)\s*/\s*(\d+)项为空\s*）", text)
    if m and (int(m.group(1)) != both_empty or int(m.group(2)) != len(items)):
        errors.append("成员 %s 的 E27 文案空行数 %s/%s 与该成员实际 %d/%d 不符（E27 逐成员判定）"
                      % (role, m.group(1), m.group(2), both_empty, len(items)))
    if "系统侧未返回完整比对明细" not in text or "billing_verify" not in text:
        errors.append("成员 %s 触发 E27（%d/%d 两侧皆空）但校准结论缺少 E27 固定文案"
                      % (role, both_empty, len(items)))


def _run_node_checks(node, text, result, plan, errors, checklist):
    """执行单环节相关校验（node 为实际业务环节名，用于各 check 内部分支）。
    公共/形态类校验（header/plan 表头/失败禁汇总表）另由调用方统一执行一次。
    仅调用本环节专属校验项，避免无关校验的空跑与清单噪音。
    若 result 为 None（该环节出参缺失）则跳过该环节专属校验，避免误报。"""
    if result is None:
        checklist.append("环节 %s 出参缺失，跳过其专属校验（仅形态校验）" % node)
        return
    if node == "fee":
        check_fee_rows("fee", text, result, errors, checklist)
    elif node == "test":
        check_no_invented_stats("test", text, result, errors, checklist)
        check_scene_rows("test", text, result, errors, checklist)
        check_e26_mute("test", text, result, plan, errors, checklist)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--node", required=True, choices=["config", "spec", "fee", "test", "all"])
    p.add_argument("--result-file", default="", help="单环节模式：本环节出参工件路径")
    p.add_argument("--output-file", required=True, help="拟输出给用户的文本草稿路径")
    p.add_argument("--plan-file", default="", help="单环节模式：plan_json 工件路径（环节4 E26 校验用）")
    p.add_argument("--workdir", default="", help="all 模式：会话可写目录（自动读取 result_<node>_<req_id>.json）")
    p.add_argument("--req-id", default="", help="all 模式：需求单号（工件命名 result_<node>_<req_id>.json）")
    args = p.parse_args()

    errors, checklist = [], []
    try:
        text = _read_text(args.output_file)
    except Exception as e:
        print(json.dumps({"resultCode": "FILE_ERROR", "resultMsg": "输出草稿读取失败：%s" % e}, ensure_ascii=False))
        sys.exit(2)

    if args.node == "all":
        if not args.workdir or not args.req_id:
            print(json.dumps({"resultCode": "PARAM_MISSING",
                              "resultMsg": "--node all 需同时提供 --workdir 与 --req-id"},
                             ensure_ascii=False))
            sys.exit(2)
        results = {}
        # 环节工件命名：config 用 config_result_<req_id>.json（run_pipeline 唯一写入口），
        # spec/fee/test 用 result_<node>_<req_id>.json
        node_result_file = {
            "config": "config_result_%s.json",
            "spec": "result_spec_%s.json",
            "fee": "result_fee_%s.json",
            "test": "result_test_%s.json",
        }
        for n in ("config", "spec", "fee", "test"):
            rp = os.path.join(args.workdir, node_result_file[n] % args.req_id)
            try:
                results[n] = _read_json(rp)
            except Exception as e:
                print(json.dumps({"resultCode": "FILE_ERROR",
                                  "resultMsg": "出参工件读取失败（%s）：%s" % (rp, e)}, ensure_ascii=False))
                sys.exit(2)
        plan_path = os.path.join(args.workdir, "plan_json_%s.json" % args.req_id)
        plan = None
        try:
            plan = _read_json(plan_path)
        except Exception:
            plan = None  # plan 缺失时跳过 E26 深校验，仅做形态校验
        # 公共/形态校验一次；各环节专属校验逐环节合并到同一全量文档上
        check_header("all", text, errors, checklist)
        check_plan_header("all", text, errors, checklist)
        check_full_structure_all(text, errors, checklist)
        check_no_summary_table_when_failed("all", text, errors, checklist)
        for n in ("fee", "test"):
            _run_node_checks(n, text, results.get(n), plan, errors, checklist)
        # config/spec 无专属校验项，仅需存在有效标题头（已由 check_header 覆盖）
        checklist.append("环节 config/spec 无专属校验项（标题头由 check_header 覆盖）")
    else:
        if not args.result_file:
            print(json.dumps({"resultCode": "PARAM_MISSING",
                              "resultMsg": "单环节模式需提供 --result-file"}, ensure_ascii=False))
            sys.exit(2)
        try:
            result = _read_json(args.result_file)
        except Exception as e:
            print(json.dumps({"resultCode": "FILE_ERROR", "resultMsg": "出参工件读取失败：%s" % e}, ensure_ascii=False))
            sys.exit(2)
        plan = None
        if args.plan_file:
            try:
                plan = _read_json(args.plan_file)
            except Exception:
                plan = None  # plan 缺失时跳过 E26 深校验，仅做形态校验
        check_header(args.node, text, errors, checklist)
        check_plan_header(args.node, text, errors, checklist)
        check_no_summary_table_when_failed(args.node, text, errors, checklist)
        _run_node_checks(args.node, text, result, plan, errors, checklist)

    if errors:
        print(json.dumps({"resultCode": "VALIDATE_FAIL", "checklist": checklist, "errors": errors},
                         ensure_ascii=False, indent=2))
        sys.exit(1)
    print(json.dumps({"resultCode": "0", "resultMsg": "校验通过", "checklist": checklist},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
