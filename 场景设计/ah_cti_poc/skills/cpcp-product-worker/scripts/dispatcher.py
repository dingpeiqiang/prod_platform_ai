#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""产销品技能入口确定性调度器（dispatcher V1.0，四层架构第0层）。

背景：SKILL.md 曾把"意图路由/确认语义门禁/实体抽取"全部交给 LLM 按文字表格判断，
导致入口行为依赖 LLM 服从度（幻觉、格式漂移、随机话术）。本脚本把**入口调度层**
收敛为确定性规则：正则 + 实体词典 + 确认语义词表先做判定，命中即返回结构化指令，
LLM 仅在规则未命中/歧义时做**最小化意图归类**（封闭枚举，禁止自由文本）。

四层架构（对应改造方案）：
  第0层 意图解析（本脚本，规则优先，LLM 兜底）→ 输出结构化指令 {
    intent, confirmed, needs_llm, entities, kb_target, route, next_action }
  第1层 路由&规则（脚本与各 flow 前置检查，代码判断参数门禁/权限/前置条件）
  第2层 执行（run_pipeline.py / cpcp_api.py 状态机与业务调用）
  第3层 结果组装（flow-A~D 模板 + validate_output.py 校验）

核心保证：
1. 意图封闭枚举（INTENTS），任何输入必归类到其中之一；
2. 确认门禁（确认类词法命中才 confirmed=true，对应 SKILL.md 纪律3 与 run_pipeline --confirmed）；
3. 实体抽取用正则/词典（req_id/offer_id/product_id/approval_id/套餐名），会话上下文兜底；
4. 规则未命中 → needs_llm=true，返回最小化 llm_prompt（仅让 LLM 在封闭枚举内单选，禁止自由文本）；
5. 大报文经 --session-file / --message-file 传递，命令行不内联（纪律8）。

出参（stdout 单行 JSON，模型仅按 route 加载对应流程、按 needs_llm 决定是否套用 llm_prompt）：
   {"resultCode":"0","intent":...,"route":"A|B|C|D1|D2|D4|QNA|NONE",
   "confirmed":bool,"needs_llm":bool,"llm_prompt":str,
   "entities":{"req_id":...,"offer_id":...,"product_id":...,"approval_id":...,"offer_name":...},
   "kb_target":"K1|K2|K3|K4|K5|", "resume":bool, "fail_node_hint":str,
   "matched_rule":str}

用法：
  python dispatcher.py --message "我要查审批进度" [--session-file session.json]
  python dispatcher.py --message-file msg.txt --session-file session.json
