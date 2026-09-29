import json, io, glob, sys, re

# Compare every field of the target workflow against a sibling, and validate placeholder/ref integrity.

def load(f):
    return json.load(io.open(f, encoding='utf-8'))

target = glob.glob('wf_sub_08*.json')[0]
d = load(target)
nodes = d['workFlowSchemaJSON']['nodes']
ids = {n['id'] for n in nodes}
print('target file:', target)
print('node count:', len(nodes))

# 1) LLM placeholder vs inputParameters
for n in nodes:
    if n.get('type') == 1:
        title = n['nodeMeta']['title']
        params = [p['name'] for p in n['inputs'].get('inputParameters', [])]
        prompt = n['inputs']['llmParam'][0]['content']
        ph = set(re.findall(r'\{(\w+)\}', prompt))
        print('LLM', title, 'params=', params, 'placeholders=', sorted(ph))
        missing = ph - set(params)
        if missing:
            print('  !! placeholder without inputParameter:', sorted(missing))

# 2) ref integrity: every ref input blockID must exist, relName must be an output of that node
byid = {n['id']: n for n in nodes}
for n in nodes:
    ins = n.get('inputs')
    if isinstance(ins, dict):
        ins = ins.get('inputParameters', [])
    for i in ins or []:
        if isinstance(i, dict) and i.get('type') == 'ref':
            bid = i.get('blockID')
            rel = i.get('relName')
            if bid not in byid:
                print('!! ref to unknown block', n['nodeMeta'].get('title'), i.get('name'), bid)
                continue
            outs = byid[bid].get('outputs')
            if isinstance(outs, dict):
                outnames = {outs.get('name')}
            else:
                outnames = {o.get('name') for o in (outs or [])}
            if rel not in outnames:
                print('!! ref relName not in source outputs', n['nodeMeta'].get('title'), '->', i.get('name'), 'rel=', rel, 'srcouts=', sorted(x for x in outnames if x))
