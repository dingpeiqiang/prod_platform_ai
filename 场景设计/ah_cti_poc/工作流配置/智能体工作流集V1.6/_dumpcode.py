import json, io, sys

fn = sys.argv[1]
ids = sys.argv[2:]
d = json.load(io.open(fn, encoding='utf-8'))
s = d['workFlowSchemaJSON']
if isinstance(s, str):
    s = json.loads(s)
for n in s['nodes']:
    if n.get('type') != 6:
        continue
    nid = n['id'].split('-')[-1].lstrip('0')
    if ids and nid not in ids:
        continue
    print('=' * 20, nid, n.get('nodeMeta', {}).get('title'), '=' * 20)
    print(n.get('code'))
    print()
