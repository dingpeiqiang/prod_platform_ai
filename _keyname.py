import json, io, glob

def allkeys(o, acc):
    if isinstance(o, dict):
        for k, v in o.items():
            acc.add(k)
            allkeys(v, acc)
    elif isinstance(o, list):
        for v in o:
            allkeys(v, acc)

sib = set()
for f in sorted(glob.glob('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json')):
    if '08_' in f:
        continue
    d = json.load(io.open(f, encoding='utf-8'))
    allkeys(d, sib)

t = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_08_审批进度查询.json', encoding='utf-8'))
tk = set()
allkeys(t, tk)

# also compare against prod version keys
p = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6-prod/wf_sub_08_审批进度查询.json', encoding='utf-8'))
pk = set()
allkeys(p, pk)

print('keys ONLY in sub_08 (dev):', sorted(tk - sib))
print('keys in dev but NOT in prod:', sorted(tk - pk))
print('keys in prod but NOT in dev:', sorted(pk - tk))
print('all keys in sub_08:', sorted(tk))
