# -*- coding: utf-8 -*-
# 生成产销品加载AI应用 V1.6 工作流导出JSON（1主工作流 + 8子工作流）
# 对齐《产销品加载AI应用开发方案.md》V1.6 / 细化设计方案 V1.2：
#  - 主工作流：两次中断（结束节点A 执行方案确认 / 节点14 审批发起确认）、执行主干四环节自动串行、统一异常处置节点（异常A）、续跑判定（resume_action/fail_node/execution_id）
#  - 子工作流：按细化设计 3.2 节逐节点
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
        "outputs": [{"name": n, "cname": d, "sechema": [], "type": t, "required": False}
                    for (n, d, t) in outputs],
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
    "你是产销品加载需求分析助手。按6步分析：理解需求→提取并拆解业务要素（基础信息/资源配置/营销资源/销售规则四类）→识别信息完整性（字段三态：原始需求/AI补全/待补充）。需求原文：{requirement_text}\n"
    "补充规则（V1.4）：仅价格、资源两类字段未提供时填\"待补充\"（禁止推理，禁止从相似产品照搬）；其余缺失字段待相似产品返回后推理补全，来源标记\"AI补全\"；来源只允许\"原始需求\"或\"AI补全\"两种。\n"
    "输出要求（两个出参逐一约定）：\n"
    "1. elements_json：输出结构化要素JSON（临时变量，不落存储），包含 fields 数组与 pending_fields 数组；\n"
    "2. need_summary：输出需求摘要（≤5000字符，供相似度分析调用使用）；\n"
    "3. 除上述两个出参内容外不输出任何多余文字。",
    [inp("requirement_text", "引用开始节点 requirement_text", ref_block=nid(1), ref_rel="requirement_text")],
    [out("elements_json", "业务要素结构化JSON"), out("need_summary", "需求摘要（>5000字符时供相似度分析用）")]))
s1.append(plugin_node(3, "相似产品查询", "query_similar_offer",
    "工具1：以《产品信息.txt》全部18个销售品为相似产品库查询相似销售品",
    BASE_URL + "/api/v1/appstore/similar/offer/query",
    [inp("businessDesc", "业务需求描述（引用节点2需求摘要，≤5000字符）", ref_block=nid(2), ref_rel="need_summary")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("similarOfferList", "相似产品列表", "string")]))
s1.append(llm_node(4, "字段映射与补全",
    "基于节点2要素JSON（elements_json={elements_json}）+ 节点3相似产品列表（similarOfferList={similarOfferList}，含《产品信息.txt》18个销售品规则）+ 知识库存量销售品资料，生成《产销品加载执行方案》。\n"
    "补全规则：价格、资源类字段未提供→\"待补充\"列入pending_fields；其余缺失字段取最高相似度产品对应值，来源\"AI补全\"。\n"
    "输出要求（四个出参逐一约定）：\n"
    "1. plan_json：执行方案JSON，含 plan_id/fields/similar_offers/pending_fields；\n"
    "2. plan_md：执行方案Markdown表格，固定4列：字段分类/字段名称/字段值/来源；\n"
    "3. pending_fields：待补充字段清单，逗号分隔；\n"
    "4. plan_id：执行方案存储key，格式 PLAN+yyyyMMdd+3位序号，修改场景沿用原值覆盖写；\n"
    "5. 除上述四个出参内容外不输出任何多余文字。",
    [inp("elements_json", "引用节点2要素JSON", ref_block=nid(2), ref_rel="elements_json"),
     inp("similarOfferList", "引用节点3相似产品列表", ref_block=nid(3), ref_rel="similarOfferList")],
    [out("plan_json", "执行方案JSON"), out("plan_md", "执行方案Markdown表格"),
     out("pending_fields", "待补充字段清单逗号分隔"), out("plan_id", "执行方案存储key")]))
