import json, io, sys

fn = sys.argv[1]
d = json.load(io.open(fn, encoding='utf-8'))
s = d['workFlowSchemaJSON']
if isinstance(s, str):
    s = json.loads(s)
def names(lst):
    out = []
    for i in lst or []:
        out.append(i.get('name') if isinstance(i, dict) else i)
    return out

for n in s['nodes']:
    print(n.get('type'), '|', n.get('nodeMeta', {}).get('title'),
          '| IN:', names(n.get('inputs')), '| OUT:', names(n.get('outputs')))
