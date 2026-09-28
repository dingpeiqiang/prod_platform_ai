import json, urllib.request, sys
sys.stdout.reconfigure(encoding='utf-8')
BASE = '场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/'
URL = 'https://devcloud.sitechcloud.com/km/sagentPlatform/aiFlow/workFlowScriptSaveV3'
headers = json.load(open(BASE + 'import_headers.json', encoding='utf-8'))
req_body = json.load(open(BASE + 'import_requests_out/wf_sub_12__import.json', encoding='utf-8'))
data = json.dumps(req_body, ensure_ascii=False).encode('utf-8')

# probe: api with GET-style body (some gateways want same), then with action only
def post(tag, body, extra=None):
    h = dict(headers)
    if extra:
        h.update(extra)
    b = json.dumps(body, ensure_ascii=False).encode('utf-8')
    r = urllib.request.Request(URL, data=b, headers=h, method='POST')
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            txt = resp.read().decode('utf-8', 'replace')
            print(f'[{tag}] HTTP', resp.status, '|', txt[:300].replace('\n', ' '))
    except urllib.error.HTTPError as e:
        print(f'[{tag}] HTTPError', e.code, '|', e.read().decode('utf-8', 'replace')[:300])
    except Exception as e:
        print(f'[{tag}] ERR', repr(e))

post('full', req_body)
post('min-org-proj', {'schema': req_body['schema'], 'action': 'A', 'orgId': '10000', 'projectId': req_body['projectId']})
post('no-cookie', req_body, {'Cookie': ''})
