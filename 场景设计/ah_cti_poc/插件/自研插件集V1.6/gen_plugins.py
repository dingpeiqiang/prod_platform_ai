# -*- coding: utf-8 -*-
# 生成产销品加载AI应用 V1.6 自研插件集导出JSON（13个工具）
# 依据：《产销品加载AI应用开发方案.md》V1.6、《产销品加载AI应用-细化设计方案.md》V1.2
# 口径：工具1~6、7~11、13 全部自研实现+模拟结果输出；模拟数据兼容《产品信息.txt》18个销售品
import json, os, io

BASE = os.path.dirname(os.path.abspath(__file__))
BASE_URL = "http://10.86.13.201:31281"
CREATE_USER = "oncon100000000556_10000"
CREATE_USER_NAME = "丁培强"

def merge(base, extra):
    out = dict(base)
    out.update(extra)
    return out

def schema_param(name, ptype, desc, required=False, default="", enum=""):
    return merge({"default": default, "description": desc, "type": ptype, "enum": enum},
                 {"required": True} if required else {})

def schema_obj_props(props, required=None):
    d = {"properties": props, "required": required or []}
    return d

def build_plugin(tool_id, tool_name, tool_code, desc, path, method, req_props, req_required,
                 resp_props, induction, llm_type=1):
    """构建与平台导出格式一致的插件JSON"""
    flow_inputs = []
    for name, p in req_props.items():
        flow_inputs.append({
            "blockID": "", "filedType": p.get("type", "string"), "relName": "",
            "name": name, "description": p["description"],
            "sechema": [], "type": p.get("type", "string"),
            "required": name in req_required, "content": ""
        })

    flow_outputs = []
    for name, o in resp_props.items():
        flow_outputs.append({
            "name": name, "cname": o["description"],
            "sechema": o.get("sechema", []), "type": o.get("type", "string"),
            "required": False
        })

    flow_json = {
        "authentic_info": "",
        "authentic_info_new": {
            "auth_type": "1",
            "auth_info": {"outparams": [], "inparams": [], "params": []}
        },
        "id": tool_id,
        "inputs": flow_inputs,
        "nodeMeta": {"code": tool_code, "description": desc, "title": tool_name, "version": "1"},
        "ontology": "",
        "ontologyValidation": 0,
        "outputs": flow_outputs,
        "parameters": [
            {"description": p["description"], "name": n, "sechema": [],
             "type": p.get("type", "string"), "required": n in req_required}
            for n, p in req_props.items()
        ]
    }

    schema_json = {
        "openapi": "3.1.0",
        "info": {"observationField": "", "description": desc, "title": tool_name, "version": "v1.6.0"},
        "servers": [{"url": BASE_URL}],
        "paths": {
            path: {
                method.lower(): {
                    "requestBody": {
                        "required": True,
                        "content": {"application/json": {"schema": schema_obj_props(req_props, req_required)}}
                    },
                    "responses": {
                        "200": {
                            "content": {"application/json": {"schema": schema_obj_props(resp_props)}}
                        }
                    }
                }
            }
        }
    }

    export = {
        "createUserId": CREATE_USER,
        "interfaceAddress": BASE_URL + path,
        "releaseTime": None,
        "sequ": 0,
        "flowJson": json.dumps(flow_json, ensure_ascii=False),
        "userScope": 4,
        "createUserName": CREATE_USER_NAME,
        "releaseUser": None,
        "toolAuthenticType": "1",
        "ontologyValidation": 0,
        "orgId": "10000",
        "toolProto": 1,
        "toolDesc": desc,
        "induction": induction,
        "llmType": llm_type,
        "toolIco": None,
        "schemaJson": schema_json,
        "toolName": tool_name,
        "toolType": 1,
        "version": 1,
        "status": 0,
        "id": tool_id,
        "toolCode": tool_code,
        "pluginName": "产销品加载插件集",
        "remarks": "V1.6 自研模拟实现；模拟数据兼容《产品信息.txt》全部18个销售品",
        "submit_way": method.lower()
    }
    return export

def arr(name, desc, item_props=None):
    d = {"description": desc, "type": "array"}
    if item_props:
        d["items"] = {"type": "object", "properties": item_props}
    return d

plugins = []

