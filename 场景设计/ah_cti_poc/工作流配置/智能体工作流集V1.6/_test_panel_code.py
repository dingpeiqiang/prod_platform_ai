# -*- coding: utf-8 -*-
# 临时校验：执行每个 xsbot-panel 代码节点，断言输出为紧凑 JSON（无空格）、单面板、config-workbench?view=stage
import asyncio, glob, json, os, sys

here = os.path.dirname(os.path.abspath(__file__))
sys.stdout.reconfigure(encoding='utf-8')
fails = total = 0

for path in sorted(glob.glob(os.path.join(here, 'wf_sub_*.json'))):
    if 'wf_sub_11' in os.path.basename(path):
        continue
    d = json.load(open(path, encoding='utf-8'))
    s = d['workFlowSchemaJSON']
    for n in s['nodes']:
        if n.get('type') != 6 or 'xsbot-panel' not in (n.get('code') or ''):
            continue
        total += 1
        class A:
            def __init__(self, p):
                self.params = p
        scope = {}
        exec(compile(n['code'], '<p>', 'exec'), scope)
        out = asyncio.new_event_loop().run_until_complete(scope['main'](A({'chat_id': '1', 'offer_id': '1', 'offer_name': ''})))
        panel = out['panel']
        body = panel.split('```xsbot-panel\n', 1)[1].split('\n```', 1)[0]
        errs = []
        if ': ' in body or ', ' in body:
            errs.append('含空格(非紧凑)')
        if body.count('"url"') != 1:
            errs.append('url数!=1')
        if 'config-workbench.html' not in body or 'view=stage' not in body:
            errs.append('url非config-workbench?view=stage')
        if 'product-detail.html' in body or 'view=workbench' in body:
            errs.append('残留旧面板')
        if not body.startswith('{"version":"1.0"'):
            errs.append('头部不符')
        st = 'OK' if not errs else 'FAIL'
        if st != 'OK':
            fails += 1
        print('%s | %s | %s%s' % (os.path.basename(path), n['nodeMeta']['title'], st,
                                  (' | ' + ';'.join(errs)) if errs else ''))
print('---- total=%d fails=%d ----' % (total, fails))
sys.exit(0 if fails == 0 else 1)
