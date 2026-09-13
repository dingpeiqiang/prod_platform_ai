# -*- coding: utf-8 -*-
# 生成产销品加载AI应用 Skills封装版 导出JSON（6个技能 + 智能体配置）
# 依据：《产销品加载AI应用开发方案-Skills封装版.md》S1.0
# 复用：插件/自研插件集V1.6（15个插件导出JSON，接口地址不变）；工作流节点生成模式对齐 gen_workflows.py
# 结构：每个技能 = 一个轻量工作流容器（flowId=skill_01~06），技能内节点复用 type=0/1/2/3/6/9/13；
#       执行主干四阶段封装进 skill_03 单技能内串行脚本（阶段节点 + 存储回放续跑 + 统一异常出口）
import json, os, io

BASE = os.path.dirname(os.path.abspath(__file__))
BASE_URL = "http://10.86.13.201:31281"

# 插件接口地址（与自研插件集V1.6严格同步）
URLS = {
    "query_similar_offer": "/api/v1/appstore/similar/offer/query",
    "realtime_spec_audit": "/api/v1/appstore/audit/realtime",
    "offer_test": "/api/v1/appstore/test/offer/start",
    "get_test_scenes": "/api/v1/appstore/test/offer/scenes",
    "get_test_progress": "/api/v1/appstore/test/offer/progress",
    "get_test_result": "/api/v1/appstore/test/offer/result",
    "save_product_config": "/api/v1/appstore/product/config/save",
    "check_billing_rule": "/api/v1/appstore/billing/rules/verify",
    "submit_release_approval": "/api/v1/appstore/approval/submit",
    "query_product_monitor": "/api/v1/appstore/product/monitor",
    "send_alert": "/api/v1/appstore/alert/send",
    "query_approval_status": "/api/v1/appstore/approval/status",
    "save_node_result": "/api/v1/appstore/result/save",
    "query_node_result": "/api/v1/appstore/result/query",
}

OPS = [{"value": 1, "label": "等于"}, {"value": 2, "label": "不等于"},
       {"value": 3, "label": "长度大于"}, {"value": 4, "label": "长度大于等于"},
       {"value": 5, "label": "长度小于"}, {"value": 6, "label": "长度小于等于"},
       {"value": 7, "label": "包含"}, {"value": 8, "label": "不包含"},
       {"value": 9, "label": "为空"}, {"value": 10, "label": "不为空"},
       {"value": 15, "label": "长度等于"}]


def nid(seq, ns="b2c3d4e5"):
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


def start_node(seq, inputs, pos=(15, 300)):
    return {
        "flowJson": None, "inputs": inputs, "checkErr": False,
        "nodeMeta": {"description": "工作流的起始节点，用于设定启动工作流需要的信息", "title": "开始节点"},
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 0
    }


def llm_node(seq, title, prompt, in_refs, outputs, pos=(390, 300), sys_prompt="", temperature=0.2):
    inputs = {"llmParam": [{"name": "prompt", "type": "string", "content": prompt}],
              "inputParameters": in_refs}
    return {
        "outputs": outputs, "max_tokens": 2048, "flowJson": None,
        "inputs": inputs, "checkErr": False,
        "prompt_system": sys_prompt,
        "nodeMeta": {"description": title, "title": title},
        "temperature": temperature, "top_p": 0.5,
        "position": {"x": pos[0], "y": pos[1]},
        "id": nid(seq), "dependencyData": [], "type": 1
    }


def cond_ref(block_seq, rel, block_title):
    b = nid(block_seq)
    return {
        "blockID": b, "relName": rel,
        "nameValue": [b, b + "," + rel],
        "currValue": b + "," + rel,
        "name": "", "description": "", "type": "ref", "content": "",
    }


def cond_str(value):
    return {"blockID": "", "relName": "", "nameValue": "", "currValue": ",",
            "name": "", "description": "", "type": "string", "content": value}


def cond_item(left, operator, right=None):
    d = {"left": left, "right": right or cond_str(""),
         "conditions": OPS, "operator": operator}
    return d


def dep_node(block_seq, block_title, rel_names):
    b = nid(block_seq)
    return {
        "relName": b, "name": block_title, "disabled": True,
        "children": [{"relName": b + "," + r, "name": r, "type": "string", "content": ""}
                     for r in rel_names]
    }


def selector_node2(seq, title, deps, branches, pos=(530, 300)):
    """条件分支节点：条件定义在 port=-1（否则分支命中语义），port=0 出边由平台自动路由为"如果分支"兜底"""
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


def plugin_out(name, desc, ptype="string", item_fields=None):
    """插件节点出参声明；array 出参带 item 树"""
    if ptype != "array":
        return {"name": name, "cname": desc, "sechema": [], "type": ptype, "required": False}
    leaves = [{"name": n, "cname": d, "sechema": [], "type": "string", "required": False}
              for (n, d) in (item_fields or [])]
    item = {"name": "item", "cname": desc.split("，")[0] if desc else name,
            "sechema": leaves, "type": "object", "required": False}
    return {"name": name, "cname": desc, "sechema": [item], "type": "array", "required": False}


def plugin_node(seq, title, code, desc, url_key, inputs, outputs, pos=(650, 300), method="post"):
    outputs = [tuple(o) if len(o) == 4 else (o[0], o[1], o[2], None) for o in outputs]
    return {
        "outputs": [plugin_out(n, d, t, item_fields=f) for (n, d, t, f) in outputs],
        "submit_way": method, "flowJson": None, "authentic_info": "",
        "inputs": inputs, "checkErr": False,
        "nodeMeta": {"title": title, "code": code, "description": desc, "version": "1"},
        "authentic_info_new": {"auth_type": "1", "auth_info": {"inparams": [], "params": [], "outparams": []}},
        "id": nid(seq), "position": {"x": pos[0], "y": pos[1]},
        "dependencyData": [], "type": 3, "url": BASE_URL + URLS[url_key]
    }


