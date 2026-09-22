# -*- coding: utf-8 -*-
import sys, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = json.load(io.open('wf_sub_00_需求提报.json', encoding='utf-8'))
wsf = d['workFlowSchemaJSON']
for n in wsf['nodes']:
    if n['type']==9 and n['id'][-4:]=='0007':
        print('END 0007 outputs:', json.dumps(n['outputs'],ensure_ascii=False,indent=1))
        print('END 0007 inputs:')
        for i in n['inputs']:
            print('  ', json.dumps(i,ensure_ascii=False))
    # check a type-6 code node structure for reference (node 0003)
    if n['type']==6 and n['id'][-4:]=='0003':
        print('CODE 0003 nodeMeta:', json.dumps(n['nodeMeta'],ensure_ascii=False))
        print('CODE 0003 language:', n['language'], 'checkErr:', n['checkErr'], 'dependencyData:', n.get('dependencyData'))
        print('CODE 0003 outputs:', json.dumps(n['outputs'],ensure_ascii=False))