# ---------------- 工具1 相似度分析 ----------------
plugins.append(build_plugin(
    "similar-offer-0001", "相似度分析", "query_similar_offer",
    "自研模拟实现（V1.6）：以《产品信息.txt》全部18个销售品（5G-A系列10个+权益随心选系列8个）为相似产品库，按业务需求描述返回相似销售品列表及相似度评分，支撑需求分析环节匹配历史产品与AI补全",
    "/api/v1/appstore/similar/offer/query", "POST",
    {
        "businessDesc": schema_param("businessDesc", "string",
            "业务需求描述文本，≤5000字符，超出由工作流节点先做摘要压缩；空返回 PARAM_MISSING", True),
    },
    ["businessDesc"],
    {
        "resultCode": {"description": "0 成功 / 1 失败 / PARAM_MISSING / PARSE_ERROR", "type": "string"},
        "resultMsg": {"description": "处理结果描述", "type": "string"},
        "similarOfferList": arr("similarOfferList", "相似产品列表，按相似度降序，取自《产品信息.txt》18个销售品", {
            "similarOfferId": {"description": "相似销售品ID（如 900102308）", "type": "string"},
            "similarOfferName": {"description": "相似销售品名称", "type": "string"},
            "similarityScore": {"description": "相似度评分（0~1）", "type": "string"},
            "similarityDesc": {"description": "相似原因描述（命中字段/资费结构说明）", "type": "string"},
        }),
    },
    "N"))

# ---------------- 工具2 实时规格稽核 ----------------
plugins.append(build_plugin(
    "realtime-audit-0001", "实时规格稽核", "realtime_spec_audit",
    "自研模拟实现（V1.6）：按销售品ID与配置JSON对照《产品信息.txt》规则库实时稽核，同步返回稽核结果（通过/驳回+问题明细+整改建议）；无文件上传、无异步轮询；svcCode=5012010056/appKey=eOrder1/dstSysId=OrderCenter",
    "/api/v1/appstore/audit/realtime", "POST",
    {
        "offer_id": schema_param("offer_id", "string",
            "配置落地返回的销售品ID（save_product_config 出参 offer_id）；缺失返回 PARAM_MISSING", True),
        "config_json": schema_param("config_json", "string",
            "落地配置JSON原文（来自执行方案JSON落地后的配置快照），工作流变量引用不提参；缺失或非法JSON返回 5001", True),
        "audit_scene": schema_param("audit_scene", "string",
            "稽核场景枚举：spec（规格）/fee（资费）/all（全量），默认 all", False, "all", "spec,fee,all"),
    },
    ["offer_id", "config_json"],
    {
        "pass": {"description": "1 通过 / 0 不通过", "type": "string"},
        "error_list": arr("error_list", "问题明细，pass=0 时非空", {
            "item": {"description": "问题项（对应配置字段/规则）", "type": "string"},
            "level": {"description": "严重级别：error 阻断 / warning 提示", "type": "string"},
            "desc": {"description": "问题描述，含实际值与期望规则", "type": "string"},
            "suggest": {"description": "整改建议", "type": "string"},
        }),
        "audit_summary": {"description": "稽核总结（一句话）", "type": "string"},
        "resultCode": {"description": "0 成功 / 1 失败 / NET_ERROR / TIMEOUT / PARAM_MISSING", "type": "string"},
    },
    "Y"))

# ---------------- 工具3 销售品测试发起 ----------------
plugins.append(build_plugin(
    "offer-test-0001", "销售品测试发起", "offer_test",
    "自研模拟实现（V1.6）：按销售品ID匹配《产品信息.txt》种子数据异步模拟测试执行（自动覆盖受理类场景：套餐新装/副卡加装/套餐退订），返回模拟测试流水 globalId（50+yyyyMMddHHmmss+10位随机数）",
    "/api/v1/appstore/test/offer/start", "POST",
    {
        "offerId": schema_param("offerId", "string",
            "被测销售品ID（offer表主键，如 900102308）；须为《产品信息.txt》18个销售品之一，未收录返回 4001", True),
    },
    ["offerId"],
    {
        "resultCode": {"description": "0 处理成功 / 1 处理失败", "type": "string"},
        "resultMsg": {"description": "处理结果描述", "type": "string"},
        "globalId": {"description": "测试流水号，格式 50+yyyyMMddHHmmss+10位随机数，后续三个查询接口必传", "type": "string"},
    },
    "N"))

