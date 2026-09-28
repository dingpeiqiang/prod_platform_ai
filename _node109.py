import json
import re
from typing import Any, Dict

SKIP_KEYS = ('templateId', 'prodId', 'prodPrcId', 'pricingId', 'opType')
SYSTEM_GEN_KEYS = ('orderNo',)
# 不可从自然语言需求提取的字段（系统生成/编码/规则类，x-required 亦不计入待补充）：
# 与要素校验节点 108 的 NON_EXTRACTABLE_PATTERNS 保持一致，禁止在此罗列系统类字段为待补充。
NON_EXTRACTABLE_PATTERNS = (
    r'(effDate|expDate)$',
    r'(effRuleId|cancelRuleId)$',
    r'(prodId|pricingId|prodPrcId|inProdPrcId|outProdPrcId|billPrcId|conditionCode|groupId)$',
    r'orderNo$',
    r'groupIdMessage$',
    r'subBillPrcIds$',
    r'^optionalInfo\.prcSmsCfg\.',
    r'acctItem$',
    r'powerCode$',
    r'familyCata$',
    r'splitRate(9|6)$',
    r'(favValidityVAlue|fixValidityVAlue)$',
)

def _is_non_extractable(path):
    return any(re.search(p, path) for p in NON_EXTRACTABLE_PATTERNS)

SOURCE_MAP = {
    '原始需求': '原始需求提取', 'AI补全': '相似产品补全',
    '本体推理': '本体推理', '默认值': '默认值', '同源派生': '同源派生',
}
SIMILAR_REF_PREFIX = '参考相似产品: '
INDENT_UNIT = '　'
OVERVIEW_PATHS = (
    ('baseInfo.prodPrcName', '资费名称'),
    ('optionalInfo.printContent.prcMonthFee', '套餐月费'),
    ('optionalInfo.printContent.containResource', '包含资源'),
)

def has_business_value(sub_schema, sub_data):
    if isinstance(sub_data, dict):
        for v in sub_data.values():
            if v not in ('', None, [], {}):
                return True
        return False
    return False

def fmt_value(v):
    if isinstance(v, bool):
        return '是' if v else '否'
    if isinstance(v, list):
        return '、'.join(str(x) for x in v)
    return str(v)

def similar_ref(similar_offer):
    if not isinstance(similar_offer, dict):
        return ''
    oid = similar_offer.get('similarOfferId', '')
    oname = similar_offer.get('similarOfferName', '')
    if isinstance(oid, (int, float)):
        oid = str(oid)
    if not oid or not oname:
        return ''
    return '%s%s %s' % (SIMILAR_REF_PREFIX, str(oid), str(oname))

def collect(schema, data, pending, meta=None, sim_ref=''):
    sections = []
    meta = meta or {}
    def hidden(obj_schema, sub, key, obj_data, path):
        # x-show-when 语义判定：'选择非全省时展示' 且 groupId=全省 -> 隐藏（不计待补充）
        cond = sub.get('x-show-when')
        if not cond or '非全省' not in str(cond):
            return False
        g = obj_data.get('groupId') if isinstance(obj_data, dict) else ''
        return '全省' in str(g or '')
    def walk(obj_schema, obj_data, depth, path, sec):
        for key, sub in obj_schema.get('properties', {}).items():
            label = sub.get('x-label', key)
            cur = (path + '.' + key) if path else key
            if sub.get('type') == 'object':
                sub_data = obj_data.get(key, {}) if isinstance(obj_data, dict) else {}
                if not has_business_value(sub, sub_data):
                    continue
                if depth <= 1:
                    sec2 = {'title': label, 'rows': []}
                    sections.append(sec2)
                    walk(sub, sub_data, depth + 1, cur, sec2)
                else:
                    sec['rows'].append(('s', depth, label, '', '', ''))
                    walk(sub, sub_data, depth + 1, cur, sec)
            else:
                if key in SKIP_KEYS or not isinstance(obj_data, dict):
                    continue
                if key not in obj_data or obj_data[key] in ('', None):
                    if sub.get('x-required') and key not in SYSTEM_GEN_KEYS and not _is_non_extractable(cur) and not hidden(obj_schema, sub, key, obj_data, path):
                        pending.append((INDENT_UNIT * depth) + label)
                    continue
                val = fmt_value(obj_data[key])
                note_parts = []
                cond = sub.get('x-show-when')
                if cond:
                    note_parts.append('满足条件时展示：' + str(cond).rstrip('才展示展示'))
                entry = meta.get(cur) or {}
                source_raw = entry.get('source', '') if isinstance(entry, dict) else ''
                source = SOURCE_MAP.get(source_raw, source_raw)
                if source_raw == 'AI补全' and sim_ref:
                    source = sim_ref
                sec['rows'].append(('|', depth, label, val, source, '；'.join(note_parts)))
    top = {'title': '', 'rows': []}
    sections.append(top)
    walk(schema, data, 0, '', top)
    return [s for s in sections if s['rows']]

