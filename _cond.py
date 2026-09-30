import json, sys, glob
sys.stdout.reconfigure(encoding='utf-8')
base = r'场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6'
for p in glob.glob(base + r'\wf_sub_*.json'):
    d = json.load(open(p, encoding='utf-8'))
    s = d['workFlowSchemaJSON']
    if isinstance(s, str):
        s = json.loads(s)
    for n in s['nodes']:
        if n.get('type') == 2:
            cond = (n.get('inputs') or {}).get('condition')
            print("=" * 70)
            print(p.split('\\')[-1], "node", n['id'][-3:])
            print(json.dumps(cond, ensure_ascii=False, indent=1))
