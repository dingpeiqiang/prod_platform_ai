import json, glob, re, sys
sys.stdout.reconfigure(encoding='utf-8')
for f in sorted(glob.glob('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json')):
    d = json.load(open(f, encoding='utf-8'))
    wf = d['workFlowSchemaJSON']
    for n in wf['nodes']:
        if n.get('type') == 6:
            code = n.get('code', '')
            declared = {o['name'] for o in n.get('outputs', [])}
            parts = re.findall(r"Output\s*=\s*\{(.*?)\}", code, re.S)
            ret = set()
            for p in parts:
                for k in re.findall(r"'([A-Za-z_][A-Za-z0-9_]*)'\s*:", p):
                    ret.add(k)
            extra = ret - declared
            if extra:
                print(f.split('/')[-1], n['nodeMeta']['title'], 'EXTRA', sorted(extra))
