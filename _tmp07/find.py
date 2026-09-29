import os, shutil, sys

# find the 工作流制作 dir (contains wf_runner and gen_workflows.py) and 场景 dir
targets = {}
for r, d, fs in os.walk('.'):
    if 'node_modules' in r or '.git' in r or 'target' in r:
        continue
    if 'gen_workflows.py' in fs and 'wf_common.py' in fs and 'V1.6' in r:
        targets.setdefault('v16', r)
    if 'workflowJSON开发规范' in r or ('工作流JSON开发规范.md' in fs):
        targets.setdefault('specdir', r)

print(targets)

# list 场景设计/ah_cti_poc/场景
for r, d, fs in os.walk('.'):
    if 'node_modules' in r or '.git' in r or 'target' in r:
        continue
    if os.path.basename(r) == '场景' and 'ah_cti_poc' in r:
        print('SCENE_DIR', repr(r))
        for f in fs:
            print('  ', f, os.path.getsize(os.path.join(r, f)))
