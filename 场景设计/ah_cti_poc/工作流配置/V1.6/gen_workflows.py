# -*- coding: utf-8 -*-
# 生成产销品加载AI应用 V1.7 工作流导出JSON（8子工作流，主工作流已删除）
# V1.7 LLM智能调度模式：智能体按【意图→子工作流智能调度映射表】直调 wf_sub_01~08，
# 主流程 wf_cpcp_main 固定编排弃用并删除；四环节结果存储下沉各子工作流
# （wf_sub_02~05 结束前 save_node_result，req_id=入参req_id，node_name=config/spec/fee/test），
# 供 wf_sub_06 审批推送的四环节门禁自查；
# ID 规范（统一键）：全链路唯一批次标识=req_id（PLAN+yyyyMMddHHmmss+3位随机数，执行方案与
# 执行主干共用同一 req_id，原 plan_id/execution_id 双键合并为 req_id 单键，同键覆盖写）
import json, os, io, uuid

BASE = os.path.dirname(os.path.abspath(__file__))
BASE_URL = "http://10.86.13.201:31281"

def nid(seq, ns="a1b2c3d4"):
    return "%s-0000-4000-8000-%012d" % (ns, seq)

def inp(name, desc, ptype="string", required=True, content="", ref_block="", ref_rel=""):
    d = {
        "blockID": ref_block, "relName": ref_rel, "name": name,
        "description": desc, "type": "ref" if ref_block else ptype,
        "required": required, "content": content
    }
    if ref_block:
        d["nameValue"] = [ref_block, ref_block + "," + ref_rel]
        d["currValue"] = ref_block + "," + ref_rel
    return d

def out(name, desc, ptype="string"):
    return {"name": name, "description": desc, "type": ptype, "content": ""}


# 各工具 array 出参的 item 叶子字段（与自研插件集V1.6 gen_plugins.py 定义严格同步）
ARRAY_ITEM_FIELDS = {
    "field_ontology_reason|violations": [
        ("field", "违规字段名"),
        ("value", "违规值"),
        ("reason", "期望规则（本体定义）"),
    ],
    "field_ontology_reason|fixed": [
        ("field", "字段名"),
        ("value", "原值（补全/修正前）"),
        ("action", "defaulted=默认值补全 / fallback=兜底待补充 / corrected=修正回写 / none=维持"),
        ("corrected", "修正后值（corrected 动作时非空）"),
        ("reason", "处理依据（本体规则）"),
    ],
    "field_ontology_reason|completed": [
        ("field", "字段名"),
        ("value", "补全值（兜底口径字段为\"待补充\"）"),
        ("defaulted", "1=本体默认值补全 / 0=未补全"),
        ("reason", "补全依据（本体规则）"),
    ],
    "field_ontology_reason|fields": [
        ("field", "字段名"),
        ("category", "字段分类（A基础信息/B资源配置/C营销资源/D销售规则）"),
        ("enums", "枚举值（顿号分隔，无枚举为空）"),
        ("rule", "格式/口径规则"),
        ("default_value", "本体默认值"),
        ("fallback", "1=兜底口径字段（不默认补全）/ 0=普通字段"),
    ],
    "query_similar_offer|similarOfferList": [
        ("similarOfferId", "相似销售品ID（如 900102308）"),
        ("similarOfferName", "相似销售品名称"),
        ("similarityScore", "相似度评分（0~1）"),
        ("similarityDesc", "相似原因描述（命中字段/资费结构说明）"),
    ],
    "realtime_spec_audit|error_list": [
        ("item", "问题项（对应配置字段/规则）"),
        ("level", "严重级别：error 阻断 / warning 提示"),
        ("desc", "问题描述，含实际值与期望规则"),
        ("suggest", "整改建议"),
    ],
    "get_test_scenes|testScenes": [
        ("testSceneId", "场景ID"),
        ("testSceneName", "场景名称（套餐新装/副卡加装/套餐退订）"),
        ("testSceneNbr", "场景编码：S_O_TC/S_ADD_CARD/S_U_TC"),
        ("testSceneDesc", "场景描述"),
        ("sort", "排序"),
    ],
    "get_test_result|testScenes": [
        ("testSceneNbr", "场景编码：S_O_TC/S_ADD_CARD/S_U_TC"),
        ("testSceneName", "场景名称"),
        ("testSceneDesc", "场景描述"),
        ("testCaseCount", "测点总数"),
        ("successTestCaseCount", "成功数"),
        ("failTestCaseCount", "失败数"),
        ("testCasePointResults", "测点明细"),
        ("objTestSceneRel", "AI场景总结：resultMsg 场景测试总结/summaryDesc 汇总描述/suggestion 优化建议"),
    ],
    "check_billing_rule|risk_list": [
        ("risk_type", "风险类型：overlap_conflict/negative_fee/boundary_price_gap/overlay_limit_exceeded/custom_rule"),
        ("risk_desc", "风险描述，含冲突/异常明细"),
        ("suggest", "处置建议"),
    ],
    "query_product_monitor|alarm_list": [
        ("alarm_id", "告警单号"),
        ("alarm_level", "告警级别：high/middle/low"),
        ("content", "告警内容"),
        ("alarm_time", "告警时间"),
    ],
}

# 工具6 testScenes.item 内嵌套二层 array：测点明细
TEST_POINT_FIELDS = [
    ("testPointNbr", "测点编码：P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE"),
    ("presetValue", "规格规定值（预期值，取自《产品信息.txt》该销售品规则值）"),
    ("testValue", "实测值（模拟CRM实际生成结果）"),
    ("resultCode", "0 一致 / 1 不一致"),
    ("resultMsg", "比对结论"),
]


def arr_item_node(cname, fields):
    return {"name": "item", "cname": cname, "sechema": [], "type": "object", "required": False} if False else {
        "name": "item", "cname": cname,
        "sechema": [{"name": n, "cname": d, "sechema": [], "type": "string", "required": False}
                    for (n, d) in fields],
        "type": "object", "required": False
    }


def plugin_out(name, desc, ptype="string", code=None):
    """插件节点出参声明：array 出参填充完整 item 树（对齐插件定义），其余 sechema 为空"""
    if ptype != "array":
        return {"name": name, "cname": desc, "sechema": [], "type": ptype, "required": False}
    key = code + "|" + name if code else None
    if key == "get_test_result|testScenes":
        # 二层嵌套：testScenes.item 中 testCasePointResults 为内层 array
        item_leaves = []
        for (n, d) in ARRAY_ITEM_FIELDS[key]:
            if n == "testCasePointResults":
                item_leaves.append({"name": n, "cname": d, "sechema": [
                    arr_item_node("测点明细", TEST_POINT_FIELDS)], "type": "array", "required": False})
            else:
                item_leaves.append({"name": n, "cname": d, "sechema": [], "type": "string", "required": False})
        item = {"name": "item", "cname": desc.split("，")[0] if desc else name,
                "sechema": item_leaves, "type": "object", "required": False}
        return {"name": name, "cname": desc, "sechema": [item], "type": "array", "required": False}
    if key and key in ARRAY_ITEM_FIELDS:
        return {"name": name, "cname": desc, "sechema": [arr_item_node(desc, ARRAY_ITEM_FIELDS[key])],
                "type": "array", "required": False}
    return {"name": name, "cname": desc, "sechema": [], "type": ptype, "required": False}

