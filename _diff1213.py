import json, glob, re
import sys
sys.stdout.reconfigure(encoding='utf-8')

BASE = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'
def norm(f):
    d = json.load(open(glob.glob(BASE + f)[0], encoding='utf-8'))
    wf = d['workFlowSchemaJSON']
    s = json.dumps(wf, ensure_ascii=False, indent=1)
    # normalize id tails 22xx->23xx and vice versa? just replace digits
    return s

a = norm('wf_sub_12*.json')
b = norm('wf_sub_13*.json')
# rewrite 22xx -> 2xxx and 23xx -> 2xxx in both
def canon(s):
    s = re.sub(r'a1b2c3d4-0000-4000-8000-00000000(2[23])(\d\d)', r'ID\1\2', s)
    return s
ca = canon(a)
cb = canon(b)
import difflib
la = ca.splitlines()
lb = cb.splitlines()
for line in difflib.unified_diff(la, lb, 'sub12', 'sub13', lineterm='', n=1):
    print(line)
