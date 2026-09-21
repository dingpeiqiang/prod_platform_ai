# -*- coding: utf-8 -*-
import sys, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.load(io.open('wf_sub_11_发起需求审批.json', encoding='utf-8'))
wsf = d['workFlowSchemaJSON']
for n in wsf['nodes']:
    if n['id'][-4:] in ('1105','1106'):
        print('NODE', n['id'][-4:], 'type=', n['type'], 'title=', n.get('nodeMeta',{}).get('title',''), 'pos=', n.get('position'))
        ins = n.get('inputs',[])
        print('   inputs type:', type(ins).__name__, json.dumps(ins,ensure_ascii=False)[:500])
        print('   outputs:', json.dumps(n.get('outputs'),ensure_ascii=False)[:500])
print('EDGES:')
for e in wsf['edges']:
    print('   %s --%s--> %s' % (e['startId'][-4:], e.get('sourcePort'), e['endId'][-4:]))
