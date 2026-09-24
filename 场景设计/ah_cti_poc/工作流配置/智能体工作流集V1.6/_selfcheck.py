import json, glob, re

ALLOWED = {0, 1, 2, 3, 6, 9, 13}


def code_returns(code):
    keys = set()
    for m in re.finditer(r'ret:\s*Output\s*=\s*\{', code):
        i = m.end()
        depth = 1
        s = ''
        while i < len(code) and depth > 0:
            c = code[i]
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
            if depth > 0:
                s += c
            i += 1
        # only top-level keys: depth==1 means not inside a nested brace
        d = 0
        for km in re.finditer(r'([\'"])(\w+)\1\s*:', s):
            pre = s[:km.start()]
            d = pre.count('{') - pre.count('}') + pre.count('[') - pre.count(']')
            if d <= 0:
                keys.add(km.group(2))
    return keys


def main():
    files = sorted(glob.glob('wf_sub_*.json'))
    bad = 0
    for f in files:
        d = json.load(open(f, encoding='utf-8'))
        nodes = d['workFlowSchemaJSON']['nodes']
        ids = {n['id'] for n in nodes}
        for n in nodes:
            t = n.get('type')
            title = n.get('nodeMeta', {}).get('title')
            if t not in ALLOWED:
                print('BAD TYPE', f, title, t)
                bad += 1
            if t == 6:
                outs = {o['name'] for o in n.get('outputs', [])}
                rets = code_returns(n.get('code', ''))
                extra = rets - outs
                if extra:
                    print('RET>OUT', f, title, sorted(extra))
                    bad += 1
        for e in d['workFlowSchemaJSON']['edges']:
            if e['startId'] not in ids or e['endId'] not in ids:
                print('DANGLING EDGE', f, e)
                bad += 1
    print('files:', len(files), 'issues:', bad)


main()
