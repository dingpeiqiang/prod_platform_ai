# -*- coding: utf-8 -*-
import asyncio, json, sys, io, importlib.util, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
import gen_workflows_v2 as G

T = r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\方案\templates"
TMP = r"C:\Users\a1521\AppData\Local\Temp"

class Args:
    def __init__(self, p): self.params = p

def run(code, params):
    ns = {'Output': dict}
    exec(compile(code, '<code>', 'exec'), ns)
    return asyncio.new_event_loop().run_until_complete(ns['main'](Args(params)))

def load_mod(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m

orig_mn = load_mod(os.path.join(TMP, 'orig_merge_nested.py'), 'orig_mn')

schema_s = open(os.path.join(T, 'personMainPrc.schema.json'), encoding='utf-8').read()
elements_s = open(os.path.join(T, '_test_elements_personMainPrc.json'), encoding='utf-8').read()

# REAL plugin output shape (toFields24): similarOffer = {similarOfferId, similarOfferName, series, sub_type, fields:[{field,category,value}]}
offer_real = {
    "similarOfferId": "900102308",
    "similarOfferName": "5G-A套餐单品299元",
    "similarityScore": "0.92",
    "similarityDesc": "同名同档位",
    "offerInfo": {
        "similarOfferId": "900102308",
        "similarOfferName": "5G-A套餐单品299元",
        "series": "5G-A",
        "sub_type": "主资费",
        "fields": [
            {"field": "套餐名称", "category": "产品属性", "value": "5G-A套餐单品299元"},
            {"field": "套餐档位", "category": "产品属性", "value": "299元"},
            {"field": "计费周期", "category": "产品属性", "value": "自然月"},
            {"field": "国内通用流量", "category": "套餐内基础资源", "value": "60GB"},
            {"field": "本地语音", "category": "套餐内基础资源", "value": "1000分钟"},
            {"field": "是否允许办理副卡", "category": "套餐内权益配置", "value": "允许"},
            {"field": "套外流量-计费标准", "category": "套外资费标准", "value": "0.29元/MB"},
            {"field": "退订规则", "category": "变更/退订/拆机", "value": "允许退订，次月生效"},
            {"field": "流量结转规则", "category": "计费/支付/风控", "value": "结转"},
        ],
    },
}

print('=== workflow merge_nested with REAL plugin offer shape ===')
wf = run(G.CODE_MERGE_NESTED, {'schema_json': schema_s, 'elements_json': elements_s,
                               'offer_json': json.dumps(offer_real, ensure_ascii=False)})
wf_payload = json.loads(wf['payload'])
wf_meta = json.loads(wf['meta'])
ai_cnt = sum(1 for v in wf_meta.values() if v.get('source') == 'AI补全')
print('AI补全 leaves =', ai_cnt)
print('pending_required =', wf['pending_required'])

print()
print('=== original _merge_flat path (24-field x-label) ===')
# emulate original track: elements as 24-field array + offer fields
elements_flat = [{"field": "套餐名称", "category": "产品属性", "value": "5G-A套餐单品299元"},
                 {"field": "套餐档位", "category": "产品属性", "value": "299元"}]
spec = importlib.util.spec_from_file_location('cpcp', os.path.join(TMP, 'orig_cpcp_api.py'))
cpcp = importlib.util.module_from_spec(spec); spec.loader.exec_module(cpcp)
merged_flat = cpcp._merge_flat(elements_flat, offer_real['offerInfo']['fields'])
filled = [m for m in merged_flat if m['value']]
print('merged fields with value =', len(filled), '/ total', len(merged_flat))
for m in merged_flat:
    print('  %-24s %-16s %s' % (m['field'], m['source'], m['value']))