s1.append(selector_node2(5, "待补充项判断",
    [dep_node(4, "字段映射与补全", ["pending_fields"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义：pending_fields 不为空（长度大于0）→ 有待补充项 → 保存执行方案 → 确认结束；
    #       port=0 兜底分支（pending_fields 为空）→ 直接结束提示，不保存（禁止进入智能配置）
    [(-1, [cond_item(cond_ref(4, "pending_fields", "字段映射与补全"), 10, cond_str(""))])]))
s1.append(plugin_node(6, "保存执行方案", "save_node_result",
    "节点结果存储（复用）：req_id=plan_id，node_name=requirement（执行方案环节），result_json=plan_json；同键覆盖",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求唯一标识=plan_id（PLAN+yyyyMMdd+3位序号）", ref_block=nid(4), ref_rel="plan_id"),
     inp("node_name", "环节名=requirement（执行方案）", content="requirement"),
     inp("result_json", "本环节结果JSON=plan_json", ref_block=nid(4), ref_rel="plan_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")]))
s1.append(end_node(7, "结束(有待补充项)",
    [inp("plan_md", "执行方案表格", ref_block=nid(4), ref_rel="plan_md"),
     inp("pending_fields", "待补充字段", ref_block=nid(4), ref_rel="pending_fields")],
    "《产销品加载执行方案》已生成（plan_id：{plan_id}）\n\n{plan_md}\n\n【待补充字段】{pending_fields}\n以上价格、资源类字段需由您补充后才能执行：\n- 请直接补充字段值，将更新执行方案并再次确认；\n- 如需调整其他字段：请直接说明修改意见（其余字段已按相似产品补全）。"))
s1.append(end_node(8, "结束(无待补充项)",
    [inp("plan_id", "执行方案存储key", ref_block=nid(4), ref_rel="plan_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(4), ref_rel="plan_md")],
    "《产销品加载执行方案》已生成并保存（plan_id：{plan_id}）\n\n{plan_md}\n\n【无待补充字段】全部字段已按相似产品补全，无需人工补充。\n请核对以上执行方案：\n- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节（每环节执行后打印结果，仅异常时中断）；\n- 如需调整：请直接说明修改意见。"))
e1 = [edge(1,2), edge(2,3), edge(3,4), edge(4,5),
      edge(5,6,0), edge(5,7,-1), edge(6,8)]
files["wf_sub_01_需求分析.json"] = workflow(
    "产销品-需求分析", "子工作流1：需求分析（执行方案生成）。需求理解→相似产品查询（自研模拟，18销售品种子）→字段映射与AI补全（价格/资源待补充，其余AI补全）→待补充项判断（无待补充→保存执行方案→确认结束；有待补充→补充提示结束）。", "wf_sub_01", s1, e1)

# ============================================================
# wf_sub_02 智能配置（配置落地）
# ============================================================
s2 = []
s2.append(start_node(101, [inp("plan_id", "已确认的执行方案存储key", required=True)]))
s2.append(plugin_node(102, "读取执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=plan_id，node_name=requirement 取回执行方案JSON原文（list[0].result_json）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "需求唯一标识=plan_id", ref_block=nid(101), ref_rel="plan_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"),
     ("total", "命中记录数", "string"), ("list", "记录数组JSON（取[0].result_json为执行方案原文）", "string")],
    method="get"))
s2.append(plugin_node(103, "配置落地", "save_product_config",
    "工具7：直读执行方案JSON原样透传落地（节点2→节点3之间禁止插入大模型/改写节点）；内部二次校验confirmed",
    BASE_URL + "/api/v1/appstore/product/config/save",
    [inp("plan_id", "执行方案key", ref_block=nid(101), ref_rel="plan_id"),
     inp("plan_json", "执行方案JSON原文（节点2查询出参list[0].result_json原样透传）", ref_block=nid(102), ref_rel="list"),
     inp("confirmed", "用户确认标志true（主流程确认门禁已保证）", content="true"),
     inp("operator", "操作人（默认system）", content="system")],
    [("product_id", "CRM产品ID", "string"), ("offer_id", "销售品ID", "string"),
     ("save_result", "四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL/NOT_CONFIRMED", "string")]))
s2.append(end_node(104, "结束(配置落地完成)",
    [inp("product_id", "CRM产品ID", ref_block=nid(103), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(103), ref_rel="offer_id"),
     inp("save_result", "四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "落地状态", ref_block=nid(103), ref_rel="status")],
    "智能配置完成：product_id={product_id}，offer_id={offer_id}\n四类字段写入结果：{save_result}\n状态：{status}"))
files["wf_sub_02_智能配置.json"] = workflow(
    "产销品-智能配置", "子工作流2：智能配置（配置落地）。节点结果查询读取执行方案JSON→save_product_config原样透传落地，中间无大模型节点；内部二次校验confirmed。", "wf_sub_02", s2,
    [edge(101,102), edge(102,103), edge(103,104)])

# ============================================================
# wf_sub_03 规格稽核（实时）
# ============================================================
s3 = []
s3.append(start_node(201, [
    inp("offer_id", "配置落地返回的销售品ID", required=True),
    inp("config_json", "落地配置JSON原文", required=True),
]))
s3.append(plugin_node(202, "实时稽核", "realtime_spec_audit",
    "工具2：自研模拟实时稽核，同步返回（无文件上传/无轮询）",
    BASE_URL + "/api/v1/appstore/audit/realtime",
    [inp("offer_id", "销售品ID", ref_block=nid(201), ref_rel="offer_id"),
     inp("config_json", "落地配置JSON", ref_block=nid(201), ref_rel="config_json"),
     inp("audit_scene", "稽核场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("error_list", "问题明细", "string"),
     ("audit_summary", "稽核总结", "string"), ("resultCode", "0成功/NET_ERROR/TIMEOUT", "string")]))
s3.append(llm_node(203, "整改建议生成",
    "将稽核问题明细整理为可执行的整改建议清单（error_list={error_list}，audit_summary={audit_summary}），按严重级别排序；pass=1 时输出\"稽核通过\"。不新增稽核结论。\n"
    "输出要求：仅输出整改建议清单内容（对应出参 audit_suggest），不输出其他多余文字。",
    [inp("error_list", "引用节点2问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_summary", "引用节点2稽核总结", ref_block=nid(202), ref_rel="audit_summary")],
    [out("audit_suggest", "整改建议清单")]))
s3.append(end_node(204, "结束(稽核完成)",
    [inp("pass", "稽核结论", ref_block=nid(202), ref_rel="pass"),
     inp("error_list", "问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_suggest", "整改建议", ref_block=nid(203), ref_rel="audit_suggest")],
    "配置规格稽核完成：pass={pass}\n{audit_suggest}"))
files["wf_sub_03_规格稽核.json"] = workflow(
    "产销品-规格稽核", "子工作流3：规格稽核（实时）。realtime_spec_audit同步返回→整改建议生成（温度0.2）。", "wf_sub_03", s3,
    [edge(201,202), edge(202,203), edge(203,204)])

# ============================================================
# wf_sub_04 自动测试（含受理验证）
# ============================================================
s4 = []
s4.append(start_node(301, [inp("offer_id", "被测销售品ID", required=True)]))
s4.append(plugin_node(302, "发起测试", "offer_test",
    "工具3：自研模拟测试发起，返回模拟测试流水globalId",
    BASE_URL + "/api/v1/appstore/test/offer/start",
    [inp("offerId", "销售品ID", ref_block=nid(301), ref_rel="offer_id")],
    [("resultCode", "0成功/1失败", "string"), ("resultMsg", "处理结果描述", "string"),
     ("globalId", "测试流水号", "string")]))
s4.append(plugin_node(303, "查询测试场景", "get_test_scenes",
    "工具4：查询受理验证覆盖范围（套餐新装/副卡加装/套餐退订）",
    BASE_URL + "/api/v1/appstore/test/offer/scenes",
    [inp("globalId", "测试流水号（节点2出参）", ref_block=nid(302), ref_rel="globalId")],
    [("resultCode", "0成功/1失败", "string"), ("testScenes", "场景列表", "string")]))
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
     ("offerInstId", "销售品实例ID", "string"), ("testScenes", "逐场景结果含测点明细", "string")]))
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
s4.append(end_node(307, "结束(测试完成)",
    [inp("test_report", "测试报告", ref_block=nid(306), ref_rel="test_report"),
     inp("test_passed", "总体结论", ref_block=nid(306), ref_rel="test_passed"),
     inp("globalId", "测试流水号", ref_block=nid(302), ref_rel="globalId")],
    "销售品自动测试完成（含受理验证）：\n{test_report}"))
files["wf_sub_04_自动测试.json"] = workflow(
    "产销品-自动测试", "子工作流4：自动测试（含受理验证）。offer_test发起→get_test_scenes→循环get_test_progress（5s/30min）→get_test_result→测试报告生成（强制含受理验证结论orderId/offerInstId）。", "wf_sub_04", s4,
    [edge(301,302), edge(302,303), edge(303,304), edge(304,305), edge(305,306), edge(306,307)])

# ============================================================
# wf_sub_05 资费校准
# ============================================================
s5 = []
s5.append(start_node(401, [inp("config_json", "落地配置JSON原文", required=True)]))
s5.append(plugin_node(402, "计费校验", "check_billing_rule",
    "工具8：自研模拟计费规则校验（内置叠加/互斥/负资费规则，适配18销售品资费结构）",
    BASE_URL + "/api/v1/appstore/billing/rules/verify",
    [inp("config_json", "落地配置JSON", ref_block=nid(401), ref_rel="config_json"),
     inp("check_scene", "校验场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("risk_list", "风险清单", "string")]))
s5.append(llm_node(403, "风险解读",
    "将资费风险清单（risk_list={risk_list}）翻译为业务语言，说明每条风险的影响与建议；risk_list 为空时输出\"资费校准通过，未发现叠加/互斥冲突\"。可引用资费规则库知识作为解释依据，但不得新增风险结论。\n"
    "输出要求：仅输出风险解读内容（对应出参 risk_summary），不输出其他多余文字。",
    [inp("risk_list", "引用节点2风险清单", ref_block=nid(402), ref_rel="risk_list")],
    [out("risk_summary", "风险解读")]))
s5.append(end_node(404, "结束(资费校准完成)",
    [inp("pass", "校验结论", ref_block=nid(402), ref_rel="pass"),
     inp("risk_list", "风险清单", ref_block=nid(402), ref_rel="risk_list"),
     inp("risk_summary", "风险解读", ref_block=nid(403), ref_rel="risk_summary")],
    "资费校准完成：pass={pass}\n{risk_summary}"))
files["wf_sub_05_资费校准.json"] = workflow(
    "产销品-资费校准", "子工作流5：资费校准。check_billing_rule（自研模拟，check_scene=all）→风险解读（温度0.2，引用资费规则库知识）。", "wf_sub_05", s5,
    [edge(401,402), edge(402,403), edge(403,404)])

# ============================================================
# wf_sub_06 上线审批
# ============================================================
s6 = []
s6.append(start_node(501, [
    inp("product_id", "CRM产品ID", required=True),
    inp("report", "上线报告（主流程节点16输出）", required=True),
]))
s6.append(plugin_node(502, "审批推送", "submit_release_approval",
    "工具9：自研模拟审批推送；插件层校验approve_confirmed==true（未经确认返回NOT_CONFIRMED）；幂等：同product_id返回原approval_id",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("product_id", "CRM产品ID", ref_block=nid(501), ref_rel="product_id"),
     inp("report_url", "上线报告", ref_block=nid(501), ref_rel="report"),
     inp("approve_confirmed", "审批发起确认标志true", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string")]))
s6.append(end_node(503, "结束(审批已推送)",
    [inp("approval_id", "审批单号", ref_block=nid(502), ref_rel="approval_id"),
     inp("status", "提交状态", ref_block=nid(502), ref_rel="status")],
    "上线审批已推送：approval_id={approval_id}，status={status}\n可随时发送\"查询审批进度\"消息查询审批状态。"))
files["wf_sub_06_上线审批.json"] = workflow(
    "产销品-上线审批", "子工作流6：上线审批。进入前主流程已校验approve_confirmed=true；submit_release_approval（自研模拟，插件层二次校验+幂等）。", "wf_sub_06", s6,
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
     ("fee_error_rate", "计费差错率", "string"), ("alarm_list", "告警列表", "string")],
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

# ============================================================
# wf_cpcp_main 主工作流（V1.6：两次中断+串行+异常A+续跑）
# ============================================================
m = []
m.append(start_node(1, [
    inp("requirement_text", "销售品需求描述/文档摘要（首次执行必填）", required=False),
    inp("requirement_file", "需求文档地址（选填）", required=False),
    inp("plan_id", "已确认的执行方案存储key（确认后续跑必填）", required=False),
    inp("confirmed", "用户是否已确认执行方案，默认false", required=False, content="false"),
    inp("resume_action", "续跑指令：retry_from_fail/revise_plan", required=False),
    inp("fail_node", "上次失败环节编码：STAGE1_CONFIG/STAGE2_AUDIT/STAGE3_FEE/STAGE4_TEST", required=False),
    inp("execution_id", "执行主干批次号 EXE+yyyyMMddHHmmss+2位序号，重新执行沿用原值", required=False),
    inp("approve_confirmed", "审批发起确认标志，默认false", required=False, content="false"),
], pos=(15, 400)))
m.append(selector_node2(2, "入口判定",
    [dep_node(1, "开始节点", ["confirmed", "plan_id"])],
    # 平台样例约定：条件定义在 port=-1（否则分支）；port=0 由平台自动路由
    # 语义（port=-1 命中）：confirmed==true 且 plan_id 不为空 → 续跑判定；
    # plan_id 非空=执行方案已保存（无待补充项才会保存），从源头拦截未保存方案进入智能配置；
    # port=0 兜底分支：未确认 → 需求分析
    [(-1, [cond_item(cond_ref(1, "confirmed", "开始节点"), 1, cond_str("true")),
           cond_item(cond_ref(1, "plan_id", "开始节点"), 10, cond_str(""))])],
    pos=(200, 400)))
m.append(subflow_node(3, "需求分析", "调用wf_sub_01：需求理解→相似产品→字段映射与AI补全→待补充判断（无待补充才保存执行方案并产出plan_id）",
    "REPLACE_WITH_SUB01_FLOWID",
    [inp("requirement_text", "需求描述", ref_block=nid(1), ref_rel="requirement_text"),
     inp("requirement_file", "需求文档地址", ref_block=nid(1), ref_rel="requirement_file")],
    [("plan_id", "执行方案存储key（有待补充项时为空）"), ("plan_md", "执行方案表格"), ("pending_fields", "待补充字段")],
    pos=(390, 560)))
m.append(end_node(31, "结束节点A(执行方案确认)",
    [inp("plan_id", "执行方案key（有待补充项时为空）", ref_block=nid(3), ref_rel="plan_id"),
     inp("plan_md", "执行方案表格", ref_block=nid(3), ref_rel="plan_md"),
     inp("pending_fields", "待补充字段", ref_block=nid(3), ref_rel="pending_fields")],
    "{plan_md}\n\n【待补充字段】{pending_fields}\n\n【若以上存在待补充字段】执行方案暂未保存、暂不能执行（回复【确认执行】无效）：\n- 请直接补充价格/资源类字段值，将更新执行方案并再次确认；\n【若待补充字段为空（plan_id 已生成）】请核对以上执行方案：\n- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节（每环节执行后打印结果，仅异常时中断）；\n- 如需调整：请直接说明修改意见。", pos=(600, 560)))
m.append(selector_node2(4, "续跑判定①审批确认",
    [dep_node(1, "开始节点", ["approve_confirmed"])],
    # 平台样例约定：条件定义在 port=-1；port=0 兜底
    # 语义（port=-1 命中）：approve_confirmed==true → 报告汇总（中断②续办）；port=0 兜底 → 续跑判定②
    [(-1, [cond_item(cond_ref(1, "approve_confirmed", "开始节点"), 1, cond_str("true"))])],
    pos=(390, 200)))
m.append(selector_node2(41, "续跑判定②修改方案",
    [dep_node(1, "开始节点", ["resume_action"])],
    # 语义（port=-1 命中）：resume_action==revise_plan → 需求分析（修改执行方案）；port=0 兜底 → 续跑判定③
    [(-1, [cond_item(cond_ref(1, "resume_action", "开始节点"), 1, cond_str("revise_plan"))])],
    pos=(390, 200)))
m.append(selector_node2(42, "续跑判定③失败续跑",
    [dep_node(1, "开始节点", ["resume_action", "fail_node"])],
    # 语义（port=-1 命中）：resume_action==retry_from_fail → 按 fail_node 跳失败环节（默认环节1重跑，中间环节复用已落地结果）；
    # port=0 兜底（首次执行/无续跑参数）→ 环节1
    [(-1, [cond_item(cond_ref(1, "resume_action", "开始节点"), 1, cond_str("retry_from_fail"))])],
    pos=(390, 200)))
# ---- 环节1 智能配置 ----
m.append(subflow_node(5, "环节1-智能配置", "调用wf_sub_02：节点结果查询读取执行方案JSON→save_product_config原样透传落地",
    "REPLACE_WITH_SUB02_FLOWID",
    [inp("plan_id", "执行方案key", ref_block=nid(1), ref_rel="plan_id")],
    [("product_id", "CRM产品ID"), ("offer_id", "销售品ID"), ("save_result", "四类字段写入结果"), ("status", "SUCCESS/PARTIAL/FAIL")],
    pos=(600, 200)))
m.append(plugin_node(51, "环节1结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=execution_id，node_name=config（智能配置），result_json=环节1结果",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求唯一标识=execution_id（EXE+yyyyMMddHHmmss+2位序号）", ref_block=nid(1), ref_rel="execution_id"),
     inp("node_name", "环节名=config（智能配置）", content="config"),
     inp("result_json", "环节1结果JSON", ref_block=nid(5), ref_rel="save_result"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(760, 200)))
m.append(selector_node2(6, "环节1判定与打印",
    [dep_node(5, "环节1-智能配置", ["status"])],
    # 语义（port=-1 命中）：status != SUCCESS → 异常A；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(5, "status", "环节1-智能配置"), 2, cond_str("SUCCESS"))])],
    pos=(900, 200)))
m.append(end_node(61, "环节1成功打印",
    [inp("product_id", "产品ID", ref_block=nid(5), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(5), ref_rel="offer_id"),
     inp("save_result", "写入结果", ref_block=nid(5), ref_rel="save_result")],
    "【环节1/智能配置】✅ 执行成功\n- 关键数据：product_id={product_id}，offer_id={offer_id}，四类字段写入结果：{save_result}\n- 已自动进入下一环节……", pos=(1060, 120)))
# ---- 环节2 实时稽核 ----
m.append(subflow_node(7, "环节2-实时稽核", "调用wf_sub_03：realtime_spec_audit同步稽核",
    "REPLACE_WITH_SUB03_FLOWID",
    [inp("offer_id", "销售品ID", ref_block=nid(5), ref_rel="offer_id"),
     inp("config_json", "落地配置JSON（环节1存储回放或直接引用）", ref_block=nid(5), ref_rel="save_result")],
    [("pass", "1通过/0不通过"), ("error_list", "问题明细"), ("audit_summary", "稽核总结")],
    pos=(1200, 200)))
m.append(plugin_node(71, "环节2结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=execution_id，node_name=spec（规格稽核）",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求唯一标识=execution_id", ref_block=nid(1), ref_rel="execution_id"),
     inp("node_name", "环节名=spec", content="spec"),
     inp("result_json", "环节2结果JSON", ref_block=nid(7), ref_rel="audit_summary"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(1350, 200)))
m.append(selector_node2(8, "环节2判定与打印",
    [dep_node(7, "环节2-实时稽核", ["pass"])],
    # 语义（port=-1 命中）：pass != 1 → 异常A；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(7, "pass", "环节2-实时稽核"), 2, cond_str("1"))])],
    pos=(1500, 200)))
m.append(end_node(81, "环节2成功打印",
    [inp("audit_summary", "稽核总结", ref_block=nid(7), ref_rel="audit_summary")],
    "【环节2/配置规格稽核】✅ 执行成功\n- 关键数据：稽核通过 + {audit_summary}\n- 已自动进入下一环节……", pos=(1650, 120)))
# ---- 环节3 资费校准 ----
m.append(subflow_node(9, "环节3-资费校准", "调用wf_sub_05：check_billing_rule校验",
    "REPLACE_WITH_SUB05_FLOWID",
    [inp("config_json", "落地配置JSON", ref_block=nid(5), ref_rel="save_result")],
    [("pass", "1通过/0不通过"), ("risk_list", "风险清单"), ("risk_summary", "风险解读")],
    pos=(1800, 200)))
m.append(plugin_node(91, "环节3结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=execution_id，node_name=fee（资费校准）",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求唯一标识=execution_id", ref_block=nid(1), ref_rel="execution_id"),
     inp("node_name", "环节名=fee", content="fee"),
     inp("result_json", "环节3结果JSON", ref_block=nid(9), ref_rel="risk_summary"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(1950, 200)))
m.append(selector_node2(10, "环节3判定与打印",
    [dep_node(9, "环节3-资费校准", ["pass"])],
    # 语义（port=-1 命中）：pass != 1 → 异常A；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(9, "pass", "环节3-资费校准"), 2, cond_str("1"))])],
    pos=(2100, 200)))
m.append(end_node(101, "环节3成功打印",
    [inp("risk_summary", "风险解读", ref_block=nid(9), ref_rel="risk_summary")],
    "【环节3/资费校准】✅ 执行成功\n- 关键数据：资费校准通过 + {risk_summary}\n- 已自动进入下一环节……", pos=(2250, 120)))
# ---- 环节4 自动测试 ----
m.append(subflow_node(11, "环节4-自动测试", "调用wf_sub_04：发起→场景→轮询→结果→报告（含受理验证结论）",
    "REPLACE_WITH_SUB04_FLOWID",
    [inp("offer_id", "销售品ID", ref_block=nid(5), ref_rel="offer_id")],
    [("test_report", "测试报告含受理验证结论"), ("test_passed", "通过/失败"), ("globalId", "测试流水号"), ("orderId", "受理订单号"), ("offerInstId", "销售品实例ID")],
    pos=(2400, 200)))
m.append(plugin_node(111, "环节4结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=execution_id，node_name=test（自动测试）",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求唯一标识=execution_id", ref_block=nid(1), ref_rel="execution_id"),
     inp("node_name", "环节名=test", content="test"),
     inp("result_json", "环节4结果JSON", ref_block=nid(11), ref_rel="test_report"),
     inp("status", "本环节状态=ok", content="ok")],
    [("code", "0成功", "string"), ("msg", "状态描述", "string"), ("record_id", "存储记录ID", "string")], pos=(2550, 200)))
m.append(selector_node2(12, "环节4判定与打印",
    [dep_node(11, "环节4-自动测试", ["test_passed"])],
    # 语义（port=-1 命中）：test_passed != 通过 → 异常A；port=0 兜底 → 成功打印
    [(-1, [cond_item(cond_ref(11, "test_passed", "环节4-自动测试"), 2, cond_str("通过"))])],
    pos=(2700, 200)))
m.append(end_node(121, "环节4成功打印",
    [inp("test_report", "测试报告", ref_block=nid(11), ref_rel="test_report")],
    "【环节4/销售品自动测试（含受理验证）】✅ 执行成功\n- 关键数据：场景数/测点数统计 + 受理验证结论（orderId/offerInstId + 逐受理场景结论）\n{test_report}\n- 执行主干全部完成", pos=(2850, 120)))
# ---- 主干完成判定与成功汇总 ----
m.append(selector_node2(13, "主干完成判定",
    [dep_node(5, "环节1", ["status"]),
     dep_node(7, "环节2", ["pass"]),
     dep_node(9, "环节3", ["pass"]),
     dep_node(11, "环节4", ["test_passed"])],
    # 语义（port=-1 命中任一不满足）：任一环节未成功（各条件且 logic=1）→ 异常A；port=0 兜底 → 成功汇总
    [(-1, [cond_item(cond_ref(5, "status", "环节1"), 2, cond_str("SUCCESS")),
           cond_item(cond_ref(7, "pass", "环节2"), 2, cond_str("1")),
           cond_item(cond_ref(9, "pass", "环节3"), 2, cond_str("1")),
           cond_item(cond_ref(11, "test_passed", "环节4"), 2, cond_str("通过"))])],
    pos=(3000, 200)))
m.append(llm_node(14, "成功结果详情汇总",
    "执行主干四个环节全部成功，请基于以下输入按模板输出（逐字引用输入数据，不新增结论）：\n【环节1落地结果】save_result={save_result}\n【稽核总结】audit_summary={audit_summary}\n【测试明细】test_report={test_report}\n\n输出模板：\n【执行主干全部完成】✅ 共4个环节执行成功：\n1. 智能配置：product_id={product_id}，offer_id={offer_id}，四类字段全部写入成功；\n2. 配置规格稽核：通过，{audit_summary}；\n3. 资费校准：通过，未发现叠加/互斥冲突；\n4. 自动测试（含受理验证）：场景 N 个、测点 M 个全部一致；\n   受理验证：orderId={orderId}，offerInstId={offerInstId}，各受理场景均通过。\n\n是否发起上线审批？回复【发起审批】将汇总以上结果提交审批流；回复【暂不】可稍后发送\"发起审批\"继续。\n\n输出要求：仅输出按上述模板渲染的汇总内容（对应出参 stage_summary），不输出其他多余文字。",
    [inp("product_id", "产品ID", ref_block=nid(5), ref_rel="product_id"),
     inp("offer_id", "销售品ID", ref_block=nid(5), ref_rel="offer_id"),
     inp("audit_summary", "稽核总结", ref_block=nid(7), ref_rel="audit_summary"),
     inp("save_result", "落地结果", ref_block=nid(5), ref_rel="save_result"),
     inp("test_report", "测试报告", ref_block=nid(11), ref_rel="test_report"),
     inp("orderId", "受理订单号（环节4测试结果出参）", ref_block=nid(11), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID（环节4测试结果出参）", ref_block=nid(11), ref_rel="offerInstId")],
    [out("stage_summary", "成功结果详情汇总")], pos=(3150, 200)))
# ---- 审批分支（中断②后续办） ----
m.append(llm_node(16, "报告汇总",
    "请基于以下输入数据，按标准模板汇总生成《销售品上线测试与稽核报告》：\n【配置落地结果】save_result={save_result}\n【稽核结论】audit_summary={audit_summary}\n【资费结论】risk_summary={risk_summary}\n【测试明细】test_report={test_report}\n报告必须包含以下章节：1.需求摘要与执行方案要点；2.配置落地结果；3.稽核结论；4.资费结论；5.测试统计与失败明细；6.受理验证结论（强制章节，引用orderId={orderId}、offerInstId={offerInstId}，逐受理场景给出通过/失败结论）；7.上线建议。只基于输入数据生成，不得新增结论。",
    [inp("save_result", "落地结果", ref_block=nid(5), ref_rel="save_result"),
     inp("audit_summary", "稽核总结", ref_block=nid(7), ref_rel="audit_summary"),
     inp("risk_summary", "风险解读", ref_block=nid(9), ref_rel="risk_summary"),
     inp("test_report", "测试报告", ref_block=nid(11), ref_rel="test_report"),
     inp("orderId", "受理订单号（环节4测试结果出参）", ref_block=nid(11), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID（环节4测试结果出参）", ref_block=nid(11), ref_rel="offerInstId")],
    [out("report", "上线报告")], pos=(3150, 400)))
m.append(subflow_node(17, "上线审批", "调用wf_sub_06：审批推送（插件层校验approve_confirmed）",
    "REPLACE_WITH_SUB06_FLOWID",
    [inp("product_id", "产品ID", ref_block=nid(5), ref_rel="product_id"),
     inp("report", "上线报告", ref_block=nid(16), ref_rel="report")],
    [("approval_id", "审批单号"), ("status", "提交状态")],
    pos=(3400, 400)))
m.append(end_node(18, "结束节点B(审批已发起)",
    [inp("product_id", "产品ID", ref_block=nid(5), ref_rel="product_id"),
     inp("plan_id", "执行方案key", ref_block=nid(1), ref_rel="plan_id"),
     inp("approval_id", "审批单号", ref_block=nid(17), ref_rel="approval_id"),
     inp("stage_summary", "各环节结果摘要", ref_block=nid(14), ref_rel="stage_summary")],
    "上线审批已发起：approval_id={approval_id}\nproduct_id={product_id}｜plan_id={plan_id}\n{stage_summary}\n\n下一步：可发送消息\"查询审批进度\"或\"查询销售品监控结果\"。", pos=(3600, 400)))
# ---- 异常处置（异常A） ----
m.append(llm_node(21, "异常处置-异常A",
    "执行主干在某一环节异常中断，请生成异常处置说明：\n1.异常环节名称（按 fail_node 映射：STAGE1_CONFIG 智能配置 / STAGE2_AUDIT 配置规格稽核 / STAGE3_FEE 资费校准 / STAGE4_TEST 销售品自动测试）；\n2.异常原因（引用接口返回原文 resultCode/resultMsg，不得臆测）；\n3.关键明细（稽核问题清单/资费风险清单/测试失败测点/超时信息，按实际输入展开）；\n4.整改建议；\n5.结尾固定引导：\n请选择下一步：\n① 回复【重新执行】：将自动从失败环节继续（已成功环节不重复执行）\n ② 回复【修改执行方案】：请说明修改意见，将重新生成执行方案并再次确认\n不得自行发起重试，不得跳过失败环节。\nfail_node={fail_node}，异常环节出参={exception_output}\n\n输出要求：仅输出按上述5点生成的异常处置说明（对应出参 exception_summary），不输出其他多余文字。",
    [inp("fail_node", "失败环节编码", ref_block=nid(1), ref_rel="fail_node"),
     inp("exception_output", "失败环节返回出参原文（异常判定节点引用）", required=False)],
    [out("exception_summary", "异常处置说明")], pos=(1200, 700)))
m.append(end_node(22, "结束(异常中断)",
    [inp("exception_summary", "异常处置说明", ref_block=nid(21), ref_rel="exception_summary")],
    "{exception_summary}", pos=(1400, 700)))

me = [
    edge(1,2),
    edge(2,4,0),      # 确认且plan_id非空 → 续跑判定①审批确认
    edge(2,3,-1),     # 未确认 → 需求分析
    edge(3,31),       # 需求分析 → 结束节点A（中断①）
    edge(4,16,0),     # 续跑判定①：approve_confirmed=true → 报告汇总（审批确认续办）
    edge(4,41,-1),    # 续跑判定①：否则 → 判定②修改方案
    edge(41,3,0),     # 续跑判定②：revise_plan → 需求分析（修改执行方案）
    edge(41,42,-1),   # 续跑判定②：否则 → 判定③失败续跑
    edge(42,5,0),     # 续跑判定③：retry_from_fail → 按 fail_node 续跑（先重入环节1判定，已成功环节由存储回放）
    edge(42,5,-1),    # 续跑判定③：否则（首次执行）→ 环节1
    edge(5,51), edge(51,6),
    edge(6,61,0), edge(6,21,-1),   # 环节1成功打印 / 失败→异常A
    edge(61,7),
    edge(7,71), edge(71,8),
    edge(8,81,0), edge(8,21,-1),
    edge(81,9),
    edge(9,91), edge(91,10),
    edge(10,101,0), edge(10,21,-1),
    edge(101,11),
    edge(11,111), edge(111,12),
    edge(12,121,0), edge(12,21,-1),
    edge(121,13),
    edge(13,14,0), edge(13,21,-1), # 全部成功→成功汇总 / 否则→异常A
    edge(14,16),      # 中断②后 approve_confirmed=true 续入报告汇总（对应续跑判定①）
    edge(16,17), edge(17,18),
    edge(21,22),
]
files["wf_cpcp_main_产销品加载主流程.json"] = workflow(
    "产销品加载主流程", "主工作流V1.6：两次中断（结束节点A执行方案确认/节点14审批发起确认）；执行主干四环节（智能配置→实时稽核→资费校准→自动测试）自动串行、每环节打印结果并存EXEC{execution_id}_STAGE{n}；异常统一走异常A节点（引导重新执行/修改执行方案，续跑回放已成功环节不重复调用写接口）。", "wf_cpcp_main", m, me)

for fn, data in files.items():
    data = apply_layout(data)
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", fn)

print("total:", len(files))

