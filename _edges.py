import json, sys
sys.stdout.reconfigure(encoding='utf-8')
base = r'场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6'
for fn, cid in [('wf_sub_12_规格稽核查询.json', '212'), ('wf_sub_01_需求分析.json', '115'), ('wf_sub_05_资费校准.json', '411'), ('wf_sub_10_存量合规扫描.json', '006'), ('wf_sub_07_监控运维.json', '704')]:
    d = json.load(open(base + '\\' + fn, encoding='utf-8'))
    s = d['workFlowSchemaJSON']
    if isinstance(s, str):
        s = json.loads(s)
    print("=" * 60)
    print(fn, "cond node", cid)
    # node titles for reference
    titles = {n['id'][-3:]: ((n.get('nodeMeta') or {}).get('title') or n.get('type')) for n in s['nodes']}
    for e in s['edges']:
        if e['startId'][-3:] == cid:
            print(f"  OUT port={e['sourcePort']} -> node {e['endId'][-3:]} ({titles.get(e['endId'][-3:])})")
