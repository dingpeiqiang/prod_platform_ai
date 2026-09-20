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
    CODE_SUMMARY_APPROVAL, CODE_PACK_RECORD,
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
    结束节点模板仅引用 {panel}。读取参数 chat_id/offer_id/offer_name/req_id，运行期完成实值替换。
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
        "    biz_url = ('%s?offer_id=' + offer_id + '&name=' + offer_name + '&chatId=' + chat_id "
        "               + '&req_id=' + req_id + '&stage=%s&view=stage')" % (page, stage),
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
    "FUSION_PRODUCT_TYPES = ('家庭基础套餐',)\n"
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
    "def fields_for(product_type):\n"
    "    if _norm(product_type) in FUSION_PRODUCT_TYPES:\n"
    "        return BASE_FIELDS + FUSION_EXTRA_FIELDS\n"
    "    return BASE_FIELDS\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    raw = p.get('elements_json') or ''\n"
    "    elements = {}\n"
    "    if isinstance(raw, str) and raw.strip():\n"
    "        try:\n"
    "            elements = json.loads(raw)\n"
    "        except Exception:\n"
    "            try:\n"
    "                t = raw.strip()\n"
    "                if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "                    t = t.split(':', 1)[1].strip()\n"
    "                elements = json.loads(t)\n"
    "            except Exception:\n"
    "                elements = {}\n"
    "    elif isinstance(raw, dict):\n"
    "        elements = raw\n"
    "    req_id = str(p.get('req_id') or _gen_req_id())\n"
    "    reporter = str(p.get('reporter') or '')\n"
    "    need_summary = str(p.get('need_summary') or '')\n"
    "    today = datetime.date.today().strftime('%Y-%m-%d')\n"
    "    fmt = lambda k: _norm(elements.get(k))\n"
    "    fields = fields_for(elements.get('product_type'))\n"
    "    values = {}\n"
    "    pending = []\n"
    "    for key, label in fields:\n"
    "        val = fmt(key)\n"
    "        values[key] = val\n"
    "        if key in REQUIRED_FIELDS and (val == '' or (key in PRICE_FIELDS and val in (0, '0', '0元'))):\n"
    "            pending.append(label)\n"
    "    def cell(v):\n"
    "        return '待补充' if v == '' else str(v)\n"
    "    lines = []\n"
    "    lines.append('## 销售品需求提报单')\n"
    "    base_rows = [('需求单号', req_id), ('提报日期', today)]\n"
    "    if reporter:\n"
    "        base_rows.append(('提报人', reporter))\n"
    "    if need_summary:\n"
    "        base_rows.append(('需求概述', need_summary))\n"
    "    prod_rows = [('销售品名称', cell(values['name'])), ('产品类型', cell(values['product_type']))]\n"
    "    if values['series']:\n"
    "        prod_rows.append(('所属系列', values['series']))\n"
    "    if values.get('members'):\n"
    "        prod_rows.append(('融合成员', values['members']))\n"
    "    fee_rows = [('套餐档位（月费）', (values['price'] + ' 元/月') if values['price'] != '' else '待补充')]\n"
    "    if values['resources']:\n"
    "        fee_rows.append(('套内资源', values['resources']))\n"
    "    if values['out_price']:\n"
    "        fee_rows.append(('套外资费', values['out_price']))\n"
    "    if values['billing_cycle']:\n"
    "        fee_rows.append(('计费周期', values['billing_cycle']))\n"
    "    order_rows = []\n"
    "    if values['effective_way']:\n"
    "        order_rows.append(('生效方式', values['effective_way']))\n"
    "    if values['validity']:\n"
    "        order_rows.append(('套餐有效期', values['validity']))\n"
    "    if values['change_rule']:\n"
    "        order_rows.append(('变更规则', values['change_rule']))\n"
    "    if values['cancel_rule']:\n"
    "        order_rows.append(('退订/拆机规则', values['cancel_rule']))\n"
    "    def table(rows):\n"
    "        if not rows:\n"
    "            return []\n"
    "        out = ['| 字段 | 内容 |', '| --- | --- |']\n"
    "        out += ['| %s | %s |' % (k, v) for k, v in rows]\n"
    "        return out\n"
    "    lines.append('### 1. 需求基本信息')\n"
    "    lines += table(base_rows)\n"
    "    lines.append('')\n"
    "    lines.append('### 2. 产品/销售品信息')\n"
    "    lines += table(prod_rows)\n"
    "    lines.append('')\n"
    "    lines.append('### 3. 资费方案要点')\n"
    "    lines += table(fee_rows)\n"
    "    lines.append('')\n"
    "    lines.append('### 4. 订购/变更/退订规则')\n"
    "    if order_rows:\n"
    "        lines += table(order_rows)\n"
    "    else:\n"
    "        lines.append('（未提及，从宽不催补）')\n"
    "    lines.append('')\n"
    "    lines.append('### 5. 待补充字段')\n"
    "    lines.append('、'.join(pending) if pending else '无')\n"
    "    while lines and lines[-1] == '':\n"
    "        lines.pop()\n"
    "    text = '\\n'.join(lines)\n"
    "    ret: Output = {\n"
    "        \"report_text\": text,\n"
    "        \"pending_fields\": ','.join(pending),\n"
    "        \"pending_count\": (str(len(pending)) if pending else ''),\n"
    "        \"req_id\": req_id,\n"
    "        \"elements\": json.dumps(values, ensure_ascii=False),\n"
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

# ---------------- CODE_RENDER_TABLE：逻辑模型报文→业务分节表格 ----------------
# 来源 scripts/render_table.py（V3.0，去 argparse）
# 输入：schema_json、payload（合并节点输出报文 JSON）、meta（溯源 JSON）、
#       similar_offer（相似产品信息 JSON，用于来源列拼接）、title
# 输出：table_text（markdown 分节多表）
CODE_RENDER_TABLE = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "SKIP_KEYS = ('templateId', 'prodId', 'prodPrcId', 'pricingId', 'opType')\n"
    "SYSTEM_GEN_KEYS = ('orderNo',)\n"
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
    "                    if sub.get('x-required') and key not in SYSTEM_GEN_KEYS and not hidden(obj_schema, sub, key, obj_data, path):\n"
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
    "async def main(args):\n"
    "    p = args.params\n"
    "    def load(text):\n"
    "        if not isinstance(text, str) or not text.strip():\n"
    "            return {}\n"
    "        try:\n"
    "            return json.loads(text)\n"
    "        except Exception:\n"
    "            try:\n"
    "                t = text.strip()\n"
    "                if ':' in t and not t.startswith('{') and not t.startswith('['):\n"
    "                    t = t.split(':', 1)[1].strip()\n"
    "                return json.loads(t)\n"
    "            except Exception:\n"
    "                return {}\n"
    "    schema = load(p.get('schema_json') or '')\n"
    "    data = load(p.get('payload') or '')\n"
    "    meta = load(p.get('meta') or '')\n"
    "    similar_offer = load(p.get('similar_offer') or '')\n"
    "    title = str(p.get('title') or '')\n"
    "    if schema.get('x-template') in data:\n"
    "        data = data[schema['x-template']]\n"
    "    pending = []\n"
    "    sim_ref = similar_ref(similar_offer)\n"
    "    sections = collect(schema, data, pending, meta, sim_ref)\n"
    "    lines = []\n"
    "    if title:\n"
    "        lines.append('## ' + title)\n"
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
    "    ret: Output = {\n"
    "        \"table_text\": '\\n'.join(lines),\n"
    "        \"pending_count\": str(len(pending)),\n"
    "    }\n"
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
    inp("chat_id", "{chatId}", required=True),
]))
# 需求字段抽取 LLM：snake_case 平面 JSON（render_requirement_report 输入契约）
s00.append(llm_node(2, "需求字段抽取",
    "你是产销品需求提报助手（环节1 需求提报），只做一件事：把用户需求原文翻译为结构化需求字段 JSON，不做补全、不做完整性判断、不渲染文档、不做任何业务决策。需求原文：{requirement_text}\n"
    "字段口径（snake_case 平面 JSON，逐字引用用户描述，未提及字段填空字符串）：\n"
    "- name：销售品名称；product_type：产品类型（个人主套餐/个人附加资费/宽带主套餐/宽带附加资费/家庭基础套餐/家庭附加资费）；series：所属系列\n"
    "- members：融合成员（仅融合品如家庭基础套餐，逗号/顿号分隔；单商品不填）\n"
    "- price：套餐档位（月费，元/月）；resources：套内资源（流量/语音/短信/副卡等）；out_price：套外资费\n"
    "- billing_cycle：计费周期；effective_way：生效方式；validity：套餐有效期\n"
    "- change_rule：变更规则；cancel_rule：退订/拆机规则\n"
    "强制同义映射：月费/月租/套餐费+金额→price；每月XX G/含XX流量→resources；XX分钟语音/通话→resources；支持副卡→resources；按月付费→billing_cycle；X月X日生效→effective_way；退订/拆机→cancel_rule。\n"
    "禁止臆造字段值；原文未明确的字段一律空字符串。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含一个键 elements_json，其值为上述需求字段 JSON 的字符串（即先按字段口径生成字段 JSON，再将该字段 JSON 整体作为 elements_json 的值，值为一个字符串，形如 {\"elements_json\": \"{\\\"name\\\":\\\"...\\\",...}\"}）；严禁输出其他文字、标签前缀、摘要、说明，严禁使用 Markdown 代码块包裹（不要输出```json```围栏）。",
    [inp("requirement_text", "引用开始节点需求文本", ref_block=nid(1), ref_rel="requirement_text")],
    [out("elements_json", "需求字段 snake_case 平面 JSON（未提及项为空字符串）")]))
# render_requirement_report 代码节点：确定性渲染需求提报单 + req_id 生成 + 待补充判定
s00.append(code_node(3, "需求提报单渲染", CODE_RENDER_REQ,
    [inp("elements_json", "引用节点2需求字段 JSON", ref_block=nid(2), ref_rel="elements_json"),
     inp("reporter", "提报人（开始节点）", ref_block=nid(1), ref_rel="reporter")],
    [code_out("report_text", 3), code_out("pending_fields", 3), code_out("pending_count", 3), code_out("req_id", 3), code_out("elements", 3)],
    pos=(650, 300)))
# 待补充判定：pending_count 长度大于0 → 有待补充（port=-1）；否则（=0/空）→ 无待补充（port=0）
s00.append(selector_node2(4, "待补充判定",
    [dep_node(3, "需求提报单渲染", ["pending_count"])],
    [(-1, [cond_item(cond_ref(3, "pending_count", "需求提报单渲染"), 10, cond_str(""))])]))
# 无待补充分支：保存需求工单（node_name=requirement_report，供 wf_sub_01 需求分析延续 req_id）
s00.append(plugin_node(5, "保存需求工单", "save_node_result",
    "节点结果存储（复用）：req_id=代码节点生成的需求单号 req_id，node_name=requirement_report（需求提报单），result_json=归一后需求字段 JSON；同键覆盖；供 wf_sub_01 需求分析按 req_id+requirement_report 读取延续",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求单号（=代码节点生成 req_id，PLAN+yyyyMMddHHmmss+3位随机）", ref_block=nid(3), ref_rel="req_id"),
     inp("node_name", "环节名=requirement_report（需求提报单）", content="requirement_report"),
     inp("result_json", "需求字段 JSON（=代码节点渲染后归一 elements）", ref_block=nid(3), ref_rel="elements"),
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
    "产销品-需求提报", "子工作流0：需求提报（环节1，双轨之需求轨）。需求文本→LLM需求字段抽取（snake_case平面JSON，未提及项空串）→代码节点render_requirement_report确定性渲染《销售品需求提报单》并生成需求单号req_id+待补充判定→无待补充：保存需求工单(node_name=requirement_report)后**止于确认点**（提示『是否发起【需求工单审批】』，不自动发起；用户回复【发起需求审批】后由 wf_sub_11 承接发起审批）→结束；有待补充：列待补充字段请补充后重新提报。req_id全程贯穿衔接wf_sub_01。",
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
    "# 业务默认值：存量产品发布地市默认为全省，schema 支持全省口径（groupIdMessage 随之隐藏）\n"
    "BUSINESS_DEFAULTS = {\n"
    "    'releaseInfo.groupId': '全省',\n"
    "}\n"
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
    "async def main(args):\n"
    "    p = args.params\n"
    "    schema = _load(p.get('schema_json') or '')\n"
    "    elements = _load(p.get('elements_json') or '')\n"
    "    if isinstance(elements, dict) and 'elements_json' in elements:\n"
    "        inner = elements['elements_json']\n"
    "        if isinstance(inner, str):\n"
    "            inner = _load(inner)\n"
    "        if isinstance(inner, dict):\n"
    "            elements = inner\n"
    "    # ① 质量校验（validate_elements 六项校验 + E31 命中率门禁）\n"
    "    threshold = float(p.get('threshold') or 0.30)\n"
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
    "    offer = unwrap_offer(_load(p.get('offer_json') or ''))\n"
    "    template_id = schema.get('x-template', '')\n"
    "    if isinstance(offer, dict) and template_id in offer:\n"
    "        offer = offer[template_id]\n"
    "    elements_map = flatten_elements(elements)\n"
    "    source_map = relabel_source_map(elements_map, leaves, p.get('record_json') or '')\n"
    "    offer_map = flatten_offer(offer)\n"
    "    req_price_map = _collect_req_prices(p.get('record_json') or '')\n"
    "    meta, pending = {}, []\n"
    "    merged = merge_body(schema, elements_map, offer_map, meta, pending=pending, source_map=source_map, req_price_map=req_price_map)\n"
    "    reconcile_same_parameter(merged, meta, pending)\n"
    "    ret: Output = {\n"
    "        'payload': json.dumps(merged, ensure_ascii=False),\n"
    "        'meta': json.dumps(meta, ensure_ascii=False),\n"
    "        'pending_required': ','.join(pending),\n"
    "        'template': template_id,\n"
    "        'quality_gate': gate,\n"
    "        've_result': ve_result,\n"
    "        've_stats': ve_stats,\n"
    "        'status': 'pend' if pending else 'ok',\n"
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
    [code_out("record_json", 103)],
    pos=(390, 135)))
