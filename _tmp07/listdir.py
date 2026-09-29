import os, shutil
specdir = None
for r, d, fs in os.walk('.'):
    if 'node_modules' in r or '.git' in r or 'target' in r:
        continue
    if '工作流JSON开发规范.md' in fs:
        specdir = r
print('specdir', repr(specdir))
for f in sorted(os.listdir(specdir)):
    print(f, os.path.getsize(os.path.join(specdir, f)) if os.path.isfile(os.path.join(specdir, f)) else '<dir>')
