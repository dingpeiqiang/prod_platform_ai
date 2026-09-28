import json
from typing import Any, Dict
def _load(v):
    if isinstance(v, (dict, list)):
        return v
    if not isinstance(v, str) or not v.strip():
        return {}
    t = v.strip()
    try:
        return json.loads(t)
    except Exception:
        try:
            if ':' in t and not t.startswith('{'):
                return json.loads(t.split(':', 1)[1].strip())
        except Exception:
            pass
        return {}
async def main(args):
    p = args.params
    req_id = str(p.get('req_id') or '')
    template = str(p.get('template') or '')
    raw_results = p.get('results')
    if not isinstance(raw_results, list):
        raw_results = [raw_results] if raw_results else []
    offers = []
    for i, item in enumerate(raw_results, start=1):
        obj = _load(item)
        if isinstance(obj, list):
            for sub in obj:
                if isinstance(sub, dict):
                    sub.setdefault('offer_seq', len(offers) + 1)
                    offers.append(sub)
            continue
        if isinstance(obj, dict):
            if isinstance(obj.get('offers'), list):
                for sub in obj['offers']:
                    if isinstance(sub, dict):
                        sub.setdefault('offer_seq', len(offers) + 1)
                        offers.append(sub)
            elif obj:
                obj.setdefault('offer_seq', i)
                offers.append(obj)
    for idx, o in enumerate(offers, start=1):
        o.setdefault('offer_seq', idx)
        o.setdefault('template', template)
    ret: Output = {'result_json': json.dumps({'req_id': req_id, 'template': template, 'offers': offers}, ensure_ascii=False)}
    return ret
