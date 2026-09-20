import json, glob, os, collections

d = r'场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6'

def analyze(f):
    j = json.load(open(f, encoding='utf-8'))
    sch = j.get('workFlowSchemaJSON') or {}
    nodes = sch.get('nodes') or []
    edges = sch.get('edges') or []
    nm = {}
    typ = {}
    for n in nodes:
        meta = n.get('nodeMeta') or {}
        code = meta.get('code') or ''
        nm[n.get('id')] = (meta.get('title') or '?') + ('/' + code if code else '')
        typ[n.get('id')] = n.get('type')
    out = collections.defaultdict(list)
    inn = collections.defaultdict(list)
    for e in edges:
        out[e['startId']].append(e['endId'])
        inn[e['endId']].append(e['startId'])
    print('=' * 70)
    print('FILE:', os.path.basename(f))
    # node types: 0=start 9=end, others
    for n in nodes:
        nid = n['id']
        t = n.get('type')
        tname = {0: 'START', 9: 'END'}.get(t, 'type%d' % t)
        print('  %s %-28s TYPE=%-6s IN=%s OUT=%s' % (
            nid[-3:], nm[nid][:26], tname,
            ','.join(x[-3:] for x in inn[nid]) or '-',
            ','.join(x[-3:] for x in out[nid]) or '-'))

for f in sorted(glob.glob(os.path.join(d, '*.json'))):
    analyze(f)
