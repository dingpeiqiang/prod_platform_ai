# -*- coding: utf-8 -*-
import sys, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.load(io.open('wf_sub_11_发起需求审批.json', encoding='utf-8'))
print('top keys:', list(d.keys()))
wsf = d['workFlowSchemaJSON']
print('flowId', wsf.get('flowId'), 'version', wsf.get('version'))
for n in wsf['nodes']:
    print('NODE', n['id'][-4:], 'type=', n['type'], 'title=', n.get('nodeMeta',{}).get('title','')[:20], 'pos=', n.get('position'))
    print('    inputs:', [(i['name'], i['relName'], i['blockID'][-4:]) for i in n.get('inputs',[])])
    print('    outputs:', json.dumps(n.get('outputs'),ensure_ascii=False)[:150])
print('EDGES:')
for e in wsf['edges']:
    print('   start=%s port=%s end=%s' % (e['startId'][-4:], e.get('sourcePort'), e['endId'][-4:]))
