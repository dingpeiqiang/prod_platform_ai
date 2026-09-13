# -*- coding: utf-8 -*-
"""主流程同步（wf_sub_02 统一内部存储后）：
1. 环节1子流(005) inputs 改为仅 req_id（ref 开始节点 execution_id）
2. 删除 0051b(环节1结果合成) 与 0051(环节1结果存储)，边直连 005→006
3. 005 出参保留（子流结束节点回传 product_id/offer_id/save_result/status），下游 006/061/013/014/016/018 引用不变
"""
import json, io

P = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\工作流配置\V1.6\wf_cpcp_main_产销品加载主流程.json'
with io.open(P, encoding='utf-8') as f:
    doc = json.load(f)

g = doc['workFlowSchemaJSON']
nodes = {n['id'].split('-')[-1]: n for n in g['nodes']}
BASE = 'a1b2c3d4-0000-4000-8000-'
START = BASE + '000000000001'

def full(i):
    return BASE + i

# 1. 005 inputs 改为仅 req_id
n5 = nodes['000000000005']
n5['inputs'] = [
    {
        "blockID": START,
        "relName": "execution_id",
        "name": "req_id",
        "description": "主流程执行批次号execution_id（子工作流内部凭req_id自查requirement环节结果获取执行方案，并存储config环节结果）",
        "type": "ref",
        "required": True,
        "content": "",
        "nameValue": [START, START + ',execution_id'],
        "currValue": START + ',execution_id'
    }
]
n5['nodeMeta']['description'] = '调用子工作流（单入参req_id）：子流程内部自查requirement环节结果落地配置，并存储config环节结果（含offer_id/product_id/save_result/status）'

# 2. 删除 0051b/0051，边 005→0051b→0051→006 变 005→006
REMOVE = ('000000000051b', '000000000051')
new_edges = []
for e in g['edges']:
    s = e['startId'].split('-')[-1]
    t = e['endId'].split('-')[-1]
    if t in REMOVE:
        # 005→0051b 变 005→006；0051b→0051、0051→006 丢弃
        if s == '000000000005':
            new_edges.append({"sourcePort": None, "endId": full('000000000006'), "startId": e['startId']})
        continue
    if s in REMOVE:
        continue
    new_edges.append(e)
g['edges'] = new_edges
g['nodes'] = [n for n in g['nodes'] if n['id'].split('-')[-1] not in REMOVE]

# 3. 校验
ids = [n['id'].split('-')[-1] for n in g['nodes']]
assert not any(r in ids for r in REMOVE), 'REMOVE nodes still present'
idset = set(ids)
for e in g['edges']:
    assert e['startId'].split('-')[-1] in idset, 'edge start missing: ' + e['startId']
    assert e['endId'].split('-')[-1] in idset, 'edge end missing: ' + e['endId']

with io.open(P, 'w', encoding='utf-8') as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)
print('OK nodes=%d edges=%d' % (len(g['nodes']), len(g['edges'])))
