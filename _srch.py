# -*- coding: utf-8 -*-
import sys, os
sys.stdout.reconfigure(encoding='utf-8')
roots = ['.']
skip = ('.git', 'node_modules', '__pycache__', '_oldgen', 'logs', 'target', 'dist')
keys = ['映射表', '语义识别直调', 'wf_sub_11', '发起需求审批']
for root in roots:
    for r, d, fs in os.walk(root):
        if any(s in r for s in skip):
            continue
        for f in fs:
            if f.endswith(('.json', '.md', '.txt', '.yml', '.yaml')):
                p = os.path.join(r, f)
                try:
                    t = open(p, encoding='utf-8', errors='ignore').read()
                except Exception:
                    continue
                if 'wf_sub_11' in t and '映射表' in t:
                    print(p, len(t))