def start_node(seq, inputs, pos=(15, 135)):
    return {
        "flowJson": None, "inputs": inputs, "checkErr": False,
        "nodeMeta": {"description": "工作流的起始节点，用于设定启动工作流需要的信息", "title": "开始节点"},
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 0
    }

def end_node(seq, title, inputs, out_content, pos=(1030, 135)):
    return {
        "outputs": {"name": "", "type": "string", "content": out_content},
        "settings": {"stream": False, "output_mode": "text"},
        "terminatePlan": "useAnswerContent",
        "flowJson": None, "inputs": inputs, "checkErr": False,
        "prompt_system": "",
        "nodeMeta": {"description": "工作流的最终节点，用于返回工作流运行后的结果信息", "title": title},
        "type": 9, "top_p": 1, "top_k": 0.1, "temperature": 0.1,
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": []
    }

def llm_node(seq, title, prompt, in_refs, outputs, pos=(390, 135), sys_prompt=""):
    inputs = {"llmParam": [{"name": "prompt", "type": "string", "content": prompt}],
              "inputParameters": in_refs}
    return {
        "outputs": outputs, "max_tokens": 2048, "flowJson": None,
        "inputs": inputs, "checkErr": False,
        "prompt_system": sys_prompt,
        "nodeMeta": {"description": title, "title": title},
        "temperature": 0.2, "top_p": 0.5,
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 1
    }

OPS = [{"value": 1, "label": "等于"}, {"value": 2, "label": "不等于"},
       {"value": 3, "label": "长度大于"}, {"value": 4, "label": "长度大于等于"},
       {"value": 5, "label": "长度小于"}, {"value": 6, "label": "长度小于等于"},
       {"value": 7, "label": "包含"}, {"value": 8, "label": "不包含"},
       {"value": 9, "label": "为空"}, {"value": 10, "label": "不为空"},
       {"value": 15, "label": "长度等于"}]

def cond_ref(block_seq, rel, block_title):
    """条件左值：引用某节点出参"""
    b = nid(block_seq)
    return {
        "blockID": b, "relName": rel,
        "nameValue": [b, b + "," + rel],
        "currValue": b + "," + rel,
        "name": "", "description": "", "type": "ref", "content": "",
    }

def cond_str(value):
    """条件右值：字符串常量（对齐样例：currValue 固定 ","，常量放 content）"""
    return {"blockID": "", "relName": "", "nameValue": "", "currValue": ",",
            "name": "", "description": "", "type": "string", "content": value}

def cond_item(left, operator, right=None):
    """单个条件项：left 引用 / operator 操作符 / right 常量(为空操作符时可省略)"""
    item = {"left": left, "right": right if right is not None else cond_str(""),
            "conditions": OPS, "operator": operator}
    return item

def dep_node(block_seq, block_title, rel_names):
    """dependencyData 条目：children 需含 relName/name/type/content 完整字段"""
    b = nid(block_seq)
    return {
        "relName": b, "name": block_title, "disabled": True,
        "children": [{"relName": b + "," + r, "name": r, "type": "string", "content": ""}
                     for r in rel_names]
    }

def selector_node2(seq, title, deps, branches, pos=(530, 135)):
    """条件分支节点（对齐平台真实导出样例格式，可正常导入编辑）
    branches: [(sourcePort, [cond_item, ...]), ...] 按端口顺序排列
    平台约定（样例 wf_sub_01）：条件定义在 port=-1（否则分支），
    port=0 出边由平台自动路由为"如果分支"（无条件定义时兜底）。
    """
    conds = []
    for port, items in branches:
        conds.append({"sourcePort": port, "itemflag": True, "logic": 1,
                      "conditions": items})
    return {
        "flowJson": None,
        "inputs": {"condition": conds},
        "nodeMeta": {"description": "if-else分支判断，符合条件走如果分支，否则走否则分支", "title": title},
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq),
        "dependencyData": deps, "type": 2
    }

def plugin_node(seq, title, code, desc, url, inputs, outputs, pos=(650, 135), method="post"):
    return {
        "outputs": [plugin_out(n, d, t, code=code) for (n, d, t) in outputs],
        "submit_way": method, "flowJson": None, "authentic_info": "",
        "inputs": inputs, "checkErr": False,
        "nodeMeta": {"title": title, "code": code, "description": desc, "version": "1"},
        "authentic_info_new": {"auth_type": "1", "auth_info": {"inparams": [], "params": [], "outparams": []}},
        "id": nid(seq), "position": {"x": pos[0], "y": pos[1]},
        "dependencyData": [], "type": 3, "url": url
    }

def subflow_node(seq, title, desc, work_flow_id, inputs, outputs, pos=(390, 135)):
    return {
        "outputs": [{"name": n, "type": "string", "content": "", "description": d} for (n, d) in outputs],
        "flowJson": None, "inputs": inputs, "checkErr": False, "history": False,
        "nodeMeta": {"workFlowId": work_flow_id, "description": desc, "title": title, "version": "1.0"},
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 13
    }

def loop_node(seq, title, desc, in_refs, outputs, pos=(650, 300)):
    return {
        "outputs": outputs, "flowJson": None,
        "inputs": {"loopParam": {"loopType": "while", "maxLoopCount": 360, "breakCondition": "done == true || failed == true || timeout"},
                   "inputParameters": in_refs},
        "checkErr": False,
        "nodeMeta": {"description": desc, "title": title},
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 6
    }


CODE_004A = (
    "import json, re, random\n"
    "from datetime import datetime\n"
    "from typing import Any, Dict\n"
    "async def main(args):\n"
    "    raw = args.params['plan_output']\n"
    "    if not isinstance(raw, str):\n"
    "        raw = json.dumps(raw, ensure_ascii=False)\n"
    "    reasoned = args.params.get('reasoned_fields') or ''\n"
    "    if not isinstance(reasoned, str):\n"
    "        reasoned = json.dumps(reasoned, ensure_ascii=False)\n"
    "    m = re.search(r'\\{[\\s\\S]*\\}', raw)\n"
    "    if not m:\n"
    "        obj = {}\n"
    "    else:\n"
    "        try:\n"
    "            obj = json.loads(m.group(0))\n"
    "        except Exception:\n"
    "            obj = {}\n"
    "    # 本体推理引擎（节点31 action=reason）返回的推理后字段数组——优先采用，实现引擎兜底闭环\n"
    "    rf = None\n"
    "    if reasoned:\n"
    "        try:\n"
    "            robj = json.loads(reasoned) if isinstance(reasoned, str) else reasoned\n"
    "            if isinstance(robj, dict):\n"
    "                rf = robj.get('fields_json')\n"
    "                if isinstance(rf, str):\n"
    "                    rf = json.loads(rf)\n"
    "            elif isinstance(robj, list):\n"
    "                rf = robj\n"
    "        except Exception:\n"
    "            rf = None\n"
    "    plan_json = obj.get('plan_json')\n"
    "    if not isinstance(plan_json, str):\n"
    "        plan_json = json.dumps(plan_json if plan_json is not None else obj, ensure_ascii=False)\n"
    "    if rf:\n"
    "        try:\n"
    "            pj = json.loads(plan_json)\n"
    "            if isinstance(pj, dict):\n"
    "                pj['fields'] = rf\n"
    "                plan_json = json.dumps(pj, ensure_ascii=False)\n"
    "        except Exception:\n"
    "            pass\n"
    "    # pending_fields 以本体推理引擎结果为准：从推理后字段数组反查 value=待补充 的字段（V2.2 单一事实源，不再采信 LLM 自判）\n"
    "    miss = []\n"
    "    if rf:\n"
    "        miss = [str(f.get('field')) for f in rf if str(f.get('value')) == '待补充']\n"
    "    pf = ','.join(miss)\n"
    "    req_id = 'PLAN' + datetime.now().strftime('%Y%m%d%H%M%S') + '%03d' % random.randint(0, 999)\n"
    "    try:\n"
    "        pj = json.loads(plan_json)\n"
    "        if isinstance(pj, dict):\n"
    "            pj['req_id'] = req_id\n"
    "            plan_json = json.dumps(pj, ensure_ascii=False)\n"
    "    except Exception:\n"
    "        pass\n"
    "    ret: Output = {\n"
    "        \"plan_json\": plan_json,\n"
    "        \"plan_md\": str(obj.get('plan_md') or ''),\n"
    "        \"pending_fields\": str(pf),\n"
    "        \"req_id\": req_id\n"
    "    }\n"
    "    return ret"
)

