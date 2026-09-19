# -*- coding: utf-8 -*-
import asyncio, json, sys, io, importlib.util, subprocess, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

import gen_workflows_v2 as G

T = r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\方案\templates"
TMP = r"C:\Users\a1521\AppData\Local\Temp"


class Args:
    def __init__(self, params):
        self.params = params


def run(code, params):
    ns = {'Output': dict}
    exec(compile(code, '<code>', 'exec'), ns)
    return asyncio.new_event_loop().run_until_complete(ns['main'](Args(params)))


def load(name):
    with open(os.path.join(T, name), 'r', encoding='utf-8') as f:
        return f.read()


def load_mod(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


orig_mn = load_mod(os.path.join(TMP, 'orig_merge_nested.py'), 'orig_mn')
orig_rt = load_mod(os.path.join(TMP, 'orig_render_table.py'), 'orig_rt')

schema_s = load('personMainPrc.schema.json')
elements_s = load('_test_elements_personMainPrc.json')
offer_s = load('_sample_personMainPrc.json')
schema = json.loads(schema_s)
elements = json.loads(elements_s)
offer = json.loads(offer_s)

# ---- workflow version ----
wf = run(G.CODE_MERGE_NESTED, {'schema_json': schema_s, 'elements_json': elements_s, 'offer_json': offer_s})
wf_payload = json.loads(wf['payload'])
wf_pending = wf['pending_required'].split(',') if wf['pending_required'] else []

# ---- original version ----
template_id = schema.get('x-template', '')
off = offer[template_id] if template_id in offer else offer
emap = orig_mn.flatten_elements(elements)
omap = orig_mn.flatten_offer(off)
meta, pending = {}, []
merged = orig_mn.merge(schema, emap, omap, meta, pending=pending)
orig_mn.reconcile_same_parameter(merged, meta, pending)

print('=== pending_required ===')
print('workflow :', wf_pending)
print('original :', pending)

print()
print('=== effDate / expDate ===')
for p in ('baseInfo.effDate', 'baseInfo.expDate'):
    node_w = wf_payload
    node_o = merged
    for k in p.split('.'):
        node_w = node_w.get(k)
        node_o = node_o.get(k)
    print('%s  workflow=%r  original=%r' % (p, node_w, node_o))

print()
print('=== payload diff (flatten) ===')
fw_map = orig_mn.flatten_offer(wf_payload)
og_map = orig_mn.flatten_offer(merged)
keys = sorted(set(fw_map) | set(og_map))
diff = 0
for k in keys:
    a, b = fw_map.get(k), og_map.get(k)
    if a != b:
        diff += 1
        print('  %-50s workflow=%-30r original=%r' % (k, a, b))
print('total diff leaves:', diff, '/', len(keys))

print()
print('=== meta source diff ===')
wf_meta = json.loads(wf['meta'])
for k in keys:
    sa = (wf_meta.get(k) or {}).get('source', '')
    sb = (meta.get(k) or {}).get('source', '')
    if sa != sb:
        print('  %-50s workflow=%-20r original=%r' % (k, sa, sb))

print()
print('=== rendered table (original) ===')
print(orig_rt.render(schema, merged, '产销品配置方案（模板轨）', meta, offer)[:2000])
