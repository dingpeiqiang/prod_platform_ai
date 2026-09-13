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
        ("value", "补全值（价格类字段为\"待补充\"）"),
        ("defaulted", "1=本体默认值补全 / 0=未补全"),
        ("reason", "补全依据（本体规则）"),
    ],
    "field_ontology_reason|fields": [
        ("field", "字段名"),
        ("category", "字段分类（A基础信息/B资源配置/C营销资源/D销售规则）"),
        ("enums", "枚举值（顿号分隔，无枚举为空）"),
        ("rule", "格式/口径规则"),
        ("default_value", "本体默认值"),
        ("fallback", "1=价格类不可推理字段 / 0=普通字段"),
    ],
    "query_similar_offer|similarOffer": [
        ("similarOfferId", "相似销售品ID（如 900102308）"),
        ("similarOfferName", "相似销售品名称"),
        ("similarityScore", "相似度评分（0~1）"),
        ("similarityDesc", "相似原因描述（命中字段/资费结构说明）"),
        ("offerInfo", "完整产品配置信息（与需求要素同构：similarOfferId/similarOfferName/series/sub_type + fields 四类18字段数组 field/category/value）"),
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


CODE_004A = (
    "import json, random\n"
    "from datetime import datetime\n"
    "from typing import Any, Dict\n"
    "async def main(args):\n"
    "    # V2.3：单一入参=节点4整合结果 fields_output——兼容 offerInfo 完整结构（含 similarOfferId/similarOfferName/series/sub_type + fields）与纯 fields 数组\n"
    "    raw = args.params.get('fields_output') or ''\n"
    "    if not isinstance(raw, str):\n"
    "        raw = json.dumps(raw, ensure_ascii=False)\n"
    "    # 剥离形如 \"fields_output: {...}\" 的标签行前缀\n"
    "    if ':' in raw and not raw.lstrip().startswith('{') and not raw.lstrip().startswith('['):\n"
    "        raw = raw.split(':', 1)[1].strip()\n"
    "    rf = None\n"
    "    offer_meta = {}\n"
    "    if raw:\n"
    "        try:\n"
    "            robj = json.loads(raw) if isinstance(raw, str) else raw\n"
    "            if isinstance(robj, dict):\n"
    "                # offerInfo 同构完整结构：先取溯源键，再取 fields\n"
    "                for k in ('similarOfferId', 'similarOfferName', 'series', 'sub_type'):\n"
    "                    if robj.get(k) is not None:\n"
    "                        offer_meta[k] = robj.get(k)\n"
    "                rf = robj.get('fields')\n"
    "                if isinstance(rf, str):\n"
    "                    rf = json.loads(rf)\n"
    "            elif isinstance(robj, list):\n"
    "                rf = robj\n"
    "        except Exception:\n"
    "            rf = None\n"
    "    if not rf:\n"
    "        rf = []\n"
    "    # pending_fields：反查 value=待补充 的字段（需求与相似产品均未提供的项）\n"
    "    miss = [str(f.get('field')) for f in rf if str(f.get('value')) == '待补充']\n"
    "    pf = ','.join(miss)\n"
    "    # plan_md：由整合后字段数组重新生成四列表格（字段分类/字段名称/字段值/来源），与 fields 严格一致\n"
    "    cats = {'A': '基础信息', 'B': '资源配置', 'C': '营销资源', 'D': '销售规则'}\n"
    "    lines = ['| 字段分类 | 字段名称 | 字段值 | 来源 |', '| --- | --- | --- | --- |']\n"
    "    last_cat = ''\n"
    "    for f in rf:\n"
    "        field = str(f.get('field') or '')\n"
    "        value = str(f.get('value') or '')\n"
    "        source = str(f.get('source') or '')\n"
    "        cat = ''\n"
    "        for fkey, cname in cats.items():\n"
    "            if field in CATEGORIES.get(fkey, []):\n"
    "                cat = cname if cname != last_cat else ''\n"
    "                last_cat = cname\n"
    "                break\n"
    "        lines.append('| ' + cat + ' | ' + field + ' | ' + value.replace('|', '\\\\|') + ' | ' + source + ' |')\n"
    "    plan_md = '\\n'.join(lines)\n"
    "    # plan_json：整合结果组装（保留 offerInfo 溯源键；方案载体 req_id/fields/pending_fields/similar_offer）\n"
    "    req_id = 'PLAN' + datetime.now().strftime('%Y%m%d%H%M%S') + '%03d' % random.randint(0, 999)\n"
    "    plan = {'req_id': req_id, 'fields': rf, 'pending_fields': miss}\n"
    "    if offer_meta:\n"
    "        plan['similar_offer'] = offer_meta\n"
    "    plan_json = json.dumps(plan, ensure_ascii=False)\n"
    "    ret: Output = {\n"
    "        \"plan_json\": plan_json,\n"
    "        \"plan_md\": plan_md,\n"
    "        \"pending_fields\": str(pf),\n"
    "        \"req_id\": req_id\n"
    "    }\n"
    "    return ret"
)

# CODE_004A 依赖的分类映射（四类18字段），注入代码字符串
CODE_004A_CATS = (
    "CATEGORIES = {\n"
    "    'A': ['产品名称', '产品属性', '产品编码', '生效日期', '退订规则'],\n"
    "    'B': ['流量资源', '语音资源', '短信资源'],\n"
    "    'C': ['套餐固定费', '收费方式', '优惠条件', '优惠期'],\n"
    "    'D': ['渠道类型', '适用地区', '订购限制', '副卡规则', '计费周期', '销售品状态'],\n"
    "}\n"
)
CODE_004A = CODE_004A_CATS + CODE_004A

# CODE_EXTRACT_RECORD：从 query_node_result 出参 list（记录数组JSON）提取 list[0].result_json 原文。
# V2.2 新增：wf_sub_02/03/04/05 自查链路共用——query_node_result 返回的是记录数组
# （[{req_id,node_name,result_json,status,...}]），下游工具需要的执行方案/环节结果原文
# 存在于 list[0].result_json 字段内，须提取后再透传（禁止把数组整体传给落地/稽核等工具）。
# V2.4 新增：追加 offer_id 出参——从 result_json 原文解析内层 JSON 后取 offer_id，
# 供 wf_sub_03 稽核（节点202）/wf_sub_04 发起测试（节点302）引用，修复开始节点无 offer_id 出参导致的断链。
CODE_EXTRACT_RECORD = (
    "import json\n"
    "from typing import Any, Dict\n"
    "async def main(args):\n"
    "    raw = args.params['query_list']\n"
    "    if not isinstance(raw, str):\n"
    "        raw = json.dumps(raw, ensure_ascii=False)\n"
    "    record_json = ''\n"
    "    offer_id = ''\n"
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
    "    try:\n"
    "        inner = json.loads(record_json)\n"
    "        if isinstance(inner, dict):\n"
    "            offer_id = str(inner.get('offer_id') or inner.get('offerId') or '')\n"
    "    except Exception:\n"
    "        offer_id = ''\n"
    "    ret: Output = {\n"
    "        \"record_json\": record_json,\n"
    "        \"offer_id\": offer_id\n"
    "    }\n"
    "    return ret"
)

# CODE_POLL_PROGRESS：wf_sub_04 节点304 轮询测试进度（V2.4 重写）。
# 设计方案 3.4.4 约束：不用循环节点（平台 loop/循环 不支持），改为 type=6 代码节点
# 内嵌轮询逻辑：asyncio.sleep(5) 间隔，最多 360 次（30 分钟超时）；
# 连续 5 次查询失败终止转人工（fail_reason 出参非空标识异常退出）。
# 轮询方式：HTTP 直连后端工具5接口（代码节点无平台工具调用能力）。
CODE_POLL_PROGRESS = (
    "import json\n"
    "import asyncio\n"
    "import urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "PROGRESS_URL = 'BASE_URL/api/v1/appstore/test/offer/progress'\n"
    "INTERVAL = 5\n"
    "MAX_RETRY = 360\n"
    "MAX_CONSECUTIVE_FAIL = 5\n"
    "\n"
    "async def main(args):\n"
    "    global_id = str(args.params.get('globalId') or '')\n"
    "    if not global_id:\n"
    "        ret: Output = {'done': 'false', 'failed': 'true', 'failIndex': '-1',\n"
    "                       'fail_reason': '缺少测试流水号 globalId，请先发起测试'}\n"
    "        return ret\n"
    "    consecutive_fail = 0\n"
    "    for i in range(MAX_RETRY):\n"
    "        resp = {}\n"
    "        ok = False\n"
    "        try:\n"
    "            body = json.dumps({'globalId': global_id}).encode('utf-8')\n"
    "            req = urllib.request.Request(PROGRESS_URL, data=body,\n"
    "                                         headers={'Content-Type': 'application/json'})\n"
    "            with urllib.request.urlopen(req, timeout=30) as http_resp:\n"
    "                resp = json.loads(http_resp.read().decode('utf-8'))\n"
    "            ok = isinstance(resp, dict)\n"
    "        except Exception:\n"
    "            ok = False\n"
    "        if not ok:\n"
    "            consecutive_fail += 1\n"
    "            if consecutive_fail >= MAX_CONSECUTIVE_FAIL:\n"
    "                ret: Output = {'done': 'false', 'failed': 'true', 'failIndex': '-1',\n"
    "                               'fail_reason': '连续查询失败5次，转人工（globalId=' + global_id + '）'}\n"
    "                return ret\n"
    "            await asyncio.sleep(INTERVAL)\n"
    "            continue\n"
    "        consecutive_fail = 0\n"
    "        if str(resp.get('done', '')).lower() == 'true':\n"
    "            ret: Output = {'done': 'true', 'failed': 'false', 'failIndex': '-1', 'fail_reason': ''}\n"
    "            return ret\n"
    "        if str(resp.get('failed', '')).lower() == 'true':\n"
    "            ret: Output = {'done': 'false', 'failed': 'true',\n"
    "                           'failIndex': str(resp.get('failIndex', '-1')),\n"
    "                           'fail_reason': '测试失败/中止，仍取完整结果供报告定位失败原因'}\n"
    "            return ret\n"
    "        await asyncio.sleep(INTERVAL)\n"
    "    ret: Output = {'done': 'false', 'failed': 'true', 'failIndex': '-1',\n"
    "                   'fail_reason': '测试超时（30分钟），请凭 globalId 人工续查（globalId=' + global_id + '）'}\n"
    "    return ret"
)
CODE_POLL_PROGRESS = CODE_POLL_PROGRESS.replace("BASE_URL", BASE_URL)

# CODE_SUMMARY_APPROVAL：wf_sub_06 节点501s 合成结构化汇总（V2.4 新增，设计方案3.2.6 节点7）。
# 输入=5类自查节点提取的环节结果原文（config/spec/fee/test + requirement），
# 解析各环节 JSON 提取关键字段（product_id/offer_id/pass/audit_summary/pass/risk_list/
# orderId/offerInstId/测试统计），合成一份 JSON 字符串供节点501g 报告生成与节点502 审批推送使用。
CODE_SUMMARY_APPROVAL = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _parse(text):\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    t = text.strip()\n"
    "    if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "        t = t.split(':', 1)[1].strip()\n"
    "    try:\n"
    "        v = json.loads(t)\n"
    "        return v if isinstance(v, dict) else {'raw': t}\n"
    "    except Exception:\n"
    "        return {'raw': t}\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    cfg = _parse(p.get('config_result') or '')\n"
    "    spec = _parse(p.get('spec_result') or '')\n"
    "    fee = _parse(p.get('fee_result') or '')\n"
    "    test = _parse(p.get('test_result') or '')\n"
    "    req = _parse(p.get('requirement_result') or '')\n"
    "    # config 环节：完整落地配置JSON（内含 product_id/offer_id 与 plan_json）\n"
    "    product_id = str(cfg.get('product_id') or '')\n"
    "    offer_id = str(cfg.get('offer_id') or '')\n"
    "    offer_name = str(cfg.get('offer_name') or '')\n"
    "    # spec 环节：存储的是稽核总结文本\n"
    "    audit_summary = str(spec.get('raw') or spec.get('audit_summary') or '')\n"
    "    spec_pass = '1' if ('通过' in audit_summary and '驳回' not in audit_summary) else '0'\n"
    "    # fee 环节：存储的是风险解读文本\n"
    "    risk_summary = str(fee.get('raw') or fee.get('risk_summary') or '')\n"
    "    fee_pass = '0' if ('未通过' in risk_summary or '风险' in risk_summary.replace('未发现', '')) else '1'\n"
    "    # test 环节：存储的是测试报告文本，受理凭证按标记提取\n"
    "    test_report = str(test.get('raw') or test.get('test_report') or '')\n"
    "    order_id = ''\n"
    "    offer_inst_id = ''\n"
    "    import re\n"
    "    m = re.search(r'order[Ii]d[=：:]*\\s*([A-Za-z0-9\\-]+)', test_report)\n"
    "    if m:\n"
    "        order_id = m.group(1)\n"
    "    m = re.search(r'offerInst[Ii]d[=：:]*\\s*([A-Za-z0-9\\-]+)', test_report)\n"
    "    if m:\n"
    "        offer_inst_id = m.group(1)\n"
    "    test_passed = '1' if ('通过' in test_report and '失败' not in test_report.split('总体结论')[-1]) else '0'\n"
    "    summary = {\n"
    "        'product_id': product_id,\n"
    "        'offer_id': offer_id,\n"
    "        'offer_name': offer_name,\n"
    "        'requirement_summary': str(req.get('raw') or '')[:500],\n"
    "        'config': {'product_id': product_id, 'offer_id': offer_id,\n"
    "                   'plan_json': cfg.get('plan_json')},\n"
    "        'spec': {'pass': spec_pass, 'audit_summary': audit_summary},\n"
    "        'fee': {'pass': fee_pass, 'risk_summary': risk_summary},\n"
    "        'test': {'passed': test_passed, 'order_id': order_id,\n"
    "                 'offer_inst_id': offer_inst_id, 'test_report': test_report},\n"
    "        'all_pass': spec_pass == '1' and fee_pass == '1' and test_passed == '1',\n"
    "    }\n"
    "    ret: Output = {\n"
    "        \"summary_json\": json.dumps(summary, ensure_ascii=False),\n"
    "        \"product_id\": product_id,\n"
    "        \"offer_id\": offer_id\n"
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
    "你是产销品加载需求分析助手，只做一件事：从需求原文提取业务要素信息（环节1：需求理解与要素拆解），不做补全、不做完整性判断、不生成执行方案。需求原文：{requirement_text}\n"
    "要素拆解字段口径（四类18字段，与下游环节保持一致）：\n"
    "A.基础信息：产品名称/产品属性（基础/可选/增值）/产品编码/生效日期/退订规则\n"
    "B.资源配置：流量资源/语音资源/短信资源\n"
    "C.营销资源：套餐固定费/收费方式（按月/按量/一次性）/优惠条件/优惠期\n"
    "D.销售规则：渠道类型/适用地区/订购限制/副卡规则/计费周期/销售品状态\n"
    "提取规则：仅当需求原文中可找到（含同义改写）的字段才填值，source标\"原始需求\"；需求原文未提及的字段value一律填空字符串\"\"，source也填\"原始需求\"（后续环节负责补全）；禁止臆造字段值。\n"
    "强制同义映射（提取前逐条比对，禁止漏提取）：\n"
    "- 月费/月租/月租费/套餐费/固定费/资费档位 + 金额（如\"月费199元\"\"199元/月\"）→ 套餐固定费（金额原样填入，如\"199元\"）；\n"
    "- 每月30G/月享XX G/含XX流量/XX G国内流量 → 流量资源；\n"
    "- XX分钟通话/语音/国内通话 → 语音资源；\n"
    "- XX条短信 → 短信资源；\n"
    "- 支持副卡/可办副卡 → 副卡规则=支持；\n"
    "- 营业厅/门店 → 渠道类型含\"营业厅\"；APP/网厅/线上 → 渠道类型含\"APP\"；\n"
    "- X月X日/X日起生效 → 生效日期；\n"
    "- 按月/按月付费/月付 → 收费方式=按月。\n"
    "自检：需求原文中出现\"月费/月租/套餐费+金额\"表述而套餐固定费为空，视为提取失败，必须回填。\n"
    "输出要求（两个出参逐一约定，每个出参只输出自己的内容，严禁把其他出参内容并入）：\n"
    "1. elements_json：仅输出结构化要素JSON对象本身（以{开头、}结尾），包含 fields 数组（18项，每项含 field/category/value/source，category取A基础信息/B资源配置/C营销资源/D销售规则），不得附带键名前缀或说明，不输出 pending_fields；elements_json 输出以}结尾即终止，严禁在其后追加任何需求原文、摘要或其他文字；\n"
    "2. need_summary：仅输出一段纯文本需求摘要（自然语言，整合产品名称/资费/资源/渠道/生效日期等关键要素，≤5000字符）；need_summary 禁止输出 JSON、禁止复制 elements_json 内容、禁止输出 fields 数组，只能是一段连续的中文摘要文本；\n"
    "3. 严格禁止输出形如\"elements_json: {...} need_summary: ...\"的拼接包；除上述两个出参各自内容外不输出任何多余文字。",
    [inp("requirement_text", "引用开始节点 requirement_text", ref_block=nid(1), ref_rel="requirement_text")],
    [out("elements_json", "业务要素结构化JSON（18字段，未提及项value为空）"), out("need_summary", "需求要素摘要（供相似产品匹配）")]))
