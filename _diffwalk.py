import json, io

a = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_08_审批进度查询.json', encoding='utf-8'))
b = json.load(io.open('场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6-prod/wf_sub_08_审批进度查询.json', encoding='utf-8'))


def walk(pa, pb, path=''):
    if type(pa) != type(pb):
        print('TYPE', path, type(pa).__name__, type(pb).__name__)
        return
    if isinstance(pa, dict):
        for k in set(pa) | set(pb):
            if k not in pa:
                print('ONLY-P', path + '/' + k, repr(pb[k])[:120])
            elif k not in pb:
                print('ONLY-T', path + '/' + k, repr(pa[k])[:120])
            else:
                walk(pa[k], pb[k], path + '/' + k)
    elif isinstance(pa, list):
        if len(pa) != len(pb):
            print('LEN', path, len(pa), len(pb))
        for i, (x, y) in enumerate(zip(pa, pb)):
            walk(x, y, path + '[%d]' % i)
    else:
        if pa != pb:
            print('DIFF', path, '\n   T:', repr(pa)[:300], '\n   P:', repr(pb)[:300])


walk(a['workFlowSchemaJSON'], b['workFlowSchemaJSON'])
