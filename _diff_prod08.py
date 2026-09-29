import json, io, glob

a = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_08_审批进度查询.json', encoding='utf-8'))
b = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6-prod/wf_sub_08_审批进度查询.json', encoding='utf-8'))


def norm(o):
    if isinstance(o, dict):
        return {k: norm(v) for k, v in o.items()}
    if isinstance(o, list):
        return [norm(v) for v in o]
    return o


print('EQUAL:', norm(a) == norm(b))
print('top equal:', {k: (a.get(k) == b.get(k)) for k in set(a) | set(b)})
aw, bw = a['workFlowSchemaJSON'], b['workFlowSchemaJSON']
print('flowId', aw.get('flowId'), bw.get('flowId'), 'ver', aw.get('version'), bw.get('version'))
print('T nodes', len(aw['nodes']), 'P nodes', len(bw['nodes']))
print('T types', [n.get('type') for n in aw['nodes']])
print('P types', [n.get('type') for n in bw['nodes']])
an = {n['id']: n for n in aw['nodes']}
bn = {n['id']: n for n in bw['nodes']}
print('id sets equal:', set(an) == set(bn))
for nid in sorted(set(an) | set(bn)):
    x, y = an.get(nid), bn.get(nid)
    if norm(x) != norm(y):
        print('NODE DIFF', nid)
        print('  T:', json.dumps({k: x[k] for k in x if k not in ('code',)}, ensure_ascii=False)[:600])
        print('  P:', json.dumps({k: y[k] for k in y if k not in ('code',)}, ensure_ascii=False)[:600])
print('edges equal:', norm(aw['edges']) == norm(bw['edges']))
