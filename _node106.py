import json, os, urllib.request
from typing import Any, Dict

TEMPLATE_URL = 'http://10.86.13.201:31281/api/v1/appstore/template/schema'
TEMPLATE_ALIAS = {
    'personMainPrc': ('个人主资费', '个人主套餐'),
    'broadBandMainPrc': ('宽带主资费', '宽带主套餐'),
    'personAddPrc': ('个人附加资费', '个人附加资费'),
    'broadBandOptSpeedPrc': ('宽带加速包', '宽带附加资费'),
    'familyBasePrc': ('家庭基础套餐', '家庭基础套餐'),
    'familyAddPrc': ('家庭附加业务', '家庭附加资费'),
}

def _pick(req):
    r = str(req or '').strip()
    # ① 精确匹配（模板标识/中文名/产品类型）
    for tid, (cn, pt) in TEMPLATE_ALIAS.items():
        if r == tid or r == cn or r == pt:
            return tid, cn, pt
    # ② 兜底：子串包含（容忍上游出参夹带标签/说明文字）
    for tid, (cn, pt) in TEMPLATE_ALIAS.items():
        if tid in r or (cn and cn in r) or (pt and pt in r):
            return tid, cn, pt
    return 'personMainPrc', '个人主资费', '个人主套餐'

def _fetch(tid):
    body = json.dumps({'templateId': tid}).encode('utf-8')
    req = urllib.request.Request(TEMPLATE_URL, data=body, method='POST',
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode('utf-8'))

async def main(args):
    p = args.params
    tid, cn, pt = _pick(p.get('template_id') or '')
    content = '{}'
    missing = '0'
    source = 'empty'
    # ① 后端下发（主路径）
    try:
        info = _fetch(tid)
        schema_text = str((info or {}).get('schema_json') or '')
        if schema_text.strip() and schema_text.strip() != '{}':
            content = schema_text
            cn = str(info.get('template_name_cn') or cn)
            pt = str(info.get('template_product_type') or pt)
            source = 'backend'
    except Exception:
        info = None
    # ② 本地文件兜底（仅当显式配置 template_base 时）
    if source != 'backend':
        base = str(p.get('template_base') or '').strip()
        if base:
            try:
                with open(os.path.join(base, '%s.schema.json' % tid), 'r', encoding='utf-8') as f:
                    content = f.read()
                source = 'file'
            except Exception:
                missing = '1'
    if source == 'empty':
        missing = '1'
    ret: Output = {
        "template_id": tid,
        "template_name_cn": cn,
        "template_product_type": pt,
        "schema_json": content,
        "schema_file": '%s.schema.json' % tid,
        "missing": missing,
        "source": source,
    }
    return ret
