import json, io, glob

for f in sorted(glob.glob('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json')):
    d = json.load(io.open(f, encoding='utf-8'))
    W = d['workFlowSchemaJSON']
    nodes = {n['id']: n for n in W['nodes']}
    # direct predecessors
    preds = {}
    for e in W['edges']:
        preds.setdefault(e['endId'], set()).add(e['startId'])
    bad = []
    for n in W['nodes']:
        ins = n.get('inputs')
        if isinstance(ins, dict):
            ins = ins.get('inputParameters', [])
        for i in ins or []:
            if isinstance(i, dict) and i.get('type') == 'ref':
                bid = i.get('blockID')
                if bid and bid in nodes and bid not in preds.get(n['id'], set()):
                    bad.append((n['nodeMeta'].get('title'), i.get('name'), bid))
    if bad:
        print('###', f.split('\\')[-1])
        for x in bad:
            print('   NON-ADJACENT ref:', x)