# CODE_EXTRACT_RECORD：从 query_node_result 出参 list（记录数组JSON）提取 list[0].result_json 原文。
# V2.2 新增：wf_sub_02/03/04/05 自查链路共用——query_node_result 返回的是记录数组
# （[{req_id,node_name,result_json,status,...}]），下游工具需要的执行方案/环节结果原文
# 存在于 list[0].result_json 字段内，须提取后再透传（禁止把数组整体传给落地/稽核等工具）。
CODE_EXTRACT_RECORD = (
    "import json\n"
    "from typing import Any, Dict\n"
    "async def main(args):\n"
    "    raw = args.params['query_list']\n"
    "    if not isinstance(raw, str):\n"
    "        raw = json.dumps(raw, ensure_ascii=False)\n"
    "    record_json = ''\n"
    "    try:\n"
    "        arr = json.loads(raw)\n"
    "        if isinstance(arr, list) and arr:\n"
    "            first = arr[0]\n"
    "            if isinstance(first, dict):\n"
    "                record_json = str(first.get('result_json') or '')\n"
    "        elif isinstance(arr, dict):\n"
    "            record_json = str(arr.get('result_json') or '')\n"
    "    except Exception:\n"
    "        record_json = ''\n"
    "    if not record_json:\n"
    "        record_json = raw\n"
    "    ret: Output = {\n"
    "        \"record_json\": record_json\n"
    "    }\n"
    "    return ret"
)


def code_node(seq, title, code, in_refs, outputs, pos=(650, 300)):
    """type=6 代码节点（对齐平台真实导出：inputs 平铺 list、language=1）"""
    return {
        "outputs": outputs, "code": code, "flowJson": None,
        "inputs": in_refs, "checkErr": False,
        "nodeMeta": {"description": "编写代码，处理输入变量来生成返回值", "title": title},
        "language": 1,
        "id": nid(seq), "position": {"x": pos[0], "y": pos[1]},
        "dependencyData": [], "type": 6
    }


def code_out(name, block_seq, ptype="string"):
    b = nid(block_seq)
    return {"relName": b + "," + name, "name": name, "type": ptype}

def edge(s, e, port=None):
    d = {"sourcePort": port, "endId": nid(e), "startId": nid(s)}
    return d

def workflow(flow_name, flow_remark, flow_id, nodes, edges):
    return {
        "flowName": flow_name,
        "flowRemark": flow_remark,
        "flowIco": "workflowIcon",
        "workFlowSchema": None,
        "workFlowSchemaJSON": {"nodes": nodes, "edges": edges, "flowId": flow_id, "version": "1.6"},
        "userScope": 4,
        "projectId": ""
    }

files = {}

# ============================================================
# 布局后处理：拓扑分层（Sugiyama 简化版）
#   - Kahn 拓扑排序定列（COL_GAP 列距），同列垂直展开（ROW_GAP 行距）
#   - 主干链（最长路径）垂直居中对齐，分支节点上下展开
#   - 结束节点(type=9)列号取其所有前驱最大列+1
# ============================================================
COL_GAP = 360
ROW_GAP = 260
MAIN_Y = 300

def apply_layout(data):
    nodes = data["workFlowSchemaJSON"]["nodes"]
    edges = data["workFlowSchemaJSON"]["edges"]
    by_id = {n["id"]: n for n in nodes}
    # 邻接表与入度
    succ = {n["id"]: [] for n in nodes}
    indeg = {n["id"]: 0 for n in nodes}
    for e in edges:
        s, t = e["startId"], e["endId"]
        if s in by_id and t in by_id and t not in succ[s]:
            succ[s].append(t)
            indeg[t] += 1
    # Kahn
    from collections import deque
    q = deque([i for i in indeg if indeg[i] == 0])
    col = {}
    order = []
    while q:
        u = q.popleft()
        order.append(u)
        for v in succ[u]:
            col[v] = max(col.get(v, 0), col.get(u, 0) + 1)
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)
    # 环兜底：未入拓扑的节点列号 = 前驱最大+1
    for n in nodes:
        if n["id"] not in col:
            col[n["id"]] = 0
    # 最长路径（主干）节点集合
    dist = {}
    for u in order:
        for v in succ[u]:
            d = dist.get(u, col[u]) + 1
            if d > dist.get(v, -1):
                dist[v] = d
    main_path = set()
    # 主干 = 每个 col 上 dist 最大的一条链（贪心回溯终点）
    ends = [u for u in by_id if not succ[u]]
    end_main = max(ends, key=lambda u: dist.get(u, 0)) if ends else None
    u = end_main
    preds = {v: [] for v in by_id}
    for e in edges:
        if e["startId"] in by_id and e["endId"] in by_id:
            preds[e["endId"]].append(e["startId"])
    while u is not None:
        main_path.add(u)
        cand = [p for p in preds[u] if col.get(p, 0) == col.get(u, 0) - 1]
        u = max(cand, key=lambda p: dist.get(p, 0)) if cand else None
    # 分列
    cols = {}
    for n in nodes:
        cols.setdefault(col[n["id"]], []).append(n)
    # 同列排序：主干居中，其余按与主干列的相对顺序
    for c, ns in cols.items():
        mains = [n for n in ns if n["id"] in main_path]
        others = [n for n in ns if n["id"] not in main_path]
        # others 稳定排序：按依赖主干距离（无更好依据时保持生成顺序）
        arranged = []
        half = len(others) // 2
        # 交替放上下方
        below = others[:half]
        above = others[half:]
        arranged = list(reversed(above)) + mains + below
        for i, n in enumerate(arranged):
            dy = (i - (len(arranged) - 1) / 2.0) * ROW_GAP
            n["position"] = {"x": 15 + c * COL_GAP, "y": int(MAIN_Y + dy)}
    return data


