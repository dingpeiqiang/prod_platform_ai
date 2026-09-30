import json, sys
sys.stdout.reconfigure(encoding='utf-8')
p = r'场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6\wf_sub_05_资费校准.json'
d = json.load(open(p, encoding='utf-8'))
s = d['workFlowSchemaJSON']
if isinstance(s, str):
    s = json.loads(s)
nodes = s['nodes']
titles = {n['id'][-3:]: ((n.get('nodeMeta') or {}).get('title') or ('t' + str(n.get('type')))) for n in nodes}
# predecessor map
pred = {}
for e in s['edges']:
    pred.setdefault(e['endId'][-3:], []).append(e['startId'][-3:])

def refs(node):
    out = []
    inp = node.get('inputs')
    def walk(o):
        if isinstance(o, dict):
            if 'nameValue' in o and isinstance(o['nameValue'], list) and o['nameValue']:
                bid = str(o['nameValue'][0])
                nm = o.get('name') or o.get('relName')
                if bid:
                    out.append((bid[-3:], nm))
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(inp)
    return out

for n in nodes:
    nid = n['id'][-3:]
    rs = refs(n)
    if not rs:
        continue
    print(f"NODE {nid} ({titles[nid]})  preds={pred.get(nid)}")
    for bid, nm in rs:
        tag = "OK " if bid in (pred.get(nid) or []) else "!! "
        print(f"   {tag}ref-> {bid} ({titles.get(bid)}) as '{nm}'")
