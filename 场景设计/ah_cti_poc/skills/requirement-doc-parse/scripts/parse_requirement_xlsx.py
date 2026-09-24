#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""需求文档（.xlsx）解析 Skill —— 产销品加载 AI 应用（统一方案 6.6 / G1）。

职责边界（纪律）：
    只做**确定性结构归一**（识别 sheet / 定位表头 / 取值 / 关系枚举归一），
    不推理、不补全、不改写原始值；字段三归属（映射入 24 字段 / 落模板 schema /
    冗余入报告）由下游 wf_sub_01 模板轨完成，故此处保留全量原始列。

输入：需求文档 .xlsx（如《新增四档5G畅享融合套餐销售品.xlsx》），含
    「可选包」宽表（每行一个新增销售品）+「关联关系」A/Z 端关系表 + 其它 sheet（忽略）。

输出（stdout，UTF-8 JSON）：
    {
      "resultCode": "0" | "E41",
      "resultMsg": "...",
      "count": N,
      "offers":    [{offer_seq, ofr_id, offer_name, sheet_name, fields{原始列名: 值}}],
      "relations": [{a_ofr_id, a_name, relation, relation_label, z_ofr_id, z_name}],
      "source_sheets": ["可选包", "关联关系", "Sheet3"],
      "requirement_text": "渲染给 wf_sub_00 的结构化文本（多品清单 + 品间关联关系）"
    }

与后端 RequirementDocService 的契约一致（等价端点 2e
/api/v1/appstore/requirement/parse-xlsx）；本脚本为 skill 内嵌纯 Python 实现（openpyxl），
供无后端环境下由智能体直接解析。

用法：
    python parse_requirement_xlsx.py <需求文档.xlsx> [--pretty]
    python parse_requirement_xlsx.py <需求文档.xlsx> --text-only   # 只输出 requirement_text
