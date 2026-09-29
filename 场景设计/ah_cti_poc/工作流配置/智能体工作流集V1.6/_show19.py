import json, io, glob, sys

prefix = sys.argv[1] if len(sys.argv) > 1 else 'wf_sub_00'
files = glob.glob(prefix + '*.json')
for f in files:
    d = json.load(io.open(f, encoding='utf-8'))
    for n in d['workFlowSchemaJSON']['nodes']:
        if n.get('type') in (1, 9):
            print('==', f, 'type', n.get('type'), n.get('nodeMeta', {}).get('title'))
            print(json.dumps(n, ensure_ascii=False, indent=1))
