import os
base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
target = None
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    for f in fs:
        if f == 'gen_workflows.py' and os.path.basename(root).endswith('V1.6'):
            target = os.path.join(root, f)
print('TARGET', target)
s = open(target, encoding='utf-8').read()
i = s.find('# wf_sub_07')
j = s.find('files7f = workflow(', i)
k = s.find('s8f = []', j)
print('idx', i, j, k, 'len', len(s))
open('_tmp07/s7f_real.txt', 'w', encoding='utf-8').write(s[i - 90:k])
print('written')
