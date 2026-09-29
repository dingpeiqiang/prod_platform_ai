import os

base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai'
target = None
for root, dirs, fs in os.walk(base):
    if 'node_modules' in root or '.git' in root:
        continue
    if 'gen_workflows.py' in fs and os.path.basename(root).endswith('V1.6'):
        target = os.path.join(root, 'gen_workflows.py')
        break
assert target, 'gen_workflows.py not found'
s = open(target, encoding='utf-8').read()

new_block = open('_tmp07/s7f_new.txt', encoding='utf-8').read().rstrip() + '\n'
const = open('_tmp07/monitor_analyze_const.txt', encoding='utf-8').read()

i = s.find('# ============================================================\n# wf_sub_07')
assert i > 0, 's7f header not found'
# end: files7f = workflow(...) .. up to the line before next section (s8f header comment)
j = s.find('s8f = []', i)
assert j > i, 's8f not found'

# build replacement: constant + new_block + blank line
replacement = const + new_block + '\n'
s2 = s[:i] + replacement + s[j:]

# sanity: exactly one new s7f
assert s2.count('# wf_sub_07') == 1
open(target, 'w', encoding='utf-8').write(s2)
print('patched', target, 'oldlen', len(s), 'newlen', len(s2))