s1 = []
s1.append(start_node(1, [
    inp("requirement_text", "销售品需求描述文本或文档内容摘要", required=True),
    inp("requirement_file", "需求文档地址（可选）", required=False),
]))
s1.append(llm_node(2, "需求理解与要素拆解",
    "你是产销品加载需求分析助手。按6步分析：理解需求→提取并拆解业务要素（基础信息/资源配置/营销资源/销售规则四类）→识别信息完整性（字段三态：原始需求/AI补全/待补充）。需求原文：{requirement_text}\n"    "要素拆解字段口径（四类18字段，与执行方案生成保持一致）：\n"
    "A.基础信息：产品名称/产品属性（基础/可选/增值）/产品编码/生效日期/退订规则\n"
    "B.资源配置：流量资源/语音资源/短信资源\n"
    "C.营销资源：套餐固定费/收费方式（按月/按量/一次性）/优惠条件/优惠期\n"
    "D.销售规则：渠道类型/适用地区/订购限制/副卡规则/计费周期/销售品状态\n"
    "补充规则（V1.6）：pending_fields默认为空——仅当套餐固定费（月租费）未提取到，或流量/语音/短信三类资源一个都未提取到时，才将缺失字段填\"待补充\"并计入pending_fields（禁止推理，禁止从相似产品照搬；任一类资源已提取到则其余资源字段不算缺失）；产品编码不做补全（由智能配置环节落地后生成）；其余缺失字段待相似产品返回后按最高相似度产品补全，来源标记\"AI补全\"；来源只允许\"原始需求\"或\"AI补全\"两种。\n"
    "输出要求（两个出参逐一约定，每个出参只输出自己的内容，严禁把其他出参内容并入）：\n"
    "1. elements_json：仅输出结构化要素JSON对象本身（以{开头、}结尾），包含 fields 数组（每项含 field/category/value/source，category取A基础信息/B资源配置/C营销资源/D销售规则）与 pending_fields 数组，不得附带键名前缀或说明；\n"
    "2. need_summary：仅输出需求摘要文本本身（≤5000字符，供相似度分析调用使用）；\n"
    "3. 严格禁止输出形如\"elements_json: {...} need_summary: ...\"的拼接包；除上述两个出参各自内容外不输出任何多余文字。",
    [inp("requirement_text", "引用开始节点 requirement_text", ref_block=nid(1), ref_rel="requirement_text")],
    [out("elements_json", "业务要素结构化JSON"), out("need_summary", "需求摘要（>5000字符时供相似度分析用）")]))
s1.append(plugin_node(3, "相似产品查询", "query_similar_offer",
    "工具1：以《产品信息.txt》全部18个销售品为相似产品库查询相似销售品",
    BASE_URL + "/api/v1/appstore/similar/offer/query",
    [inp("businessDesc", "业务需求描述（引用节点2需求摘要，≤5000字符）", ref_block=nid(2), ref_rel="need_summary")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("similarOfferList", "相似产品列表", "array")]))
s1.append(llm_node(4, "字段映射与补全",
    "你是产销品加载执行方案生成助手。基于节点2要素JSON（elements_json={elements_json}）+ 节点3相似产品列表（similarOfferList={similarOfferList}，含《产品信息.txt》18个销售品规则），生成《产销品加载执行方案》。\n"
    "\n"
    "【职责边界（V2.1 本体推理引擎架构，严格执行）】\n"
    "本节点只负责取值链的前两级：①需求原文有值→直接采用（source=原始需求，含同义改写如\"每月30G\"→流量资源）；②需求无值→取最高相似度产品对应字段值（source=AI补全）。字段形态校验与默认值补全由下游「字段本体推理引擎」节点（工具14 action=reason）自动执行，本节点无需担心枚举/格式违规，相似产品亦缺失时直接留空字符串。\n"
    "\n"
    "【特殊字段口径（V2.2）】\n"
    "- 9.套餐固定费：需求未提取到时值填\"待补充\"（禁止从相似产品照搬）；\n"
    "- 6.流量资源/7.语音资源/8.短信资源：三类资源全部未提取到时，未提取到的字段值填\"待补充\"（任一类已提取到则其余资源字段按相似产品补全）；\n"
    "- 3.产品编码：禁止AI补全——由智能配置环节落地后生成，需求未提供时值填\"由智能配置生成\"、source标\"AI补全\"；\n"
    "- 销售品状态：新需求一律填\"待上线\"（禁止取相似产品的\"在售\"）。\n"
    "- 待补充项判定（pending_fields）由下游本体推理引擎统一执行，本节点不再自行输出 pending_fields，只需把待补充字段value填\"待补充\"。\n"
    "\n"
    "【逐字段生成，必须覆盖四类全部字段（共18个，一个都不能少）】\n"
    "A.基础信息：1.产品名称 2.产品属性（基础/可选/增值） 3.产品编码 4.生效日期 5.退订规则\n"
    "B.资源配置：6.流量资源 7.语音资源 8.短信资源\n"
    "C.营销资源：9.套餐固定费 10.收费方式（按月/按量/一次性） 11.优惠条件 12.优惠期\n"
    "D.销售规则：13.渠道类型 14.适用地区 15.订购限制 16.副卡规则 17.计费周期 18.销售品状态\n"
    "\n"
    "【来源标注（仅两种取值）】\n"
    "source只允许\"原始需求\"或\"AI补全\"：需求原文可逐字找到（含同义改写）标\"原始需求\"，其余（含相似产品取值、产品编码特殊值）标\"AI补全\"；禁止\"待补充\"作为来源（待补充只出现在value中）。\n"
    "\n"
    "【输出要求（单一出参 plan_output）】\n"
    "按以下格式输出，第一行原样输出标签 plan_output:，随后紧跟一个JSON对象（以{开头、}结尾），除该标签行外不得输出任何其他文字、代码块或说明：\n"
    "plan_output: {\"plan_json\":..., \"plan_md\":..., \"pending_fields\":...}\n"
    "该JSON对象固定包含以下3个键：\n"
    "1. plan_json：执行方案JSON对象，含 req_id/fields/similar_offers/pending_fields 四个键；fields数组必须包含上述18个字段，每项形如{\"field\":\"字段名称\",\"value\":\"字段值\",\"source\":\"原始需求或AI补全\"}，相似产品亦缺失的字段value填空字符串\"\"（由下游本体推理引擎补全）；待补充字段value填\"待补充\"；req_id 键留空字符串（由后续代码节点统一生成，禁止自行生成）；pending_fields 键固定输出空数组[]（实际待补充判定由下游本体推理引擎执行）；\n"
    "2. plan_md：执行方案Markdown表格字符串，以|字段分类|开头，固定4列：字段分类/字段名称/字段值/来源，共18行数据行（与fields一一对应），同分类连续行按规范合并单元格（首行填分类，后续行留空）；\n"
    "3. pending_fields：固定输出空数组[]（待补充判定由下游本体推理引擎统一执行，本节点不做判定）；\n"
    "注意：Markdown表格内的换行使用\\n转义，确保整个输出是合法JSON。",
    [inp("elements_json", "引用节点2要素JSON", ref_block=nid(2), ref_rel="elements_json"),
     inp("similarOfferList", "引用节点3相似产品列表", ref_block=nid(3), ref_rel="similarOfferList")],
    [out("plan_output", "执行方案总输出JSON字符串，含 plan_json/plan_md/pending_fields 三个键")]))