# 产品识别（LLM）：识别产品类型 → 选模板 + 生成要素提取所需上下文
s01.append(llm_node(104, "产品识别与模板选择",
    "你是产销品需求分析助手（环节2 产品识别）。基于需求字段（{elements}={elements_record}）识别产品类型并从 6 套配置模板中选定 1 套：\n"
    "模板注册表：\n"
    "- personMainPrc：个人主资费（产品类型=个人主套餐，来源5.3.1）\n"
    "- broadBandMainPrc：宽带主资费（产品类型=宽带主套餐，来源5.3.2）\n"
    "- personAddPrc：个人附加资费（产品类型=个人附加资费，来源5.3.3）\n"
    "- broadBandOptSpeedPrc：宽带加速包（产品类型=宽带附加资费，来源5.3.4.1）\n"
    "- familyBasePrc：家庭基础套餐（产品类型=家庭基础套餐，来源5.3.5.1，含融合成员）\n"
    "- familyAddPrc：家庭附加业务（产品类型=家庭附加资费，来源5.3.5.2）\n"
    "判定规则：需求字段 product_type 直接匹配模板 product_type → 锁定唯一模板；product_type 缺失时按 name/系列/内容关键词推断；无法判定默认 personMainPrc。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含两个键——\n"
    "1. template_id：六选一模板标识（字符串，如 \"personMainPrc\"）；\n"
    "2. need_summary：一段需求要素摘要（产品类型+模板+资费+资源要点，供相似检索检索词，≤2000字符，字符串）。\n"
    "严禁输出其他文字、编号列表、标签前缀（如 \"1. template_id：\"）、解释或 Markdown 代码块围栏；严禁把两个出参塞进同一段文本。",
    [inp("elements_record", "引用节点103需求字段原文", ref_block=nid(103), ref_rel="record_json")],
    [out("template_id", "选定的模板标识（personMainPrc 等六选一）"), out("need_summary", "需求要素摘要（相似检索检索词）")]))
# 相似产品检索：以需求摘要+模板检索最相似存量产品，返回同构逻辑模型报文供合并补全
s01.append(plugin_node(105, "相似产品检索", "query_similar_offer",
    "工具1（复用·兜底）：以《产品信息.txt》全部销售品为相似库，返回相似度最高的产品（含 offerInfo 同构字段，与模板同构；并附 offerModel 存量逻辑模型报文——按当前模板实例化，模板同构嵌套 key=模板字段名）；供要素合并节点按 JSONPath 对位补全",
    BASE_URL + "/api/v1/appstore/similar/offer/query",
    [inp("businessDesc", "业务需求描述（=节点104需求要素摘要）", ref_block=nid(104), ref_rel="need_summary"),
     inp("templateId", "模板标识（=节点104选定），后端按此模板返回存量逻辑模型报文 offerModel", ref_block=nid(104), ref_rel="template_id")],
    [
     ("similarOffer", "相似产品（含相似度评分、offerInfo 同构字段与 offerModel 逻辑模型报文）", "object")]))
# 获取配置模板：读已迁移模板 schema，供要素提取与合并共用
s01.append(code_node(106, "获取配置模板", CODE_GET_TEMPLATE,
    [inp("template_id", "模板标识（节点104选定）", ref_block=nid(104), ref_rel="template_id"),
     inp("template_base", "模板目录（方案/templates，随包迁移）", content=r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\方案\templates")],
    [code_out("template_id", 106), code_out("schema_json", 106), code_out("missing", 106)],
    pos=(530, 300)))
# 要素提取（LLM）：按模板 x-label 提取配置要素嵌套 JSON
s01.append(llm_node(107, "配置要素提取",
    "你是产销品需求分析助手（环节2 要素提取）。基于需求字段（{elements_record}）与选定模板 schema（{schema_json}），严格按模板 schema 提取配置要素。\n"
    "【硬性约束】\n"
    "1. 输出 JSON 的 key 必须**逐字复制模板 schema 中的字段键名**（英文键，如 baseInfo.prodPrcName、optionalInfo.printContent.containResource 末段键），层级必须与 schema 的 properties 嵌套完全一致；**严禁**自造键名（如 name/product_type/price/resources/effective_way/out_price 等一律禁止），**严禁**改写成中文键；对位靠 x-label 语义理解，但落键必须是 schema 英文键。\n"
    "2. **严禁枚举展开多值/枚举型字段**：凡 schema 中标注 x-element=多选框、enum 为集合（如 groupIdMessage 的「22个地市」）或语义为“明细列表”的字段，一律**只写一个占位/汇总值或直接省略**，绝不可逐项罗列具体地市、号码、日期区间等；这类长枚举是本环节最主要的失败源，会导致输出被截断。\n"
    "3. **必须输出完整、闭合的合法 JSON**：即使部分叶子未命中，也必须先保证 JSON 结构闭合（括号/引号成对）；宁可少写叶子，也不可让输出中途截断。\n"
    "步骤：①读 schema 顶层容器（baseInfo/releaseInfo/optionalInfo 等）与其下各级 properties；②逐个叶子取其 x-label 语义，在需求原文中查找对应信息（含同义改写，如「套餐名称/资费名称」→prodPrcName、「月费/199元/月租」→optionalInfo.printContent.prcMonthFee 与 optionalInfo.acctMonth.fixFee、「包含资源/流量通话」→containResource、「120GB/1000分钟」→billGprsCfg.theshold 与 billVoiceCfg.theshold、「套外3元/GB」→billGprsCfg.outChargeMode、「是否结转」→billGprsCfg.carryFlag、「营业厅/APP渠道」→releaseInfo.chnClassLimit）；③命中则写入该叶子路径，值为需求原文表述；未命中则不输出该键。\n"
    "【重点叶子（务必逐项核对需求原文，命中即必须输出）】optionalInfo.printContent.prcMonthFee、optionalInfo.acctMonth.fixFee（月费金额，如 199）、optionalInfo.printContent.containResource（包含资源汇总）、optionalInfo.billGprsCfg.theshold（流量数值，如 120）、optionalInfo.billGprsCfg.unit（单位，如 GB）、optionalInfo.billGprsCfg.outChargeMode（套外流量计费，如 3元/GB）、optionalInfo.billGprsCfg.carryFlag（是否结转，是/否）、optionalInfo.billVoiceCfg.theshold（语音分钟数，如 1000）。\n"
    "示例（仅示键路径与嵌套，非值）：需求「5G-A套餐199元/月，含120GB国内流量，套外3元/GB，可结转」→ {\"baseInfo\":{\"prodPrcName\":\"5G-A套餐\"},\"optionalInfo\":{\"printContent\":{\"prcMonthFee\":\"199\",\"containResource\":\"120GB国内流量\"},\"acctMonth\":{\"fixFee\":\"199\"},\"billGprsCfg\":{\"theshold\":\"120\",\"unit\":\"GB\",\"outChargeMode\":\"3元/GB\",\"carryFlag\":\"是\"}}}。\n"
    "禁止臆造值：每个键值必须能在需求原文中找到对应表述（含同义改写），需求原文未出现的模板默认项（如协议期36个月、成员9人、发布地市全省等）一律不输出该键；价格类字段只取需求原文，不得照搬相似产品；值一律沿用需求原文中文表述，单位禁止中英混写（如「0.15 元/分钟」不得写成「0.15 元/minute」）。\n"
    "输出要求：只输出一个 JSON 对象，对象仅含一个键 elements_json，其值为上述元素 JSON 的**字符串**（形如 {\"elements_json\":\"{\\\"baseInfo\\\":{...}}\"}）；严禁输出其他文字、标签前缀、说明，严禁 Markdown 代码块围栏。",
    [inp("elements_record", "引用节点103需求字段原文", ref_block=nid(103), ref_rel="record_json"),
     inp("schema_json", "引用节点106模板 schema", ref_block=nid(106), ref_rel="schema_json")],
    [out("elements_json", "配置要素（与模板嵌套结构同构的JSON，仅需求原文有值项）")], max_tokens=8192))
# 要素质量校验 + 报文合并（合一代码节点：validate_elements 六项校验 + merge_nested 递归合并）
s01.append(code_node(108, "要素校验与方案合并", CODE_EXTRACT_MERGE,
    [inp("schema_json", "模板 schema（节点106）", ref_block=nid(106), ref_rel="schema_json"),
     inp("elements_json", "配置要素（节点107提取）", ref_block=nid(107), ref_rel="elements_json"),
     inp("offer_json", "相似产品出参（节点105 similarOffer；含 offerModel 逻辑模型报文，代码内优先取 offerModel 回退 offerInfo）", ref_block=nid(105), ref_rel="similarOffer"),
     inp("threshold", "可提取命中率阈值（默认0.30）", content="0.30"),
     inp("record_json", "需求字段原文（节点103；供价格类叶子确定性兜底）", ref_block=nid(103), ref_rel="record_json")],
    [code_out("payload", 108), code_out("meta", 108), code_out("pending_required", 108), code_out("quality_gate", 108), code_out("ve_stats", 108), code_out("status", 108)],
    pos=(690, 300)))
# 方案表格渲染：按模板 schema 分节渲染业务可读表格
s01.append(code_node(109, "方案表格渲染", CODE_RENDER_TABLE,
    [inp("schema_json", "模板 schema（节点106）", ref_block=nid(106), ref_rel="schema_json"),
     inp("payload", "合并报文（节点108）", ref_block=nid(108), ref_rel="payload"),
     inp("meta", "溯源（节点108）", ref_block=nid(108), ref_rel="meta"),
     inp("similar_offer", "相似产品（节点105，来源列拼接）", ref_block=nid(105), ref_rel="similarOffer"),
     inp("title", "方案标题", content="产销品配置方案（模板轨）")],
    [code_out("table_text", 109), code_out("pending_count", 109)],
    pos=(920, 300)))
# 保存 requirement（延续 req_id，node_name=requirement 与既有 wf_sub_02~05 自查链路口径一致）
s01.append(plugin_node(110, "保存执行方案", "save_node_result",
    "节点结果存储（复用）：req_id=开始节点 req_id（环节1延续，不重新生成），node_name=requirement（执行方案），result_json=合并报文 payload（实例化逻辑模型）；供 wf_sub_02 智能配置自查链路与 wf_sub_06 门禁按 req_id+requirement 读取",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "需求单号/方案批次号（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=requirement（执行方案）", content="requirement"),
     inp("result_json", "执行方案JSON=实例化逻辑模型报文（节点108 payload）", ref_block=nid(108), ref_rel="payload"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")],
    pos=(1080, 135)))
# xsbot-panel 独立代码节点（智能配置落地前无 offer_id，传空，页面以 req_id 定位）
s01.append(panel_code_node(112, "构建页面地址面板", "01",
    panel_inputs(101, req_seq=101), biz_title="配置方案"))
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
e01 = [edge(101, 102), edge(102, 103), edge(103, 104),
       edge(104, 105), edge(105, 106), edge(106, 107),
       edge(107, 108), edge(108, 109), edge(109, 110), edge(110, 112), edge(112, 111)]
files01 = workflow(
    "产销品-需求分析", "子工作流1（模板轨）：需求分析（环节2）。req_id（环节1延续，不重新生成）→读取需求工单(requirement_report)并提取需求字段→产品识别LLM(选模板)→相似产品检索(query_similar_offer,供合并补全)→获取配置模板(读已迁移模板schema)→配置要素提取LLM(按模板x-label,嵌套同构)→要素校验与方案合并(合一代码节点:validate_elements六项校验+E31命中率门禁+merge_nested递归合并,需求优先/价格不照搬/相似品补全)→方案表格渲染(业务分节表格)→保存requirement(延续req_id,衔接wf_sub_02~05自查链路)→结束输出方案+待补充+要素质量结论。",
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
    "    cfg = _load(p.get('group_json') or '')\n"
    "    raw_status = str(p.get('status') or '')\n"
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
# 输入：product_config（save_product_config 出参完整落地配置JSON文本）
# 输出：config_summary（配置要点简介 markdown：销售品名称/月费/套内资源/套外资费/计费周期/订购退订规则）
# 说明：解析失败时回退为空串，结束节点仅少一段简介，不阻断链路。
CODE_CONFIG_SUMMARY = (
    "import json\n"
    "from typing import Any, Dict\n"
    "\n"
    "def _load(text):\n"
    "    if isinstance(text, dict):\n"
    "        return text\n"
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
    "def _cell(v):\n"
    "    return str(v) if v not in (None, '') else '—'\n"
    "\n"
    "async def main(args):\n"
    "    cfg = _load(args.params.get('product_config') or '')\n"
    "    if not cfg:\n"
    "        ret: Output = {'config_summary': ''}\n"
    "        return ret\n"
    "    plan = cfg.get('plan_json') if isinstance(cfg.get('plan_json'), dict) else {}\n"
    "    info = plan.get('optionalInfo') if isinstance(plan.get('optionalInfo'), dict) else {}\n"
    "    print_c = info.get('printContent') if isinstance(info.get('printContent'), dict) else {}\n"
    "    gprs = info.get('billGprsCfg') if isinstance(info.get('billGprsCfg'), dict) else {}\n"
    "    voice = info.get('billVoiceCfg') if isinstance(info.get('billVoiceCfg'), dict) else {}\n"
    "    base = plan.get('baseInfo') if isinstance(plan.get('baseInfo'), dict) else {}\n"
    "    in_fee = cfg.get('in_fee') if isinstance(cfg.get('in_fee'), dict) else {}\n"
    "    out_fee = cfg.get('out_fee') if isinstance(cfg.get('out_fee'), dict) else {}\n"
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
    "    ret: Output = {'config_summary': '\\n'.join(lines)}\n"
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
    [code_out("record_json", 106), code_out("offer_id", 106)],
    pos=(530, 300)))
s2f.append(plugin_node(103, "配置落地", "save_product_config",
    "工具7：执行方案JSON原文透传落地（节点106已从查询记录中提取 result_json 原文）；req_id 与 plan_json 均引用自查链路结果，方案key由后端从 plan_json 的 req_id 键提取；融合组（plan_json.offer_type=融合）时出参含 group（main_offer_id+members[]{role,offer_id}）",
    BASE_URL + "/api/v1/appstore/product/config/save",
    [inp("req_id", "执行批次号（=开始节点 req_id，与执行方案存储键同一）", ref_block=nid(101), ref_rel="req_id"),
     inp("plan_json", "执行方案JSON原文（节点106提取的 result_json）", ref_block=nid(106), ref_rel="record_json"),
     inp("confirmed", "用户确认标志true（V2.2起后端不校验，仅记录）", content="true"),
     inp("operator", "操作人（默认system）", content="system")],
    [("offer_id", "销售品ID", "string"),
     ("save_result", "四类字段写入结果", "string"), ("status", "SUCCESS/PARTIAL/FAIL", "string"),
     ("product_config", "完整落地配置JSON（含offer_id/offer_name等与plan_json原文及可选group）", "string"),
     ("script_url", "配置脚本下载地址（后端生成，GET即返回可执行脚本）", "string")]))
# 融合成员回显代码节点（V4.0 新增）：出参含 group 时生成融合成员行；无 group 路径零变化
s2f.append(code_node(107, "融合成员回显", CODE_FUSION_GROUP_ECHO,
    [inp("group_json", "落地配置JSON（节点103出参 product_config，含可选 group）", ref_block=nid(103), ref_rel="product_config"),
     inp("status", "落地总状态（节点103出参 status）", ref_block=nid(103), ref_rel="status")],
    [code_out("fusion_echo", 107), code_out("fusion_status", 107), code_out("fusion_note", 107)],
    pos=(900, 300)))
s2f.append(code_node(108, "配置要点简介", CODE_CONFIG_SUMMARY,
    [inp("product_config", "落地配置JSON（节点103出参 product_config）", ref_block=nid(103), ref_rel="product_config")],
    [code_out("config_summary", 108)],
    pos=(860, 420)))
s2f.append(plugin_node(105, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=config（智能配置），result_json=完整落地配置JSON（含 offer_id 及可选 group）；供 wf_sub_03 稽核、wf_sub_04 测试、wf_sub_06 门禁按 req_id+config 自查",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(101), ref_rel="req_id"),
     inp("node_name", "环节名=config（智能配置）", content="config"),
     inp("result_json", "环节结果JSON=完整落地配置JSON（含offer_id及可选group）", ref_block=nid(103), ref_rel="product_config"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(1060, 135)))
# xsbot-panel 独立代码节点
s2f.append(panel_code_node(109, "构建页面地址面板", "02",
    panel_inputs(101, off_seq=103, off_rel="offer_id", req_seq=101), biz_title="落地配置看板"))
s2f.append(end_node(104, "结束(配置落地完成)",
    [inp("offer_id", "销售品ID", ref_block=nid(103), ref_rel="offer_id"),
     inp("save_result", "四类字段写入结果", ref_block=nid(103), ref_rel="save_result"),
     inp("status", "落地状态", ref_block=nid(103), ref_rel="status"),
     inp("fusion_echo", "融合成员回显（V4.0，无 group 时为空）", ref_block=nid(107), ref_rel="fusion_echo"),
     inp("fusion_note", "融合组失败提示（仅融合品且有成员 PARTIAL/FAIL 时非空）", ref_block=nid(107), ref_rel="fusion_note"),
     inp("config_summary", "配置销售品要点简介（节点108渲染）", ref_block=nid(108), ref_rel="config_summary"),
     inp("script_url", "配置脚本下载地址（节点103出参）", ref_block=nid(103), ref_rel="script_url"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(109), ref_rel="panel")],
    "智能配置完成：offer_id={offer_id}\n{fusion_echo}\n四类字段写入结果：{save_result}\n状态：{status}\n\n"
    "{config_summary}\n{fusion_note}\n"
    "【配置脚本下载】下载地址：{script_url}（为空填写\"暂不可用，见智能配置结果\"）\n\n{panel}"))
files2f = workflow(
    "产销品-智能配置", "子工作流2（融合组扩展）：智能配置（配置落地）。单入参 req_id 自查链路：query_node_result 按 req_id+requirement 读取执行方案→代码节点提取 result_json 原文→save_product_config 透传落地→新增融合成员回显代码节点（出参含 group 时生成融合成员行，逐字引用 role/offer_id）；结束前存储 node_name=config（result_json 含 offer_id 及可选 group）。单商品路径零变化。", "wf_sub_02", s2f,
    [edge(101,102), edge(102,106), edge(106,103), edge(103,107), edge(107,108), edge(108,105), edge(105,109), edge(109,104)])

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
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=config 取回智能配置环节结果记录数组（list[0].result_json 为落地结果原文，内含 offer_id 及可选 group）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=config", content="config"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON（取[0].result_json为环节结果原文）", "string")],
    method="get", pos=(240, 135)))