s1.append(plugin_node(3, "相似产品查询", "query_similar_offer",
    "工具1：以《产品信息.txt》全部18个销售品为相似产品库，返回相似度最高的产品（仅1个，含相似度评分与完整产品配置信息 offerInfo——与需求要素同构的 fields 四类18字段数组）",
    BASE_URL + "/api/v1/appstore/similar/offer/query",
    [inp("businessDesc", "业务需求描述（引用节点2需求要素摘要，≤5000字符）", ref_block=nid(2), ref_rel="need_summary")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("similarOffer", "相似度最高的产品（含相似度评分与完整产品配置信息 offerInfo，未命中时为空对象）", "object")]))
s1.append(llm_node(4, "产品信息整合",
    "你是产销品加载执行方案生成助手（环节3：产品信息整合）。任务：节点3返回的相似产品完整产品配置信息（similarOffer.offerInfo）与节点2要素信息（elements_json.fields）为**同一套配置结构模板规范**（同为四类18字段数组，field 名一致），逐字段执行同构键值合并，输出最终产品信息——输出结构必须与匹配成功产品的 offerInfo JSON结构完全一致且完整（含全部溯源键+fields四类18字段数组，一个键都不能少）。\n"
    "输入：节点2要素JSON（elements_json={elements_json}）+ 节点3相似产品（similarOffer={similarOffer}，offerInfo 为完整产品配置信息：similarOfferId/similarOfferName/series/sub_type + fields 18字段同构数组）。\n"
    "\n"
    "【同构合并规则（按 field 名逐字段对齐，严格执行）】\n"
    "1. 需求要素字段有值（value非空且非\"待补充\"）→ 采用需求要素值（source标\"原始需求\"）；\n"
    "2. 需求要素字段无值 → 采用 offerInfo.fields 中同名字段值（source标\"AI推理\"）；\n"
    "3. 两侧皆缺失 → value填空字符串\"\"（source标\"AI推理\"）。\n"
    "\n"
    "【特殊字段口径（V2.2）】\n"
    "- 9.套餐固定费：需求未提取到时按同构合并规则取 offerInfo 同名字段值（source标\"AI推理\"），offerInfo 亦无时值填\"待补充\"；\n"
    "- 6.流量资源/7.语音资源/8.短信资源：三类资源全部未提取到时，未提取到的字段值填\"待补充\"（按\"无\"处理口径交下游执行；任一类已提取到则其余资源字段按相似产品补全）；\n"
    "- 3.产品编码：禁止AI推理——由智能配置环节落地后生成，需求未提供时值填\"由智能配置生成\"、source标\"AI推理\"；\n"
    "- 销售品状态：新需求一律填\"待上线\"（禁止取相似产品的\"在售\"）。\n"
    "- 待补充项判定（pending_fields）由下游代码节点统一执行，本节点不再自行输出 pending_fields，只需把待补充字段value填\"待补充\"。\n"
    "\n"
    "【逐字段整合，必须覆盖四类全部字段（共18个，一个都不能少）】\n"
    "A.基础信息：1.产品名称 2.产品属性（基础/可选/增值） 3.产品编码 4.生效日期 5.退订规则\n"
    "B.资源配置：6.流量资源 7.语音资源 8.短信资源\n"
    "C.营销资源：9.套餐固定费 10.收费方式（按月/按量/一次性） 11.优惠条件 12.优惠期\n"
    "D.销售规则：13.渠道类型 14.适用地区 15.订购限制 16.副卡规则 17.计费周期 18.销售品状态\n"
    "\n"
    "【来源标注（仅两种取值，输出前逐字段重算，禁止沿用输入source）】\n"
    "source只允许\"原始需求\"或\"AI推理\"：value非空且取自需求要素（需求原文可逐字找到，含同义改写）→标\"原始需求\"；value取自offerInfo同名字段、产品编码特殊值\"由智能配置生成\"、销售品状态\"待上线\"、空字符串\"\"、\"待补充\"→一律标\"AI推理\"；禁止\"待补充\"作为来源（待补充只出现在value中）。自检：source为\"原始需求\"时value必须非空且非\"待补充\"。\n"
    "\n"
    "【输出要求（单一出参 fields_output，输出与 offerInfo 完全一致且完整的JSON结构）】\n"
    "按以下格式输出，第一行原样输出标签 fields_output:，随后紧跟一个JSON对象（以{开头、}结尾），除该标签行外不得输出任何其他文字、代码块或说明：\n"
    "fields_output: 后紧跟一个完整JSON对象，该JSON对象结构必须与输入 similarOffer.offerInfo 完全一致，固定包含以下5个键（一个都不能少）：\n"
    "1. similarOfferId：字符串，直接取 offerInfo.similarOfferId 原值；\n"
    "2. similarOfferName：字符串，直接取 offerInfo.similarOfferName 原值；\n"
    "3. series：字符串，直接取 offerInfo.series 原值；\n"
    "4. sub_type：字符串，直接取 offerInfo.sub_type 原值；\n"
    "5. fields：字段数组（18项），每项为JSON对象，固定包含4个键：field（字符串=字段名称）、category（字符串=字段分类，保留与 elements_json/offerInfo 一致的分类前缀格式）、value（字符串=字段值）、source（字符串=原始需求或AI推理）；待补充字段value填\"待补充\"；执行方案Markdown表格（plan_md）与待补充判定（pending_fields）均由下游代码节点基于整合后字段数组自动生成，本节点不再输出 plan_output/plan_md/pending_fields。",
    [inp("elements_json", "引用节点2要素JSON", ref_block=nid(2), ref_rel="elements_json"),
     inp("similarOffer", "引用节点3相似产品（含offerInfo完整产品配置信息）", ref_block=nid(3), ref_rel="similarOffer")],
    [out("fields_output", "整合后完整产品信息JSON字符串（结构与 offerInfo 完全一致：similarOfferId/similarOfferName/series/sub_type + fields 18项字段）")]))
