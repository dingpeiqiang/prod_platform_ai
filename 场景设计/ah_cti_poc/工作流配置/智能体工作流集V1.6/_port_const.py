# -*- coding: utf-8 -*-
import subprocess, json, io, re
repo = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
base = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'

def git_show(rev, path):
    return subprocess.run(['git', '-C', repo, 'show', rev + ':' + path],
                          capture_output=True).stdout.decode('utf-8-sig')

j = json.loads(git_show('7e1375db', base + 'wf_sub_01_需求分析.json'))
n108 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('108')][0]['code']
n109 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('109')][0]['code']

p = repo + '/' + base + 'gen_workflows.py'
src = io.open(p, encoding='utf-8').read()

for name, code in (('CODE_EXTRACT_MERGE', n108), ('CODE_RENDER_TABLE', n109)):
    m = re.search(r'\n' + name + r' = \(\n', src)
    start = m.start() + 1
    end = src.index('\n)\n', start) + 3
    rep = '\n' + name + ' = ' + repr(code) + '\n'
    src = src[:start - 1] + rep + src[end:]

io.open(p, 'w', encoding='utf-8').write(src)
print('constants replaced. EXTRACT len', len(n108), 'RENDER len', len(n109))
