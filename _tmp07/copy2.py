import os, shutil
specdir = None
for r, d, fs in os.walk('.'):
    if 'node_modules' in r or '.git' in r or 'target' in r:
        continue
    if '工作流JSON开发规范.md' in fs:
        specdir = r
os.makedirs('_tmp07', exist_ok=True)
for f, dst in [('工作流JSON开发规范.md', 'json_spec.md'), ('平台能力与工具清单.md', 'ability.md')]:
    p = os.path.join(specdir, f)
    if os.path.exists(p):
        shutil.copy(p, os.path.join('_tmp07', dst))
        print('copied', f, '->', dst)
    else:
        print('missing', f)