# ---------------- 工具4 查询测试场景 ----------------
plugins.append(build_plugin(
    "test-scenes-0001", "查询测试场景", "get_test_scenes",
    "自研模拟实现（V1.6）：按 globalId 返回该销售品在《产品信息.txt》规则推导的模拟测试场景集合（即受理验证覆盖范围：套餐新装 S_O_TC/副卡加装 S_ADD_CARD/套餐退订 S_U_TC）",
    "/api/v1/appstore/test/offer/scenes", "POST",
    {
        "globalId": schema_param("globalId", "string",
            "测试流水号（offer_test 出参）；缺失或查无返回 4002", True),
    },
    ["globalId"],
    {
        "resultCode": {"description": "0 成功 / 1 失败", "type": "string"},
        "testScenes": arr("testScenes", "测试场景列表", {
            "testSceneId": {"description": "场景ID", "type": "string"},
            "testSceneName": {"description": "场景名称（套餐新装/副卡加装/套餐退订）", "type": "string"},
            "testSceneNbr": {"description": "场景编码：S_O_TC/S_ADD_CARD/S_U_TC", "type": "string"},
            "testSceneDesc": {"description": "场景描述", "type": "string"},
            "sort": {"description": "排序", "type": "string"},
        }),
    },
    "Y"))

# ---------------- 工具5 查询测试进度 ----------------
plugins.append(build_plugin(
    "test-progress-0001", "查询测试进度", "get_test_progress",
    "自研模拟实现（V1.6）：按 globalId 推进模拟测试进度状态机（总步骤=场景数+2，前2步固定为智能匹配测试场景&用例、智能匹配测试资源），支持工作流循环节点轮询（间隔5s，超时30分钟）",
    "/api/v1/appstore/test/offer/progress", "POST",
    {
        "globalId": schema_param("globalId", "string",
            "测试流水号（offer_test 出参）；缺失或查无返回 4002", True),
    },
    ["globalId"],
    {
        "totalSteps": {"description": "总步骤数=场景数+2", "type": "string"},
        "activeIndex": {"description": "当前步骤下标（从0计）", "type": "string"},
        "done": {"description": "测试是否全部完成", "type": "string"},
        "failed": {"description": "是否存在失败/中止（RESULT_CODE=1/2）场景", "type": "string"},
        "failIndex": {"description": "第一个失败场景步骤下标，无失败为-1", "type": "string"},
        "totalSceneCount": {"description": "场景总数", "type": "string"},
        "finishedSceneCount": {"description": "已完成场景数", "type": "string"},
        "failedSceneCount": {"description": "失败（含中止）场景数", "type": "string"},
    },
    "N"))

# ---------------- 工具6 查询测试结果 ----------------
plugins.append(build_plugin(
    "test-result-0001", "查询测试结果", "get_test_result",
    "自研模拟实现（V1.6）：按 globalId 生成逐场景测点比对明细，预期值 presetValue 取自该销售品在《产品信息.txt》中的规则值，testValue 模拟生成（默认与预期一致，可构造不一致用例）；受理凭证 orderId/offerInstId 模拟生成，作为受理验证结论依据",
    "/api/v1/appstore/test/offer/result", "POST",
    {
        "globalId": schema_param("globalId", "string",
            "测试流水号；须在测试进度全部完成后查询（done=true），否则返回 4003", True),
    },
    ["globalId"],
    {
        "resultCode": {"description": "0 成功 / 1 失败", "type": "string"},
        "resultMsg": {"description": "处理结果描述", "type": "string"},
        "testRequestId": {"description": "测试请求ID（auto_test_request 表主键）", "type": "string"},
        "testRequestName": {"description": "测试名称（销售品系统名+_测试验证）", "type": "string"},
        "offerName": {"description": "被测销售品名称", "type": "string"},
        "orderId": {"description": "实际受理生成的订单号（受理验证依据）", "type": "string"},
        "offerInstId": {"description": "实际受理生成的销售品实例ID（受理验证依据）", "type": "string"},
        "testScenes": arr("testScenes", "逐场景结果", {
            "testSceneNbr": {"description": "场景编码：S_O_TC/S_ADD_CARD/S_U_TC", "type": "string"},
            "testSceneName": {"description": "场景名称", "type": "string"},
            "testSceneDesc": {"description": "场景描述", "type": "string"},
            "testCaseCount": {"description": "测点总数", "type": "string"},
            "successTestCaseCount": {"description": "成功数", "type": "string"},
            "failTestCaseCount": {"description": "失败数", "type": "string"},
            "testCasePointResults": arr("testCasePointResults", "测点明细", {
                "testPointNbr": {"description": "测点编码：P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE", "type": "string"},
                "presetValue": {"description": "规格规定值（预期值，取自《产品信息.txt》该销售品规则值）", "type": "string"},
                "testValue": {"description": "实测值（模拟CRM实际生成结果）", "type": "string"},
                "resultCode": {"description": "0 一致 / 1 不一致", "type": "string"},
                "resultMsg": {"description": "比对结论", "type": "string"},
            }),
            "objTestSceneRel": {"description": "AI场景总结：resultMsg 场景测试总结/summaryDesc 汇总描述/suggestion 优化建议", "type": "string"},
        }),
    },
    "Y"))

