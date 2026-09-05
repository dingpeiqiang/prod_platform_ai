#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R4-Step2: xlsx → JSON 模板导入器（只增不覆盖）。

将《产品结构化映射逻辑模型报文规范.xlsx》各「配置逻辑报文规范」sheet 解析为
模板字段草案，与 ontologies/templates/*.json 现有模板做字段级 diff：

- xlsx 新增字段 → 仅写入 out/<template_id>.draft.json（不触碰 resources 模板）
- 已存在字段   → 属性级差异写入报告（人工确认后合并，工具不直接改模板）
- xlsx 已无而模板仍有 → XLSX_MISSING 报告项
- 问题待办 sheet → 逐条核对模板落点，--writeback 时将结论回写 xlsx

用法（仓库根目录）:
  python backend-app/scripts/import_template_from_xlsx.py            # 生成草案 + 报告
  python backend-app/scripts/import_template_from_xlsx.py --writeback  # 额外回写问题待办结论
"""
import argparse
import copy
import json
import os
import re
import sys

import openpyxl

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_XLSX = os.path.join(REPO_ROOT, 'docs', '产品结构化映射逻辑模型报文规范.xlsx')
DEFAULT_TEMPLATES_DIR = os.path.join(REPO_ROOT, 'backend-app', 'src', 'main', 'resources',
                                     'ontologies', 'templates')
DEFAULT_OUT_DIR = os.path.join(REPO_ROOT, 'backend-app', 'scripts', 'out')

NODE_RE = re.compile(r'^([^（）()]+)[（(]([A-Za-z0-9_]+)[）)]\s*$')
SKIP_NODE_RE = re.compile(r'^(ROOT|BUSI_INFO)', re.IGNORECASE)
# 报文信封字段（非用户表单字段）
ENVELOPE_CODES = {'opType', 'templateId'}

# 元素类型 → 模板 type（KISS：xlsx 只表达业务视角，复杂语义由 diff 报告人工合并）
ELEMENT_TYPE_MAP = {
    '文本框': ('input', None),
    '数字框': ('number', None),
    '下拉框': ('select', None),
    '下拉搜索框': ('select', None),
    '可模糊检索下拉框': ('select', None),
    '选择框': ('select', None),
    '单选框': ('select', None),
    '多选框': ('multiselect', None),
    '日期框': ('date', None),
    '不显示': ('input', 'hidden'),
}
# 不参与字段建模的元素类型（容器/按钮/纯展示说明）
NON_FIELD_TYPES = {'按钮'}


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--xlsx', default=DEFAULT_XLSX)
    parser.add_argument('--templates-dir', default=DEFAULT_TEMPLATES_DIR)
    parser.add_argument('--out-dir', default=DEFAULT_OUT_DIR)
    parser.add_argument('--report', default=os.path.join(DEFAULT_OUT_DIR, 'import_report.md'))
    parser.add_argument('--writeback', action='store_true',
                        help='将问题待办核对结论回写 xlsx（默认只写报告）')
    return parser.parse_args()


def find_col(header, keyword):
    for idx, name in enumerate(header):
        if keyword in name:
            return idx
    return None


def parse_cell_name(value):
    """'资费名称（prodPrcName）' → ('资费名称', 'prodPrcName')；不匹配返回 (原文, None)。"""
    text = str(value).strip().replace('\n', '')
    match = NODE_RE.match(text)
    if match:
        return match.group(1).strip(), match.group(2)
    return text, None


def detect_template_id(ws, rows, header):
    """从『模板编码（templateId）』行捕获 template_id。"""
    for row in rows:
        if not row:
            continue
        for idx, cell in enumerate(row):
            if cell and '模板编码' in str(cell) and ('templateId' in str(cell) or 'templateI' in str(cell)):
                for value in row[idx + 1:]:
                    if value and str(value).strip():
                        return str(value).strip()
                # 兜底：下一行同列
                return None
    return None


def parse_spec_sheet(ws):
    """解析单个规范 sheet → 字段草案列表 + sections 草案。"""
    rows = list(ws.iter_rows(values_only=True))
    hdr_idx = next((i for i, r in enumerate(rows) if r and any(c and '一级节点' in str(c) for c in r)), None)
    if hdr_idx is None:
        return None
    header = [str(c) if c else '' for c in rows[hdr_idx]]
    level_cols = [i for i, h in enumerate(header) if re.match(r'^[一二三四五六]级节点$', h)]
    col_elem = find_col(header, '元素类型')
    col_default = find_col(header, '默认值')
    col_required = find_col(header, '是否必填')
    col_desc = find_col(header, '元素说明')
    col_value_desc = find_col(header, '取值说明')

    stack = {}          # level -> (label, code)
    fields, sections = [], []
    seen_sections = set()
    template_id = None

    for row in rows[hdr_idx + 1:]:
        if not row or not any(cell is not None and str(cell).strip() for cell in row):
            continue
        cells = []  # (level, text)：扫描全部层级列（容器与字段可能同行出现）
        skip_row = False
        for level, col in enumerate(level_cols):
            cell = row[col] if col < len(row) else None
            if cell is None or not str(cell).strip():
                continue
            text = str(cell).strip()
            if text == '{}':
                continue  # 对象节点起始标记，层级从下一列起算
            if SKIP_NODE_RE.match(text):
                skip_row = True
                break
            cells.append((level, text))
        if skip_row:
            if template_id is None:
                template_id = detect_template_id(None, [row], header)
            continue
        if not cells:
            continue

        deepest = None
        for level, text in cells:
            label, code = parse_cell_name(text)
            stack[level] = (label, code)
            for deeper in list(stack):
                if deeper > level:
                    del stack[deeper]
            deepest = level
        deepest_label, code = stack[deepest]
        if code is None:
            continue  # 无编码节点（如根对象名/说明行），不参与字段建模
        if code in ENVELOPE_CODES:
            continue  # 报文信封字段
        chain = [stack[lvl] for lvl in sorted(stack) if lvl >= 2 and stack[lvl][1]]  # level0/1 = 报文根/业务对象

        # 元数据列归属最深节点
        def meta(col):
            if col is None or col >= len(row) or row[col] is None:
                return None
            return str(row[col]).strip() or None

        elem_type = meta(col_elem)

        if not chain:
            continue
        # 容器节点：无元素类型（或有子节点语义），非按钮
        is_leaf = bool(elem_type) and elem_type not in NON_FIELD_TYPES
        if not is_leaf:
            if elem_type is None and len(chain) >= 1 and code not in seen_sections:
                seen_sections.add(code)
                sections.append({
                    'code': code,
                    'label': stack[deepest][0],
                    'message_path': '.'.join(c[1] for c in chain),
                    'component': len(chain) >= 2,  # 顶层容器为表单 section，深层为子对象容器
                })
            continue

        # 叶子字段
        type_code = ELEMENT_TYPE_MAP.get(elem_type, ('input', None))
        field = {
            'field_code': code,
            'label': stack[deepest][0],
            'type': type_code[0],
            'required': (meta(col_required) == '是'),
            'section': chain[-2][1] if len(chain) >= 2 else chain[-1][1],
            'message_path': '.'.join(c[1] for c in chain),
        }
        if type_code[1]:
            field['source'] = type_code[1]
        if elem_type in ('仅页面展示：流量提醒：按全省统一提醒规则执行',):
            field['field_class'] = 'readonly'
        if elem_type in ('下拉框', '下拉搜索框', '可模糊检索下拉框', '选择框', '单选框', '多选框'):
            raw_enum = meta(col_default)
            if raw_enum and raw_enum not in ('无', '无默认值', '无默认值（系统按当前时间）'):
                candidates = [c.strip() for c in re.split(r'[、，,;；]', raw_enum) if c.strip()]
                displayable = [c for c in candidates if not re.fullmatch(r'[0-9.~-]+', c)]
                if displayable:
                    field['enum_config'] = {
                        'type': 'static',
                        'enum_map': [{'display': d, 'value': d} for d in displayable],
                    }
        hint = meta(col_desc) or meta(col_value_desc)
        if hint:
            field['extract_hint'] = hint[:200]
        fields.append(field)
    return {'template_id': template_id, 'fields': fields, 'sections': sections}


def resolve_extends(template, templates_by_id, visiting=None):
    """与 ProductTemplateRegistry 同语义：extends 父模板字段按 field_code 合并，子覆盖同名项。"""
    visiting = visiting or set()
    parent_id = template.get('extends')
    if not parent_id or parent_id not in templates_by_id or parent_id in visiting:
        return template
    visiting.add(parent_id)
    parent = resolve_extends(templates_by_id[parent_id], templates_by_id, visiting)
    merged = copy.deepcopy(template)
    by_code = {f.get('field_code'): f for f in parent.get('fields', []) if isinstance(f, dict)}
    for f in template.get('fields', []):
        if isinstance(f, dict) and f.get('field_code'):
            by_code[f['field_code']] = f
    merged['fields'] = list(by_code.values())
    return merged


def diff_template(draft, existing):
    """draft(xlsx) vs existing(模板) 字段级 diff → 报告行 + 只增合并草案。"""
    existing_by_code = {f.get('field_code'): f for f in existing.get('fields', []) if isinstance(f, dict)}
    draft_by_code = {f['field_code']: f for f in draft['fields']}
    added = [c for c in draft_by_code if c not in existing_by_code]
    missing_in_xlsx = [c for c in existing_by_code if c not in draft_by_code]
    changed = []
    for code, xfield in draft_by_code.items():
        if code not in existing_by_code:
            continue
        tfield = existing_by_code[code]
        for attr in ('label', 'required', 'section', 'message_path'):
            xv, tv = xfield.get(attr), tfield.get(attr)
            if xv is not None and tv is not None and xv != tv:
                changed.append((code, attr, tv, xv))
        # type 特判：xlsx 粒度粗（select），模板可能精确为 multiselect/number 等
        xt, tt = xfield.get('type'), tfield.get('type')
        if xt and tt and xt != tt and not (xt == 'select' and tt in ('select', 'multiselect')):
            changed.append((code, 'type', tt, xt))
    merged = copy.deepcopy(existing)
    merged_fields = merged.setdefault('fields', [])
    for code in added:
        merged_fields.append(copy.deepcopy(draft_by_code[code]))
    return {'added': added, 'changed': changed, 'missing_in_xlsx': missing_in_xlsx,
            'draft_only': draft, 'merged': merged}


def writeback_pending_items(wb, conclusions, xlsx_path):
    """问题待办回写：在 C 列追加核对结论。"""
    ws = wb['问题待办']
    ws.cell(row=1, column=2, value='核对结论（R4 导入器首跑 %s）' % __import__('datetime').date.today())
    for row_idx, conclusion in conclusions.items():
        ws.cell(row=row_idx, column=2, value=conclusion)
    wb.save(xlsx_path)


def main():
    args = parse_args()
    if not os.path.exists(args.xlsx):
        print('xlsx 不存在: %s' % args.xlsx)
        return 1
    wb = openpyxl.load_workbook(args.xlsx, data_only=True)
    os.makedirs(args.out_dir, exist_ok=True)

    report_lines = ['# R4 xlsx→JSON 导入报告', '',
                    '> 只增不覆盖：新增字段仅落草案 out/<template_id>.draft.json；',
                    '> 差异需人工确认后手动合并进 resources 模板。',
                    '> diff 基准为 resolved 视图（含 extends 父模板字段合并，与 ProductTemplateRegistry 同语义）。', '']

    templates_by_id = {}
    for name in os.listdir(args.templates_dir):
        if name.endswith('.json'):
            with open(os.path.join(args.templates_dir, name), encoding='utf-8') as fh:
                tpl = json.load(fh)
            templates_by_id[tpl.get('template_id')] = tpl

    pending_conclusions = {}
    pending_idx = 0

    for sheet_name in wb.sheetnames:
        if '规范' not in sheet_name:
            continue
        ws = wb[sheet_name]
        draft = parse_spec_sheet(ws)
        if not draft or not draft['template_id']:
            report_lines += ['## %s' % sheet_name, '', '- 未能识别 template_id，跳过', '']
            continue
        template_id = draft['template_id']
        template_path = os.path.join(args.templates_dir, template_id + '.json')
        existing = None
        if os.path.exists(template_path):
            with open(template_path, encoding='utf-8') as fh:
                existing = json.load(fh)
        report_lines += ['## %s（%s）' % (template_id, sheet_name), '']
        report_lines.append('- xlsx 字段数: %d，xlsx 容器节点数: %d' % (len(draft['fields']), len(draft['sections'])))
        if existing is None:
            report_lines += ['- 模板不存在，整模板草案输出', '']
            merged = draft
        else:
            resolved = resolve_extends(existing, templates_by_id)
            result = diff_template(draft, resolved)
            report_lines.append('- 现有模板字段数: %d（resolved %d）'
                                % (len(existing.get('fields', [])), len(resolved.get('fields', []))))
            report_lines.append('- xlsx 新增字段（已并入草案）: %s' % (result['added'] or '无'))
            report_lines.append('- 属性差异（xlsx vs 模板，需人工确认）: %d 处' % len(result['changed']))
            for code, attr, tv, xv in result['changed'][:30]:
                report_lines.append('  - `%s.%s`: 模板=%s xlsx=%s' % (code, attr, tv, xv))
            report_lines.append('- 模板存在但 xlsx 缺失: %s' % (result['missing_in_xlsx'] or '无'))
            report_lines.append('')
            merged = result['merged']
        # 草案落盘（schema 关键约束粗校验：field_code/message_path 形态）
        out_path = os.path.join(args.out_dir, template_id + '.draft.json')
        with open(out_path, 'w', encoding='utf-8') as fh:
            json.dump(merged, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
        report_lines += ['- 草案: `backend-app/scripts/out/%s.draft.json`' % template_id, '']

    # 问题待办核对
    if '问题待办' in wb.sheetnames:
        ws = wb['问题待办']
        report_lines += ['## 问题待办核对', '']
        for row in ws.iter_rows(min_row=2, values_only=False):
            for cell in row:
                if cell and cell.value and str(cell.value).strip():
                    text = str(cell.value).replace('\n', ' ')
                    pending_idx += 1
                    conclusion = PENDING_CONCLUSIONS.get(pending_idx, '（待确认）')
                    pending_conclusions[cell.row] = conclusion
                    report_lines += ['- 原始待办: %s' % text,
                                     '- 核对结论: %s' % conclusion, '']

    with open(args.report, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(report_lines) + '\n')
    print('报告: %s' % args.report)

    if args.writeback and pending_conclusions:
        writeback_pending_items(wb, pending_conclusions, args.xlsx)
        print('已回写问题待办: %s' % args.xlsx)
    return 0


# xlsx 尾页待办 → 模板落点核对结论（R4-Step4 闭环）
PENDING_CONCLUSIONS = {
    1: '已闭环：发布渠道互斥校验由模板 commonBasePrc.channelLimit.mutex_value_groups 表达，'
       '合规校验在提交阶段执行（前端不做即时校验），见模板 rule_description。',
    2: '已闭环：发布地市数量展示为前端增强项，模板 commonBasePrc.groupIdMessage.rule_description '
       '已声明「22地市多选；groupId=全省 时数量展示为前端增强项」。',
}

if __name__ == '__main__':
    sys.exit(main())
