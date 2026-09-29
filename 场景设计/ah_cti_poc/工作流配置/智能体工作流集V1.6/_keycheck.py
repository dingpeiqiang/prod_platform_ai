import json, io, glob

def load(f):
    return json.load(io.open(f, encoding='utf-8'))

# Collect allowed key sets per node type across ALL siblings + target
from collections import defaultdict
keysets = defaultdict(lambda: defaultdict(set))  # type -> frozenset(keys) -> files
for f in sorted(glob.glob('wf_sub_*.json')):
    d = load(f)
    for n in d['workFlowSchemaJSON']['nodes']:
        t = n.get('type')
        keysets[t][frozenset(n.keys())].add(f)

for t in sorted(keysets):
    print('### type', t)
    for ks, fs in keysets[t].items():
        print('   keys=', sorted(ks))
        print('      files=', sorted(fs))
