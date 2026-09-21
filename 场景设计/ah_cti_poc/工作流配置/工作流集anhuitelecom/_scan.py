# -*- coding: utf-8 -*-
import sys, io, json, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
for f in sorted(glob.glob('wf_sub_0[0-9]*.json')):
    d = json.load(io.open(f, encoding='utf-8'))
    wsf = d['workFlowSchemaJSON']
    for n in wsf['nodes']:
        if n['type']==9:
            c = n['outputs'].get('content','')
            has = 'xsbot-panel' in c
            npanel = c.count('xsbot-panel')
            print('%-22s end %s panel=%d inputs=%s' % (f[-22:], n['id'][-4:], npanel, [i['name'] for i in n['inputs']]))