def _render_one(schema, data, meta, similar_offer, title, seq=None):
    if schema.get('x-template') in data:
        data = data[schema['x-template']]
    pending = []
    sim_ref = similar_ref(similar_offer)
    sections = collect(schema, data, pending, meta, sim_ref)
    lines = []
    head = ('%d. ' % seq) if seq is not None else ''
    if title:
        lines.append('## ' + head + title)
        lines.append('')
    overview = []
    for dotted, cn in OVERVIEW_PATHS:
        node = data
        for k in dotted.split('.'):
            node = node.get(k) if isinstance(node, dict) else None
        if node not in ('', None):
            overview.append('**%s**：%s' % (cn, fmt_value(node)))
    if overview:
        lines.append('> **套餐概览**　|　' + '　|　'.join(overview))
        lines.append('')
    n = 0
    for sec in sections:
        if not sec['rows']:
            continue
        n += 1
        title_txt = '%d. %s' % (n, sec['title']) if sec['title'] else '%d. 配置明细' % n
        lines.append('**%s**' % title_txt)
        lines.append('')
        lines.append('| 字段名称 | 字段值 | 取值来源 | 备注 |')
        lines.append('| :--- | :--- | :--- | :--- |')
        for kind, depth, label, val, source, note in sec['rows']:
            if kind == 's':
                lines.append('| **%s%s** |  |  |  |' % (INDENT_UNIT * (depth - 1), label))
            else:
                pad = INDENT_UNIT * (depth - 1) if depth > 0 else ''
                lines.append('| %s%s | %s | %s | %s |' % (pad, label, val, source, note))
        lines.append('')
    if pending:
        lines.append('**【待补充字段】**（%d 项，补充后可进入配置）：' % len(pending))
        lines.append('、'.join(pending))
        lines.append('')
    return '\n'.join(lines), pending

def _offers_of(raw):
    # V3.12 多商品：元素可为 [{offer_seq,payload,meta}] 数组；旧单品对象包装为 1 品
    if isinstance(raw, list):
        return raw
    if isinstance(raw, dict) and isinstance(raw.get('offers'), list):
        return raw['offers']
    return None

def load(text):
    if not isinstance(text, str) or not text.strip():
        return {}
    try:
        return json.loads(text)
    except Exception:
        try:
            t = text.strip()
            if ':' in t and not t.startswith('{') and not t.startswith('['):
                t = t.split(':', 1)[1].strip()
            return json.loads(t)
        except Exception:
            return {}

async def main(args):
    p = args.params
    schema = load(p.get('schema_json') or '')
    title = str(p.get('title') or '')
    # V3.12 多商品：offers_json 为合并节点出参 offers[]（[{offer_seq,payload,meta}]）；逐品渲染
    elems = _offers_of(load(p.get('offers_json') or ''))
    blocks, pending_all, offers_out = [], [], []
    if isinstance(elems, list) and elems:
        for i, e in enumerate(elems, start=1):
            if not isinstance(e, dict):
                continue
            seq = int(e.get('offer_seq') or i)
            pay = load(e.get('payload') or '')
            mt = load(e.get('meta') or '')
            text, pend = _render_one(schema, pay, mt, load(p.get('similar_offer') or ''), title, seq)
            blocks.append(text)
            pending_all += pend
            offers_out.append({'offer_seq': seq, 'table_text': text,
                               'pending_fields': pend, 'pending_count': str(len(pend))})
    else:
        text, pend = _render_one(schema, {}, {},
                                 load(p.get('similar_offer') or ''), title)
        blocks.append(text)
        pending_all += pend
        offers_out.append({'offer_seq': 1, 'table_text': text,
                           'pending_fields': pend, 'pending_count': str(len(pend))})
    ret: Output = {
        "table_text": '\n\n'.join(blocks),
        "pending_count": str(len(pending_all)),
        "offers": json.dumps(offers_out, ensure_ascii=False),
    }
    return ret