# 31 字段本体推理（V2.1 闭环，工具14 action=reason 一体推理）：LLM节点4补全结果 →
# 逐字段执行 本体校验+非法值修正回写（月付/包月→按月等枚举归一）+缺失字段默认值补全+兜底口径置待补充，
# 返回推理后 fields_json——004a 从该结果闭环取值组装 plan_json，引擎兜底真正生效（替代 V2.0 LLM 提示词自觉遵守）
s1.append(plugin_node(31, "字段本体推理", "field_ontology_reason",
    "工具14：字段本体推理引擎（闭环）——对节点4补全结果一体推理：枚举/格式校验+非法值修正回写+缺失字段按本体默认值补全+兜底口径置待补充，返回推理后fields_json供下游组装方案",
    BASE_URL + "/api/v1/appstore/ontology/fields",
    [inp("action", "推理动作=reason（一体推理：校验+修正+补全）", content="reason"),
     inp("fields_json", "字段数组JSON（引用节点4总输出，后端从中解析fields数组）", ref_block=nid(4), ref_rel="plan_output")],
    [("code", "0成功/5101非法action", "string"), ("msg", "状态描述", "string"),
     ("fixed", "修正/补全明细（field/value/action/reason）", "array"),
     ("violations", "无法自动修正的违规明细（field/value/reason期望规则）", "array"),
     ("fields_json", "推理后的完整字段数组JSON（004a从该结果取值）", "string")]))

# 004a 方案输出拆分：plan_output → 拆分；fields 以节点31推理后结果为准（引擎兜底闭环）；
# pending_fields 同样以推理后字段数组为准（value=待补充 反查，V2.2 单一事实源）；
# req_id 由代码节点系统生成（PLAN+当前时刻+3位随机数，每次分析重新生成，保证唯一）
s1.append(code_node(41, "方案输出拆分", CODE_004A,
    [inp("plan_output", "引用节点4总输出", ref_block=nid(4), ref_rel="plan_output"),
     inp("reasoned_fields", "引用节点31推理后字段数组（含fields_json出参）", ref_block=nid(31), ref_rel="fields_json")],
    [code_out("plan_json", 41), code_out("plan_md", 41),
     code_out("pending_fields", 41), code_out("req_id", 41)],
    pos=(1455, 300)))
s1.append(selector_node2(5, "待补充项判断",
    [dep_node(41, "方案输出拆分", ["pending_fields"]), dep_node(31, "字段本体推理", ["violations", "fields_json"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义：pending_fields 不为空（长度大于0）→ 有待补充项 → 保存执行方案 → 确认结束；
    #       port=0 兜底分支（pending_fields 为空）→ 直接结束提示，不保存（禁止进入智能配置）
    # 本体推理 violations 输出至结束节点提示（无法自动修正项交用户判断）
    [(-1, [cond_item(cond_ref(41, "pending_fields", "方案输出拆分"), 10, cond_str(""))])]))
s1.append(plugin_node(6, "保存执行方案", "save_node_result",
    "节点结果存储（复用）：req_id=代码节点生成的方案批次号 req_id（取节点41拆分出参，系统时钟生成），node_name=requirement（执行方案环节），result_json=plan_json；同键覆盖",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "方案批次标识（=节点41拆分出参 req_id，PLAN+yyyyMMddHHmmss+3位随机数）", ref_block=nid(41), ref_rel="req_id"),
     inp("node_name", "环节名=requirement（执行方案）", content="requirement"),
     inp("result_json", "本环节结果JSON=plan_json", ref_block=nid(41), ref_rel="plan_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")]))
s1.append(end_node(7, "结束(有待补充项)",
    [inp("req_id", "执行方案存储key", ref_block=nid(41), ref_rel="req_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(41), ref_rel="plan_md"),
     inp("pending_fields", "待补充字段", ref_block=nid(41), ref_rel="pending_fields"),
     inp("violations", "本体推理无法修正项", ref_block=nid(31), ref_rel="violations")],
    "《产销品加载执行方案》已生成（req_id：{req_id}）\n\n{plan_md}\n\n【待补充字段】{pending_fields}\n以上字段为套餐固定费（月租费）或全部资源信息（流量/语音/短信均未提供）需求中未提取到，需由您补充后才能执行：\n- 请直接补充字段值，将更新执行方案并再次确认；\n- 如需调整其他字段：请直接说明修改意见（其余字段已按相似产品与本体推理引擎补全）。\n【本体校验待处理】{violations}"))
s1.append(end_node(8, "结束(无待补充项)",
    [inp("req_id", "执行方案存储key", ref_block=nid(41), ref_rel="req_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(41), ref_rel="plan_md"),
     inp("violations", "本体推理无法修正项", ref_block=nid(31), ref_rel="violations")],
    "《产销品加载执行方案》已生成并保存（req_id：{req_id}）\n\n{plan_md}\n\n【无待补充字段】缺失字段已按相似产品与字段本体推理引擎补全（非法值已按本体规则修正），来源已逐字段标注。\n【本体校验待处理】{violations}\n请核对以上执行方案：\n- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节（每环节执行后打印结果，仅异常时中断）；\n- 如需调整：请直接说明修改意见。"))
e1 = [edge(1,2), edge(2,3), edge(3,4), edge(4,31), edge(31,41), edge(41,5),
      edge(5,6,0), edge(5,7,-1), edge(6,8)]
files["wf_sub_01_需求分析.json"] = workflow(
    "产销品-需求分析", "子工作流1：需求分析（执行方案生成）。需求理解→相似产品查询（自研模拟，18销售品种子）→字段映射与AI补全（LLM只做原始需求+相似产品两级取值，缺失留空，待补充字段value填\"待补充\"）→字段本体推理（V2.1工具14 action=reason 一体推理：校验+非法值修正回写+缺失字段默认值补全+兜底口径置待补充，引擎兜底闭环）→方案输出拆分（fields与pending_fields均以推理后结果为准，从字段数组反查 value=待补充 判定待补充项，V2.2 单一事实源）→待补充项判断（无待补充→保存执行方案→确认结束；有待补充→补充提示结束）。", "wf_sub_01", s1, e1)

# ============================================================
# wf_sub_02 智能配置（配置落地）
# ============================================================
s2 = []
s2.append(start_node(101, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True)]))
s2.append(plugin_node(102, "读取执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=requirement 取回执行方案记录数组（list[0].result_json 为执行方案原文，内含 req_id/fields/...）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为执行方案原文）", "string")],
    method="get"))
# 106 提取执行方案原文（V2.2 新增代码节点）：query_node_result 出参 list 是记录数组，
# save_product_config 需要的是 list[0].result_json（执行方案对象原文）——代码节点提取，杜绝数组整体透传
s2.append(code_node(106, "提取执行方案原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点2查询出参 list（记录数组JSON）", ref_block=nid(102), ref_rel="list")],
    [code_out("record_json", 106)],
    pos=(530, 300)))
s2.append(plugin_node(103, "配置落地", "save_product_config",
    "工具7：执行方案JSON原文透传落地（节点106已从查询记录中提取 result_json 原文）；req_id 与 plan_json 均引用自查链路结果，方案key由后端从 plan_json 的 req_id 键提取；确认与否由外层智能体识别判断（V2.2 门禁移除），本环节不再校验 confirmed 标记",
    BASE_URL + "/api/v1/appstore/product/config/save",
    [inp("req_id", "执行批次号（=开始节点 req_id，与执行方案存储键同一）", ref_block=nid(101), ref_rel="req_id"),
     inp("plan_json", "执行方案JSON原文（节点106提取的 result_json）", ref_block=nid(106), ref_rel="record_json"),
     inp("confirmed", "用户确认标志true（V2.2起后端不校验，仅记录）", content="true"),
     inp("operator", "操作人（默认system）", content="system")],
    [("product_id", "CRM产品ID", "string"), ("offer_id", "销售品ID", "string"),
     ("save_result", "四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL", "string")]))