"""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any, Dict, List, Optional, Tuple

try:
    from openpyxl import load_workbook
except ImportError:  # pragma: no cover - 环境缺失提示
    sys.stderr.write(
        "[requirement-doc-parse] 缺少 openpyxl，请先安装：pip install openpyxl\n"
    )
    raise

# ---------------------------------------------------------------------------
# 表头候选（与后端 RequirementDocService 常量保持一一对应）
# ---------------------------------------------------------------------------

OFFER_ID_HEADERS = ("OFR_ID", "OFFER_ID", "销售品ID", "销售品编码", "商品编码")
OFFER_NAME_HEADERS = ("套餐客户化名称", "套餐名称", "销售品名称", "商品名称")

REL_A_ID_HEADERS = ("A_OFR_ID", "A端销售品ID", "A端ID")
REL_A_NAME_HEADERS = ("A_OFR_NAME", "A端名称", "A端销售品名称")
REL_TYPE_HEADERS = ("关系", "关系类型", "关联类型")
REL_Z_ID_HEADERS = ("Z_OFR_ID", "Z端销售品ID", "Z端ID")
REL_Z_NAME_HEADERS = ("Z_OFR_NAME", "Z端名称", "Z端销售品名称")

# 可选包 / 关联关系 sheet 名候选（模糊匹配，大小写不敏感）
OFFER_SHEET_HINTS = ("可选包", "销售品", "商品", "offer", "package")
RELATION_SHEET_HINTS = ("关联关系", "关系", "relation")

# 关联关系枚举（统一方案 7.2）
RELATION_ENUM = ("100000", "110000", "120000", "130000", "200000", "410000", "100100")

# 中文标签 → 数字枚举（需求文档「关系」列常填中文标签）
RELATION_LABEL_TO_ENUM = {
    "可选依赖": "100000",
    "强制依赖": "110000",
    "默认依赖": "120000",
    "赠送": "130000",
    "赠送关系": "130000",
    "互斥": "200000",
    "不可迁转": "410000",
    "不可迁转关系": "410000",
    "自主版": "100100",
    "自主版关系": "100100",
}

# 关系枚举 → 中文标签（渲染 requirement_text 用）
RELATION_ENUM_TO_LABEL = {
    "100000": "可选依赖",
    "110000": "强制依赖",
    "120000": "默认依赖",
    "130000": "赠送",
    "200000": "互斥",
    "410000": "不可迁转",
    "100100": "自主版",
}


# ---------------------------------------------------------------------------
# 归一 / 匹配工具
# ---------------------------------------------------------------------------

def _norm(value: Any) -> str:
    """大小写不敏感、去空白归一（对齐后端 norm()）。"""
    if value is None:
        return ""
    return str(value).strip().lower().replace(" ", "")


def _str(value: Any) -> str:
    """单元格值 → 去空白字符串；数字去除无意义的 .0（对齐后端 resolveCellValue 语义）。"""
    if value is None:
        return ""
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def _matches(value: Any, candidates: Tuple[str, ...]) -> bool:
    """候选模糊匹配：归一后相等或包含（对齐后端 matches()）。"""
    v = _norm(value)
    if not v:
        return False
    for c in candidates:
        cn = _norm(c)
        if cn and (v == cn or cn in v):
            return True
    return False


def _is_header_like(value: Any, candidates: Tuple[str, ...]) -> bool:
    """表头行判定：归一后**完全相等**某个候选（避免把含「关系」的数据值误判为表头）。"""
    v = _norm(value)
    if not v:
        return False
    return any(v == _norm(c) for c in candidates)


def _column_index(headers: List[str], candidates: Tuple[str, ...]) -> int:
    """返回首个命中候选的列下标（精确优先，其次包含匹配）；未命中 -1。"""
    for i, h in enumerate(headers):
        hn = _norm(h)
        if hn and any(hn == _norm(c) for c in candidates):
            return i
    for i, h in enumerate(headers):
        hn = _norm(h)
        if not hn:
            continue
        for c in candidates:
            cn = _norm(c)
            if cn and (cn in hn or hn in cn):
                return i
    return -1


def _normalize_relation(raw: Any) -> str:
    """关系列归一：中文标签 → 数字枚举；已是数字或未知值原样返回（越界由调用方校验）。"""
    v = _str(raw)
    if not v:
        return ""
    if v in RELATION_ENUM:
        return v
    return RELATION_LABEL_TO_ENUM.get(v, v)


def _is_blank_row(row: List[Any]) -> bool:
    return all(c is None or str(c).strip() == "" for c in row)


# ---------------------------------------------------------------------------
# sheet 定位
# ---------------------------------------------------------------------------

def _locate_sheets(workbook) -> Tuple[Optional[List[List[str]]], Optional[str],
                                      Optional[List[List[str]]], Optional[str], List[str]]:
    """遍历 sheet，返回 (可选包行、可选包名、关联关系行、关联关系名、全部 sheet 名)。"""
    offer_rows: Optional[List[List[str]]] = None
    offer_name: Optional[str] = None
    relation_rows: Optional[List[List[str]]] = None
    relation_name: Optional[str] = None
    source_sheets: List[str] = []

    for ws in workbook.worksheets:
        sheet = ws.title or ""
        source_sheets.append(sheet)
        rows = _sheet_rows(ws)
        if not rows:
            continue
        headers = rows[0]
        if relation_rows is None and _matches(sheet, RELATION_SHEET_HINTS):
            relation_rows, relation_name = rows, sheet
        elif offer_rows is None and _matches(sheet, OFFER_SHEET_HINTS):
            offer_rows, offer_name = rows, sheet

    # 兜底：未按 sheet 名识别到可选包表时，取第一个含 OFR_ID 列的表
    if offer_rows is None:
        for ws in workbook.worksheets:
            rows = _sheet_rows(ws)
            if rows and _column_index(rows[0], OFFER_ID_HEADERS) >= 0:
                offer_rows, offer_name = rows, (ws.title or "")
                break

    return offer_rows, offer_name, relation_rows, relation_name, source_sheets


def _sheet_rows(ws) -> List[List[str]]:
    """工作表 → 二维字符串表（跳过全空行）。"""
    rows: List[List[str]] = []
    for raw in ws.iter_rows(values_only=True):
        if raw is None or _is_blank_row(list(raw)):
            continue
        rows.append([_str(c) for c in raw])
    return rows


# ---------------------------------------------------------------------------
# 抽取
# ---------------------------------------------------------------------------

def _extract_offers(rows: List[List[str]], sheet_name: str) -> List[Dict[str, Any]]:
    headers = rows[0]
    data = rows[1:]
    id_idx = _column_index(headers, OFFER_ID_HEADERS)
    name_idx = _column_index(headers, OFFER_NAME_HEADERS)
    offers: List[Dict[str, Any]] = []
    seq = 0
    for row in data:
        if _is_blank_row(row):
            continue
        ofr_id = row[id_idx] if 0 <= id_idx < len(row) else ""
        name = row[name_idx] if 0 <= name_idx < len(row) else ""
        if not ofr_id and not name:
            continue
        if _is_header_like(ofr_id, OFFER_ID_HEADERS):
            continue  # 重复表头行
        seq += 1
        offers.append({
            "offer_seq": seq,
            "ofr_id": ofr_id,
            "offer_name": name,
            "sheet_name": sheet_name,
            "fields": _row_to_fields(headers, row),
        })
    return offers


def _row_to_fields(headers: List[str], row: List[str]) -> Dict[str, str]:
    """整行按表头映射为 {列名: 值}；列名空白时用 col{n} 占位，保证不丢列。"""
    fields: Dict[str, str] = {}
    for i, value in enumerate(row):
        key = headers[i].strip() if i < len(headers) and headers[i].strip() else f"col{i + 1}"
        if key not in fields:
            fields[key] = value
    return fields


def _extract_relations(rows: List[List[str]], sheet_name: str) -> List[Dict[str, Any]]:
    headers = rows[0]
    data = rows[1:]
    a_id_idx = _column_index(headers, REL_A_ID_HEADERS)
    a_name_idx = _column_index(headers, REL_A_NAME_HEADERS)
    type_idx = _column_index(headers, REL_TYPE_HEADERS)
    z_id_idx = _column_index(headers, REL_Z_ID_HEADERS)
    z_name_idx = _column_index(headers, REL_Z_NAME_HEADERS)

    relations: List[Dict[str, Any]] = []
    seen = set()
    for row in data:
        if _is_blank_row(row):
            continue
        a_id = row[a_id_idx] if 0 <= a_id_idx < len(row) else ""
        z_id = row[z_id_idx] if 0 <= z_id_idx < len(row) else ""
        raw_type = row[type_idx] if 0 <= type_idx < len(row) else ""
        if _is_header_like(a_id, REL_A_ID_HEADERS) or _is_header_like(raw_type, REL_TYPE_HEADERS):
            continue  # 表头行 / 重复表头行
        if not a_id and not z_id:
            continue  # 仅说明文字、无 A/Z 端
        if not a_id and not z_id and not raw_type:
            continue
        rel = {
            "a_ofr_id": a_id,
            "a_name": row[a_name_idx] if 0 <= a_name_idx < len(row) else "",
            "relation": _normalize_relation(raw_type),
            "relation_label": raw_type,
            "z_ofr_id": z_id,
            "z_name": row[z_name_idx] if 0 <= z_name_idx < len(row) else "",
        }
        key = f"{rel['a_ofr_id']}|{rel['relation']}|{rel['z_ofr_id']}"
        if key in seen:
            continue
        seen.add(key)
        relations.append(rel)
    return relations


# ---------------------------------------------------------------------------
# requirement_text 渲染（供 wf_sub_00 LLM 抽取多品）
# ---------------------------------------------------------------------------

def render_requirement_text(offers: List[Dict[str, Any]],
                            relations: List[Dict[str, Any]]) -> str:
    """将结构化需求渲染为自然语言文本，作为 wf_sub_00 的 requirement_text 入参。

    含：需求概览 + 多品清单（逐品字段行）+ 品间关联关系。字段以「列名: 值」原样列出，
    不做三归属裁剪（由下游模板轨处理），确保 LLM 抽取不漏信息。
    """
    lines: List[str] = []
    lines.append("【需求文档解析结果】本次需求文档共包含 %d 个新增销售品。" % len(offers))
    lines.append("")

    for offer in offers:
        seq = offer.get("offer_seq")
        lines.append("■ 销售品 %s（offer_seq=%s）" % (offer.get("offer_name") or "未命名", seq))
        if offer.get("ofr_id"):
            lines.append("  销售品ID（OFR_ID）：%s" % offer["ofr_id"])
        fields = offer.get("fields") or {}
        if fields:
            lines.append("  字段明细：")
            for key, value in fields.items():
                if value is None or str(value).strip() == "":
                    continue
                lines.append("    %s：%s" % (key, value))
        lines.append("")

    if relations:
        lines.append("【品间关联关系】")
        for rel in relations:
            enum = rel.get("relation") or ""
            label = RELATION_ENUM_TO_LABEL.get(enum, rel.get("relation_label") or enum)
            lines.append(
                "  %s（%s） --%s(%s)--> %s（%s）" % (
                    rel.get("a_name") or "", rel.get("a_ofr_id") or "",
                    label, enum,
                    rel.get("z_name") or "", rel.get("z_ofr_id") or "",
                )
            )
    else:
        lines.append("【品间关联关系】无")

    return "\n".join(lines).strip()


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def _fail(message: str, source_sheets: Optional[List[str]] = None) -> Dict[str, Any]:
    return {
        "resultCode": "E41",
        "resultMsg": message,
        "count": 0,
        "offers": [],
        "relations": [],
        "source_sheets": source_sheets or [],
        "requirement_text": "",
    }


def parse(path: str) -> Dict[str, Any]:
    try:
        workbook = load_workbook(path, data_only=True, read_only=True)
    except Exception as exc:  # noqa: BLE001
        return _fail("需求文档解析失败：%s" % exc)

    try:
        offer_rows, offer_name, relation_rows, relation_name, source_sheets = _locate_sheets(workbook)
        if not source_sheets:
            return _fail("需求文档未识别到表格（需含【可选包】与【关联关系】sheet）")
        if offer_rows is None:
            return _fail("需求文档未识别到【可选包】sheet（需含 OFR_ID / 套餐名称 列）", source_sheets)

        offers = _extract_offers(offer_rows, offer_name or "")
        if not offers:
            return _fail("需求文档【可选包】sheet 无有效销售品行（offers 为空）")

        relations = _extract_relations(relation_rows, relation_name or "") if relation_rows else []

        # 关联关系枚举越界 → E41
        for rel in relations:
            enum = rel.get("relation") or ""
            if enum and enum not in RELATION_ENUM:
                return _fail(
                    "关联关系枚举越界：relation=%s（原始值「%s」，合法值 %s），请检查【关联关系】sheet"
                    % (enum, rel.get("relation_label"), list(RELATION_ENUM))
                )

        return {
            "resultCode": "0",
            "resultMsg": "success",
            "count": len(offers),
            "offers": offers,
            "relations": relations,
            "source_sheets": source_sheets,
            "requirement_text": render_requirement_text(offers, relations),
        }
    finally:
        workbook.close()


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="解析需求文档 .xlsx 为结构化需求（skill 内嵌实现）")
    parser.add_argument("xlsx", help="需求文档 .xlsx 路径")
    parser.add_argument("--pretty", action="store_true", help="缩进输出 JSON")
    parser.add_argument("--text-only", action="store_true", help="仅输出 requirement_text")
    args = parser.parse_args(argv)

    result = parse(args.xlsx)
    if args.text_only:
        sys.stdout.write(result.get("requirement_text", ""))
        sys.stdout.write("\n")
    else:
        json.dump(
            result,
            sys.stdout,
            ensure_ascii=False,
            indent=2 if args.pretty else None,
        )
        sys.stdout.write("\n")

    # 解析成功 exit 0；E41 仍输出结构体，但以非零码提示调用方
    return 0 if result.get("resultCode") == "0" else 2


if __name__ == "__main__":
    sys.exit(main())
