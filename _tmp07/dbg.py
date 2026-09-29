import os
base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    if 'gen_workflows.py' in fs:
        print(repr(root))
        print('  contains V1.6:', 'V1.6' in root)
        print('  basename:', os.path.basename(root))
