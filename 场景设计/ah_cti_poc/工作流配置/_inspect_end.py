import re, io

fn = 'wf_sub_01_产销品-需求分析_20260928_131833.log.md'
t = io.open(r'wf_runner/logs/verify_20260928_131831/' + fn, encoding='utf-8').read()
b = [x for x in re.split(r'\n## \[', t) if x.startswith('111]')][0]
seg = b.split('### 输出', 1)[1]
parts = seg.split('```text')
val = parts[1].rsplit('```', 1)[0]
print('val len', len(val))
print('table title count', val.count('产销品配置方案'))
print('fee matches', re.findall(r'套餐月费\*\*：\s*(\d+)', val))
print('待补充 count', val.count('待补充'))
