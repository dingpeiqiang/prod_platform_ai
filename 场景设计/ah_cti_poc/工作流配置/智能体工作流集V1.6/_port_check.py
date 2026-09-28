# -*- coding: utf-8 -*-
import subprocess, io, re, os
repo = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
base = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'

def git_show(rev, path):
    return subprocess.run(['git', '-C', repo, 'show', rev + ':' + path],
                          capture_output=True).stdout.decode('utf-8-sig')

def extract(src, name):
    m = re.search(r'\n' + name + r' = \(\n', src)
    start = m.start() + 1
    end = src.index('\n)\n', start) + 3
    return src[start:end]

cur_path = repo + '/' + base + 'gen_workflows.py'
cur = io.open(cur_path, encoding='utf-8').read()
v2 = git_show('7e1375db', base + 'gen_workflows_v2.py')

for name in ['CODE_EXTRACT_MERGE', 'CODE_RENDER_TABLE']:
    old = extract(cur, name)
    new = extract(v2, name)
    print(name, 'old_len', len(old), 'new_len', len(new), 'equal', old == new)
    if old != new:
        io.open(repo + '/_old_' + name + '.txt', 'w', encoding='utf-8').write(old)
        io.open(repo + '/_new_' + name + '.txt', 'w', encoding='utf-8').write(new)
