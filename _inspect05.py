import json, sys
sys.stdout.reconfigure(encoding='utf-8')
p = r'场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6\wf_sub_05_资费校准.json'
d = json.load(open(p, encoding='utf-8'))
s = d['workFlowSchemaJSON']
if isinstance(s, str):
    s = json.loads(s)
nodes = {n['id'][-3:]: n for n in s['nodes']}
for nid in ['410', '411', '403', '412', '413', '404', '408', '414']:
    n = nodes[nid]
    print("=" * 60)
    print("NODE", nid, "type=", n.get('type'))
    for k, v in n.items():
        if k in ('id',):
            continue
        vs = json.dumps(v, ensure_ascii=False)
        if len(vs) > 1500:
            vs = vs[:1500] + ' ...[TRUNC]'
        print(f"  {k}: {vs}")
