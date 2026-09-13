# -*- coding: utf-8 -*-
# 校验 Skills 版生成产物：JSON 可解析、节点边引用完整、与插件契约一致
import json, io, os, sys

sys.stdout.reconfigure(encoding='utf-8')
BASE = os.path.dirname(os.path.abspath(__file__))

# 1. 全部 JSON 可解析
files = [f for f in os.listdir(BASE) if f.endswith('.json')]
ok = True
for f in sorted(files):
    try:
        d = json.load(io.open(os.path.join(BASE, f), encoding='utf-8'))
        print('[OK] parse:', f)
    except Exception as e:
        ok = False
        print('[FAIL] parse:', f, e)

# 2. 工作流类 JSON：节点/边完整性 + URL 与插件契约一致
URLS_EXPECTED = {
    "query_similar_offer": "/api/v1/appstore/similar/offer/query",
    "realtime_spec_audit": "/api/v1/appstore/audit/realtime",
    "offer_test": "/api/v1/appstore/test/offer/start",
    "get_test_scenes": "/api/v1/appstore/test/offer/scenes",
    "get_test_progress": "/api/v1/appstore/test/offer/progress",
    "get_test_result": "/api/v1/appstore/test/offer/result",
    "save_product_config": "/api/v1/appstore/product/config/save",
    "check_billing_rule": "/api/v1/appstore/billing/rules/verify",
    "submit_release_approval": "/api/v1/appstore/approval/submit",
    "query_product_monitor": "/api/v1/appstore/product/monitor",
    "send_alert": "/api/v1/appstore/alert/send",
    "query_approval_status": "/api/v1/appstore/approval/status",
    "save_node_result": "/api/v1/appstore/result/save",
    "query_node_result": "/api/v1/appstore/result/query",
}
for f in sorted(files):
    d = json.load(io.open(os.path.join(BASE, f), encoding='utf-8'))
    if 'workFlowSchemaJSON' not in d:
        continue
    s = d['workFlowSchemaJSON']
    ids = {n['id'] for n in s['nodes']}
    # 边引用完整
    for e in s['edges']:
        if e['startId'] not in ids or e['endId'] not in ids:
            ok = False
            print('[FAIL] edge ref:', f, e)
    # 节点 url 校验
    for n in s['nodes']:
        if n['type'] == 3:
            code = n['nodeMeta'].get('code')
            expect = 'http://10.86.13.201:31281' + URLS_EXPECTED.get(code)
            if n.get('url') != expect:
                ok = False
                print('[FAIL] url mismatch:', f, code, n.get('url'))
    # 引用 ref blockID 存在（开始节点/插件/LLM 出参引用）
    def check_refs(inputs):
        if isinstance(inputs, dict):
            for ip in inputs.get('inputParameters', []) or []:
                b = ip.get('blockID')
                if b and b not in ids:
                    return ip
            for ip in inputs.get('llmParam', []) or []:
                pass
            lp = inputs.get('llmParam')
        elif isinstance(inputs, list):
            for ip in inputs:
                b = ip.get('blockID')
                if b and b not in ids:
                    return ip
        return None
    for n in s['nodes']:
        bad = check_refs(n.get('inputs'))
        if bad:
            ok = False
            print('[FAIL] ref block:', f, n['nodeMeta'].get('title'), bad.get('name'), bad.get('blockID'))
    print('[OK] graph:', f, len(s['nodes']), 'nodes,', len(s['edges']), 'edges')

# 3. skillMeta 存在
for f in sorted(files):
    d = json.load(io.open(os.path.join(BASE, f), encoding='utf-8'))
    if 'skillMeta' in d:
        sm = d['skillMeta']
        print('[OK] skillMeta:', f, sm['skillId'], 'passive' if sm['triggerType'].get('passive') else 'active')

print('ALL PASS' if ok else 'HAS FAILURES')
