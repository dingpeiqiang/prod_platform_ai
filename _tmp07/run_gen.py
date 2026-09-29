import os, subprocess, sys
base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
d = None
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    if 'gen_workflows.py' in fs and os.path.basename(root).endswith('V1.6'):
        d = root
        break
print('RUN in', d)
r = subprocess.run([sys.executable, 'gen_workflows.py'], cwd=d, capture_output=True, text=True, encoding='utf-8', errors='replace')
print('RC', r.returncode)
print(r.stdout[-3000:])
print('ERR', r.stderr[-3000:])