# 31 字段本体推理节点已移除（V2.3 简化）：fields_output 由节点41 直接解析拆分

# 004a 方案输出拆分（V2.3 重构：节点31 字段本体推理已移除，单入参 fields_output=节点4整合结果）：
# 兼容 offerInfo 完整结构（similarOfferId/similarOfferName/series/sub_type + fields）与纯 fields 数组两种形态；
# pending_fields 反查 value=待补充、plan_md 由代码重新生成四列表格；req_id 系统生成
s1.append(code_node(41, "方案输出拆分", CODE_004A,
    [inp("fields_output", "引用节点4整合结果（完整产品信息JSON，唯一数据源）", ref_block=nid(4), ref_rel="fields_output")],
    [code_out("plan_json", 41), code_out("plan_md", 41),
     code_out("pending_fields", 41), code_out("req_id", 41)],
    pos=(1455, 300)))
s1.append(selector_node2(5, "待补充项判断",
    [dep_node(41, "方案输出拆分", ["pending_fields"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义：pending_fields 不为空（长度大于0）→ 有待补充项 → 保存执行方案 → 确认结束；
    #       port=0 兜底分支（pending_fields 为空）→ 直接结束提示，不保存（禁止进入智能配置）
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
     inp("pending_fields", "待补充字段", ref_block=nid(41), ref_rel="pending_fields")],
    "《产销品加载执行方案》已生成（req_id：{req_id}）\n\n{plan_md}\n\n【待补充字段】{pending_fields}\n以上字段需求未提供且相似产品中无对应配置，需由您补充后才能执行：\n- 请直接补充字段值，将更新执行方案并再次确认；\n- 如需调整其他字段：请直接说明修改意见（其余字段已按相似产品补全，如与预期不符可一并说明）。"))
s1.append(end_node(8, "结束(无待补充项)",
    [inp("req_id", "执行方案存储key", ref_block=nid(41), ref_rel="req_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(41), ref_rel="plan_md")],
    "《产销品加载执行方案》已生成并保存（req_id：{req_id}）\n\n{plan_md}\n\n【无待补充字段】缺失字段已按相似产品补全，来源已逐字段标注：原始需求=需求原文提取，AI推理=相似产品取值。\n请核对以上执行方案：\n- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节（每环节执行后打印结果，仅异常时中断）；\n- 如需调整：请直接说明修改意见。"))
e1 = [edge(1,2), edge(2,3), edge(3,4), edge(4,41), edge(41,5),
      edge(5,6,0), edge(5,7,-1), edge(6,8)]
files["wf_sub_01_需求分析.json"] = workflow(
    "产销品-需求分析", "子工作流1：需求分析（执行方案生成，6环节新链路）。环节1需求理解与要素拆解（LLM仅提取要素信息，18字段未提及项value为空）→环节2相似产品查询（自研模拟，18销售品种子，入参=要素摘要，返回相似度最高的1个产品并附完整产品配置信息 offerInfo——与需求要素同构的 fields 四类18字段数组）→环节3产品信息整合（同一套配置结构模板规范，按field名逐字段同构键值合并：需求有值→原始需求，无值→offerInfo同名字段值标AI推理，皆缺失→留空；输出结构与offerInfo完全一致且完整）→方案输出拆分（单入参=整合结果：fields直接组装、pending_fields反查value=待补充、plan_md代码重新生成四列表格）→待补充项判断（无待补充→保存执行方案→确认结束；有待补充→补充提示结束）。", "wf_sub_01", s1, e1)

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
      ("save_result", "四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL", "string"),
      ("product_config", "完整落地配置JSON（含product_id/offer_id/offer_name等与plan_json原文）", "string")]))
s2.append(plugin_node(105, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=config（智能配置），result_json=完整落地配置JSON（内含 product_id/offer_id/offer_name/... 与 plan_json 原文，供 wf_sub_03 稽核、wf_sub_04 测试、wf_sub_06 门禁按 req_id+config 自查提取 offer_id）；主流程删除后存储下沉子工作流",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=config（智能配置）", content="config"),
     inp("result_json", "环节结果JSON=完整落地配置JSON（含offer_id编码）", ref_block=nid(103), ref_rel="product_config"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(900, 135)))
s2.append(end_node(104, "结束(配置落地完成)",
    [inp("product_id", "CRM产品ID", ref_block=nid(103), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(103), ref_rel="offer_id"),
     inp("save_result", "四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "落地状态", ref_block=nid(103), ref_rel="status")],
    "智能配置完成：product_id={product_id}，offer_id={offer_id}\n四类字段写入结果：{save_result}\n状态：{status}"))
files["wf_sub_02_智能配置.json"] = workflow(
    "产销品-智能配置", "子工作流2：智能配置（配置落地）。单入参 req_id 自查链路：节点结果查询按 req_id+requirement 读取执行方案记录→代码节点提取 list[0].result_json 原文（V2.2）→save_product_config 透传落地（req_id 与存储键同一，方案key由后端从 plan_json 提取）；确认与否由外层智能体识别判断（V2.2 门禁移除）；结束前存储 node_name=config，result_json=完整落地配置JSON（V2.4：含 offer_id 编码，供下游稽核/测试/门禁自查）。", "wf_sub_02", s2,
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
    [code_out("record_json", 207), code_out("offer_id", 207)],
    pos=(390, 135)))
s3.append(plugin_node(202, "实时稽核", "realtime_spec_audit",
    "工具2：自研模拟实时稽核，同步返回（无文件上传/无轮询）；offer_id/config_json 取自 config 环节结果（节点207提取原文与解析的offer_id）",
    BASE_URL + "/api/v1/appstore/audit/realtime",
    [inp("offer_id", "销售品ID（节点207从落地结果解析的offer_id）", ref_block=nid(207), ref_rel="offer_id"),
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
    [code_out("record_json", 310), code_out("offer_id", 310)],
    pos=(240, 135)))
s4.append(plugin_node(302, "发起测试", "offer_test",
    "工具3：自研模拟测试发起，返回模拟测试流水globalId；offerId 取自 config 环节结果（节点310解析的offer_id）",
    BASE_URL + "/api/v1/appstore/test/offer/start",
    [inp("offerId", "销售品ID（节点310从落地结果解析的offer_id）", ref_block=nid(310), ref_rel="offer_id")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("globalId", "测试流水号", "string")]))
s4.append(plugin_node(303, "查询测试场景", "get_test_scenes",
    "工具4：查询受理验证覆盖范围（套餐新装/副卡加装/套餐退订）",
    BASE_URL + "/api/v1/appstore/test/offer/scenes",
    [inp("globalId", "测试流水号（节点2出参）", ref_block=nid(302), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"), ("testScenes", "场景列表", "array")]))
# 304 轮询测试进度（V2.4 重写）：平台不做循环节点（设计方案3.4.4），改为 type=6 代码节点
# 内嵌 asyncio.sleep(5) 轮询工具5逻辑——360次/30分钟超时，连续5次查询失败终止转人工；
# 出参 fail_reason 非空标识异常退出（查询失败/超时/测试失败），供报告节点区分
s4.append(code_node(304, "轮询测试进度", CODE_POLL_PROGRESS,
    [inp("globalId", "测试流水号（节点302出参）", ref_block=nid(302), ref_rel="globalId")],
    [code_out("done", 304), code_out("failed", 304), code_out("failIndex", 304),
     code_out("fail_reason", 304)],
    pos=(1815, 300)))
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
    [code_out("record_json", 407), code_out("offer_id", 407)],
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
# wf_sub_06 上线审批（V2.4 按设计方案3.2.6 重构：11节点/10边）
#   串行自查5类环节结果（config/spec/fee/test/requirement）→ 代码节点501s
#   合成结构化汇总（含 product_id/orderId/offerInstId）→ LLM 501g 生成7章节报告
#   → 报告存储501r（node_name=report）→ 审批推送502（report_url=报告）→ 结束
# ============================================================
s6 = []
s6.append(start_node(501, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，四环节结果门禁校验依据，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
]))
s6.append(plugin_node(5011, "自查配置结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果（list[0].result_json=完整落地配置JSON，内含 product_id/offer_id）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 135)))
s6.append(code_node(5012, "提取配置环节原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点5011查询出参 list（记录数组JSON）", ref_block=nid(5011), ref_rel="list")],
    [code_out("record_json", 5012), code_out("offer_id", 5012)],
    pos=(390, 135)))
s6.append(plugin_node(5013, "自查稽核结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=spec 取回规格稽核环节结果（list[0].result_json=稽核总结文本）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=spec", content="spec"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 260)))
s6.append(code_node(5014, "提取稽核环节原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点5013查询出参 list（记录数组JSON）", ref_block=nid(5013), ref_rel="list")],
    [code_out("record_json", 5014)],
    pos=(390, 260)))
s6.append(plugin_node(5015, "自查资费结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=fee 取回资费校准环节结果（list[0].result_json=风险解读文本）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=fee", content="fee"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 385)))
s6.append(code_node(5016, "提取资费环节原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点5015查询出参 list（记录数组JSON）", ref_block=nid(5015), ref_rel="list")],
    [code_out("record_json", 5016)],
    pos=(390, 385)))
