import sys, re
sys.path.insert(0, '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6')
sys.stdout.reconfigure(encoding='utf-8')
import wf_common as w
for name in ['CODE_POLL_AUDIT', 'CODE_AUDIT_QUERY', 'CODE_PACK_RECORD']:
    code = getattr(w, name)
    keys = set()
    for m in re.finditer(r'Output\s*=\s*\{(.*?)\}', code, re.S):
        for k in re.findall(r"'([A-Za-z_][A-Za-z0-9_]*)'\s*:", m.group(1)):
            keys.add(k)
    print(name, sorted(keys))
