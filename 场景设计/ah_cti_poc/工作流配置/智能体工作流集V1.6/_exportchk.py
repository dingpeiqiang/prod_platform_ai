import json, io, glob

for f in glob.glob('*export*.json'):
    print('#####', f)
    d = json.load(io.open(f, encoding='utf-8'))
    print('topkeys:', sorted(d.keys()))
    s = d.get('workFlowSchemaJSON')
    print('schemaJSON type:', type(s).__name__)
    if isinstance(s, dict):
        print('schemakeys:', sorted(s.keys()))
        for n in s.get('nodes', []):
            print('  type', n.get('type'), '|', n.get('nodeMeta', {}).get('title'), '| keys', sorted(n.keys()))
    elif isinstance(s, str):
        print('schemaJSON is STRING len', len(s))