s3f.append(code_node(207, "提取配置结果原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点206查询出参 list（记录数组JSON）", ref_block=nid(206), ref_rel="list")],
    [code_out("record_json", 207), code_out("offer_id", 207)],
    pos=(390, 135)))
s3f.append(plugin_node(202, "实时稽核", "realtime_spec_audit",
    "工具2：自研模拟实时稽核，同步返回；offer_id/config_json 取自 config 环节结果（节点207提取原文与解析的offer_id）；融合组 error_list 可含 group:<role> 类目",
    BASE_URL + "/api/v1/appstore/audit/realtime",
    [inp("offer_id", "销售品ID（节点207从落地结果解析的offer_id）", ref_block=nid(207), ref_rel="offer_id"),
     inp("config_json", "落地配置JSON（节点207提取的环节结果原文）", ref_block=nid(207), ref_rel="record_json"),
     inp("audit_scene", "稽核场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("error_list", "问题明细（融合组可含 group:<role> 类目）", "array"),
     ("audit_summary", "稽核总结", "string"), ("resultCode", "0成功/NET_ERROR/TIMEOUT", "string")]))
s3f.append(llm_node(203, "整改建议生成",
    "你是配置规格稽核结果呈现助手。基于稽核出参（error_list={error_list}，audit_summary={audit_summary}）"
    "输出**稽核明细清单**，无论是否存在问题都必须完整呈现七项固定检查项，禁止只输出\"稽核通过\"。\n"
    "稽核对象行：`> **稽核对象：销售品ID（offer_id）{offer_id}**`（逐字引用入参 offer_id，禁止省略该行）。\n"
    "七项固定检查项（顺序固定，逐项输出，不得增删）：\n"
    "- 基础信息完整性\n- 资源配置完整性\n- 套外资费完整性\n- 字段格式规范性\n- 字段枚举合法性\n"
    "- 配置项关联一致性\n- 业务规则完整性\n"
    "判定规则（七项与 error_list 分类一一对应）：pass={pass}；\n"
    "- error_list 为空或该分类无告警项 → 该项输出 ✅（禁止虚构 ✅，须逐项与 error_list 核对）；\n"
    "- 某分类存在告警项 → 该项改标 ❌ 并在该行下缩进附 error_list 对应明细（item/level/desc/suggest 逐字引用）。\n"
    "融合组维度（出参含 group 时）：在稽核对象行下追加组维度回显行 "
    "`> 组维度：主 offer_id {offer_id} + 成员 role（offer_id）/...`（逐字引用 config 出参原文 record_json 的 group，禁止增删成员）；"
    "error_list 含 `group:<role>` 类目时，对应成员所在行标 ❌ 并附明细（item 中 role 定位成员），七项结构不变。\n"
    "输出模板（严格按此渲染）：\n"
    "> **稽核对象：销售品ID（offer_id）{offer_id}**\n"
    "> **稽核结果：{通过/不通过}**\n"
    "> - 基础信息完整性：✅/❌\n> - 资源配置完整性：✅/❌\n> - 套外资费完整性：✅/❌\n"
    "> - 字段格式规范性：✅/❌\n> - 字段枚举合法性：✅/❌\n> - 配置项关联一致性：✅/❌\n"
    "> - 业务规则完整性：✅/❌\n>\n"
    "> {pass=1 且无告警项→\"未发现任何异常。\"；否则逐条列 ❌ 项明细}\n"
    "不新增稽核结论。\n"
    "输出要求：仅输出上述稽核明细清单内容（对应出参 audit_suggest），不输出其他多余文字。",
    [inp("error_list", "引用节点202问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_summary", "引用节点202稽核总结", ref_block=nid(202), ref_rel="audit_summary"),
     inp("record_json", "config 环节结果原文（含可选 group，供组维度回显）", ref_block=nid(207), ref_rel="record_json"),
     inp("offer_id", "主 offer_id（稽核对象回显）", ref_block=nid(207), ref_rel="offer_id"),
     inp("pass", "引用节点202稽核结论（1通过/0不通过）", ref_block=nid(202), ref_rel="pass")],
    [out("audit_suggest", "稽核明细清单（七项固定检查项，无条件输出）")], max_tokens=3072))
# 208 结构化封包：上游结构化出参（pass/error_list/audit_summary）+ LLM 明细清单
#   → 合成 result_json envelope（error_list 供 sub_04 节点315 CUST-008 等确定性映射）
s3f.append(code_node(208, "稽核结果结构化封包", CODE_PACK_RECORD,
    [inp("pass", "稽核结论（节点202）", ref_block=nid(202), ref_rel="pass"),
     inp("error_list", "问题明细（节点202）", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_summary", "稽核总结（节点202）", ref_block=nid(202), ref_rel="audit_summary"),
     inp("md", "稽核明细清单 markdown（节点203）", ref_block=nid(203), ref_rel="audit_suggest"),
     inp("md_key", "md 落库字段名", content="audit_suggest")],
    [code_out("record_json", 208)], pos=(780, 135)))
s3f.append(plugin_node(205, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=spec（规格稽核），result_json=结构化封包（含 error_list + audit_suggest 明细）；主流程删除后存储下沉子工作流",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(201), ref_rel="req_id"),
     inp("node_name", "环节名=spec（规格稽核）", content="spec"),
     inp("result_json", "环节结果JSON=结构化封包（error_list+audit_suggest）", ref_block=nid(208), ref_rel="record_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(900, 135)))
# xsbot-panel 独立代码节点
s3f.append(panel_code_node(209, "构建页面地址面板", "03",
    panel_inputs(201, off_seq=207, off_rel="offer_id", req_seq=201), biz_title="规格稽核"))
s3f.append(end_node(204, "结束(稽核完成)",
    [inp("pass", "稽核结论", ref_block=nid(202), ref_rel="pass"),
     inp("error_list", "问题明细", ref_block=nid(202), ref_rel="error_list"),
     inp("audit_suggest", "稽核明细清单（七项固定检查项，无条件输出）", ref_block=nid(203), ref_rel="audit_suggest"),
     inp("offer_id", "销售品ID（节点207提取，供查看面板）", ref_block=nid(207), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(209), ref_rel="panel")],
    "配置规格稽核完成：pass={pass}\n{audit_suggest}\n\n"
    "{panel}"))
files3f = workflow(
    "产销品-规格稽核", "子工作流3（融合组扩展）：规格稽核（实时）。单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取原文→realtime_spec_audit 同步返回→稽核明细呈现（V4.1 无条件输出七项固定检查项清单：通过时逐项 ✅，未通过项 ❌ 并附明细，不再只输出\"稽核通过\"；融合组 error_list 按 group:<role> 定位成员）→结构化封包（V2.5：error_list+audit_suggest 合成 envelope）；结束前存储 node_name=spec。单商品路径零变化。", "wf_sub_03", s3f,
    [edge(201,206), edge(206,207), edge(207,202), edge(202,203), edge(203,208), edge(202,208), edge(208,205), edge(205,209), edge(209,204)])

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
    [code_out("record_json", 407), code_out("offer_id", 407)],
    pos=(240, 135)))
s5f.append(plugin_node(402, "计费校验", "check_billing_rule",
    "工具8：自研模拟计费规则校验（内置叠加/互斥/负资费规则）；config_json 取自 config 环节结果；融合组 compare_list 行可含 member_role 键",
    BASE_URL + "/api/v1/appstore/billing/rules/verify",
    [inp("config_json", "落地配置JSON（节点407提取的环节结果原文）", ref_block=nid(407), ref_rel="record_json"),
     inp("check_scene", "校验场景默认all", content="all")],
    [("pass", "1通过/0不通过", "string"), ("risk_list", "风险清单", "array"),
     ("compare_list", "资费比对明细（8项，融合组行含member_role）", "array")]))
s5f.append(llm_node(403, "风险解读",
    "将资费比对明细（compare_list={compare_list}）与资费风险清单（risk_list={risk_list}）翻译为业务语言，输出风险解读：\n"
    "1）比对信息（compare_list 非空时必出）：Markdown表格，列为 项目|套餐描述（需求）|计费配置描述（系统）|比对结果；8 项比对项目名不变（套餐月租/流量赠送量/语音赠送量/短信赠送量/流量超出资费/语音超出资费/短信超出资费/商品有效期）；行内 requirement_desc 与 billing_desc 同时为空或空串时该行整体省略，仅一侧有值则照常输出；比对信息标题写「比对信息（实际行数/compare_list总数项，本销售品不涉及项已省略）：」；两侧皆空行数≥总数一半时（E27），有值行照常输出，校准结论行引用 E27 固定文案（系统侧未返回比对明细），不中断、不改变 pass 判定，禁止编造系统侧值。\n"
    "2）融合组维度（V4.0）：compare_list 行含 member_role 键时，比对表按 member_role 分组输出（组间空行分隔或按成员独立小表，8 项比对项目名不变，行数=Σ各成员有值行）；空值行省略规则逐成员内执行；E27 阈值逐成员内计算（某成员两侧皆空行数 ≥ 该成员行数一半 → 该成员小节引用 E27 固定文案），禁止跨成员加总稀释或误判。\n"
    "3）风险解读：risk_list 逐条翻译为业务语言，说明每条风险的影响与处置建议；risk_list 为空时输出「未发现叠加/互斥冲突」。\n"
    "4）校准结论行：「**校准结论：**」+ 全部比对项 result==一致 时输出「资费配置与计费规则完全一致」，存在不一致时逐条列出。\n"
    "可引用资费规则库知识作为解释依据，但不得新增风险结论。compare_list 与 risk_list 均为空时，直接输出「资费校准通过，未发现叠加/互斥冲突」。\n"
    "输出要求：仅输出以上内容（对应出参 risk_summary，融合组时按成员分组），不输出其他多余文字。",
    [inp("risk_list", "引用节点402风险清单", ref_block=nid(402), ref_rel="risk_list"),
     inp("compare_list", "引用节点402资费比对明细（8项，融合组行含member_role）", ref_block=nid(402), ref_rel="compare_list")],
    [out("risk_summary", "风险解读（融合组按 member_role 分组）")]))
# 408 结构化封包：上游结构化出参（pass/risk_list/compare_list）+ LLM 风险解读
#   → 合成 result_json envelope（compare_list 供 sub_04 节点315 BILL-001~009/CUST-004 等确定性映射）
s5f.append(code_node(408, "资费结果结构化封包", CODE_PACK_RECORD,
    [inp("pass", "校验结论（节点402）", ref_block=nid(402), ref_rel="pass"),
     inp("risk_list", "风险清单（节点402）", ref_block=nid(402), ref_rel="risk_list"),
     inp("compare_list", "资费比对明细（节点402）", ref_block=nid(402), ref_rel="compare_list"),
     inp("md", "风险解读 markdown（节点403）", ref_block=nid(403), ref_rel="risk_summary"),
     inp("md_key", "md 落库字段名", content="risk_summary")],
    [code_out("record_json", 408)], pos=(780, 135)))
s5f.append(plugin_node(405, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=fee（资费校准），result_json=结构化封包（含 compare_list/risk_list + risk_summary 解读）；主流程删除后存储下沉子工作流",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(401), ref_rel="req_id"),
     inp("node_name", "环节名=fee（资费校准）", content="fee"),
     inp("result_json", "环节结果JSON=结构化封包（compare_list+risk_summary）", ref_block=nid(408), ref_rel="record_json"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(900, 135)))
# xsbot-panel 独立代码节点
s5f.append(panel_code_node(409, "构建页面地址面板", "05",
    panel_inputs(401, off_seq=407, off_rel="offer_id", req_seq=401), biz_title="资费校准"))
s5f.append(end_node(404, "结束(资费校准完成)",
    [inp("pass", "校验结论", ref_block=nid(402), ref_rel="pass"),
     inp("risk_list", "风险清单", ref_block=nid(402), ref_rel="risk_list"),
     inp("risk_summary", "风险解读（融合组按成员分组）", ref_block=nid(403), ref_rel="risk_summary"),
     inp("offer_id", "销售品ID（节点407提取，供查看面板）", ref_block=nid(407), ref_rel="offer_id"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(409), ref_rel="panel")],
    "资费校准完成：pass={pass}\n{risk_summary}\n\n"
    "{panel}"))
files5f = workflow(
    "产销品-资费校准", "子工作流5（融合组扩展）：资费校准。单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取原文→check_billing_rule（check_scene=all）→风险解读（V4.0 融合组：比对表按 member_role 分组，E27 阈值逐成员内计算）→结构化封包（V2.5：compare_list/risk_list+risk_summary 合成 envelope）；结束前存储 node_name=fee。单商品路径零变化。", "wf_sub_05", s5f,
    [edge(401,406), edge(406,407), edge(407,402), edge(402,403), edge(403,408), edge(402,408), edge(408,405), edge(405,409), edge(409,404)])


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
    "FIXED_CASES = [\n"
    "    ('ACC-001', '销售品基础准入规则校验', 'P0', 'ACC', lambda tr, sp, fee: _scene_pass(tr, 'S_O_TC')),\n"
    "    ('ACC-002', '产品互斥规则校验', 'P0', 'ACC', lambda tr, sp, fee: _point_ok(tr, 'S_O_TC', 'P_MUTEX_REL')),\n"
    "    ('ACC-003', '产品依赖规则校验', 'P0', 'ACC', lambda tr, sp, fee: _point_ok(tr, 'S_O_TC', 'P_RELY_REL')),\n"
    "    ('ACC-004', '订购操作能力校验', 'P0', 'ACC', lambda tr, sp, fee: (\n"
    "        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(\n"
    "            _scene_pass(tr, 'S_O_TC'), _point_ok(tr, 'S_O_TC', 'P_STATUS')))),\n"
    "    ('ACC-005', '变更操作能力校验', 'P1', 'ACC', lambda tr, sp, fee: None),\n"
    "    ('ACC-006', '退订操作能力校验', 'P0', 'ACC', lambda tr, sp, fee: _scene_pass(tr, 'S_U_TC')),\n"
    "    ('ACC-007', '受理表单必填字段完整性', 'P0', 'ACC', lambda tr, sp, fee: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'),\n"
    "                         _point_ok(tr, 'S_O_TC', 'P_PAY_MODE'))\n"
    "        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_OFFER_NAME', 'P_OFFER_TYPE', 'P_PAY_MODE'))))),\n"
    "    ('ACC-008', '限购数量规则校验', 'P1', 'ACC', lambda tr, sp, fee: _point_ok(tr, 'S_O_TC', 'P_ORD_CNT')),\n"
    "    ('ACC-009', '地域受理范围校验', 'P1', 'ACC', lambda tr, sp, fee: None),\n"
    "    ('ACC-010', '受理时段生效校验', 'P1', 'ACC', lambda tr, sp, fee: _point_ok(tr, 'S_O_TC', 'P_EFF_DATE')),\n"
    "    ('ACC-011', '模拟订购接口预测试', 'P0', 'ACC', lambda tr, sp, fee: (\n"
    "        True if tr.get('orderId') and tr.get('offerInstId') else\n"
    "        (None if not any((tr.get('testScenes') or [])) else False))),\n"
    "    ('ACC-012', '模拟退订接口预测试', 'P0', 'ACC', lambda tr, sp, fee: (\n"
    "        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(\n"
    "            _scene_pass(tr, 'S_U_TC'), _point_ok(tr, 'S_U_TC', 'P_STATUS')))),\n"
    "    ('BILL-001', '基础资费金额合法性校验', 'P0', 'BILL', lambda tr, sp, fee: _compare_ok(fee, '套餐月租')),\n"
    "    ('BILL-002', '计费周期类型校验', 'P0', 'BILL', lambda tr, sp, fee: (\n"
    "        None if fee is None or not fee.get('compare_list') or not tr.get('plan_billing_cycle')\n"
    "        else str(tr.get('plan_billing_cycle')) == '自然月')),\n"
    "    ('BILL-003', '计费起算时间规则校验', 'P0', 'BILL', lambda tr, sp, fee: (\n"
    "        None if fee is None or not fee.get('compare_list') else all(\n"
    "            str(c.get('result')) == '一致' for c in fee['compare_list'] if c.get('project_name') == '套餐月租'))),\n"
    "    ('BILL-004', '资源扣减规则校验', 'P0', 'BILL', lambda tr, sp, fee: (\n"
    "        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(\n"
    "            [_compare_ok(fee, p) for p in ('流量赠送量', '语音赠送量', '短信赠送量')]))),\n"
    "    ('BILL-005', '阶梯/按量批价规则校验', 'P1', 'BILL', lambda tr, sp, fee: (\n"
    "        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(\n"
    "            [_compare_ok(fee, p) for p in ('流量超出资费', '语音超出资费', '短信超出资费')]))),\n"
    "    ('BILL-006', '优惠叠加/捆绑减免校验', 'P1', 'BILL', lambda tr, sp, fee: (\n"
    "        None if fee is None or 'risk_list' not in fee else not fee.get('risk_list'))),\n"
    "    ('BILL-007', '账单展示项配置校验', 'P1', 'BILL', lambda tr, sp, fee: None),\n"
    "    ('BILL-008', '模拟订购账单试算', 'P0', 'BILL', lambda tr, sp, fee: None),\n"
    "    ('BILL-009', '退订费用结算试算', 'P1', 'BILL', lambda tr, sp, fee: None),\n"
    "    ('BILL-010', '资费生效失效联动校验', 'P0', 'BILL', lambda tr, sp, fee: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),\n"
    "    ('CUST-001', '客服产品基础视图完整性', 'P0', 'CUST', lambda tr, sp, fee: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME') and _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE')))),\n"
    "    ('CUST-002', '客户订单查询能力校验', 'P0', 'CUST', lambda tr, sp, fee: (\n"
    "        True if tr.get('offerInstId') else\n"
    "        (None if not any((tr.get('testScenes') or [])) else False))),\n"
    "    ('CUST-003', '客服侧产品操作权限校验', 'P1', 'CUST', lambda tr, sp, fee: None),\n"
    "    ('CUST-004', '产品资费对外说明话术校验', 'P0', 'CUST', lambda tr, sp, fee: (\n"
    "        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(\n"
    "            [str(c.get('result')) == '一致' for c in fee['compare_list']]))),\n"
    "    ('CUST-005', '产品生效失效规则话术校验', 'P1', 'CUST', lambda tr, sp, fee: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))\n"
    "        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),\n"
    "    ('CUST-006', '产品退订规则话术校验', 'P1', 'CUST', lambda tr, sp, fee: _scene_pass(tr, 'S_U_TC')),\n"
    "    ('CUST-007', '产品限制规则话术校验', 'P1', 'CUST', lambda tr, sp, fee: (\n"
    "        None if None in (_point_ok(tr, 'S_O_TC', 'P_MUTEX_REL'), _point_ok(tr, 'S_O_TC', 'P_RELY_REL'),\n"
    "                         _point_ok(tr, 'S_O_TC', 'P_ORD_CNT'))\n"
    "        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_MUTEX_REL', 'P_RELY_REL', 'P_ORD_CNT'))))),\n"
    "    ('CUST-008', '对外展示信息合规校验', 'P0', 'CUST', lambda tr, sp, fee: (\n"
    "        None if sp is None or 'error_list' not in sp else not sp.get('error_list'))),\n"
    "    ('CUST-009', '客服常见问题FAQ完备性', 'P1', 'CUST', lambda tr, sp, fee: None),\n"
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
    "async def main(args):\n"
    "    p = args.params\n"
    "    tr = _load(p.get('test_result_json') or '')\n"
    "    if not isinstance(tr, dict):\n"
    "        tr = {}\n"
    "    # 受理凭证/被测名称由上游单独透传（绑定到 305 的独立出参），此处回填拦截\n"
    "    if not tr.get('offerName'):\n"
    "        tr['offerName'] = str(p.get('test_offer_name') or '')\n"
    "    if not tr.get('orderId'):\n"
    "        tr['orderId'] = str(p.get('order_id') or '')\n"
    "    if not tr.get('offerInstId'):\n"
    "        tr['offerInstId'] = str(p.get('offer_inst_id') or '')\n"
    "    sp = _load(p.get('spec_record') or '')\n"
    "    fee = _load(p.get('fee_record') or '')\n"
    "    cfg_offer_id = str(p.get('offer_id') or '')\n"
    "    plan = _load(p.get('plan_json') or '')\n"
    "    if not isinstance(plan, dict):\n"
    "        plan = {}\n"
    "    inner_plan = plan.get('plan_json')\n"
    "    if isinstance(inner_plan, dict):\n"
    "        merged = dict(plan)\n"
    "        merged.update(inner_plan)\n"
    "        plan = merged\n"
    "    rows, counts = [], {'ACC': [0, 0], 'BILL': [0, 0], 'CUST': [0, 0]}\n"
    "    defects = []\n"
    "    for case_id, name, level, dim, judge in FIXED_CASES:\n"
    "        verdict = judge(tr, sp, fee)\n"
    "        if verdict is True:\n"
    "            result = '\u2705'\n"
    "        elif verdict is False:\n"
    "            result = '\u274c'\n"
    "        else:\n"
    "            result = '\u672c\u9500\u552e\u54c1\u672a\u8986\u76d6'\n"
    "        if result in ('\u2705', '\u274c'):\n"
    "            counts[dim][0 if result == '\u2705' else 1] += 1\n"
    "            if result == '\u274c':\n"
    "                defects.append({'id': case_id, 'module': {'ACC': '\u53d7\u7406', 'BILL': '\u8ba1\u8d39', 'CUST': '\u5ba2\u670d'}[dim],\n"
    "                                'level': level, 'desc': '\u51fa\u73b0' + name + '\u672a\u901a\u8fc7\uff0c\u8bf7\u6838\u67e5\u914d\u7f6e'})\n"
    "        rows.append({'caseId': case_id, 'caseName': name, 'level': level, 'dimension': dim, 'result': result})\n"
    "    conclusion = _conclude(rows, fee)\n"
    "    p0_pass = '1' if not any(r['level'] == 'P0' and r['result'] == '\u274c' for r in rows) else '0'\n"
    "    # E26 被测一致性核对：主 offerName 一致 + 融合组 offer_group_check 成员角色集合 vs plan member_offers 角色集合一致\n"
    "    e26 = '1'\n"
    "    e26_note = ''\n"
    "    test_offer_name = str(tr.get('offerName') or '')\n"
    "    plan_name = str(plan.get('offer_name') or plan.get('套餐名称') or '')\n"
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
    "    # 场景覆盖核对（K3 第5章）：S_O_TC+S_U_TC 必选；plan 允许副卡（member 存在 ACTION 或 plan_json 副卡规则）时须含 S_ADD_CARD\n"
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
    "    ret: Output = {\n"
    "        \"cases_json\": json.dumps(rows, ensure_ascii=False),\n"
    "        \"dimension_summary\": json.dumps({d: {'pass': v[0], 'fail': v[1]} for d, v in counts.items()}, ensure_ascii=False),\n"
    "        \"overall_conclusion\": conclusion,\n"
    "        \"defect_list\": json.dumps(defects, ensure_ascii=False),\n"
    "        \"p0_pass\": p0_pass,\n"
    "        \"e26\": e26,\n"
    "        \"e26_note\": e26_note,\n"
    "        \"scene_cover\": ';'.join(scene_cover),\n"
    "    }\n"
    "    return ret"
)

# ---------------- CODE_DOWNLOAD_TEST_REPORT：测试报告下载（阶段1.2） ----------------
# 下载地址=文档化正式端点 GET /api/v1/appstore/test/offer/report?global_id={globalId}，
# 由代码节点按 global_id 确定性组装（不伪造 example.invalid 假链接）；随后做一次真实探活：
# 200 且返回体非空置 backend_pending=0，否则置 1 并在 note 给出引导（download_url 仍保留
# 正确地址供后端就绪后直接下载）。
CODE_DOWNLOAD_TEST_REPORT = (
    "import urllib.request, urllib.parse\n"
    "from typing import Any, Dict\n"
    "\n"
    "REPORT_URL = 'BASE_URL/api/v1/appstore/test/offer/report'\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    global_id = str(p.get('global_id') or p.get('globalId') or '').strip()\n"
    "    if not global_id:\n"
    "        return {\n"
    "            'backend_pending': '1',\n"
    "            'download_url': '',\n"
    "            'note': '未获取到测试流水号，无法生成报告下载地址；报告正文见上方输出，可复制保存',\n"
    "        }\n"
    "    url = REPORT_URL + '?global_id=' + urllib.parse.quote(global_id)\n"
    "    backend_ok = False\n"
    "    try:\n"
    "        req = urllib.request.Request(url)\n"
    "        with urllib.request.urlopen(req, timeout=20) as resp:\n"
    "            backend_ok = resp.status == 200 and bool(resp.read())\n"
    "    except Exception:\n"
    "        backend_ok = False\n"
    "    if backend_ok:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0',\n"
    "            'download_url': url,\n"
    "            'note': '正式版测试报告已生成，可点击链接下载',\n"
    "        }\n"
    "    else:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'download_url': url,\n"
    "            'note': '报告下载端点暂不可达或报告未生成，可复制上方报告正文保存；后端就绪后点击链接下载正式版报告',\n"
    "        }\n"
    "    return ret"
)
CODE_DOWNLOAD_TEST_REPORT = CODE_DOWNLOAD_TEST_REPORT.replace("BASE_URL", BASE_URL)

# ============================================================
# wf_sub_04 自动测试（阶段4 精简版：正式版 9 章节报告）
#   流程：开始(req_id) → 读取 config 环节结果(309) → 提取原文+offer_id(310)
#     → 发起测试(302 offer_test 取 globalId) → 轮询进度(304 CODE_POLL_PROGRESS)
#     → 查询结果(305 get_test_result 取 testScenes/orderId/offerInstId/offerName)
#     → 自查 spec(317/318 取 error_list)、自查 fee(319/320 取 compare_list/risk_list)
#     → CODE_MAP_FIXED_CASES(315 确定性 31 条固定用例 + 结论 + 缺陷 + 场景覆盖 + E26 核对)
#     → LLM(306 按 K3 模板 V2.0 九章节渲染《销售品自动化测试报告》正式版，受理验证独立成节)
#     → 存储(308 node_name=test) → 下载测试报告(316) → 结束(307)
#   注：V2.5 起 sub_03/sub_05 存储结构化 envelope（error_list/compare_list），
#   故此处恢复 317~320 自检读取（原判定为冗余已删除，现重新接线），
#   315 的 spec_record/fee_record 入参据此填充，计费/客服分项由「未覆盖」升级为确定性判定。
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
    [code_out("record_json", 310), code_out("offer_id", 310)],
    pos=(390, 135)))
# 发起测试
s4f.append(plugin_node(302, "发起测试", "offer_test",
    "工具3：自研模拟测试发起，返回模拟测试流水 globalId；offerId 取自 config 环节结果（节点310解析的offer_id）",
    BASE_URL + "/api/v1/appstore/test/offer/start",
    [inp("offerId", "销售品ID（节点310从落地结果解析的offer_id）", ref_block=nid(310), ref_rel="offer_id")],
    [
     ("globalId", "测试流水号", "string")]))
# 轮询测试进度（代码节点内嵌轮询，5s 间隔 / 30 分钟超时）
s4f.append(code_node(304, "轮询测试进度", CODE_POLL_PROGRESS,
    [inp("globalId", "测试流水号（节点302出参）", ref_block=nid(302), ref_rel="globalId")],
    [code_out("done", 304), code_out("failed", 304), code_out("failIndex", 304), code_out("fail_reason", 304)],
    pos=(690, 300)))
# 查询测试结果（done=true 后取 testScenes/orderId/offerInstId/offerName）
s4f.append(plugin_node(305, "查询测试结果", "get_test_result",
    "工具6：done=true 后调用一次；返回逐场景结果 testScenes（含测点明细 testCasePointResults）、受理凭证 orderId/offerInstId、被测销售品 offerName；presetValue 取自《产品信息.txt》该销售品规则值",
    BASE_URL + "/api/v1/appstore/test/offer/result",
    [inp("globalId", "测试流水号（节点302出参）", ref_block=nid(302), ref_rel="globalId")],
    [
     ("testRequestId", "测试请求ID", "string"), ("offerName", "被测销售品名称", "string"),
     ("orderId", "受理订单号", "string"), ("offerInstId", "销售品实例ID", "string"),
     ("testScenes", "逐场景结果含测点明细（照列出参）", "array")]))
# 自检 spec（node_name=spec → 结构化 envelope：error_list/audit_suggest）与
# 自检 fee（node_name=fee → 结构化 envelope：compare_list/risk_list），
# 供 315 固定用例的计费(BILL-001~009)/客服(CUST-004/008)分项确定性填充。
s4f.append(plugin_node(317, "自查稽核结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=spec 取回规格稽核环节结果（result_json=结构化 envelope，含 error_list）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=spec（规格稽核）", content="spec"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 430)))
s4f.append(code_node(318, "提取稽核原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点317查询出参 list", ref_block=nid(317), ref_rel="list")],
    [code_out("record_json", 318)],
    pos=(390, 430)))