"""
import argparse
import json
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def _force_utf8_stdio():
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is not None and hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


_force_utf8_stdio()

# ---------------- 意图封闭枚举 ----------------
# 与 SKILL.md 意图路由表一一对应，禁止新增自由形态
INTENTS = [
    "ASK_INTENT",        # 产品描述未表明意图 → 澄清"查询 or 配置"（ASK）
    "REQ_REPORT",        # 提报需求/修改需求 → flow-A0 需求提报（生成需求提报文档+需求工单审批）→ flow-A
    "CONFIRM_EXEC",      # 确认配置/确认执行 → 执行主干（run_pipeline --confirmed）
    "RESUME_EXEC",       # 重新执行失败环节 → run_pipeline --resume
    "APPROVAL",          # 上线审批/发起审批 → flow-C
    "QUERY_APPROVAL",    # 查询审批进度 → flow-D D-1
    "QUERY_MONITOR",     # 查询监控/运营 → flow-D D-2
    "QUERY_OFFER",       # 查询存量产品信息（按名称/描述或产品ID）→ flow-D D-4
    "ACCEPTANCE_PLAYBACK",  # 受理验证单独询问 → flow-B 环节4 回放
    "QNA",               # 业务知识问答 → K1~K5
    "REJECT",            # 否定/取消确认（不执行，不归任何流程）
    "OUT_OF_SCOPE",      # 超出产销品范围
]

# ---------------- 确认语义词表（纪律3 门禁） ----------------
# 命中任一词即 confirmed=true（对应"确认配置/确认执行/同意/可以/执行吧"等）
CONFIRM_WORDS = [
    "确认配置", "确认执行", "确认", "同意", "可以", "执行吧", "执行", "就这么办",
    "没问题", "好的", "开始", "继续配置", "马上执行", "就这么定", "可以执行",
]
# 重新执行类（须与确认同时或独立判定）
RESUME_WORDS = ["重新执行", "重跑", "续跑", "失败环节", "从失败", "重试", "再来一次", "重新开始"]
# 否定/拒绝类：命中即不视为确认，且不作为 CONFIRM_EXEC 意图（对应纪律3 未确认不执行）
NEGATION_WORDS = ["不同意", "不确认", "暂不", "先不", "不要执行", "先别", "不了", "拒绝",
                  "再等等", "先放一放", "不执行"]

# ---------------- 意图规则表（按优先级顺序匹配，先命中先得） ----------------
# 每项: (intent, [触发正则], 说明)
INTENT_RULES = [
    ("QUERY_APPROVAL",
     [r"审批.{0,20}(进度|状态|单状态|到哪|到哪了|结果|意见)", r"(审批|审核).{0,6}到哪"],
     "审批进度查询"),
    ("APPROVAL",
     [r"(上线|发起|提交).{0,3}(审批|审核)", r"(审批|审核).{0,3}(发起|提交|上线)"],
     "上线审批"),
    ("QUERY_MONITOR",
     [r"(查询|查看|查).{0,20}(监控|运营|运行监控|运行情况|上线后表现|运营情况)", r"监控.{0,6}(情况|结果|告警)"],
     "运行监控"),
    ("ACCEPTANCE_PLAYBACK",
     [r"(受理|验收)(验证|测试|结果)", r"查询受理验证", r"受理验证"],
     "受理验证回放"),
    ("RESUME_EXEC",
     [r"重新执行", r"重跑", r"续跑", r"从失败.{0,6}(环节|继续)", r"失败环节", r"重试"],
     "重新执行失败环节"),
    ("REQ_REPORT",
     [r"(提报|提交|新增|我要{0,2})(销售品|套餐|产品|需求|方案)", r"需求", r"创建.{0,4}(套餐|销售品)",
      r"(改|调整|变更|修改).{0,8}(一下|配置|方案|需求)", r"改配置",
      r"(我要|帮我|麻烦|请|现在|直接)?(配置|提报|创建|开通|办|生成)(.{0,8})(销售品|套餐|产品|方案|需求|这个|一款)",
      r"^(?:我|现在|直接)?(?:要|想)?配置(?:这个|这款|这个套餐|这款套餐)?$",
      r"^我要配置$"],
     "提报/修改需求"),
    ("CONFIRM_EXEC",
     [r"确认配置", r"确认执行", r"确认", r"同意", r"可以", r"执行吧"],
     "确认配置/确认执行（执行主干）"),
    # 存量产品信息查询（flow-D 支线D-4）：按名称/描述 或 产品ID 查询存量在架产品信息。
    # 置于 QNA 之前，避免"存量产品信息""XX套餐详情"被泛化问答吞掉；查询词（查/查看/了解…）优先命中。
    # 监控/审批/受理验证/上线等更具体意图已在前序规则命中，本组仅兜底"存量/在架产品信息"场景。
    ("QUERY_OFFER",
     [r"(查|查询|查看|看看|了解一下|介绍)。?.{0,10}(存量|在架|在售|现有|上架|历史).{0,8}(产品|销售品|套餐|商品)(信息|资料|规格|详情|资费|价格|在售)",
      r"(存量|在架|在售|现有|上架|历史).{0,6}(产品|销售品|套餐|商品).{0,8}(有哪|有哪些|信息|资料|规格|详情|资费|价格|在售)",
      r"(查|查询|查看|看下|了解一下).{0,12}\d{9}\b",
      r"\d{9}\b.{0,6}(信息|资料|详情|规格|产品|套餐)",
      r"(?:查|查询|查看|了解|介绍)。?\s*([\u4e00-\u9fa5A-Za-z0-9\-·元]{1,20}(?:套餐|卡|包|产品)).{0,6}(信息|资料|详情|在售|在架|存量)",
      r"([\u4e00-\u9fa5A-Za-z0-9\-·元]{1,20}(?:套餐|卡|包|产品)).{0,6}(在售|在架|存量信息|的信息|资料|详情)",
      r"(查|看看|了解一下)。?(有没有|是否还在售|还有没有|在哪查).{0,8}(套餐|产品|销售品|卡|包)"],
     "存量产品信息查询"),
    ("QNA",
     [r"(规范|规则|标准|怎么|如何|能否|能不能|是否|是什么|有哪些|收费|费用|资费|测试|用例|FAQ|常见|存量)"],
     "业务知识问答"),
]

# 重新执行（显式续跑）优先于确认/提报；但被否定（"不要重新执行"）则不续跑
RESUME_ONLY_RULES = [r"^重新执行", r"^重跑", r"^续跑", r"^从失败"]

# BUG① 修复：裸"执行"仅当短句确认（用户简短回复"执行/好执行/可以执行"）才归 CONFIRM_EXEC；
# 长文档正文中出现的"执行"（如"当月执行过渡期资费""老用户...当月执行原套餐资费"）属普通动词，
# 不构成执行确认。显式确认词（确认/同意/可以/确认执行/确认配置/执行吧）仍在规则表内正常命中。
BARE_EXEC_SHORT_RE = re.compile(r"^(?:好|嗯|行|可以|就|那)?\s*执行\s*(?:吧|下|一下|！|!|。|\.)?$")
BARE_EXEC_MAX_LEN = 20

# ---------------- 知识库问答分流（kb_target）---------------
KB_RULES = [
    ("K1", [r"规范", r"命名", r"管理办法", r"配置规范", r"规则"]),
    ("K2", [r"资费", r"费用", r"收费", r"月租", r"套餐档位", r"叠加", r"优惠"]),
    ("K3", [r"测试", r"用例", r"自动化", r"验收"]),
    ("K5", [r"FAQ", r"常见问题", r"怎么", r"如何", r"能不能"]),
    # K4 存量：按销售品 ID 单文件查询，规则无法确定 ID 类型时默认 K4 让流程按 ID 检索
    ("K4", [r"存量", r"已上线", r"现有销售品", r"历史产品"]),
]

# ---------------- 完整产品文档识别（→ REQ_REPORT / flow-A） ----------------
# 用户直接上传/粘贴整份销售品规格文档（资费方案/订购/退订规则等）时，属"新需求提报"而非知识问答：
# 文档为陈述性规格、含章节编号结构、内容量大，且不带疑问句。识别规则：
#   长度 ≥ PRODUCT_DOC_MIN_LEN 且 无疑问词 且 (含章节编号 且 资费方案关键词 ≥ 2)
# 优先级高于 QNA（常规交互动词意图仍在更前命中），故放在常规规则循环之前判定。
PRODUCT_DOC_MIN_LEN = 120
PRODUCT_DOC_SECTION_RE = re.compile(
    r"(?:[一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|\n\d+[、.．]|\s\d+[、.．])")
PRODUCT_DOC_KEYWORDS = [
    "资费档位", "套餐内", "套外", "生效方式", "退订", "订购", "副卡",
    "计费周期", "套餐有效", "资费方案", "流量结转", "断网授权", "续订", "计费",
]
# 问句判定：文档正文里"是否允许办理副卡：允许"这类规格标题属陈述性，单次"是否"不构成提问；
# 只有出现问号或 ≥2 个交互式问语（如何/怎么/能否/请问/帮我查/查一下…）才视为问句。
PRODUCT_DOC_ASK_WORDS = ["如何", "怎么", "能否", "能不能", "是什么", "有哪些",
                         "请问", "帮我查", "查一下", "介绍一下"]


def _is_product_doc(message):
    """整份销售品规格文档判定（陈述性长文、有章节结构、资费关键词充足、非交互问句）。"""
    if len(message) < PRODUCT_DOC_MIN_LEN:
        return False
    if "？" in message or "?" in message:
        return False
    if sum(1 for q in PRODUCT_DOC_ASK_WORDS if q in message) >= 2:
        return False
    if not PRODUCT_DOC_SECTION_RE.search(message):
        return False
    kw = sum(1 for k in PRODUCT_DOC_KEYWORDS if k in message)
    return kw >= 2


# ---------------- 产品描述意图澄清（ASK_INTENT） ----------------
# 用户发送了一段产品/套餐/资费的**陈述性描述**，但既没有明确"查询"也没有明确"配置"意图时，
# 不擅自按"需求提报"处理，先向用户澄清："您是要查询该产品，还是要进行配置？"
# 规则（第0层，规则优先）：含产品/资费描述特征 + 非问句 + 无明确查询词 + 无明确配置词 → ASK_INTENT；
# 一旦含明确查询词（查/看/怎么/能否…）或明确配置词（提报/配置/需求/创建…）则交回常规意图规则，不拦截。
PRODUCT_DESC_NAME_HINTS = ["套餐", "产品", "销售品", "副卡", "宽带", "天翼", "5G", "流量包",
                           "权益包", "加装包", "语音包", "会员", "校园卡"]
PRODUCT_DESC_FEE_HINTS = ["元/月", "月租", "月费", "资费", "流量", "语音", "分钟", "GB", "G流量",
                          "档位", "通话", "短信", "叠加包"]
EXPLICIT_QUERY_WORDS = ["查询", "查看", "查一下", "查查", "看看", "帮我查", "给我查", "介绍",
                        "有没有", "能不能", "能否", "怎么", "如何", "是什么", "有哪些", "多少钱",
                        "了解", "请问", "问一下", "存量", "信息"]
EXPLICIT_CONFIG_WORDS = ["提报", "配置", "需求", "创建", "新增", "生成", "开通", "办一个",
                         "报装", "上线", "落地", "要走配置", "进入配置",
                         "做成", "要办", "我要办", "我要提报", "我要配置", "配置一下"]
CONFIRM_AS_INTENT_WORDS = ["确认配置", "确认执行", "确认", "同意", "可以", "执行吧"]


def _is_product_desc_no_intent(message):
    """产品描述但未表明意图（→ ASK_INTENT）：陈述性规格描述，非问句，且无查询/配置/确认意图词。"""
    if len(message) < 4 or len(message) > 400:
        return False
    if "？" in message or "?" in message:
        return False
    for w in PRODUCT_DESC_NAME_HINTS:
        if w in message:
            break
    else:
        return False
    if sum(1 for f in PRODUCT_DESC_FEE_HINTS if f in message) < 1:
        return False
    # 无明确查询/配置/确认意图词，才视为"未表明意图"
    for w in EXPLICIT_QUERY_WORDS + EXPLICIT_CONFIG_WORDS + CONFIRM_AS_INTENT_WORDS:
        if w in message:
            return False
    return True


def _has_config_intent(message):
    """消息是否附带明确配置/提报意图（用于产品文档/描述场景判定是否走 REQ_REPORT）。"""
    for w in EXPLICIT_CONFIG_WORDS + CONFIRM_AS_INTENT_WORDS:
        if w in message:
            return True
    for pat in (r"(提报|提交|新增|我要{0,2})(销售品|套餐|产品|需求|方案)", r"需求",
                r"创建.{0,4}(套餐|销售品)", r"(改|调整|变更|修改).{0,8}(一下|配置|方案|需求)",
                r"改配置", r"(我要|帮我|麻烦|请|现在|直接)?(配置|提报|创建|开通|办|生成)"
                r"(.{0,8})(销售品|套餐|产品|方案|需求|这个|一款)",
                r"^(?:我|现在|直接)?(?:要|想)?配置(?:这个|这款|这个套餐|这款套餐)?$",
                r"^我要配置$"):
        if re.search(pat, message):
            return True
    return False


# ---------------- 实体抽取（正则 + 形如字典） ----------------
REQ_ID_RE = re.compile(r"PLAN\d{14,20}", re.IGNORECASE)
# BUG② 修复：9 位产品 ID 须为孤立数字串（前后不为数字），避免从 10 位服务号（如 4008610000）中部误截
PRODUCT_ID_RE = re.compile(r"(?<!\d)(?:P?\d{9}|\b\d{9}\b)(?!\d)")
APPROVAL_ID_RE = re.compile(r"APPR?\d{6,20}", re.IGNORECASE)
OFFER_ID_RE = re.compile(r"(?:OFFER|OFP?)[-_]?\d{6,20}", re.IGNORECASE)

# 套餐名称：先尝试"具体名＋卡/包/产品"（校园青春卡、副卡、权益包），再退化"XX套餐"（畅享套餐）。
# 匹配后剥离句首动词/助词前缀（帮我/我要/请/查/查看/查询/给/下/的 等），避免把"帮我校园青春卡"误当名称。
# BUG③ 修复：`卡/包/产品` 后缀后不接受"含括围容装裹"——避免把"不包含港澳台"的动词"包"误当"X包"商品名
OFFER_NAME_RE = re.compile(r"([\u4e00-\u9fa5A-Za-z0-9]{1,12}(?:卡|包|产品)(?![含括围容装裹]))")
# 连字符/带宽名等存量品名称（如"5G-A融合套餐199元"）容错：名称可含 -· 与档位"元"，仍以 套餐 收尾
OFFER_NAME_GEN_RE = re.compile(r"([\u4e00-\u9fa5A-Za-z0-9\-·元]{1,14}套餐)")
OFFER_NAME_STOP_PREFIX = ["帮我", "请帮", "请", "我要", "我", "查询", "查看", "查", "给",
                          "把", "下", "的", "和", "与", "和我的", "修改", "改", "调整", "变更", "上传"]


def _load_text(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return f.read()


def _load_json(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def _clean_offer_name(raw):
    if not raw:
        return ""
    for stop in OFFER_NAME_STOP_PREFIX:
        if raw.startswith(stop):
            return raw[len(stop):]
    return raw


def _extract_entities(message, session):
    """实体抽取：消息优先，会话兜底（会话上下文取参规则见 flow-D 前置检查）。"""
    ents = {}
    m = REQ_ID_RE.search(message)
    ents["req_id"] = m.group(0).upper() if m else (session.get("req_id") or "")
    ents["product_id"] = ""
    ents["offer_id"] = ""
    ents["approval_id"] = ""
    m = APPROVAL_ID_RE.search(message)
    if m:
        ents["approval_id"] = m.group(0).upper()
    else:
        ents["approval_id"] = session.get("approval_id") or ""
    # offer_id / product_id：9 位数字（存量）或 P+编号（新增落地）；优先消息显式
    for pat, key in ((OFFER_ID_RE, "offer_id"), (PRODUCT_ID_RE, "product_id")):
        m = pat.search(message)
        if m:
            ents[key] = m.group(0)
    if not ents["offer_id"]:
        ents["offer_id"] = session.get("offer_id") or ""
    if not ents["product_id"]:
        # 会话 offer_id（新增落地 P 形态）或 product_id
        ents["product_id"] = session.get("product_id") or (session.get("offer_id") or "")
    m = OFFER_NAME_RE.search(message) or OFFER_NAME_GEN_RE.search(message)
    ents["offer_name"] = _clean_offer_name(m.group(1)) if m else (session.get("offer_name") or "")
    return ents


def _classify_kb(message):
    for kb, pats in KB_RULES:
        for p in pats:
            if re.search(p, message):
                return kb
    return ""


def _classify_intent(message):
    """返回 (intent, matched_rule, confirmed, resume)。规则优先，未命中返回 None 供 LLM 兜底。"""
    negated = any(w in message for w in NEGATION_WORDS)
    confirmed = (not negated) and any(w in message for w in CONFIRM_WORDS)
    resume = any(w in message for w in RESUME_WORDS)

    # 1. 重新执行（显式续跑）优先于确认/提报；但被否定（"不要重新执行"）则不续跑
    if resume and not any(w in message for w in NEGATION_WORDS) and any(
            re.search(p, message) for p in RESUME_ONLY_RULES):
        return "RESUME_EXEC", "resume-only-regex", confirmed, resume

    # 1'. 完整产品规格文档 → 产品描述；附带明确配置/提报意图则 REQ_REPORT，否则 ASK_INTENT（先澄清查询/配置）
    if not negated and _is_product_doc(message):
        if _has_config_intent(message):
            return "REQ_REPORT", "product-doc-config", confirmed, resume
        return "ASK_INTENT", "product-doc-ask", confirmed, resume

    # 1''. 产品描述但未表明意图（非问句、且无任何查询/配置/确认词）→ ASK_INTENT（澄清"查询 or 配置"）
    #      判断在规则循环前：只要是"陈述性产品/资费描述 + 无意图词"，先澄清，不被宽松的 QNA 词抢走；
    #      一旦含查询词（存量/信息/查/怎么…）或配置词则放行给常规规则，不拦截。
    if not negated and _is_product_desc_no_intent(message):
        return "ASK_INTENT", "product-desc-ask", confirmed, resume

    # 2. 按优先级顺序匹配意图规则
    for intent, pats, desc in INTENT_RULES:
        if intent == "CONFIRM_EXEC" and negated:
            continue  # 被否定时不得归为确认执行
        for p in pats:
            if re.search(p, message):
                return intent, p, confirmed, resume

    # 2'. 裸"执行"仅当短句确认才归 CONFIRM_EXEC（长文档正文上的"执行"不误判）
    if (not negated and len(message.strip()) <= BARE_EXEC_MAX_LEN
            and BARE_EXEC_SHORT_RE.match(message.strip())):
        return "CONFIRM_EXEC", "bare-exec-short", confirmed, resume
    # 3. 被否定且无其他匹配 → 取消确认（REJECT，不归任何流程，避免被误判/走 LLM）
    if negated:
        return "REJECT", "negation-regex", confirmed, resume
    return None, "", confirmed, resume


def _build_route(intent):
    """意图 → 路由（第1层起点）。"""
    return {
        "ASK_INTENT": "ASK",
        "REQ_REPORT": "A",
        "CONFIRM_EXEC": "B",
        "RESUME_EXEC": "B",
        "APPROVAL": "C",
        "QUERY_APPROVAL": "D1",
        "QUERY_MONITOR": "D2",
        "QUERY_OFFER": "D4",
        "ACCEPTANCE_PLAYBACK": "B",
        "QNA": "QNA",
        "REJECT": "NONE",
        "OUT_OF_SCOPE": "NONE",
    }.get(intent, "NONE")


def _llm_prompt(message, session, entities):
    """规则未命中时的最小化兜底 prompt：仅让 LLM 在封闭枚举内单选 + 填实体，禁止自由文本。"""
    known = {k: entities.get(k) or session.get(k) for k in ("req_id", "offer_id", "product_id", "approval_id")}
    return json.dumps({
        "task": "intent_classify",
        "user_message": message[:500],
        "allowed_intents": INTENTS,
        "known_entities": {k: v for k, v in known.items() if v},
        "output_schema": {"intent": "<one of allowed_intents>",
                          "confirmed": "<true if user confirmed execution>",
                          "offer_name": "<offer name extracted, or empty>"},
        "rules": "仅做意图归类与实体提取；禁止输出任何自由文本、禁止新增字段、禁止执行业务。",
    }, ensure_ascii=False)


def main():
    _force_utf8_stdio()
    p = argparse.ArgumentParser(description="产销品技能入口确定性调度器（意图路由/确认门禁/实体抽取）")
    p.add_argument("--message", default="", help="用户最新消息")
    p.add_argument("--message-file", default="", help="用户消息文件（>1KB 建议文件方式）")
    p.add_argument("--session-file", default="", help="会话上下文 JSON（req_id/offer_id/product_id/approval_id 等）")
    args = p.parse_args()

    message = args.message
    if args.message_file:
        try:
            message = _load_text(args.message_file).strip()
        except Exception as e:
            print(json.dumps({"resultCode": "FILE_ERROR", "resultMsg": "消息文件读取失败：%s" % e},
                             ensure_ascii=False))
            sys.exit(2)
    if not message.strip():
        print(json.dumps({"resultCode": "PARAM_MISSING", "resultMsg": "缺少用户消息"},
                         ensure_ascii=False))
        sys.exit(2)

    session = {}
    if args.session_file:
        try:
            session = _load_json(args.session_file)
        except Exception:
            session = {}

    entities = _extract_entities(message, session)

    intent, matched_rule, confirmed, resume = _classify_intent(message)

    # 兜底：仅能确定为"超出范围"的明确拒绝 → OUT_OF_SCOPE；
    # 其余规则未命中 → 不猜意图，needs_llm=true 让 LLM 在封闭枚举内兜底归类（禁止自由文本）
    needs_llm = False
    llm_prompt = ""
    if not matched_rule:
        if any(w in message for w in ["贷款", "股票", "天气", "笑话", "你好", "你是谁", "自我介绍"]):
            intent = "OUT_OF_SCOPE"
        else:
            intent = ""
            needs_llm = True
            llm_prompt = _llm_prompt(message, session, entities)
        matched_rule = "llm-fallback"

    out = {
        "resultCode": "0",
        "intent": intent,
        "route": _build_route(intent),
        "confirmed": confirmed,
        "resume": resume,
        "fail_node_hint": "",
        "needs_llm": needs_llm,
        "llm_prompt": llm_prompt,
        "entities": entities,
        "kb_target": _classify_kb(message) if intent == "QNA" else "",
        "matched_rule": matched_rule,
    }
    print(json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()
