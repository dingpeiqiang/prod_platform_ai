import os, subprocess, sys

base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
v16 = None
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    if os.path.basename(root).endswith('V1.6') and 'gen_import_requests.py' in fs:
        v16 = root
        break

def run(script, cwd):
    print('RUN', script, 'in', os.path.basename(cwd))
    r = subprocess.run([sys.executable, script], cwd=cwd, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    print('RC', r.returncode)
    out = (r.stdout or '') + (r.stderr or '')
    sys.stdout.buffer.write(out[-4000:].encode('utf-8', 'replace'))
    print()

run('gen_import_requests.py', v16)
# locate validate script
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    if '_validate_flows.py' in fs:
        run('_validate_flows.py', root)
        break
