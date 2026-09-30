import io, glob, os, json, re

base = os.path.dirname(os.path.abspath(__file__))
ph = re.compile(r'\{([^{}]+)\}')
BADCH = (',', '，', '"', "'", '（', '）', '(', ')')

for f in sorted(glob.glob(os.path.join(base, 'wf_sub_*.json'))):
    d = json.load(io.open(f, encoding='utf-8'))
    for n in d['workFlowSchemaJSON']['nodes']:
        t = n.get('type')
        ti = n.get('nodeMeta', {}).get('title')
        ins = n.get('inputs')
        names = set()
        if isinstance(ins, list):
            names = {i.get('name') for i in ins}
        elif isinstance(ins, dict):
            for v in ins.values():
                if isinstance(v, list):
                    names |= {i.get('name') for i in v if isinstance(i, dict)}
        texts = []
        if t == 9 and isinstance(n.get('outputs'), dict):
            texts.append(n['outputs'].get('content') or '')
        if t == 1 and isinstance(ins, dict):
            for p in ins.get('llmParam', []):
                texts.append(p.get('content') or '')
        for tx in texts:
            for m in ph.findall(tx):
                key = m.strip()
                tag = None
                if key not in names:
                    tag = 'MISMATCH' if any(c in key for c in BADCH) else 'MISSING'
                elif any(c in key for c in BADCH):
                    tag = 'BADCHAR'
                if tag:
                    print('%s %s t=%s %s | %s PH=%r' % (
                        os.path.basename(f)[:14], n['id'][-4:], t, ti, tag, m))
