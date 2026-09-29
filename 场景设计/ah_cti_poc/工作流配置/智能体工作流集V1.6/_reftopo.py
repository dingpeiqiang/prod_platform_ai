import json, io, glob

def load(f):
    return json.load(io.open(f, encoding='utf-8'))

for f in sorted(glob.glob('wf_sub_*.json')):
    d = load(f)
    nodes = {n['id']: n for n in d['workFlowSchemaJSON']['nodes']}
    # build adjacency
    adj = {}
    for e in d['workFlowSchemaJSON']['edges']:
        adj.setdefault(e['startId'], set()).add(e['endId'])
    # reachability
    def reach(src):
        seen, stack = set(), [src]
        while stack:
            x = stack.pop()
            if x in seen: continue
            seen.add(x)
            stack.extend(adj.get(x, []))
        return seen
    problems = []
    for n in nodes.values():
        ins = n.get('inputs')
        if isinstance(ins, dict):
            ins = ins.get('inputParameters', [])
        for i in ins or []:
            if isinstance(i, dict) and i.get('type') == 'ref':
                bid = i.get('blockID')
                if bid and bid in nodes:
                    # is bid reachable to n (or is n downstream of bid)?
                    if n['id'] not in reach(bid) and bid not in reach(n['id']):
                        problems.append((n['nodeMeta'].get('title'), i.get('name'), bid))
    if problems:
        print('### ', f)
        for p in problems:
            print('   ref not connected by path:', p)