# ---------------- 工具7 配置落地 ----------------
plugins.append(build_plugin(
    "save-config-0001", "配置落地", "save_product_config",
    "自研模拟实现（V1.6）：读取执行方案JSON，将基础信息/资源配置/营销资源/销售规则四类字段写入模拟CRM销售品配置库（内存产品档案，种子数据含《产品信息.txt》18个销售品），生成 product_id/offer_id；内部二次校验 confirmed==true，未确认返回 NOT_CONFIRMED 防止绕过确认门禁；写操作不自动重试",
    "/api/v1/appstore/product/config/save", "POST",
    {
        "plan_id": schema_param("plan_id", "string",
            "执行方案存储key（如 PLAN20260912001）；缺失返回 PARAM_MISSING", True),
        "plan_json": schema_param("plan_json", "string",
            "执行方案JSON原文，必须为节点结果存储查询插件取回的JSON原文，原样透传（工作流变量引用，不提参）；禁止二次生成", True),
        "confirmed": schema_param("confirmed", "string",
            "用户确认标志 true/false，由工作流从会话上下文传入；非 true 返回 NOT_CONFIRMED", True, "true"),
        "operator": schema_param("operator", "string", "操作人（从会话上下文取），可空", False),
    },
    ["plan_id", "plan_json", "confirmed"],
    {
        "product_id": {"description": "CRM 产品ID", "type": "string"},
        "offer_id": {"description": "销售品ID（后续稽核/测试入参）", "type": "string"},
        "save_result": {"description": "各字段分类写入结果：基础信息/资源配置/营销资源/销售规则 各自 success/fail 及原因", "type": "string"},
        "status": {"description": "SUCCESS / PARTIAL / FAIL / NOT_CONFIRMED", "type": "string"},
    },
    "N"))

# ---------------- 工具8 计费规则校验 ----------------
plugins.append(build_plugin(
    "billing-verify-0001", "计费规则校验", "check_billing_rule",
    "自研模拟实现（V1.6）：内置规则引擎按该销售品《产品信息.txt》资费/叠加/互斥规则校验配置JSON，输出模拟风险清单（默认通过，支持构造冲突用例验证驳回分支）",
    "/api/v1/appstore/billing/rules/verify", "POST",
    {
        "config_json": schema_param("config_json", "string",
            "落地配置JSON，工作流变量引用不提参；缺失或非法JSON返回 3001", True),
        "check_scene": schema_param("check_scene", "string",
            "校验场景枚举：fee（计费）/overlay（叠加）/superposition（互斥叠加）/all（全量），默认 all", False, "all", "fee,overlay,superposition,all"),
    },
    ["config_json"],
    {
        "pass": {"description": "1 通过 / 0 不通过", "type": "string"},
        "risk_list": arr("risk_list", "风险清单，pass=0 时非空", {
            "risk_type": {"description": "风险类型：overlap_conflict/negative_fee/boundary_price_gap/overlay_limit_exceeded/custom_rule", "type": "string"},
            "risk_desc": {"description": "风险描述，含冲突/异常明细", "type": "string"},
            "suggest": {"description": "处置建议", "type": "string"},
        }),
    },
    "Y"))

# ---------------- 工具9 上线审批推送 ----------------
plugins.append(build_plugin(
    "approval-submit-0001", "上线审批推送", "submit_release_approval",
    "自研模拟实现（V1.6）：汇总测试与稽核报告生成模拟审批单号 approval_id 并写入模拟审批状态库（供工具13 query_approval_status 查询）；插件层校验 approve_confirmed==true，未经确认返回 NOT_CONFIRMED；幂等：同 product_id 重复提交返回原 approval_id",
    "/api/v1/appstore/approval/submit", "POST",
    {
        "product_id": schema_param("product_id", "string",
            "CRM 产品ID（save_product_config 出参）；缺失返回 PARAM_MISSING", True),
        "report_url": schema_param("report_url", "string",
            "上线报告内容或链接（主流程报告汇总节点 report 输出，工作流变量引用）", True),
        "approve_confirmed": schema_param("approve_confirmed", "string",
            "审批发起确认标志，非 true 返回 NOT_CONFIRMED", True, "true"),
        "approval_flow": schema_param("approval_flow", "string",
            "审批流枚举：standard/urgent，默认 standard", False, "standard", "standard,urgent"),
    },
    ["product_id", "report_url", "approve_confirmed"],
    {
        "approval_id": {"description": "审批单号", "type": "string"},
        "status": {"description": "提交状态", "type": "string"},
    },
    "N"))