s4f.append(plugin_node(319, "自查资费结果", "query_node_result",
    "节点结果查询（复用）：req_id=开始节点 req_id，node_name=fee 取回资费校准环节结果（result_json=结构化 envelope，含 compare_list/risk_list）",
    BASE_URL + "/api/v1/appstore/result/query",
    [inp("req_id", "存储键（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=fee（资费校准）", content="fee"),
     inp("latest_only", "1=只返回最新一条（默认）", content="1")],
    [ ("list", "记录数组JSON", "string")],
    method="get", pos=(240, 555)))
s4f.append(code_node(320, "提取资费原文", CODE_EXTRACT_RECORD,
    [inp("query_list", "引用节点319查询出参 list", ref_block=nid(319), ref_rel="list")],
    [code_out("record_json", 320)],
    pos=(390, 555)))
# CODE_MAP_FIXED_CASES：确定性构建 31 条固定用例表 + 结论 + 缺陷 + 场景覆盖 + E26 核对
s4f.append(code_node(315, "固定用例映射", CODE_MAP_FIXED_CASES,
    [inp("test_result_json", "get_test_result 逐场景结果（节点305 testScenes，含测点明细）", ref_block=nid(305), ref_rel="testScenes"),
     inp("test_offer_name", "被测销售品名称（节点305 offerName，E26 一致性核对）", ref_block=nid(305), ref_rel="offerName"),
     inp("order_id", "受理订单号（节点305 orderId，ACC-011 受理凭证）", ref_block=nid(305), ref_rel="orderId"),
     inp("offer_inst_id", "销售品实例ID（节点305 offerInstId，ACC-011/CUST-002 受理凭证）", ref_block=nid(305), ref_rel="offerInstId"),
     inp("offer_id", "被测销售品ID（节点310解析）", ref_block=nid(310), ref_rel="offer_id"),
     inp("plan_json", "config 环节结果原文（节点310提取，含 plan_json/offer_name/member_offers，E26 与场景覆盖依据）", ref_block=nid(310), ref_rel="record_json"),
     inp("spec_record", "稽核环节结构化原文（节点318提取，含 error_list，供 CUST-008 等映射）", ref_block=nid(318), ref_rel="record_json"),
     inp("fee_record", "资费环节结构化原文（节点320提取，含 compare_list/risk_list，供 BILL-001~009/CUST-004 映射）", ref_block=nid(320), ref_rel="record_json")],
    [code_out("cases_json", 315), code_out("dimension_summary", 315), code_out("overall_conclusion", 315), code_out("defect_list", 315), code_out("p0_pass", 315), code_out("e26", 315), code_out("e26_note", 315), code_out("scene_cover", 315)],
    pos=(900, 300)))
