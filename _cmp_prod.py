import glob, os

A = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6'
B = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6-prod'
REPL = [
    ('http://10.86.13.201:31281', 'http://172.30.8.52:6174'),
    ('http://10.86.13.201:31280', 'http://szyg-stq-test.anhuitelecom.com:10001/prod-ai'),
]


def rep(t):
    for o, n in REPL:
        t = t.replace(o, n)
    return t


srcs = sorted(glob.glob(A + '/*.json'))
bad = 0
for f in srcs:
    n = os.path.basename(f)
    got = os.path.join(B, n)
    if not os.path.exists(got):
        print('MISSING', n)
        bad += 1
        continue
    if rep(open(f, encoding='utf-8').read()) != open(got, encoding='utf-8').read():
        print('DIFF', n)
        bad += 1
print('RESULT', 'OK' if bad == 0 else 'BAD ' + str(bad), 'total', len(srcs))
