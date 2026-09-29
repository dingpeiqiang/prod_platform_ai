import json, io, glob, ast, re

for f in sorted(glob.glob('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json')):
    d = json.load(io.open(f, encoding='utf-8'))
    for n in d['workFlowSchemaJSON']['nodes']:
        if n.get('type') != 6:
            continue
        code = n.get('code') or ''
        try:
            ast.parse(code)
        except SyntaxError as e:
            print('SYNTAX ERR', f.split('\\')[-1], n['nodeMeta'].get('title'), 'line', e.lineno, e.msg)
            print('   >>>', (code.splitlines()[e.lineno-1] if e.lineno and e.lineno <= len(code.splitlines()) else ''))
print('done')
