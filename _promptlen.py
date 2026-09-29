import json, io, glob

def load(p):
    return json.load(io.open(p, encoding='utf-8'))

print('=== LLM prompt lengths per workflow ===')
for f in sorted(glob.glob('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json')):
    d = load(f)
    for n in d['workFlowSchemaJSON']['nodes']:
        if n.get('type') == 1:
            pr = n['inputs']['llmParam'][0].get('content', '')
            print('%-40s %-20s len=%d' % (f.split('\\')[-1], n['nodeMeta'].get('title'), len(pr)))

print()
print('=== dev vs prod node 805 code ===')
t = load('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_08_审批进度查询.json')
p = load('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6-prod/wf_sub_08_审批进度查询.json')
tc = [n for n in t['workFlowSchemaJSON']['nodes'] if n['id'].endswith('805')][0]['code']
pc = [n for n in p['workFlowSchemaJSON']['nodes'] if n['id'].endswith('805')][0]['code']
print('DEV len', len(tc))
print(tc)
print('---PROD len', len(pc))
print(pc)
