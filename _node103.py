import json
from typing import Any, Dict

def _offers_of(obj):
    # V3.12 多商品：优先取 offers[] 数组；旧单品平面对象包装为 1 品（offer_seq=1）
    if not isinstance(obj, dict):
        return []
    offers = obj.get('offers')
    if isinstance(offers, list):
        out = []
        for i, o in enumerate(offers, start=1):
            if not isinstance(o, dict):
                continue
            e = dict(o)
            e.setdefault('offer_seq', i)
            out.append(e)
        return out
    return [obj]

async def main(args):
    raw = args.params['query_list']
    if not isinstance(raw, str):
        raw = json.dumps(raw, ensure_ascii=False)
    record_json = ''
    offer_id = ''
    req_id = ''
    try:
        arr = json.loads(raw)
        if isinstance(arr, list) and arr:
            first = arr[0]
            if isinstance(first, dict):
                record_json = str(first.get('result_json') or '')
                req_id = str(first.get('req_id') or '')
        elif isinstance(arr, dict):
            record_json = str(arr.get('result_json') or '')
            req_id = str(arr.get('req_id') or '')
    except Exception:
        record_json = ''
    if not record_json:
        record_json = raw
    offers = []
    try:
        inner = json.loads(record_json)
        if isinstance(inner, dict):
            if not req_id:
                req_id = str(inner.get('req_id') or '')
            offers = _offers_of(inner)
            if offers:
                offer_id = str(offers[0].get('offer_id') or offers[0].get('offerId') or '')
    except Exception:
        offers = []
    ret: Output = {
        "record_json": record_json,
        "req_id": req_id,
        "offer_id": offer_id,
        "offers": json.dumps(offers, ensure_ascii=False)
    }
    return ret
