import json, io, glob, sys

prefix = sys.argv[1]
t = sys.argv[2] if len(sys.argv) > 2 else None
for f in glob.glob(prefix + '*.json'):
    d = json.load(io.open(f, encoding='utf-8'))
    for n in d['workFlowSchemaJSON']['nodes']:
        if t and str(n.get('type')) != t:
            continue
        print('==', f, 'type', n.get('type'), n.get('nodeMeta', {}).get('title'))
        print(json.dumps(n, ensure_ascii=False, indent=1))
