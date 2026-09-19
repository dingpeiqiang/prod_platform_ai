# -*- coding: utf-8 -*-
"""重建 wf_sub_11 原始（pre-v2）结束节点：去除新增的 content/b00e 代码节点，
恢复结束节点 1106 的原始 content（话术 + 内联 stage=11 xsbot-panel JSON）与 inputs，
并把边 1105->b00e->1106 恢复为 1105->1106。随后对全部 12 个工作流统一执行 v3 变换。
"""
import sys, io, json, re, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

sys.path.insert(0, '.')
import importlib
_trans = importlib.import_module('_transform_v3')  # noqa

PANEL_TPL = ('{"version":"1.0","message_id":"{chat_id}","panels":['
             '{"panel":"right","mode":"external","url":"http://10.86.13.201:31280/ops-web/config-workbench.html?offer_id={req_id}&name=&chatId={chat_id}&stage=11&view=workbench","title":"配置工作台"},'
             '{"panel":"right","mode":"external","url":"http://10.86.13.201:31280/ops-web/config-workbench.html?offer_id={req_id}&name=&chatId={chat_id}&stage=11&view=stage","title":"审批详情"}]}')

fname = 'wf_sub_11_发起需求审批.json'
d = json.load(io.open(fname, encoding='utf-8'))
wsf = d['workFlowSchemaJSON']
nodes = wsf['nodes']
edges = wsf['edges']

# 1) remove b00e code node (the content builder from broken run)
nodes = [n for n in nodes if not (n.get('type') == 6 and n['id'][-4:] == 'b00e' and n.get('nodeMeta', {}).get('title', '').startswith('xsbot'))]

# 2) reconstruct end node 1106
def ref(block, rel, name, desc):
    return {"blockID": block, "relName": rel, "name": name, "description": desc,
            "type": "ref", "required": True, "content": "",
            "nameValue": [block, "%s,%s" % (block, rel)], "currValue": "%s,%s" % (block, rel)}

for n in nodes:
    if n['type'] == 9 and n['id'][-4:] == '1106':
        content = ("{approval_receipt}\n\n"
                   "**页面地址**：通过 xsbot-panel 外链加载（配置工作台 / 环节业务页）：\n"
                   "```xsbot-panel\n" + PANEL_TPL + "\n```\n")
        n['outputs']['content'] = content
        n['inputs'] = [
            ref('a1b2c3d4-0000-4000-8000-000000001105', 'approval_receipt', 'approval_receipt', '审批发起回执正文'),
            ref('a1b2c3d4-0000-4000-8000-000000001101', 'req_id', 'req_id', '需求单号'),
            ref('a1b2c3d4-0000-4000-8000-000000001101', 'chat_id', 'chat_id', '会话消息ID'),
        ]
        break

# 3) rewire edges: 1105 -> b00e -> 1106  =>  1105 -> 1106
new_edges = []
for e in edges:
    if e['endId'][-4:] == 'b00e' and e['startId'][-4:] == '1105':
        new_edges.append({"sourcePort": e.get('sourcePort'), "endId": "a1b2c3d4-0000-4000-8000-000000001106",
                          "startId": "a1b2c3d4-0000-4000-8000-000000001105"})
    elif e['startId'][-4:] == 'b00e' and e['endId'][-4:] == '1106':
        continue
    else:
        new_edges.append(e)
edges[:] = new_edges

wsf['nodes'] = nodes
wsf['edges'] = edges
io.open(fname, 'w', encoding='utf-8').write(json.dumps(d, ensure_ascii=False, indent=2))
print('reconstructed', fname)

# 4) apply v3 transform to all 12
for f in sorted(glob.glob('wf_sub_*.json')):
    _trans.transform_file(f)
    print('v3 transformed', f)