def loop_node(seq, title, desc, in_refs, outputs, pos=(650, 300)):
    return {
        "outputs": outputs, "flowJson": None,
        "inputs": {"loopParam": {"loopType": "while", "maxLoopCount": 360,
                                 "breakCondition": "done == true || failed == true || timeout"},
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
    "    m = re.search(r'\\{[\\s\\S]*\\}', raw)\n"
    "    if not m:\n"
    "        obj = {}\n"
    "    else:\n"
    "        try:\n"
    "            obj = json.loads(m.group(0))\n"
    "        except Exception:\n"
    "            obj = {}\n"
    "    pf = obj.get('pending_fields') or ''\n"
    "    if isinstance(pf, list):\n"
    "        pf = ','.join([str(x) for x in pf])\n"
    "    plan_json = obj.get('plan_json')\n"
    "    if not isinstance(plan_json, str):\n"
    "        plan_json = json.dumps(plan_json if plan_json is not None else obj, ensure_ascii=False)\n"
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


def code_node(seq, title, code, in_refs, outputs, pos=(650, 300)):
    """代码节点（对齐 gen_workflows.py / 平台真实导出：inputs 平铺 list、language=1、type=6）"""
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


def end_node(seq, title, inputs, out_content, pos=(1030, 300)):
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


def edge(s, e, port=None):
    return {"sourcePort": port, "endId": nid(e), "startId": nid(s)}


def skill(flow_name, flow_remark, flow_id, nodes, edges, triggers, skill_desc):
    """技能导出JSON：工作流容器 + 技能元数据（skillMeta：触发方式/技能描述，供平台技能注册参考）"""
    return {
        "flowName": flow_name,
        "flowRemark": flow_remark,
        "flowIco": "workflowIcon",
        "workFlowSchema": None,
        "workFlowSchemaJSON": {"nodes": nodes, "edges": edges, "flowId": flow_id, "version": "1.6"},
        "userScope": 4,
        "projectId": "",
        "skillMeta": {
            "skillId": flow_id,
            "skillName": flow_name,
            "triggerType": triggers,
            "description": skill_desc
        }
    }


# 常用 array 出参 item 字段（与 gen_workflows.py ARRAY_ITEM_FIELDS 同步）
ITEM_ERR = [("item", "问题项"), ("level", "error/warning"), ("desc", "问题描述"), ("suggest", "整改建议")]
ITEM_RISK = [("risk_type", "风险类型"), ("risk_desc", "风险描述"), ("suggest", "处置建议")]
ITEM_ALARM = [("alarm_id", "告警单号"), ("alarm_level", "high/middle/low"), ("content", "告警内容"), ("alarm_time", "告警时间")]

files = {}

# ============================================================
# skill_01 需求分析与执行方案生成（主动）
# ============================================================
s1 = []
s1.append(start_node(1, [
    inp("requirement_text", "销售品需求描述文本或文档内容摘要（必填）", required=True),
    inp("requirement_file", "需求文档地址（选填）", required=False),
    inp("revise_opinion", "修改意见（修改执行方案续跑时传入，选填）", required=False),
]))
s1.append(llm_node(2, "需求理解与要素拆解",
    "你是产销品加载需求分析助手。按6步分析：理解需求→提取并拆解业务要素（基础信息/资源配置/营销资源/销售规则四类）→识别信息完整性（字段三态：原始需求/AI补全/待补充）。需求原文：{requirement_text}\n修改意见（如有则覆盖分析）：{revise_opinion}\n补充规则（V1.6）：pending_fields默认为空——仅当费用（价格）或资源（流量/语音/短信）未提取到时，才将该字段填\"待补充\"并计入pending_fields（禁止推理，禁止从相似产品照搬）；其余缺失字段待相似产品返回后按最高相似度产品补全，来源标记\"AI补全\"；来源只允许\"原始需求\"或\"AI补全\"两种。\n输出要求（两个出参逐一约定，每个出参只输出自己的内容，严禁把其他出参内容并入）：\n1. elements_json：仅输出结构化要素JSON对象本身（以{开头、}结尾），包含 fields 数组与 pending_fields 数组，不得附带键名前缀或说明；\n2. need_summary：仅输出需求摘要文本本身（≤5000字符，供相似度分析调用使用）；\n3. 严格禁止输出形如\"elements_json: {...} need_summary: ...\"的拼接包；除上述两个出参各自内容外不输出任何多余文字。",
    [inp("requirement_text", "引用开始节点 requirement_text", ref_block=nid(1), ref_rel="requirement_text"),
     inp("revise_opinion", "引用开始节点 revise_opinion", ref_block=nid(1), ref_rel="revise_opinion")],
    [out("elements_json", "业务要素结构化JSON"), out("need_summary", "需求摘要")], pos=(375, 300)))
s1.append(plugin_node(3, "相似产品查询", "query_similar_offer",
    "工具1：自研模拟相似度分析，以《产品信息.txt》18个销售品为相似产品库",
    "query_similar_offer",
    [inp("businessDesc", "业务需求描述文本（>5000字符时用需求摘要）", ref_block=nid(2), ref_rel="need_summary")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("similarOfferList", "相似产品列表（similarOfferId/similarOfferName/similarityScore/similarityDesc）", "array", None)], pos=(640, 300)))
s1.append(llm_node(4, "字段映射与补全",
    "基于业务要素与相似产品列表，映射为实际配置字段并补全：\n要素：{elements_json}\n相似产品：{similarOfferList}\n规则：价格、资源类未提供填\"待补充\"（禁止推理）；其余缺失字段取最高相似度产品对应值，来源\"AI补全\"；来源只允许\"原始需求\"/\"AI补全\"两种。\n输出要求（单一出参 plan_output）：\n按以下格式输出，第一行原样输出标签 plan_output:，随后紧跟一个JSON对象（以{开头、}结尾），除该标签行外不得输出任何其他文字、代码块或说明：\nplan_output: {\"plan_json\":..., \"plan_md\":..., \"pending_fields\":...}\n该JSON对象固定包含以下3个键：\n1. plan_json：执行方案JSON对象，含 req_id/fields/similar_offers/pending_fields 四个键，fields内每项含 field/value/source；req_id 键留空字符串（由后续代码节点统一生成，禁止自行生成）；\n2. plan_md：执行方案Markdown表格字符串（以|字段分类|开头，固定4列：字段分类/字段名称/字段值/来源），表格内换行使用\\n转义，确保整个输出是合法JSON；\n3. pending_fields：待补充字段名称数组，无待补充时为空数组[]。",
    [inp("elements_json", "引用节点2要素JSON", ref_block=nid(2), ref_rel="elements_json"),
     inp("similarOfferList", "引用节点3相似产品列表", ref_block=nid(3), ref_rel="similarOfferList")],
    [out("plan_output", "执行方案总输出JSON字符串，含 plan_json/plan_md/pending_fields 三个键")], pos=(905, 300)))
# 004a 方案输出拆分：LLM节点4单出参 plan_output → 代码节点拆分为 plan_json/plan_md/pending_fields；req_id 由代码节点系统生成（PLAN+当前时刻+3位随机数，每次分析重新生成，保证唯一）
s1.append(code_node(41, "方案输出拆分", CODE_004A,
    [inp("plan_output", "引用节点4总输出", ref_block=nid(4), ref_rel="plan_output")],
    [code_out("plan_json", 41), code_out("plan_md", 41),
     code_out("pending_fields", 41), code_out("req_id", 41)],
    pos=(1170, 300)))
s1.append(selector_node2(5, "待补充项判断",
    [dep_node(41, "方案输出拆分", ["pending_fields"])],
    # 语义（port=-1 命中）：pending_fields 不为空（长度大于0）→ 有待补充项 → 补充提示结束；port=0 兜底（为空）→ 保存执行方案
    [(-1, [cond_item(cond_ref(41, "pending_fields", "方案输出拆分"), 3, cond_str("0"))])],
    pos=(1435, 300)))
s1.append(plugin_node(6, "保存执行方案", "save_node_result",
    "节点结果存储（复用）：req_id=入参 req_id（V1.7 统一键），node_name=requirement（执行方案环节），result_json=plan_json；同键覆盖",
    "save_node_result",
    [inp("req_id", "需求唯一标识=执行方案存储key（PLAN+yyyyMMddHHmmss+3位随机数）", ref_block=nid(41), ref_rel="req_id"),
     inp("node_name", "环节名=requirement（执行方案）", content="requirement"),
     inp("result_json", "本环节结果JSON=plan_json", ref_block=nid(41), ref_rel="plan_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(1705, 300)))
s1.append(end_node(7, "结束(有待补充项)",
    [inp("plan_md", "执行方案表格", ref_block=nid(41), ref_rel="plan_md"),
     inp("pending_fields", "待补充字段", ref_block=nid(41), ref_rel="pending_fields")],
    "《产销品加载执行方案》已生成（暂未保存）\n\n{plan_md}\n\n【待补充字段】{pending_fields}\n以上价格、资源类字段需求中未提取到，需由您补充后才能执行：\n- 请直接补充字段值，将更新执行方案并再次确认；\n- 如需调整其他字段：请直接说明修改意见（其余字段已按相似产品补全）。", pos=(1985, 120)))
s1.append(end_node(8, "结束(无待补充项)",
    [inp("req_id", "执行方案存储key", ref_block=nid(41), ref_rel="req_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(41), ref_rel="plan_md")],
    "《产销品加载执行方案》已生成并保存（req_id：{req_id}）\n\n{plan_md}\n\n【无待补充字段】全部字段已按相似产品补全，无需人工补充。\n请核对以上执行方案：\n- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节（每环节执行后打印结果，仅异常时中断）；\n- 如需调整：请直接说明修改意见。", pos=(1985, 480)))
e1 = [edge(1, 2), edge(2, 3), edge(3, 4), edge(4, 41), edge(41, 5),
      edge(5, 7, -1), edge(5, 6, 0), edge(6, 8)]
files["skill_01_需求分析与执行方案.json"] = skill(
    "产销品-需求分析技能", "Skill-1（主动）：需求分析与执行方案生成。需求理解→相似产品查询（自研模拟，18销售品种子）→字段映射与AI补全（LLM单出参plan_output，代码节点004a拆分）→待补充判断（无待补充→保存执行方案产出req_id；有待补充→补充提示结束）。",
    "skill_01", s1, e1,
    {"passive": False, "active": True, "triggers": ["上传需求文档", "口述需求", "生成执行方案", "修改执行方案"]},
    "接收需求文档或口述需求，生成《产销品加载执行方案》并存储（req_id）。补全规则：仅价格、资源类字段未提供填\"待补充\"（禁止推理），其余基于相似产品AI补全。")

# ============================================================
# skill_02 确认解析与门禁（被动）
# ============================================================
s2 = []
s2.append(start_node(101, [
    inp("req_id", "上下文中的执行方案存储key（必填，V1.7 统一键）", required=True),
    inp("user_reply", "用户回复文本（必填）", required=True),
]))
s2.append(llm_node(102, "确认意图解析",
    "你是产销品执行方案确认助手。解析用户对执行方案的回复意图：\n用户回复：{user_reply}\n三类意图判定：\n1. 确认（\"确认执行/同意/OK/开始吧\"等肯定语义）→ intent=confirm；\n2. 修改（\"把XX改成XX/价格调整为XX\"等含具体修改内容）→ intent=revise，并提炼修改意见 revise_opinion；\n3. 拒绝/其他（\"不用了/取消\"等否定或无关语义）→ intent=reject。\n输出要求（两个出参逐一约定）：\n1. intent：仅输出 confirm/revise/reject 三个词之一；\n2. revise_opinion：intent=revise 时仅输出提炼后的修改意见文本，其余情况输出空字符串；\n3. 不输出任何多余文字。",
    [inp("user_reply", "用户回复", ref_block=nid(101), ref_rel="user_reply")],
    [out("intent", "confirm/revise/reject"), out("revise_opinion", "修改意见（revise时非空）")], pos=(375, 300)))
s2.append(selector_node2(103, "意图分支",
    [dep_node(102, "确认意图解析", ["intent"])],
    # 语义（port=-1 命中）：intent == confirm → 写确认标记；port=0 兜底 → 非确认处理
    [(-1, [cond_item(cond_ref(102, "intent", "确认意图解析"), 1, cond_str("confirm"))])],
    pos=(640, 300)))
s2.append(plugin_node(104, "写入确认标记", "save_node_result",
    "确认标记写入（复用节点结果存储）：req_id=开始节点入参 req_id（V1.7 统一键，与执行方案存储同键），node_name=CONFIRMED，result_json={\"confirmed\":true,\"req_id\":...}；后端 save_product_config 硬校验此标记",
    "save_node_result",
    [inp("req_id", "执行方案存储key（与执行方案存储同键，V1.7 统一键）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "标记名=CONFIRMED", content="CONFIRMED"),
     inp("result_json", "确认标记JSON", content='{"confirmed":true}'),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(905, 120)))
s2.append(end_node(105, "结束(已确认)",
    [inp("req_id", "执行方案存储key", ref_block=nid(101), ref_rel="req_id")],
    "✅ 已确认执行方案（req_id：{req_id}），确认标记已写入存储。\n即将自动启动执行主干：智能配置→实时稽核→资费校准→自动测试（四环节串行、每环节打印结果、异常即停）。", pos=(1170, 120)))
s2.append(end_node(106, "结束(非确认)",
    [inp("intent", "解析意图", ref_block=nid(102), ref_rel="intent"),
     inp("revise_opinion", "修改意见", ref_block=nid(102), ref_rel="revise_opinion")],
    "意图：{intent}\n- revise：请核对修改意见「{revise_opinion}」，将重新生成执行方案并再次确认；\n- reject：已取消本次执行，方案已保留，可随时回复【确认执行】继续。", pos=(1170, 480)))
e2 = [edge(101, 102), edge(102, 103), edge(103, 104, -1), edge(104, 105), edge(103, 106, 0)]
files["skill_02_确认解析与门禁.json"] = skill(
    "产销品-方案确认技能", "Skill-2（被动）：确认解析与门禁。解析用户确认/修改/拒绝意图；确认→写CONFIRMED确认标记（与执行方案同键 req_id，后端硬校验）并触发执行主干；修改→带修改意见回到Skill-1；拒绝→结束。",
    "skill_02", s2, e2,
    {"passive": True, "active": False, "triggers": ["确认执行", "同意", "修改意见", "取消"]},
    "用户对执行方案回复确认/修改/拒绝时触发：确认→写入确认标记并启动执行主干；修改→重新分析；拒绝→结束。未确认绝不触发配置落地。")

# ============================================================
# skill_03 执行主干流水线（主动，核心：四阶段串行+存储回放续跑+统一异常出口）
# ============================================================
s3 = []
s3.append(start_node(201, [
    inp("req_id", "执行方案存储key（必填，V1.7 统一键：PLAN+yyyyMMddHHmmss+3位随机数，与执行方案/确认标记存储同键）", required=True),
    inp("resume_action", "续跑指令：retry_from_fail/空=首跑", required=False),
    inp("fail_node", "上次失败阶段编码：STAGE1_CONFIG/STAGE2_AUDIT/STAGE3_FEE/STAGE4_TEST", required=False),
]))
# 阶段1 智能配置
s3.append(plugin_node(202, "读取执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点入参 req_id（V1.7 统一键），node_name=requirement 取回执行方案JSON原文",
    "query_node_result",
    [inp("req_id", "需求唯一标识=开始节点入参 req_id", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "仅取最新一条=true", content="true")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "记录数", "string"), ("list", "结果列表（list[0].result_json=执行方案JSON原文）", "array", None)], pos=(375, 300)))
s3.append(plugin_node(203, "阶段1-智能配置", "save_product_config",
    "工具7：直读执行方案JSON原样透传落地（节点202→203之间禁止插入大模型/改写节点）；后端硬校验 req_id 的 CONFIRMED 标记，req_id 由后端从 plan_json 的 req_id 键提取",
    "save_product_config",
    [inp("req_id", "执行方案存储key（V1.7 统一键）", ref_block=nid(201), ref_rel="req_id"),
     inp("plan_json", "执行方案JSON原文（节点202查询出参list[0].result_json原样透传）", ref_block=nid(202), ref_rel="list"),
     inp("confirmed", "用户确认标志true（Skill-2确认门禁已写入存储）", content="true"),
     inp("operator", "操作人（默认system）", content="system")],
    [("product_id", "CRM产品ID", "string"), ("offer_id", "销售品ID", "string"),
     ("save_result", "四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL/NOT_CONFIRMED", "string")], pos=(640, 300)))
s3.append(selector_node2(204, "阶段1判定",
    [dep_node(203, "阶段1-智能配置", ["status"])],
    # 语义（port=-1 命中）：status != SUCCESS → 异常出口；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(203, "status", "阶段1-智能配置"), 2, cond_str("SUCCESS"))])],
    pos=(905, 300)))
s3.append(end_node(205, "阶段1成功打印",
    [inp("product_id", "产品ID", ref_block=nid(203), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(203), ref_rel="offer_id"),
     inp("save_result", "写入结果", ref_block=nid(203), ref_rel="save_result")],
    "【阶段1/智能配置】✅ 执行成功\n- 关键数据：product_id={product_id}，offer_id={offer_id}，四类字段写入结果：{save_result}\n- 已自动进入下一阶段……", pos=(1170, 120)))
# 阶段2 实时稽核
s3.append(plugin_node(206, "阶段2-实时稽核", "realtime_spec_audit",
    "工具2：自研模拟实时稽核，同步返回（无文件上传/无轮询）",
    "realtime_spec_audit",
    [inp("offer_id", "销售品ID", ref_block=nid(203), ref_rel="offer_id"),
     inp("config_json", "落地配置JSON（阶段1 save_result 原样引用）", ref_block=nid(203), ref_rel="save_result"),
     inp("audit_scene", "稽核场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("error_list", "问题明细（item/level/desc/suggest）", "array", ITEM_ERR),
     ("audit_summary", "稽核总结", "string"), ("resultCode", "0成功/NET_ERROR/TIMEOUT", "string")], pos=(1440, 300)))
s3.append(selector_node2(207, "阶段2判定",
    [dep_node(206, "阶段2-实时稽核", ["pass"])],
    # 语义（port=-1 命中）：pass != 1 → 异常出口；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(206, "pass", "阶段2-实时稽核"), 2, cond_str("1"))])],
    pos=(1705, 300)))
s3.append(end_node(208, "阶段2成功打印",
    [inp("audit_summary", "稽核总结", ref_block=nid(206), ref_rel="audit_summary")],
    "【阶段2/配置规格稽核】✅ 执行成功\n- 关键数据：稽核通过 + {audit_summary}\n- 已自动进入下一阶段……", pos=(1975, 120)))
# 阶段3 资费校准
s3.append(plugin_node(209, "阶段3-资费校准", "check_billing_rule",
    "工具8：自研模拟计费规则校验（内置叠加/互斥/负资费规则，适配18销售品资费结构）",
    "check_billing_rule",
    [inp("config_json", "落地配置JSON", ref_block=nid(203), ref_rel="save_result"),
     inp("check_scene", "校验场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("risk_list", "风险清单（risk_type/risk_desc/suggest）", "array", ITEM_RISK)], pos=(2245, 300)))
s3.append(selector_node2(210, "阶段3判定",
    [dep_node(209, "阶段3-资费校准", ["pass"])],
    # 语义（port=-1 命中）：pass != 1 → 异常出口；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(209, "pass", "阶段3-资费校准"), 2, cond_str("1"))])],
    pos=(2510, 300)))
s3.append(end_node(211, "阶段3成功打印",
    [],
    "【阶段3/资费校准】✅ 执行成功\n- 关键数据：资费校准通过，未发现叠加/互斥冲突\n- 已自动进入下一阶段……", pos=(2780, 120)))
# 阶段4 自动测试
s3.append(plugin_node(212, "阶段4-发起测试", "offer_test",
    "工具3：按销售品ID发起自动化测试（异步执行），返回测试流水globalId",
    "offer_test",
    [inp("offerId", "销售品ID", ref_block=nid(203), ref_rel="offer_id")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("globalId", "测试流水号 50+yyyyMMddHHmmss+10位随机数", "string")], pos=(3050, 300)))
s3.append(plugin_node(213, "查询测试场景", "get_test_scenes",
    "工具4：查询本次测试匹配的受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC）",
    "get_test_scenes",
    [inp("globalId", "测试流水号", ref_block=nid(212), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"),
     ("testScenes", "场景列表（testSceneId/testSceneName/testSceneNbr/testSceneDesc/sort）", "array", None)], pos=(3315, 300)))
s3.append(loop_node(214, "轮询测试进度",
    "工具5循环轮询：间隔5s，超时30分钟（360次），done==true 或 failed==true 退出",
    [inp("globalId", "测试流水号", ref_block=nid(212), ref_rel="globalId")],
    [out("done", "是否全部完成"), out("failed", "是否失败"), out("failIndex", "失败场景下标")], pos=(3580, 300)))
s3.append(plugin_node(215, "查询测试结果", "get_test_result",
    "工具6：测试全部完成后查询逐场景测点比对明细与AI总结；orderId/offerInstId为受理验证依据",
    "get_test_result",
    [inp("globalId", "测试流水号", ref_block=nid(212), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("testRequestId", "测试请求ID", "string"), ("testRequestName", "测试请求名称", "string"),
     ("offerName", "销售品名称", "string"), ("orderId", "受理订单号（受理验证依据）", "string"),
     ("offerInstId", "销售品实例ID（受理验证依据）", "string"),
     ("testScenes", "逐场景统计与测点明细（testSceneNbr/testCaseCount/successTestCaseCount/failTestCaseCount/testCasePointResults/objTestSceneRel）", "array", None)], pos=(3845, 300)))
s3.append(llm_node(216, "测试报告生成",
    "你是产销品自动测试报告生成助手。基于逐场景测试结果（testScenes={testScenes}）生成《销售品自动测试报告》，必须包含：1.测试概要（offerName/globalId/场景与测点统计）；2.受理验证结论（强制章节：orderId={orderId}、offerInstId={offerInstId}，为空则写明\"未获取到受理凭证，需人工核实\"；逐受理场景 S_O_TC/S_ADD_CARD/S_U_TC 给出通过/失败结论）；3.逐场景明细（仅展开resultCode=1不一致测点）；4.AI总结与建议（引用objTestSceneRel）；5.总体结论。\n输出要求（两个出参逐一约定）：\n1. test_report：完整测试报告文本（含上述5个章节，受理验证结论为强制章节）；\n2. test_passed：总体结论，取值\"通过\"或\"失败\"（仅输出这两个词之一）；\n3. 只基于输入数据生成，不得虚构测点或结论。",
    [inp("testScenes", "引用节点215逐场景结果", ref_block=nid(215), ref_rel="testScenes"),
     inp("orderId", "受理订单号", ref_block=nid(215), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID", ref_block=nid(215), ref_rel="offerInstId")],
    [out("test_report", "测试报告（含受理验证结论）"), out("test_passed", "通过/失败")], pos=(4110, 300)))
s3.append(selector_node2(217, "阶段4判定",
    [dep_node(216, "测试报告生成", ["test_passed"])],
    # 语义（port=-1 命中）：test_passed != 通过 → 异常出口；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(216, "test_passed", "测试报告生成"), 2, cond_str("通过"))])],
    pos=(4375, 300)))
s3.append(end_node(218, "阶段4成功打印",
    [inp("test_report", "测试报告", ref_block=nid(216), ref_rel="test_report")],
    "【阶段4/销售品自动测试（含受理验证）】✅ 执行成功\n- 关键数据：场景数/测点数统计 + 受理验证结论（orderId/offerInstId + 逐受理场景结论）\n{test_report}\n- 执行主干全部完成", pos=(4640, 120)))
# 主干完成判定 → 成功汇总
s3.append(selector_node2(219, "主干完成判定",
    [dep_node(203, "阶段1", ["status"]),
     dep_node(206, "阶段2", ["pass"]),
     dep_node(209, "阶段3", ["pass"]),
     dep_node(216, "阶段4", ["test_passed"])],
    # 语义（port=-1 命中任一不满足）：任一阶段未成功 → 异常出口；port=0 兜底 → 成功汇总
    [(-1, [cond_item(cond_ref(203, "status", "阶段1"), 2, cond_str("SUCCESS")),
           cond_item(cond_ref(206, "pass", "阶段2"), 2, cond_str("1")),
           cond_item(cond_ref(209, "pass", "阶段3"), 2, cond_str("1")),
           cond_item(cond_ref(216, "test_passed", "阶段4"), 2, cond_str("通过"))])],
    pos=(4905, 300)))
s3.append(llm_node(220, "成功结果详情汇总",
    "执行主干四个阶段全部成功，请基于以下输入按模板输出（逐字引用输入数据，不新增结论）：\n【阶段1落地结果】save_result={save_result}\n【稽核总结】audit_summary={audit_summary}\n【测试明细】test_report={test_report}\n\n输出模板：\n【执行主干全部完成】✅ 共4个阶段执行成功：\n1. 智能配置：product_id={product_id}，offer_id={offer_id}，四类字段全部写入成功；\n2. 配置规格稽核：通过，{audit_summary}；\n3. 资费校准：通过，未发现叠加/互斥冲突；\n4. 自动测试（含受理验证）：场景 N 个、测点 M 个全部一致；\n   受理验证：orderId={orderId}，offerInstId={offerInstId}，各受理场景均通过。\n\n是否发起上线审批？回复【发起审批】将汇总以上结果提交审批流；回复【暂不】可稍后发送\"发起审批\"继续。\n\n输出要求：仅输出按上述模板渲染的汇总内容（对应出参 stage_summary），不输出其他多余文字。",
    [inp("product_id", "产品ID", ref_block=nid(203), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(203), ref_rel="offer_id"),
     inp("audit_summary", "稽核总结", ref_block=nid(206), ref_rel="audit_summary"),
     inp("save_result", "落地结果", ref_block=nid(203), ref_rel="save_result"),
     inp("test_report", "测试报告", ref_block=nid(216), ref_rel="test_report"),
     inp("orderId", "受理订单号", ref_block=nid(215), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID", ref_block=nid(215), ref_rel="offerInstId")],
    [out("stage_summary", "成功结果详情汇总")], pos=(5170, 300)))
# 统一异常出口
s3.append(llm_node(221, "异常处置-统一异常出口",
    "执行主干在某一阶段异常中断，请生成异常处置说明：\n1.异常阶段名称（按 fail_node 映射：STAGE1_CONFIG 智能配置 / STAGE2_AUDIT 配置规格稽核 / STAGE3_FEE 资费校准 / STAGE4_TEST 销售品自动测试）；\n2.异常原因（引用接口返回原文 resultCode/resultMsg/pass=0，不得臆测）；\n3.关键明细（稽核问题清单/资费风险清单/测试失败测点/超时信息，按实际输入展开）；\n4.整改建议；\n5.结尾固定引导：\n请选择下一步：\n① 回复【重新执行】：将自动从失败阶段继续（已成功阶段不重复执行）\n② 回复【修改执行方案】：请说明修改意见，将重新生成执行方案并再次确认\n不得自行发起重试，不得跳过失败阶段。\nfail_node={fail_node}，异常阶段出参={exception_output}\n\n输出要求：仅输出按上述5点生成的异常处置说明（对应出参 exception_summary），不输出其他多余文字。",
    [inp("fail_node", "失败阶段编码", ref_block=nid(201), ref_rel="fail_node"),
     inp("exception_output", "失败阶段返回出参原文（异常判定节点引用）", required=False)],
    [out("exception_summary", "异常处置说明")], pos=(5170, 600)))
s3.append(end_node(222, "结束(异常中断)",
    [inp("exception_summary", "异常处置说明", ref_block=nid(221), ref_rel="exception_summary")],
    "{exception_summary}", pos=(5435, 600)))
e3 = [
    edge(201, 202), edge(202, 203), edge(203, 204),
    edge(204, 205, 0), edge(204, 221, -1),   # 阶段1失败 → 异常出口
    edge(205, 206), edge(206, 207),
    edge(207, 208, 0), edge(207, 221, -1),   # 阶段2失败 → 异常出口
    edge(208, 209), edge(209, 210),
    edge(210, 211, 0), edge(210, 221, -1),   # 阶段3失败 → 异常出口
    edge(211, 212), edge(212, 213), edge(213, 214), edge(214, 215), edge(215, 216), edge(216, 217),
    edge(217, 218, 0), edge(217, 221, -1),   # 阶段4失败 → 异常出口
    edge(218, 219),
    edge(219, 220, 0), edge(219, 221, -1),   # 主干判定不通过 → 异常出口
    edge(221, 222),
]
files["skill_03_执行主干流水线.json"] = skill(
    "产销品-执行主干技能", "Skill-3（主动，核心）：执行主干流水线。读取执行JSON→四阶段自动串行（智能配置→实时稽核→资费校准→自动测试含受理验证），每阶段打印结果、异常即停走统一异常出口（引导重新执行/修改执行方案）；续跑=携resume_action/fail_node重入，已成功阶段按节点结果存储回放不重复调用写接口。",
    "skill_03", s3, e3,
    {"passive": False, "active": True, "triggers": ["确认执行后自动触发", "重新执行"]},
    "用户确认后自动串行执行四阶段：智能配置→实时稽核→资费校准→自动测试（含受理验证）。每阶段打印结果，异常即停并引导重新执行/修改执行方案；续跑时已成功阶段由存储回放不重复执行。")

# ============================================================
# skill_04 上线审批发起（主动）
# ============================================================
s4 = []
s4.append(start_node(301, [
    inp("req_id", "执行方案存储key（必填，V1.7 统一键，回放各阶段结果用）", required=True),
]))
s4.append(llm_node(302, "报告汇总",
    "请基于以下输入数据，按标准模板汇总生成《销售品上线测试与稽核报告》：\n【配置落地结果】product_id={product_id}\n【各阶段结果】req_id={req_id}（节点结果存储 req_id=入参 req_id 回放 config/spec/fee/test 四条记录）\n报告必须包含以下章节：1.需求摘要与执行方案要点；2.配置落地结果；3.稽核结论；4.资费结论；5.测试统计与失败明细；6.受理验证结论（强制章节，逐受理场景给出通过/失败结论）；7.上线建议。只基于输入数据生成，不得新增结论。\n输出要求：仅输出报告内容（对应出参 report），不输出其他多余文字。",
    [inp("product_id", "产品ID", ref_block=nid(301), ref_rel="product_id"),
     inp("req_id", "执行方案存储key", ref_block=nid(301), ref_rel="req_id")],
    [out("report", "上线报告")], pos=(375, 300)))
s4.append(plugin_node(303, "审批推送", "submit_release_approval",
    "工具9：自研模拟审批推送；后端硬校验 req_id 四环节（config/spec/fee/test）结果齐全（未走完执行主干返回NOT_CONFIRMED）+approve_confirmed==true；幂等：同product_id返回原approval_id",
    "submit_release_approval",
    [inp("req_id", "执行方案存储key（V1.7 统一键，四环节门禁校验依据）", ref_block=nid(301), ref_rel="req_id"),
     inp("product_id", "CRM产品ID", ref_block=nid(301), ref_rel="product_id"),
     inp("report_url", "上线报告", ref_block=nid(302), ref_rel="report"),
     inp("approve_confirmed", "审批发起确认标志true（智能体确认门禁保证）", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string")], pos=(640, 300)))
s4.append(end_node(304, "结束(审批已推送)",
    [inp("approval_id", "审批单号", ref_block=nid(303), ref_rel="approval_id"),
     inp("status", "提交状态", ref_block=nid(303), ref_rel="status")],
    "上线审批已推送：approval_id={approval_id}，status={status}\n可随时发送\"查询审批进度\"消息查询审批状态。", pos=(905, 300)))
e4 = [edge(301, 302), edge(302, 303), edge(303, 304)]
files["skill_04_上线审批发起.json"] = skill(
    "产销品-上线审批技能", "Skill-4（主动）：上线审批发起。执行主干全部成功且用户确认后触发：报告汇总（强制含受理验证结论）→submit_release_approval推送（插件层二次校验approve_confirmed+幂等）。",
    "skill_04", s4, e4,
    {"passive": False, "active": True, "triggers": ["发起审批", "确认上线"]},
    "执行主干全部成功且用户明确确认后，汇总报告（含受理验证结论）并推送审批流，输出审批单号。不得自动发起。")

# ============================================================
# skill_05 审批进度查询（被动）
# ============================================================
s5 = []
s5.append(start_node(401, [
    inp("approval_id", "审批单号（与product_id至少一个非空）", required=False),
    inp("product_id", "产品ID（与approval_id至少一个非空）", required=False),
]))
s5.append(plugin_node(402, "审批状态查询", "query_approval_status",
    "工具13：自研模拟审批进度查询（从模拟审批状态库查询）",
    "query_approval_status",
    [inp("approval_id", "审批单号", ref_block=nid(401), ref_rel="approval_id"),
     inp("product_id", "产品ID", ref_block=nid(401), ref_rel="product_id")],
    [("approval_id", "审批单号", "string"), ("status", "审批中/通过/驳回", "string"),
     ("current_node", "当前审批环节", "string"), ("approver", "当前审批人", "string"),
     ("opinion", "审批意见", "string"), ("submit_time", "提交时间", "string"),
     ("update_time", "更新时间", "string")], method="get", pos=(375, 300)))
s5.append(llm_node(403, "状态摘要归纳",
    "按'审批单号 {approval_id}｜状态：{status}｜当前环节：{current_node}（审批人 {approver}）｜最近意见：{opinion}｜更新时间：{update_time}'格式输出；status=驳回 时附驳回原因并提示可修改执行方案后重新发起。查无审批单时输出\"未找到该销售品的审批单，请确认是否已发起审批\"。\n输出要求：仅输出审批状态摘要内容（对应出参 approval_summary），不输出其他多余文字。",
    [inp("approval_id", "审批单号", ref_block=nid(402), ref_rel="approval_id"),
     inp("status", "审批状态", ref_block=nid(402), ref_rel="status"),
     inp("current_node", "当前环节", ref_block=nid(402), ref_rel="current_node"),
     inp("approver", "审批人", ref_block=nid(402), ref_rel="approver"),
     inp("opinion", "审批意见", ref_block=nid(402), ref_rel="opinion"),
     inp("update_time", "更新时间", ref_block=nid(402), ref_rel="update_time")],
    [out("approval_summary", "审批状态摘要")], pos=(640, 300)))
s5.append(end_node(404, "结束(查询完成)",
    [inp("approval_summary", "审批状态摘要", ref_block=nid(403), ref_rel="approval_summary")],
    "{approval_summary}", pos=(905, 300)))
e5 = [edge(401, 402), edge(402, 403), edge(403, 404)]
files["skill_05_审批进度查询.json"] = skill(
    "产销品-审批进度查询技能", "Skill-5（被动）：审批进度查询。消息含\"审批进度/审批状态\"触发：query_approval_status（approval_id优先，缺失按product_id查最新）→状态摘要归纳。",
    "skill_05", s5, e5,
    {"passive": True, "active": False, "triggers": ["审批进度", "审批状态", "审批到哪了"]},
    "用户发送\"查询审批进度\"等消息即查：返回审批单状态（审批中/通过/驳回）、当前审批环节与意见。")

# ============================================================
# skill_06 监控运维与告警（主动+被动双入口）
# ============================================================
s6 = []
s6.append(start_node(501, [
    inp("product_id", "销售品ID（必填，缺失时智能体反问补齐）", required=True),
    inp("date_range", "日期范围，默认最近1天", required=False),
]))
s6.append(plugin_node(502, "监控查询", "query_product_monitor",
    "工具10：自研模拟监控查询（订单量/异常量/计费差错率/告警列表）",
    "query_product_monitor",
    [inp("product_id", "销售品ID", ref_block=nid(501), ref_rel="product_id"),
     inp("date_range", "日期范围", ref_block=nid(501), ref_rel="date_range"),
     inp("metric", "指标默认all", content="all")],
    [("order_count", "订单量", "string"), ("error_count", "异常量", "string"),
     ("fee_error_rate", "计费差错率", "string"), ("alarm_list", "告警列表（alarm_id/alarm_level/content/alarm_time）", "array", ITEM_ALARM)],
    method="get", pos=(375, 300)))
s6.append(selector_node2(503, "异常判定",
    [dep_node(502, "监控查询", ["error_count", "fee_error_rate"])],
    # 语义（port=-1 命中）：error_count 不等于"0"（存在异常）→ 告警；port=0 兜底（等于"0"正常）→ 正常摘要
    [(-1, [cond_item(cond_ref(502, "error_count", "监控查询"), 2, cond_str("0"))])],
    pos=(640, 300)))
s6.append(llm_node(504, "告警文案生成",
    "基于监控异常数据生成告警文案（含产品、异常摘要、建议）。输入：product_id={product_id}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n输出要求：仅输出告警文案内容（对应出参 alert_content，需包含产品名称、异常摘要、处置建议三部分），不输出其他多余文字。",
    [inp("product_id", "销售品ID", ref_block=nid(501), ref_rel="product_id"),
     inp("order_count", "订单量", ref_block=nid(502), ref_rel="order_count"),
     inp("error_count", "异常量", ref_block=nid(502), ref_rel="error_count"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(502), ref_rel="fee_error_rate"),
     inp("alarm_list", "告警列表", ref_block=nid(502), ref_rel="alarm_list")],
    [out("alert_content", "告警文案")], pos=(905, 120)))
s6.append(plugin_node(505, "异常告警", "send_alert",
    "工具11：自研模拟告警推送（生成alert_id，记录写入模拟库供监控回显）",
    "send_alert",
    [inp("product_id", "销售品ID", ref_block=nid(501), ref_rel="product_id"),
     inp("alarm_level", "告警级别", content="high"),
     inp("content", "告警文案（节点504输出）", ref_block=nid(504), ref_rel="alert_content")],
    [("alert_id", "告警单号", "string"), ("status", "推送状态", "string")], pos=(1170, 120)))
s6.append(end_node(506, "结束(异常-已告警)",
    [inp("alert_id", "告警单号", ref_block=nid(505), ref_rel="alert_id"),
     inp("alert_content", "告警文案", ref_block=nid(504), ref_rel="alert_content")],
    "监控发现异常，已推送告警：alert_id={alert_id}\n{alert_content}"),)
s6.append(end_node(507, "结束(正常)",
    [inp("order_count", "订单量", ref_block=nid(502), ref_rel="order_count"),
     inp("error_count", "异常量", ref_block=nid(502), ref_rel="error_count"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(502), ref_rel="fee_error_rate")],
    "监控正常：订单量={order_count}，异常量={error_count}，计费差错率={fee_error_rate}，无需告警。"),)
e6 = [edge(501, 502), edge(502, 503), edge(503, 504, -1), edge(504, 505), edge(505, 506), edge(503, 507, 0)]
files["skill_06_监控运维与告警.json"] = skill(
    "产销品-监控运维技能", "Skill-6（主动+被动双入口）：监控运维与告警。query_product_monitor（自研模拟）→异常判定（error_count!=0）→send_alert告警/正常摘要。支持对话触发与每日定时触发。",
    "skill_06", s6, e6,
    {"passive": True, "active": True, "triggers": ["监控结果", "运行监控", "定时触发"]},
    "查询销售品上线后监控指标（订单量/异常量/计费差错率/告警列表）；异常时自动send_alert告警。支持消息查询与定时触发双入口。")

# ============================================================
# 智能体配置（Skills 版）
# ============================================================
agent = {
    "agentCode": "cpcp_product_worker",
    "agentName": "产销品数字员工（Skills版）",
    "description": "面向产销品域的数字员工（Skills封装实现）：以6个技能替代主/子工作流编排——需求分析（Skill-1）、确认门禁（Skill-2被动）、执行主干流水线（Skill-3，四阶段串行）、上线审批（Skill-4）、审批进度查询（Skill-5被动）、监控运维（Skill-6双入口）。业务目标、插件清单、知识库与工作流版一致。",
    "publishScope": "所有人可见",
    "icon": "default",
    "skills": [
        {"skillId": "skill_01", "name": "需求分析与执行方案", "type": "active",
         "tools": ["query_similar_offer", "save_node_result"],
         "knowledge": ["K4存量销售品资料库", "K1业务规范库"]},
        {"skillId": "skill_02", "name": "确认解析与门禁", "type": "passive",
         "trigger": ["确认执行", "修改意见", "取消"],
         "tools": ["save_node_result"],
         "nextSkill": "skill_03"},
        {"skillId": "skill_03", "name": "执行主干流水线", "type": "active",
         "tools": ["query_node_result", "save_node_result", "save_product_config",
                   "realtime_spec_audit", "check_billing_rule", "offer_test",
                   "get_test_scenes", "get_test_progress", "get_test_result"],
         "nextSkill": "skill_04"},
        {"skillId": "skill_04", "name": "上线审批发起", "type": "active",
         "tools": ["submit_release_approval"],
         "gates": ["all_passed==true", "approve_confirmed==true"]},
        {"skillId": "skill_05", "name": "审批进度查询", "type": "passive",
         "trigger": ["审批进度", "审批状态"],
         "tools": ["query_approval_status"]},
        {"skillId": "skill_06", "name": "监控运维与告警", "type": "active_passive",
         "trigger": ["监控结果", "运行监控", "定时触发"],
         "tools": ["query_product_monitor", "send_alert"]},
    ],
    "modelConfig": {"temperature": 0.2, "top_p": 0.5, "maxRounds": 20},
    "faqDirectReturn": False,
    "openingRemark": "您好，我是产销品数字员工，可协助您完成销售品从需求提报、需求分析（生成执行方案）、确认后智能配置、稽核校准、自动测试到上线审批、监控运维的全流程。请上传需求文档或直接描述需求，我将为您生成《产销品加载执行方案》。",
    "guideQuestions": [
        "我要上新一个 5G 流量套餐，请帮我分析需求并生成执行方案",
        "确认执行刚才的产销品加载方案",
        "查询一下刚才那个销售品的审批进度",
        "查询销售品 900102308 的运行监控结果",
        "重新执行失败的环节",
    ],
    "emptyAnswerTip": "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。",
    "systemPrompt": "【角色】\n你是安徽电信产销品域的数字员工，精通 CPCP 产销品管理、CRM 配置、计费规则、\n订单受理与测试验证。你通过 6 个技能完成销售品从需求到上线的端到端自动化加载。\n你不直接操作CRM，不直接修改配置，不代替用户进行最终业务决策。\n\n【技能】\n1. 需求分析（skill_01）：接收需求文档或口述需求，生成《产销品加载执行方案》。\n   补全规则：仅价格、资源类字段未提供填\"待补充\"（禁止推理）；其余缺失字段基于\n   相似产品推理补全，标记\"AI补全\"。输出表格 + req_id（存储key）。\n2. 方案确认（skill_02）：用户对执行方案回复确认/修改/拒绝时，解析意图：\n   确认→写入确认标记并自动启动执行主干；修改→带修改意见重新分析；拒绝→结束。\n   未确认绝不触发配置落地。\n3. 执行主干（skill_03）：确认后自动串行执行 智能配置→实时稽核→资费校准→自动测试\n   四阶段，中途不停顿，每阶段打印结果；任一阶段异常即中断，打印异常阶段+原因+\n   明细+建议，引导【重新执行】（从失败阶段续跑）或【修改执行方案】（回到需求分析）。\n4. 审批发起（skill_04）：四阶段全部成功后提示\"是否发起上线审批\"，用户确认后汇总\n   报告（含受理验证结论）并推送审批，输出审批单号。不得自动发起。\n5. 审批进度查询（skill_05）：用户发送\"查询审批进度\"等消息即查，返回审批单状态、\n   当前环节与意见。\n6. 监控运维（skill_06）：用户发送\"查询监控结果\"即查订单量/异常量/计费差错率/告警；\n   异常时自动 send_alert 告警。\n\n【限制】\n1. 仅回答产销品加载相关业务，其他问题按答案为空提示回复。\n2. 配置落地前必须存在已写入存储的确认标记；执行方案以存储版本为准，配置时\n   不得重新生成。\n3. 字段来源仅\"原始需求/AI补全\"；\"待补充\"仅限价格、资源两类字段。\n4. 稽核/资费/测试不通过时不得跳过环节或自行重试，须输出异常详情并引导用户选择。\n5. 上线审批须执行主干全部成功且用户明确确认后才能发起。\n6. 输出遵循结构化格式：环节（阶段）名称、执行结果、关键数据、下一步动作。\n7. 不得泄露资费、配置等敏感数据明细，仅展示摘要。"
}
files["agent_cpcp_product_worker_skills.json"] = agent

# ============================================================
# 简化布局：按边拓扑分层（对齐 gen_workflows.py 的 Sugiyama 简化版）
# ============================================================
COL_GAP = 265
ROW_GAP = 220
MAIN_Y = 300


def apply_layout(data):
    if "workFlowSchemaJSON" not in data:
        return data
    nodes = data["workFlowSchemaJSON"]["nodes"]
    edges = data["workFlowSchemaJSON"]["edges"]
    by_id = {n["id"]: n for n in nodes}
    succ = {n["id"]: [] for n in nodes}
    indeg = {n["id"]: 0 for n in nodes}
    for e in edges:
        s, t = e["startId"], e["endId"]
        if s in by_id and t in by_id and t not in succ[s]:
            succ[s].append(t)
            indeg[t] += 1
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
    for n in nodes:
        if n["id"] not in col:
            col[n["id"]] = 0
    dist = {}
    for u in order:
        for v in succ[u]:
            d = dist.get(u, col[u]) + 1
            if d > dist.get(v, -1):
                dist[v] = d
    preds = {v: [] for v in by_id}
    for e in edges:
        if e["startId"] in by_id and e["endId"] in by_id:
            preds[e["endId"]].append(e["startId"])
    ends = [u for u in by_id if not succ[u]]
    end_main = max(ends, key=lambda u: dist.get(u, 0)) if ends else None
    main_path = set()
    u = end_main
    while u is not None:
        main_path.add(u)
        cand = [p for p in preds[u] if col.get(p, 0) == col.get(u, 0) - 1]
        u = max(cand, key=lambda p: dist.get(p, 0)) if cand else None
    cols = {}
    for n in nodes:
        cols.setdefault(col[n["id"]], []).append(n)
    for c, ns in cols.items():
        mains = [n for n in ns if n["id"] in main_path]
        others = [n for n in ns if n["id"] not in main_path]
        half = len(others) // 2
        arranged = list(reversed(others[half:])) + mains + others[:half]
        for i, n in enumerate(arranged):
            dy = (i - (len(arranged) - 1) / 2.0) * ROW_GAP
            n["position"] = {"x": 15 + c * COL_GAP, "y": int(MAIN_Y + dy)}
    return data


for fn, data in files.items():
    data = apply_layout(data)
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", fn)

print("total:", len(files))
