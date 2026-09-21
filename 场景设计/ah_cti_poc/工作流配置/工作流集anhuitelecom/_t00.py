# -*- coding: utf-8 -*-
import importlib, io, json, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
_t = importlib.import_module('_transform_v3')
_t.transform_file('_test00.json')
d = json.load(io.open('_test00.json', encoding='utf-8'))
wsf = d['workFlowSchemaJSON']
for n in wsf['nodes']:
    if n['type'] in (6,9):
        print('==== type', n['type'], 'title=', n.get('nodeMeta',{}).get('title'))
        if n['type']==9:
            print('  CONTENT repr:', repr(n['outputs']['content']))
            print('  inputs:', [(i['name'], i['blockID'][-3:]) for i in n['inputs']])
        else:
            print('  id-suffix', n['id'][-4:], 'outputs:', json.dumps(n['outputs'],ensure_ascii=False))
            print('  inputs:', [(i['name'], i['relName'], i['blockID'][-3:]) for i in n['inputs']])
            print('  CODE:')
            print(n['code'])
print('EDGES:')
for e in wsf['edges']:
    print('   %s --%s--> %s' % (e['startId'][-3:], e.get('sourcePort'), e['endId'][-3:]))