# ---------------- 工具10 监控查询 ----------------
plugins.append(build_plugin(
    "monitor-query-0001", "监控查询", "query_product_monitor",
    "自研模拟实现（V1.6）：按销售品返回模拟运行指标（订单量/异常量/计费差错率/告警列表），可构造 error_count>0 演示告警分支；模拟数据兼容18个销售品",
    "/api/v1/appstore/product/monitor", "GET",
    {
        "product_id": schema_param("product_id", "string",
            "要查询的销售品ID；缺失返回 PARAM_MISSING", True),
        "date_range": schema_param("date_range", "string",
            "日期范围，如 2026-09-11~2026-09-12，默认最近1天", False),
        "metric": schema_param("metric", "string",
            "指标枚举：order/error/fee/all，默认 all", False, "all", "order,error,fee,all"),
    },
    ["product_id"],
    {
        "order_count": {"description": "订单量", "type": "string"},
        "error_count": {"description": "异常量", "type": "string"},
        "fee_error_rate": {"description": "计费差错率", "type": "string"},
        "alarm_list": arr("alarm_list", "已产生告警列表", {
            "alarm_id": {"description": "告警单号", "type": "string"},
            "alarm_level": {"description": "告警级别：high/middle/low", "type": "string"},
            "content": {"description": "告警内容", "type": "string"},
            "alarm_time": {"description": "告警时间", "type": "string"},
        }),
    },
    "Y"))

# ---------------- 工具11 异常告警 ----------------
plugins.append(build_plugin(
    "alert-send-0001", "异常告警", "send_alert",
    "自研模拟实现（V1.6）：生成模拟告警单号 alert_id 并返回推送成功状态；告警记录写入模拟库供监控查询回显闭环",
    "/api/v1/appstore/alert/send", "POST",
    {
        "product_id": schema_param("product_id", "string",
            "告警关联销售品ID；缺失返回 PARAM_MISSING", True),
        "alarm_level": schema_param("alarm_level", "string",
            "告警级别枚举：high/middle/low；缺失返回 PARAM_MISSING", True, "", "high,middle,low"),
        "content": schema_param("content", "string",
            "告警正文（含环节、问题描述、建议），由大模型节点生成，工作流变量引用", True),
    },
    ["product_id", "alarm_level", "content"],
    {
        "alert_id": {"description": "告警单号", "type": "string"},
        "status": {"description": "推送状态", "type": "string"},
    },
    "N"))

# ---------------- 工具13 审批进度查询 ----------------
plugins.append(build_plugin(
    "approval-status-0001", "审批进度查询", "query_approval_status",
    "自研模拟实现（V1.6）：从模拟审批状态库（工具9 写入）按 approval_id 或 product_id 查询审批单当前状态（审批中/通过/驳回）、当前审批环节与意见，支撑用户消息查询审批进度",
    "/api/v1/appstore/approval/status", "GET",
    {
        "approval_id": schema_param("approval_id", "string",
            "审批单号（submit_release_approval 出参），优先使用；与 product_id 至少一个非空，均为空返回 PARAM_MISSING", False),
        "product_id": schema_param("product_id", "string",
            "产品ID，缺失 approval_id 时按其查最新审批单；与 approval_id 至少一个非空", False),
    },
    [],
    {
        "approval_id": {"description": "审批单号", "type": "string"},
        "status": {"description": "审批状态：审批中 / 通过 / 驳回", "type": "string"},
        "current_node": {"description": "当前审批环节（如：产品经理审核/部门主管审批）", "type": "string"},
        "approver": {"description": "当前审批人", "type": "string"},
        "opinion": {"description": "审批意见（最近一条）", "type": "string"},
        "submit_time": {"description": "提交时间", "type": "string"},
        "update_time": {"description": "最近更新时间", "type": "string"},
    },
    "Y"))

