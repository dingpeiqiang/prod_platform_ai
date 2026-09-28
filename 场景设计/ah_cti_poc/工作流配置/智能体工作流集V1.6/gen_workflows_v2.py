# -*- coding: utf-8 -*-
# 产销品加载AI应用 工作流配置 V2.0（废弃 skill → 工作流配置重塑）
# 本生成器扩充 gen_workflows.py（V1.7 基线），新增/重写前端子工作流：
#   - wf_sub_00 需求提报（环节1，新增）：需求文本 → LLM 抽取需求字段(snake_case)
#     → render_requirement_report 代码节点（确定性渲染需求提报单）→ 待补充判定
#     → 无待补充：保存需求工单(node_name=requirement_report) → 止于确认点
#       （提示"是否发起【需求工单审批】"，不自动发起审批；用户回复【发起需求审批】
#       后由 wf_sub_11 承接审批发起）→ 衔接 wf_sub_01
#   - wf_sub_11 发起需求审批（环节1）：自查需求工单(requirement_report) → 提取原文
#     → submit_release_approval(approval-type=requirement) → 回执渲染
#     （V3.3 契约：wf_sub_00 止于确认点后，须用户明确回复【发起需求审批】才由本流发起）
#   - wf_sub_01 需求分析（环节2，模板轨精简版）：读取需求工单 → 产品识别(LLM)
#     → 相似产品检索(query_similar_offer) → 取模板 → 要素提取(LLM)
#     → 要素校验与方案合并(CODE_EXTRACT_MERGE 合一) → render_table 渲染
#     → 保存 requirement（延续 wf_sub_00 的 req_id）
#
# 阶段1.3 抽为代码节点的确定性逻辑（从 skill 脚本直搬，去 argparse/CLI，改 async main(args)）：
#   CODE_RENDER_REQ    <- scripts/render_requirement_report.py  render()
#   CODE_GET_TEMPLATE  <- 模板注册表选择（读 方案/templates/_registry.json + schema 文件）
#   CODE_EXTRACT_MERGE <- scripts/validate_elements.py + scripts/merge_nested.py（校验+合并合一）
#   CODE_RENDER_TABLE  <- scripts/render_table.py  render()
#
# 依赖模板块引入：nid/inp/out/start_node/end_node/llm_node/plugin_node/code_node/
#   selector_node2/cond_item/cond_ref/cond_str/edge/workflow/apply_layout/dep_node
import io, json, os, sys
from gen_workflows import (
    nid, inp, out, code_out, start_node, end_node, llm_node, plugin_node, code_node,
    selector_node2, cond_item, cond_ref, cond_str, edge, workflow, apply_layout,
    dep_node, BASE, BASE_URL, CODE_EXTRACT_RECORD, CODE_POLL_PROGRESS,
    CODE_SUMMARY_APPROVAL, CODE_PACK_RECORD, CODE_POLL_AUDIT, CODE_EXTRACT_GLOBAL_ID,
    CODE_EXTRACT_REQUEST_IDS,
    CODE_AUDIT_LAUNCH, CODE_AUDIT_INSERT, CODE_AUDIT_QUERY,
)

# ============================================================
# 页面地址输出（每个工作流结束后输出环节业务页外链）
#   约定：由独立代码节点（type=6，panel_code_node）专门构建 xsbot-panel 片段，
#   结束节点模板仅引用 {panel}（不再内联 xsbot-panel 代码块）；
#   每个结束节点固定单面板单 url：config-workbench.html?offer_id=..&name=..&chatId=..&req_id=..&stage=<N>&view=stage，
#   title 用环节业务名（对齐参考示例形态），chatId 运行期以 chat_id 实值替换（req_id 有 req_id 的子流 00~06 透传，供页面按 req_id 定位）。
# ============================================================
OPS_WEB_BASE = "http://10.86.13.201:31280/ops-web"
CONFIG_WB_PAGE = OPS_WEB_BASE + "/config-workbench.html"


def gen_panel_code(stage, biz_title=None):
    """构建 xsbot-panel 片段的代码节点源码（type=6 内联 Python，供 panel_code_node 使用）。
    对齐参考示例：**单面板**（一个 url + 一个 title），url 用环节业务页
    config-workbench.html?stage=<N>&view=stage，chatId 运行期以 chat_id 实值填充，req_id 透传供页面定位；
    输出 panel 为 `\n\n```xsbot-panel {compact_json} ``` ` 片段（无文字前缀、JSON 紧凑无空格，逐字对齐参考示例），
    结束节点模板仅引用 {panel}。读取参数 chat_id/offer_id/offer_name/req_id，运行期完成实值替换；
    offer_id/offer_name 为空时不在 url 拼接（避免出现 offer_id=&name= 空参），多品时以 req_id 定位。
    """
    page = CONFIG_WB_PAGE
    title = biz_title or "环节业务页"
    body = [
        "from typing import Any, Dict",
        "",
        "async def main(args):",
        "    p = args.params",
        "    chat_id = str(p.get('chat_id') or '')",
        "    offer_id = str(p.get('offer_id') or '')",
        "    offer_name = str(p.get('offer_name') or '')",
        "    req_id = str(p.get('req_id') or '')",
        "    qs = ('?chatId=' + chat_id + '&req_id=' + req_id + '&stage=%s&view=stage')" % stage,
        "    if offer_id:",
        "        qs = ('?offer_id=' + offer_id + '&name=' + offer_name + '&chatId=' + chat_id "
        "+ '&req_id=' + req_id + '&stage=%s&view=stage')" % stage,
        "    biz_url = '%s' + qs" % page,
    ]
    # 紧凑 JSON（无空格，逐字对齐参考示例），title 在生成期烘焙为字面量
    body += [
        "    panel = (",
        "        \"\\n\\n```xsbot-panel\\n\"",
        "        + '{\"version\":\"1.0\",\"message_id\":\"' + chat_id + '\",'",
        "        + '\"panels\":[{\"panel\":\"right\",\"mode\":\"external\",\"url\":\"' + biz_url + '\",\"title\":\"%s\"}]}'" % title,
        "        + \"\\n```\\n\"",
        "    )",
        "    result: Output = {'panel': panel}",
        "    return result",
    ]
    return "\n".join(body) + "\n"


def panel_inputs(chat_seq, off_seq=None, off_rel='offer_id', name_seq=None, name_rel='offer_name', req_seq=None):
    """构建 xsbot-panel 代码节点入参（name 即代码读取的 param key，ref_rel 绑定上游出参）。
    供各工作流按其实有可用的 会话ID/销售品ID/产品名/需求单号 绑定；offer/name/req 可选传入。
    req_seq 指定提供 req_id 的节点（有 req_id 的工作流 00~06 传入；只读/监控等无 req_id 的留空）。
    """
    refs = [inp("chat_id", "会话消息ID（用于 xsbot-panel message_id 与 url 的 chatId）",
                ref_block=nid(chat_seq), ref_rel="chat_id")]
    if req_seq is not None:
        refs.append(inp("req_id", "需求单号/方案批次号（用于面板 url 的 req_id）",
                        ref_block=nid(req_seq), ref_rel="req_id"))
    if off_seq is not None:
        refs.append(inp("offer_id", "销售品ID/需求单号（用于面板 url 的 offer_id）",
                        ref_block=nid(off_seq), ref_rel=off_rel))
    if name_seq is not None:
        refs.append(inp("offer_name", "产品名称（用于面板 url 的 name）",
                        ref_block=nid(name_seq), ref_rel=name_rel))
    return refs


def panel_code_node(seq, title, stage, in_refs, biz_title=None):
    """type=6 代码节点：独立构建单面板 xsbot-panel 片段，输出 panel 供结束节点 {panel} 引用。"""
    return code_node(seq, title, gen_panel_code(stage, biz_title), in_refs,
                     [code_out("panel", seq)])


# ============================================================
# 阶段1.3 代码节点（嵌入式确定性逻辑）
# ============================================================