s6.append(plugin_node(5017, "自查测试结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=test 取回自动测试环节结果（list[0].result_json=测试报告文本，内含受理凭证 orderId/offerInstId）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=test", content="test"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 510)))
s6.append(code_node(5018, "提取测试环节原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点5017查询出参 list（记录数组JSON）", ref_block=nid(5017), ref_rel="list")],
    [code_out("record_json", 5018)],
    pos=(390, 510)))
s6.append(plugin_node(5019, "自查执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=requirement 取回执行方案（list[0].result_json=执行方案原文，用于报告需求摘要章节）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 635)))
s6.append(code_node(5020, "提取执行方案原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点5019查询出参 list（记录数组JSON）", ref_block=nid(5019), ref_rel="list")],
    [code_out("record_json", 5020)],
    pos=(390, 635)))
# 501s 合成结构化汇总：解析5类环节原文，提取关键字段（product_id/orderId/offerInstId/各环节结论）合成 JSON
s6.append(code_node(5021, "合成结构化汇总", CODE_SUMMARY_APPROVAL,
    [inp("config_result", "配置环节原文（节点5012提取）", ref_block=nid(5012), ref_rel="record_json"),
     inp("spec_result", "稽核环节原文（节点5014提取）", ref_block=nid(5014), ref_rel="record_json"),
     inp("fee_result", "资费环节原文（节点5016提取）", ref_block=nid(5016), ref_rel="record_json"),
     inp("test_result", "测试环节原文（节点5018提取）", ref_block=nid(5018), ref_rel="record_json"),
     inp("requirement_result", "执行方案原文（节点5020提取）", ref_block=nid(5020), ref_rel="record_json")],
    [code_out("summary_json", 5021), code_out("product_id", 5021), code_out("offer_id", 5021)],
    pos=(540, 385)))