# ---------------- 节点结果存储（平台复用，重新导出对齐V1.6口径） ----------------
def build_storage_plugin(tool_id, tool_name, tool_code, desc, path, method, req_props, req_required, resp_props, induction):
    export = build_plugin(tool_id, tool_name, tool_code, desc, path, method,
                          req_props, req_required, resp_props, induction)
    export["pluginName"] = "节点结果存储查询插件"
    export["remarks"] = "平台已有通用插件，直接挂载，不自研；本导出对齐 req_id+node_name 存取契约"
    return export

plugins.append(build_storage_plugin(
    "node-result-save-0001", "节点结果存储", "save_node_result",
    "平台复用插件：按需求单号+环节名存储工作流节点结果JSON（同键覆盖，支持重跑环节）。执行方案环节：req_id=plan_id（PLAN+yyyyMMdd+3位序号）、node_name=requirement；执行主干各环节：req_id=execution_id（EXE+yyyyMMddHHmmss+2位序号）、node_name=config/spec/fee/test",
    "/api/v1/appstore/result/save", "POST",
    {
        "req_id": schema_param("req_id", "string",
            "需求唯一标识；执行方案环节=plan_id，执行主干环节=execution_id；非法格式返回 5002", True),
        "node_name": schema_param("node_name", "string",
            "环节名：requirement/config/spec/fee/test；为空返回 5003", True),
        "result_json": schema_param("result_json", "string",
            "本环节结果JSON字符串，最大64KB，超限返回 5004", True),
        "status": schema_param("status", "string",
            "本环节状态，默认 ok", False, "ok"),
    },
    ["req_id", "node_name", "result_json"],
    {
        "code": {"description": "统一状态码，0 成功", "type": "string"},
        "msg": {"description": "状态描述", "type": "string"},
        "record_id": {"description": "存储记录ID", "type": "string"},
    },
    "N"))

plugins.append(build_storage_plugin(
    "node-result-query-0001", "节点结果查询", "query_node_result",
    "平台复用插件：按需求单号（+环节名可选）查询工作流节点结果JSON原文。智能配置环节按 req_id=plan_id、node_name=requirement 取回执行方案JSON（list[0].result_json）后原样透传 save_product_config（中间禁止大模型二次加工）；续跑时按 req_id=execution_id 回放已成功环节结果",
    "/api/v1/appstore/result/query", "GET",
    {
        "req_id": schema_param("req_id", "string",
            "需求唯一标识；执行方案环节=plan_id，执行主干环节=execution_id；非法格式返回 5002", True),
        "node_name": schema_param("node_name", "string",
            "环节名（可选）：requirement/config/spec/fee/test；为空返回该需求单号下全部环节最新记录", False),
        "latest_only": schema_param("latest_only", "string",
            "1=只返回每个环节最新一条（默认）；0=返回历史全部版本", False, "1", "0,1"),
    },
    ["req_id"],
    {
        "code": {"description": "统一状态码，0 成功", "type": "string"},
        "msg": {"description": "状态描述", "type": "string"},
        "total": {"description": "命中记录数", "type": "string"},
        "list": {"description": "记录数组JSON（每条含 record_id/req_id/node_name/result_json/status/create_time/update_time；执行方案取 list[0].result_json）", "type": "string"},
    },
    "Y"))

filenames = {
    "query_similar_offer": "工具1_相似度分析_export.json",
    "realtime_spec_audit": "工具2_实时规格稽核_export.json",
    "offer_test": "工具3_销售品测试发起_export.json",
    "get_test_scenes": "工具4_查询测试场景_export.json",
    "get_test_progress": "工具5_查询测试进度_export.json",
    "get_test_result": "工具6_查询测试结果_export.json",
    "save_product_config": "工具7_配置落地_export.json",
    "check_billing_rule": "工具8_计费规则校验_export.json",
    "submit_release_approval": "工具9_上线审批推送_export.json",
    "query_product_monitor": "工具10_监控查询_export.json",
    "send_alert": "工具11_异常告警_export.json",
    "query_approval_status": "工具13_审批进度查询_export.json",
    "save_node_result": "节点结果存储_export_V1.6.json",
    "query_node_result": "节点结果查询_export_V1.6.json",
}

for p in plugins:
    fn = filenames[p["toolCode"]]
    with io.open(os.path.join(BASE, fn), "w", encoding="utf-8") as f:
        json.dump(p, f, ensure_ascii=False, indent=2)
    print("written:", fn)

print("total:", len(plugins))
