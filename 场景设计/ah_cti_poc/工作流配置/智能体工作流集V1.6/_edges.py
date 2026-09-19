# -*- coding: utf-8 -*-
import sys, io, json, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
for f in ['wf_sub_00_需求提报.json','wf_sub_07_监控运维.json','wf_sub_03_规格稽核.json']:
    d = json.load(io.open(f, encoding='utf-8'))
    wsf = d['workFlowSchemaJSON']
    ttl = {n['id']: n.get('nodeMeta',{}).get('title','') for n in wsf['nodes']}
    print('###', f)
    for e in wsf['edges']:
        print('   %s[%s] --%s--> %s[%s]' % (ttl.get(e['startId'],'?'), e['startId'][-3:], e.get('sourcePort'), ttl.get(e['endId'],'?'), e['endId'][-3:]))
