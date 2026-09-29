import json, io

t = json.load(io.open('wf_sub_08_审批进度查询.json', encoding='utf-8'))
s = json.load(io.open('wf_sub_07_监控运维.json', encoding='utf-8'))

print('TOP KEYS equal:', list(t.keys()) == list(s.keys()))
for k in set(t) | set(s):
    tv, sv = t.get(k), s.get(k)
    if tv != sv and not isinstance(tv, (dict, list)):
        print('  top', k, 'T=', repr(tv)[:100], 'S=', repr(sv)[:100])

tw, sw = t['workFlowSchemaJSON'], s['workFlowSchemaJSON']
print('SCHEMA KEYS T:', list(tw.keys()), 'S:', list(sw.keys()))

# per-node type sequence
print('T types:', [n.get('type') for n in tw['nodes']])
print('S types:', [n.get('type') for n in sw['nodes']])

# edges shape
print('T edge sample:', json.dumps(tw['edges'][0], ensure_ascii=False))
print('S edge sample:', json.dumps(sw['edges'][0], ensure_ascii=False))
print('T edges count', len(tw['edges']), 'S', len(sw['edges']))