# 501g 报告生成：强制7章节（设计方案3.1.4），温度0.2
s6.append(llm_node(5022, "上线报告生成",
    "请按标准模板汇总生成《销售品上线测试与稽核报告》，输入为结构化汇总（summary_json={summary_json}），强制包含 7 章节：\n"
    "1. 需求摘要（引用 requirement_summary，仅列关键字段）；\n"
    "2. 配置落地结果（config：product_id/offer_id/offer_name 及写入情况）；\n"
    "3. 稽核结论（spec：audit_summary，通过/驳回）；\n"
    "4. 资费结论（fee：risk_summary，是否存在风险）；\n"
    "5. 测试统计（test：场景数/测点数/成功/失败统计，失败测点逐条列出）；\n"
    "6. 受理验证结论（强制章节，不得省略）：引用 orderId={order_id}、offerInstId={offer_inst_id}，为空则写明\"未获取到受理凭证，需人工核实\"，并逐受理场景给出通过/失败结论；\n"
    "7. 上线建议：全部通过 → \"建议上线\"；任一环节未通过 → \"暂缓上线\"。\n"
    "只基于输入数据生成，不得新增结论。\n"
    "输出要求：仅输出报告正文（对应出参 report），不输出其他多余文字。",
    [inp("summary_json", "引用节点501s结构化汇总", ref_block=nid(5021), ref_rel="summary_json"),
     inp("order_id", "受理订单号（501s从测试报告提取）", ref_block=nid(5021), ref_rel="summary_json"),
     inp("offer_inst_id", "销售品实例ID（501s从测试报告提取）", ref_block=nid(5021), ref_rel="summary_json")],
    [out("report", "上线报告（含7章节）")], pos=(690, 385)))