# LLM（306）：按 K3 模板 V2.0 九章节渲染《销售品自动化测试报告》正式版 + 受理验证独立成节
s4f.append(llm_node(306, "测试报告生成(正式版9章节)",
    "你是产销品自动测试报告生成助手。基于逐场景测试结果（testScenes={testScenes}）、31条固定用例确定性映射结果（cases_json={cases_json}，dimension_summary={dimension_summary}，overall_conclusion={overall_conclusion}，defect_list={defect_list}，scene_cover={scene_cover}，e26={e26}）与受理凭证（orderId={orderId}，offerInstId={offerInstId}），按 K3 模板 V2.0 生成《销售品自动化测试报告》正式版，9 章节结构：\n"
    "一、报告概述（目的/范围/依据/等级定义 P0拦截/P1警告/P2提示）；\n"
    "二、基础信息（12 项：报告编号 TEST-REP-当日-序号/测试任务ID {testRequestId}/被测销售品名称 {offerName}/销售品编码 {offerId}/产品类型（取配置 plan_json 套餐属性）/所属业务域 产销品域/所属部门 产商品中心CRM_POS/生效时间（配置套餐生效规则摘要）/测试方式 全自动智能测试/测试时间 报告生成时间/关联加载方案 {req_id}/测试流水号 {globalId}）；\n"
    "三、测试总体结论（**唯一数据源=dimension_summary**：总校验用例数=dimension_summary 各维度 pass+fail 合计；通过=各维度 pass 合计；阻断=各维度 fail 合计；通过率=通过/总数；整体上线结论={overall_conclusion}。**禁止改用 testScenes 的场景 testCaseCount/successTestCaseCount 合计作为总体结论**——testScenes 仅为测试引擎场景级明细，不等于 31 条固定用例判定结果，两处口径不同不得混用；\n"
    "四、分项测试结果：4.1 受理验证（用例ID ACC-001~012，逐行引用 cases_json 中 ACC 行 caseName/level/result 原值，未覆盖标'本销售品未覆盖'，不判❌不计入阻断；受理凭证 orderId={orderId}、offerInstId={offerInstId}，为空按E14标注'未获取到受理凭证，需人工核实'）；4.2 计费验证（用例ID BILL-001~010，逐行引用 cases_json BILL 行）；4.3 客服验证（用例ID CUST-001~009，逐行引用 cases_json CUST 行）。**章节标题与用例段必须严格对应**：4.1 只放 ACC、4.2 只放 BILL、4.3 只放 CUST，禁止错位（如把 ACC 行写进计费小节）；\n"
    "五、缺陷问题明细清单（引用 defect_list，无则写'无'）；\n"
    "六、业务风险汇总（引用 defect_list 中 P1 ❌ 项；无警告级风险固定输出'未发现警告级风险。'）；\n"
    "七、整改修复建议（无阻断/警告问题固定输出'无需整改'；有则逐条给出可落地整改建议）；\n"
    "八、最终测试结论与审批建议（三选一={overall_conclusion}，判定规则见 K3 规范第6章；e26=0 时结论须从严标注并提示人工核实，不输出通过性明细）；\n"
    "九、版本说明（V1.0）。\n"
    "严格遵守：31 条固定用例清单禁止增删改；用例结果仅依据 cases_json 判定；只基于输入数据生成，禁止虚构测点/结论/统计值；**三章统计与四章逐行结论必须自洽**（四章 ✅ 行数须等于三章通过数、❌ 行数须等于三章阻断数），出现矛盾时以 cases_json 为准重新生成；记录 e26 不一致时按 E26 中断口径输出（不输出通过性明细，标注被测一致性需人工核实）。\n"
    "输出要求：仅输出报告正文（对应出参 test_report），其中四章分项内**受理验证独立成节**（标题'环节7/9·受理验证'，逐字引用 orderId/offerInstId 与逐受理场景 S_O_TC/S_ADD_CARD/S_U_TC 结论、关键测点比对，融合品含 S_GROUP_BIND/S_ADDON_SUB）；不输出其他多余文字。",
    [inp("testScenes", "引用节点305逐场景结果", ref_block=nid(305), ref_rel="testScenes"),
     inp("testRequestId", "测试请求ID（节点305）", ref_block=nid(305), ref_rel="testRequestId"),
     inp("offerName", "被测销售品名称（节点305）", ref_block=nid(305), ref_rel="offerName"),
     inp("orderId", "受理订单号（节点305）", ref_block=nid(305), ref_rel="orderId"),
     inp("offerInstId", "销售品实例ID（节点305）", ref_block=nid(305), ref_rel="offerInstId"),
     inp("globalId", "测试流水号（节点302）", ref_block=nid(302), ref_rel="globalId"),
     inp("req_id", "执行批次号（开始节点）", ref_block=nid(301), ref_rel="req_id"),
     inp("offerId", "销售品ID（节点310）", ref_block=nid(310), ref_rel="offer_id"),
     inp("cases_json", "31条固定用例（节点315）", ref_block=nid(315), ref_rel="cases_json"),
     inp("dimension_summary", "三维度统计（节点315）", ref_block=nid(315), ref_rel="dimension_summary"),
     inp("overall_conclusion", "整体结论（节点315）", ref_block=nid(315), ref_rel="overall_conclusion"),
     inp("defect_list", "缺陷清单（节点315）", ref_block=nid(315), ref_rel="defect_list"),
     inp("scene_cover", "场景覆盖核对（节点315）", ref_block=nid(315), ref_rel="scene_cover"),
     inp("e26", "被测一致性（节点315）", ref_block=nid(315), ref_rel="e26")],
    [out("test_report", "《销售品自动化测试报告》正式版 9 章节正文（含受理验证独立成节）")],
    pos=(1140, 300)))