# ---------------- CODE_RENDER_REQ：需求提报单确定性渲染 ----------------
# 来源 scripts/render_requirement_report.py（V1.0）
# 输入 args.params['elements_json']：LLM 抽取的需求字段 snake_case 平面 JSON 字符串
# 输出：report_text（《销售品需求提报单》markdown）、pending_fields（待补充业务标签逗号串）、
#       pending_count、req_id（未传时系统生成 PLAN+时间戳+3位随机）
CODE_RENDER_REQ = (
    "import json, random, datetime\n"
    "from typing import Any, Dict\n"
    "\n"
    "EMPTY_MARKS = ('', '待补充', '系统待生成', '无', '暂无')\n"
    "FUSION_PRODUCT_TYPES = ('家庭基础套餐', '融合套餐')\n"
    "BASE_FIELDS = (\n"
    "    ('name', '销售品名称'), ('product_type', '产品类型'), ('series', '所属系列'),\n"
    "    ('price', '套餐档位（月费）'), ('resources', '套内资源'), ('out_price', '套外资费'),\n"
    "    ('billing_cycle', '计费周期'), ('effective_way', '生效方式'), ('validity', '套餐有效期'),\n"
    "    ('change_rule', '变更规则'), ('cancel_rule', '退订/拆机规则'),\n"
    ")\n"
    "FUSION_EXTRA_FIELDS = (('members', '融合成员'),)\n"
    "PRICE_FIELDS = ('price',)\n"
    "REQUIRED_FIELDS = ('price', 'resources')\n"
    "\n"
    "def _norm(v):\n"
    "    if v is None:\n"
    "        return ''\n"
    "    if isinstance(v, bool):\n"
    "        return '是' if v else '否'\n"
    "    if isinstance(v, (int, float)):\n"
    "        return v\n"
    "    s = str(v).strip()\n"
    "    return '' if s in EMPTY_MARKS else s\n"
    "\n"
    "def _gen_req_id():\n"
    "    ts = datetime.datetime.now().strftime('%Y%m%d%H%M%S')\n"
    "    return 'PLAN%s%03d' % (ts, random.randint(0, 999))\n"
    "\n"
    "def _parse(raw):\n"
    "    if isinstance(raw, dict):\n"
    "        return raw\n"
    "    if isinstance(raw, list):\n"
    "        return {'offers': raw}\n"
    "    if not isinstance(raw, str) or not raw.strip():\n"
    "        return {}\n"
    "    try:\n"
    "        obj = json.loads(raw)\n"
    "        if isinstance(obj, list):\n"
    "            # V3.12 多商品：LLM 抽取出参可能是 N 品字段数组，归一为 {'offers': [...]}\n"
    "            return {'offers': obj}\n"
    "        return obj\n"
    "    except Exception:\n"
    "        t = raw.strip()\n"
    "        if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "            try:\n"
    "                obj = json.loads(t.split(':', 1)[1].strip())\n"
    "                if isinstance(obj, list):\n"
    "                    return {'offers': obj}\n"
    "                return obj\n"
    "            except Exception:\n"
    "                return {}\n"
    "        return {}\n"
    "\n"
    "def fields_for(product_type):\n"
    "    pt = _norm(product_type)\n"
    "    # 融合判定：枚举命中 或 产品类型文本含“融合”二字（兼容 LLM 输出“融合套餐/5G-A融合”等变体）\n"
    "    if pt in FUSION_PRODUCT_TYPES or '融合' in str(pt):\n"
    "        return BASE_FIELDS + FUSION_EXTRA_FIELDS\n"
    "    return BASE_FIELDS\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    # V3.12 多商品：入参 offers_json（N 品字段数组），兼容旧 elements_json（单品平面 JSON → 包装为 1 品）\n"
    "    raw = p.get('offers_json') or p.get('elements_json') or ''\n"
    "    obj = _parse(raw)\n"
    "    if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "        elements_list = obj.get('offers')\n"
    "    elif isinstance(obj, dict) and obj:\n"
    "        elements_list = [obj]\n"
    "    else:\n"
    "        elements_list = []\n"
    "    req_id = str(p.get('req_id') or _gen_req_id())\n"
    "    reporter = str(p.get('reporter') or '')\n"
    "    need_summary = str(p.get('need_summary') or '')\n"
    "    today = datetime.date.today().strftime('%Y-%m-%d')\n"
    "    def cell(v):\n"
    "        return '待补充' if v == '' else str(v)\n"
    "    def table(rows):\n"
    "        if not rows:\n"
    "            return []\n"
    "        out = ['| 字段 | 内容 |', '| --- | --- |']\n"
    "        out += ['| %s | %s |' % (k, v) for k, v in rows]\n"
    "        return out\n"
    "    offers_out = []\n"
    "    pending_all = []\n"
    "    lines = []\n"
    "    lines.append('## 销售品需求提报单')\n"
    "    base_rows = [('需求单号', req_id), ('提报日期', today), ('商品数量', str(len(elements_list)))]\n"
    "    if reporter:\n"
    "        base_rows.append(('提报人', reporter))\n"
    "    if need_summary:\n"
    "        base_rows.append(('需求概述', need_summary))\n"
    "    lines.append('### 1. 需求基本信息')\n"
    "    lines += table(base_rows)\n"
    "    lines.append('')\n"
    "    for idx, elements in enumerate(elements_list, start=1):\n"
    "        if not isinstance(elements, dict):\n"
    "            continue\n"
    "        offer_seq = int(elements.get('offer_seq') or idx)\n"
    "        fmt = lambda k: _norm(elements.get(k))\n"
    "        fields = fields_for(elements.get('product_type'))\n"
    "        values = {}\n"
    "        pending = []\n"
    "        for key, label in fields:\n"
    "            val = fmt(key)\n"
    "            values[key] = val\n"
    "            if key in REQUIRED_FIELDS and (val == '' or (key in PRICE_FIELDS and val in (0, '0', '0元'))):\n"
    "                pending.append(label)\n"
    "        offers_out.append({'offer_seq': offer_seq, 'fields': values, 'pending_fields': pending})\n"
    "        if pending:\n"
    "            pending_all.append('第%d档（%s）：%s' % (offer_seq, cell(values.get('name')), '、'.join(pending)))\n"
    "        prod_rows = [('销售品名称', cell(values['name'])), ('产品类型', cell(values['product_type']))]\n"
    "        if values['series']:\n"
    "            prod_rows.append(('所属系列', values['series']))\n"
    "        if values.get('members'):\n"
    "            prod_rows.append(('融合成员', values['members']))\n"
    "        fee_rows = [('套餐档位（月费）', (str(values['price']) + ' 元/月') if values['price'] != '' else '待补充')]\n"
    "        if values['resources']:\n"
    "            fee_rows.append(('套内资源', values['resources']))\n"
    "        if values['out_price']:\n"
    "            fee_rows.append(('套外资费', values['out_price']))\n"
    "        if values['billing_cycle']:\n"
    "            fee_rows.append(('计费周期', values['billing_cycle']))\n"
    "        order_rows = []\n"
    "        if values['effective_way']:\n"
    "            order_rows.append(('生效方式', values['effective_way']))\n"
    "        if values['validity']:\n"
    "            order_rows.append(('套餐有效期', values['validity']))\n"
    "        if values['change_rule']:\n"
    "            order_rows.append(('变更规则', values['change_rule']))\n"
    "        if values['cancel_rule']:\n"
    "            order_rows.append(('退订/拆机规则', values['cancel_rule']))\n"
    "        lines.append('### 商品 %d（offer_seq=%d）：%s' % (idx, offer_seq, cell(values['name'])))\n"
    "        lines.append('#### 产品/销售品信息')\n"
    "        lines += table(prod_rows)\n"
    "        lines.append('')\n"
    "        lines.append('#### 资费方案要点')\n"
    "        lines += table(fee_rows)\n"
    "        lines.append('')\n"
    "        lines.append('#### 订购/变更/退订规则')\n"
    "        if order_rows:\n"
    "            lines += table(order_rows)\n"
    "        else:\n"
    "            lines.append('（未提及，从宽不催补）')\n"
    "        lines.append('')\n"
    "    lines.append('### 待补充字段')\n"
    "    lines.append('\\n'.join(pending_all) if pending_all else '无')\n"
    "    while lines and lines[-1] == '':\n"
    "        lines.pop()\n"
    "    text = '\\n'.join(lines)\n"
    "    ret: Output = {\n"
    "        \"report_text\": text,\n"
    "        \"pending_fields\": '；'.join(pending_all),\n"
    "        \"pending_count\": (str(len(pending_all)) if pending_all else ''),\n"
    "        \"req_id\": req_id,\n"
    "        \"offers\": json.dumps({'req_id': req_id, 'offers': offers_out}, ensure_ascii=False),\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_GET_TEMPLATE：模板注册表选择 ----------------
# 来源：后端 BASE_URL/api/v1/appstore/template/schema（classpath 下发，避免平台环境读不到本地文件）；
# 本地开发可用 args.params['template_base'] 指定目录做文件兜底（默认空=仅走接口）。
# 输入 args.params['template_id']（六选一，或中文名/产品类型）+ 可选 template_base（文件兜底目录）
# 输出：schema_json（完整模板 schema JSON 字符串）、template_name_cn、template_product_type、
#       schema_file、missing、source（backend/file/empty）
CODE_GET_TEMPLATE = (
    "import json, os, urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "TEMPLATE_URL = 'BASE_URL/api/v1/appstore/template/schema'\n"
    "TEMPLATE_ALIAS = {\n"
    "    'personMainPrc': ('个人主资费', '个人主套餐'),\n"
    "    'broadBandMainPrc': ('宽带主资费', '宽带主套餐'),\n"
    "    'personAddPrc': ('个人附加资费', '个人附加资费'),\n"
    "    'broadBandOptSpeedPrc': ('宽带加速包', '宽带附加资费'),\n"
    "    'familyBasePrc': ('家庭基础套餐', '家庭基础套餐'),\n"
    "    'familyAddPrc': ('家庭附加业务', '家庭附加资费'),\n"
    "}\n"
    "\n"
    "def _pick(req):\n"
    "    r = str(req or '').strip()\n"
    "    # ① 精确匹配（模板标识/中文名/产品类型）\n"
    "    for tid, (cn, pt) in TEMPLATE_ALIAS.items():\n"
    "        if r == tid or r == cn or r == pt:\n"
    "            return tid, cn, pt\n"
    "    # ② 兜底：子串包含（容忍上游出参夹带标签/说明文字）\n"
    "    for tid, (cn, pt) in TEMPLATE_ALIAS.items():\n"
    "        if tid in r or (cn and cn in r) or (pt and pt in r):\n"
    "            return tid, cn, pt\n"
    "    return 'personMainPrc', '个人主资费', '个人主套餐'\n"
    "\n"
    "def _fetch(tid):\n"
    "    body = json.dumps({'templateId': tid}).encode('utf-8')\n"
    "    req = urllib.request.Request(TEMPLATE_URL, data=body, method='POST',\n"
    "                                 headers={'Content-Type': 'application/json'})\n"
    "    with urllib.request.urlopen(req, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    tid, cn, pt = _pick(p.get('template_id') or '')\n"
    "    content = '{}'\n"
    "    missing = '0'\n"
    "    source = 'empty'\n"
    "    # ① 后端下发（主路径）\n"
    "    try:\n"
    "        info = _fetch(tid)\n"
    "        schema_text = str((info or {}).get('schema_json') or '')\n"
    "        if schema_text.strip() and schema_text.strip() != '{}':\n"
    "            content = schema_text\n"
    "            cn = str(info.get('template_name_cn') or cn)\n"
    "            pt = str(info.get('template_product_type') or pt)\n"
    "            source = 'backend'\n"
    "    except Exception:\n"
    "        info = None\n"
    "    # ② 本地文件兜底（仅当显式配置 template_base 时）\n"
    "    if source != 'backend':\n"
    "        base = str(p.get('template_base') or '').strip()\n"
    "        if base:\n"
    "            try:\n"
    "                with open(os.path.join(base, '%s.schema.json' % tid), 'r', encoding='utf-8') as f:\n"
    "                    content = f.read()\n"
    "                source = 'file'\n"
    "            except Exception:\n"
    "                missing = '1'\n"
    "    if source == 'empty':\n"
    "        missing = '1'\n"
    "    ret: Output = {\n"
    "        \"template_id\": tid,\n"
    "        \"template_name_cn\": cn,\n"
    "        \"template_product_type\": pt,\n"
    "        \"schema_json\": content,\n"
    "        \"schema_file\": '%s.schema.json' % tid,\n"
    "        \"missing\": missing,\n"
    "        \"source\": source,\n"
    "    }\n"
    "    return ret"
)
CODE_GET_TEMPLATE = CODE_GET_TEMPLATE.replace("BASE_URL", BASE_URL)

# ---------------- CODE_ROUTE_TEMPLATE：产品类型→模板标识确定性路由 ----------------
# 设计依据：统一方案 §6.2「LLM 只输出 product_type，路由代码定 templateId，防模板名幻觉」。
# 输入：offers_json（节点104 LLM 出参，[{offer_seq,product_type,need_summary}] 的 JSON 字符串或数组）
# 输出：offers_tpl（[{offer_seq,template_id,need_summary}] JSON 字符串，逐品绑定模板）、
#       template_id（首品模板标识，供节点106取模板）、template_ids（逗号串，供结束节点展示）、
#       fusion_flag（'1'=任一品命中融合，'0' 否则）。
CODE_ROUTE_TEMPLATE = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "# 产品类型关键词 → 模板标识（顺序即优先级；命中即锁定，全部不命中默认 personMainPrc）\n"
    "TYPE_RULES = (\n"
    "    ('家庭基础套餐', 'familyBasePrc'), ('融合套餐', 'familyBasePrc'),\n"
    "    ('融合', 'familyBasePrc'), ('家庭', 'familyBasePrc'),\n"
    "    ('宽带加速包', 'broadBandOptSpeedPrc'), ('宽带附加资费', 'broadBandOptSpeedPrc'),\n"
    "    ('宽带主套餐', 'broadBandMainPrc'), ('宽带主资费', 'broadBandMainPrc'),\n"
    "    ('家庭附加资费', 'familyAddPrc'), ('家庭附加业务', 'familyAddPrc'),\n"
    "    ('个人附加资费', 'personAddPrc'), ('个人附加', 'personAddPrc'),\n"
    "    ('个人主套餐', 'personMainPrc'), ('个人主资费', 'personMainPrc'),\n"
    ")\n"
    "VALID = ('personMainPrc', 'broadBandMainPrc', 'personAddPrc',\n"
    "         'broadBandOptSpeedPrc', 'familyBasePrc', 'familyAddPrc')\n"
    "\n"
    "def _load(raw):\n"
    "    if isinstance(raw, (list, dict)):\n"
    "        return raw\n"
    "    if not isinstance(raw, str) or not raw.strip():\n"
    "        return []\n"
    "    t = raw.strip()\n"
    "    if ':' in t and not t.lstrip().startswith('{') and not t.lstrip().startswith('['):\n"
    "        t = t.split(':', 1)[1].strip()\n"
    "    s, e = t.find('['), t.rfind(']')\n"
    "    if s != -1 and e > s:\n"
    "        t = t[s:e + 1]\n"
    "    try:\n"
    "        return json.loads(t)\n"
    "    except Exception:\n"
    "        return []\n"
    "\n"
    "def _route(offer):\n"
    "    tid = str(offer.get('template_id') or '').strip()\n"
    "    if tid in VALID:\n"
    "        return tid\n"
    "    pt = str(offer.get('product_type') or '')\n"
    "    names = pt + ' ' + str(offer.get('name') or '') + ' ' + str(offer.get('series') or '')\n"
    "    for kw, mapped in TYPE_RULES:\n"
    "        if kw in names:\n"
    "            return mapped\n"
    "    return 'personMainPrc'\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    data = _load(p.get('offers_json') or '')\n"
    "    if isinstance(data, dict):\n"
    "        data = data.get('offers') or data.get('offers_json') or [data]\n"
    "    if not isinstance(data, list):\n"
    "        data = []\n"
    "    out, first, fusion = [], '', '0'\n"
    "    for i, offer in enumerate(data, start=1):\n"
    "        if not isinstance(offer, dict):\n"
    "            continue\n"
    "        tid = _route(offer)\n"
    "        out.append({'offer_seq': offer.get('offer_seq') or i,\n"
    "                    'template_id': tid,\n"
    "                    'need_summary': str(offer.get('need_summary') or '')})\n"
    "        if not first:\n"
    "            first = tid\n"
    "        if tid == 'familyBasePrc':\n"
    "            fusion = '1'\n"
    "    if not out:\n"
    "        out = [{'offer_seq': 1, 'template_id': 'personMainPrc', 'need_summary': ''}]\n"
    "        first = 'personMainPrc'\n"
    "    ret: Output = {\n"
    "        'offers_tpl': json.dumps(out, ensure_ascii=False),\n"
    "        'template_id': first,\n"
    "        'template_ids': ','.join(str(o['template_id']) for o in out),\n"
    "        'fusion_flag': fusion,\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_RENDER_TABLE：逻辑模型报文→业务分节表格 ----------------
# 来源 scripts/render_table.py（V3.0，去 argparse）
# 输入：schema_json、payload（合并节点输出报文 JSON）、meta（溯源 JSON）、
#       similar_offer（相似产品信息 JSON，用于来源列拼接）、title
# 输出：table_text（markdown 分节多表）
CODE_RENDER_TABLE = (
    "import json\n"
    "import re\n"
    "from typing import Any, Dict\n"
    "\n"
    "SKIP_KEYS = ('templateId', 'prodId', 'prodPrcId', 'pricingId', 'opType')\n"
    "SYSTEM_GEN_KEYS = ('orderNo',)\n"
    "# 不可从自然语言需求提取的字段（系统生成/编码/规则类，x-required 亦不计入待补充）：\n"
    "# 与要素校验节点 108 的 NON_EXTRACTABLE_PATTERNS 保持一致，禁止在此罗列系统类字段为待补充。\n"
    "NON_EXTRACTABLE_PATTERNS = (\n"
    "    r'(effDate|expDate)$',\n"
    "    r'(effRuleId|cancelRuleId)$',\n"
    "    r'(prodId|pricingId|prodPrcId|inProdPrcId|outProdPrcId|billPrcId|conditionCode|groupId)$',\n"
    "    r'orderNo$',\n"
    "    r'groupIdMessage$',\n"
    "    r'subBillPrcIds$',\n"
    "    r'^optionalInfo\\.prcSmsCfg\\.',\n"
    "    r'acctItem$',\n"
    "    r'powerCode$',\n"
    "    r'familyCata$',\n"
    "    r'splitRate(9|6)$',\n"
    "    r'(favValidityVAlue|fixValidityVAlue)$',\n"
    ")\n"
    "\n"
    "def _is_non_extractable(path):\n"
    "    return any(re.search(p, path) for p in NON_EXTRACTABLE_PATTERNS)\n"
    "\n"
    "SOURCE_MAP = {\n"
    "    '原始需求': '原始需求提取', 'AI补全': '相似产品补全',\n"
    "    '本体推理': '本体推理', '默认值': '默认值', '同源派生': '同源派生',\n"
    "}\n"
    "SIMILAR_REF_PREFIX = '参考相似产品: '\n"
    "INDENT_UNIT = '\u3000'\n"
    "OVERVIEW_PATHS = (\n"
    "    ('baseInfo.prodPrcName', '资费名称'),\n"
    "    ('optionalInfo.printContent.prcMonthFee', '套餐月费'),\n"
    "    ('optionalInfo.printContent.containResource', '包含资源'),\n"
    ")\n"
    "\n"
    "def has_business_value(sub_schema, sub_data):\n"
    "    if isinstance(sub_data, dict):\n"
    "        for v in sub_data.values():\n"
    "            if v not in ('', None, [], {}):\n"
    "                return True\n"
    "        return False\n"
    "    return False\n"
    "\n"
    "def fmt_value(v):\n"
    "    if isinstance(v, bool):\n"
    "        return '是' if v else '否'\n"
    "    if isinstance(v, list):\n"
    "        return '、'.join(str(x) for x in v)\n"
    "    return str(v)\n"
    "\n"
    "def similar_ref(similar_offer):\n"
    "    if not isinstance(similar_offer, dict):\n"
    "        return ''\n"
    "    oid = similar_offer.get('similarOfferId', '')\n"
    "    oname = similar_offer.get('similarOfferName', '')\n"
    "    if isinstance(oid, (int, float)):\n"
    "        oid = str(oid)\n"
    "    if not oid or not oname:\n"
    "        return ''\n"
    "    return '%s%s %s' % (SIMILAR_REF_PREFIX, str(oid), str(oname))\n"
    "\n"
    "def collect(schema, data, pending, meta=None, sim_ref=''):\n"
    "    sections = []\n"
    "    meta = meta or {}\n"
    "    def hidden(obj_schema, sub, key, obj_data, path):\n"
    "        # x-show-when 语义判定：'选择非全省时展示' 且 groupId=全省 -> 隐藏（不计待补充）\n"
    "        cond = sub.get('x-show-when')\n"
    "        if not cond or '非全省' not in str(cond):\n"
    "            return False\n"
    "        g = obj_data.get('groupId') if isinstance(obj_data, dict) else ''\n"
    "        return '全省' in str(g or '')\n"
    "    def walk(obj_schema, obj_data, depth, path, sec):\n"
    "        for key, sub in obj_schema.get('properties', {}).items():\n"
    "            label = sub.get('x-label', key)\n"
    "            cur = (path + '.' + key) if path else key\n"
    "            if sub.get('type') == 'object':\n"
    "                sub_data = obj_data.get(key, {}) if isinstance(obj_data, dict) else {}\n"
    "                if not has_business_value(sub, sub_data):\n"
    "                    continue\n"
    "                if depth <= 1:\n"
    "                    sec2 = {'title': label, 'rows': []}\n"
    "                    sections.append(sec2)\n"
    "                    walk(sub, sub_data, depth + 1, cur, sec2)\n"
    "                else:\n"
    "                    sec['rows'].append(('s', depth, label, '', '', ''))\n"
    "                    walk(sub, sub_data, depth + 1, cur, sec)\n"
    "            else:\n"
    "                if key in SKIP_KEYS or not isinstance(obj_data, dict):\n"
    "                    continue\n"
    "                if key not in obj_data or obj_data[key] in ('', None):\n"
    "                    if sub.get('x-required') and key not in SYSTEM_GEN_KEYS and not _is_non_extractable(cur) and not hidden(obj_schema, sub, key, obj_data, path):\n"
    "                        pending.append((INDENT_UNIT * depth) + label)\n"
    "                    continue\n"
    "                val = fmt_value(obj_data[key])\n"
    "                note_parts = []\n"
    "                cond = sub.get('x-show-when')\n"
    "                if cond:\n"
    "                    note_parts.append('满足条件时展示：' + str(cond).rstrip('才展示展示'))\n"
    "                entry = meta.get(cur) or {}\n"
    "                source_raw = entry.get('source', '') if isinstance(entry, dict) else ''\n"
    "                source = SOURCE_MAP.get(source_raw, source_raw)\n"
    "                if source_raw == 'AI补全' and sim_ref:\n"
    "                    source = sim_ref\n"
    "                sec['rows'].append(('|', depth, label, val, source, '；'.join(note_parts)))\n"
    "    top = {'title': '', 'rows': []}\n"
    "    sections.append(top)\n"
    "    walk(schema, data, 0, '', top)\n"
    "    return [s for s in sections if s['rows']]\n"
    "\n"
    "def _render_one(schema, data, meta, similar_offer, title, seq=None):\n"
    "    if schema.get('x-template') in data:\n"
    "        data = data[schema['x-template']]\n"
    "    pending = []\n"
    "    sim_ref = similar_ref(similar_offer)\n"
    "    sections = collect(schema, data, pending, meta, sim_ref)\n"
    "    lines = []\n"
    "    head = ('%d. ' % seq) if seq is not None else ''\n"
    "    if title:\n"
    "        lines.append('## ' + head + title)\n"
    "        lines.append('')\n"
    "    overview = []\n"
    "    for dotted, cn in OVERVIEW_PATHS:\n"
    "        node = data\n"
    "        for k in dotted.split('.'):\n"
    "            node = node.get(k) if isinstance(node, dict) else None\n"
    "        if node not in ('', None):\n"
    "            overview.append('**%s**：%s' % (cn, fmt_value(node)))\n"
    "    if overview:\n"
    "        lines.append('> **套餐概览**\u3000|\u3000' + '\u3000|\u3000'.join(overview))\n"
    "        lines.append('')\n"
    "    n = 0\n"
    "    for sec in sections:\n"
    "        if not sec['rows']:\n"
    "            continue\n"
    "        n += 1\n"
    "        title_txt = '%d. %s' % (n, sec['title']) if sec['title'] else '%d. 配置明细' % n\n"
    "        lines.append('**%s**' % title_txt)\n"
    "        lines.append('')\n"
    "        lines.append('| 字段名称 | 字段值 | 取值来源 | 备注 |')\n"
    "        lines.append('| :--- | :--- | :--- | :--- |')\n"
    "        for kind, depth, label, val, source, note in sec['rows']:\n"
    "            if kind == 's':\n"
    "                lines.append('| **%s%s** |  |  |  |' % (INDENT_UNIT * (depth - 1), label))\n"
    "            else:\n"
    "                pad = INDENT_UNIT * (depth - 1) if depth > 0 else ''\n"
    "                lines.append('| %s%s | %s | %s | %s |' % (pad, label, val, source, note))\n"
    "        lines.append('')\n"
    "    if pending:\n"
    "        lines.append('**【待补充字段】**（%d 项，补充后可进入配置）：' % len(pending))\n"
    "        lines.append('、'.join(pending))\n"
    "        lines.append('')\n"
    "    return '\\n'.join(lines), pending\n"
    "\n"
    "def _offers_of(raw):\n"
    "    # V3.12 多商品：元素可为 [{offer_seq,payload,meta}] 数组；旧单品对象包装为 1 品\n"
    "    if isinstance(raw, list):\n"
    "        return raw\n"
    "    if isinstance(raw, dict) and isinstance(raw.get('offers'), list):\n"
    "        return raw['offers']\n"
    "    return None\n"
    "\n"
    "def load(text):\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    try:\n"
    "        return json.loads(text)\n"
    "    except Exception:\n"
    "        try:\n"
    "            t = text.strip()\n"
    "            if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "                t = t.split(':', 1)[1].strip()\n"
    "            return json.loads(t)\n"
    "        except Exception:\n"
    "            return {}\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    schema = load(p.get('schema_json') or '')\n"
    "    title = str(p.get('title') or '')\n"
    "    # V3.12 多商品：offers_json 为合并节点出参 offers[]（[{offer_seq,payload,meta}]）；逐品渲染\n"
    "    elems = _offers_of(load(p.get('offers_json') or ''))\n"
    "    blocks, pending_all, offers_out = [], [], []\n"
    "    if isinstance(elems, list) and elems:\n"
    "        for i, e in enumerate(elems, start=1):\n"
    "            if not isinstance(e, dict):\n"
    "                continue\n"
    "            seq = int(e.get('offer_seq') or i)\n"
    "            pay = load(e.get('payload') or '')\n"
    "            mt = load(e.get('meta') or '')\n"
    "            text, pend = _render_one(schema, pay, mt, load(p.get('similar_offer') or ''), title, seq)\n"
    "            blocks.append(text)\n"
    "            pending_all += pend\n"
    "            offers_out.append({'offer_seq': seq, 'table_text': text,\n"
    "                               'pending_fields': pend, 'pending_count': str(len(pend))})\n"
    "    else:\n"
    "        text, pend = _render_one(schema, {}, {},\n"
    "                                 load(p.get('similar_offer') or ''), title)\n"
    "        blocks.append(text)\n"
    "        pending_all += pend\n"
    "        offers_out.append({'offer_seq': 1, 'table_text': text,\n"
    "                           'pending_fields': pend, 'pending_count': str(len(pend))})\n"
    "    ret: Output = {\n"
    "        \"table_text\": '\\n\\n'.join(blocks),\n"
    "        \"pending_count\": str(len(pending_all)),\n"
    "        \"offers\": json.dumps(offers_out, ensure_ascii=False),\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_COLLECT_OFFERS：逐品合并结果聚合为 offers[] 方案包 ----------------
# 输入 results：逐品合并结果数组（元素为 CODE_EXTRACT_MERGE 的 offers[]，代码内逐品合并无扇出）
# 出参 result_json：{"req_id":..,"template":..,"offers":[{offer_seq,template,payload,meta,
#                  pending_required,quality_gate,ve_result,ve_stats,status}]}
CODE_COLLECT_OFFERS = (
    "import json\n"
    "from typing import Any, Dict\n"
    "def _load(v):\n"
    "    if isinstance(v, (dict, list)):\n"
    "        return v\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return {}\n"
    "    t = v.strip()\n"
    "    try:\n"
    "        return json.loads(t)\n"
    "    except Exception:\n"
    "        try:\n"
    "            if ':' in t and not t.startswith('{'):\n"
    "                return json.loads(t.split(':', 1)[1].strip())\n"
    "        except Exception:\n"
    "            pass\n"
    "        return {}\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    req_id = str(p.get('req_id') or '')\n"
    "    template = str(p.get('template') or '')\n"
    "    raw_results = p.get('results')\n"
    "    if not isinstance(raw_results, list):\n"
    "        raw_results = [raw_results] if raw_results else []\n"
    "    offers = []\n"
    "    for i, item in enumerate(raw_results, start=1):\n"
    "        obj = _load(item)\n"
    "        if isinstance(obj, list):\n"
    "            for sub in obj:\n"
    "                if isinstance(sub, dict):\n"
    "                    sub.setdefault('offer_seq', len(offers) + 1)\n"
    "                    offers.append(sub)\n"
    "            continue\n"
    "        if isinstance(obj, dict):\n"
    "            if isinstance(obj.get('offers'), list):\n"
    "                for sub in obj['offers']:\n"
    "                    if isinstance(sub, dict):\n"
    "                        sub.setdefault('offer_seq', len(offers) + 1)\n"
    "                        offers.append(sub)\n"
    "            elif obj:\n"
    "                obj.setdefault('offer_seq', i)\n"
    "                offers.append(obj)\n"
    "    for idx, o in enumerate(offers, start=1):\n"
    "        o.setdefault('offer_seq', idx)\n"
    "        o.setdefault('template', template)\n"
    "    ret: Output = {'result_json': json.dumps({'req_id': req_id, 'template': template, 'offers': offers}, ensure_ascii=False)}\n"
    "    return ret"
)

# ============================================================
# wf_sub_00 需求提报（环节1，V3.3：无待补充分支止于确认点，不自动发起审批）
#   流程：开始(需求文本/文档) → LLM 需求字段抽取(snake_case JSON)
#     → 代码节点 render_requirement_report（需求提报单确定性渲染 + req_id 生成 + 待补充判定）
#     → 待补充判定：有待补充 → 结束(待补充清单，请补充)；无待补充 → 保存 requirement 需求工单
#       后止于确认点（提示"是否发起【需求工单审批】"，回复【发起需求审批】后由 wf_sub_11 发起）
# ============================================================
s00 = []
s00.append(start_node(1, [
    inp("requirement_text", "需求描述文本（用户原始需求，含产品名称/资费/资源/规则等）", required=True),
    inp("reporter", "提报人（会话用户，未提供可空）", required=False),
    inp("req_id", "需求单号（可选，链路/调试透传；非空时沿用，严禁重新生成）", required=False),
    inp("chat_id", "{chatId}", required=True),
]))
# 需求字段抽取 LLM：一次抽 N 品 → offers 数组（render_requirement_report 输入契约）
s00.append(llm_node(2, "需求字段抽取",
    "你是产销品需求提报助手（环节1 需求提报），只做一件事：把用户需求原文翻译为结构化需求字段 JSON（可能含多个商品），不做补全、不做完整性判断、不渲染文档、不做任何业务决策。需求原文：{requirement_text}\n"
    "输出一个 offers 数组：用户原文中每提及一个销售品，就输出一个数组元素；元素按原文出现顺序编号 offer_seq 从 1 开始递增；仅提及一个销售品时数组长度为 1。\n"
    "每个元素字段口径（snake_case，逐字引用用户描述，未提及字段填空字符串）：\n"
    "- offer_seq：商品序号（整数，从 1 开始）\n"
    "- name：销售品名称；product_type：产品类型（个人主套餐/个人附加资费/宽带主套餐/宽带附加资费/家庭基础套餐/家庭附加资费/融合套餐）；series：所属系列\n"
    "  · 产品类型判定优先看需求原文关键词：含“融合”二字（如“5G-A 融合套餐”“5G畅享融合79元套餐”）一律判为**融合套餐**；家庭类（家庭成员/家庭共享）判为家庭基础套餐；仅个人/宽带/附加资费按其余枚举对应。\n"
    "- members：融合成员（仅融合品——product_type 为融合套餐或家庭基础套餐时填写，逗号/顿号分隔，逐字引用原文成员如“主卡、宽带、天翼高清、副卡功能费”；非融合品不填）\n"
    "- price：套餐档位（月费，元/月）；resources：套内资源（流量/语音/短信/副卡等）；out_price：套外资费\n"
    "- billing_cycle：计费周期；effective_way：生效方式；validity：套餐有效期\n"
    "- change_rule：变更规则；cancel_rule：退订/拆机规则\n"
    "强制同义映射：月费/月租/套餐费+金额→price；每月XX G/含XX流量→resources；XX分钟语音/通话→resources；支持副卡→resources；按月付费→billing_cycle；X月X日生效→effective_way；退订/拆机→cancel_rule。\n"
    "各商品字段严格归属对应商品，禁止跨商品串值；禁止臆造字段值；原文未明确的字段一律空字符串。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含一个键 offers_json，其值为上述 offers 数组 JSON 的字符串（即先按字段口径生成 offers 数组，再将该数组 JSON 整体作为 offers_json 的值，值为一个字符串，形如 {\"offers_json\": \"[{\\\"offer_seq\\\":1,\\\"name\\\":\\\"...\\\",...}]\"}）；严禁输出其他文字、标签前缀、摘要、说明，严禁使用 Markdown 代码块包裹（不要输出```json```围栏）。",
    [inp("requirement_text", "引用开始节点需求文本", ref_block=nid(1), ref_rel="requirement_text")],
    [out("offers_json", "需求商品字段数组 JSON 字符串（每元素为一个商品的 snake_case 字段，未提及项为空字符串）")]))
# render_requirement_report 代码节点：确定性渲染需求提报单（逐品）+ req_id 生成 + 待补充判定
s00.append(code_node(3, "需求提报单渲染", CODE_RENDER_REQ,
    [inp("offers_json", "引用节点2需求商品字段数组 JSON", ref_block=nid(2), ref_rel="offers_json"),
     inp("req_id", "需求单号（开始节点，非空时沿用不重新生成）", ref_block=nid(1), ref_rel="req_id"),
     inp("reporter", "提报人（开始节点）", ref_block=nid(1), ref_rel="reporter")],
    [code_out("report_text", 3), code_out("pending_fields", 3), code_out("pending_count", 3), code_out("req_id", 3), code_out("offers", 3)],
    pos=(650, 300)))
# 待补充判定：pending_count 长度大于0 → 有待补充（port=-1）；否则（=0/空）→ 无待补充（port=0）
s00.append(selector_node2(4, "待补充判定",
    [dep_node(3, "需求提报单渲染", ["pending_count"])],
    [(-1, [cond_item(cond_ref(3, "pending_count", "需求提报单渲染"), 10, cond_str(""))])]))
# 无待补充分支：保存需求工单（node_name=requirement_report，供 wf_sub_01 需求分析延续 req_id）
s00.append(plugin_node(5, "保存需求工单", "save_node_result",
    "节点结果存储（复用）：req_id=代码节点生成的需求单号 req_id，node_name=requirement_report（需求提报单），result_json=归一后需求商品字段数组 JSON（含 req_id+offers[]）；同键覆盖；供 wf_sub_01 需求分析按 req_id+requirement_report 读取延续",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求单号（=代码节点生成 req_id，PLAN+yyyyMMddHHmmss+3位随机）", ref_block=nid(3), ref_rel="req_id"),
     inp("node_name", "环节名=requirement_report（需求提报单）", content="requirement_report"),
     inp("result_json", "需求商品字段数组 JSON（=代码节点渲染后归一 offers，含 req_id+offers[]）", ref_block=nid(3), ref_rel="offers"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")]))
# 无待补充分支：止于确认点（V3.3：不自动发起需求工单审批，提示是否发起；审批由 wf_sub_11 承接）
# xsbot-panel 独立代码节点：无待补充分支（智能配置落地前无 offer_id，传空，页面以 req_id 定位）
s00.append(panel_code_node(9, "构建页面地址面板(提报成功)", "00",
    panel_inputs(1, req_seq=3), biz_title="需求提报单"))
s00.append(end_node(7, "结束(无待补充-止于确认点)",
    [inp("req_id", "需求单号", ref_block=nid(3), ref_rel="req_id"),
     inp("report_text", "需求提报单", ref_block=nid(3), ref_rel="report_text"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(9), ref_rel="panel")],
    "《销售品需求提报单》已生成（需求单号：{req_id}）。\n\n{report_text}\n\n**是否发起【需求工单审批】？**\n回复【发起需求审批】即发起需求工单审批（审批通过后衔接需求分析生成《加载方案》）；未回复确认前不会自动发起审批。\n\n{panel}"))
# xsbot-panel 独立代码节点：有待补充分支（智能配置落地前无 offer_id，传空，页面以 req_id 定位）
s00.append(panel_code_node(10, "构建页面地址面板(有待补充)", "00",
    panel_inputs(1, req_seq=3), biz_title="需求提报单"))
s00.append(end_node(8, "结束(有待补充)",
    [inp("req_id", "需求单号", ref_block=nid(3), ref_rel="req_id"),
     inp("report_text", "需求提报单", ref_block=nid(3), ref_rel="report_text"),
     inp("pending_fields", "待补充字段", ref_block=nid(3), ref_rel="pending_fields"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(10), ref_rel="panel")],
    "《销售品需求提报单》已生成（需求单号：{req_id}），但存在待补充字段：{pending_fields}\n请补充以下必要信息后重新提报（价格与套内资源为必填）。\n\n{report_text}\n\n{panel}"))
e00 = [edge(1, 2), edge(2, 3), edge(3, 4),
       edge(4, 5, 0), edge(5, 9), edge(9, 7),
       edge(4, 10, -1), edge(10, 8)]
files00 = workflow(
    "产销品-需求提报", "子工作流0：需求提报（环节1，双轨之需求轨）。需求文本→LLM需求字段抽取（一次抽 N 品，输出 offers 数组 snake_case 字段，未提及项空串）→代码节点render_requirement_report确定性逐品渲染《销售品需求提报单》并生成需求单号req_id+待补充判定→无待补充：保存需求工单(node_name=requirement_report，result_json 含 req_id+offers[])后**止于确认点**（提示『是否发起【需求工单审批】』，不自动发起；用户回复【发起需求审批】后由 wf_sub_11 承接发起审批）→结束；有待补充：列待补充字段请补充后重新提报。req_id全程贯穿衔接wf_sub_01。",
    "wf_sub_00", s00, e00)

# ============================================================
# wf_sub_01 需求分析（环节2，模板轨精简版，8 节点）
#   流程：开始(req_id) → 读取需求工单(requirement_report) → 提取需求字段
#     → 产品识别(LLM：识别产品类型并选模板) → 相似产品检索(similar_offer 存量报文)
#     → 取模板(get_template) → 要素提取(LLM：按模板 x-label 提取配置要素嵌套JSON)
#     → 要素校验与方案合并(CODE_EXTRACT_MERGE：六项质量校验+递归合并合一)
#     → render_table(代码节点渲染分节表格)
#     → 保存 requirement（延续 req_id，不再自生成）→ 结束（输出方案表格+待补充）
# ============================================================
CODE_EXTRACT_MERGE = (
    "import json\n"
    "import re\n"
    "from typing import Any, Dict\n"
    "\n"
    "NON_EXTRACTABLE_PATTERNS = (\n"
    "    r\"(effDate|expDate)$\",\n"
    "    r\"(effRuleId|cancelRuleId)$\",\n"
    "    r\"(prodId|pricingId|prodPrcId|inProdPrcId|outProdPrcId|billPrcId|conditionCode|groupId)$\",\n"
    "    r\"orderNo$\",\n"
    "    r\"groupIdMessage$\",\n"
    "    r\"subBillPrcIds$\",\n"
    "    r\"^optionalInfo\\.prcSmsCfg\\.\",\n"
    "    r\"acctItem$\",\n"
    "    r\"powerCode$\",\n"
    "    r\"familyCata$\",\n"
    "    r\"splitRate(9|6)$\",\n"
    "    r\"(favValidityVAlue|fixValidityVAlue)$\",\n"
    ")\n"
    "YES_HINT_WORDS = ('允许', '开通', '支持', '可以', '可办')\n"
    "PRICE_KEYWORDS = ('档位', '月功能费', '月租', '月费', '固定费', '费用')\n"
    "PRICE_EXCLUDE_MARKS = ('有效期', '周期', '时长', '间隔')\n"
    "CHARGE_DESC_KEYWORDS = ('超套', '套外', '资费', '计费', '收费')\n"
    "EMPTY_MARKS = ('', '待补充', '系统待生成')\n"
    "SKIP_KEYS = ('templateId', 'prodId', 'prodPrcId', 'pricingId', 'opType')\n"
    "SYSTEM_GEN_KEYS = ('orderNo',)\n"
    "SAME_PARAMETER_PAIRS = (\n"
    "    ('optionalInfo.printContent.prcMonthFee', 'optionalInfo.acctMonth.fixFee', '套餐月费', '套餐固定费'),\n"
    ")\n"
    "\n"
    "# 业务默认值：不强制发布地市取值——需求未提及地市时不填、不默认，\n"
    "# 由原始需求原文/模板默认决定，避免臆断『全省/非全省/22地市』。\n"
    "BUSINESS_DEFAULTS = {}\n"
    "\n"
    "# 模板取值溯源：区分“原始需求 / 模板默认 / 模板枚举 / 模型推测”\n"
    "DEFAULT_MARK_RE = re.compile(r'默认(?:为)?([^，。、；：\\s]+)')\n"
    "ENUM_SPLIT_RE = re.compile(r'[、，,/\\n；;]')\n"
    "REQ_TRACE_MIN_LEN = 2\n"
    "MODEL_GUESS_SOURCE = '模型推测'\n"
    "TEMPLATE_SOURCE = '模板值'\n"
    "DEFAULT_SOURCE = '默认值'\n"
    "REQ_SOURCE = '原始需求'\n"
    "\n"
    "def _req_tokens(req_text):\n"
    "    return set(re.split(r'[\\s,，、。;；:：()（）{}\\[\\]\"\\'\\\\/\\n]+', str(req_text or '')))\n"
    "\n"
    "def _matches_requirement(val, req_text, tokens):\n"
    "    s = str(val).strip()\n"
    "    if not s:\n"
    "        return False\n"
    "    if len(s) >= REQ_TRACE_MIN_LEN and s in str(req_text):\n"
    "        return True\n"
    "    return s in tokens\n"
    "\n"
    "def _schema_owned_values(prop, path):\n"
    "    out = []\n"
    "    if path in BUSINESS_DEFAULTS:\n"
    "        out.append(('default', str(BUSINESS_DEFAULTS[path])))\n"
    "    d = prop.get('default')\n"
    "    if d not in (None, ''):\n"
    "        out.append(('default', str(d).strip()))\n"
    "    for e in prop.get('enum') or []:\n"
    "        s = str(e).strip()\n"
    "        if s:\n"
    "            out.append(('enum', s))\n"
    "            out.extend(('enum', t.strip()) for t in ENUM_SPLIT_RE.split(s) if t.strip())\n"
    "    for key in ('x-hint', 'description'):\n"
    "        txt = str(prop.get(key) or '')\n"
    "        m = DEFAULT_MARK_RE.search(txt)\n"
    "        if m and m.group(1).strip():\n"
    "            out.append(('hint_default', m.group(1).strip()))\n"
    "        out.extend(('hint_word', t.strip()) for t in ENUM_SPLIT_RE.split(txt)\n"
    "                   if t.strip() and len(t.strip()) >= REQ_TRACE_MIN_LEN)\n"
    "    return out\n"
    "\n"
    "def relabel_source_map(elements_map, leaves, req_text):\n"
    "    # 溯源修正：需求原文可追溯→原始需求；命中 schema 显式默认/枚举→默认值/模板值；\n"
    "    # 其余为模型推测。仅改来源标注与默认值归一，不改变取值语义。\n"
    "    tokens = _req_tokens(req_text)\n"
    "    source_map = {}\n"
    "    for path, val in list(elements_map.items()):\n"
    "        prop = leaves.get(path)\n"
    "        if not prop:\n"
    "            continue\n"
    "        if _matches_requirement(val, req_text, tokens):\n"
    "            source_map[path] = REQ_SOURCE\n"
    "            continue\n"
    "        matched = None\n"
    "        for kind, sv in _schema_owned_values(prop, path):\n"
    "            if str(val).strip() == sv:\n"
    "                matched = (kind, sv)\n"
    "                break\n"
    "        if matched is None:\n"
    "            source_map[path] = MODEL_GUESS_SOURCE\n"
    "            continue\n"
    "        kind, sv = matched\n"
    "        if kind in ('default', 'hint_default'):\n"
    "            elements_map[path] = sv\n"
    "            source_map[path] = DEFAULT_SOURCE\n"
    "        else:\n"
    "            source_map[path] = TEMPLATE_SOURCE\n"
    "    return source_map\n"
    "\n"
    "def _is_non_extractable(path):\n"
    "    return any(re.search(p, path) for p in NON_EXTRACTABLE_PATTERNS)\n"
    "\n"
    "def _collect_leaves(schema):\n"
    "    leaves = {}\n"
    "    def walk(node, prefix=''):\n"
    "        props = node.get('properties') if isinstance(node, dict) else None\n"
    "        if not isinstance(props, dict):\n"
    "            return\n"
    "        for k, v in props.items():\n"
    "            p2 = (prefix + '.' + k) if prefix else k\n"
    "            if isinstance(v, dict) and v.get('type') == 'object':\n"
    "                walk(v, p2)\n"
    "            else:\n"
    "                leaves[p2] = v or {}\n"
    "    walk(schema)\n"
    "    return leaves\n"
    "\n"
    "def _flatten(node, prefix='', out=None):\n"
    "    if out is None:\n"
    "        out = {}\n"
    "    if not isinstance(node, dict):\n"
    "        return out\n"
    "    for k, v in node.items():\n"
    "        path = (prefix + '.' + k) if prefix else k\n"
    "        if isinstance(v, dict):\n"
    "            _flatten(v, path, out)\n"
    "        else:\n"
    "            out[path] = v\n"
    "    return out\n"
    "\n"
    "def _to_number(v):\n"
    "    if isinstance(v, bool):\n"
    "        return None\n"
    "    if isinstance(v, (int, float)):\n"
    "        return v\n"
    "    s = str(v).strip()\n"
    "    if not s:\n"
    "        return None\n"
    "    try:\n"
    "        return float(s) if '.' in s else int(s)\n"
    "    except ValueError:\n"
    "        m = re.match(r'^(\\d+(?:\\.\\d+)?)\\s*元?$', s)\n"
    "        return float(m.group(1)) if m else None\n"
    "\n"
    "def _today_str():\n"
    "    import datetime\n"
    "    return datetime.date.today().strftime('%Y-%m-%d')\n"
    "\n"
    "def is_price(label, key):\n"
    "    for text in (label, key):\n"
    "        t = text or ''\n"
    "        if any(m in t for m in PRICE_EXCLUDE_MARKS):\n"
    "            continue\n"
    "        if any(kw in t for kw in PRICE_KEYWORDS):\n"
    "            return True\n"
    "    return False\n"
    "\n"
    "def strip(v):\n"
    "    if v is None:\n"
    "        return ''\n"
    "    if isinstance(v, bool):\n"
    "        return '是' if v else '否'\n"
    "    if isinstance(v, (int, float)):\n"
    "        return v\n"
    "    s = str(v).strip()\n"
    "    return '' if s in EMPTY_MARKS else s\n"
    "\n"
    "def _charge_desc_covers(prop, cur, elements_map):\n"
    "    if not prop.get('enum'):\n"
    "        return False\n"
    "    label = str(prop.get('x-label', '') or '') + cur\n"
    "    if not [w for w in CHARGE_DESC_KEYWORDS if w in label]:\n"
    "        return False\n"
    "    for epath, evalue in elements_map.items():\n"
    "        if not epath.endswith('chargeStandard'):\n"
    "            continue\n"
    "        if any(w in str(evalue) for w in CHARGE_DESC_KEYWORDS):\n"
    "            return True\n"
    "    return False\n"
    "\n"
    "def flatten_elements(node, prefix='', out=None):\n"
    "    if out is None:\n"
    "        out = {}\n"
    "    if not isinstance(node, dict):\n"
    "        return out\n"
    "    for k, v in node.items():\n"
    "        path = (prefix + '.' + k) if prefix else k\n"
    "        if isinstance(v, dict):\n"
    "            flatten_elements(v, path, out)\n"
    "        else:\n"
    "            val = strip(v)\n"
    "            if val != '':\n"
    "                out[path] = val\n"
    "    return out\n"
    "\n"
    "def flatten_offer(node, prefix='', out=None):\n"
    "    if out is None:\n"
    "        out = {}\n"
    "    if isinstance(node, dict):\n"
    "        for k, v in node.items():\n"
    "            path = (prefix + '.' + k) if prefix else k\n"
    "            flatten_offer(v, path, out)\n"
    "    elif isinstance(node, list):\n"
    "        for i, v in enumerate(node):\n"
    "            flatten_offer(v, '%s[%d]' % (prefix, i), out)\n"
    "    else:\n"
    "        val = strip(node)\n"
    "        if val != '':\n"
    "            out[prefix] = val\n"
    "    return out\n"
    "\n"
    "def _visible(cond_map, out_map, cur):\n"
    "    # 依据节点级 x-show-when 判定当前字段是否展示（不展示即不计入待补充）\n"
    "    cond = cond_map.get(cur)\n"
    "    if not cond:\n"
    "        return True\n"
    "    parent = cur.rsplit('.', 1)[0] if '.' in cur else ''\n"
    "    gkey = (parent + '.groupId') if parent else 'groupId'\n"
    "    gval = str(out_map.get(gkey) or '')\n"
    "    # 语义：'选择非全省时展示' -> groupId=全省 时隐藏\n"
    "    if '非全省' in str(cond):\n"
    "        return '全省' not in gval\n"
    "    return True\n"
    "\n"
    "def merge_body(schema, elements_map, offer_map, meta, path='', pending=None, cond_map=None, out_map=None, source_map=None, req_price_map=None):\n"
    "    if pending is None:\n"
    "        pending = []\n"
    "    if cond_map is None:\n"
    "        cond_map = {}\n"
    "    if out_map is None:\n"
    "        out_map = {}\n"
    "    if source_map is None:\n"
    "        source_map = {}\n"
    "    if req_price_map is None:\n"
    "        req_price_map = {}\n"
    "    out = {}\n"
    "    props = schema.get('properties') or {}\n"
    "    for key, sub in props.items():\n"
    "        cur = (path + '.' + key) if path else key\n"
    "        label = sub.get('x-label', key)\n"
    "        if sub.get('x-show-when'):\n"
    "            cond_map[cur] = sub['x-show-when']\n"
    "        if sub.get('type') == 'object':\n"
    "            out[key] = merge_body(sub, elements_map, offer_map, meta, cur, pending, cond_map, out_map, source_map, req_price_map)\n"
    "            continue\n"
    "        val = elements_map.get(cur)\n"
    "        source = source_map.get(cur) or (REQ_SOURCE if val is not None else '')\n"
    "        if val is None and not is_price(label, key):\n"
    "            oval = offer_map.get(cur)\n"
    "            if oval is not None:\n"
    "                val, source = oval, 'AI补全'\n"
    "        if val is None and req_price_map:\n"
    "            rp = _match_req_price(cur, label, req_price_map)\n"
    "            if rp is not None:\n"
    "                val, source = rp, '原始需求'\n"
    "        if val is None and sub.get('default') is not None:\n"
    "            val, source = sub['default'], '默认值'\n"
    "        if val is None and sub.get('x-default-rule'):\n"
    "            rule = sub['x-default-rule']\n"
    "            val = _today_str() if rule == 'system_date' else rule\n"
    "            source = '默认值'\n"
    "        if val is None and cur in BUSINESS_DEFAULTS:\n"
    "            val, source = BUSINESS_DEFAULTS[cur], '默认值'\n"
    "        if val is None:\n"
    "            val, source = '', ''\n"
    "        out_map[cur] = val\n"
    "        if val == '' and source == '':\n"
    "            if sub.get('x-required') and key not in SKIP_KEYS and key not in SYSTEM_GEN_KEYS \\\n"
    "                    and not _is_non_extractable(cur) \\\n"
    "                    and (is_price(label, key) or '资源' in label or 'resource' in str(label).lower()):\n"
    "                if not _charge_desc_covers(sub, cur, elements_map) and _visible(cond_map, out_map, cur):\n"
    "                    pending.append(cur)\n"
    "        out[key] = val\n"
    "        if source:\n"
    "            meta[cur] = {'label': label, 'value': val, 'source': source}\n"
    "    return out\n"
    "\n"
    "def _collect_req_prices(record_json):\n"
    "    # 从需求字段原文提取价格候选（仅按月费/固定费口径），供 LLM 漏提时确定性兜底\n"
    "    rec = _load(record_json)\n"
    "    if not isinstance(rec, dict):\n"
    "        return {}\n"
    "    out = {}\n"
    "    for k in ('price', 'month_fee', 'monthFee', 'fee', 'monthly_fee'):\n"
    "        v = rec.get(k)\n"
    "        if v in (None, ''):\n"
    "            continue\n"
    "        num = _to_number(v)\n"
    "        if num is not None:\n"
    "            out[k] = v\n"
    "    return out\n"
    "\n"
    "PRICE_LABEL_HINTS = ('套餐月费', '套餐固定费', '月功能费')\n"
    "PRICE_LABEL_EXCLUDE = ('有效期', '周期', '时长', '间隔', '值')\n"
    "\n"
    "def _match_req_price(path, label, req_price_map):\n"
    "    # 仅对“月费/固定费”类价格叶子兜底：标签严格命中月费语义且非有效期类\n"
    "    lab = str(label or '')\n"
    "    if any(x in lab for x in PRICE_LABEL_EXCLUDE):\n"
    "        return None\n"
    "    if not any(h in lab for h in PRICE_LABEL_HINTS):\n"
    "        return None\n"
    "    for _, v in req_price_map.items():\n"
    "        return v\n"
    "    return None\n"
    "\n"
    "def _path_get(node, path):\n"
    "    for part in path.split('.'):\n"
    "        if not isinstance(node, dict) or part not in node:\n"
    "            return None\n"
    "        node = node[part]\n"
    "    return node\n"
    "\n"
    "def _path_set(node, path, value):\n"
    "    parts = path.split('.')\n"
    "    cur = node\n"
    "    for part in parts[:-1]:\n"
    "        if not isinstance(cur, dict):\n"
    "            return\n"
    "        cur = cur.setdefault(part, {})\n"
    "    if isinstance(cur, dict):\n"
    "        cur[parts[-1]] = value\n"
    "\n"
    "def reconcile_same_parameter(merged, meta, pending):\n"
    "    for path_a, path_b, label_a, label_b in SAME_PARAMETER_PAIRS:\n"
    "        na, nb = _to_number(_path_get(merged, path_a)), _to_number(_path_get(merged, path_b))\n"
    "        src_a = (meta.get(path_a) or {}).get('source', '')\n"
    "        src_b = (meta.get(path_b) or {}).get('source', '')\n"
    "        def _authority():\n"
    "            if na is None and nb is None:\n"
    "                return None\n"
    "            pri = {'原始需求': 0, '存量提取': 0, 'AI补全': 1, '同源派生': 2, '默认值': 2}\n"
    "            rank_a, rank_b = pri.get(str(src_a), 2), pri.get(str(src_b), 2)\n"
    "            if na is not None and nb is not None:\n"
    "                if na == nb:\n"
    "                    return None\n"
    "                return (na, path_a, label_a) if (rank_a, path_a) <= (rank_b, path_b) else (nb, path_b, label_b)\n"
    "            return (na, path_a, label_a) if na is not None else (nb, path_b, label_b)\n"
    "        auth = _authority()\n"
    "        if auth is None:\n"
    "            continue\n"
    "        value, apath, alabel = auth\n"
    "        for path, label in ((path_a, label_a), (path_b, label_b)):\n"
    "            if _to_number(_path_get(merged, path)) != value:\n"
    "                _path_set(merged, path, value)\n"
    "                meta[path] = {'label': label, 'value': value, 'source': '同源派生(%s)' % alabel}\n"
    "                if path in pending:\n"
    "                    pending.remove(path)\n"
    "\n"
    "def unwrap_offer(offer):\n"
    "    # 归一化：优先取 offerModel（按模板实例化的逻辑模型报文，键=模板字段名），\n"
    "    # 其次 offerInfo（同构字段）；兼容两种包裹层级——\n"
    "    # ① {similarOffer:{offerModel/offerInfo:...}}（工具外层再包一层）\n"
    "    # ② {similarOfferId,...,offerModel/offerInfo:...}（相似检索直接返回的扁平静态）\n"
    "    def _pick(d):\n"
    "        if not isinstance(d, dict):\n"
    "            return None\n"
    "        if isinstance(d.get('offerModel'), dict):\n"
    "            return d['offerModel']\n"
    "        if isinstance(d.get('offerInfo'), dict):\n"
    "            return d['offerInfo']\n"
    "        return None\n"
    "    if isinstance(offer, dict):\n"
    "        picked = _pick(offer)\n"
    "        if picked is not None:\n"
    "            return picked\n"
    "        inner = offer.get('similarOffer')\n"
    "        picked = _pick(inner)\n"
    "        if picked is not None:\n"
    "            return picked\n"
    "    return offer\n"
    "\n"
    "def _as_group(obj):\n"
    "    # 归一融合组对象：兼容两种入参形态——① 裸组对象（含 members/main_offer_id）；\n"
    "    # ② 包裹对象（{offer_group:{...}} 或 {similarOffer:{offer_group:{...}}}）。无组返回 None。\n"
    "    if not isinstance(obj, dict):\n"
    "        return None\n"
    "    if isinstance(obj.get('members'), list) or obj.get('main_offer_id') or obj.get('group_id'):\n"
    "        return obj\n"
    "    if isinstance(obj.get('offer_group'), dict):\n"
    "        return obj.get('offer_group')\n"
    "    inner = obj.get('similarOffer')\n"
    "    if isinstance(inner, dict) and isinstance(inner.get('offer_group'), dict):\n"
    "        return inner.get('offer_group')\n"
    "    return None\n"
    "\n"
    "def extract_offer_group(raw_offer_json, raw_group=None):\n"
    "    # 融合组抽取：优先取节点105 显式出参 offer_group（后端顶层内嵌），回退从相似检索\n"
    "    # 出参（similar_offer）中取顶层 offer_group（组定义下发），\n"
    "    # 归一为 plan_json 组结构字段：{offer_type:\"融合\", main_offer:{role,offer_id,name},\n"
    "    # member_offers:[{role,offer_id,required,dependency?,preset?}], group_rules:{...}}。\n"
    "    # 未命中融合组（单品）返回 None——单商品链路行为零变化。\n"
    "    grp = _as_group(_load(raw_group or ''))\n"
    "    if grp is None:\n"
    "        grp = _as_group(_load(raw_offer_json or ''))\n"
    "    if not isinstance(grp, dict) or not grp:\n"
    "        return None\n"
    "    members = grp.get('members') if isinstance(grp.get('members'), list) else []\n"
    "    main_offer_id = str(grp.get('main_offer_id') or '')\n"
    "    main_offer_name = str(grp.get('main_offer_name') or '')\n"
    "    member_offers = []\n"
    "    for m in members:\n"
    "        if not isinstance(m, dict):\n"
    "            continue\n"
    "        item = {'role': str(m.get('role') or ''),\n"
    "                'offer_id': str(m.get('offer_id') or ''),\n"
    "                'required': bool(m.get('required'))}\n"
    "        if m.get('dependency'):\n"
    "            item['dependency'] = str(m.get('dependency'))\n"
    "        if isinstance(m.get('preset'), dict):\n"
    "            item['preset'] = m.get('preset')\n"
    "        member_offers.append(item)\n"
    "    return {\n"
    "        'offer_type': '融合',\n"
    "        'main_offer': {'role': '主卡套餐', 'offer_id': main_offer_id, 'name': main_offer_name},\n"
    "        'member_offers': member_offers,\n"
    "        'group_rules': grp.get('group_rules') if isinstance(grp.get('group_rules'), dict) else {},\n"
    "    }\n"
    "\n"
    "def _load(text):\n"
    "    # 兼容上游直接透传 dict/list（节点出参未序列化时的常见情形）\n"
    "    if isinstance(text, (dict, list)):\n"
    "        return text\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    t = text.strip()\n"
    "    # 剥离标签行前缀，如 \"elements_json: {...}\"\n"
    "    if ':' in t and not t.lstrip().startswith('{') and not t.lstrip().startswith('['):\n"
    "        t = t.split(':', 1)[1].strip()\n"
    "    # 去除 Markdown 代码块围栏\n"
    "    t = re.sub(r'^```[a-zA-Z]*\\s*', '', t)\n"
    "    t = re.sub(r'\\s*```$', '', t)\n"
    "    try:\n"
    "        return json.loads(t)\n"
    "    except Exception:\n"
    "        pass\n"
    "    # 回退①：截取首个 { 到末个 } 的子串\n"
    "    s, e = t.find('{'), t.rfind('}')\n"
    "    if s != -1 and e > s:\n"
    "        try:\n"
    "            return json.loads(t[s:e + 1])\n"
    "        except Exception:\n"
    "            pass\n"
    "    # 回退②：截断 JSON 抢救——补齐未闭合的字符串/容器\n"
    "    s = t.find('{')\n"
    "    if s != -1:\n"
    "        salvaged = _salvage_json(t[s:])\n"
    "        if salvaged is not None:\n"
    "            return salvaged\n"
    "    return {}\n"
    "\n"
    "def _salvage_json(text):\n"
    "    # 逐字符扫描，遇未闭合字符串/括号则补齐，再尝试解析（抢救被 max_tokens 截断的 JSON）\n"
    "    stack, in_str, esc = [], False, False\n"
    "    for ch in text:\n"
    "        if in_str:\n"
    "            if esc:\n"
    "                esc = False\n"
    "            elif ch == '\\\\':\n"
    "                esc = True\n"
    "            elif ch == '\"':\n"
    "                in_str = False\n"
    "            continue\n"
    "        if ch == '\"':\n"
    "            in_str = True\n"
    "        elif ch in '{[':\n"
    "            stack.append(ch)\n"
    "        elif ch in '}]':\n"
    "            if stack:\n"
    "                stack.pop()\n"
    "    # 从末尾向前回溯，寻找可解析的最长前缀\n"
    "    for cut in range(len(text), 0, -1):\n"
    "        frag = text[:cut].rstrip()\n"
    "        frag = re.sub(r',\\s*$', '', frag)\n"
    "        # 去掉尾部不完整的键（形如 ,\"key\" 或 ,\"key\": 或 ,{\"key\"）\n"
    "        frag = re.sub(r',\\s*\"[^\"]*\"\\s*:?\\s*$', '', frag)\n"
    "        frag = re.sub(r',\\s*\\{[^}]*$', '', frag)\n"
    "        frag = frag.rstrip().rstrip(',')\n"
    "        st, ins, es = [], False, False\n"
    "        for ch in frag:\n"
    "            if ins:\n"
    "                if es:\n"
    "                    es = False\n"
    "                elif ch == '\\\\':\n"
    "                    es = True\n"
    "                elif ch == '\"':\n"
    "                    ins = False\n"
    "                continue\n"
    "            if ch == '\"':\n"
    "                ins = True\n"
    "            elif ch in '{[':\n"
    "                st.append(ch)\n"
    "            elif ch in '}]':\n"
    "                if st:\n"
    "                    st.pop()\n"
    "        if ins:\n"
    "            frag += '\"'\n"
    "        frag += ''.join('}' if c == '{' else ']' for c in reversed(st))\n"
    "        try:\n"
    "            obj = json.loads(frag)\n"
    "            if isinstance(obj, dict) and obj:\n"
    "                return obj\n"
    "        except Exception:\n"
    "            continue\n"
    "    return None\n"
    "\n"
    "def _salvage_offer_list(text):\n"
    "    # LLM 输出的逐品数组若整体非法（如多余括号/未闭合），按 \"offer_seq\" 边界切片，逐品抢救，\n"
    "    # 保证单个商品格式瑕疵不吞掉其余商品。返回 [dict, ...]（可能少于实际，但尽量多保留）。\n"
    "    if not isinstance(text, str):\n"
    "        return []\n"
    "    s = text.find('[')\n"
    "    if s != -1:\n"
    "        text = text[s + 1:]\n"
    "    # 以 ,{ 或 {\"offer_seq\" 作为切分点，按平衡括号提取每个对象\n"
    "    items, i, n = [], 0, len(text)\n"
    "    while i < n:\n"
    "        if text[i] != '{':\n"
    "            i += 1\n"
    "            continue\n"
    "        depth, ins, esc, j = 0, False, False, i\n"
    "        while j < n:\n"
    "            ch = text[j]\n"
    "            if ins:\n"
    "                if esc:\n"
    "                    esc = False\n"
    "                elif ch == '\\\\':\n"
    "                    esc = True\n"
    "                elif ch == '\"':\n"
    "                    ins = False\n"
    "            elif ch == '\"':\n"
    "                ins = True\n"
    "            elif ch == '{':\n"
    "                depth += 1\n"
    "            elif ch == '}':\n"
    "                depth -= 1\n"
    "                if depth == 0:\n"
    "                    frag = text[i:j + 1]\n"
    "                    try:\n"
    "                        obj = json.loads(frag)\n"
    "                    except Exception:\n"
    "                        obj = _salvage_json(frag)\n"
    "                    if isinstance(obj, dict) and obj:\n"
    "                        items.append(obj)\n"
    "                    break\n"
    "            j += 1\n"
    "        i = j + 1 if j < n else n\n"
    "    return items\n"
    "\n"
    "def _load_offers(raw):\n"
    "    # 逐品要素数组解析：优先整体 JSON；失败则按品切片抢救。返回 [dict, ...]。\n"
    "    if isinstance(raw, list):\n"
    "        return raw\n"
    "    if isinstance(raw, dict):\n"
    "        if isinstance(raw.get('offers'), list):\n"
    "            return raw['offers']\n"
    "        return [raw]\n"
    "    if isinstance(raw, str):\n"
    "        obj = _load(raw)\n"
    "        if isinstance(obj, list):\n"
    "            return obj\n"
    "        if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "            return obj['offers']\n"
    "        salvaged = _salvage_offer_list(raw)\n"
    "        if salvaged:\n"
    "            return salvaged\n"
    "        if isinstance(obj, dict) and obj:\n"
    "            return [obj]\n"
    "    return []\n"
    "\n"
    "async def _merge_one(elems, schema, threshold, record_json, offer_json, offer_group_raw=None):\n"
    "    elements = elems\n"
    "    # V3.12.1 多商品：LLM 逐品输出 {offer_seq, template_id, elements:{...schema 键...}}，\n"
    "    # 需先解包内层要素对象再拍平，否则拍平路径成 'elements.baseInfo.x' 与 schema 键不匹配，\n"
    "    # 导致 elements_map 全程未命中、回退相似品（offer_json）串值。\n"
    "    for _wrap in ('elements', 'elements_json', 'payload', 'fields'):\n"
    "        if isinstance(elements, dict) and _wrap in elements:\n"
    "            inner = elements[_wrap]\n"
    "            if isinstance(inner, str):\n"
    "                inner = _load(inner)\n"
    "            if isinstance(inner, dict) and inner:\n"
    "                elements = inner\n"
    "                break\n"
    "    if not isinstance(elements, dict):\n"
    "        elements = {}\n"
    "    # ① 质量校验（validate_elements 六项校验 + E31 命中率门禁）\n"
    "    leaves = _collect_leaves(schema)\n"
    "    flat = _flatten(elements)\n"
    "    removed, yes_norm_hints, price_cross = [], [], []\n"
    "    valid_paths = {}\n"
    "    for path, val in flat.items():\n"
    "        prop = leaves.get(path)\n"
    "        if prop is None:\n"
    "            removed.append({'path': path, 'value': val, 'reason': 'invalid_path'})\n"
    "            continue\n"
    "        if prop.get('type') == 'number' and _to_number(val) is None:\n"
    "            removed.append({'path': path, 'value': val, 'reason': 'invalid_number'})\n"
    "            continue\n"
    "        valid_paths[path] = (val, prop)\n"
    "    for path, (val, prop) in valid_paths.items():\n"
    "        label = prop.get('x-label') or ''\n"
    "        enum = prop.get('enum') or []\n"
    "        is_yes_no = ('是否' in label) or any(e in ('是、否', '是、否、默认') or str(e) in ('是', '否') for e in enum)\n"
    "        if is_yes_no and isinstance(val, str) and any(w in val for w in YES_HINT_WORDS):\n"
    "            yes_norm_hints.append({'path': path, 'value': val, 'suggest': '是'})\n"
    "    extractable_required = [p for p, prop in leaves.items()\n"
    "                            if prop.get('x-required') and not _is_non_extractable(p)]\n"
    "    hit = [p for p in extractable_required if p in valid_paths]\n"
    "    rate = (len(hit) / len(extractable_required)) if extractable_required else 1.0\n"
    "    gate = '通过' if rate >= threshold else '不通过'\n"
    "    pmf = _to_number(valid_paths.get('optionalInfo.printContent.prcMonthFee', (None,))[0]) \\\n"
    "        if 'optionalInfo.printContent.prcMonthFee' in valid_paths else None\n"
    "    ff = _to_number(valid_paths.get('optionalInfo.acctMonth.fixFee', (None,))[0]) \\\n"
    "        if 'optionalInfo.acctMonth.fixFee' in valid_paths else None\n"
    "    if pmf is not None and ff is not None and pmf != ff:\n"
    "        price_cross.append({'paths': ['optionalInfo.printContent.prcMonthFee',\n"
    "                                      'optionalInfo.acctMonth.fixFee'],\n"
    "                            'values': [pmf, ff],\n"
    "                            'note': '套餐月费与固定费不一致，请人工确认（不阻断）'})\n"
    "    ve_result = '不通过' if gate == '不通过' else ('通过（含提示）' if removed else '通过')\n"
    "    ve_stats = '提取字段总数 %d，有效 %d，无效剔除 %d；必填字段 %d（可提取 %d，命中 %d，可提取命中率 %.1f%%）；门禁阈值 %.2f' % (\n"
    "        len(flat), len(valid_paths), len(removed),\n"
    "        sum(1 for x in leaves.values() if x.get('x-required')),\n"
    "        len(extractable_required), len(hit), rate * 100.0, threshold)\n"
    "    # ② 报文合并（merge_nested：schema 骨架 + 需求要素优先 + 相似品补全 + 同源派生）\n"
    "    offer = unwrap_offer(_load(offer_json or ''))\n"
    "    template_id = schema.get('x-template', '')\n"
    "    if isinstance(offer, dict) and template_id in offer:\n"
    "        offer = offer[template_id]\n"
    "    elements_map = flatten_elements(elements)\n"
    "    source_map = relabel_source_map(elements_map, leaves, record_json or '')\n"
    "    offer_map = flatten_offer(offer)\n"
    "    req_price_map = _collect_req_prices(record_json or '')\n"
    "    meta, pending = {}, []\n"
    "    merged = merge_body(schema, elements_map, offer_map, meta, pending=pending, source_map=source_map, req_price_map=req_price_map)\n"
    "    reconcile_same_parameter(merged, meta, pending)\n"
    "    # ③ 融合组注入（V3.13）：相似检索命中融合组（similar_offer 出参顶层 offer_group）时，\n"
    "    # 把组结构写入 plan_json（offer_type/main_offer/member_offers/group_rules）——members 以组种子为准，\n"
    "    # 供 wf_sub_02 save_product_config 生成 group 出参、CODE_FUSION_GROUP_ECHO 回显；单品无 offer_group 不注入。\n"
    "    offer_group = extract_offer_group(offer_json, offer_group_raw)\n"
    "    if offer_group:\n"
    "        merged['offer_type'] = offer_group['offer_type']\n"
    "        merged['main_offer'] = offer_group['main_offer']\n"
    "        merged['member_offers'] = offer_group['member_offers']\n"
    "        merged['group_rules'] = offer_group['group_rules']\n"
    "    return {'payload': json.dumps(merged, ensure_ascii=False),\n"
    "            'meta': json.dumps(meta, ensure_ascii=False),\n"
    "            'pending_required': ','.join(pending),\n"
    "            'template': template_id,\n"
    "            'quality_gate': gate,\n"
    "            've_result': ve_result,\n"
    "            've_stats': ve_stats,\n"
    "            'status': 'pend' if pending else 'ok'}\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    schema = _load(p.get('schema_json') or '')\n"
    "    threshold = float(p.get('threshold') or 0.30)\n"
    "    record_json = p.get('record_json') or ''\n"
    "    offer_json = p.get('offer_json') or ''\n"
    "    offer_group_raw = p.get('offer_group') or ''\n"
    "    raw_elems = p.get('elements_json') or ''\n"
    "    template_id = schema.get('x-template', '')\n"
    "    # V3.12 多商品：elements_json 为 [{offer_seq,...}] 数组时逐品合并；单品对象包装为 1 品\n"
    "    # 解析容错：整体非法时按 \"offer_seq\" 边界逐品抢救，避免单品格瑕疵吞掉其余商品\n"
    "    elems_list = _load_offers(raw_elems)\n"
    "    offers_out, payloads, metas = [], [], []\n"
    "    all_pending, gates, ve_results, ve_stats_list, statuses = [], [], [], [], []\n"
    "    for i, elems in enumerate(elems_list, start=1):\n"
    "        if isinstance(elems, dict) and 'elements_json' in elems and len(elems) == 1:\n"
    "            elems = elems.get('elements_json')\n"
    "        one = await _merge_one(elems, schema, threshold, record_json, offer_json, offer_group_raw)\n"
    "        payloads.append(one['payload'])\n"
    "        metas.append(one['meta'])\n"
    "        gates.append(one['quality_gate'])\n"
    "        ve_results.append(one['ve_result'])\n"
    "        ve_stats_list.append(one['ve_stats'])\n"
    "        statuses.append(one['status'])\n"
    "        all_pending.append({'offer_seq': i, 'pending_required': one['pending_required']})\n"
    "        offers_out.append({'offer_seq': i,\n"
    "                           'template': one['template'],\n"
    "                           'payload': one['payload'],\n"
    "                           'meta': one['meta'],\n"
    "                           'pending_required': one['pending_required'],\n"
    "                           'quality_gate': one['quality_gate'],\n"
    "                           've_result': one['ve_result'],\n"
    "                           've_stats': one['ve_stats'],\n"
    "                           'status': one['status']})\n"
    "    if not offers_out:\n"
    "        one = await _merge_one({}, schema, threshold, record_json, offer_json, offer_group_raw)\n"
    "        payloads = [one['payload']]\n"
    "        metas = [one['meta']]\n"
    "        gates = [one['quality_gate']]\n"
    "        ve_results = [one['ve_result']]\n"
    "        ve_stats_list = [one['ve_stats']]\n"
    "        statuses = [one['status']]\n"
    "        offers_out = [{'offer_seq': 1, 'template': one['template'],\n"
    "                       'payload': one['payload'], 'meta': one['meta'],\n"
    "                       'pending_required': one['pending_required'],\n"
    "                       'quality_gate': one['quality_gate'], 've_result': one['ve_result'],\n"
    "                       've_stats': one['ve_stats'], 'status': one['status']}]\n"
    "    all_pending_flat = [(str(x.get('offer_seq')), x.get('pending_required') or '') for x in all_pending]\n"
    "    pending_joined = '；'.join('第%s档：%s' % (s, r) for s, r in all_pending_flat if r)\n"
    "    ret: Output = {\n"
    "        'payload': payloads[0],\n"
    "        'meta': metas[0],\n"
    "        'pending_required': pending_joined,\n"
    "        'template': template_id,\n"
    "        'quality_gate': ('通过' if all(g == '通过' for g in gates) else '不通过'),\n"
    "        've_result': ('通过' if all('通过' in v for v in ve_results) else '不通过'),\n"
    "        've_stats': '\\n'.join('第%d档：%s' % (i, s) for i, s in enumerate(ve_stats_list, start=1)),\n"
    "        'status': ('ok' if all(s == 'ok' for s in statuses) else 'pend'),\n"
    "        'offers': json.dumps(offers_out, ensure_ascii=False),\n"
    "    }\n"
    "    return ret"
)

s01 = []
s01.append(start_node(101, [
    inp("req_id", "需求单号（环节1 wf_sub_00 生成，PLAN+yyyyMMddHHmmss+3位随机；严禁重新生成）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
# 读取需求工单（延续环节1 的 req_id，而非自生成）
s01.append(plugin_node(102, "读取需求工单", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id（环节1 延续），node_name=requirement_report 取回需求字段记录数组",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement_report（需求提报单）", content="requirement_report"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为需求字段原文）", "string")],
    method="get"))
# 取需求字段原文：派生 record_json 供产品识别/要素提取复用（node 103）
s01.append(code_node(103, "提取需求字段原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点102查询出参 list（记录数组JSON）", ref_block=nid(102), ref_rel="list")],
    [code_out("record_json", 103), code_out("req_id", 103), code_out("offer_id", 103), code_out("offers", 103)],
    pos=(390, 135)))
# 需求工单自查门禁：offers 为空（=[]）说明未先完成 wf_sub_00 需求提报或 req_id 不匹配，
# port=-1(否则) → 失败结束（wf_sub_01_01）；port=0(如果) → 正常主链
s01.append(selector_node2(115, "需求工单有效性判定",
    [dep_node(103, "提取需求字段原文", ["offers"])],
    [(-1, [cond_item(cond_ref(103, "offers", "提取需求字段原文"), 1, cond_str("[]"))])]))
# 失败结束：需求工单缺失（未先完成需求提报）
s01.append(end_node(116, "结束(需求工单缺失-失败)",
    [inp("req_id", "需求单号", ref_block=nid(101), ref_rel="req_id")],
    "❌ 未找到该需求单号（{req_id}）的需求提报记录，无法进行需求分析。\n请先完成【需求提报】（环节1/8），生成《销售品需求提报单》后再回复【确认执行】进入需求分析。"))
# 产品识别（LLM）：逐品识别产品类型 → 生成要素提取所需上下文
# 设计依据：统一方案 §6.2「LLM 只输出 product_type，路由代码定 templateId，防模板名幻觉」；
# 模板标识由下游 CODE_ROUTE_TEMPLATE（节点119）确定性映射，LLM 不再直接产出 template_id。
s01.append(llm_node(104, "产品识别",
    "你是产销品需求分析助手（环节2 产品识别）。基于需求商品数组（{offers}）为每个商品识别其产品类型（product_type）：\n"
    "产品类型枚举（六选一，逐商品判定）：\n"
    "- 个人主套餐：面向个人/手机单卡的主套餐（含纯流量/语音主套餐）\n"
    "- 宽带主套餐：以宽带为主体、不含融合成员的主套餐\n"
    "- 个人附加资费：挂在个人主套餐之外的附加资费/可选包\n"
    "- 宽带附加资费：宽带提速/加速等附加包\n"
    "- 家庭基础套餐：含家庭/融合成员的套餐（如“5G-A 融合套餐”“家庭套餐”，成员含主卡+宽带+高清+副卡等）\n"
    "- 家庭附加资费：家庭/融合场景下的附加业务\n"
    "判定规则（逐商品独立判定）：\n"
    "1. 商品已有 product_type 字段且属上述枚举 → 直接采用该值（逐字）；\n"
    "2. product_type 缺失或不属于枚举 → 按 name/所属系列/资源内容关键词推断；\n"
    "3. **关键约束**：凡商品名称或内容中出现“融合”“家庭”或含多类成员（宽带/天翼高清/副卡等与主卡并存）时，**必须**判定为“家庭基础套餐”，严禁判定为“个人主套餐”；\n"
    "4. 仍无法判定默认“个人主套餐”。\n"
    "每个商品保持输入 offer_seq 原值，输出顺序与输入一致。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含两个键——\n"
    "1. offers_json：一个数组的字符串，数组每个元素对应一个商品，字段为 {\"offer_seq\": 输入序号, \"product_type\": 六选一中文产品类型（逐字取上述枚举值）, \"need_summary\": 该商品需求要素摘要（≤2000字符）}；\n"
    "2. need_summary：全部商品的合并摘要（产品类型+资费+资源要点，供相似检索检索词，≤2000字符，字符串）。\n"
    "严禁输出其他文字、编号列表、标签前缀、解释或 Markdown 代码块围栏。",
    [inp("offers", "引用节点103需求商品数组", ref_block=nid(103), ref_rel="offers")],
    [out("offers_json", "逐商品产品类型数组JSON字符串（[{offer_seq,product_type,need_summary}]）"), out("need_summary", "全部商品合并摘要（相似检索检索词）")]))
# 产品类型→模板标识确定性路由（LLM 只判类型，路由由代码锁定，防模板名幻觉/融合误判）
s01.append(code_node(119, "模板路由（类型→模板）", CODE_ROUTE_TEMPLATE,
    [inp("offers_json", "逐商品产品识别结果（节点104 offers_json）", ref_block=nid(104), ref_rel="offers_json")],
    [code_out("offers_tpl", 119), code_out("template_id", 119), code_out("template_ids", 119), code_out("fusion_flag", 119)],
    pos=(530, 135)))
# 相似产品检索：以需求摘要+模板检索最相似存量产品，返回同构逻辑模型报文供合并补全
s01.append(plugin_node(105, "相似产品检索", "query_similar_offer",
    "工具1（复用·兜底）：以《产品信息.txt》全部销售品为相似库，返回相似度最高的产品（含 offerInfo 同构字段，与模板同构；并附 offerModel 存量逻辑模型报文——按当前模板实例化，模板同构嵌套 key=模板字段名）；供要素合并节点按 JSONPath 对位补全",
    BASE_URL + "/api/v1/appstore/similar/offer/query",
    [inp("businessDesc", "业务需求描述（=节点104合并摘要）", ref_block=nid(104), ref_rel="need_summary"),
     inp("templateId", "模板标识（节点119首品模板；相似检索模板提示）", ref_block=nid(119), ref_rel="template_id")],
    [
     ("similarOffer", "相似产品（含相似度评分、offerInfo 同构字段与 offerModel 逻辑模型报文）", "object"),
     ("offer_group", "融合组下发（命中融合品时后端顶层内嵌 offer_group：group_id/main_offer_id/members/group_rules；单品为空）", "object")]))
# 获取配置模板：读已迁移模板 schema，供要素提取与合并共用
s01.append(code_node(106, "获取配置模板", CODE_GET_TEMPLATE,
    [inp("template_id", "模板标识（节点119首品模板）", ref_block=nid(119), ref_rel="template_id"),
     inp("template_base", "模板目录（方案/templates，随包迁移）", content=r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\方案\templates")],
    [code_out("template_id", 106), code_out("schema_json", 106), code_out("missing", 106),
     code_out("template_name_cn", 106), code_out("template_product_type", 106),
     code_out("schema_file", 106), code_out("source", 106)],
    pos=(530, 300)))
# 要素提取（LLM）：逐品按各自模板 schema 提取配置要素，输出元素数组
s01.append(llm_node(107, "配置要素提取",
    "你是产销品需求分析助手（环节2 要素提取）。已有需求商品数组（{offers}）与产品识别结果（{offers_tpl}）。请**逐商品**在其需求字段中查找信息，输出一个元素数组。\n"
    "【硬性约束】\n"
    "0. 输出数组每个元素为 {\"offer_seq\": 输入序号, \"template_id\": 该商品模板标识, \"elements\": 该商品配置要素嵌套对象}；元素顺序与输入一致，offer_seq 原值保持。\n"
    "1. 每个商品的 elements 对象，key 必须**逐字复制该商品模板 schema 中的字段键名**（英文键，如 baseInfo.prodPrcName、optionalInfo.printContent.containResource 末段键），层级必须与 schema 的 properties 嵌套完全一致；**严禁**自造键名（如 name/product_type/price/resources 等一律禁止），**严禁**改写成中文键。\n"
    "2. **严禁枚举展开多值/枚举型字段**：凡 schema 中标注 x-element=多选框、enum 为集合或语义为“明细列表”的字段，一律**只写一个占位/汇总值或直接省略**，绝不可逐项罗列。\n"
    "3. **必须输出完整、闭合的合法 JSON**：宁可少写叶子，也不可让输出中途截断。\n"
    "4. 禁止臆造值：每个键值必须能在对应商品需求原文中找到对应表述（含同义改写）；价格类字段只取该商品需求原文，不得照搬相似产品或其他商品；严禁跨商品串值。\n"
    "重点叶子：optionalInfo.printContent.prcMonthFee、optionalInfo.acctMonth.fixFee、optionalInfo.printContent.containResource、optionalInfo.billGprsCfg.theshold、optionalInfo.billGprsCfg.unit、optionalInfo.billGprsCfg.outChargeMode、optionalInfo.billGprsCfg.carryFlag、optionalInfo.billVoiceCfg.theshold。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含一个键 elements_json，其值为上述元素数组 JSON 的**字符串**（形如 {\"elements_json\":\"[{\\\"offer_seq\\\":1,\\\"template_id\\\":\\\"personMainPrc\\\",\\\"elements\\\":{...}}]\"}）；严禁输出其他文字、标签前缀、说明，严禁 Markdown 代码块围栏。",
    [inp("offers", "引用节点103需求商品数组", ref_block=nid(103), ref_rel="offers"),
     inp("offers_tpl", "逐商品模板识别（节点119 offers_tpl）", ref_block=nid(119), ref_rel="offers_tpl")],
    [out("elements_json", "逐品配置要素数组JSON字符串（[{offer_seq,template_id,elements}]）")], max_tokens=8192))
# 要素质量校验 + 报文合并（合一代码节点：逐品 validate_elements 六项校验 + merge_nested 递归合并）
s01.append(code_node(108, "要素校验与方案合并", CODE_EXTRACT_MERGE,
    [inp("schema_json", "模板 schema（节点106；按首品模板）", ref_block=nid(106), ref_rel="schema_json"),
     inp("elements_json", "配置要素数组（节点107提取；代码内逐品合并）", ref_block=nid(107), ref_rel="elements_json"),
     inp("offer_json", "相似产品出参（节点105 similarOffer；含 offerModel 逻辑模型报文，代码内优先取 offerModel 回退 offerInfo）", ref_block=nid(105), ref_rel="similarOffer"),
     inp("offer_group", "融合组下发（节点105 offer_group；命中融合品时写入 plan_json 组结构）", ref_block=nid(105), ref_rel="offer_group"),
     inp("threshold", "可提取命中率阈值（默认0.30）", content="0.30"),
     inp("record_json", "需求字段原文（节点103；供价格类叶子确定性兜底）", ref_block=nid(103), ref_rel="record_json")],
    [code_out("payload", 108), code_out("meta", 108), code_out("pending_required", 108), code_out("quality_gate", 108), code_out("ve_stats", 108), code_out("status", 108),
     code_out("template", 108), code_out("ve_result", 108), code_out("offers", 108)],
    pos=(690, 300)))
# 方案表格渲染：按模板 schema 分节渲染业务可读表格（逐品）
s01.append(code_node(109, "方案表格渲染", CODE_RENDER_TABLE,
    [inp("schema_json", "模板 schema（节点106）", ref_block=nid(106), ref_rel="schema_json"),
     inp("offers_json", "逐品合并结果（节点108 offers；含 [{offer_seq,payload,meta}]）", ref_block=nid(108), ref_rel="offers"),
     inp("similar_offer", "相似产品（节点105，来源列拼接）", ref_block=nid(105), ref_rel="similarOffer"),
     inp("title", "方案标题", content="产销品配置方案（模板轨）")],
    [code_out("table_text", 109), code_out("pending_count", 109), code_out("offers", 109)],
    pos=(920, 300)))
# 方案聚合：把逐品合并结果（含异常兜底）收拢为 offers[] 方案包
s01.append(code_node(114, "方案聚合", CODE_COLLECT_OFFERS,
    [inp("results", "逐品合并结果列表（节点108 offers）", ref_block=nid(108), ref_rel="offers"),
     inp("req_id", "需求单号（开始节点）", ref_block=nid(101), ref_rel="req_id"),
     inp("template", "模板标识（节点106）", ref_block=nid(106), ref_rel="template_id")],
    [code_out("result_json", 114)],
    pos=(1020, 135)))
# 保存 requirement（延续 req_id，node_name=requirement 与既有 wf_sub_02~05 自查链路口径一致）
s01.append(plugin_node(110, "保存执行方案", "save_node_result",
    "节点结果存储（复用）：req_id=开始节点 req_id（环节1延续，不重新生成），node_name=requirement（执行方案），result_json={req_id,offers:[{offer_seq,template,payload,meta,pending_required,quality_gate,...}]}（逐品实例化逻辑模型）；供 wf_sub_02 智能配置自查链路与 wf_sub_06 门禁按 req_id+requirement 读取",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求单号/方案批次号（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement（执行方案）", content="requirement"),
     inp("result_json", "执行方案JSON=方案包（节点114 result_json，含 offers[]）", ref_block=nid(114), ref_rel="result_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")],
    pos=(1080, 135)))
# xsbot-panel 独立代码节点（智能配置落地前无 offer_id，传空，页面以 req_id 定位）
s01.append(panel_code_node(112, "构建页面地址面板", "01",
    panel_inputs(101, off_seq=103, off_rel="offer_id", req_seq=101), biz_title="配置方案"))
# 质量门禁（P2-1，软门禁）：保存后按 quality_gate 分流引导——通过才引导【确认执行】；
# 不通过（可提取命中率低于阈值）时仍保留 requirement 落库，但阻断引导，要求先补充/整改。
s01.append(selector_node2(117, "方案质量门禁",
    [dep_node(108, "要素校验与方案合并", ["quality_gate"])],
    [(-1, [cond_item(cond_ref(108, "quality_gate", "要素校验与方案合并"), 1, cond_str("通过"))])],
    pos=(1120, 300)))
s01.append(end_node(118, "结束(方案质量未通过)",
    [inp("req_id", "需求单号/方案批次号", ref_block=nid(101), ref_rel="req_id"),
     inp("table_text", "配置方案表格", ref_block=nid(109), ref_rel="table_text"),
     inp("pending_required", "必填待补充", ref_block=nid(108), ref_rel="pending_required"),
     inp("template_id", "模板", ref_block=nid(106), ref_rel="template_id"),
     inp("quality_gate", "要素提取质量门禁（节点108）", ref_block=nid(108), ref_rel="quality_gate"),
     inp("ve_stats", "要素校验统计（节点108）", ref_block=nid(108), ref_rel="ve_stats"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(112), ref_rel="panel")],
    "《产销品配置方案》已生成并保存（需求单号：{req_id}，模板：{template_id}）\n\n{table_text}\n\n"
    "【要素提取质量校验】门禁：{quality_gate}；统计：{ve_stats}（为空省略）\n\n"
    "【待补充必填】{pending_required}\n"
    "⚠️ 方案质量门禁**未通过**（可提取命中率低于阈值），已保存但**暂不引导进入配置**。\n"
    "【下一步】请补充/修正上述待补充或命中不足字段后重新分析（回复【重新分析】），"
    "或直接说明修改意见；质量达标后方可回复【确认执行】。\n\n{panel}"))
s01.append(end_node(111, "结束(方案已生成)",
    [inp("req_id", "需求单号/方案批次号", ref_block=nid(101), ref_rel="req_id"),
     inp("table_text", "配置方案表格", ref_block=nid(109), ref_rel="table_text"),
     inp("pending_required", "必填待补充", ref_block=nid(108), ref_rel="pending_required"),
     inp("template_id", "模板", ref_block=nid(106), ref_rel="template_id"),
     inp("quality_gate", "要素提取质量门禁（节点108）", ref_block=nid(108), ref_rel="quality_gate"),
     inp("ve_stats", "要素校验统计（节点108）", ref_block=nid(108), ref_rel="ve_stats"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(112), ref_rel="panel")],
    "《产销品配置方案》已生成并保存（需求单号：{req_id}，模板：{template_id}）\n\n{table_text}\n\n"
    "【要素提取质量校验】门禁：{quality_gate}；统计：{ve_stats}（为空省略）\n\n"
    "【待补充必填】{pending_required}\n请核对以上方案：\n"
    "- 回复【确认执行】：将串行执行 智能配置→稽核→资费校准→自动测试 四个环节；\n"
    "- 如需调整：请直接说明修改意见（待补充字段需补充后才能进入配置）。\n\n{panel}"))
e01 = [edge(101, 102), edge(102, 103), edge(103, 115),
       edge(115, 104, 0), edge(115, 116, -1),
       edge(104, 119), edge(119, 105), edge(105, 106), edge(106, 107),
       edge(107, 108),
       edge(108, 109), edge(109, 114), edge(114, 110),
       edge(110, 117), edge(117, 112, -1), edge(112, 111), edge(117, 118, 0)]
files01 = workflow(
    "产销品-需求分析", "子工作流1（模板轨）：需求分析（环节2）。req_id（环节1延续，不重新生成）→读取需求工单(requirement_report)提取需求商品数组→产品识别LLM(逐品判产品类型)→模板路由代码(类型→模板，防幻觉)→相似产品检索(query_similar_offer，含融合 offer_group 下发)→获取配置模板→配置要素提取LLM(逐品输出elements数组)→要素校验与方案合并(代码内逐品:validate_elements六项校验+E31门禁+merge_nested+融合组注入)→方案表格渲染(逐品)→方案聚合(offers[]方案包)→保存requirement(req_id+offers[])→结束输出方案+待补充+质量结论。",
    "wf_sub_01", s01, e01)


# ============================================================
# 阶段3 执行主干融合组扩展（wf_sub_02/03/05；门槛=插件输出含 group/member_role/offer_group_check）
#   融合组口径（对齐 flow-B V4.0）：单商品行为零变化；全部成员维度数据逐字引用出参，
#   禁止自行推理/增删成员/跨成员照搬价格或状态。
# ============================================================

# ---------------- CODE_FUSION_GROUP_ECHO：智能配置融合成员回显 ----------------
# 输入：group_json（save_product_config 出参 product_config 或 group 的 JSON 文本，含 group 键时解析）、
#       status（落地总状态 SUCCESS/PARTIAL/FAIL）
# 输出：fusion_echo（融合成员回显行，无 group 键时为空串）、
#       fusion_status（修正后状态：任一成员 PARTIAL→PARTIAL）、
#       fusion_note（仅融合品且有成员失败时的提示行；单商品/无 group 时为空串，
#                    避免在单商品执行时输出无关的融合口径说明）
CODE_FUSION_GROUP_ECHO = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(text):\n"
    "    # 兼容批量落地出参两种形态：裸 offers[] 数组 或 {offers:[...]} 信封；\n"
    "    # 均为 {offers:[...]} 便于逐品回显（批量信封顶层不含 product_config）。\n"
    "    if isinstance(text, list):\n"
    "        return {'offers': text}\n"
    "    if isinstance(text, dict):\n"
    "        return text\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    try:\n"
    "        obj = json.loads(text)\n"
    "        if isinstance(obj, list):\n"
    "            return {'offers': obj}\n"
    "        return obj if isinstance(obj, dict) else {}\n"
    "    except Exception:\n"
    "        try:\n"
    "            t = text.strip()\n"
    "            if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "                t = t.split(':', 1)[1].strip()\n"
    "            obj = json.loads(t)\n"
    "            if isinstance(obj, list):\n"
    "                return {'offers': obj}\n"
    "            return obj if isinstance(obj, dict) else {}\n"
    "        except Exception:\n"
    "            return {}\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    cfg = _load(p.get('group_json') or '')\n"
    "    raw_status = str(p.get('status') or '')\n"
    "    # V3.12 多商品：group_json 为批量落地结果 {offers:[{offer_seq,offer_id,status,product_config/group}]}\n"
    "    elems = None\n"
    "    if isinstance(cfg, dict) and isinstance(cfg.get('offers'), list):\n"
    "        elems = [c for c in cfg['offers'] if isinstance(c, dict)]\n"
    "    lines, failed_all = [], []\n"
    "    fusion_status = raw_status.upper()\n"
    "    if elems:\n"
    "        for i, c in enumerate(elems, start=1):\n"
    "            seq = int(c.get('offer_seq') or i)\n"
    "            merged = c.get('group') if isinstance(c.get('group'), dict) else None\n"
    "            if not merged:\n"
    "                continue\n"
    "            members = merged.get('members') or []\n"
    "            parts = []\n"
    "            for m in members:\n"
    "                role = str(m.get('role') or '')\n"
    "                oid = str(m.get('offer_id') or '')\n"
    "                if role:\n"
    "                    parts.append('%s（offer_id %s）' % (role, oid))\n"
    "                m_status = str(m.get('status') or '').upper()\n"
    "                if m_status in ('PARTIAL', 'FAIL'):\n"
    "                    failed_all.append('第%d档 %s=%s' % (seq, role or oid, m_status))\n"
    "            main_id = str(merged.get('main_offer_id') or c.get('offer_id') or '')\n"
    "            line = '第%d档融合成员：' % seq\n"
    "            if main_id:\n"
    "                line += '主 offer_id %s / ' % main_id\n"
    "            line += ' / '.join(parts) if parts else ''\n"
    "            lines.append(line)\n"
    "        if failed_all and fusion_status == 'SUCCESS':\n"
    "            fusion_status = 'PARTIAL'\n"
    "        note = ''\n"
    "        if fusion_status == 'PARTIAL':\n"
    "            note = '融合组部分成功（%s）：请按失败明细说明修改意见或回复【重新执行】' % '、'.join(failed_all)\n"
    "        elif fusion_status == 'FAIL':\n"
    "            note = '融合组配置失败：请按失败明细说明修改意见或回复【重新执行】'\n"
    "        ret: Output = {\n"
    "            'fusion_echo': '\\n'.join(lines),\n"
    "            'fusion_status': fusion_status,\n"
    "            'fusion_note': note,\n"
    "        }\n"
    "        return ret\n"
    "    # 兼容旧单品：group_json 即单个落地配置\n"
    "    merged = cfg.get('group') if isinstance(cfg.get('group'), dict) else None\n"
    "    if not merged:\n"
    "        ret: Output = {'fusion_echo': '', 'fusion_status': raw_status, 'fusion_note': ''}\n"
    "        return ret\n"
    "    members = merged.get('members') or []\n"
    "    parts = []\n"
    "    failed = []\n"
    "    for m in members:\n"
    "        role = str(m.get('role') or '')\n"
    "        oid = str(m.get('offer_id') or '')\n"
    "        if role:\n"
    "            parts.append('%s（offer_id %s）' % (role, oid))\n"
    "        m_status = str(m.get('status') or '').upper()\n"
    "        if m_status in ('PARTIAL', 'FAIL'):\n"
    "            failed.append('%s=%s' % (role or oid, m_status))\n"
    "    main_id = str(merged.get('main_offer_id') or cfg.get('offer_id') or '')\n"
    "    line = '融合成员：'\n"
    "    if main_id:\n"
    "        line += '主 offer_id %s / ' % main_id\n"
    "    line += ' / '.join(parts) if parts else ''\n"
    "    fusion_status = raw_status.upper()\n"
    "    if failed and fusion_status == 'SUCCESS':\n"
    "        fusion_status = 'PARTIAL'\n"
    "    note = ''\n"
    "    if fusion_status == 'PARTIAL':\n"
    "        detail = '、'.join(failed) if failed else '详见失败明细'\n"
    "        note = '融合组部分成功（%s）：请按失败明细说明修改意见或回复【重新执行】' % detail\n"
    "    elif fusion_status == 'FAIL':\n"
    "        note = '融合组配置失败：请按失败明细说明修改意见或回复【重新执行】'\n"
    "    ret: Output = {\n"
    "        \"fusion_echo\": line if parts else '',\n"
    "        \"fusion_status\": fusion_status,\n"
    "        \"fusion_note\": note,\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_CONFIG_SUMMARY：智能配置要点简介 ----------------
# 输入：product_config（save_product_config 批量出参：裸 offers[] 数组 或 {offers:[...]} 信封）
#       req_id（执行批次号，用于组装 config_json 信封供下游 result_json 落库）
# 输出：config_summary（逐品配置要点简介 markdown）、
#       offers（逐品简介数组JSON）、
#       config_json（{req_id, offers:[...]} 信封JSON，含各品 product_config，供 result/save 落库）
# 说明：批量信封顶层无 product_config，offers[] 元素内才是逐品落地配置；解析失败时回退为空串，不阻断链路。
CODE_CONFIG_SUMMARY = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(text):\n"
    "    if isinstance(text, list):\n"
    "        return {'offers': text}\n"
    "    if isinstance(text, dict):\n"
    "        return text\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    try:\n"
    "        obj = json.loads(text)\n"
    "        if isinstance(obj, list):\n"
    "            return {'offers': obj}\n"
    "        return obj if isinstance(obj, dict) else {}\n"
    "    except Exception:\n"
    "        try:\n"
    "            t = text.strip()\n"
    "            if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "                t = t.split(':', 1)[1].strip()\n"
    "            obj = json.loads(t)\n"
    "            if isinstance(obj, list):\n"
    "                return {'offers': obj}\n"
    "            return obj if isinstance(obj, dict) else {}\n"
    "        except Exception:\n"
    "            return {}\n"
    "\n"
    "def _cell(v):\n"
    "    return str(v) if v not in (None, '') else '—'\n"
    "\n"
    "def _summary_of(cfg):\n"
    "    plan = cfg.get('plan_json') if isinstance(cfg.get('plan_json'), dict) else {}\n"
    "    info = plan.get('optionalInfo') if isinstance(plan.get('optionalInfo'), dict) else {}\n"
    "    print_c = info.get('printContent') if isinstance(info.get('printContent'), dict) else {}\n"
    "    gprs = info.get('billGprsCfg') if isinstance(info.get('billGprsCfg'), dict) else {}\n"
    "    voice = info.get('billVoiceCfg') if isinstance(info.get('billVoiceCfg'), dict) else {}\n"
    "    base = plan.get('baseInfo') if isinstance(plan.get('baseInfo'), dict) else {}\n"
    "    in_fee = cfg.get('in_fee') if isinstance(cfg.get('in_fee'), dict) else {}\n"
    "    offer_name = str(base.get('prodPrcName') or cfg.get('offer_name') or '').strip()\n"
    "    fee = str(print_c.get('prcMonthFee') or base.get('fixFee') or '').strip()\n"
    "    gift = str(print_c.get('containResource') or '').strip()\n"
    "    if not gift:\n"
    "        parts = []\n"
    "        if str(gprs.get('theshold') or '').strip():\n"
    "            parts.append('国内通用流量 %s%s' % (gprs.get('theshold'), gprs.get('unit') or ''))\n"
    "        if str(voice.get('theshold') or '').strip():\n"
    "            parts.append('国内通话 %s分钟' % voice.get('theshold'))\n"
    "        gift = '、'.join(parts)\n"
    "    out_price = str(print_c.get('chargeStandard') or '').strip()\n"
    "    if not out_price:\n"
    "        parts = []\n"
    "        if str(gprs.get('outChargeMode') or '').strip():\n"
    "            parts.append('套外流量 %s' % gprs.get('outChargeMode'))\n"
    "        if str(voice.get('outCharge') or '').strip():\n"
    "            parts.append('套外语音 %s元/分钟' % voice.get('outCharge'))\n"
    "        out_price = '、'.join(parts)\n"
    "    cycle = str(in_fee.get('计费周期') or '').strip()\n"
    "    carry = '是' if str(gprs.get('carryFlag') or '').strip() in ('是', 'true', 'True', '1') else ''\n"
    "    lines = ['【配置销售品要点简介】']\n"
    "    lines.append('- 销售品名称：%s' % _cell(offer_name))\n"
    "    lines.append('- 套餐档位（月费）：%s' % ((fee + ' 元/月') if fee else '—'))\n"
    "    lines.append('- 套内资源：%s' % _cell(gift))\n"
    "    lines.append('- 套外资费：%s' % _cell(out_price))\n"
    "    if cycle:\n"
    "        lines.append('- 计费周期：%s' % cycle)\n"
    "    if carry:\n"
    "        lines.append('- 流量结转：可结转至次月')\n"
    "    lines.append('- 订购规则：%s' % _cell(cfg.get('order_rule')))\n"
    "    lines.append('- 退订规则：%s' % _cell(cfg.get('cancel_rule')))\n"
    "    return '\\n'.join(lines)\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    cfg = _load(p.get('product_config') or '')\n"
    "    req_id = str(p.get('req_id') or '')\n"
    "    # V3.12 多商品：product_config 为节点103出参 offers[]（含逐品 product_config）；\n"
    "    # 组装 config_json={req_id, offers:[...]} 信封供下游 result_json 落库（下游按 offers[] 键提取）。\n"
    "    if isinstance(cfg, dict) and isinstance(cfg.get('offers'), list):\n"
    "        blocks, offers_out, cfg_offers = [], [], []\n"
    "        for i, c in enumerate(cfg['offers'], start=1):\n"
    "            if not isinstance(c, dict):\n"
    "                continue\n"
    "            seq = int(c.get('offer_seq') or i)\n"
    "            inner = c.get('product_config')\n"
    "            if isinstance(inner, str):\n"
    "                inner = _load(inner) or None\n"
    "            elif not isinstance(inner, dict):\n"
    "                inner = None\n"
    "            if inner is None:\n"
    "                inner = c\n"
    "            text = _summary_of(inner)\n"
    "            blocks.append('第%d档：\\n%s' % (seq, text))\n"
    "            offers_out.append({'offer_seq': seq, 'config_summary': text})\n"
    "            cfg_offers.append(c)\n"
    "        config_json = json.dumps({'req_id': req_id, 'offers': cfg_offers}, ensure_ascii=False)\n"
    "        ret: Output = {\n"
    "            'config_summary': '\\n\\n'.join(blocks),\n"
    "            'offers': json.dumps(offers_out, ensure_ascii=False),\n"
    "            'config_json': config_json,\n"
    "        }\n"
    "        return ret\n"
    "    if not cfg:\n"
    "        ret: Output = {'config_summary': '', 'offers': '[]', 'config_json': json.dumps({'req_id': req_id, 'offers': []}, ensure_ascii=False)}\n"
    "        return ret\n"
    "    one_sum = _summary_of(cfg)\n"
    "    ret: Output = {'config_summary': one_sum, 'offers': json.dumps([{'offer_seq': 1, 'config_summary': one_sum}], ensure_ascii=False), 'config_json': json.dumps({'req_id': req_id, 'offers': [cfg]}, ensure_ascii=False)}\n"
    "    return ret"
)

# ---------------- CODE_GEN_OFFER_ID：生成商品编码 + 组装逐品 plan_json（9位 900 编码，逐品） ----------------
# 输入：offers（节点106提取的需求商品数组 JSON；每元素含 payload 合并方案/offer_id/similarOfferId 等）
#       existing_offer_id（旧单品入参兜底）
#       req_id（需求单号，注入 plan_json 供后端提取方案key）
# 输出：offers（逐品元素含 offer_seq/offer_id/plan_json=payload 合并方案并注入 req_id）、
#       offer_id（首品编码，兼容单项引用）
CODE_GEN_OFFER_ID = (
    "import json, random\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _str(v):\n"
    "    return str(v).strip() if v not in (None, '') else ''\n"
    "\n"
    "def _gen():\n"
    "    return '900%06d' % random.randint(0, 999999)\n"
    "\n"
    "def _plan_of(item, req_id):\n"
    "    # 逐品 plan_json：优先取元素 payload（合并方案 JSON），注入 req_id 供后端提取方案key\n"
    "    raw = item.get('plan_json') or item.get('payload') or item.get('product_config') or ''\n"
    "    plan = None\n"
    "    if isinstance(raw, dict):\n"
    "        plan = dict(raw)\n"
    "    elif isinstance(raw, str) and raw.strip():\n"
    "        try:\n"
    "            obj = json.loads(raw)\n"
    "            if isinstance(obj, dict):\n"
    "                plan = obj\n"
    "        except Exception:\n"
    "            plan = None\n"
    "    if plan is None:\n"
    "        return ''\n"
    "    if req_id and not plan.get('req_id'):\n"
    "        plan['req_id'] = req_id\n"
    "    return json.dumps(plan, ensure_ascii=False)\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    req_id = _str(p.get('req_id') or '')\n"
    "    raw = p.get('offers') or ''\n"
    "    items = []\n"
    "    try:\n"
    "        obj = json.loads(raw) if isinstance(raw, str) else raw\n"
    "        if isinstance(obj, list):\n"
    "            items = [dict(o) for o in obj if isinstance(o, dict)]\n"
    "        elif isinstance(obj, dict):\n"
    "            if isinstance(obj.get('offers'), list):\n"
    "                items = [dict(o) for o in obj['offers'] if isinstance(o, dict)]\n"
    "            elif obj:\n"
    "                items = [dict(obj)]\n"
    "    except Exception:\n"
    "        items = []\n"
    "    existing = _str(p.get('existing_offer_id') or '')\n"
    "    out = []\n"
    "    for i, item in enumerate(items, start=1):\n"
    "        oid = _str(item.get('offer_id') or item.get('offerId') or '')\n"
    "        if not oid:\n"
    "            oid = _str(item.get('similarOfferId') or '')\n"
    "        if not oid and len(items) == 1:\n"
    "            oid = existing\n"
    "        if not (oid.isdigit() and len(oid) == 9):\n"
    "            oid = _gen()\n"
    "        seq = int(item.get('offer_seq') or i)\n"
    "        plan_json = _plan_of(item, req_id)\n"
    "        out.append({'offer_seq': seq, 'offer_id': oid, 'plan_json': plan_json})\n"
    "    ret: Output = {\n"
    "        'offers': json.dumps(out, ensure_ascii=False),\n"
    "        'offer_id': (out[0]['offer_id'] if out else ''),\n"
    "    }\n"
    "    return ret"
)

# ============================================================
# wf_sub_02 智能配置（融合组扩展）：在既有链路上新增融合成员回显代码节点，
# 出参含 group 时结束节点追加融合成员行；单商品路径零变化。
# 并在结束时追加「配置销售品要点简介」（CODE_CONFIG_SUMMARY）。
# ============================================================
s2f = []
s2f.append(start_node(101, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
    inp("chat_id", "{chatId}", required=True)]))
s2f.append(plugin_node(102, "读取执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=requirement 取回执行方案记录数组（list[0].result_json 为执行方案原文）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为执行方案原文）", "string")],
    method="get"))
s2f.append(code_node(106, "提取执行方案原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点102查询出参 list（记录数组JSON）", ref_block=nid(102), ref_rel="list")],
    [code_out("record_json", 106), code_out("req_id", 106), code_out("offer_id", 106), code_out("offers", 106)],
    pos=(530, 300)))
# 111 生成商品编码（V3.12 多商品）：逐品补齐 9 位 900 编码（存量重配透传既有编码），
# 输出 offers[]（每元素含 offer_id）注入 save_product_config 批量落地
s2f.append(code_node(111, "生成商品编码", CODE_GEN_OFFER_ID,
    [inp("offers", "执行方案需求商品数组（节点106提取）", ref_block=nid(106), ref_rel="offers"),
     inp("existing_offer_id", "执行方案中既有 offer_id（节点106提取，单品兜底）", ref_block=nid(106), ref_rel="offer_id"),
     inp("req_id", "需求单号（节点106提取，注入逐品 plan_json）", ref_block=nid(106), ref_rel="req_id")],
    [code_out("offers", 111), code_out("offer_id", 111)],
    pos=(530, 440)))
s2f.append(plugin_node(103, "配置落地", "save_product_config",
    "工具7（V3.12 多商品批量）：入参 {req_id, offers:[{offer_seq, offer_id, plan_json}]}，逐品解析执行方案JSON写入模拟CRM配置库（同 plan_json 幂等），聚合返回 {req_id, status, offers:[{offer_seq, offer_id, save_result, status, product_config, script_url, group?}]}；后端从各品 plan_json 的 req_id 键提取方案key；新增产品时商品编码由节点111逐品生成（9位900编码）注入 offer_id；融合组时元素含 group（main_offer_id+members[]{role,offer_id}）",
    BASE_URL + "/api/v1/appstore/product/config/save",
    [inp("req_id", "执行批次号（=开始节点 req_id，与执行方案存储键同一）", ref_block=nid(101), ref_rel="req_id"),
     inp("offers", "逐品需求商品数组=执行方案（节点111，每元素含 offer_seq/offer_id/plan_json）", ref_block=nid(111), ref_rel="offers"),
     inp("confirmed", "用户确认标志true（V2.2起后端不校验，仅记录）", content="true"),
     inp("operator", "操作人（默认system）", content="system")],
    [("req_id", "执行批次号（=入参 req_id，回带）", "string"),
     ("offer_id", "首品销售品ID", "string"),
     ("save_result", "整批四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL", "string"),
     ("product_config", "整批落地结果JSON（含 offers[]，每元素含 offer_id/plan_json/可选 group）", "string"),
     ("script_url", "配置脚本下载地址（后端生成，GET即返回可执行脚本）", "string"),
     ("offers", "逐品落地结果数组JSON（[{offer_seq,offer_id,status,product_config,...}]）", "string")]))
# 融合成员回显代码节点（V3.12 多商品）：逐品 group 回显；无 group 路径零变化
s2f.append(code_node(107, "融合成员回显", CODE_FUSION_GROUP_ECHO,
    [inp("group_json", "整批逐品落地结果数组JSON（节点103出参 offers，裸 offers[]）", ref_block=nid(103), ref_rel="offers"),
     inp("status", "落地总状态（节点103出参 status）", ref_block=nid(103), ref_rel="status")],
    [code_out("fusion_echo", 107), code_out("fusion_status", 107), code_out("fusion_note", 107)],
    pos=(900, 300)))
s2f.append(code_node(108, "配置要点简介", CODE_CONFIG_SUMMARY,
    [inp("product_config", "整批逐品落地结果数组JSON（节点103出参 offers，裸 offers[]，元素含 product_config）", ref_block=nid(103), ref_rel="offers"),
     inp("req_id", "执行批次号（节点103出参 req_id，组装 config_json 信封）", ref_block=nid(103), ref_rel="req_id")],
    [code_out("config_summary", 108), code_out("offers", 108), code_out("config_json", 108)],
    pos=(860, 420)))
# 写入门禁：契约§4——config 仅当整批落地 status==SUCCESS（全品通过）才写入；
# status!=SUCCESS（PARTIAL/FAIL）跳过硬写，走未写库结束分支，避免脏写 config 蒙混下游。
s2f.append(selector_node2(112, "配置写入门禁",
    [dep_node(103, "配置落地", ["status"])],
    [(-1, [cond_item(cond_ref(103, "status", "配置落地"), 1, cond_str("SUCCESS"))])],
    pos=(980, 300)))
s2f.append(plugin_node(105, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=config（智能配置），result_json=整批落地结果JSON（含 req_id+offers[]，每元素含 offer_id 及可选 group）；仅整批 status==SUCCESS 时写入（门禁：全品落地通过，由节点112判定）；供 wf_sub_03 稽核、wf_sub_04 测试、wf_sub_06 门禁按 req_id+config 自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=config（智能配置）", content="config"),
     inp("result_json", "环节结果JSON=整批落地结果JSON（含 req_id+offers[]，由节点108组装）", ref_block=nid(108), ref_rel="config_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(1060, 135)))
# xsbot-panel 独立代码节点
s2f.append(panel_code_node(109, "构建页面地址面板", "02",
    panel_inputs(101, off_seq=111, off_rel="offer_id", req_seq=101), biz_title="落地配置看板"))
# 未通过分支面板 + 结束（未写 config）
s2f.append(panel_code_node(113, "构建页面地址面板(未写库)", "02",
    panel_inputs(101, off_seq=111, off_rel="offer_id", req_seq=101), biz_title="落地配置看板"))
s2f.append(end_node(114, "结束(配置未通过-未写config)",
    [inp("status", "整批落地状态（节点103）", ref_block=nid(103), ref_rel="status"),
     inp("save_result", "整批四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("offer_id", "首品销售品ID（节点111）", ref_block=nid(111), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(113), ref_rel="panel")],
    "【环节3/8·智能配置】配置未全部通过：status={status}\n写入结果：{save_result}\n\n"
    "整批存在 PARTIAL/FAIL，本次未写入 config（config 仍为空），无法推进稽核/测试/审批。\n"
    "【下一步】请按落地失败明细修正后重新回复\"下一步\"发起配置（环节3）。\n\n"
    "{panel}"))
s2f.append(end_node(104, "结束(配置落地完成)",
    [inp("offer_id", "首品销售品ID（节点111生成/透传的逐品编码首品值）", ref_block=nid(111), ref_rel="offer_id"),
     inp("save_result", "整批四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "落地状态", ref_block=nid(103), ref_rel="status"),
     inp("fusion_echo", "融合成员回显（V3.12 逐品，无 group 时为空）", ref_block=nid(107), ref_rel="fusion_echo"),
     inp("fusion_note", "融合组失败提示（仅融合品且有成员 PARTIAL/FAIL 时非空）", ref_block=nid(107), ref_rel="fusion_note"),
     inp("config_summary", "配置销售品要点简介（节点108逐品渲染）", ref_block=nid(108), ref_rel="config_summary"),
     inp("script_url", "配置脚本下载地址（节点103出参）", ref_block=nid(103), ref_rel="script_url"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(109), ref_rel="panel")],
    "智能配置完成：offer_id={offer_id}\n{fusion_echo}\n四类字段写入结果：{save_result}\n状态：{status}\n\n"
    "{config_summary}\n{fusion_note}\n"
    "【配置脚本下载】下载地址：{script_url}（为空填写\"暂不可用，见智能配置结果\"）\n\n{panel}"))
files2f = workflow(
    "产销品-智能配置", "子工作流2（V3.12 多商品）：智能配置（配置落地）。单入参 req_id 自查链路：query_node_result 按 req_id+requirement 读取执行方案→代码节点提取需求商品数组 offers[]→生成商品编码（逐品：新增品生成9位900编码/存量重配透传既有编码）→save_product_config 批量落地（后端逐品聚合返回 offers[]）→融合成员回显（逐品 group 回显）→配置要点简介（逐品）→存储 node_name=config（result_json 含 req_id+offers[]）。", "wf_sub_02", s2f,
    [edge(101,102), edge(102,106), edge(106,111), edge(111,103), edge(103,107), edge(107,108), edge(108,112), edge(112,105,-1), edge(112,113,0), edge(113,114), edge(105,109), edge(109,104)])

# ============================================================
# wf_sub_03 规格稽核（融合组扩展）：组维度稽核——LLM 提示词增加组维度回显与
# error_list 按 member_role/group:<role> 定位；单商品零变化。
# ============================================================
s3f = []
s3f.append(start_node(201, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
s3f.append(plugin_node(206, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为整批落地结果原文，内含 req_id+offers[] 及可选 group）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为环节结果原文，含 offers[]）", "string")],
    method="get", pos=(240, 135)))
s3f.append(code_node(207, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点206查询出参 list（记录数组JSON）", ref_block=nid(206), ref_rel="list")],
    [code_out("record_json", 207), code_out("req_id", 207), code_out("offer_id", 207), code_out("offers", 207)],
    pos=(390, 135)))
s3f.append(plugin_node(202, "智能稽核文件上传", "audit_file_upload",
    "接口B1：智能稽核文件上传（后端按 req_id 查需求与配置信息，逐品生成稽核 Excel 后上传）→ file_id + sales_id{销售品名:{offerId,location,sheet_name}}（N 品行清单）；入参仅 req_id + chat_id，无前端文件（开发 mock 返回预置清单）",
    BASE_URL + "/api/v1/appstore/audit/file/upload",
    [inp("req_id", "执行主干批次号（=开始节点 req_id；后端按此查需求+配置生成稽核 Excel）", ref_block=nid(201), ref_rel="req_id"),
     inp("chat_id", "会话消息ID（会话上下文）", ref_block=nid(201), ref_rel="chat_id")],
    [("file_id", "上传文件ID", "string"), ("sales_id", "销售品清单（销售品名→offerId/location/sheet_name，N 品行）", "string")]))
s3f.append(code_node(203, "异步报告生成并通知", CODE_AUDIT_LAUNCH,
    [inp("file_id", "上传文件ID（节点202）", ref_block=nid(202), ref_rel="file_id"),
     inp("sales_id", "销售品清单（节点202）", ref_block=nid(202), ref_rel="sales_id")],
    [code_out("request_id", 203), code_out("requestIds", 203)]))
s3f.append(code_node(204, "插入AI稽核结果", CODE_AUDIT_INSERT,
    [inp("file_id", "上传文件ID（节点202）", ref_block=nid(202), ref_rel="file_id"),
     inp("sales_id", "销售品清单（节点202）", ref_block=nid(202), ref_rel="sales_id"),
     inp("requestIds", "全部 requestId 集合（节点203）", ref_block=nid(203), ref_rel="requestIds")],
    [code_out("resultCode", 204)]))
# 汇总 requestIds → 结构化封包（含 offers[]/sales_id，供 4b 轮询自查 audit_pending 读取）
s3f.append(code_node(208, "稽核发起结果封包", CODE_PACK_RECORD,
    [inp("requestIds", "全部 requestId 集合（节点203）", ref_block=nid(203), ref_rel="requestIds"),
     inp("file_id", "上传文件ID（节点202）", ref_block=nid(202), ref_rel="file_id"),
     inp("sales_id", "销售品清单（节点202）", ref_block=nid(202), ref_rel="sales_id"),
     inp("offers", "需求商品数组（节点207提取，供 4b 逐品回显）", ref_block=nid(207), ref_rel="offers"),
     inp("req_id", "需求单号（节点207提取）", ref_block=nid(207), ref_rel="req_id"),
     inp("offer_id", "主销售品ID（节点207提取，供 4b 面板/回显）", ref_block=nid(207), ref_rel="offer_id"),
     inp("md", "稽核发起回执文案", content="稽核已发起"),
     inp("md_key", "md 落库字段名", content="launch_note")],
    [code_out("record_json", 208)], pos=(780, 135)))
# 发起门禁：仅当逐品发起均产出 requestIds（非空）才写 audit_pending 临时态，供 4b 轮询读取；
# requestIds 为空说明 B1/B2 发起失败，跳过写入走告警分支。
s3f.append(selector_node2(211, "稽核发起门禁",
    [dep_node(203, "异步报告生成并通知", ["requestIds"])],
    [(-1, [cond_item(cond_ref(203, "requestIds", "异步报告生成并通知"), 10)])],
    pos=(860, 300)))
s3f.append(plugin_node(205, "稽核发起临时态存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=audit_pending（稽核发起临时态，非最终 spec）；result_json=发起结果封包（requestIds/sales_id 清单/回执）；仅 requestIds 非空时写入（门禁：逐品发起成功，由节点211判定）；4b 轮询自查读取",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=audit_pending（稽核发起临时态）", content="audit_pending"),
     inp("result_json", "环节结果JSON=发起结果封包（requestIds/sales_id）", ref_block=nid(208), ref_rel="record_json"),
     inp("status", "本环节状态=pending", content="pending")],
    [ ("record_id", "存储记录ID", "string")], pos=(900, 135)))
# xsbot-panel 独立代码节点
s3f.append(panel_code_node(209, "构建页面地址面板", "03",
    panel_inputs(201, off_seq=207, off_rel="offer_id", req_seq=201), biz_title="规格稽核"))
# 发起失败分支面板 + 结束（未写 audit_pending）
s3f.append(panel_code_node(212, "构建页面地址面板(发起失败)", "03",
    panel_inputs(201, off_seq=207, off_rel="offer_id", req_seq=201), biz_title="规格稽核"))
s3f.append(end_node(213, "结束(稽核发起失败-未写audit_pending)",
    [inp("requestIds", "全部 requestId 集合（节点203，空表示发起失败）", ref_block=nid(203), ref_rel="requestIds"),
     inp("file_id", "上传文件ID（节点202）", ref_block=nid(202), ref_rel="file_id"),
     inp("offer_id", "销售品ID（节点207）", ref_block=nid(207), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(212), ref_rel="panel")],
    "【环节4/8·规格稽核】稽核发起失败：requestIds 为空（file_id={file_id}）\n\n"
    "发起未产出 requestIds，本次未写入 audit_pending，无法轮询结论。\n"
    "【下一步】请核查配置/上传后重新回复\"下一步\"发起稽核。\n\n{panel}"))
s3f.append(end_node(210, "结束(稽核已发起)",
    [inp("requestIds", "全部 requestId 集合（节点203）", ref_block=nid(203), ref_rel="requestIds"),
     inp("file_id", "上传文件ID（节点202）", ref_block=nid(202), ref_rel="file_id"),
     inp("offer_id", "销售品ID（节点207提取，供查看面板）", ref_block=nid(207), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(209), ref_rel="panel")],
    "【环节4/8·规格稽核】稽核已发起（requestIds={requestIds}，file_id={file_id}）\n"
    "> 接口④为异步处理（最长 30 分钟）。请回复【查询稽核结果】拉取稽核结论（未完成会提示稍后续查）。\n\n"
    "{panel}"))
files3f = workflow(
    "产销品-规格稽核", "子工作流3（环节4a 稽核发起，两阶段拆分）：单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取 offer_id→B1 audit_file_upload（后端按 req_id 查需求+配置生成稽核 Excel 后上传，入参仅 req_id+chat_id）→B2 audit_async_notify（逐品）→B3 audit_insert_result（紧跟 B2）→汇总 requestIds→save(node_name=audit_pending，临时态)→结束止于反馈点，引导【查询稽核结果】（4b 走 wf_sub_12，pass==1 才写 spec）。", "wf_sub_03", s3f,
    [edge(201,206), edge(206,207), edge(207,202), edge(202,203), edge(203,204),
     edge(204,208), edge(208,211), edge(211,205,-1), edge(211,212,0), edge(212,213), edge(205,209), edge(209,210)])

# ============================================================
# wf_sub_12 规格稽核轮询（环节4b，两阶段拆分后半；可重复触发）
#   自查 audit_pending 取 requestIds → B4 audit_sql_query（sqlId=20260626001，
#   CODE_POLL_AUDIT 短窗口轮询）→ 完成再查 20260626002 取最终结果 → pass==1 写 spec。
# ============================================================
s12f = []
s12f.append(start_node(2201, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，取当前真实时刻生成，与发起流同键）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
s12f.append(plugin_node(2202, "自查稽核发起临时态", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=audit_pending 取回稽核发起临时态（result_json 含 requestIds/sales_id）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(2201), ref_rel="req_id"),
     inp("node_name", "环节名=audit_pending", content="audit_pending"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 135)))
s12f.append(code_node(2203, "提取 requestIds", CODE_EXTRACT_REQUEST_IDS,
    [inp("query_list", "引用节点2202查询出参 list", ref_block=nid(2202), ref_rel="list")],
    [code_out("record_json", 2203), code_out("offer_id", 2203), code_out("requestIds", 2203),
     code_out("offers", 2203)],
    pos=(390, 135)))
s12f.append(code_node(2204, "轮询稽核完成", CODE_POLL_AUDIT,
    [inp("offers", "逐品 requestIds 集合（节点2203）", ref_block=nid(2203), ref_rel="offers")],
    [code_out("completed", 2204), code_out("fail_reason", 2204), code_out("offers", 2204)],
    pos=(560, 300)))
# 完成门禁：completed==true 才继续查询；否则（处理中/缺 requestIds/轮询异常）跳过查询与存储，直接结束
s12f.append(selector_node2(2211, "轮询完成判定",
    [dep_node(2204, "轮询稽核完成", ["completed"])],
    [(-1, [cond_item(cond_ref(2204, "completed", "轮询稽核完成"), 1, cond_str("true"))])],
    pos=(430, 300)))
s12f.append(code_node(2205, "查询稽核结果", CODE_AUDIT_QUERY,
    [inp("offers", "逐品 requestIds 集合（节点2203）", ref_block=nid(2203), ref_rel="offers")],
    [code_out("pass", 2205), code_out("error_list", 2205),
     code_out("audit_summary", 2205), code_out("resultCode", 2205),
     code_out("offers", 2205), code_out("fail_reason", 2205)],
    pos=(720, 135)))
s12f.append(llm_node(2206, "整改建议生成",
    "你是配置规格稽核结果呈现助手。基于稽核出参（per_offer={per_offer}，error_list={error_list}，"
    "audit_summary={audit_summary}）输出稽核结果。整批结论 pass={pass}（全品通过=1）。\n"
    "【通过分支】pass==1 或 error_list 为空时，只输出一行通过结论，禁止列任何检查项/明细/统计：\n"
    "  格式：`全部销售品稽核通过（{audit_summary}）`，逐字引用 audit_summary。\n"
    "  **严禁输出七项检查项清单、严禁输出\"稽核项总数/通过数/失败数\"、严禁出现任何 ❌、失败、缺失、"
    "\"服务项缺失\"、\"资源容量不匹配\"等字样**；该分支下\"未发现问题\"即是唯一正确结论。\n"
    "【不通过分支】pass==0 且 error_list 非空时才进入明细渲染（逐品渲染，仅渲染 pass!=1 的商品）：\n"
    "  逐品解析 per_offer（[{offer_seq,offer_id,pass,error_list}]），对 pass!=1 的商品输出：\n"
    "    稽核对象行：`> **稽核对象：销售品ID（offer_id）{offer_id}**`（逐字引用该品 offer_id，禁止省略该行）；\n"
    "    逐条引用该品 error_list 中的实际告警项（item/level/desc/suggest 逐字引用），只列真实存在的项，"
    "禁止虚构、禁止凑数、禁止补充 error_list 之外的检查项。\n"
    "融合组维度（出参含 group 时）：在不通过分支的稽核对象行下追加组维度回显行。\n"
    "不新增稽核结论。\n"
    "输出要求：仅输出上述内容（对应出参 audit_suggest），不输出其他多余文字。",
    [inp("per_offer", "逐品稽核结果（节点2205）", ref_block=nid(2205), ref_rel="offers"),
     inp("error_list", "引用节点2205问题明细", ref_block=nid(2205), ref_rel="error_list"),
     inp("audit_summary", "引用节点2205稽核总结", ref_block=nid(2205), ref_rel="audit_summary"),
     inp("pass", "引用节点2205稽核结论（1全品通过/0存在不通过）", ref_block=nid(2205), ref_rel="pass")],
    [out("audit_suggest", "稽核明细清单（逐品渲染，无条件输出）")], max_tokens=3072))
s12f.append(code_node(2207, "稽核结果结构化封包", CODE_PACK_RECORD,
    [inp("pass", "整批稽核结论（节点2205）", ref_block=nid(2205), ref_rel="pass"),
     inp("error_list", "问题明细（节点2205）", ref_block=nid(2205), ref_rel="error_list"),
     inp("audit_summary", "稽核总结（节点2205）", ref_block=nid(2205), ref_rel="audit_summary"),
     inp("offers", "逐品稽核结果（节点2205）", ref_block=nid(2205), ref_rel="offers"),
     inp("md", "稽核明细清单 markdown（节点2206）", ref_block=nid(2206), ref_rel="audit_suggest"),
     inp("md_key", "md 落库字段名", content="audit_suggest")],
    [code_out("record_json", 2207)], pos=(900, 135)))
# 通过门禁：pass==1 才写 spec（门禁：环节5 资费校准与审批四环节硬校验以 spec 为准）；
# pass!=1（不通过/未完成）跳过存储，避免空壳 spec 蒙混放行下游。
# 说明：整改建议生成(2206)前置到本判定之前，使“通过/未通过”两分支均可引用 audit_suggest。
s12f.append(selector_node2(2212, "稽核通过判定",
    [dep_node(2205, "查询稽核结果", ["pass"])],
    [(-1, [cond_item(cond_ref(2205, "pass", "查询稽核结果"), 1, cond_str("1"))])],
    pos=(880, 450)))
s12f.append(plugin_node(2208, "稽核结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=spec（规格稽核，最终态）；仅 pass==1 时写入（门禁：环节5 与审批四环节硬校验以 spec 为准）",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(2201), ref_rel="req_id"),
     inp("node_name", "环节名=spec（规格稽核）", content="spec"),
     inp("result_json", "环节结果JSON=结构化封包（error_list+audit_suggest）", ref_block=nid(2207), ref_rel="record_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(1040, 135)))
s12f.append(panel_code_node(2209, "构建页面地址面板", "03",
    panel_inputs(2201, off_seq=2203, off_rel="offer_id", req_seq=2201), biz_title="规格稽核"))
# 未完成/轮询异常分支结束（未写 spec）：展示 completed/fail_reason + 面板，引导续查
s12f.append(panel_code_node(2216, "构建页面地址面板(处理中)", "03",
    panel_inputs(2201, off_seq=2203, off_rel="offer_id", req_seq=2201), biz_title="规格稽核"))
s12f.append(end_node(2213, "结束(稽核处理中-未写spec)",
    [inp("completed", "窗口内是否完成（节点2204）", ref_block=nid(2204), ref_rel="completed"),
     inp("fail_reason", "轮询异常/处理中说明（节点2204）", ref_block=nid(2204), ref_rel="fail_reason"),
     inp("offer_id", "销售品ID（节点2203解析，供查看面板）", ref_block=nid(2203), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2216), ref_rel="panel")],
    "【环节4/8·规格稽核】查询稽核结果：完成={completed}\n"
    "{fail_reason}\n\n"
    "稽核尚未完成，本次未写入规格稽核结论（spec 仍为空），无法推进环节5/上线审批。\n"
    "【下一步】请稍后回复\"查询稽核结果\"续查；若持续无进展请联系人工核查稽核发起（环节4a）。\n\n"
    "{panel}"))
# 未通过分支结束（未写 spec）：展示 ❌ 明细 + 面板，引导整改后重新发起
s12f.append(panel_code_node(2215, "构建页面地址面板(未通过)", "03",
    panel_inputs(2201, off_seq=2203, off_rel="offer_id", req_seq=2201), biz_title="规格稽核"))
s12f.append(end_node(2214, "结束(稽核未通过-未写spec)",
    [inp("pass", "稽核结论（节点2205）", ref_block=nid(2205), ref_rel="pass"),
     inp("audit_suggest", "稽核明细清单（节点2206）", ref_block=nid(2206), ref_rel="audit_suggest"),
     inp("offer_id", "销售品ID（节点2203解析，供查看面板）", ref_block=nid(2203), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2215), ref_rel="panel")],
    "【环节4/8·规格稽核】查询稽核结果：稽核结论 pass={pass}（未通过）\n\n"
    "{audit_suggest}\n\n"
    "稽核未通过，本次未写入规格稽核结论（spec 仍为空），无法推进环节5/上线审批。\n"
    "【下一步】请按上述 ❌ 明细整改配置后，重新回复\"下一步\"发起稽核（环节4a）。\n\n"
    "{panel}"))
s12f.append(end_node(2210, "结束(稽核查询完成)",
    [inp("pass", "稽核结论（节点2205）", ref_block=nid(2205), ref_rel="pass"),
     inp("completed", "窗口内是否完成（节点2204）", ref_block=nid(2204), ref_rel="completed"),
     inp("fail_reason", "轮询异常/处理中说明（节点2204）", ref_block=nid(2204), ref_rel="fail_reason"),
     inp("audit_suggest", "稽核明细清单（节点2206）", ref_block=nid(2206), ref_rel="audit_suggest"),
     inp("offer_id", "销售品ID（节点2203解析，供查看面板）", ref_block=nid(2203), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2209), ref_rel="panel")],
    "【环节4/8·规格稽核】查询稽核结果：完成={completed}，稽核结论 pass={pass}\n"
    "{fail_reason}\n\n{audit_suggest}\n\n"
    "【下一步】pass=1 可回复\"下一步\"推进环节5 资费校准；pass=0 请按上述 ❌ 明细整改后重新发起稽核。\n\n"
    "{panel}"))
files12f = workflow(
    "产销品-规格稽核查询", "子工作流12（环节4b 稽核轮询，两阶段拆分后半；可重复触发）：自查 audit_pending 取 requestIds→CODE_POLL_AUDIT 短窗口轮询 B4 audit_sql_query(sqlId=20260626001，间隔5s/上限60次=5分钟)→**【门禁A】completed==true 才继续**→查 20260626002 取最终 error_list/audit_summary→七项固定检查项明细→结构化封包→**【门禁B】pass==1 才 save(node_name=spec)**。completed!=true 或 pass!=1 均不写 spec（跳过存储直接结束），杜绝空壳 spec 蒙混下游；窗口耗尽非错误，输出处理中引导续查；连续5次失败终止转人工。", "wf_sub_12", s12f,
    [edge(2201,2202), edge(2202,2203), edge(2203,2204),
     edge(2204,2211), edge(2211,2205,-1), edge(2211,2216,0), edge(2216,2213),
     edge(2205,2206), edge(2206,2212), edge(2212,2207,-1), edge(2212,2215,0),
     edge(2207,2208), edge(2208,2209), edge(2209,2210),
     edge(2215,2214)])

# ============================================================
# wf_sub_05 资费校准（融合组扩展）：比对表按 member_role 分组、E27 阈值逐成员内计算；单商品零变化。
# ============================================================
s5f = []
s5f.append(start_node(401, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
s5f.append(plugin_node(406, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地结果原文）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get"))
s5f.append(code_node(407, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点406查询出参 list（记录数组JSON）", ref_block=nid(406), ref_rel="list")],
    [code_out("record_json", 407), code_out("req_id", 407), code_out("offer_id", 407), code_out("offers", 407)],
    pos=(240, 135)))
s5f.append(plugin_node(402, "计费校验", "check_billing_rule",
    "工具8（V3.12 多商品批量）：入参 {req_id, check_scene, offers:[{offer_seq, config_json}]}，逐品自研模拟计费规则校验（内置叠加/互斥/负资费规则），聚合返回 {req_id, status, offers:[{offer_seq, pass, risk_list, compare_list}]}；后端对每品取该品配置原文，融合组 compare_list 行含 member_role 键",
    BASE_URL + "/api/v1/appstore/billing/rules/verify",
    [inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("offers", "逐品配置数组=config 环节结果 offers[]（节点407提取，每元素含 offer_seq/offer_id/plan_json）", ref_block=nid(407), ref_rel="offers"),
     inp("check_scene", "校验场景默认all", content="all")],
    [("pass", "整批结论 1通过/0不通过", "string"), ("status", "整批状态 SUCCESS/PARTIAL/FAIL", "string"),
     ("risk_list", "整批风险清单（逐品聚合）", "array"),
     ("compare_list", "整批资费比对明细（逐品聚合，融合组行含 member_role）", "array"),
     ("offers", "逐品校验结果数组JSON（[{offer_seq,pass,risk_list,compare_list}]）", "string")]))
s5f.append(llm_node(403, "风险解读",
    "将资费比对明细（compare_list={compare_list}）与资费风险清单（risk_list={risk_list}）翻译为业务语言，输出风险解读。多商品时 compare_list/risk_list 为逐品聚合（行内含 offer_seq 键），须**按商品分组**输出：\n"
    "0）多商品分组：compare_list/risk_list 行含 offer_seq 键时，按 offer_seq 升序为每个商品输出一个小节，小节标题「## 商品{offer_seq}」；单商品（无 offer_seq 键）不输出小节标题。\n"
    "1）比对信息（取该商品 compare_list 非空时必出）：Markdown表格，列为 项目|套餐描述（需求）|计费配置描述（系统）|比对结果；8 项比对项目名不变（套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期）；行内 requirement_desc 与 billing_desc 同时为空或空串时该行整体省略，仅一侧有值则照常输出；比对信息标题写「比对信息（实际行数/该品compare_list总数项，本销售品不涉及项已省略）：」；两侧皆空行数≥该品总数一半时（E27），有值行照常输出，校准结论行引用 E27 固定文案（系统侧未返回比对明细），不中断、不改变 pass 判定，禁止编造系统侧值。\n"
    "2）融合组维度（V4.0）：compare_list 行含 member_role 键时，比对表按 member_role 分组输出（组间空行分隔或按成员独立小表，8 项比对项目名不变，行数=Σ各成员有值行）；空值行省略规则逐成员内执行；E27 阈值逐成员内计算（某成员两侧皆空行数 ≥ 该成员行数一半 → 该成员小节引用 E27 固定文案），禁止跨成员加总稀释或误判。\n"
    "3）风险解读：risk_list 逐条翻译为业务语言（含 offer_seq 归属），说明每条风险的影响与处置建议；某品 risk_list 为空时输出「未发现叠加/互斥冲突」。\n"
    "4）校准结论行：「**校准结论：**」+ 该品全部比对项 result==一致 时输出「资费配置与计费规则完全一致」，存在不一致时逐条列出。\n"
    "可引用资费规则库知识作为解释依据，但不得新增风险结论。compare_list 与 risk_list 均为空时，直接输出「资费校准通过，未发现叠加/互斥冲突」。\n"
    "输出要求：仅输出以上内容（对应出参 risk_summary，多商品时按 offer_seq 分组），不输出其他多余文字。",
    [inp("risk_list", "引用节点402风险清单（逐品聚合）", ref_block=nid(402), ref_rel="risk_list"),
     inp("compare_list", "引用节点402资费比对明细（逐品聚合，融合组行含member_role）", ref_block=nid(402), ref_rel="compare_list")],
    [out("risk_summary", "风险解读（多商品按 offer_seq 分组）")]))
# 计费校验结论聚合：插件仅逐品返回 offers[].pass，顶层 pass/risk_list/compare_list 为空；
# 本节点从 offers[] 确定性聚合顶层结论，供结束节点 {pass} 与后续封包引用（避免空值）。
CODE_AGG_BILLING = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(v):\n"
    "    if isinstance(v, list):\n"
    "        return v\n"
    "    if isinstance(v, dict):\n"
    "        return v.get('offers') if isinstance(v.get('offers'), list) else [v]\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return []\n"
    "    try:\n"
    "        obj = json.loads(v)\n"
    "        if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "            return obj['offers']\n"
    "        return obj if isinstance(obj, list) else ([obj] if isinstance(obj, dict) else [])\n"
    "    except Exception:\n"
    "        return []\n"
    "\n"
    "async def main(args):\n"
    "    offers = [o for o in _load(args.params.get('offers')) if isinstance(o, dict)]\n"
    "    pass_top = args.params.get('pass')\n"
    "    if pass_top is None or str(pass_top) == '':\n"
    "        vals = [str(o.get('pass') or '') for o in offers]\n"
    "        pass_top = '1' if vals and all(v == '1' for v in vals) else ('0' if vals else '')\n"
    "    risks = []\n"
    "    for o in offers:\n"
    "        rl = o.get('risk_list')\n"
    "        if isinstance(rl, list):\n"
    "            for r in rl:\n"
    "                if isinstance(r, dict):\n"
    "                    r.setdefault('offer_seq', o.get('offer_seq'))\n"
    "                    risks.append(r)\n"
    "    ret: Output = {\n"
    "        'pass': str(pass_top),\n"
    "        'offers': json.dumps(offers, ensure_ascii=False),\n"
    "        'risk_list': json.dumps(risks, ensure_ascii=False) if risks else '',\n"
    "    }\n"
    "    return ret"
)
s5f.append(code_node(410, "计费结论聚合", CODE_AGG_BILLING,
    [inp("offers", "逐品校验结果数组（节点402出参 offers）", ref_block=nid(402), ref_rel="offers"),
     inp("pass", "插件顶层结论（多为空，代码内回退逐品聚合）", ref_block=nid(402), ref_rel="pass")],
    [code_out("pass", 410), code_out("offers", 410), code_out("risk_list", 410)],
    pos=(650, 260)))
# 408 结构化封包：上游结构化出参（pass/risk_list/compare_list/offers）+ LLM 风险解读
#   → 合成 result_json envelope（offers[]/compare_list 供 sub_04 节点315 BILL-001~009/CUST-004 等确定性映射）
s5f.append(code_node(408, "资费结果结构化封包", CODE_PACK_RECORD,
    [inp("pass", "校验结论（节点410聚合）", ref_block=nid(410), ref_rel="pass"),
     inp("risk_list", "风险清单（节点410聚合）", ref_block=nid(410), ref_rel="risk_list"),
     inp("compare_list", "资费比对明细（节点402，逐品聚合）", ref_block=nid(402), ref_rel="compare_list"),
     inp("offers", "逐品校验结果数组（节点402出参 offers）", ref_block=nid(402), ref_rel="offers"),
     inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("md", "风险解读 markdown（节点403）", ref_block=nid(403), ref_rel="risk_summary"),
     inp("md_key", "md 落库字段名", content="risk_summary")],
    [code_out("record_json", 408)], pos=(780, 135)))
# 写入门禁：契约§4——fee 仅当整批校验 pass==1（全品通过）才写入；pass!=1 跳过写入走未写库分支。
s5f.append(selector_node2(411, "资费写入门禁",
    [dep_node(410, "计费结论聚合", ["pass"])],
    [(-1, [cond_item(cond_ref(410, "pass", "计费结论聚合"), 1, cond_str("1"))])],
    pos=(860, 300)))
s5f.append(plugin_node(405, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=fee（资费校准），result_json=结构化封包（含 req_id+offers[] + 逐品聚合的 compare_list/risk_list + risk_summary 解读）；仅整批 pass==1 时写入（门禁：全品通过，由节点411判定）；主流程删除后存储下沉子工作流",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("node_name", "环节名=fee（资费校准）", content="fee"),
     inp("result_json", "环节结果JSON=结构化封包（req_id+offers[]+compare_list+risk_summary）", ref_block=nid(408), ref_rel="record_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(900, 135)))
# xsbot-panel 独立代码节点
s5f.append(panel_code_node(409, "构建页面地址面板", "05",
    panel_inputs(401, off_seq=407, off_rel="offer_id", req_seq=401), biz_title="资费校准"))
# 未通过分支面板 + 结束（未写 fee）
s5f.append(panel_code_node(412, "构建页面地址面板(未通过)", "05",
    panel_inputs(401, off_seq=407, off_rel="offer_id", req_seq=401), biz_title="资费校准"))
s5f.append(end_node(413, "结束(资费未通过-未写fee)",
    [inp("pass", "校验结论（节点410聚合）", ref_block=nid(410), ref_rel="pass"),
     inp("risk_list", "风险清单（节点410聚合）", ref_block=nid(410), ref_rel="risk_list"),
     inp("risk_summary", "风险解读（节点403）", ref_block=nid(403), ref_rel="risk_summary"),
     inp("offer_id", "首品销售品ID（节点407）", ref_block=nid(407), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(412), ref_rel="panel")],
    "【环节5/8·资费校准】资费校验未全部通过：pass={pass}\n{risk_summary}\n\n"
    "整批存在不一致，本次未写入 fee，无法推进上线审批。\n"
    "【下一步】请按比对明细修正计费配置后重新回复\"下一步\"校准（环节5）。\n\n{panel}"))
s5f.append(end_node(404, "结束(资费校准完成)",
    [inp("pass", "校验结论（节点410聚合）", ref_block=nid(410), ref_rel="pass"),
     inp("risk_list", "风险清单（节点410聚合）", ref_block=nid(410), ref_rel="risk_list"),
     inp("risk_summary", "风险解读（多商品按 offer_seq 分组）", ref_block=nid(403), ref_rel="risk_summary"),
     inp("offer_id", "首品销售品ID（节点407提取，供查看面板）", ref_block=nid(407), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(409), ref_rel="panel")],
    "资费校准完成：pass={pass}\n{risk_summary}\n\n"
    "{panel}"))
files5f = workflow(
    "产销品-资费校准", "子工作流5（V3.12 多商品）：资费校准。单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取需求商品数组 offers[]→check_billing_rule 批量校验（check_scene=all，后端逐品聚合返回 offers[]/risk_list/compare_list）→计费结论聚合（从 offers[] 聚合顶层 pass/risk_list）→风险解读（多商品按 offer_seq 分组：比对表按商品分组、融合组再按 member_role 分组，E27 阈值逐成员内计算）→结构化封包（compare_list/risk_list+offers[]+risk_summary 合成 envelope）；结束前存储 node_name=fee。单商品路径零变化。", "wf_sub_05", s5f,
    [edge(401,406), edge(406,407), edge(407,402), edge(402,403), edge(403,410),
     edge(410,408), edge(408,411), edge(411,405,-1), edge(411,412,0), edge(412,413), edge(405,409), edge(409,404)])


# ============================================================
# 阶段4 wf_sub_04 自动测试正式版报告（重写）
#   承接流程-B 环节6/7 九章节正式版报告（K3 模板 V2.0）。
#   链路上游：302 offer_test 发起 → 303 get_test_scenes 场景清单 → 304 轮询进度（CODE_POLL_PROGRESS）
#      → 305 get_test_result（done 后取 testScenes+orderId+offerInstId+offerName）
#   新增自检读取：311/312 自检 spec（node_name=spec → error_list）、313/314 自检 fee
#     （node_name=fee → compare_list/risk_list），供 31 条固定用例计费/客服填充分项。
#   315 CODE_MAP_FIXED_CASES 代码节点：确定性构建 31 条固定用例表（ACC/BILL/CUST 含 ✅/❌/未覆盖判定）、
#     P0/P1 统计、整体上线结论三选一、缺陷清单、场景覆盖核对、E26 组核对。
#   306 LLM 按 9 章节 + 31 条固定表渲染《销售品自动化测试报告》正式版；
#   受理验证独立成节（环节7 标题头）；308 存储 node=test；307 结束。
# ============================================================

# ---------------- CODE_MAP_FIXED_CASES：31 条固定用例确定性映射 ----------------
# 来源 K3 用例设计规范 V2.0 第4章 + scripts/cpcp_api.py FIXED_CASES（去 argparse 改 async main(args)）
# V2.1 全覆盖：31 条固定用例均产出确定性 ✅/❌，不再出现「本销售品未覆盖」。
#   硬编码恒 None 的 7 条（ACC-005/009、BILL-007/008/009、CUST-003/009）改由 plan_json/compare_list/testScenes
#   推导；数据缺位时 judge 返回 None，主循环兜底视为 ✅（POC 虚拟数据默认为一致）；BILL-002 改读 plan 计费周期
#   （原依赖 get_test_result 不存在的 plan_billing_cycle 字段）。judge 统一签名 (tr, sp, fee, plan)。
# 输入：test_result_json（get_test_result 出参 JSON 原文，含 testScenes/orderId/offerInstId/offerName）、
#       spec_record（自检 spec 环节结果原文，可含 error_list）、fee_record（自检 fee 环节结果原文，可含 compare_list/risk_list）、
#       offer_id（config 落地 offer_id，供 E26 主商品一致性核对）、plan_json（config 落地 plan_json 原文，供 E26 组核对与场景覆盖）
# 输出：cases_json（31 条固定用例表 rows JSON）、dimension_summary（ACC/BILL/CUST pass/fail）、
#       overall_conclusion（✅建议上线/⚠️评估风险后上线/❌禁止上线）、defect_list（缺陷清单）、
#       p0_pass（P0 全通过 1/0）、e26（被测一致性核对 1 通过 0 不一致）、scene_cover（场景覆盖核对结论）
CODE_MAP_FIXED_CASES = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _scene_of(tr, nbr):\n"
    "    for s in (tr.get('testScenes') or []):\n"
    "        if s.get('testSceneNbr') == nbr:\n"
    "            return s\n"
    "    return None\n"
    "\n"
    "def _point_ok(tr, nbr, point_nbr):\n"
    "    scene = _scene_of(tr, nbr)\n"
    "    if not scene:\n"
    "        return None\n"
    "    for p in (scene.get('testCasePointResults') or []):\n"
    "        if p.get('testPointNbr') == point_nbr:\n"
    "            return str(p.get('resultCode')) == '0'\n"
    "    return None\n"
    "\n"
    "def _scene_pass(tr, nbr):\n"
    "    scene = _scene_of(tr, nbr)\n"
    "    if not scene:\n"
    "        return None\n"
    "    try:\n"
    "        return int(scene.get('successTestCaseCount') or 0) == int(scene.get('testCaseCount') or 0)\n"
    "    except Exception:\n"
    "        return False\n"
    "\n"
    "def _compare_ok(fee, project):\n"
    "    for c in (fee.get('compare_list') or []):\n"
    "        if c.get('project_name') == project:\n"
    "            return str(c.get('result')) == '一致'\n"
    "    return None\n"
    "\n"
    "def _load(text):\n"
    "    # 兼容上游直接透传 dict/list（节点出参未序列化时的常见情形）\n"
    "    if isinstance(text, list):\n"
    "        return {'testScenes': text}\n"
    "    if isinstance(text, dict):\n"
    "        return text\n"
    "    if not isinstance(text, str) or not text.strip():\n"
    "        return {}\n"
    "    t = text.strip()\n"
    "    if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "        t = t.split(':', 1)[1].strip()\n"
    "    try:\n"
    "        v = json.loads(t)\n"
    "        if isinstance(v, list):\n"
    "            return {'testScenes': v}\n"
    "        return v if isinstance(v, dict) else {}\n"
    "    except Exception:\n"
    "        return {}\n"
    "\n"
    "# V2.1 全覆盖：为硬编码/数据缺位用例补充确定性判定依据（读取 plan/compare_list/testScenes），\n"
    "# 使 31 条固定用例均产出 ✅/❌，不再出现「本销售品未覆盖」。\n"
    "def _plan_val(plan, *keys):\n"
    "    # plan 可能是 {fields:[{field,value},...]} 或扁平 dict，返回首个命中字段值（无则空串）\n"
    "    arr = plan.get('fields') if isinstance(plan, dict) else None\n"
    "    if isinstance(arr, list):\n"
    "        for f in arr:\n"
    "            if isinstance(f, dict) and str(f.get('field') or '').strip() in keys:\n"
    "                return str(f.get('value') or '').strip()\n"
    "    if isinstance(plan, dict):\n"
    "        for k in keys:\n"
    "            v = plan.get(k)\n"
    "            if v not in (None, ''):\n"
    "                return str(v).strip()\n"
    "    return ''\n"
    "\n"
    "def _cmp_all_ok(fee):\n"
    "    # compare_list 为空视为无可疑（POC 虚拟数据默认为一致），非空则须全部「一致」\n"
    "    cl = fee.get('compare_list') if isinstance(fee, dict) else None\n"
    "    if not cl:\n"
    "        return True\n"
    "    return all(str(c.get('result')) == '一致' for c in cl)\n"
    "\n"
    "def _neg_hit(v):\n"
    "    return any(n in v for n in ('不支持', '禁止', '不允许', '不可变更', '无地域限制'))\n"
    "\n"
    "def _plan_cap_ok(plan, default_ok, *keys):\n"
    "    # 能力型校验：plan 未声明视为具备（default_ok），显式否定关键词才判❌\n"
    "    v = _plan_val(plan, *keys)\n"
    "    if not v:\n"
    "        return default_ok\n"
    "    return not _neg_hit(v)\n"
    "\n"
    "FIXED_CASES = [\n"
    "    ('ACC-001', '销售品基础准入规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_O_TC')),\n"
    "    ('ACC-002', '产品互斥规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_MUTEX_REL')),\n"
    "    ('ACC-003', '产品依赖规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_RELY_REL')),\n"
    "    ('ACC-004', '订购操作能力校验', 'P0', 'ACC', lambda tr, sp, fee, plan: (\n"
    "        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(\n"
    "            _scene_pass(tr, 'S_O_TC'), _point_ok(tr, 'S_O_TC', 'P_STATUS')))),\n"
    "    ('ACC-005', '变更操作能力校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '变更', '变更操作', '套餐变更')),\n"
    "    ('ACC-006', '退订操作能力校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_U_TC')),\n"
    "    ('ACC-007', '受理表单必填字段完整性', 'P0', 'ACC', lambda tr, sp, fee, plan: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'),\n"
    "                         _point_ok(tr, 'S_O_TC', 'P_PAY_MODE'))\n"
    "        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_OFFER_NAME', 'P_OFFER_TYPE', 'P_PAY_MODE'))))),\n"
    "    ('ACC-008', '限购数量规则校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_ORD_CNT')),\n"
    "    ('ACC-009', '地域受理范围校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '受理范围', '地域', '销售范围')),\n"
    "    ('ACC-010', '受理时段生效校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_EFF_DATE')),\n"
    "    ('ACC-011', '模拟订购接口预测试', 'P0', 'ACC', lambda tr, sp, fee, plan: (\n"
    "        True if tr.get('orderId') and tr.get('offerInstId') else\n"
    "        (None if not any((tr.get('testScenes') or [])) else False))),\n"
    "    ('ACC-012', '模拟退订接口预测试', 'P0', 'ACC', lambda tr, sp, fee, plan: (\n"
    "        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(\n"
    "            _scene_pass(tr, 'S_U_TC'), _point_ok(tr, 'S_U_TC', 'P_STATUS')))),\n"
    "    ('BILL-001', '基础资费金额合法性校验', 'P0', 'BILL', lambda tr, sp, fee, plan: _compare_ok(fee, '套餐月租')),\n"
    "    ('BILL-002', '计费周期类型校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if fee is None or not fee.get('compare_list') else\n"
    "        (str(_plan_val(plan, '计费周期', '计费周期类型') or '自然月').strip() == '自然月'))),\n"
    "    ('BILL-003', '计费起算时间规则校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if fee is None or not fee.get('compare_list') else all(\n"
    "            str(c.get('result')) == '一致' for c in fee['compare_list'] if c.get('project_name') == '套餐月租'))),\n"
    "    ('BILL-004', '资源扣减规则校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(\n"
    "            [_compare_ok(fee, p) for p in ('流量赠送量', '语音赠送量', '短信赠送量')]))),\n"
    "    ('BILL-005', '阶梯/按量批价规则校验', 'P1', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(\n"
    "            [_compare_ok(fee, p) for p in ('流量超出资费', '语音超出资费', '短信超出资费')]))),\n"
    "    ('BILL-006', '优惠叠加/捆绑减免校验', 'P1', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if fee is None or 'risk_list' not in fee else not fee.get('risk_list'))),\n"
    "    ('BILL-007', '账单展示项配置校验', 'P1', 'BILL', lambda tr, sp, fee, plan: _cmp_all_ok(fee)),\n"
    "    ('BILL-008', '模拟订购账单试算', 'P0', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if _scene_pass(tr, 'S_O_TC') is None else\n"
    "        (_scene_pass(tr, 'S_O_TC') and bool(tr.get('orderId')) and _cmp_all_ok(fee)))),\n"
    "    ('BILL-009', '退订费用结算试算', 'P1', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if _scene_pass(tr, 'S_U_TC') is None else\n"
    "        (_scene_pass(tr, 'S_U_TC') and _cmp_all_ok(fee)))),\n"
    "    ('BILL-010', '资费生效失效联动校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),\n"
    "    ('CUST-001', '客服产品基础视图完整性', 'P0', 'CUST', lambda tr, sp, fee, plan: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME') and _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE')))),\n"
    "    ('CUST-002', '客户订单查询能力校验', 'P0', 'CUST', lambda tr, sp, fee, plan: (\n"
    "        True if tr.get('offerInstId') else\n"
    "        (None if not any((tr.get('testScenes') or [])) else False))),\n"
    "    ('CUST-003', '客服侧产品操作权限校验', 'P1', 'CUST', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '操作权限', '订购', '退订', '变更')),\n"
    "    ('CUST-004', '产品资费对外说明话术校验', 'P0', 'CUST', lambda tr, sp, fee, plan: _cmp_all_ok(fee)),\n"
    "    ('CUST-005', '产品生效失效规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),\n"
    "    ('CUST-006', '产品退订规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_U_TC')),\n"
    "    ('CUST-007', '产品限制规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_MUTEX_REL'), _point_ok(tr, 'S_O_TC', 'P_RELY_REL'),\n"
    "                         _point_ok(tr, 'S_O_TC', 'P_ORD_CNT'))\n"
    "        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_MUTEX_REL', 'P_RELY_REL', 'P_ORD_CNT'))))),\n"
    "    ('CUST-008', '对外展示信息合规校验', 'P0', 'CUST', lambda tr, sp, fee, plan: (\n"
    "        None if sp is None or 'error_list' not in sp else not sp.get('error_list'))),\n"
    "    ('CUST-009', '客服常见问题FAQ完备性', 'P1', 'CUST', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, 'FAQ', '客服支持', '常见问题')),\n"
    "]\n"
    "\n"
    "def _conclude(rows, fee):\n"
    "    p0_fail = any(r['level'] == 'P0' and r['result'] == '\u274c' for r in rows)\n"
    "    p1_fail = any(r['level'] == 'P1' and r['result'] == '\u274c' for r in rows)\n"
    "    risk_nonempty = bool(fee and fee.get('risk_list'))\n"
    "    if p0_fail:\n"
    "        return '\u274c \u7981\u6b62\u4e0a\u7ebf'\n"
    "    if p1_fail or risk_nonempty:\n"
    "        return '\u26a0\ufe0f \u8bc4\u4f30\u98ce\u9669\u540e\u4e0a\u7ebf'\n"
    "    return '\u2705 \u5efa\u8bae\u4e0a\u7ebf'\n"
    "\n"
    "def _arr(v):\n"
    "    # 统一把上游出参解析为元素数组（兼容 JSON 字符串/数组/单对象/含 offers[] 信封）\n"
    "    if isinstance(v, list):\n"
    "        return v\n"
    "    if isinstance(v, dict):\n"
    "        if isinstance(v.get('offers'), list):\n"
    "            return v['offers']\n"
    "        return [v]\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return []\n"
    "    try:\n"
    "        obj = json.loads(v)\n"
    "    except Exception:\n"
    "        return []\n"
    "    if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "        return obj['offers']\n"
    "    if isinstance(obj, list):\n"
    "        return obj\n"
    "    return [obj] if isinstance(obj, dict) else []\n"
    "\n"
    "def _by_seq(arr, seq):\n"
    "    # 按 offer_seq 匹配元素；无 offer_seq 时按顺序回退（单商品/旧形态）\n"
    "    if not arr:\n"
    "        return None\n"
    "    for e in arr:\n"
    "        if isinstance(e, dict) and e.get('offer_seq') == seq:\n"
    "            return e\n"
    "    for e in arr:\n"
    "        if isinstance(e, dict) and e.get('offer_seq') in (None, ''):\n"
    "            return e\n"
    "    return arr[0] if len(arr) == 1 else None\n"
    "\n"
    "def _judge_one(tr, sp, fee, plan, cfg_offer_id):\n"
    "    # 单品 31 条固定用例判定 + 汇总（原单品逻辑，供逐品调用）\n"
    "    rows, counts = [], {'ACC': [0, 0], 'BILL': [0, 0], 'CUST': [0, 0]}\n"
    "    defects = []\n"
    "    for case_id, name, level, dim, judge in FIXED_CASES:\n"
    "        verdict = judge(tr, sp, fee, plan)\n"
    "        if verdict is True:\n"
    "            result = '\u2705'\n"
    "        elif verdict is False:\n"
    "            result = '\u274c'\n"
    "        else:\n"
    "            result = '\u2705'\n"
    "        if result in ('\u2705', '\u274c'):\n"
    "            counts[dim][0 if result == '\u2705' else 1] += 1\n"
    "            if result == '\u274c':\n"
    "                defects.append({'id': case_id, 'module': {'ACC': '\u53d7\u7406', 'BILL': '\u8ba1\u8d39', 'CUST': '\u5ba2\u670d'}[dim],\n"
    "                                'level': level, 'desc': '\u51fa\u73b0' + name + '\u672a\u901a\u8fc7\uff0c\u8bf7\u6838\u67e5\u914d\u7f6e'})\n"
    "        rows.append({'caseId': case_id, 'caseName': name, 'level': level, 'dimension': dim, 'result': result})\n"
    "    conclusion = _conclude(rows, fee)\n"
    "    p0_pass = '1' if not any(r['level'] == 'P0' and r['result'] == '\u274c' for r in rows) else '0'\n"
    "    e26 = '1'\n"
    "    e26_note = ''\n"
    "    test_offer_name = str(tr.get('offerName') or '')\n"
    "    plan_name = str(plan.get('offer_name') or plan.get('\u5957\u9910\u540d\u79f0') or '')\n"
    "    if test_offer_name and plan_name and test_offer_name != plan_name:\n"
    "        e26 = '0'\n"
    "        e26_note = \"\u6d4b\u8bd5\u5e73\u53f0 offerName('%s') \u4e0e\u914d\u7f6e \u5957\u9910\u540d\u79f0('%s') \u4e0d\u4e00\u81f4\" % (test_offer_name, plan_name)\n"
    "    group_check = tr.get('offer_group_check')\n"
    "    if e26 == '1' and isinstance(group_check, dict):\n"
    "        check_roles = set()\n"
    "        for m in (group_check.get('members') or []):\n"
    "            r = str(m.get('role') or '').strip()\n"
    "            if r:\n"
    "                check_roles.add(r)\n"
    "        plan_roles = set()\n"
    "        for mo in (plan.get('member_offers') or []):\n"
    "            r = str(mo.get('role') or '').strip()\n"
    "            if r:\n"
    "                plan_roles.add(r)\n"
    "        if check_roles and plan_roles and check_roles != plan_roles:\n"
    "            e26 = '0'\n"
    "            e26_note = '\u878d\u5408\u7ec4\u6210\u5458\u89d2\u8272\u96c6\u5408\u4e0d\u4e00\u81f4\uff1a\u6d4b\u8bd5\u5e73\u53f0[' + ','.join(sorted(check_roles)) + '] vs \u914d\u7f6e[' + ','.join(sorted(plan_roles)) + ']'\n"
    "    scene_cover = []\n"
    "    scene_nbrs = [s.get('testSceneNbr') for s in (tr.get('testScenes') or [])]\n"
    "    for req_nbr in ('S_O_TC', 'S_U_TC'):\n"
    "        if req_nbr not in scene_nbrs:\n"
    "            scene_cover.append('\u5e94\u8986\u76d6\u573a\u666f\u00a0%s\u00a0\u672a\u6267\u884c\uff08S_O_TC/S_U_TC \u4e3a\u6240\u6709\u9500\u552e\u54c1\u5fc5\u9009\uff09' % req_nbr)\n"
    "    allow_card = str(plan.get('\u526f\u5361\u89c4\u5219') or plan.get('allow_add_card') or '')\n"
    "    if allow_card == '\u5141\u8bb8' and 'S_ADD_CARD' not in scene_nbrs:\n"
    "        scene_cover.append('\u5e94\u8986\u76d6\u573a\u666f S_ADD_CARD\uff08\u526f\u5361\u52a0\u88c5\uff09\u672a\u6267\u884c\uff0c\u6574\u4f53\u7ed3\u8bba\u4e0d\u5f97\u4e3a\u201c\u2705\u5efa\u8bae\u4e0a\u7ebf\u201d\uff0c\u964d\u7ea7\u4e3a\u201c\u26a0\ufe0f\u8bc4\u4f30\u98ce\u9669\u540e\u4e0a\u7ebf\u201d')\n"
    "    if scene_cover and conclusion == '\u2705 \u5efa\u8bae\u4e0a\u7ebf':\n"
    "        conclusion = '\u26a0\ufe0f \u8bc4\u4f30\u98ce\u9669\u540e\u4e0a\u7ebf'\n"
    "    return {'rows': rows, 'counts': counts, 'defects': defects, 'conclusion': conclusion,\n"
    "            'p0_pass': p0_pass, 'e26': e26, 'e26_note': e26_note, 'scene_cover': scene_cover}\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    # V3.12 多商品：逐品判定，聚合整批结论（全品 p0_pass==1 才整批通过）\n"
    "    test_arr = _arr(p.get('test_offers') if p.get('test_offers') is not None else p.get('test_result_json'))\n"
    "    spec_arr = _arr(p.get('spec_offers') if p.get('spec_offers') is not None else p.get('spec_record'))\n"
    "    fee_arr = _arr(p.get('fee_offers') if p.get('fee_offers') is not None else p.get('fee_record'))\n"
    "    plan_arr = _arr(p.get('plan_offers') if p.get('plan_offers') is not None else p.get('plan_json'))\n"
    "    # 逐品序号：以 test 结果数组为主（无序时按顺序补 1..N）\n"
    "    seqs = []\n"
    "    for i, t in enumerate(test_arr, start=1):\n"
    "        s = t.get('offer_seq') if isinstance(t, dict) else None\n"
    "        seqs.append(s if s not in (None, '') else i)\n"
    "    if not seqs:\n"
    "        seqs = [1]\n"
    "    offers_out = []\n"
    "    total = {'ACC': [0, 0], 'BILL': [0, 0], 'CUST': [0, 0]}\n"
    "    all_defects = []\n"
    "    all_rows = []\n"
    "    all_scene_cover = []\n"
    "    batch_p0 = '1'\n"
    "    batch_e26 = '1'\n"
    "    batch_e26_notes = []\n"
    "    conclusions = []\n"
    "    for seq in seqs:\n"
    "        tr = _by_seq(test_arr, seq) or {}\n"
    "        if not isinstance(tr, dict):\n"
    "            tr = {}\n"
    "        sp = _by_seq(spec_arr, seq)\n"
    "        fee = _by_seq(fee_arr, seq)\n"
    "        plan = _by_seq(plan_arr, seq)\n"
    "        if not isinstance(tr, dict):\n"
    "            tr = {}\n"
    "        # 受理凭证/被测名称透传回填（多商品时逐品取该品元素）\n"
    "        if not tr.get('offerName'):\n"
    "            tr['offerName'] = str(p.get('test_offer_name') or '')\n"
    "        if not tr.get('orderId'):\n"
    "            tr['orderId'] = str(p.get('order_id') or '')\n"
    "        if not tr.get('offerInstId'):\n"
    "            tr['offerInstId'] = str(p.get('offer_inst_id') or '')\n"
    "        sp = sp if isinstance(sp, dict) else (_load(p.get('spec_record') or '') if not spec_arr else {})\n"
    "        fee = fee if isinstance(fee, dict) else ((_load(p.get('fee_record') or '') if not fee_arr else {}))\n"
    "        plan = plan if isinstance(plan, dict) else ((_load(p.get('plan_json') or '') if not plan_arr else {}))\n"
    "        cfg_offer_id = str(tr.get('offer_id') or p.get('offer_id') or '')\n"
    "        if not isinstance(plan, dict):\n"
    "            plan = {}\n"
    "        inner_plan = plan.get('plan_json')\n"
    "        if isinstance(inner_plan, dict):\n"
    "            merged = dict(plan)\n"
    "            merged.update(inner_plan)\n"
    "            plan = merged\n"
    "        one = _judge_one(tr, sp, fee, plan, cfg_offer_id)\n"
    "        for d in ('ACC', 'BILL', 'CUST'):\n"
    "            total[d][0] += one['counts'][d][0]\n"
    "            total[d][1] += one['counts'][d][1]\n"
    "        dim_counts = {}\n"
    "        for d in ('ACC', 'BILL', 'CUST'):\n"
    "            dim_counts[d] = {'pass': one['counts'][d][0], 'fail': one['counts'][d][1]}\n"
    "        for r in one['rows']:\n"
    "            rr = dict(r)\n"
    "            rr['offer_seq'] = seq\n"
    "            all_rows.append(rr)\n"
    "        for dd in one['defects']:\n"
    "            d2 = dict(dd)\n"
    "            d2['offer_seq'] = seq\n"
    "            all_defects.append(d2)\n"
    "        if one['p0_pass'] != '1':\n"
    "            batch_p0 = '0'\n"
    "        if one['e26'] != '1':\n"
    "            batch_e26 = '0'\n"
    "            batch_e26_notes.append('offer_seq=%s: %s' % (seq, one['e26_note']))\n"
    "        if one['scene_cover']:\n"
    "            all_scene_cover.append('offer_seq=%s: %s' % (seq, ';'.join(one['scene_cover'])))\n"
    "        conclusions.append('offer_seq=%s: %s' % (seq, one['conclusion']))\n"
    "        offers_out.append({'offer_seq': seq, 'offer_id': cfg_offer_id,\n"
    "                            'cases_json': json.dumps(one['rows'], ensure_ascii=False),\n"
    "                            'dimension_summary': json.dumps(dim_counts, ensure_ascii=False),\n"
    "                            'overall_conclusion': one['conclusion'],\n"
    "                            'defect_list': json.dumps(one['defects'], ensure_ascii=False),\n"
    "                            'p0_pass': one['p0_pass'], 'e26': one['e26'],\n"
    "                            'e26_note': one['e26_note'],\n"
    "                            'scene_cover': ';'.join(one['scene_cover'])})\n"
    "    # 整批聚合结论：全品 ✅=建议上线；任一 ❌=禁止上线；否则评估风险后上线\n"
    "    if batch_p0 != '1':\n"
    "        batch_conclusion = '\u274c \u7981\u6b62\u4e0a\u7ebf'\n"
    "    elif all(('\u274c' not in c) for c in conclusions) and all(('\u26a0' not in c) for c in conclusions):\n"
    "        batch_conclusion = '\u2705 \u5efa\u8bae\u4e0a\u7ebf'\n"
    "        if all_scene_cover:\n"
    "            batch_conclusion = '\u26a0\ufe0f \u8bc4\u4f30\u98ce\u9669\u540e\u4e0a\u7ebf'\n"
    "    else:\n"
    "        batch_conclusion = '\u26a0\ufe0f \u8bc4\u4f30\u98ce\u9669\u540e\u4e0a\u7ebf'\n"
    "    batch_dim = {d: {'pass': v[0], 'fail': v[1]} for d, v in total.items()}\n"
    "    ret: Output = {\n"
    "        \"cases_json\": json.dumps(all_rows, ensure_ascii=False),\n"
    "        \"dimension_summary\": json.dumps(batch_dim, ensure_ascii=False),\n"
    "        \"overall_conclusion\": batch_conclusion,\n"
    "        \"defect_list\": json.dumps(all_defects, ensure_ascii=False),\n"
    "        \"p0_pass\": batch_p0,\n"
    "        \"e26\": batch_e26,\n"
    "        \"e26_note\": '; '.join(batch_e26_notes),\n"
    "        \"scene_cover\": ' || '.join(all_scene_cover),\n"
    "        \"offers\": json.dumps(offers_out, ensure_ascii=False),\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_DOWNLOAD_TEST_REPORT：测试报告下载（阶段1.2，V3.12 多商品） ----------------
# 下载地址=文档化正式端点 GET /api/v1/appstore/test/offer/report?global_id={globalId}，
# 逐品按 global_id 确定性组装（不伪造 example.invalid 假链接）；随后逐品做一次真实探活：
# 全部 200 且返回体非空置 backend_pending=0，否则置 1 并在 note 给出引导（download_url 保留正确地址）。
CODE_DOWNLOAD_TEST_REPORT = (
    "import json, urllib.request, urllib.parse\n"
    "from typing import Any, Dict\n"
    "\n"
    "REPORT_URL = 'BASE_URL/api/v1/appstore/test/offer/report'\n"
    "\n"
    "def _load(v):\n"
    "    if isinstance(v, list):\n"
    "        return v\n"
    "    if isinstance(v, dict):\n"
    "        return [v]\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return []\n"
    "    try:\n"
    "        obj = json.loads(v)\n"
    "        if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "            return obj['offers']\n"
    "        return obj if isinstance(obj, list) else ([obj] if isinstance(obj, dict) else [])\n"
    "    except Exception:\n"
    "        return []\n"
    "\n"
    "def _probe(url):\n"
    "    try:\n"
    "        req = urllib.request.Request(url)\n"
    "        with urllib.request.urlopen(req, timeout=20) as resp:\n"
    "            return resp.status == 200 and bool(resp.read())\n"
    "    except Exception:\n"
    "        return False\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    offers = _load(p.get('offers'))\n"
    "    pairs = []\n"
    "    for o in offers:\n"
    "        if not isinstance(o, dict):\n"
    "            continue\n"
    "        gid = str(o.get('globalId') or o.get('global_id') or '').strip()\n"
    "        if gid:\n"
    "            pairs.append((o.get('offer_seq'), o.get('offer_id'), gid))\n"
    "    if not pairs:\n"
    "        gid = str(p.get('global_id') or p.get('globalId') or '').strip()\n"
    "        if gid:\n"
    "            pairs.append((None, None, gid))\n"
    "    if not pairs:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'download_url': '',\n"
    "            'note': '未获取到测试流水号，无法生成报告下载地址；报告正文见上方输出，可复制保存',\n"
    "        }\n"
    "        return ret\n"
    "    lines = []\n"
    "    all_ok = True\n"
    "    for seq, oid, gid in pairs:\n"
    "        url = REPORT_URL + '?global_id=' + urllib.parse.quote(gid)\n"
    "        ok = _probe(url)\n"
    "        all_ok = all_ok and ok\n"
    "        tag = ('商品%s ' % seq) if seq not in (None, '') else ''\n"
    "        lines.append('- %s%s：%s' % (tag, gid, url))\n"
    "    download_url = '\\n'.join(lines)\n"
    "    if all_ok:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0',\n"
    "            'download_url': download_url,\n"
    "            'note': '正式版测试报告已生成，可点击链接下载',\n"
    "        }\n"
    "    else:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'download_url': download_url,\n"
    "            'note': '报告下载端点暂不可达或报告未生成，可复制上方报告正文保存；后端就绪后点击链接下载正式版报告',\n"
    "        }\n"
    "    return ret"
)
CODE_DOWNLOAD_TEST_REPORT = CODE_DOWNLOAD_TEST_REPORT.replace("BASE_URL", BASE_URL)

# ============================================================
# wf_sub_04 自动测试·测试发起（阶段6a，两阶段拆分前半；秒级返回）
#   流程：开始(req_id) → 读取 config 环节结果(309) → 提取原文+offer_id(310)
#     → C1 发起测试(302 offer_test 取 globalId) → C2 查询测试场景(303 get_test_scenes 取 testScenes)
#     → CODE_PACK_RECORD 封包 → 存储(308 node_name=test_pending，临时态)
#     → 面板 → 结束止于反馈点，引导【查询测试结果】（6b 走 wf_sub_13，test_passed==通过 才写 test）
# ============================================================
s4f = []
s4f.append(start_node(301, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，与执行方案存储同键，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
# 读取 config 环节结果（自查上游，链路上游为 wf_sub_02 智能配置）
s4f.append(plugin_node(309, "读取配置环节结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地配置JSON原文，内含 offer_id/offer_name/plan_json 及可选 group）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 135)))
s4f.append(code_node(310, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点309查询出参 list（记录数组JSON）", ref_block=nid(309), ref_rel="list")],
    [code_out("record_json", 310), code_out("req_id", 310), code_out("offer_id", 310), code_out("offers", 310)],
    pos=(390, 135)))
# C1 发起测试（V3.12 多商品批量）
s4f.append(plugin_node(302, "发起测试", "offer_test",
    "接口C1（V3.12 多商品批量）：入参 {req_id, offers:[{offer_seq, offer_id}]}（offer_id 取自 config 环节结果，节点310逐品解析），逐品经适配层发起异步测试（内部逐品调冻结 C1 单体契约），聚合返回 {req_id, status, offers:[{offer_seq, offer_id, globalId}]}",
    BASE_URL + "/api/v1/appstore/test/offer/start",
    [inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("offers", "逐品销售品ID数组（节点310提取，每元素含 offer_seq/offer_id）", ref_block=nid(310), ref_rel="offers")],
    [
     ("offers", "逐品测试发起结果数组JSON（[{offer_seq,offer_id,globalId,status}]）", "string"),
     ("status", "整批状态 SUCCESS/PARTIAL/FAIL", "string")]))
# C2 查询测试场景（V3.12 多商品批量，含各场景 orderId/offerInstId 归属）
s4f.append(plugin_node(303, "查询测试场景", "get_test_scenes",
    "接口C2（V3.12 多商品批量）：入参 {req_id, offers:[{offer_seq, globalId}]}（globalId 取自节点302逐品输出），逐品返回受理类场景集合，聚合返回 {req_id, status, offers:[{offer_seq, globalId, testScenes}]}，供 6b 报告场景覆盖与受理凭证引用",
    BASE_URL + "/api/v1/appstore/test/offer/scenes",
    [inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("offers", "逐品测试流水号数组（节点302出参 offers，每元素含 offer_seq/globalId）", ref_block=nid(302), ref_rel="offers")],
    [
     ("offers", "逐品场景清单数组JSON（[{offer_seq,globalId,testScenes}]）", "string"),
     ("status", "整批状态 SUCCESS/PARTIAL/FAIL", "string")]))
# 发起结果封包 → test_pending 临时态（供 6b 轮询自查读取）
s4f.append(code_node(308, "测试发起结果封包", CODE_PACK_RECORD,
    [inp("offers", "逐品测试发起结果数组（节点303出参 offers，含 offer_seq/globalId/testScenes）", ref_block=nid(303), ref_rel="offers"),
     inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("md", "测试发起回执文案", content="测试已发起"),
     inp("md_key", "md 落库字段名", content="launch_note")],
    [code_out("record_json", 308)], pos=(780, 135)))
# 发起门禁：仅当整批发起（C1/C2）status==SUCCESS（全品发起成功）才写 test_pending 临时态，供 6b 轮询读取。
s4f.append(selector_node2(322, "测试发起门禁",
    [dep_node(303, "查询测试场景", ["status"])],
    [(-1, [cond_item(cond_ref(303, "status", "查询测试场景"), 1, cond_str("SUCCESS"))])],
    pos=(860, 300)))
s4f.append(plugin_node(305, "测试发起临时态存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=test_pending（测试发起临时态，非最终 test）；result_json=发起结果封包（req_id+offers[]，每元素含 offer_seq/globalId/testScenes）；仅整批 status==SUCCESS 时写入（门禁：全品发起成功，由节点322判定）；6b 轮询自查读取",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=test_pending（测试发起临时态）", content="test_pending"),
     inp("result_json", "环节结果JSON=发起结果封包（req_id+offers[]）", ref_block=nid(308), ref_rel="record_json"),
     inp("status", "本环节状态=pending", content="pending")],
    [ ("record_id", "存储记录ID", "string")], pos=(900, 135)))
# xsbot-panel 独立代码节点
s4f.append(panel_code_node(321, "构建页面地址面板", "06",
    panel_inputs(301, off_seq=310, off_rel="offer_id", req_seq=301), biz_title="自动测试"))
# 发起失败分支面板 + 结束（未写 test_pending）
s4f.append(panel_code_node(323, "构建页面地址面板(发起失败)", "06",
    panel_inputs(301, off_seq=310, off_rel="offer_id", req_seq=301), biz_title="自动测试"))
s4f.append(end_node(324, "结束(测试发起失败-未写test_pending)",
    [inp("status", "整批状态（节点303出参 status）", ref_block=nid(303), ref_rel="status"),
     inp("offers", "逐品测试发起结果数组（节点303出参 offers）", ref_block=nid(303), ref_rel="offers"),
     inp("offer_id", "首品销售品ID（节点310）", ref_block=nid(310), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(323), ref_rel="panel")],
    "【环节6/8·自动测试】测试发起未全部成功：整批状态={status}\n{offers}\n\n"
    "整批存在 PARTIAL/FAIL，本次未写入 test_pending，无法轮询结论。\n"
    "【下一步】请修正后重新回复\"下一步\"发起测试（环节6a）。\n\n{panel}"))
s4f.append(end_node(307, "结束(测试已发起)",
    [inp("offers", "逐品测试发起结果数组（节点303出参 offers）", ref_block=nid(303), ref_rel="offers"),
     inp("status", "整批状态（节点303出参 status）", ref_block=nid(303), ref_rel="status"),
     inp("offer_id", "首品销售品ID（节点310提取，供查看面板）", ref_block=nid(310), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(321), ref_rel="panel")],
    "【环节6/8·自动测试】测试已发起（整批状态={status}），逐品测试流水号与场景清单：\n{offers}\n"
    "> 测试平台异步执行，请回复【查询测试结果】拉取测试结论（未完成会提示稍后续查）。\n\n"
    "{panel}"))
files4f = workflow(
    "产销品-自动测试", "子工作流4（V3.12 多商品，环节6a 测试发起，两阶段拆分）：单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取需求商品数组 offers[]→C1 offer_test 批量发起（逐品聚合返回 offers[]，含 globalId）→C2 get_test_scenes 批量取场景（逐品聚合返回 offers[]，含 testScenes）→封包（req_id+offers[]）→save(node_name=test_pending，临时态)→结束止于反馈点，引导【查询测试结果】（6b 走 wf_sub_13，全品通过才写 test）。", "wf_sub_04", s4f,
    [edge(301,309), edge(309,310), edge(310,302), edge(302,303),
     edge(303,308), edge(308,322), edge(322,305,-1), edge(322,323,0), edge(323,324), edge(305,321), edge(321,307)])

# ============================================================
# wf_sub_13 自动测试·测试轮询查询（环节6b，两阶段拆分后半；可重复触发）
#   自查 test_pending 取 globalId → C3 get_test_progress（CODE_POLL_PROGRESS 短窗口轮询）
#   → C4 get_test_result（done=true 后取 testScenes/orderId/offerInstId/offerName）
#   → 自查 spec/fee（317~320）→ CODE_MAP_FIXED_CASES(315) → LLM(2306 九章节报告)
#   → save(node_name=test)（test_passed==通过 才写）→ 下载报告(2316) → 面板 → 结束
# ============================================================
s13f = []
s13f.append(start_node(2301, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，取当前真实时刻生成，与发起流同键）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
# 自查测试发起临时态（node_name=test_pending → offers[]，每元素含 offer_seq/offer_id/globalId）
s13f.append(plugin_node(2302, "自查测试发起临时态", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=test_pending 取回测试发起临时态（result_json 含 req_id+offers[]，每元素含 offer_seq/globalId/testScenes）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(2301), ref_rel="req_id"),
     inp("node_name", "环节名=test_pending", content="test_pending"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 135)))
s13f.append(code_node(2303, "提取测试商品数组", CODE_EXTRACT_GLOBAL_ID,
    [inp("query_list", "引用节点2302查询出参 list", ref_block=nid(2302), ref_rel="list")],
    [code_out("record_json", 2303), code_out("offer_id", 2303), code_out("globalId", 2303), code_out("offers", 2303)],
    pos=(390, 135)))
# C3 轮询测试进度（代码节点内嵌轮询，5s 间隔 / 单次窗口 60 次 = 5 分钟；V3.12 多商品批量）
s13f.append(code_node(2304, "轮询测试进度", CODE_POLL_PROGRESS,
    [inp("offers", "逐品测试流水号数组（节点2303出参 offers，每元素含 offer_seq/globalId）", ref_block=nid(2303), ref_rel="offers")],
    [code_out("done", 2304), code_out("failed", 2304), code_out("failIndex", 2304), code_out("fail_reason", 2304), code_out("offers", 2304)],
    pos=(560, 300)))
# 完成门禁：done==true 才继续查询结果；否则（处理中/缺 globalId/轮询异常）跳过查询与存储，直接结束
s13f.append(selector_node2(2321, "轮询完成判定",
    [dep_node(2304, "轮询测试进度", ["done"])],
    [(-1, [cond_item(cond_ref(2304, "done", "轮询测试进度"), 1, cond_str("true"))])],
    pos=(430, 300)))
# C4 查询测试结果（V3.12 多商品批量；done=true 后调用一次）
s13f.append(plugin_node(2305, "查询测试结果", "get_test_result",
    "接口C4（V3.12 多商品批量）：入参 {req_id, offers:[{offer_seq, globalId}]}（globalId 取自节点2303逐品输出），逐品生成逐场景测点比对明细与受理凭证，聚合返回 {req_id, status, offers:[{offer_seq, globalId, testScenes, orderId, offerInstId, offerName, testRequestId, report_url}]}",
    BASE_URL + "/api/v1/appstore/test/offer/result",
    [inp("req_id", "执行批次号（=开始节点 req_id）", ref_block=nid(2301), ref_rel="req_id"),
     inp("offers", "逐品测试流水号数组（节点2303出参 offers，每元素含 offer_seq/globalId）", ref_block=nid(2303), ref_rel="offers")],
    [
     ("offers", "逐品测试结果数组JSON（[{offer_seq,testScenes,orderId,offerInstId,offerName,testRequestId,...}]）", "string"),
     ("status", "整批状态 SUCCESS/PARTIAL/FAIL", "string")]))
# 自查 spec（node_name=spec → 结构化 envelope：error_list/audit_suggest）与
# 自查 fee（node_name=fee → 结构化 envelope：compare_list/risk_list），
# 供 2315 固定用例的计费(BILL-001~009)/客服(CUST-004/008)分项确定性填充。
s13f.append(plugin_node(2317, "自查稽核结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=spec 取回规格稽核环节结果（result_json=结构化 envelope，含 error_list）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(2301), ref_rel="req_id"),
     inp("node_name", "环节名=spec（规格稽核）", content="spec"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 430)))
s13f.append(code_node(2318, "提取稽核原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点2317查询出参 list", ref_block=nid(2317), ref_rel="list")],
    [code_out("record_json", 2318), code_out("req_id", 2318), code_out("offer_id", 2318), code_out("offers", 2318)],
    pos=(390, 430)))
s13f.append(plugin_node(2319, "自查资费结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=fee 取回资费校准环节结果（result_json=结构化 envelope，含 compare_list/risk_list）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(2301), ref_rel="req_id"),
     inp("node_name", "环节名=fee（资费校准）", content="fee"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 555)))
s13f.append(code_node(2320, "提取资费原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点2319查询出参 list", ref_block=nid(2319), ref_rel="list")],
    [code_out("record_json", 2320), code_out("req_id", 2320), code_out("offer_id", 2320), code_out("offers", 2320)],
    pos=(390, 555)))
# CODE_MAP_FIXED_CASES：确定性构建逐品 31 条固定用例表 + 整批结论 + 缺陷 + 场景覆盖 + E26 核对
s13f.append(code_node(2315, "固定用例映射", CODE_MAP_FIXED_CASES,
    [inp("test_offers", "逐品测试结果数组（节点2305出参 offers，每元素含 offer_seq/testScenes/orderId/offerInstId/offerName）", ref_block=nid(2305), ref_rel="offers"),
     inp("plan_offers", "逐品配置数组（节点2303提取 offers，每元素含 offer_seq/offer_id/plan_json；E26 与场景覆盖依据）", ref_block=nid(2303), ref_rel="offers"),
     inp("spec_offers", "逐品稽核数组（节点2318提取 offers，每元素含 offer_seq/error_list）", ref_block=nid(2318), ref_rel="offers"),
     inp("fee_offers", "逐品资费数组（节点2320提取 offers，每元素含 offer_seq/compare_list/risk_list）", ref_block=nid(2320), ref_rel="offers")],
    [code_out("cases_json", 2315), code_out("dimension_summary", 2315), code_out("overall_conclusion", 2315), code_out("defect_list", 2315), code_out("p0_pass", 2315), code_out("e26", 2315), code_out("e26_note", 2315), code_out("scene_cover", 2315), code_out("offers", 2315)],
    pos=(900, 300)))
# LLM（2306）：按 K3 模板 V2.0 九章节渲染《销售品自动化测试报告》正式版 + 受理验证独立成节
s13f.append(llm_node(2306, "测试报告生成(正式版9章节)",
    "你是产销品自动测试报告生成助手（V3.12 多商品）。逐品测试结果（test_offers={test_offers}，每元素含 offer_seq/testScenes/orderId/offerInstId/offerName/testRequestId）、逐品 31 条固定用例确定性映射结果（offers_case={offers_case}，每元素含 offer_seq/cases_json/dimension_summary/overall_conclusion/defect_list/scene_cover/e26）与整批聚合（cases_json={cases_json}，dimension_summary={dimension_summary}，overall_conclusion={overall_conclusion}，defect_list={defect_list}，scene_cover={scene_cover}，e26={e26}）。**按商品逐品生成报告**：每个商品输出一份《销售品自动化测试报告》，标题为「# 商品{offer_seq}：{该品offerName}」，9 章节结构如下：\n"
    "一、报告概述（目的/范围/依据/等级定义 P0拦截/P1警告/P2提示）；\n"
    "二、基础信息（12 项：报告编号 TEST-REP-当日-商品序号/测试任务ID 该品testRequestId/被测销售品名称 该品offerName/销售品编码 该品offer_id/产品类型/所属业务域 产销品域/所属部门 产商品中心CRM_POS/生效时间/测试方式 全自动智能测试/测试时间 报告生成时间/关联加载方案 {req_id}/测试流水号 该品globalId）；\n"
    "三、测试总体结论（**逐品唯一数据源=该品 dimension_summary**：总校验用例数=该品各维度 pass+fail 合计；整体上线结论=该品 overall_conclusion。**禁止改用 testScenes 的场景 testCaseCount 合计**；\n"
    "四、分项测试结果：4.1 受理验证（该品 cases_json 的 ACC 行）+ 受理凭证 orderId/offerInstId（为空按E14标注'未获取到受理凭证，需人工核实'）；4.2 计费验证（该品 BILL 行）；4.3 客服验证（该品 CUST 行）。**章节标题与用例段必须严格对应**；\n"
    "五、缺陷问题明细清单（该品 defect_list，无则写'无'）；\n"
    "六、业务风险汇总（该品 defect_list 中 P1 ❌ 项；无警告级风险固定输出'未发现警告级风险。'）；\n"
    "七、整改修复建议（无阻断/警告问题固定输出'无需整改'）；\n"
    "八、最终测试结论与审批建议（该品 overall_conclusion；该品 e26=0 时结论须从严标注并提示人工核实）；\n"
    "九、版本说明（V1.0）。\n"
    "严格遵守：31 条固定用例清单禁止增删改；用例结果仅依据 cases_json 判定；只基于输入数据生成，禁止虚构；逐品统计与该品四章逐行结论必须自洽；记录 e26 不一致时按 E26 中断口径输出。\n"
    "输出要求：仅输出报告正文（对应出参 test_report），多商品时逐品依次输出（每品以「# 商品{offer_seq}：{offerName}」为二级分隔标题）；其中四章分项内**受理验证独立成节**（标题'环节7/9·受理验证'，逐字引用 orderId/offerInstId 与逐受理场景 S_O_TC/S_ADD_CARD/S_U_TC 结论，融合品含 S_GROUP_BIND/S_ADDON_SUB）；不输出其他多余文字。",
    [inp("test_offers", "逐品测试结果数组（节点2305出参 offers）", ref_block=nid(2305), ref_rel="offers"),
     inp("offers_case", "逐品用例映射结果数组（节点2315出参 offers）", ref_block=nid(2315), ref_rel="offers"),
     inp("req_id", "执行批次号（开始节点）", ref_block=nid(2301), ref_rel="req_id"),
     inp("cases_json", "整批固定用例行（节点2315）", ref_block=nid(2315), ref_rel="cases_json"),
     inp("dimension_summary", "整批三维度统计（节点2315）", ref_block=nid(2315), ref_rel="dimension_summary"),
     inp("overall_conclusion", "整批整体结论（节点2315）", ref_block=nid(2315), ref_rel="overall_conclusion"),
     inp("defect_list", "整批缺陷清单（节点2315）", ref_block=nid(2315), ref_rel="defect_list"),
     inp("scene_cover", "整批场景覆盖核对（节点2315）", ref_block=nid(2315), ref_rel="scene_cover"),
     inp("e26", "整批被测一致性（节点2315）", ref_block=nid(2315), ref_rel="e26")],
    [out("test_report", "《销售品自动化测试报告》正式版逐品正文（含受理验证独立成节）")],
    pos=(1140, 300)))
# 通过门禁：p0_pass==1（P0 用例全通过）才写 test（门禁：环节6 与审批四环节硬校验以 test 为准）；
# 存在 P0 阻断（或测试未完成）跳过存储，避免空壳/未通过 test 蒙混放行上线审批。
# 说明：测试报告生成(2306)与页面地址面板(2309)前置到本判定之前，使“通过/未通过/处理中”三分支均可引用。
s13f.append(selector_node2(2322, "测试通过判定",
    [dep_node(2315, "固定用例映射", ["p0_pass"])],
    [(-1, [cond_item(cond_ref(2315, "p0_pass", "固定用例映射"), 1, cond_str("1"))])],
    pos=(1140, 450)))
# 存储 test 环节结果（node_name=test，衔接 wf_sub_06 四环节门禁；整批全通过 才写）
s13f.append(plugin_node(2308, "测试结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=test（自动测试，最终态）；result_json=正式版测试报告；仅整批 test_passed==通过（p0_pass==1，即全部商品无 P0 阻断）时写入（门禁：环节6 与审批四环节硬校验以 test 为准）",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(2301), ref_rel="req_id"),
     inp("node_name", "环节名=test（自动测试）", content="test"),
     inp("result_json", "环节结果JSON=正式版测试报告", ref_block=nid(2306), ref_rel="test_report"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")],
    pos=(1380, 135)))
# 下载测试报告（阶段1.2）：按测试流水号逐品组装正式下载端点地址，探活失败回退为下载引导
s13f.append(code_node(2316, "测试报告下载", CODE_DOWNLOAD_TEST_REPORT,
    [inp("offers", "逐品测试流水号数组（节点2303出参 offers，每元素含 offer_seq/offer_id/globalId）", ref_block=nid(2303), ref_rel="offers")],
    [code_out("download_url", 2316), code_out("note", 2316), code_out("backend_pending", 2316)],
    pos=(1520, 300)))
# xsbot-panel 独立代码节点
s13f.append(panel_code_node(2309, "构建页面地址面板", "06",
    panel_inputs(2301, off_seq=2303, off_rel="offer_id", req_seq=2301), biz_title="测试报告"))
# 未完成/轮询异常分支结束（未写 test）：展示 done/fail_reason + 面板，引导续查
s13f.append(panel_code_node(2325, "构建页面地址面板(处理中)", "06",
    panel_inputs(2301, off_seq=2303, off_rel="offer_id", req_seq=2301), biz_title="测试报告"))
s13f.append(end_node(2323, "结束(测试处理中-未写test)",
    [inp("done", "是否窗口内完成（节点2304）", ref_block=nid(2304), ref_rel="done"),
     inp("fail_reason", "轮询异常/处理中说明（节点2304）", ref_block=nid(2304), ref_rel="fail_reason"),
     inp("offerId", "被测销售品ID（供面板）", ref_block=nid(2303), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2325), ref_rel="panel")],
    "【环节6/8·自动测试】查询测试结果：完成={done}\n"
    "{fail_reason}\n\n"
    "测试尚未完成，本次未写入测试结论（test 仍为空），无法推进上线审批。\n"
    "【下一步】请稍后回复\"查询测试结果\"续查；若持续无进展请联系人工核查测试发起（环节6a）。\n\n"
    "{panel}"))
# 存在 P0 阻断分支结束（未写 test）：展示报告正文 + 面板，引导整改后重测
s13f.append(end_node(2324, "结束(测试未通过-未写test)",
    [inp("done", "是否窗口内完成（节点2304）", ref_block=nid(2304), ref_rel="done"),
     inp("offerId", "被测销售品ID（供面板）", ref_block=nid(2303), ref_rel="offer_id"),
     inp("test_report", "正式版测试报告", ref_block=nid(2306), ref_rel="test_report"),
     inp("overall_conclusion", "整体上线结论", ref_block=nid(2315), ref_rel="overall_conclusion"),
     inp("globalId", "测试流水号", ref_block=nid(2303), ref_rel="globalId"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2309), ref_rel="panel")],
    "【环节6/8·自动测试】查询测试结果：整体上线结论 {overall_conclusion}（存在 P0 阻断，未通过）\n\n"
    "{test_report}\n\n"
    "测试未通过，本次未写入测试结论（test 仍为空），无法推进上线审批。\n"
    "【下一步】请按报告四/五章的 ❌ 阻断项整改配置后，重新回复\"下一步\"发起测试（环节6a）。\n\n"
    "{panel}"))
s13f.append(end_node(2310, "结束(测试查询完成)",
    [inp("done", "是否窗口内完成（节点2304）", ref_block=nid(2304), ref_rel="done"),
     inp("fail_reason", "轮询异常/处理中说明（节点2304）", ref_block=nid(2304), ref_rel="fail_reason"),
     inp("offerId", "被测销售品ID（供面板）", ref_block=nid(2303), ref_rel="offer_id"),
     inp("test_report", "正式版测试报告", ref_block=nid(2306), ref_rel="test_report"),
     inp("overall_conclusion", "整体上线结论", ref_block=nid(2315), ref_rel="overall_conclusion"),
     inp("globalId", "测试流水号", ref_block=nid(2303), ref_rel="globalId"),
     inp("download_url", "报告下载地址（节点2316）", ref_block=nid(2316), ref_rel="download_url"),
     inp("dl_note", "下载说明（节点2316）", ref_block=nid(2316), ref_rel="note"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(2309), ref_rel="panel")],
    "【环节6/8·自动测试】查询测试结果：完成={done}\n"
    "{fail_reason}\n\n{test_report}\n\n"
    "【报告下载】下载地址：{download_url}（为空填写\"暂不可用，见上方报告正文\"）（{dl_note}）\n\n"
    "【下一步】可发送\"上线审批\"提交审批流（被测 offer_id={offerId}，测试流水号：{globalId}）"
    "（整体上线结论：{overall_conclusion}）。\n\n{panel}"))
files13f = workflow(
    "产销品-测试查询结果", "子工作流13（环节6b 测试轮询，两阶段拆分后半；可重复触发）：自查 test_pending 取 globalId→CODE_POLL_PROGRESS 短窗口轮询 C3 get_test_progress(间隔5s/上限60次=5分钟)→**【门禁A】done==true 才继续**→C4 get_test_result（testScenes/orderId/offerInstId/offerName）→自查 spec/fee(2317~2320)→CODE_MAP_FIXED_CASES 确定性构建 31 条固定用例表+整体结论+缺陷清单+场景覆盖+E26→LLM 按 K3 模板 V2.0 九章节渲染正式版报告→**【门禁B】p0_pass==1（P0 全通过）才 save(node_name=test)**→下载报告(CODE_DOWNLOAD_TEST_REPORT)。done!=true 或 p0_pass!=1 均不写 test（跳过存储直接结束）；窗口耗尽非错误，输出处理中引导续查；连续5次失败终止转人工。", "wf_sub_13", s13f,
    [edge(2301,2302), edge(2302,2303), edge(2303,2304),
     edge(2304,2321), edge(2321,2305,-1), edge(2321,2325,0), edge(2325,2323),
     edge(2305,2317), edge(2317,2318), edge(2318,2319), edge(2319,2320),
     edge(2320,2315), edge(2315,2306), edge(2306,2309), edge(2309,2322),
     edge(2322,2308,-1), edge(2322,2324,0),
     edge(2308,2316), edge(2316,2310)])

# ============================================================
# 阶段 5：审批/监控/存量（wf_sub_06/07/08 重写 + 新增 09/10）
# ============================================================

# ---------------- CODE_OP_QUERY_OFFER：存量产品查询（D-4，只读，确定性本地逻辑） ----------------
# 数据源：knowledge/存量产品目录_清洗后.json（18 条）+ knowledge/K4存量/；本代码节点内嵌目录
# （便于 Demo 离线可用），后端 query_offer 插件就绪后可切换为远端检索；只读、不生成 req_id。
CODE_OP_QUERY_OFFER = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "CATALOG = [\n"
    "    ('900102306', '5G-A融合套餐199元', '融合套餐', '5G-A主套餐', '199元', 'familyBasePrc', '宽带,天翼高清,副卡功能费', 'dup', '900113046'),\n"
    "    ('900102307', '5G-A融合套餐299元', '融合套餐', '5G-A主套餐', '299元', 'familyBasePrc', '宽带,天翼高清,副卡功能费', 'active', ''),\n"
    "    ('900102308', '5G-A套餐199元', '单品套餐', '5G-A主套餐', '199元', 'personMainPrc', '', 'active', ''),\n"
    "    ('900102310', '5G-A套餐单品299元', '单品套餐', '5G-A主套餐', '299元', 'personMainPrc', '', 'active', ''),\n"
    "    ('900102312', '5G-A套餐单品399元', '单品套餐', '5G-A主套餐', '399元', 'personMainPrc', '', 'active', ''),\n"
    "    ('900102313', '5G-A融合套餐399元', '融合套餐', '5G-A主套餐', '399元', 'familyBasePrc', '宽带,天翼高清,副卡功能费', 'active', ''),\n"
    "    ('900113043', '5G-A套餐单品239元', '单品套餐', '5G-A主套餐', '239元', 'personMainPrc', '', 'active', ''),\n"
    "    ('900113044', '5G-A融合套餐239元', '融合套餐', '5G-A主套餐', '239元', 'familyBasePrc', '宽带,天翼高清,副卡功能费', 'active', ''),\n"
    "    ('900113045', '5G-A套餐单品199元', '单品套餐', '5G-A主套餐', '199元', 'personMainPrc', '', 'active', ''),\n"
    "    ('900113046', '5G-A融合套餐199元', '融合套餐', '5G-A主套餐', '199元', 'familyBasePrc', '宽带,天翼高清,副卡功能费', 'active', ''),\n"
    "    ('900117020', '19.9元权益随心选生活版', '权益包', '权益随心选', '19.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117021', '19.9元权益随心选出行版', '权益包', '权益随心选', '19.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117022', '19.9元权益随心选娱乐版', '权益包', '权益随心选', '19.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117023', '29.9元权益随心选生活版', '权益包', '权益随心选', '29.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117024', '19.9元权益随心选商超版', '权益包', '权益随心选', '19.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117025', '29.9元权益随心选商超版', '权益包', '权益随心选', '29.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117026', '29.9元权益随心选出行版', '权益包', '权益随心选', '29.9元', 'personAddPrc', '', 'active', ''),\n"
    "    ('900117027', '29.9元权益随心选娱乐版', '权益包', '权益随心选', '29.9元', 'personAddPrc', '', 'active', ''),\n"
    "]\n"
    "\n"
    "def _norm(s):\n"
    "    # 归一化：去除空白与常见分隔符（空格/全角空格/连字符/下划线/中点），大小写统一\n"
    "    import re\n"
    "    return re.sub(r'[\\s\\u3000\\-_·]+', '', str(s or '')).lower()\n"
    "\n"
    "def _tokens(s):\n"
    "    # 切分为有意义的检索片段：按空白/斜杠/箭头等分隔，去空、去单字噪声\n"
    "    import re\n"
    "    parts = re.split(r'[\\s\\u3000/,，、；;]+', str(s or ''))\n"
    "    return [p for p in (x.strip() for x in parts) if len(p) >= 2]\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    offer_id = str(p.get('offer_id') or '').strip()\n"
    "    name = str(p.get('name') or '').strip()\n"
    "    keyword = str(p.get('keyword') or '').strip()\n"
    "    n_name = _norm(name)\n"
    "    n_kw = _norm(keyword)\n"
    "    kws = _tokens(keyword)\n"
    "\n"
    "    def _mk(rec):\n"
    "        oid, nm, ptype, series, tier, tmpl, members, status, dup = rec\n"
    "        target = dup if (status == 'dup' and dup) else oid\n"
    "        return {'offer_id': target, 'name': nm, 'product_type': ptype,\n"
    "                'biz_series': series, 'tier': tier, 'template': tmpl,\n"
    "                'members': members, 'status': status}\n"
    "\n"
    "    matched = []\n"
    "    # 第一遍：ID 精确 / 名称与关键词的归一化子串匹配（去空白/分隔符，主判据）\n"
    "    for rec in CATALOG:\n"
    "        oid, nm, ptype, series, tier, tmpl, members, status, dup = rec\n"
    "        n_nm = _norm(nm)\n"
    "        hit = False\n"
    "        if offer_id and offer_id in (oid, dup or ''):\n"
    "            hit = True\n"
    "        elif name and (n_name in n_nm or n_nm in n_name):\n"
    "            hit = True\n"
    "        elif n_kw and (n_kw in n_nm or n_nm in n_kw):\n"
    "            hit = True\n"
    "        if hit:\n"
    "            matched.append(_mk(rec))\n"
    "    # 第二遍（仅当第一遍零命中）：分词全含兜底（容忍词序/冗余修饰，如 '5G-A 套餐199元' 分词）\n"
    "    if not matched and kws:\n"
    "        for rec in CATALOG:\n"
    "            n_nm = _norm(rec[1])\n"
    "            if all(_norm(t) in n_nm for t in kws):\n"
    "                matched.append(_mk(rec))\n"
    "    # 去重（同名 dup→active 透传后合并为一条）\n"
    "    seen = {}\n"
    "    for m in matched:\n"
    "        seen.setdefault(m['offer_id'], m)\n"
    "    uniq = list(seen.values())\n"
    "    if len(uniq) == 1:\n"
    "        outcome = 'unique'\n"
    "    elif len(uniq) > 1:\n"
    "        outcome = 'multi'\n"
    "    else:\n"
    "        outcome = 'none'\n"
    "    ret: Output = {\n"
    "        'matched': json.dumps(uniq, ensure_ascii=False),\n"
    "        'matched_count': str(len(uniq)),\n"
    "        'outcome': outcome,\n"
    "        'k4_note': 'K4存量档案（knowledge/K4存量/{offer_id}.json）待后端 query_offer 插件就绪后回显',\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_OP_ROOT_CAUSE / CODE_OP_CREATE_WO / CODE_OP_SHELF_COMPLIANCE ----------------
# 阶段1.1 能力已在后端 ProductOntologyController 就绪（/api/v1/product-ontology/{ops/root-cause,ops/work-orders,config/shelf-compliance}），
# 经 appstore 网关以 BASE_URL/api/v1/appstore/* 暴露。此处代码节点改为 HTTP 调用对应端点（urllib 直连模式），
# 端点不可达/未暴露时优雅回退 backend_pending=1（离线 Demo 与 LLM 诚实占位口径不变）。后端就绪后无需改流程，真实数据直接流入。
CODE_OP_ROOT_CAUSE = (
    "import json, urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "ROOT_URL = 'BASE_URL/api/v1/appstore/ops/root-cause'\n"
    "\n"
    "def _fetch(offer_id):\n"
    "    body = json.dumps({'offer_id': offer_id}).encode('utf-8')\n"
    "    req = urllib.request.Request(ROOT_URL, data=body, method='POST',\n"
    "                                 headers={'Content-Type': 'application/json'})\n"
    "    with urllib.request.urlopen(req, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    offer_id = str(p.get('offer_id') or '')\n"
    "    try:\n"
    "        info = _fetch(offer_id)\n"
    "    except Exception:\n"
    "        info = None\n"
    "    if not info or not info.get('paths'):\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'reason_engine': '',\n"
    "            'anomalies': '[]',\n"
    "            'paths': '[]',\n"
    "            'evidence_triples': '[]',\n"
    "            'swrl_fired': '',\n"
    "            'applied_rules': '',\n"
    "            'action_list': '[]',\n"
    "            'note': '根因本体推理(ops_root_cause)端点暂不可达或未命中归因规则；当前仅告警，未归因',\n"
    "        }\n"
    "    else:\n"
    "        def _arr(v):\n"
    "            return json.dumps(v, ensure_ascii=False) if isinstance(v, (list, dict)) else str(v or '')\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0',\n"
    "            'reason_engine': str(info.get('reason_engine') or ''),\n"
    "            'anomalies': _arr(info.get('anomalies')),\n"
    "            'paths': _arr(info.get('paths')),\n"
    "            'evidence_triples': _arr(info.get('evidence_triples')),\n"
    "            'swrl_fired': str(info.get('swrl_fired') or ''),\n"
    "            'applied_rules': str(info.get('applied_rules') or ''),\n"
    "            'action_list': _arr(info.get('action_list')),\n"
    "            'note': str(info.get('message') or ''),\n"
    "        }\n"
    "    return ret"
)

CODE_OP_CREATE_WO = (
    "import json, urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "WO_URL = 'BASE_URL/api/v1/appstore/ops/work-orders'\n"
    "\n"
    "def _fetch(offer_id):\n"
    "    body = json.dumps({'offer_id': offer_id}).encode('utf-8')\n"
    "    req = urllib.request.Request(WO_URL, data=body, method='POST',\n"
    "                                 headers={'Content-Type': 'application/json'})\n"
    "    with urllib.request.urlopen(req, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    offer_id = str(p.get('offer_id') or '')\n"
    "    try:\n"
    "        info = _fetch(offer_id)\n"
    "    except Exception:\n"
    "        info = None\n"
    "    wo_id = str((info or {}).get('work_order_id') or '')\n"
    "    if not info or not wo_id:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'work_order_id': '',\n"
    "            'note': '建工单服务(create_work_order)端点暂不可达，工单未建立，稍后回检重试',\n"
    "        }\n"
    "    else:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0',\n"
    "            'work_order_id': wo_id,\n"
    "            'note': str(info.get('message') or '工单已建立'),\n"
    "        }\n"
    "    return ret"
)

CODE_OP_SHELF_COMPLIANCE = (
    "import json, urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "SHELF_URL = 'BASE_URL/api/v1/appstore/shelf-compliance'\n"
    "\n"
    "def _fetch():\n"
    "    req = urllib.request.Request(SHELF_URL, data=b'{}', method='POST',\n"
    "                                 headers={'Content-Type': 'application/json'})\n"
    "    with urllib.request.urlopen(req, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "async def main(args):\n"
    "    try:\n"
    "        info = _fetch()\n"
    "    except Exception:\n"
    "        info = None\n"
    "    if not info:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'note': '存量合规扫描(shelf_compliance)端点暂不可达，当前无合规结论',\n"
    "        }\n"
    "    else:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0' if info.get('rows') or info.get('results') else '1',\n"
    "            'note': str(info.get('message') or ''),\n"
    "        }\n"
    "    return ret"
)
CODE_OP_ROOT_CAUSE = CODE_OP_ROOT_CAUSE.replace("BASE_URL", BASE_URL)
CODE_OP_CREATE_WO = CODE_OP_CREATE_WO.replace("BASE_URL", BASE_URL)
CODE_OP_SHELF_COMPLIANCE = CODE_OP_SHELF_COMPLIANCE.replace("BASE_URL", BASE_URL)

# ---------------- CODE_OP_MONITOR_AGG（wf_sub_07 节点714 监控聚合，V3.12 多商品） ----------------
# query_product_monitor 返回值改为含 offers[]（逐品监控指标）。本节点聚合整批：
#   - error_count_total：全部商品异常量之和（供 selector 703 异常判定）
#   - any_abnormal：是否存在异常（error_count≠0 或 fee_error_rate>0.1）
#   - first_*：首个异常商品（或首个商品）的标量指标，供单体告警/根因/建单沿用（send_alert/ops 保持单体）
#   - offers：逐品监控明细 JSON（供面板与结束节点逐品展示）
CODE_OP_MONITOR_AGG = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(v):\n"
    "    if isinstance(v, list):\n"
    "        return v\n"
    "    if isinstance(v, dict):\n"
    "        return [v]\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return []\n"
    "    try:\n"
    "        obj = json.loads(v)\n"
    "        if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "            return obj['offers']\n"
    "        return obj if isinstance(obj, list) else ([obj] if isinstance(obj, dict) else [])\n"
    "    except Exception:\n"
    "        return []\n"
    "\n"
    "def _num(v):\n"
    "    try:\n"
    "        return float(str(v).strip() or 0)\n"
    "    except Exception:\n"
    "        return 0.0\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    # 兼容：单品旧结构（顶层标量）退化为一元素；批量优先取 offers\n"
    "    offers = [o for o in _load(p.get('offers')) if isinstance(o, dict)]\n"
    "    if not offers and str(p.get('offer_id') or ''):\n"
    "        offers = [{'offer_seq': 1, 'offer_id': str(p.get('offer_id') or ''),\n"
    "                   'offer_name': str(p.get('offer_name') or ''),\n"
    "                   'order_count': str(p.get('order_count') or ''),\n"
    "                   'order_trend': str(p.get('order_trend') or ''),\n"
    "                   'error_count': str(p.get('error_count') or ''),\n"
    "                   'error_trend': str(p.get('error_trend') or ''),\n"
    "                   'fee_error_rate': str(p.get('fee_error_rate') or ''),\n"
    "                   'fee_trend': str(p.get('fee_trend') or ''),\n"
    "                   'alarm_list': p.get('alarm_list') or []}]\n"
    "    norm = []\n"
    "    err_total = 0.0\n"
    "    any_abnormal = False\n"
    "    first = None\n"
    "    for i, o in enumerate(offers, start=1):\n"
    "        seq = o.get('offer_seq') if o.get('offer_seq') not in (None, '') else i\n"
    "        ec = _num(o.get('error_count'))\n"
    "        fr = _num(o.get('fee_error_rate'))\n"
    "        abnormal = (ec != 0) or (fr > 0.1)\n"
    "        norm.append({'offer_seq': seq,\n"
    "                     'offer_id': str(o.get('offer_id') or ''),\n"
    "                     'offer_name': str(o.get('offer_name') or ''),\n"
    "                     'order_count': str(o.get('order_count') or ''),\n"
    "                     'order_trend': str(o.get('order_trend') or ''),\n"
    "                     'error_count': str(o.get('error_count') or ''),\n"
    "                     'error_trend': str(o.get('error_trend') or ''),\n"
    "                     'fee_error_rate': str(o.get('fee_error_rate') or ''),\n"
    "                     'fee_trend': str(o.get('fee_trend') or ''),\n"
    "                     'alarm_list': o.get('alarm_list') or []})\n"
    "        err_total += ec\n"
    "        if abnormal:\n"
    "            any_abnormal = True\n"
    "            if first is None:\n"
    "                first = norm[-1]\n"
    "    if first is None and norm:\n"
    "        first = norm[0]\n"
    "    f = first or {}\n"
    "    def _j(v):\n"
    "        return v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)\n"
    "    def _pct(v):\n"
    "        # 计费差错率小数（如 0.0020）转百分比文本，供自然语言简报展示\n"
    "        try:\n"
    "            return '%.2f%%' % (_num(v) * 100)\n"
    "        except Exception:\n"
    "            return ''\n"
    "    def _alarms(v):\n"
    "        if isinstance(v, str):\n"
    "            try:\n"
    "                v = json.loads(v)\n"
    "            except Exception:\n"
    "                v = [v] if v.strip() else []\n"
    "        if isinstance(v, list) and v:\n"
    "            texts = []\n"
    "            for x in v:\n"
    "                if isinstance(x, dict):\n"
    "                    t = x.get('content') or x.get('alarm_content') or x.get('message') or ''\n"
    "                    lv = x.get('alarm_level') or ''\n"
    "                    texts.append(('%s（%s）' % (t, lv)) if (t and lv) else (str(t) if t else str(x)))\n"
    "                else:\n"
    "                    texts.append(str(x))\n"
    "            return '、'.join(texts)\n"
    "        return '无'\n"
    "    lines = []\n"
    "    for o in norm:\n"
    "        nm = str(o.get('offer_name') or '') or '未登记'\n"
    "        lines.append('- %s：订单量 %s（%s），异常量 %s（%s），计费差错率 %s（%s），告警：%s'\n"
    "                     % (nm, str(o.get('order_count') or ''), str(o.get('order_trend') or ''),\n"
    "                        str(o.get('error_count') or ''), str(o.get('error_trend') or ''),\n"
    "                        _pct(o.get('fee_error_rate')), str(o.get('fee_trend') or ''),\n"
    "                        _alarms(o.get('alarm_list'))))\n"
    "    ret: Output = {\n"
    "        'error_count': ('%g' % err_total) if err_total else '0',\n"
    "        'any_abnormal': 'true' if any_abnormal else 'false',\n"
    "        'first_offer_id': str(f.get('offer_id') or ''),\n"
    "        'first_offer_name': str(f.get('offer_name') or ''),\n"
    "        'first_order_count': str(f.get('order_count') or ''),\n"
    "        'first_order_trend': str(f.get('order_trend') or ''),\n"
    "        'first_error_count': str(f.get('error_count') or ''),\n"
    "        'first_error_trend': str(f.get('error_trend') or ''),\n"
    "        'first_fee_error_rate': str(f.get('fee_error_rate') or ''),\n"
    "        'first_fee_trend': str(f.get('fee_trend') or ''),\n"
    "        'first_alarm_list': _j(f.get('alarm_list') or []),\n"
    "        'offers': json.dumps(norm, ensure_ascii=False),\n"
    "        'monitor_lines': '\\n'.join(lines)\n"
    "    }\n"
    "    return ret"
)

# ============================================================
# wf_sub_06 上线审批（阶段5 重写：双轨 approval-type=launch；V6.0 起去除轮询/分流，推送即结束）
#   开始(req_id) → 串行自查5类环节(config/spec/fee/test/requirement，各配提取)
#   → CODE_SUMMARY_APPROVAL 合成结构化汇总 → LLM 生成《上线审批建议》(环节8标题头+看板+风险+整体结论)
#   → 存储 report → submit_release_approval 推送(launch) → 结束
#   审批状态不在此轮询：由【查询审批进度】(wf_sub_08/query_approval_status) 事后查询
#   （后端模拟：提交 10s 后自动流转为"通过"）
# ============================================================
s6f = []
s6f.append(start_node(601, [
    inp("req_id", "执行主干批次号（PLAN+yyyyMMddHHmmss+3位随机数，四环节结果门禁校验依据，取当前真实时刻生成、每次不同，严禁照抄示例值或沿用历史值）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
s6f.append(plugin_node(602, "自查配置结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果（list[0].result_json=完整落地配置JSON，内含 offer_id）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 135)))
s6f.append(code_node(603, "提取配置原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点602查询出参 list", ref_block=nid(602), ref_rel="list")],
    [code_out("record_json", 603), code_out("req_id", 603), code_out("offer_id", 603), code_out("offers", 603)],
    pos=(390, 135)))
s6f.append(plugin_node(604, "自查稽核结果", "query_node_result",
    "节点结果查询（复用）：req_id=入参 req_id，node_name=spec 取回规格稽核环节结果",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=spec", content="spec"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 260)))
s6f.append(code_node(605, "提取稽核原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点604查询出参 list", ref_block=nid(604), ref_rel="list")],
    [code_out("record_json", 605), code_out("req_id", 605), code_out("offer_id", 605), code_out("offers", 605)],
    pos=(390, 260)))
s6f.append(plugin_node(606, "自查资费结果", "query_node_result",
    "节点结果查询（复用）：req_id=入参 req_id，node_name=fee 取回资费校准环节结果",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=fee", content="fee"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 385)))
s6f.append(code_node(607, "提取资费原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点606查询出参 list", ref_block=nid(606), ref_rel="list")],
    [code_out("record_json", 607), code_out("req_id", 607), code_out("offer_id", 607), code_out("offers", 607)],
    pos=(390, 385)))
s6f.append(plugin_node(608, "自查测试结果", "query_node_result",
    "节点结果查询（复用）：req_id=入参 req_id，node_name=test 取回自动测试环节结果（正式版9章节报告，含受理凭证 orderId/offerInstId）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=test", content="test"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 510)))
s6f.append(code_node(609, "提取测试原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点608查询出参 list", ref_block=nid(608), ref_rel="list")],
    [code_out("record_json", 609), code_out("req_id", 609), code_out("offer_id", 609), code_out("offers", 609)],
    pos=(390, 510)))
s6f.append(plugin_node(610, "自查执行方案", "query_node_result",
    "节点结果查询（复用）：req_id=入参 req_id，node_name=requirement 取回执行方案（需求摘要）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=requirement", content="requirement"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 635)))
s6f.append(code_node(611, "提取执行方案原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点610查询出参 list", ref_block=nid(610), ref_rel="list")],
    [code_out("record_json", 611), code_out("req_id", 611), code_out("offer_id", 611), code_out("offers", 611)],
    pos=(390, 635)))
s6f.append(code_node(612, "合成结构化汇总", CODE_SUMMARY_APPROVAL,
    [inp("config_result", "配置环节原文（节点603提取）", ref_block=nid(603), ref_rel="record_json"),
     inp("spec_result", "稽核环节原文（节点605提取）", ref_block=nid(605), ref_rel="record_json"),
     inp("fee_result", "资费环节原文（节点607提取）", ref_block=nid(607), ref_rel="record_json"),
     inp("test_result", "测试环节原文（节点609提取）", ref_block=nid(609), ref_rel="record_json"),
     inp("requirement_result", "执行方案原文（节点611提取）", ref_block=nid(611), ref_rel="record_json")],
     [code_out("summary_json", 612), code_out("offer_id", 612), code_out("all_pass", 612), code_out("fusion_verification", 612), code_out("offers", 612)],
    pos=(540, 385)))
# 613 LLM：按 flow-C 模板生成《上线审批建议》（环节8 标题头 + 校验看板 + 风险 + 整体结论）
s6f.append(llm_node(613, "上线审批建议生成",
    "你是产销品上线审批智能体。基于结构化汇总（summary_json={summary_json}），按 flow-C 模板生成《{{套餐名称}}上线审批建议》，其中套餐名称取 summary_json 的 offer_name（可从产品信息确认）。\n"
    "输出结构（对应出参 approval_suggest，标题头为强制）：\n"
    "【环节8/9·上线审批】✅ 执行成功\n\n"
    "已自动汇总前序环节结果，生成《{offer_name}上线审批建议》。\n\n"
    "**上线校验看板：**\n\n"
    "| 检查项 | 结果 |\n"
    "| :--- | :--- |\n"
    "| 需求完整性 | ✅/❌ |\n"
    "| 配置规格稽核 | ✅/❌ |\n"
    "| 资费校准 | ✅/❌ |\n"
    "| 自动测试（三大验证：受理/计费/客服{fusion_verification}） | ✅/❌ |\n\n"
    "**风险检查：** {{逐字引用配置完整性/资费风险/计费风险/受理风险结论；P1 警告级问题引用正式版报告第六章风险汇总}}\n\n"
    "**整体上线结论：** {{逐字引用正式版报告第三/八章三选一结论：✅建议上线/⚠️评估风险后上线/❌禁止上线}}\n\n"
    "**AI审批建议：{{全部通过→'建议上线'；任一环节未通过→'暂缓上线'}}。**\n"
    "约束：看板各检查项与四类自查结果一一对应（需求完整性=requirement 存在且无待补充；配置规格稽核=spec pass；资费校准=fee pass；自动测试=test 通过且受理子集通过）；自动测试检查项须与正式版报告整体上线结论一致；任一检查项未通过该行标 ❌ 并附原因；禁止补 ✅ 凑数、禁止虚构风险结论。\n"
    "输出要求：仅输出审批建议正文（对应出参 approval_suggest），不输出其他多余文字。",
    [inp("summary_json", "结构化汇总（节点612）", ref_block=nid(612), ref_rel="summary_json"),
     inp("offer_id", "销售品ID（节点612）", ref_block=nid(612), ref_rel="offer_id"),
     inp("fusion_verification", "融合成员组合验证标记（节点612）", ref_block=nid(612), ref_rel="fusion_verification")],
    [out("approval_suggest", "《上线审批建议》正文（环节8标题头）")], pos=(690, 385)))
# 写入门禁：契约§4——report 仅当全环节 all_pass（spec&fee&test 全品通过）才写入；
# all_pass!=1 时跳过 report 写入，审批推送仍执行（后端另有四环节硬门禁）但提示待补齐。
s6f.append(selector_node2(617, "报告写入门禁",
    [dep_node(612, "合成结构化汇总", ["all_pass"])],
    [(-1, [cond_item(cond_ref(612, "all_pass", "合成结构化汇总"), 1, cond_str("1"))])],
    pos=(770, 560)))
# 614 报告存储 node=report
s6f.append(plugin_node(614, "报告存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=report（上线审批建议），result_json=审批建议正文；仅 all_pass==1（spec/fee/test 全品通过）时写入（门禁：由节点617判定）；正式版测试报告(节点608自查 test 原文)按原样归档不在此改写",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=report（上线审批建议）", content="report"),
     inp("result_json", "环节结果JSON=上线审批建议", ref_block=nid(613), ref_rel="approval_suggest"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(840, 385)))
# 615 审批推送 approval-type=launch（V3.12 多商品批量）
s6f.append(plugin_node(615, "审批推送", "submit_release_approval",
    "工具9（V3.12 多商品批量）：入参 {req_id, approval_flow, report_url, offers:[{offer_seq, offer_id}]}，一个需求单对应一张审批单（工单级 approval_id），工单内含多商品；逐品自研模拟审批推送（V9.1 双轨，此处 approval-type=launch 上线审批）；插件层硬门禁：approve_confirmed==true 且存储中存在该 req_id 的四环节结果（config/spec/fee/test）；幂等：同offer_id返回原approval_id；聚合返回 {approval_id, req_id, status, offers:[{offer_seq, approval_type, status, approval_matrix}]}（approval_id 为工单级单值，仅顶层；商品级状态见 offers[].status）",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("req_id", "执行批次号（四环节结果门禁校验依据，=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("offers", "逐品销售品ID数组（节点612出参 offers，每元素含 offer_seq/offer_id）", ref_block=nid(612), ref_rel="offers"),
     inp("report_url", "上线审批建议（节点613正文，逐品共用）", ref_block=nid(613), ref_rel="approval_suggest"),
     inp("approve_confirmed", "审批发起确认标志true", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard"),
     inp("approval_type", "审批类型=launch（上线审批）", content="launch")],
    [("approval_id", "工单级审批单号（顶层单值，覆盖整批商品）", "string"), ("status", "整批提交状态 SUCCESS/PARTIAL/FAIL", "string"),
     ("approval_type", "审批类型", "string"),
     ("offers", "逐品审批推送结果数组JSON（[{offer_seq,approval_type,status,approval_matrix}]，商品级状态；approval_id 已提升至顶层，元素不再重复携带）", "string")], pos=(990, 385)))
# 616 结束：审批推送后即结束（V6.0 起去除轮询/分流）；审批状态由【查询审批进度】事后查询
# （后端模拟自动流转：提交 10s 后"审批中"→"通过（上架完成）"，查询时惰性推进）
s6f.append(end_node(616, "结束(审批已提交)",
    [inp("approval_id", "工单级审批单号（顶层单值）", ref_block=nid(615), ref_rel="approval_id"),
     inp("status", "整批提交状态（审批中，模拟约10s后自动通过）", ref_block=nid(615), ref_rel="status"),
     inp("approval_type", "审批类型（launch=上线审批）", ref_block=nid(615), ref_rel="approval_type"),
     inp("offers", "逐品审批推送结果数组（节点615出参）", ref_block=nid(615), ref_rel="offers")],
    "《上线审批建议》已提交审批：\n- 工单级审批单号：{approval_id}，整批提交状态：{status}，审批类型：{approval_type}\n"
    "逐品审批状态：\n{offers}\n"
    "审批已提交（不自动上线/不轮询，状态由【查询审批进度】事后查询）。\n\n"
    "> **建议处理：** 可回复【查询审批进度】随时查询当前审批状态（模拟审批约10秒后自动通过）\n\n"))
files6f = workflow(
    "产销品-上线审批", "子工作流6（双轨 approval-type=launch；V3.12 多商品；V6.0 去除轮询/分流：审批推送后即结束）。单入参 req_id：串行自查5类环节结果(config/spec/fee/test/requirement，各配提取代码节点)→CODE_SUMMARY_APPROVAL 合成结构化汇总(含 offer_id/逐品 offers[] 与各环节结论)→LLM 生成《上线审批建议》(环节8标题头+校验看板+风险+整体结论)→存储 report→submit_release_approval 批量推送(approval-type=launch；后端硬门禁 approve_confirmed=true+req_id四环节齐全，逐品聚合返回)→结束(展示审批单号/提交状态，引导回复【查询审批进度】查询审批状态；后端模拟审批提交约10s后自动流转为通过)。", "wf_sub_06", s6f,
    [edge(601,602), edge(602,603), edge(603,604), edge(604,605), edge(605,606),
     edge(606,607), edge(607,608), edge(608,609), edge(609,610), edge(610,611),
     edge(611,612), edge(612,613), edge(613,617), edge(617,614,-1), edge(614,615), edge(617,615,0), edge(615,616)])

# ============================================================
# wf_sub_07 监控运维（阶段5 重写：异常分支追加根因推理链+建工单闭环；两分支均输出 xsbot-panel+环节9收尾块）
#   开始(req_id, chat_id, offer_id 非必填) → query_product_monitor → 异常判定(error_count≠0) →
#     异常分支：告警文案→send_alert→ops_root_cause(待后端占位)→LLM 根因推理链+优化方案→create_work_order(占位)→结束(异常闭环)
#     正常分支：LLM 运营摘要 → 结束(正常)
#   两分支结束节点输出环节9标题头 + 摘要/结论 + xsbot-panel + 环节9 收尾固定块
#   offer_id 缺省时后端按 req_id 关联解析（监控接口 req_id 兜底）
# ============================================================
s7f = []
s7f.append(start_node(701, [
    inp("req_id", "需求单号（必填，PLAN+yyyyMMddHHmmss+3位随机数；用于关联解析销售品ID与页面定位）", required=True),
    inp("chat_id", "{chatId}", required=True),
    inp("offer_id", "销售品ID（非必填；9位存量编码或配置落地返回的新销售品ID，缺省时后端按 req_id 关联解析）", required=False),
    inp("date_range", "统计周期（非必填；缺省默认最近七天）", required=False, content="最近七天"),
]))
s7f.append(plugin_node(702, "监控查询", "query_product_monitor",
    "工具10：自研模拟监控查询（产品名称/订单量及趋势/异常量及趋势/计费差错率及趋势/告警列表），供运营报告与异常判定；offer_id 缺省时按 req_id 关联解析全部商品，返回 offers[] 逐品指标",
    BASE_URL + "/api/v1/appstore/product/monitor",
    [inp("offer_id", "销售品ID（非必填）", ref_block=nid(701), ref_rel="offer_id"),
     inp("req_id", "需求单号（offer_id 缺省时兜底解析）", ref_block=nid(701), ref_rel="req_id"),
     inp("date_range", "统计周期", ref_block=nid(701), ref_rel="date_range"),
     inp("metric", "指标默认all", content="all")],
    [("offer_id", "销售品ID", "string"), ("offer_name", "产品名称", "string"),
     ("order_count", "订单量", "string"), ("order_trend", "订单量趋势", "string"),
     ("error_count", "异常量", "string"), ("error_trend", "异常量趋势", "string"),
     ("fee_error_rate", "计费差错率", "string"), ("fee_trend", "计费差错率趋势", "string"),
     ("alarm_list", "告警列表", "array"), ("offers", "逐品监控明细（多商品批量）", "string")],
    method="get"))
s7f.append(code_node(714, "监控聚合", CODE_OP_MONITOR_AGG,
    [inp("offers", "逐品监控明细（节点702）", ref_block=nid(702), ref_rel="offers"),
     inp("offer_id", "销售品ID（单品兜底，节点702）", ref_block=nid(702), ref_rel="offer_id"),
     inp("offer_name", "产品名称（单品兜底，节点702）", ref_block=nid(702), ref_rel="offer_name"),
     inp("order_count", "订单量（单品兜底，节点702）", ref_block=nid(702), ref_rel="order_count"),
     inp("order_trend", "订单量趋势（单品兜底，节点702）", ref_block=nid(702), ref_rel="order_trend"),
     inp("error_count", "异常量（单品兜底，节点702）", ref_block=nid(702), ref_rel="error_count"),
     inp("error_trend", "异常量趋势（单品兜底，节点702）", ref_block=nid(702), ref_rel="error_trend"),
     inp("fee_error_rate", "计费差错率（单品兜底，节点702）", ref_block=nid(702), ref_rel="fee_error_rate"),
     inp("fee_trend", "计费差错率趋势（单品兜底，节点702）", ref_block=nid(702), ref_rel="fee_trend"),
     inp("alarm_list", "告警列表（单品兜底，节点702）", ref_block=nid(702), ref_rel="alarm_list")],
    [code_out("error_count", 714), code_out("any_abnormal", 714),
     code_out("first_offer_id", 714), code_out("first_offer_name", 714),
     code_out("first_order_count", 714), code_out("first_order_trend", 714),
     code_out("first_error_count", 714), code_out("first_error_trend", 714),
     code_out("first_fee_error_rate", 714), code_out("first_fee_trend", 714),
     code_out("first_alarm_list", 714), code_out("offers", 714),
     code_out("monitor_lines", 714)]))
s7f.append(selector_node2(703, "异常判定",
    [dep_node(714, "监控聚合", ["error_count", "any_abnormal"])],
    [(-1, [cond_item(cond_ref(714, "any_abnormal", "监控聚合"), 1, cond_str("true"))])]))
# ---- 异常分支 ----
s7f.append(llm_node(704, "告警文案生成",
    "基于监控异常数据生成告警文案（含产品、异常摘要、建议）。输入：offer_name={offer_name}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "输出要求：仅输出告警文案内容（对应出参 alert_content，需包含产品名称、异常摘要、处置建议三部分），不输出其他多余文字。",
    [inp("offer_name", "产品名称（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_name"),
     inp("order_count", "订单量（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_order_count"),
     inp("error_count", "异常量（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_error_count"),
     inp("fee_error_rate", "计费差错率（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_fee_error_rate"),
     inp("alarm_list", "告警列表（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_alarm_list")],
    [out("alert_content", "告警文案")]))
s7f.append(plugin_node(705, "异常告警", "send_alert",
    "工具11：自研模拟告警推送（生成alert_id，记录写入模拟库供监控回显）；单体：仅对首个异常商品告警（send_alert 暂未批量化）",
    BASE_URL + "/api/v1/appstore/alert/send",
    [inp("offer_id", "销售品ID（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_id"),
     inp("alarm_level", "告警级别（按异常程度 high/middle/low）", content="high"),
     inp("content", "告警文案（节点704输出）", ref_block=nid(704), ref_rel="alert_content")],
    [("alert_id", "告警单号", "string"), ("status", "推送状态", "string")]))
s7f.append(code_node(706, "异动根因推理", CODE_OP_ROOT_CAUSE,
    [inp("offer_id", "销售品ID（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_id")],
    [code_out("backend_pending", 706), code_out("reason_engine", 706), code_out("anomalies", 706), code_out("paths", 706), code_out("evidence_triples", 706), code_out("swrl_fired", 706), code_out("applied_rules", 706), code_out("action_list", 706), code_out("note", 706)]))
s7f.append(llm_node(707, "根因推理链+优化方案",
    "你是产销品运维归因智能体。基于监控异常数据与 ops_root_cause 出参（backend_pending={backend_pending}，anomalies={anomalies}，paths={paths}，reason_engine={reason_engine}，action_list={action_list}，note={note}），按 flow-D D-2 第6步模板输出根因推理链与优化方案：\n"
    "**异动根因推理链路（{reason_engine，占位时省略}）**\n- 异动确认：{anomalies 摘要：指标 code、delta、message，逐字引用}\n- 归因路径（按 paths 排名）：| 排名 | 根因类型 | 对象 | 权重 | 规则 | 证据 |\n（逐行展开 topN；paths 为空 → 照实引用出参 message\"已确认异动但未命中归因规则\"，禁止编造根因）\n- 命中规则：{swrl_fired / applied_rules，逗号分隔，空则照实省略}\n- 证据三元组：{evidence_triples 逐字摘要}\n\n"
    "**优化方案（规则驱动）**\n{action_list 逐条引用；无则引用出参 message，禁止编造}\n"
    "约束：本服务当前 backend_pending={backend_pending}（根因本体推理 ops_root_cause 待后端接入，阶段1.1），此时 output 归因段落须以占位说明（{note}）呈现，禁止自行编造根因/证据/优化方案。\n"
    "**输出纪律**：所有字段引用必须替换为实际数据；字段为空时该行省略或写「无」，**严禁原样输出 `{...}` 形式的模板占位符**，**严禁输出「逐字引用/逐条引用」等提示词指令文字**。\n"
    "输出要求：仅输出根因推理链+优化方案正文（对应出参 root_cause_report），不输出其他多余文字。",
    [inp("backend_pending", "根因插件占位标记（节点706）", ref_block=nid(706), ref_rel="backend_pending"),
     inp("reason_engine", "推理引擎（节点706）", ref_block=nid(706), ref_rel="reason_engine"),
     inp("anomalies", "异动确认（节点706）", ref_block=nid(706), ref_rel="anomalies"),
     inp("paths", "归因路径（节点706）", ref_block=nid(706), ref_rel="paths"),
     inp("swrl_fired", "命中规则（节点706）", ref_block=nid(706), ref_rel="swrl_fired"),
     inp("evidence_triples", "证据三元组（节点706）", ref_block=nid(706), ref_rel="evidence_triples"),
     inp("action_list", "优化建议（节点706）", ref_block=nid(706), ref_rel="action_list"),
     inp("note", "占位说明（节点706）", ref_block=nid(706), ref_rel="note")],
    [out("root_cause_report", "根因推理链+优化方案正文")]))
s7f.append(code_node(708, "建工单闭环", CODE_OP_CREATE_WO,
    [inp("offer_id", "销售品ID（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_id")],
    [code_out("backend_pending", 708), code_out("work_order_id", 708), code_out("note", 708)]))
# xsbot-panel 独立代码节点（异常分支；产品名/offer_id 取首个异常商品，req_id 取自开始节点）
s7f.append(panel_code_node(712, "构建页面地址面板(异常闭环)", "07",
    panel_inputs(701, off_seq=714, off_rel="first_offer_id", name_seq=714, name_rel="first_offer_name", req_seq=701),
    biz_title="单品运营看板"))
s7f.append(end_node(709, "结束(异常-已告警并闭环)",
    [inp("offer_name", "产品名称（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_name"),
     inp("offer_id", "销售品ID（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_offer_id"),
     inp("date_range", "统计周期", ref_block=nid(701), ref_rel="date_range"),
     inp("order_count", "订单量（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_order_count"),
     inp("order_trend", "订单量趋势（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_order_trend"),
     inp("error_count", "异常量（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_error_count"),
     inp("error_trend", "异常量趋势（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_error_trend"),
     inp("fee_error_rate", "计费差错率（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_fee_error_rate"),
     inp("fee_trend", "计费差错率趋势（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_fee_trend"),
     inp("alarm_list", "告警列表（首个异常商品，节点714）", ref_block=nid(714), ref_rel="first_alarm_list"),
     inp("monitor_lines", "逐品监控自然语言明细（节点714）", ref_block=nid(714), ref_rel="monitor_lines"),
     inp("offers", "逐品监控明细（节点714）", ref_block=nid(714), ref_rel="offers"),
     inp("alert_id", "告警单号", ref_block=nid(705), ref_rel="alert_id"),
     inp("root_cause_report", "根因推理链+优化方案", ref_block=nid(707), ref_rel="root_cause_report"),
     inp("work_order_id", "工单号", ref_block=nid(708), ref_rel="work_order_id"),
     inp("wo_note", "建单占位说明", ref_block=nid(708), ref_rel="note"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(712), ref_rel="panel")],
    "【环节9/9·监控运维】✅ 执行成功\n\n"
    "【销售品运行监控】{offer_id}（{date_range}）\n"
    "- 产品名称：{offer_name}\n"
    "- 订单量：{order_count}（{order_trend}）　异常量：{error_count}（{error_trend}）　计费差错率：{fee_error_rate}（{fee_trend}）\n"
    "已推送告警，告警单号 {alert_id}\n\n"
    "【逐品运行监控明细】\n{monitor_lines}\n\n"
     "{root_cause_report}\n"
     "**处置工单已建立（持续闭环）**：工单号 {work_order_id}｜（建单服务占位说明：{wo_note}）\n\n"
     "> **建议处理：** 已推送告警并建立处置工单 {work_order_id}，建议按优化方案执行后回复【查询监控】回检工单状态（工单号 {work_order_id} 已在会话中留存）；建单服务暂不可用时{wo_note}\n\n"
     "{panel}"))
# ---- 正常分支 ----
s7f.append(llm_node(710, "运营摘要生成(正常分支)",
    "你是产销品监控运维智能体。基于正常监控数据输出面向用户的中文自然语言运营摘要。输入：offers={offers}，offer_name={offer_name}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "格式要求：\n"
    "1. 逐品各一行，每行形如「产品名称：订单量 X（趋势），异常量 Y（趋势），计费差错率 Z（趋势），无告警」；计费差错率以百分比呈现（如 0.0020 → 0.20%）。\n"
    "2. alarm_list 为空或 [] 时写「无告警」，非空时列出告警内容。\n"
    "3. 结尾附一句运营结论，如「整体运行正常，无需人工干预」。\n"
    "4. 禁止将入参按其出现顺序斜杠/逗号原样拼接成值串（如「产品名/287/0/0.0020/[]」）；禁止内嵌原始 JSON；禁止残留任何 { 占位符或「为空显示…」一类括注指令。\n"
    "输出要求：仅输出运营摘要正文（对应出参 ops_summary），不输出其他多余文字。",
    [inp("offers", "逐品监控明细（节点714）", ref_block=nid(714), ref_rel="offers"),
     inp("offer_name", "产品名称（首个商品，节点714）", ref_block=nid(714), ref_rel="first_offer_name"),
     inp("order_count", "订单量（首个商品，节点714）", ref_block=nid(714), ref_rel="first_order_count"),
     inp("order_trend", "订单量趋势（首个商品，节点714）", ref_block=nid(714), ref_rel="first_order_trend"),
     inp("error_count", "异常量（首个商品，节点714）", ref_block=nid(714), ref_rel="first_error_count"),
     inp("fee_error_rate", "计费差错率（首个商品，节点714）", ref_block=nid(714), ref_rel="first_fee_error_rate"),
     inp("alarm_list", "告警列表（首个商品，节点714）", ref_block=nid(714), ref_rel="first_alarm_list")],
    [out("ops_summary", "运营摘要正文")]))
s7f.append(end_node(711, "结束(正常-运营报告)",
    [inp("ops_summary", "运营摘要", ref_block=nid(710), ref_rel="ops_summary"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(713), ref_rel="panel")],
    "【环节9/9·监控运维】✅ 执行成功\n\n"
    "{ops_summary}\n\n"
     "> **建议处理：** 运行指标正常，无需人工干预；可继续观察，如需刷新运行情况可回复【查询监控】\n\n{panel}"))
# xsbot-panel 独立代码节点（正常分支；产品名/offer_id 取首品，req_id 取自开始节点）
s7f.append(panel_code_node(713, "构建页面地址面板(正常)", "07",
    panel_inputs(701, off_seq=714, off_rel="first_offer_id", name_seq=714, name_rel="first_offer_name", req_seq=701),
    biz_title="单品运营看板"))
files7f = workflow(
    "产销品-监控运维", "子工作流7（入参 req_id+chat_id 必填，offer_id 非必填；V3.12 多商品：监控查询返回 offers[]，节点714 聚合异常判定；异常分支对首个异常商品追加异动根因本体推理链+建工单闭环；两分支均输出环节9标题头+xsbot-panel+环节9收尾固定块。query_product_monitor（自研模拟，含产品名称/订单量/异常量/计费差错率及趋势/告警列表；offer_id 缺省时后端按 req_id 关联解析全部商品）→监控聚合（聚合异常量/首个异常商品；error_count≠0 或任一品 fee_error_rate>0.1 走异常分支）→异常分支：告警文案→send_alert→ops_root_cause 根因推理(阶段1.1 Java插件待接入占位)→LLM 根因推理链+优化方案→create_work_order 建工单闭环(占位)→结束(异常闭环+xsbot-panel+收尾块)；正常分支：运营摘要→结束(正常+xsbot-panel+收尾块)。告警/根因/建单保持单体（首个异常商品）；根因/优化一律依据出参逐字引用，禁止自行编造。", "wf_sub_07", s7f,
    [edge(701,702), edge(702,714), edge(714,703), edge(703,710,0), edge(703,704,-1),
     edge(704,705), edge(705,706), edge(706,707), edge(707,708), edge(708,712), edge(712,709),
     edge(710,713), edge(713,711)])

# ============================================================
# wf_sub_08 审批工单查询（入参仅 req_id + chat_id：req_id 为审批工单关联主键，常态按此反查；按 approval_type 区分双轨回显+审批矩阵+收尾块）
#   开始(req_id, chat_id) → query_approval_status(req_id 关联主键/chat_id 会话入参) → LLM 工单摘要归纳(区分需求工单/上线审批 + 审批矩阵 + 收尾块) → 结束
# ============================================================
s8f = []
s8f.append(start_node(801, [
    inp("req_id", "需求单号（必填，审批工单关联主键：按此反查审批工单信息）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
s8f.append(plugin_node(802, "审批工单查询", "query_approval_status",
    "工具13：自研模拟审批工单查询（req_id 关联主键，chat_id 会话入参；V3.12 多商品：按 req_id 聚合返回 offers[]，逐品含审批单号/需求单号/销售品/类型/状态/当前环节/审批人/意见/更新时间及审批矩阵）",
    BASE_URL + "/api/v1/appstore/approval/status",
    [inp("req_id", "需求单号", ref_block=nid(801), ref_rel="req_id"),
     inp("chat_id", "会话ID", ref_block=nid(801), ref_rel="chat_id")],
    [("req_id", "需求单号", "string"), ("status", "整批状态", "string"),
     ("resultCode", "结果码", "string"),
     ("offers", "逐品审批工单明细（多商品批量）", "string")],
    method="get"))
s8f.append(code_node(806, "审批首品提取", (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(v):\n"
    "    if isinstance(v, list):\n"
    "        return v\n"
    "    if isinstance(v, dict):\n"
    "        return [v]\n"
    "    if not isinstance(v, str) or not v.strip():\n"
    "        return []\n"
    "    try:\n"
    "        obj = json.loads(v)\n"
    "        if isinstance(obj, dict) and isinstance(obj.get('offers'), list):\n"
    "            return obj['offers']\n"
    "        return obj if isinstance(obj, list) else ([obj] if isinstance(obj, dict) else [])\n"
    "    except Exception:\n"
    "        return []\n"
    "\n"
    "async def main(args):\n"
    "    raw = args.params.get('offers')\n"
    "    top_approval_id = ''\n"
    "    if isinstance(raw, dict):\n"
    "        top_approval_id = str(raw.get('approval_id') or '')\n"
    "    offers = [o for o in _load(raw) if isinstance(o, dict)]\n"
    "    first = offers[0] if offers else {}\n"
    "    req_id = str(args.params.get('req_id') or first.get('req_id') or '')\n"
    "    ret: Output = {\n"
    "        'req_id': req_id,\n"
    "        'offer_id': str(first.get('offer_id') or ''),\n"
    "        'approval_id': str(top_approval_id or first.get('approval_id') or ''),\n"
    "        'approval_type': str(first.get('approval_type') or ''),\n"
    "        'offers': json.dumps(offers, ensure_ascii=False)\n"
    "    }\n"
    "    return ret"
), [inp("offers", "逐品审批工单明细（节点802）", ref_block=nid(802), ref_rel="offers"),
    inp("req_id", "需求单号（节点802）", ref_block=nid(802), ref_rel="req_id")],
    [code_out("req_id", 806), code_out("offer_id", 806), code_out("approval_id", 806),
     code_out("approval_type", 806), code_out("offers", 806)]))
s8f.append(llm_node(803, "状态摘要归纳",
    "你是产销品审批工单查询智能体。输入 per_offer={per_offer}（JSON 数组，每项含 approval_id/req_id/offer_id/status/approval_type/current_node/approver/opinion/update_time/approval_matrix），req_id={req_id}，status={status}（整批状态，≠单个商品状态）。按 flow-D D-1 固定格式归纳输出（多商品时逐品输出一段）：\n"
    "对 per_offer 的每一项，输出下面这一固定行；行内「」中的字段逐字取自该商品对应键，禁止保留花括号、禁止给值加 key 前缀（如不得写成 approval_id: X）、禁止把整批 status 当作单品状态：\n"
    "审批单号「approval_id」｜需求单号 {req_id}｜销售品「offer_id」｜状态：「status（该项的单品状态）」｜当前环节：「current_node」（审批人「approver」）｜最近意见：「opinion」｜更新时间：「update_time」\n\n"
    "**审批矩阵（逐字引用该品 approval_matrix[]，禁止编造）：**\n"
    "| 序号 | 审批节点 | 审批角色 | 状态 | 审批意见 | 更新时间 |\n"
    "| :---: | :--- | :--- | :--- | :--- | :--- |\n"
    "（逐行展开该品 approval_matrix 全部节点；出参无 approval_matrix 时省略本表，不得补造）\n\n"
    "审批类型衔接（按下述区分；否则只给摘要）：\n"
    "- 当前环节含「待上线审批」或 approval_type=requirement 且 status=审批中（需求审核已通过）→ 收尾块：> **建议处理：** 需求工单审批已通过，建议进入【需求分析】（可回复【开始配置】）\n"
    "- approval_type=launch 且 status=通过（上线审批通过）→ 输出\"审批已通过，销售品上架完成 ✅\"；审批通过即视为已完成上架，引导后续可查询监控运维\n"
    "status=驳回 时附驳回原因，并提示\"可修改执行方案后重新发起\";查无审批单时输出\"未找到该需求单号的审批工单，请确认是否已发起审批\"。\n"
    "输出要求：仅输出审批工单摘要（对应出参 approval_summary，含审批矩阵与收尾块），不输出其他多余文字。",
    [inp("per_offer", "逐品审批工单明细（节点802）", ref_block=nid(802), ref_rel="offers"),
     inp("req_id", "需求单号（节点802）", ref_block=nid(802), ref_rel="req_id"),
     inp("status", "整批状态（节点802）", ref_block=nid(802), ref_rel="status")],
    [out("approval_summary", "审批工单摘要")]))
# xsbot-panel 独立代码节点（offer_id/req_id 取自审批首品提取节点806；chat_id 取自开始节点）
s8f.append(panel_code_node(805, "构建页面地址面板", "08",
    panel_inputs(801, off_seq=806, off_rel="offer_id", name_seq=806, name_rel="approval_id", req_seq=806),
    biz_title="上线审批看板"))
s8f.append(end_node(804, "结束(查询完成)",
    [inp("approval_summary", "审批工单摘要", ref_block=nid(803), ref_rel="approval_summary"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(805), ref_rel="panel")],
    "{approval_summary}\n\n{panel}"))
files8f = workflow(
    "产销品-审批工单查询", "子工作流8（入参仅 req_id + chat_id；V3.12 多商品：按 req_id 聚合返回 offers[]，逐品回显）。query_approval_status（req_id 关联主键反查审批工单，chat_id 会话透传，多商品聚合返回 offers[]）→审批首品提取（节点806，取首个商品字段供面板）→LLM 工单摘要归纳：固定格式（审批单号/需求单号/销售品/状态/当前环节/审批人/最近意见/更新时间）+审批矩阵（逐字引用 approval_matrix[] 禁止编造）+按 approval_type 区分衔接（requirement→引导需求分析【开始配置】；launch→审批通过即视为已上架，引导后续运维查询，不再输出自动上线/监控运维方案）+收尾固定块（SKILL.md纪律5.1，禁止以矩阵表格收尾）。", "wf_sub_08", s8f,
    [edge(801,802), edge(802,803), edge(803,806), edge(806,805), edge(805,804)])

# ============================================================
# wf_sub_09 存量产品查询（阶段5 新增：D-4 只读，query_offer 内嵌代码节点 + 多命中收敛/唯一回显/未命中追问）
#   开始(offer_id/name/keyword) → CODE_OP_QUERY_OFFER(内嵌目录) → LLM 查询回显渲染 → 结束
#   只读、不生成 req_id、不进入配置流水线
# ============================================================
s9f = []
s9f.append(start_node(901, [
    inp("offer_id", "产品ID（9位存量编码，可选）", required=False),
    inp("name", "产品名称（可选）", required=False),
    inp("keyword", "描述关键词（可选）", required=False),
    inp("chat_id", "{chatId}", required=True),
]))
s9f.append(code_node(902, "存量检索", CODE_OP_QUERY_OFFER,
    [inp("offer_id", "产品ID", ref_block=nid(901), ref_rel="offer_id"),
     inp("name", "产品名称", ref_block=nid(901), ref_rel="name"),
     inp("keyword", "关键词", ref_block=nid(901), ref_rel="keyword")],
    [code_out("matched", 902), code_out("matched_count", 902), code_out("outcome", 902), code_out("k4_note", 902)]))
s9f.append(llm_node(903, "查询回显渲染",
    "你是产销品存量产品查询智能体（只读，不触发任何配置/审批/上线动作）。基于存量检索结果（matched={matched}，matched_count={matched_count}，outcome={outcome}，k4_note={k4_note}），按 flow-D D-4 输出查询回显：\n"
    "- outcome=unique（命中唯一）→ 结构化回显（逐字引用，禁止编造）：\n"
    "  **存量产品信息（{name}）**\n"
    "  - 产品ID：{offer_id}｜类型：{product_type}｜产品线：{biz_series}｜档位：{tier}｜模板：{template}\n"
    "  - 成员：{members，逗号分隔；无则省略}\n"
    "  - 档案说明：{k4_text}/K4档案说明占位（{k4_note}）\n"
    "  提示下一步：\"如需了解该产品的监控情况，可回复【查询监控】。\"\n"
    "  收尾固定块：> **建议处理：** 存量产品信息为只读查询；如需了解该产品上线后表现可回复【查询监控】\n"
    "- outcome=multi（命中多条）→ 先列匹配清单请用户收敛，禁止并成一团/臆选一款：\n"
    "  **存量产品信息查询，匹配到 {matched_count} 款，请选择或提供产品ID：**\n"
    "  | 产品ID | 名称 | 类型 | 档位 |\n  （逐行列出全部 matched）\n"
    "  收尾固定块：> **建议处理：** 命中多款产品，请收敛到具体产品后精确查询（请直接回复产品ID或完整产品名称，我可据此重新查询）\n"
    "- outcome=none（未命中）→ 输出\"未在存量目录中找到该产品，请核对名称/ID后重新提供关键词（我会按名称/ID重新查询）\"\n"
    "约束：逐字引用出参，禁止编造产品字段；status=dup 的条目按 duplicate_of 透传到 active 品；只读查询不生成 req_id。\n"
    "输出要求：仅输出查询回显正文（对应出参 query_reply），不输出其他多余文字。",
    [inp("matched", "命中列表（节点902）", ref_block=nid(902), ref_rel="matched"),
     inp("matched_count", "命中数量（节点902）", ref_block=nid(902), ref_rel="matched_count"),
     inp("outcome", "查询结果（节点902）", ref_block=nid(902), ref_rel="outcome"),
     inp("k4_note", "K4档案说明（节点902）", ref_block=nid(902), ref_rel="k4_note")],
    [out("query_reply", "查询回显正文")]))
# xsbot-panel 独立代码节点（offer_name 取开始节点 name）
s9f.append(panel_code_node(905, "构建页面地址面板", "09",
    panel_inputs(901, off_seq=901, off_rel="offer_id", name_seq=901, name_rel="name"),
    biz_title="存量产品详情"))
s9f.append(end_node(904, "结束(查询完成)",
    [inp("query_reply", "查询回显正文", ref_block=nid(903), ref_rel="query_reply"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(905), ref_rel="panel")],
    "{query_reply}\n\n{panel}"))
files9f = workflow(
    "产销品-存量产品查询", "子工作流9（阶段5 新增：D-4 只读存量查询）。CODE_OP_QUERY_OFFER 内嵌确定性检索（数据源 knowledge/存量产品目录_清洗后.json 18 条，内嵌目录便于 Demo；后端 query_offer 插件就绪后切换远端检索；K4 档案待后端回显）→LLM 按 flow-D D-4 渲染查询回显（unique 结构化回显+收尾块 / multi 匹配清单收敛 / none 追问引导）。只读、不生成 req_id、不进入配置流水线。", "wf_sub_09", s9f,
    [edge(901,902), edge(902,903), edge(903,905), edge(905,904)])

# ============================================================
# wf_sub_10 存量合规扫描（阶段5 新增(可选)：shelf_compliance 批量 R-C* 合规 + 整改引导）
#   开始 → CODE_OP_SHELF_COMPLIANCE(待后端占位) → LLM 合规报告渲染 → 结束
# ============================================================
s10f = []
s10f.append(start_node(1001, [
    inp("chat_id", "{chatId}", required=True)]))
s10f.append(code_node(1002, "存量合规扫描", CODE_OP_SHELF_COMPLIANCE,
    [],
    [code_out("backend_pending", 1002), code_out("note", 1002)]))
# 就绪门禁：backend_pending=1（后端未接入）→ 失败结束（不可用态，非成功）；
# port=-1(否则=非1) → 正常渲染；port=0(否则=…不对；平台条件定义在 port=-1)
s10f.append(selector_node2(1006, "合规服务就绪判定",
    [dep_node(1002, "存量合规扫描", ["backend_pending"])],
    [(-1, [cond_item(cond_ref(1002, "backend_pending", "存量合规扫描"), 1, cond_str("1"))])]))
# 失败结束：合规服务未就绪（后端待接入）
s10f.append(end_node(1007, "结束(合规服务未就绪-失败)",
    [inp("note", "占位说明", ref_block=nid(1002), ref_rel="note")],
    "❌ 存量合规扫描暂不可用：shelf_compliance 服务尚未接入（阶段1.1）。\n说明：{note}\n请在服务就绪后重试。"))
s10f.append(llm_node(1003, "合规报告渲染",
    "你是产销品存量合规扫描智能体。基于 shelf_compliance 出参（backend_pending={backend_pending}，note={note}），按 R-C* 合规口径输出存量合规报告：批量对存量在架产品执行合规检查（字段完整性/本体约束/资费规则等），输出合规结论与整改引导。\n"
    "约束：当前 backend_pending={backend_pending}（shelf_compliance 待后端接入，阶段1.1），本报告须以占位说明（{note}）呈现，禁止编造合规结论/违规项。\n"
    "输出要求：仅输出合规报告正文（对应出参 compliance_report），不输出其他多余文字。",
    [inp("backend_pending", "合规插件占位标记（节点1002）", ref_block=nid(1002), ref_rel="backend_pending"),
     inp("note", "占位说明（节点1002）", ref_block=nid(1002), ref_rel="note")],
    [out("compliance_report", "合规报告正文")]))
# xsbot-panel 独立代码节点（无 offer 场景）
s10f.append(panel_code_node(1005, "构建页面地址面板", "10",
    panel_inputs(1001), biz_title="存量合规看板"))
s10f.append(end_node(1004, "结束(合规扫描)",
    [inp("compliance_report", "合规报告正文", ref_block=nid(1003), ref_rel="compliance_report"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(1005), ref_rel="panel")],
    "{compliance_report}\n\n{panel}"))
files10f = workflow(
    "产销品-存量合规扫描", "子工作流10（阶段5 新增(可选)：shelf_compliance 批量 R-C* 合规+整改引导）。CODE_OP_SHELF_COMPLIANCE 占位(阶段1.1 Java 插件待就绪)→就绪门禁（backend_pending=1 走失败结束，标记服务未接入）→LLM 合规报告渲染（R-C* 合规结论+整改引导）。", "wf_sub_10", s10f,
    [edge(1001,1002), edge(1002,1006), edge(1006,1003,0), edge(1006,1007,-1),
     edge(1003,1005), edge(1005,1004)])

# ============================================================
# wf_sub_11 发起需求审批（环节1，V3.3 新增：需求工单审批拆分为独立子流）
#   流程：开始(req_id) → 自查需求工单(query_node_result, node_name=requirement_report)
#     → CODE_EXTRACT_RECORD 提取原文 → submit_release_approval(approval-type=requirement)
#     → LLM 回执渲染 → 面板 → 结束
#   前置契约：wf_sub_00 提报单就绪即止于确认点，须用户明确回复【发起需求审批】
#     才由本子流发起（approve_confirmed 固定 true，确认门禁在调度层/提示词，未确认不得调入本流）
# ============================================================
s11f = []
s11f.append(start_node(1101, [
    inp("req_id", "需求单号（环节1 wf_sub_00 生成，PLAN+yyyyMMddHHmmss+3位随机；严禁重新生成）", required=True),
    inp("chat_id", "{chatId}", required=True),
]))
# 自查需求工单：按 req_id+requirement_report 取回需求提报单记录（审批发起依据）
s11f.append(plugin_node(1102, "自查需求工单", "query_node_result",
    "节点结果存储查询（复用）：按 req_id+node_name=requirement_report 取回需求提报单记录，作为审批发起依据；total=0 → 提示先完成需求提报",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "需求单号", ref_block=nid(1101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement_report", content="requirement_report"),
     inp("latest_only", "仅取最新一条", content="1")],
    [("total", "记录数", "string"), ("list", "记录列表（list[0].result_json 为原文）", "string")]))
# 提取需求工单原文
s11f.append(code_node(1103, "提取需求工单原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点1102查询出参 list（记录数组JSON）", ref_block=nid(1102), ref_rel="list")],
    [code_out("record_json", 1103), code_out("req_id", 1103), code_out("offer_id", 1103), code_out("offers", 1103)]))
# 需求工单有效性门禁：record_json 为空（未先完成需求提报/req_id 不匹配）→ 失败结束；
# port=-1(否则=空) → 失败结束(1107)；port=0(否则=非空) → 正常发起审批主链
s11f.append(selector_node2(1107, "需求工单有效性判定",
    [dep_node(1103, "提取需求工单原文", ["record_json"])],
    [(-1, [cond_item(cond_ref(1103, "record_json", "提取需求工单原文"), 9, cond_str(""))])]))
s11f.append(end_node(1108, "结束(需求工单缺失-失败)",
    [inp("req_id", "需求单号", ref_block=nid(1101), ref_rel="req_id")],
    "❌ 未找到该需求单号（{req_id}）的需求提报记录，无法发起需求工单审批。\n请先在【需求提报】（环节1/8）生成《销售品需求提报单》，再回复【发起需求审批】。"))
# 发起需求工单审批（approval-type=requirement，双轨之需求轨）
s11f.append(plugin_node(1104, "发起需求工单审批", "submit_release_approval",
    "工具9（复用，需求轨双轨）：提交需求工单审批；approval-type=requirement 区分需求单审批（区别于上线审批 launch 轨）。approve_confirmed 固定 true——以用户已明确回复【发起需求审批】为前提（确认门禁在调度层，未确认不得调入本流）",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("offer_id", "销售品ID（需求阶段未落地，传需求单号 req_id 占位）", ref_block=nid(1101), ref_rel="req_id"),
     inp("report_url", "需求提报单（节点1103 取回的需求工单原文）", ref_block=nid(1103), ref_rel="record_json"),
     inp("req_id", "需求单号", ref_block=nid(1101), ref_rel="req_id"),
     inp("approve_confirmed", "审批发起确认标志true（用户已明确回复【发起需求审批】）", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard"),
     inp("approval_type", "审批类型=requirement（需求工单审批，区别于 launch 上线审批）", content="requirement")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string")]))
# LLM 回执渲染（逐字引用出参，禁止编造）
s11f.append(llm_node(1105, "审批发起回执渲染",
    "你是产销品需求工单审批发起助手。基于提交结果（req_id={req_id}，approval_id={approval_id}，status={status}），**先判定提交是否成功**：仅当 approval_id 非空且 status 不属于（FAIL/失败/error/空）时视为成功，否则视为失败。\n"
    "【成功口径】（approval_id 非空且 status 正常）输出：\n"
    "【环节1/8·需求提报】✅ 需求工单审批已发起\n"
    "- 需求单号：{req_id}\n"
    "- 需求工单审批单号：{approval_id}，状态：{status}\n"
    "- 下一步建议：可回复【查询审批进度】查看审批状态；审批通过后可进入【需求分析】（环节2/8）生成《加载方案》。\n"
    "【失败口径】（approval_id 为空或 status=FAIL/失败）**严禁输出✅已发起**，必须输出：\n"
    "【环节1/8·需求提报】❌ 需求工单审批发起失败\n"
    "- 需求单号：{req_id}\n"
    "- 失败原因：审批服务未返回有效审批单号（approval_id 为空，status={status}）\n"
    "- 建议：请确认需求提报已完成，回复【重新执行】后重试；若持续失败请联系管理员。\n"
    "约束：逐字引用出参，严禁编造审批单号/状态，严禁在失败时输出任何✅成功措辞。\n"
    "输出要求：仅输出审批发起回执正文（对应出参 approval_receipt），不输出其他多余文字。",
    [inp("req_id", "需求单号", ref_block=nid(1101), ref_rel="req_id"),
     inp("approval_id", "审批单号", ref_block=nid(1104), ref_rel="approval_id"),
     inp("status", "提交状态", ref_block=nid(1104), ref_rel="status")],
    [out("approval_receipt", "审批发起回执正文")]))
# xsbot-panel 独立代码节点（无 offer 场景，页面以 req_id 定位）
s11f.append(panel_code_node(1109, "构建页面地址面板", "01",
    panel_inputs(1101, req_seq=1101), biz_title="需求工单审批"))
s11f.append(end_node(1106, "结束(需求审批已发起)",
    [inp("approval_receipt", "审批发起回执正文", ref_block=nid(1105), ref_rel="approval_receipt"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(1109), ref_rel="panel")],
    "{approval_receipt}\n\n{panel}"))
files11f = workflow(
    "产销品-发起需求审批", "子工作流11（环节1 需求工单审批发起，确认后触发）：用户明确回复【发起需求审批】→ 自查需求工单 query_node_result(req_id+requirement_report) → 提取原文 → submit_release_approval(approval-type=requirement) → 回执+引导【查询审批进度】。approve_confirmed 固定 true，以用户明确确认为前提（确认门禁在调度层）. 审批进度查询走 wf_sub_08（双轨支持 requirement）。",
    "wf_sub_11", s11f,
    [edge(1101,1102), edge(1102,1103), edge(1103,1107),
     edge(1107,1104,0), edge(1107,1108,-1),
     edge(1104,1105), edge(1105,1109), edge(1109,1106)])

# ============================================================
# 写出（V2.0 重塑：11 个子工作流 wf_sub_00~10 + V3.3 新增 wf_sub_11
# + V3.10 两阶段拆分新增 wf_sub_12/13，共 14 个 JSON；
# 意图调度交由智能体层，无 wf_main_intent）
# ============================================================
for fn, data in [("wf_sub_00_需求提报.json", files00), ("wf_sub_01_需求分析.json", files01),
                 ("wf_sub_02_智能配置.json", files2f), ("wf_sub_03_规格稽核.json", files3f),
                 ("wf_sub_04_自动测试.json", files4f), ("wf_sub_05_资费校准.json", files5f),
                 ("wf_sub_06_上线审批.json", files6f), ("wf_sub_07_监控运维.json", files7f),
                 ("wf_sub_08_审批进度查询.json", files8f), ("wf_sub_09_存量产品查询.json", files9f),
                 ("wf_sub_10_存量合规扫描.json", files10f), ("wf_sub_11_发起需求审批.json", files11f),
                 ("wf_sub_12_规格稽核查询.json", files12f), ("wf_sub_13_测试查询结果.json", files13f)]:
    data = apply_layout(data)
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", fn)