# 501r 报告存储：node_name=report
s6.append(plugin_node(5023, "报告存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=report（上线报告），result_json=报告正文；落库后传审批推送",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("node_name", "环节名=report（上线报告）", content="report"),
     inp("result_json", "环节结果JSON=上线报告", ref_block=nid(5022), ref_rel="report"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(840, 385)))
# 502 审批推送：product_id/report_url 均引用结构化结果（V2.4：不再传空由后端回读）
s6.append(plugin_node(502, "审批推送", "submit_release_approval",
    "工具9：自研模拟审批推送；插件层硬门禁：approve_confirmed==true 且存储中存在该 req_id 的四环节结果（config/spec/fee/test）；幂等：同product_id返回原approval_id；product_id=501s从config环节结果提取，report_url=501g生成的上线报告",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("product_id", "CRM产品ID（节点5021从config环节结果提取）", ref_block=nid(5021), ref_rel="product_id"),
     inp("report_url", "上线报告（节点5022生成的报告正文）", ref_block=nid(5022), ref_rel="report"),
     inp("req_id", "执行批次号（四环节结果门禁校验依据，=开始节点 req_id）", ref_block=nid(501), ref_rel="req_id"),
     inp("approve_confirmed", "审批发起确认标志true", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string")], pos=(990, 385)))
s6.append(end_node(503, "结束(审批已推送)",
    [inp("approval_id", "审批单号", ref_block=nid(502), ref_rel="approval_id"),
     inp("status", "提交状态", ref_block=nid(502), ref_rel="status"),
     inp("report", "上线报告", ref_block=nid(5022), ref_rel="report")],
    "上线审批已推送：approval_id={approval_id}，status={status}\n{report}\n可随时发送\"查询审批进度\"消息查询审批状态。"))
files["wf_sub_06_上线审批.json"] = workflow(
    "产销品-上线审批", "子工作流6：上线审批（V2.4 按设计方案3.2.6 重构，13节点/12边）。单入参 req_id：串行自查5类环节结果（config/spec/fee/test/requirement，各配提取代码节点）→代码节点5021合成结构化汇总（含product_id/offer_id/orderId/offerInstId与各环节结论）→LLM生成7章节上线报告→报告落库（node_name=report）→submit_release_approval推送（product_id=结构化汇总提取，report_url=报告正文；后端硬门禁：approve_confirmed=true+req_id四环节结果齐全，幂等）。", "wf_sub_06", s6,
    [edge(501,5011), edge(5011,5012), edge(5012,5013), edge(5013,5014), edge(5014,5015),
     edge(5015,5016), edge(5016,5017), edge(5017,5018), edge(5018,5019), edge(5019,5020),
     edge(5020,5021), edge(5021,5022), edge(5022,5023), edge(5023,502), edge(502,503)])

# ============================================================
# wf_sub_07 监控运维（V2.4 优化：输出单产品运营情况报告）
#   query_product_monitor（含 offer_name/趋势字段）→ 异常判定 →
#   异常分支：告警文案→send_alert→报告生成；正常分支：直接报告生成 →
#   两个分支汇聚到 LLM 运营报告节点（按固定模板输出）→ 结束输出报告
# ============================================================
OPS_REPORT_PROMPT_HEAD = (
    "# 角色\n"
    "\n"
    "你是产销品监控运维智能体。根据系统提供的运营数据，输出单产品运营情况报告。\n"
    "\n"
    "# 运营数据\n"
    "{ops_text}\n"
    "\n"
    "# 处理要求\n"
    "1. 从效益、市场、质量三个维度展示运营指标(数据以系统提供的为准，不得凭空创造不存在的数据)。\n"
    "2. 综合判断产品运行状态：🟢运行良好 / 🟡重点关注 / 🔴存在异常。综合当前值、趋势、多指标是否同时异常判断，不要仅凭单一指标机械判断。\n"
    "3. 输出简洁AI运营分析：整体判断、效益表现、市场表现、质量表现、重点关注。只在发现明显异常或风险时给出建议，整体正常时不要强行生成建议。\n"
    "\n"
    "# 固定输出格式\n"
    "# 【产品名称】｜运营情况\n"
    "\n"
    "**当前状态：🟢/🟡/🔴**\n"
    "**数据周期：最近1天（以系统返回的最新数据为准）**\n"
    "**更新时间：XXX**\n"
    "\n"
    "## 核心运营指标\n"
    "\n"
    "| 维度 | 指标 | 当前值 | 趋势 | 状态 |\n"
    "|---|---:|---:|---|---|\n"
    "\n"
    "## AI运营分析\n"
    "\n"
    "> 整体判断：\n"
    "> XXX\n"
    "\n"
    "> 效益表现：\n"
    "> XXX\n"
    "\n"
    "> 市场表现：\n"
    "> XXX\n"
    "\n"
    "> 质量表现：\n"
    "> XXX\n"
    "\n"
    "> 重点关注：\n"
    "> XXX"
)
s7 = []
s7.append(start_node(601, [
    inp("product_id", "销售品ID", required=True),
]))
s7.append(plugin_node(602, "监控查询", "query_product_monitor",
    "工具10：自研模拟监控查询（产品名称/订单量及趋势/异常量及趋势/计费差错率及趋势/告警列表），供运营报告生成",
    BASE_URL + "/api/v1/appstore/product/monitor",
    [inp("product_id", "销售品ID", ref_block=nid(601), ref_rel="product_id"),
     inp("metric", "指标默认all", content="all")],
    [("offer_name", "产品名称", "string"),
     ("order_count", "订单量", "string"), ("order_trend", "订单量趋势", "string"),
     ("error_count", "异常量", "string"), ("error_trend", "异常量趋势", "string"),
     ("fee_error_rate", "计费差错率", "string"), ("fee_trend", "计费差错率趋势", "string"),
     ("alarm_list", "告警列表", "array")],
    method="get"))
s7.append(selector_node2(603, "异常判定",
    [dep_node(602, "监控查询", ["error_count", "fee_error_rate"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义：error_count 不等于"0"（即存在异常）→ 走异常告警（port=-1 命中条件）；
    #       port=0 兜底分支（error_count 等于"0"即正常）→ 走正常报告生成
    [(-1, [cond_item(cond_ref(602, "error_count", "监控查询"), 2, cond_str("0"))])]))
s7.append(llm_node(604, "告警文案生成",
    "基于监控异常数据生成告警文案（含产品、异常摘要、建议）。输入：offer_name={offer_name}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "输出要求：仅输出告警文案内容（对应出参 alert_content，需包含产品名称、异常摘要、处置建议三部分），不输出其他多余文字。",
    [inp("offer_name", "产品名称", ref_block=nid(602), ref_rel="offer_name"),
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
     inp("content", "告警文案（节点604输出）", ref_block=nid(604), ref_rel="alert_content")],
    [("alert_id", "告警单号", "string"), ("status", "推送状态", "string")]))
# 608/609 运营报告生成：异常分支（告警后）与正常分支均汇聚到此 LLM 节点，按固定模板输出单产品运营情况报告
s7.append(llm_node(608, "运营报告生成(异常分支)",
    OPS_REPORT_PROMPT_HEAD + "\n\n补充说明：本产品存在异常（error_count={error_count}，fee_error_rate={fee_error_rate}，告警列表={alarm_list}），已触发告警推送；报告状态判定应不低于🟡，异常指标须在\"重点关注\"中逐条说明。",
    [inp("ops_text", "运营数据JSON（监控查询出参汇总）", ref_block=nid(602), ref_rel="offer_name"),
     inp("offer_name", "产品名称", ref_block=nid(602), ref_rel="offer_name"),
     inp("order_count", "订单量", ref_block=nid(602), ref_rel="order_count"),
     inp("order_trend", "订单量趋势", ref_block=nid(602), ref_rel="order_trend"),
     inp("error_count", "异常量", ref_block=nid(602), ref_rel="error_count"),
     inp("error_trend", "异常量趋势", ref_block=nid(602), ref_rel="error_trend"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(602), ref_rel="fee_error_rate"),
     inp("fee_trend", "计费差错率趋势", ref_block=nid(602), ref_rel="fee_trend"),
     inp("alarm_list", "告警列表", ref_block=nid(602), ref_rel="alarm_list")],
    [out("ops_report", "单产品运营情况报告")], pos=(1815, 460)))
s7.append(llm_node(609, "运营报告生成(正常分支)",
    OPS_REPORT_PROMPT_HEAD + "\n\n补充说明：本产品各指标正常（error_count=0，计费差错率低于阈值，无新增告警），报告状态判定为🟢；整体正常时不要强行生成建议。",
    [inp("offer_name", "产品名称", ref_block=nid(602), ref_rel="offer_name"),
     inp("order_count", "订单量", ref_block=nid(602), ref_rel="order_count"),
     inp("order_trend", "订单量趋势", ref_block=nid(602), ref_rel="order_trend"),
     inp("error_count", "异常量", ref_block=nid(602), ref_rel="error_count"),
     inp("error_trend", "异常量趋势", ref_block=nid(602), ref_rel="error_trend"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(602), ref_rel="fee_error_rate"),
     inp("fee_trend", "计费差错率趋势", ref_block=nid(602), ref_rel="fee_trend"),
     inp("alarm_list", "告警列表", ref_block=nid(602), ref_rel="alarm_list")],
    [out("ops_report", "单产品运营情况报告")], pos=(1815, 135)))
s7.append(end_node(606, "结束(异常-已告警)",
    [inp("alert_id", "告警单号", ref_block=nid(605), ref_rel="alert_id"),
     inp("ops_report", "运营情况报告", ref_block=nid(608), ref_rel="ops_report")],
    "监控发现异常，已推送告警（alert_id={alert_id}）。单产品运营情况报告如下：\n\n{ops_report}"),)
s7.append(end_node(607, "结束(正常-运营报告)",
    [inp("ops_report", "运营情况报告", ref_block=nid(609), ref_rel="ops_report")],
    "{ops_report}"))
files["wf_sub_07_监控运维.json"] = workflow(
    "产销品-监控运维", "子工作流7：监控运维（V2.4 优化：输出单产品运营情况报告）。query_product_monitor（自研模拟，含产品名称与指标趋势）→异常判定（error_count≠0走异常分支）→异常分支：告警文案→send_alert→运营报告生成；正常分支：直接运营报告生成——两分支各自 LLM 节点按固定模板（状态🟢/🟡/🔴+核心运营指标表+AI运营分析五要素）输出报告，结束节点输出报告全文。支持每日定时与对话触发。", "wf_sub_07", s7,
    [edge(601,602), edge(602,603), edge(603,609,0), edge(603,604,-1), edge(604,605), edge(605,608), edge(608,606), edge(609,607)])

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