# 存储 test 环节结果（node_name=test，衔接 wf_sub_06 四环节门禁）
s4f.append(plugin_node(308, "环节结果存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=test（自动测试），result_json=正式版测试报告；主流程删除后存储下沉子工作流，供 wf_sub_06 审批门禁四环节自查与程序C 归档",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(301), ref_rel="req_id"),
     inp("node_name", "环节名=test（自动测试）", content="test"),
     inp("result_json", "环节结果JSON=正式版测试报告", ref_block=nid(306), ref_rel="test_report"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")],
    pos=(1380, 135)))
# 下载测试报告（阶段1.2）：按测试流水号组装正式下载端点地址，探活失败回退为下载引导
s4f.append(code_node(316, "测试报告下载", CODE_DOWNLOAD_TEST_REPORT,
    [inp("global_id", "测试流水号（节点302出参，报告下载端点检索键）", ref_block=nid(302), ref_rel="globalId")],
    [code_out("download_url", 316), code_out("note", 316)],
    pos=(1520, 300)))
# xsbot-panel 独立代码节点
s4f.append(panel_code_node(321, "构建页面地址面板", "04",
    panel_inputs(301, off_seq=310, off_rel="offer_id", req_seq=301), biz_title="测试报告"))
s4f.append(end_node(307, "结束(测试完成)",
    [inp("offerId", "被测销售品ID（供面板）", ref_block=nid(310), ref_rel="offer_id"),
     inp("test_report", "正式版测试报告", ref_block=nid(306), ref_rel="test_report"),
     inp("overall_conclusion", "整体上线结论", ref_block=nid(315), ref_rel="overall_conclusion"),
     inp("globalId", "测试流水号", ref_block=nid(302), ref_rel="globalId"),
     inp("download_url", "报告下载地址（节点316）", ref_block=nid(316), ref_rel="download_url"),
     inp("dl_note", "下载说明（节点316）", ref_block=nid(316), ref_rel="note"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(321), ref_rel="panel")],
    "《销售品自动化测试报告》（正式版 9 章节）已生成（被测 offer_id={offerId}，测试流水号：{globalId}）\n整体上线结论：{overall_conclusion}\n\n{test_report}\n\n"
    "【报告下载】下载地址：{download_url}（为空填写\"暂不可用，见上方报告正文\"）（{dl_note}）\n\n"
    "【下一步】可发送\"上线审批\"提交审批流，将按该测试报告与四环节结果发起上线审批。\n\n{panel}"))
files4f = workflow(
    "产销品-自动测试", "子工作流4（阶段4 精简版）：自动测试（含受理验证独立成节）。单入参 req_id 自查链路：query_node_result 按 req_id+config 读取→代码节点提取原文+offer_id→offer_test 发起→轮询进度(CODE_POLL_PROGRESS)→get_test_result（testScenes/orderId/offerInstId/offerName）→自查 spec/fee(317~320 取 error_list/compare_list)→CODE_MAP_FIXED_CASES 确定性构建 31 条固定用例表+整体结论+缺陷清单+场景覆盖核对+E26 被测一致性核对→LLM 按 K3 模板 V2.0 九章节渲染《销售品自动化测试报告》正式版（受理验证独立成节环节7/9）→存储 node_name=test→下载测试报告(CODE_DOWNLOAD_TEST_REPORT,端点不可达回退下载引导)→结束。", "wf_sub_04", s4f,
    [edge(301,309), edge(309,310), edge(310,302), edge(302,304),
     edge(304,305), edge(305,315), edge(301,317), edge(317,318), edge(318,315),
     edge(301,319), edge(319,320), edge(320,315),
     edge(315,306), edge(306,308),
     edge(308,316), edge(316,321), edge(321,307)])

# ============================================================
# 阶段 5：审批/监控/存量（wf_sub_06/07/08 重写 + 新增 09/10）
# ============================================================

# ---------------- CODE_APPROVAL_POLL：审批状态轮询（V13.0 审批通过即自动上线） ----------------
# 依据 flow-C 步骤7：轮询 approval_status（模拟服务审批提交 10s 后自动流转为"通过"），
# 判定 status=通过 即自动衔接环节9 监控运维；否则待审/驳回 → 环节8 收尾块。
CODE_APPROVAL_POLL = (
    "import json, time, asyncio\n"
    "import urllib.request, urllib.parse\n"
    "from typing import Any, Dict\n"
    "\n"
    "STATUS_URL = 'BASE_URL/api/v1/appstore/approval/status'\n"
    "MAX_RETRY = 12\n"
    "INTERVAL = 2\n"
    "\n"
    "def _fetch(approval_id, offer_id):\n"
    "    params = {}\n"
    "    if approval_id:\n"
    "        params['approval_id'] = approval_id\n"
    "    if offer_id:\n"
    "        params['offer_id'] = offer_id\n"
    "    url = STATUS_URL + '?' + urllib.parse.urlencode(params)\n"
    "    with urllib.request.urlopen(url, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "def _norm_status(info):\n"
    "    # 对齐后端口径：终态 通过/驳回；处理中 审批中/待审/待审核/进行中/approved\n"
    "    st = str(info.get('status') or '').strip()\n"
    "    if st in ('通过', '已通过'):\n"
    "        return '通过'\n"
    "    if st in ('驳回', '已驳回', '拒绝'):\n"
    "        return '驳回'\n"
    "    if str(info.get('approved') or '').lower() in ('true', 'yes', '1'):\n"
    "        return '通过'\n"
    "    if str(info.get('rejected') or '').lower() in ('true', 'yes', '1'):\n"
    "        return '驳回'\n"
    "    if st == '' or st in ('审批中', '待审', '待审核', '进行中', 'approved', 'pending', 'auditing'):\n"
    "        return '审批中'\n"
    "    return st\n"
    "\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    approval_id = str(p.get('approval_id') or '')\n"
    "    offer_id = str(p.get('offer_id') or '')\n"
    "    out_status = '审批中'\n"
    "    info = {}\n"
    "    for i in range(MAX_RETRY):\n"
    "        try:\n"
    "            info = _fetch(approval_id, offer_id)\n"
    "        except Exception:\n"
    "            await asyncio.sleep(INTERVAL)\n"
    "            continue\n"
    "        out_status = _norm_status(info)\n"
    "        if out_status in ('通过', '驳回'):\n"
    "            break\n"
    "        time.sleep(INTERVAL)\n"
    "    ret: Output = {\n"
    "        'status': out_status,\n"
    "        'approval_id': str(info.get('approval_id') or approval_id),\n"
    "        'approval_type': str(info.get('approval_type') or 'launch'),\n"
    "        'current_node': str(info.get('current_node') or ''),\n"
    "        'approver': str(info.get('approver') or ''),\n"
    "        'opinion': str(info.get('opinion') or ''),\n"
    "        'update_time': str(info.get('update_time') or ''),\n"
    "        'approval_matrix': json.dumps(info.get('approval_matrix') or [], ensure_ascii=False),\n"
    "    }\n"
    "    return ret"
)
CODE_APPROVAL_POLL = CODE_APPROVAL_POLL.replace("BASE_URL", BASE_URL)

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
    "async def main(args):\n"
    "    p = args.params\n"
    "    offer_id = str(p.get('offer_id') or '').strip()\n"
    "    name = str(p.get('name') or '').strip()\n"
    "    keyword = str(p.get('keyword') or '').strip()\n"
    "    matched = []\n"
    "    for rec in CATALOG:\n"
    "        oid, nm, ptype, series, tier, tmpl, members, status, dup = rec\n"
    "        target = dup if (status == 'dup' and dup) else oid\n"
    "        if offer_id and offer_id == target:\n"
    "            matched.append({'offer_id': target, 'name': nm, 'product_type': ptype,\n"
    "                            'biz_series': series, 'tier': tier, 'template': tmpl,\n"
    "                            'members': members, 'status': status})\n"
    "            continue\n"
    "        if name and (name in nm or nm in name):\n"
    "            matched.append({'offer_id': target, 'name': nm, 'product_type': ptype,\n"
    "                            'biz_series': series, 'tier': tier, 'template': tmpl,\n"
    "                            'members': members, 'status': status})\n"
    "            continue\n"
    "        if keyword and keyword in nm:\n"
    "            matched.append({'offer_id': target, 'name': nm, 'product_type': ptype,\n"
    "                            'biz_series': series, 'tier': tier, 'template': tmpl,\n"
    "                            'members': members, 'status': status})\n"
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
# 经 appstore 网关以 BASE_URL/api/v1/appstore/* 暴露。此处代码节点改为 HTTP 调用对应端点（urllib，对齐 CODE_APPROVAL_POLL 模式），
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

# ---------------- CODE_DOWNLOAD_LAUNCH_SCRIPT：上线/配置脚本下载（阶段1.2） ----------------
# 审批通过自动上线后提供配置/加载脚本下载；后端暴露下载端点后返回地址，不可达回退为引导文本。
# 对齐 CODE_DOWNLOAD_TEST_REPORT 的 urllib POST + 优雅回退模式。
CODE_DOWNLOAD_LAUNCH_SCRIPT = (
    "import json, urllib.request\n"
    "from typing import Any, Dict\n"
    "\n"
    "DL_URL = 'BASE_URL/api/v1/appstore/script/download'\n"
    "\n"
    "def _fetch(offer_id, approval_id):\n"
    "    body = json.dumps({'offer_id': offer_id, 'approval_id': approval_id, 'kind': 'launch_script'}).encode('utf-8')\n"
    "    req = urllib.request.Request(DL_URL, data=body, method='POST',\n"
    "                                 headers={'Content-Type': 'application/json'})\n"
    "    with urllib.request.urlopen(req, timeout=30) as resp:\n"
    "        return json.loads(resp.read().decode('utf-8'))\n"
    "\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    offer_id = str(p.get('offer_id') or '').strip()\n"
    "    approval_id = str(p.get('approval_id') or '').strip()\n"
    "    try:\n"
    "        info = _fetch(offer_id, approval_id)\n"
    "    except Exception:\n"
    "        info = None\n"
    "    if not info or not (info.get('download_url') or info.get('url')):\n"
    "        ret: Output = {\n"
    "            'backend_pending': '1',\n"
    "            'download_url': '',\n"
    "            'note': '配置/上线脚本下载端点暂不可达，可联系产商品中心获取加载脚本；后端就绪后可直接下载',\n"
    "        }\n"
    "    else:\n"
    "        ret: Output = {\n"
    "            'backend_pending': '0',\n"
    "            'download_url': str(info.get('download_url') or info.get('url') or ''),\n"
    "            'note': str(info.get('message') or '配置/上线脚本已生成，可点击链接下载'),\n"
    "        }\n"
    "    return ret"
)
CODE_DOWNLOAD_LAUNCH_SCRIPT = CODE_DOWNLOAD_LAUNCH_SCRIPT.replace("BASE_URL", BASE_URL)

# ============================================================
# wf_sub_06 上线审批（阶段5 重写：双轨 approval-type=launch + V13.0 审批通过自动上线）
#   开始(req_id) → 串行自查5类环节(config/spec/fee/test/requirement，各配提取)
#   → CODE_SUMMARY_APPROVAL 合成结构化汇总 → LLM 生成《上线审批建议》(环节8标题头+看板+风险+整体结论)
#   → 存储 report → submit_release_approval 推送(launch) → CODE_APPROVAL_POLL 轮询审批状态
#   → selector 按 status=通过 分流：通过分支 → LLM 监控运维方案(环节9标题头+xsbot-panel看板) → 自动上线结束；
#     待审/驳回分支 → 环节8 收尾块结束（引导【查询审批进度】核验，禁止再要求【确认上线】）
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
    [code_out("record_json", 603), code_out("offer_id", 603)],
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
    [code_out("record_json", 605)],
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
    [code_out("record_json", 607)],
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
    [code_out("record_json", 609)],
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
    [code_out("record_json", 611)],
    pos=(390, 635)))
