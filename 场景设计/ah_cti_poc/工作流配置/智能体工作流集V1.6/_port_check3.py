# -*- coding: utf-8 -*-
import subprocess, json, ast
repo = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
base = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'
dir_ = base

def git_show(rev, path):
    return subprocess.run(['git', '-C', repo, 'show', rev + ':' + path],
                          capture_output=True).stdout.decode('utf-8-sig')

def const_val(src, name):
    try:
        tree = ast.parse(src)
    except Exception:
        return None
    for n in ast.walk(tree):
        if isinstance(n, ast.Assign) and any(getattr(t, 'id', None) == name for t in n.targets):
            try:
                return ast.literal_eval(n.value)
            except Exception:
                return None
    return None

j = json.loads(git_show('7e1375db', dir_ + 'wf_sub_01_需求分析.json'))
n108 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('108')][0]['code']
n109 = [n for n in j['workFlowSchemaJSON']['nodes'] if n['id'].endswith('109')][0]['code']

for rev in ['7e1375db', '61fbbe3', '447dd35']:
    for f in ['gen_workflows_v2.py', 'gen_workflows.py']:
        try:
            s = git_show(rev, dir_ + f)
        except Exception:
            continue
        m = const_val(s, 'CODE_EXTRACT_MERGE') == n108
        r = const_val(s, 'CODE_RENDER_TABLE') == n109
        print(rev, f, 'EXTRACT==json108:', m, 'RENDER==json109:', r)
