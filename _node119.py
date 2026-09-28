import json
from typing import Any, Dict

# 产品类型关键词 → 模板标识（顺序即优先级；命中即锁定，全部不命中默认 personMainPrc）
TYPE_RULES = (
    ('家庭基础套餐', 'familyBasePrc'), ('融合套餐', 'familyBasePrc'),
    ('融合', 'familyBasePrc'), ('家庭', 'familyBasePrc'),
    ('宽带加速包', 'broadBandOptSpeedPrc'), ('宽带附加资费', 'broadBandOptSpeedPrc'),
    ('宽带主套餐', 'broadBandMainPrc'), ('宽带主资费', 'broadBandMainPrc'),
    ('家庭附加资费', 'familyAddPrc'), ('家庭附加业务', 'familyAddPrc'),
    ('个人附加资费', 'personAddPrc'), ('个人附加', 'personAddPrc'),
    ('个人主套餐', 'personMainPrc'), ('个人主资费', 'personMainPrc'),
)
VALID = ('personMainPrc', 'broadBandMainPrc', 'personAddPrc',
         'broadBandOptSpeedPrc', 'familyBasePrc', 'familyAddPrc')

def _load(raw):
    if isinstance(raw, (list, dict)):
        return raw
    if not isinstance(raw, str) or not raw.strip():
        return []
    t = raw.strip()
    if ':' in t and not t.lstrip().startswith('{') and not t.lstrip().startswith('['):
        t = t.split(':', 1)[1].strip()
    s, e = t.find('['), t.rfind(']')
    if s != -1 and e > s:
        t = t[s:e + 1]
    try:
        return json.loads(t)
    except Exception:
        return []

def _route(offer):
    tid = str(offer.get('template_id') or '').strip()
    if tid in VALID:
        return tid
    pt = str(offer.get('product_type') or '')
    names = pt + ' ' + str(offer.get('name') or '') + ' ' + str(offer.get('series') or '')
    for kw, mapped in TYPE_RULES:
        if kw in names:
            return mapped
    return 'personMainPrc'

async def main(args):
    p = args.params
    data = _load(p.get('offers_json') or '')
    if isinstance(data, dict):
        data = data.get('offers') or data.get('offers_json') or [data]
    if not isinstance(data, list):
        data = []
    out, first, fusion = [], '', '0'
    for i, offer in enumerate(data, start=1):
        if not isinstance(offer, dict):
            continue
        tid = _route(offer)
        out.append({'offer_seq': offer.get('offer_seq') or i,
                    'template_id': tid,
                    'need_summary': str(offer.get('need_summary') or '')})
        if not first:
            first = tid
        if tid == 'familyBasePrc':
            fusion = '1'
    if not out:
        out = [{'offer_seq': 1, 'template_id': 'personMainPrc', 'need_summary': ''}]
        first = 'personMainPrc'
    ret: Output = {
        'offers_tpl': json.dumps(out, ensure_ascii=False),
        'template_id': first,
        'template_ids': ','.join(str(o['template_id']) for o in out),
        'fusion_flag': fusion,
    }
    return ret