s6f.append(code_node(612, "合成结构化汇总", CODE_SUMMARY_APPROVAL,
    [inp("config_result", "配置环节原文（节点603提取）", ref_block=nid(603), ref_rel="record_json"),
     inp("spec_result", "稽核环节原文（节点605提取）", ref_block=nid(605), ref_rel="record_json"),
     inp("fee_result", "资费环节原文（节点607提取）", ref_block=nid(607), ref_rel="record_json"),
     inp("test_result", "测试环节原文（节点609提取）", ref_block=nid(609), ref_rel="record_json"),
     inp("requirement_result", "执行方案原文（节点611提取）", ref_block=nid(611), ref_rel="record_json")],
    [code_out("summary_json", 612), code_out("offer_id", 612), code_out("fusion_verification", 612)],
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
# 614 报告存储 node=report
s6f.append(plugin_node(614, "报告存储", "save_node_result",
    "环节结果存储（复用）：req_id=入参 req_id，node_name=report（上线审批建议），result_json=审批建议正文；正式版测试报告(节点608自查 test 原文)按原样归档不在此改写",
    BASE_URL + "/api/v1/appstore/result/save",
    [inp("req_id", "执行批次标识（=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("node_name", "环节名=report（上线审批建议）", content="report"),
     inp("result_json", "环节结果JSON=上线审批建议", ref_block=nid(613), ref_rel="approval_suggest"),
     inp("status", "本环节状态=ok", content="ok")],
    [ ("record_id", "存储记录ID", "string")], pos=(840, 385)))
# 615 审批推送 approval-type=launch
s6f.append(plugin_node(615, "审批推送", "submit_release_approval",
    "工具9：自研模拟审批推送（V9.1 双轨，此处 approval-type=launch 上线审批）；插件层硬门禁：approve_confirmed==true 且存储中存在该 req_id 的四环节结果（config/spec/fee/test）；幂等：同offer_id返回原approval_id；offer_id=节点612从config提取，report_url=节点613审批建议",
    BASE_URL + "/api/v1/appstore/approval/submit",
    [inp("offer_id", "销售品ID（节点612从config提取）", ref_block=nid(612), ref_rel="offer_id"),
     inp("report_url", "上线审批建议（节点613正文）", ref_block=nid(613), ref_rel="approval_suggest"),
     inp("req_id", "执行批次号（四环节结果门禁校验依据，=开始节点 req_id）", ref_block=nid(601), ref_rel="req_id"),
     inp("approve_confirmed", "审批发起确认标志true", content="true"),
     inp("approval_flow", "审批流默认standard", content="standard"),
     inp("approval_type", "审批类型=launch（上线审批）", content="launch")],
    [("approval_id", "审批单号", "string"), ("status", "提交状态", "string"),
     ("approval_type", "审批类型", "string")], pos=(990, 385)))
# 616 轮询审批状态（V13.0 审批通过即自动上线，无需二次确认）
s6f.append(code_node(616, "轮询审批状态", CODE_APPROVAL_POLL,
    [inp("approval_id", "审批单号（节点615）", ref_block=nid(615), ref_rel="approval_id"),
     inp("offer_id", "销售品ID（节点612）", ref_block=nid(612), ref_rel="offer_id")],
    [code_out("status", 616), code_out("approval_id", 616), code_out("approval_type", 616), code_out("current_node", 616), code_out("approver", 616), code_out("opinion", 616), code_out("update_time", 616), code_out("approval_matrix", 616)],
    pos=(1140, 385)))
# 617 分流：status=通过 → 自动衔接环节9；否则 → 环节8 收尾块
s6f.append(selector_node2(617, "审批状态分流",
    [dep_node(616, "轮询审批状态", ["status"])],
    [(-1, [cond_item(cond_ref(616, "status", "轮询审批状态"), 1, cond_str("通过"))])]))
# 618 LLM 监控运维方案（环节9 标题头，V13.0 审批通过自动上线后输出；面板由代码节点 622 单独构建）
s6f.append(llm_node(618, "监控运维方案生成",
    "你是产销品监控运维智能体。上线审批（approval-type=launch，审批单号 {approval_id}）已通过，销售品已自动上线，请按 flow-D V13.0「审批通过自动上线·监控运维方案」固定模板输出（环节9 标题头为强制，监控阈值/推送时间为平台标准口径禁止改写）：\n"
    "【环节9/9·监控运维】✅ 执行成功\n\n"
    "**满足条件的套餐监控运维方案已生成**\n\n"
    "**一、销售情况监控**\n- 订购量、新增量、退订量、订购成功率\n\n"
    "**二、受理运行监控**\n- 受理成功率、订购失败率、变更失败率、退订失败率\n\n"
    "**三、推送规则**\n- **定时推送：** 每日09:00推送前一日销售及受理情况；每周一09:00推送近7日趋势；每月1日09:00推送上月运营报告。\n- **异常推送：** 订购成功率低于95%、受理成功率低于95%、失败率超过5%、核心指标波动超过30%时立即推送。\n\n"
     "**当前状态：** 监控指标已配置，定时推送已配置，异常推送已配置。\n\n"
     "**产品已成功上线，运营视图已开启。**\n\n"
     "> **建议处理：** 销售品已成功上线，建议查询运行监控确认上线后表现（可回复【查询监控】查看运行情况）\n"
     "监控运维方案固定模板禁止改写阈值。\n"
     "输出要求：仅输出监控运维方案正文（对应出参 monitor_plan），不输出其他多余文字。",
    [inp("approval_id", "审批单号（节点615）", ref_block=nid(615), ref_rel="approval_id")],
    [out("monitor_plan", "监控运维方案正文（环节9标题头）")], pos=(1590, 260)))
# 下载配置/上线脚本（阶段1.2）：审批通过自动上线后提供，不可达回退为引导
s6f.append(code_node(621, "配置/上线脚本下载", CODE_DOWNLOAD_LAUNCH_SCRIPT,
    [inp("offer_id", "销售品ID（节点612）", ref_block=nid(612), ref_rel="offer_id"),
     inp("approval_id", "审批单号（节点615）", ref_block=nid(615), ref_rel="approval_id")],
    [code_out("backend_pending", 621), code_out("download_url", 621), code_out("note", 621)],
    pos=(1740, 385)))
# xsbot-panel 独立代码节点（审批通过-自动上线分支；offer_name 沿用 offer_id）
s6f.append(panel_code_node(622, "构建页面地址面板(自动上线)", "06",
    panel_inputs(601, off_seq=612, off_rel="offer_id", name_seq=612, name_rel="offer_id", req_seq=601),
    biz_title="单品运营看板"))
s6f.append(end_node(619, "结束(审批通过-自动上线)",
    [inp("approval_id", "审批单号", ref_block=nid(615), ref_rel="approval_id"),
     inp("status", "审批状态", ref_block=nid(616), ref_rel="status"),
     inp("monitor_plan", "监控运维方案", ref_block=nid(618), ref_rel="monitor_plan"),
     inp("download_url", "脚本下载地址（节点621）", ref_block=nid(621), ref_rel="download_url"),
     inp("dl_note", "下载说明（节点621）", ref_block=nid(621), ref_rel="note"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(622), ref_rel="panel")],
    "审批已通过（approval_id={approval_id}，status={status}），销售品已自动上线；自动衔接环节9 监控运维：\n\n{monitor_plan}\n\n"
    "【配置/上线脚本下载】地址 {download_url}（为空填写\"暂不可用（后端下载端点未就绪），见监控运维方案\"）（{dl_note}）\n\n{panel}"))
# xsbot-panel 独立代码节点（审批轮询中分支；offer_name 沿用 offer_id）
s6f.append(panel_code_node(623, "构建页面地址面板(审批轮询中)", "06",
    panel_inputs(601, off_seq=612, off_rel="offer_id", name_seq=612, name_rel="offer_id", req_seq=601),
    biz_title="上线审批看板"))
s6f.append(end_node(620, "结束(审批轮询中)",
    [inp("approval_id", "审批单号", ref_block=nid(615), ref_rel="approval_id"),
     inp("status", "审批状态", ref_block=nid(616), ref_rel="status"),
     inp("current_node", "当前审批环节", ref_block=nid(616), ref_rel="current_node"),
     inp("approver", "当前审批人", ref_block=nid(616), ref_rel="approver"),
     inp("opinion", "审批意见", ref_block=nid(616), ref_rel="opinion"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(623), ref_rel="panel")],
    "《上线审批建议》已提交审批：\n- 审批单号：{approval_id}，当前状态：{status}（当前环节 {current_node}，审批人 {approver}，最近意见：{opinion}）\n"
    "审批通过后将自动上线并衔接监控运维方案输出。\n\n"
    "> **建议处理：** 上线审批已提交并轮询中，审批通过后将自动上线（可回复【查询审批进度】核验当前进度）\n\n{panel}"))
files6f = workflow(
    "产销品-上线审批", "子工作流6（阶段5 重写：双轨 approval-type=launch + V13.0 审批通过自动上线）。单入参 req_id：串行自查5类环节结果(config/spec/fee/test/requirement，各配提取代码节点)→CODE_SUMMARY_APPROVAL 合成结构化汇总(含offer_id与各环节结论)→LLM 生成《上线审批建议》(环节8标题头+校验看板+风险+整体结论)→存储 report→submit_release_approval 推送(approval-type=launch；后端硬门禁 approve_confirmed=true+req_id四环节齐全)→CODE_APPROVAL_POLL 轮询审批状态(模拟10s后自动通过)→selector 按 status=通过 分流：通过→LLM 生成监控运维方案(环节9标题头+xsbot-panel看板)→CODE_DOWNLOAD_LAUNCH_SCRIPT 配置/上线脚本下载(阶段1.2,端点不可达回退引导)并自动上线结束(不需要用户回复【确认上线】)；待审/驳回→环节8收尾块结束(引导【查询审批进度】核验)。", "wf_sub_06", s6f,
    [edge(601,602), edge(602,603), edge(603,604), edge(604,605), edge(605,606),
     edge(606,607), edge(607,608), edge(608,609), edge(609,610), edge(610,611),
     edge(611,612), edge(612,613), edge(613,614), edge(614,615), edge(615,616),
     edge(616,617), edge(617,618,-1), edge(617,623,0), edge(618,621), edge(621,622), edge(622,619), edge(623,620)])

# ============================================================
# wf_sub_07 监控运维（阶段5 重写：异常分支追加根因推理链+建工单闭环；两分支均输出 xsbot-panel+环节9收尾块）
#   开始(offer_id) → query_product_monitor → 异常判定(error_count≠0) →
#     异常分支：告警文案→send_alert→ops_root_cause(待后端占位)→LLM 根因推理链+优化方案→create_work_order(占位)→结束(异常闭环)
#     正常分支：LLM 运营摘要 → 结束(正常)
#   两分支结束节点输出环节9标题头 + 摘要/结论 + xsbot-panel + 环节9 收尾固定块
# ============================================================
s7f = []
s7f.append(start_node(701, [
    inp("offer_id", "销售品ID（9位存量编码或配置落地返回的新销售品ID；会话内可兜底）", required=True),
    inp("date_range", "统计周期（默认最近1天，可选）", required=False),
    inp("chat_id", "{chatId}", required=True),
]))
s7f.append(plugin_node(702, "监控查询", "query_product_monitor",
    "工具10：自研模拟监控查询（产品名称/订单量及趋势/异常量及趋势/计费差错率及趋势/告警列表），供运营报告与异常判定",
    BASE_URL + "/api/v1/appstore/product/monitor",
    [inp("offer_id", "销售品ID", ref_block=nid(701), ref_rel="offer_id"),
     inp("date_range", "统计周期", ref_block=nid(701), ref_rel="date_range"),
     inp("metric", "指标默认all", content="all")],
    [("offer_name", "产品名称", "string"),
     ("order_count", "订单量", "string"), ("order_trend", "订单量趋势", "string"),
     ("error_count", "异常量", "string"), ("error_trend", "异常量趋势", "string"),
     ("fee_error_rate", "计费差错率", "string"), ("fee_trend", "计费差错率趋势", "string"),
     ("alarm_list", "告警列表", "array")],
    method="get"))
s7f.append(selector_node2(703, "异常判定",
    [dep_node(702, "监控查询", ["error_count", "fee_error_rate"])],
    [(-1, [cond_item(cond_ref(702, "error_count", "监控查询"), 2, cond_str("0"))])]))
# ---- 异常分支 ----
s7f.append(llm_node(704, "告警文案生成",
    "基于监控异常数据生成告警文案（含产品、异常摘要、建议）。输入：offer_name={offer_name}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "输出要求：仅输出告警文案内容（对应出参 alert_content，需包含产品名称、异常摘要、处置建议三部分），不输出其他多余文字。",
    [inp("offer_name", "产品名称", ref_block=nid(702), ref_rel="offer_name"),
     inp("order_count", "订单量", ref_block=nid(702), ref_rel="order_count"),
     inp("error_count", "异常量", ref_block=nid(702), ref_rel="error_count"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(702), ref_rel="fee_error_rate"),
     inp("alarm_list", "告警列表", ref_block=nid(702), ref_rel="alarm_list")],
    [out("alert_content", "告警文案")]))
s7f.append(plugin_node(705, "异常告警", "send_alert",
    "工具11：自研模拟告警推送（生成alert_id，记录写入模拟库供监控回显）",
    BASE_URL + "/api/v1/appstore/alert/send",
    [inp("offer_id", "销售品ID", ref_block=nid(701), ref_rel="offer_id"),
     inp("alarm_level", "告警级别（按异常程度 high/middle/low）", content="high"),
     inp("content", "告警文案（节点704输出）", ref_block=nid(704), ref_rel="alert_content")],
    [("alert_id", "告警单号", "string"), ("status", "推送状态", "string")]))
s7f.append(code_node(706, "异动根因推理", CODE_OP_ROOT_CAUSE,
    [inp("offer_id", "销售品ID", ref_block=nid(701), ref_rel="offer_id")],
    [code_out("backend_pending", 706), code_out("reason_engine", 706), code_out("anomalies", 706), code_out("paths", 706), code_out("evidence_triples", 706), code_out("swrl_fired", 706), code_out("applied_rules", 706), code_out("action_list", 706), code_out("note", 706)]))
s7f.append(llm_node(707, "根因推理链+优化方案",
    "你是产销品运维归因智能体。基于监控异常数据与 ops_root_cause 出参（backend_pending={backend_pending}，anomalies={anomalies}，paths={paths}，reason_engine={reason_engine}，action_list={action_list}，note={note}），按 flow-D D-2 第6步模板输出根因推理链与优化方案：\n"
    "**异动根因推理链路（{reason_engine，占位时省略}）**\n- 异动确认：{anomalies 摘要：指标 code、delta、message，逐字引用}\n- 归因路径（按 paths 排名）：| 排名 | 根因类型 | 对象 | 权重 | 规则 | 证据 |\n（逐行展开 topN；paths 为空 → 照实引用出参 message\"已确认异动但未命中归因规则\"，禁止编造根因）\n- 命中规则：{swrl_fired / applied_rules，逗号分隔，空则照实省略}\n- 证据三元组：{evidence_triples 逐字摘要}\n\n"
    "**优化方案（规则驱动）**\n{action_list 逐条引用；无则引用出参 message，禁止编造}\n"
    "约束：本服务当前 backend_pending={backend_pending}（根因本体推理 ops_root_cause 待后端接入，阶段1.1），此时 output 归因段落须以占位说明（{note}）呈现，禁止自行编造根因/证据/优化方案。\n"
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
    [inp("offer_id", "销售品ID", ref_block=nid(701), ref_rel="offer_id")],
    [code_out("backend_pending", 708), code_out("work_order_id", 708), code_out("note", 708)]))
# xsbot-panel 独立代码节点（异常分支；产品名取自监控查询）
s7f.append(panel_code_node(712, "构建页面地址面板(异常闭环)", "07",
    panel_inputs(701, off_seq=701, off_rel="offer_id", name_seq=702, name_rel="offer_name"),
    biz_title="单品运营看板"))
s7f.append(end_node(709, "结束(异常-已告警并闭环)",
    [inp("offer_name", "产品名称", ref_block=nid(702), ref_rel="offer_name"),
     inp("offer_id", "销售品ID", ref_block=nid(701), ref_rel="offer_id"),
     inp("date_range", "统计周期", ref_block=nid(701), ref_rel="date_range"),
     inp("order_count", "订单量", ref_block=nid(702), ref_rel="order_count"),
     inp("order_trend", "订单量趋势", ref_block=nid(702), ref_rel="order_trend"),
     inp("error_count", "异常量", ref_block=nid(702), ref_rel="error_count"),
     inp("error_trend", "异常量趋势", ref_block=nid(702), ref_rel="error_trend"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(702), ref_rel="fee_error_rate"),
     inp("fee_trend", "计费差错率趋势", ref_block=nid(702), ref_rel="fee_trend"),
     inp("alarm_list", "告警列表", ref_block=nid(702), ref_rel="alarm_list"),
     inp("alert_id", "告警单号", ref_block=nid(705), ref_rel="alert_id"),
     inp("root_cause_report", "根因推理链+优化方案", ref_block=nid(707), ref_rel="root_cause_report"),
     inp("work_order_id", "工单号", ref_block=nid(708), ref_rel="work_order_id"),
     inp("wo_note", "建单占位说明", ref_block=nid(708), ref_rel="note"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(712), ref_rel="panel")],
    "【环节9/9·监控运维】✅ 执行成功\n\n"
    "【销售品运行监控】{offer_id}（{date_range}）\n"
    "- 产品名称：{offer_name}（为空显示\"未登记\"）\n"
    "- 订单量：{order_count}（{order_trend}）　异常量：{error_count}（{error_trend}）　计费差错率：{fee_error_rate}（{fee_trend}）\n"
    "- 告警列表：{alarm_list}（为空显示\"无\"）\n"
    "已推送告警，告警单号 {alert_id}\n\n"
     "{root_cause_report}\n"
     "**处置工单已建立（持续闭环）**：工单号 {work_order_id}｜（建单服务占位说明：{wo_note}）\n\n"
     "> **建议处理：** 已推送告警并建立处置工单 {work_order_id}，建议按优化方案执行后回复【查询监控】回检工单状态（工单号 {work_order_id} 已在会话中留存）；建单服务暂不可用时{wo_note}\n\n"
     "{panel}"))
# ---- 正常分支 ----
s7f.append(llm_node(710, "运营摘要生成(正常分支)",
    "你是产销品监控运维智能体。基于正常监控数据输出运营摘要（产品名称/订单量/异常量/计费差错率/告警列表），整体正常时给出简洁说明，无需强行生成建议。输入：offer_name={offer_name}，order_count={order_count}，error_count={error_count}，fee_error_rate={fee_error_rate}，alarm_list={alarm_list}\n"
    "输出要求：仅输出运营摘要正文（对应出参 ops_summary），不输出其他多余文字。",
    [inp("offer_name", "产品名称", ref_block=nid(702), ref_rel="offer_name"),
     inp("order_count", "订单量", ref_block=nid(702), ref_rel="order_count"),
     inp("order_trend", "订单量趋势", ref_block=nid(702), ref_rel="order_trend"),
     inp("error_count", "异常量", ref_block=nid(702), ref_rel="error_count"),
     inp("fee_error_rate", "计费差错率", ref_block=nid(702), ref_rel="fee_error_rate"),
     inp("alarm_list", "告警列表", ref_block=nid(702), ref_rel="alarm_list")],
    [out("ops_summary", "运营摘要正文")]))
s7f.append(end_node(711, "结束(正常-运营报告)",
    [inp("ops_summary", "运营摘要", ref_block=nid(710), ref_rel="ops_summary"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(713), ref_rel="panel")],
    "【环节9/9·监控运维】✅ 执行成功\n\n"
    "{ops_summary}\n\n"
     "> **建议处理：** 运行指标正常，无需人工干预；可继续观察，如需刷新运行情况可回复【查询监控】\n\n{panel}"))
# xsbot-panel 独立代码节点（正常分支；产品名取自监控查询）
s7f.append(panel_code_node(713, "构建页面地址面板(正常)", "07",
    panel_inputs(701, off_seq=701, off_rel="offer_id", name_seq=702, name_rel="offer_name"),
    biz_title="单品运营看板"))
files7f = workflow(
    "产销品-监控运维", "子工作流7（阶段5 重写：异常分支追加异动根因本体推理链+建工单闭环；两分支均输出环节9标题头+xsbot-panel+环节9收尾固定块）。query_product_monitor（自研模拟，含产品名称/订单量/异常量/计费差错率及趋势/告警列表）→异常判定（error_count≠0走异常分支；fee_error_rate>0.1 亦视为异常）→异常分支：告警文案→send_alert→ops_root_cause 根因推理(阶段1.1 Java插件待接入占位)→LLM 根因推理链+优化方案→create_work_order 建工单闭环(占位)→结束(异常闭环+xsbot-panel+收尾块)；正常分支：运营摘要→结束(正常+xsbot-panel+收尾块)。根因/优化一律依据出参逐字引用，禁止自行编造。", "wf_sub_07", s7f,
    [edge(701,702), edge(702,703), edge(703,710,0), edge(703,704,-1),
     edge(704,705), edge(705,706), edge(706,707), edge(707,708), edge(708,712), edge(712,709),
     edge(710,713), edge(713,711)])

# ============================================================
# wf_sub_08 审批进度查询（阶段5 重写：approval-id 优先/offer-id 兜底；按 approval_type 区分双轨回显+审批矩阵+收尾块）
#   开始(approval_id, offer_id) → query_approval_status → LLM 状态摘要归纳(区分需求工单/上线审批 + 审批矩阵 + 收尾块) → 结束
# ============================================================
s8f = []
s8f.append(start_node(801, [
    inp("approval_id", "审批单号（可选，与 offer_id 至少一个，approval_id 优先）", required=False),
    inp("offer_id", "销售品ID（可选，缺失 approval_id 时按此查最新审批单）", required=False),
    inp("chat_id", "{chatId}", required=True),
]))
s8f.append(plugin_node(802, "审批状态查询", "query_approval_status",
    "工具13：自研模拟审批进度查询（approval_id 优先，offer_id 兜底；返回审批单号/状态/当前环节/审批人/意见/更新时间及 approval_type/审批矩阵）",
    BASE_URL + "/api/v1/appstore/approval/status",
    [inp("approval_id", "审批单号", ref_block=nid(801), ref_rel="approval_id"),
     inp("offer_id", "销售品ID", ref_block=nid(801), ref_rel="offer_id")],
    [("approval_id", "审批单号", "string"), ("status", "审批中/通过/驳回", "string"),
     ("approval_type", "审批类型(requirement/launch)", "string"),
     ("current_node", "当前审批环节", "string"), ("approver", "当前审批人", "string"),
     ("opinion", "审批意见", "string"), ("update_time", "更新时间", "string"),
     ("approval_matrix", "审批矩阵 JSON", "array")],
    method="get"))
s8f.append(llm_node(803, "状态摘要归纳",
    "你是产销品审批进度查询智能体。基于查询出参（approval_id={approval_id}，status={status}，approval_type={approval_type}，current_node={current_node}，approver={approver}，opinion={opinion}，update_time={update_time}，approval_matrix={approval_matrix}），按 flow-D D-1 固定格式归纳输出：\n"
    "审批单号 {approval_id}｜状态：{status}｜当前环节：{current_node}（审批人 {approver}）｜最近意见：{opinion}｜更新时间：{update_time}\n\n"
    "**审批矩阵（逐字引用出参 approval_matrix[]，禁止编造）：**\n"
    "| 序号 | 审批节点 | 审批角色 | 状态 | 审批意见 | 更新时间 |\n"
    "| :---: | :--- | :--- | :--- | :--- | :--- |\n"
    "（逐行展开 approval_matrix 全部节点；出参无 approval_matrix 时省略本表，不得补造）\n\n"
    "审批类型衔接（status=通过 时按下述区分；否则只给摘要）：\n"
    "- approval_type=requirement（需求工单审批通过）→ 收尾块：> **建议处理：** 需求工单审批已通过，建议进入【需求分析】（可回复【开始配置】）\n"
    "- approval_type=launch（上线审批通过）→ 输出\"审批已通过，销售品上架完成 ✅\"，审批通过即视为自动上线，自动衔接环节9 监控运维方案（输出《监控运维方案》+xsbot-panel看板，模板见 flow-D V13.0）\n"
    "status=驳回 时附驳回原因，并提示\"可修改执行方案后重新发起\";查无审批单时输出\"未找到该销售品的审批单，请确认是否已发起审批\"。\n"
    "输出要求：仅输出审批状态摘要（对应出参 approval_summary，含审批矩阵与收尾块），不输出其他多余文字。",
    [inp("approval_id", "审批单号", ref_block=nid(802), ref_rel="approval_id"),
     inp("status", "审批状态", ref_block=nid(802), ref_rel="status"),
     inp("approval_type", "审批类型", ref_block=nid(802), ref_rel="approval_type"),
     inp("current_node", "当前环节", ref_block=nid(802), ref_rel="current_node"),
     inp("approver", "审批人", ref_block=nid(802), ref_rel="approver"),
     inp("opinion", "审批意见", ref_block=nid(802), ref_rel="opinion"),
     inp("update_time", "更新时间", ref_block=nid(802), ref_rel="update_time"),
     inp("approval_matrix", "审批矩阵", ref_block=nid(802), ref_rel="approval_matrix")],
    [out("approval_summary", "审批状态摘要")]))
# xsbot-panel 独立代码节点（offer_name 沿用 offer_id）
s8f.append(panel_code_node(805, "构建页面地址面板", "08",
    panel_inputs(801, off_seq=801, off_rel="offer_id", name_seq=801, name_rel="offer_id"),
    biz_title="上线审批看板"))
s8f.append(end_node(804, "结束(查询完成)",
    [inp("approval_summary", "审批状态摘要", ref_block=nid(803), ref_rel="approval_summary"),
     inp("panel", "xsbot-panel 页面地址片段", ref_block=nid(805), ref_rel="panel")],
    "{approval_summary}\n\n{panel}"))
files8f = workflow(
    "产销品-审批进度查询", "子工作流8（阶段5 重写：支持 approval-type 双轨）。query_approval_status（approval_id 优先/offer_id 兜底，V9.1 双轨）→LLM 状态摘要归纳：固定格式（审批单号/状态/当前环节/审批人/最近意见/更新时间）+审批矩阵（逐字引用 approval_matrix[] 禁止编造）+按 approval_type 区分衔接（requirement→引导需求分析【开始配置】；launch→通过即自动上线衔接监控运维方案）+收尾固定块（SKILL.md纪律5.1，禁止以矩阵表格收尾）。", "wf_sub_08", s8f,
    [edge(801,802), edge(802,803), edge(803,805), edge(805,804)])

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
    "产销品-存量合规扫描", "子工作流10（阶段5 新增(可选)：shelf_compliance 批量 R-C* 合规+整改引导）。CODE_OP_SHELF_COMPLIANCE 占位(阶段1.1 Java 插件待就绪)→LLM 合规报告渲染（R-C* 合规结论+整改引导）。", "wf_sub_10", s10f,
    [edge(1001,1002), edge(1002,1003), edge(1003,1005), edge(1005,1004)])

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
    [code_out("record_json", 1103), code_out("offer_id", 1103)]))
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
    "你是产销品需求工单审批发起助手。基于提交结果（req_id={req_id}，approval_id={approval_id}，status={status}），输出需求工单审批发起回执：\n"
    "【环节1/8·需求提报】✅ 需求工单审批已发起\n"
    "- 需求单号：{req_id}\n"
    "- 需求工单审批单号：{approval_id}，状态：{status}\n"
    "- 下一步建议：可回复【查询审批进度】查看审批状态；审批通过后可进入【需求分析】（环节2/8）生成《加载方案》。\n"
    "约束：逐字引用出参，禁止编造审批单号/状态；未取得 approval_id 时按失败口径输出并引导【重新执行】。\n"
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
    [edge(1101,1102), edge(1102,1103), edge(1103,1104),
     edge(1104,1105), edge(1105,1109), edge(1109,1106)])

# ============================================================
# 写出（V2.0 重塑：11 个子工作流 wf_sub_00~10 + V3.3 新增 wf_sub_11，共 12 个 JSON；
# 意图调度交由智能体层，无 wf_main_intent）
# ============================================================
for fn, data in [("wf_sub_00_需求提报.json", files00), ("wf_sub_01_需求分析.json", files01),
                 ("wf_sub_02_智能配置.json", files2f), ("wf_sub_03_规格稽核.json", files3f),
                 ("wf_sub_04_自动测试.json", files4f), ("wf_sub_05_资费校准.json", files5f),
                 ("wf_sub_06_上线审批.json", files6f), ("wf_sub_07_监控运维.json", files7f),
                 ("wf_sub_08_审批进度查询.json", files8f), ("wf_sub_09_存量产品查询.json", files9f),
                 ("wf_sub_10_存量合规扫描.json", files10f), ("wf_sub_11_发起需求审批.json", files11f)]:
    data = apply_layout(data)
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("written:", fn)
