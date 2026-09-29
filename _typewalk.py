import json, io

def load(p):
    return json.load(io.open(p, encoding='utf-8'))

T = load('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_08_审批进度查询.json')

# Report every key path with its value TYPE, across the whole tree, focusing on non-string scalars in odd places
def walk(o, path=''):
    if isinstance(o, dict):
        for k, v in o.items():
            walk(v, path + '/' + str(k))
    elif isinstance(o, list):
        for i, v in enumerate(o):
            walk(v, path + '[%d]' % i)
    else:
        if not isinstance(o, str):
            print('NON-STRING', path, '=', repr(o), type(o).__name__)

walk(T)
print('--- string lengths > 2000 ---')
def walk2(o, path=''):
    if isinstance(o, dict):
        for k, v in o.items():
            walk2(v, path + '/' + str(k))
    elif isinstance(o, list):
        for i, v in enumerate(o):
            walk2(v, path + '[%d]' % i)
    elif isinstance(o, str) and len(o) > 2000:
        print('LONG', path, len(o))
walk2(T)