s2.append(plugin_node(105, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=config（智能配置），result_json=环节落地结果；主流程删除后存储下沉子工作流，供 wf_sub_06 审批门禁四环节自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=config（智能配置）", content="config"),
     inp("result_json", "环节结果JSON", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(900, 135)))
s2.append(end_node(104, "结束(配置落地完成)",
    [inp("product_id", "CRM产品ID", ref_block=nid(103), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(103), ref_rel="offer_id"),
     inp("save_result", "四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "落地状态", ref_block=nid(103), ref_rel="status")],
    "智能配置完成：product_id={product_id}，offer_id={offer_id}\n四类字段写入结果：{save_result}\n状态：{status}"))
files["wf_sub_02_智能配置.json"] = workflow(
    "产销品-智能配置", "子工作流2：智能配置（配置落地）。单入参 req_id 自查链路：节点结果查询按 req_id+requirement 读取执行方案记录→代码节点提取 list[0].result_json 原文（V2.2）→save_product_config 透传落地（req_id 与存储键同一，方案key由后端从 plan_json 提取）；确认与否由外层智能体识别判断（V2.2 门禁移除）；结束前存储 node_name=config（req_id 同入参，主流程删除后环节存储下沉子工作流）。", "wf_sub_02", s2,
    [edge(101,102), edge(102,106), edge(106,103), edge(103,105), edge(105,104)])

# ============================================================
# wf_sub_03 规格稽核（实时）
# ============================================================
s3 = []
s3.append(start_node(201, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
]))
# 206/207 自查链路（V2.2 修复断链）：上游 offer_id/config_json 不再从开始节点入参引用
# （wf_sub_03 单入参 req_id），改为按 req_id+node_name=config 查询 config 环节结果，
# 代码节点提取 result_json 原文（落地配置JSON，内含 offer_id）供稽核使用
s3.append(plugin_node(206, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地结果原文，内含 product_id/offer_id/...）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 135)))
s3.append(code_node(207, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点206查询出参 list（记录数组JSON）", ref_block=nid(206), ref_rel="list")],
    [code_out("record_json", 207)],
    pos=(390, 135)))
s3.append(plugin_node(202, "实时稽核", "realtime_spec_audit",
    "工具2：自研模拟实时稽核，同步返回（无文件上传/无轮询）；offer_id/config_json 取自 config 环节结果（节点207提取原文）",
    BASE_URL + "/api/v1/appstore/audit/realtime",
    [inp("offer_id", "销售品ID（落地结果offer_id，智能体调度时若上一步已返回可直接传入）", ref_block=nid(201), ref_rel="offer_id"),
     inp("config_json", "落地配置JSON（节点207提取的环节结果原文）", ref_block=nid(207), ref_rel="record_json"),
     inp("audit_scene", "稽核场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("error_list", "问题明细", "array"),
     ("audit_summary", "稽核总结", "string"), ("resultCode", "0成功/NET_ERROR/TIMEOUT", "string")]))
s3.append(llm_node(203, "整改建议生成",
    "将稽核问题明细整理为可执行的整改建议清单（error_list={error_list}，audit_summary={audit_summary}），按严重级别排序；pass=1 时输出\"稽核通过\"。不新增稽核结论。\n"
    "输出要求：仅输出整改建议清单内容（对应出参 audit_suggest），不输出其他多余文字。",
    [inp("error_list", "引用节点2问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_summary", "引用节点2稽核总结", ref_block=nid(202), ref_rel="audit_summary")],
    [out("audit_suggest", "整改建议清单")]))
s3.append(plugin_node(205, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=spec（规格稽核），result_json=稽核总结；主流程删除后存储下沉子工作流，供 wf_sub_06 审批门禁四环节自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=spec（规格稽核）", content="spec"),
     inp("result_json", "环节结果JSON=稽核总结", ref_block=nid(202), ref_rel="audit_summary"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(900, 135)))
s3.append(end_node(204, "结束(稽核完成)",
    [inp("pass", "稽核结论", ref_block=nid(202), ref_rel="pass"),
     inp("error_list", "问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_suggest", "整改建议", ref_block=nid(203), ref_rel="audit_suggest")],
    "配置规格稽核完成：pass={pass}\n{audit_suggest}"))
files["wf_sub_03_规格稽核.json"] = workflow(
    "产销品-规格稽核", "子工作流3：规格稽核（实时）。单入参 req_id 自查链路：query_node_result 按req_id+config读取智能配置环节结果→代码节点提取 result_json 原文（V2.2 修复断链）→realtime_spec_audit同步返回→整改建议生成（温度0.2）；结束前存储 node_name=spec（req_id=入参，主流程删除后环节存储下沉子工作流）。", "wf_sub_03", s3,
    [edge(201,206), edge(206,207), edge(207,202), edge(202,203), edge(203,205), edge(205,204)])

# ============================================================
# wf_sub_04 自动测试（含受理验证）
# ============================================================
s4 = []
s4.append(start_node(301, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
]))
# 309/310 自查链路（V2.2 修复断链）：上游 offerId 不再从开始节点入参引用
# （wf_sub_04 单入参 req_id），改为按 req_id+node_name=config 查询 config 环节结果，
# 代码节点提取 result_json 原文供发起测试取 offerId
s4.append(plugin_node(309, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地结果原文，内含 offer_id）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get"))
s4.append(code_node(310, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点309查询出参 list（记录数组JSON）", ref_block=nid(309), ref_rel="list")],
    [code_out("record_json", 310)],
    pos=(240, 135)))
s4.append(plugin_node(302, "发起测试", "offer_test",
    "工具3：自研模拟测试发起，返回模拟测试流水globalId；offerId 取自 config 环节结果（智能体调度时若上一步已返回可直接传入，引用开始节点 offer_id 兜底）",
    BASE_URL + "/api/v1/appstore/test/offer/start",
    [inp("offerId", "销售品ID（引用开始节点 offer_id，智能体从上一步落地结果透传）", ref_block=nid(301), ref_rel="offer_id")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("globalId", "测试流水号", "string")]))
s4.append(plugin_node(303, "查询测试场景", "get_test_scenes",
    "工具4：查询受理验证覆盖范围（套餐新装/副卡加装/套餐退订）",
    BASE_URL + "/api/v1/appstore/test/offer/scenes",
    [inp("globalId", "测试流水号（节点2出参）", ref_block=nid(302), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"), ("testScenes", "场景列表", "array")]))
s4.append(loop_node(304, "轮询测试进度",
    "工具5循环轮询：间隔5s，超时30分钟（360次），连续5次查询失败终止转人工",
    [inp("globalId", "测试流水号", ref_block=nid(302), ref_rel="globalId")],
    [out("done", "是否全部完成"), out("failed", "是否失败"), out("failIndex", "失败场景下标")]))
s4.append(plugin_node(305, "查询测试结果", "get_test_result",
    "工具6：done=true后调用一次；presetValue取自《产品信息.txt》该销售品规则值；返回受理凭证orderId/offerInstId",
    BASE_URL + "/api/v1/appstore/test/offer/result",
    [inp("globalId", "测试流水号", ref_block=nid(302), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("testRequestId", "测试请求ID", "string"), ("testRequestName", "测试请求名称", "string"),
     ("offerName", "被测销售品名称", "string"), ("orderId", "受理订单号", "string"),
     ("offerInstId", "销售品实例ID", "string"), ("testScenes", "逐场景结果含测点明细", "array")]))
s4.append(llm_node(306, "测试报告生成",
    "你是产销品自动测试报告生成助手。基于逐场景测试结果（testScenes={testScenes}）生成《销售品自动测试报告》，必须包含：1.测试概要（offerName/globalId/场景与测点统计）；2.受理验证结论（强制章节：orderId={orderId}、offerInstId={offerInstId}，为空则写明\"未获取到受理凭证，需人工核实\"；逐受理场景 S_O_TC/S_ADD_CARD/S_U_TC 给出通过/失败结论）；3.逐场景明细（仅展开resultCode=1不一致测点）；4.AI总结与建议（引用objTestSceneRel）；5.总体结论。\n"
    "输出要求（两个出参逐一约定）：\n"
    "1. test_report：完整测试报告文本（含上述5个章节，受理验证结论为强制章节）；\n"
    "2. test_passed：总体结论，取值\"通过\"或\"失败\"（仅输出这两个词之一）；\n"
    "3. 只基于输入数据生成，不得虚构测点或结论。",
    [inp("testScenes", "引用节点5逐场景结果", ref_block=nid(305), ref_rel="testScenes"),
     inp("orderId", "受理订单号", ref_block=nid(305), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID", ref_block=nid(305), ref_rel="offerInstId")],
    [out("test_report", "测试报告（含受理验证结论）"), out("test_passed", "通过/失败")]))
s4.append(plugin_node(308, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=test（自动测试），result_json=测试报告；主流程删除后存储下沉子工作流，供 wf_sub_06 审批门禁四环节自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=test（自动测试）", content="test"),
     inp("result_json", "环节结果JSON=测试报告", ref_block=nid(306), ref_rel="test_report"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(1300, 135)))
s4.append(end_node(307, "结束(测试完成)",
    [inp("test_report", "测试报告", ref_block=nid(306), ref_rel="test_report"),
     inp("test_passed", "总体结论", ref_block=nid(306), ref_rel="test_passed"),
     inp("globalId", "测试流水号", ref_block=nid(302), ref_rel="globalId")],
    "销售品自动测试完成（含受理验证）：\n{test_report}"))
files["wf_sub_04_自动测试.json"] = workflow(
    "产销品-自动测试", "子工作流4：自动测试（含受理验证）。单入参 req_id 自查链路：query_node_result 按req_id+config读取环节结果→代码节点提取原文（V2.2 修复断链）→offer_test发起→get_test_scenes→循环get_test_progress（5s/30min）→get_test_result→测试报告生成（强制含受理验证结论orderId/offerInstId）；结束前存储 node_name=test（req_id=入参，主流程删除后环节存储下沉子工作流）。", "wf_sub_04", s4,
    [edge(301,309), edge(309,310), edge(310,302), edge(302,303), edge(303,304), edge(304,305), edge(305,306), edge(306,308), edge(308,307)])

# ============================================================
# wf_sub_05 资费校准
# ============================================================
s5 = []
s5.append(start_node(401, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
]))
# 406/407 自查链路（V2.2 修复断链）：上游 config_json 不再从开始节点入参引用
# （wf_sub_05 单入参 req_id），改为按 req_id+node_name=config 查询 config 环节结果，
# 代码节点提取 result_json 原文（落地配置JSON）供计费校验使用
s5.append(plugin_node(406, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地结果原文）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get"))
s5.append(code_node(407, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点406查询出参 list（记录数组JSON）", ref_block=nid(406), ref_rel="list")],
    [code_out("record_json", 407)],
    pos=(240, 135)))
s5.append(plugin_node(402, "计费校验", "check_billing_rule",
    "工具8：自研模拟计费规则校验（内置叠加/互斥/负资费规则，适配18销售品资费结构）；config_json 取自 config 环节结果（节点407提取原文）",
    BASE_URL + "/api/v1/appstore/billing/rules/verify",
    [inp("config_json", "落地配置JSON（节点407提取的环节结果原文）", ref_block=nid(407), ref_rel="record_json"),
     inp("check_scene", "校验场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("risk_list", "风险清单", "array")]))
s5.append(llm_node(403, "风险解读",
    "将资费风险清单（risk_list={risk_list}）翻译为业务语言，说明每条风险的影响与建议；risk_list 为空时输出\"资费校准通过，未发现叠加/互斥冲突\"。可引用资费规则库知识作为解释依据，但不得新增风险结论。\n"
    "输出要求：仅输出风险解读内容（对应出参 risk_summary），不输出其他多余文字。",
    [inp("risk_list", "引用节点2风险清单", ref_block=nid(402), ref_rel="risk_list")],
    [out("risk_summary", "风险解读")]))
s5.append(plugin_node(405, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=fee（资费校准），result_json=风险解读；主流程删除后存储下沉子工作流，供 wf_sub_06 审批门禁四环节自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("node_name", "环节名=fee（资费校准）", content="fee"),
     inp("result_json", "环节结果JSON=风险解读", ref_block=nid(403), ref_rel="risk_summary"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(900, 135)))
s5.append(end_node(404, "结束(资费校准完成)",
    [inp("pass", "校验结论", ref_block=nid(402), ref_rel="pass"),
     inp("risk_list", "风险清单", ref_block=nid(402), ref_rel="risk_list"),
     inp("risk_summary", "风险解读", ref_block=nid(403), ref_rel="risk_summary")],
    "资费校准完成：pass={pass}\n{risk_summary}"))
files["wf_sub_05_资费校准.json"] = workflow(
    "产销品-资费校准", "子工作流5：资费校准。单入参 req_id 自查链路：query_node_result 按req_id+config读取环节结果→代码节点提取原文（V2.2 修复断链）→check_billing_rule（自研模拟，check_scene=all）→风险解读（温度0.2，引用资费规则库知识）；结束前存储 node_name=fee（req_id=入参，主流程删除后环节存储下沉子工作流）。", "wf_sub_05", s5,
    [edge(401,406), edge(406,407), edge(407,402), edge(402,403), edge(403,405), edge(405,404)])

# ============================================================
# wf_sub_06 上线审批
# ============================================================
s6 = []
s6.append(start_node(501, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，四环节结果门禁校验依据，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
]))
s6.append(plugin_node(502, "审批推送", "submit_release_approval",
    "工具9：自研模拟审批推送；插件层硬门禁：approve_confirmed==true 且存储中存在该 req_id 的四环节结果（config/spec/fee/test）；幂等：同product_id返回原approval_id",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("product_id", "CRM产品ID（后端从config环节结果回读）", content=""),
     inp("report_url", "上线报告（LLM生成后传入，或空由后端从report环节结果回读）", content=""),
     inp("req_id", "执行批次号（四环节结果门禁校验依据，=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("approve_confirmed", "审批发起确认标志true", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string")]))
s6.append(end_node(503, "结束(审批已推送)",
    [inp("approval_id", "审批单号", ref_block=nid(502), ref_rel="approval_id"),
     inp("status", "提交状态", ref_block=nid(502), ref_rel="status")],
    "上线审批已推送：approval_id={approval_id}，status={status}\n可随时发送\"查询审批进度\"消息查询审批状态。"))
files["wf_sub_06_上线审批.json"] = workflow(
    "产销品-上线审批", "子工作流6：上线审批。单入参 req_id：submit_release_approval（自研模拟，插件层硬门禁：approve_confirmed=true+req_id四环节结果config/spec/fee/test齐全，缺失返回NOT_CONFIRMED；幂等）。", "wf_sub_06", s6,
    [edge(501,502), edge(502,503)])

# ============================================================
# wf_sub_07 监控运维
# ============================================================
s7 = []
s7.append(start_node(601, [
    inp("product_id", "销售品ID", required=True),
    inp("date_range", "日期范围，默认最近1天", required=False),
]))
s7.append(plugin_node(602, "监控查询", "query_product_monitor",
    "工具10：自研模拟监控查询（订单量/异常量/计费差错率/告警列表）",
    BASE_URL + "/api/v1/appstore/product/monitor",
    [inp("product_id", "销售品ID", ref_block=nid(601), ref_rel="product_id"),
     inp("date_range", "日期范围", ref_block=nid(601), ref_rel="date_range"),
     inp("metric", "指标默认all", content="all")],
    [("order_count", "订单量", "string"), ("error_count", "异常量", "string"),
     ("fee_error_rate", "计费差错率", "string"), ("alarm_list", "告警列表", "array")],
    method="get"))
s7.append(selector_node2(603, "异常判定",
    [dep_node(602, "监控查询", ["error_count", "fee_error_rate"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义：error_count 不等于"0"（即存在异常）→ 走异常告警（port=-1 命中条件）；
    #       port=0 兜底分支（error_count 等于"0"即正常）→ 走正常结束
    [(-1, [cond_item(cond_ref(602, "error_count", "监控查询"), 2, cond_str("0"))])]))
s7.append(llm_node(604, "告警文案生成",
    "基于监控异常数据生成告警文案（含产品、异常摘要、建议）。输入：product_id={product_id}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "输出要求：仅输出告警文案内容（对应出参 alert_content，需包含产品名称、异常摘要、处置建议三部分），不输出其他多余文字。",
    [inp("product_id", "销售品ID", ref_block=nid(601), ref_rel="product_id"),
     inp("order_count", "订单量", ref_block=nid(602), ref_rel="order_count"),
     inp("error_count", "异常量", ref_block=nid(602), ref_rel="error_count"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(602), ref_rel="fee_error_rate"),
     inp("alarm_list", "告警列表", ref_block=nid(602), ref_rel="alarm_list")],
    [out("alert_content", "告警文案")]))
s7.append(plugin_node(605, "异常告警", "send_alert",
    "工具11：自研模拟告警推送（生成alert_id，记录写入模拟库供监控回显）",
    BASE_URL + "/api/v1/appstore/alert/send",
    [inp("product_id", "销售品ID", ref_block=nid(601), ref_rel="product_id"),
     inp("alarm_level", "告警级别", content="high"),
     inp("content", "告警文案（节点4输出）", ref_block=nid(604), ref_rel="alert_content")],
    [("alert_id", "告警单号", "string"), ("status", "推送状态", "string")]))
s7.append(end_node(606, "结束(异常-已告警)",
    [inp("alert_id", "告警单号", ref_block=nid(605), ref_rel="alert_id"),
     inp("alert_content", "告警文案", ref_block=nid(604), ref_rel="alert_content")],
    "监控发现异常，已推送告警：alert_id={alert_id}\n{alert_content}"),)
s7.append(end_node(607, "结束(正常)",
    [inp("order_count", "订单量", ref_block=nid(602), ref_rel="order_count"),
     inp("error_count", "异常量", ref_block=nid(602), ref_rel="error_count")],
    "监控正常：订单量={order_count}，异常量={error_count}，无需告警。"))
files["wf_sub_07_监控运维.json"] = workflow(
    "产销品-监控运维", "子工作流7：监控运维。query_product_monitor（自研模拟）→异常判定（error_count>0或fee_error_rate>0.1）→send_alert告警/正常摘要。支持每日定时与对话触发。", "wf_sub_07", s7,
    [edge(601,602), edge(602,603), edge(603,607,0), edge(603,604,-1), edge(604,605), edge(605,606)])

# ============================================================
# wf_sub_08 审批进度查询
# ============================================================
s8 = []
s8.append(start_node(701, [
    inp("approval_id", "审批单号（与product_id至少一个非空）", required=False),
    inp("product_id", "产品ID（与approval_id至少一个非空）", required=False),
]))
s8.append(plugin_node(702, "审批状态查询", "query_approval_status",
    "工具13：自研模拟审批进度查询（从模拟审批状态库查询）",
    BASE_URL + "/api/v1/appstore/approval/status",
    [inp("approval_id", "审批单号", ref_block=nid(701), ref_rel="approval_id"),
     inp("product_id", "产品ID", ref_block=nid(701), ref_rel="product_id")],
    [("approval_id", "审批单号", "string"), ("status", "审批中/通过/驳回", "string"),
     ("current_node", "当前审批环节", "string"), ("approver", "当前审批人", "string"),
     ("opinion", "审批意见", "string"), ("submit_time", "提交时间", "string"),
     ("update_time", "更新时间", "string")],
    method="get"))
s8.append(llm_node(703, "状态摘要归纳",
    "按'审批单号 {approval_id}｜状态：{status}｜当前环节：{current_node}（审批人 {approver}）｜最近意见：{opinion}｜更新时间：{update_time}'格式输出；status=驳回 时附驳回原因并提示可修改执行方案后重新发起。查无审批单时输出\"未找到该销售品的审批单，请确认是否已发起审批\"。\n"
    "输出要求：仅输出审批状态摘要内容（对应出参 approval_summary），不输出其他多余文字。",
    [inp("approval_id", "审批单号", ref_block=nid(702), ref_rel="approval_id"),
     inp("status", "审批状态", ref_block=nid(702), ref_rel="status"),
     inp("current_node", "当前环节", ref_block=nid(702), ref_rel="current_node"),
     inp("approver", "审批人", ref_block=nid(702), ref_rel="approver"),
     inp("opinion", "审批意见", ref_block=nid(702), ref_rel="opinion"),
     inp("update_time", "更新时间", ref_block=nid(702), ref_rel="update_time")],
    [out("approval_summary", "审批状态摘要")]))
s8.append(end_node(704, "结束(查询完成)",
    [inp("approval_summary", "审批状态摘要", ref_block=nid(703), ref_rel="approval_summary")],
    "{approval_summary}"))
files["wf_sub_08_审批进度查询.json"] = workflow(
    "产销品-审批进度查询", "子工作流8：审批进度查询（V1.5新增）。query_approval_status（自研模拟）→状态摘要归纳（温度0.2）。轻量查询子工作流，智能体可直调工具13替代。", "wf_sub_08", s8,
    [edge(701,702), edge(702,703), edge(703,704)])


for fn, data in files.items():
    data = apply_layout(data)
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", fn)

print("total:", len(files))

