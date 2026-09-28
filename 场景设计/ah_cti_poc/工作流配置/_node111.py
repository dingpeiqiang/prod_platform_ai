import json, random
from typing import Any, Dict

def _str(v):
    return str(v).strip() if v not in (None, '') else ''

def _gen():
    return '900%06d' % random.randint(0, 999999)

def _plan_of(item, req_id):
    # 逐品 plan_json：优先取元素 payload（合并方案 JSON），注入 req_id 供后端提取方案key
    raw = item.get('plan_json') or item.get('payload') or item.get('product_config') or ''
    plan = None
    if isinstance(raw, dict):
        plan = dict(raw)
    elif isinstance(raw, str) and raw.strip():
        try:
            obj = json.loads(raw)
            if isinstance(obj, dict):
                plan = obj
        except Exception:
            plan = None
    if plan is None:
        return ''
    if req_id and not plan.get('req_id'):
        plan['req_id'] = req_id
    return json.dumps(plan, ensure_ascii=False)

async def main(args):
    p = args.params
    req_id = _str(p.get('req_id') or '')
    raw = p.get('offers') or ''
    items = []
    try:
        obj = json.loads(raw) if isinstance(raw, str) else raw
        if isinstance(obj, list):
            items = [dict(o) for o in obj if isinstance(o, dict)]
        elif isinstance(obj, dict):
            if isinstance(obj.get('offers'), list):
                items = [dict(o) for o in obj['offers'] if isinstance(o, dict)]
            elif obj:
                items = [dict(obj)]
    except Exception:
        items = []
    existing = _str(p.get('existing_offer_id') or '')
    out = []
    for i, item in enumerate(items, start=1):
        oid = _str(item.get('offer_id') or item.get('offerId') or '')
        if not oid:
            oid = _str(item.get('similarOfferId') or '')
        if not oid and len(items) == 1:
            oid = existing
        if not (oid.isdigit() and len(oid) == 9):
            oid = _gen()
        seq = int(item.get('offer_seq') or i)
        plan_json = _plan_of(item, req_id)
        out.append({'offer_seq': seq, 'offer_id': oid, 'plan_json': plan_json})
    ret: Output = {
        'offers': json.dumps(out, ensure_ascii=False),
        'offer_id': (out[0]['offer_id'] if out else ''),
    }
    return ret