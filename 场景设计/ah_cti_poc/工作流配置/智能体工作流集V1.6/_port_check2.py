# -*- coding: utf-8 -*-
import subprocess, json, io, ast
repo = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
base = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'

def git_show(rev, path):
    return subprocess.run(['git', '-C', repo, 'show', rev + ':' + path],
                          capture_output=True).stdout.decode('utf-8-sig')

def const_val(src, name):
    tree = ast.parse(src)
    for n in ast.walk(tree):
        if isinstance(n, ast.Assign) and any(getattr(t, 'id', None) == name for t in n.targets):
            return ast.literal_eval(n.value)
    return None

v2 = git_show('7e1375db', base + 'gen_workflows_v2.py')
j = json.loads(git_show('7e1375db', base + 'wf_sub_01_需求分析.json'))
n108 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('108')][0]['code']
n109 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('109')][0]['code']

for name, code in (('CODE_EXTRACT_MERGE', n108), ('CODE_RENDER_TABLE', n109)):
    v = const_val(v2, name)
    print(name, 'v2_const==json_code:', v == code, 'len', len(v or ''), len(code))
