import re, io, os, json

d = r'wf_runner/logs/verify_20260928_131831'
for fn in sorted(os.listdir(d)):
    if fn.startswith('wf_sub_01'):
        continue
    p = os.path.join(d, fn)
    t = io.open(p, encoding='utf-8').read()
    blocks = re.split(r'\n## \[', t)
    print('=====', fn[:30])
    for b in blocks[1:]:
        head = b.split('\n', 1)[0]
        if '插件（type=3）' in b or '代码（type=6）' in b:
            outs = b.split('### 输出', 1)
            seg = outs[1] if len(outs) > 1 else ''
            n = seg.count('"offer_seq"')
            if n:
                st = re.search(r'状态\*\*：(ok|error|fallback)', b)
                print('  node', head[:34], '| offer_seq:', n, '| status', st.group(1) if st else '?')
