package com.sitech.prodai.service.flow.definitions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智聊场景工作流定义常量（W3-2 + W5）：chat_configure_v2 主流程 + draft_and_check_v2 子流程
 * + query_reuse_v2 查询复用 + ops_analysis_v2 运营问诊。
 * <p>
 * 定义依据：《智聊场景重设计方案》§4.1/§7.2，结合引擎现实约束（§4.2 勘误）：
 * <ul>
 *   <li>human 节点不放子流程内——workflow 节点要求子流程终态=completed，挂起会断裂链路；</li>
 *   <li>整改回环用线性展开（fix-redraft → compliance-recheck），规避子流程防环重入拒绝；</li>
 *   <li>落库确认门（confirm-gate）放在主流程，挂起态可直接被 ChatHumanBridge 恢复。</li>
 * </ul>
 * W5（query/ops 扶正）：两场景均为纯查询分析链路，无人工节点；按用户决策采用
 * <b>全部 fail-fast</b>（任一工具失败即整单失败，不做 onFailure=continue 容错），
 * 语义最严格，失败根因直接呈现在 error_message。
 */
final class SceneWorkflowDefinitions {

    /** 主流程编码（ProdAiProperties.ChatWorkflow.workflowCode 对应）。 */
    static final String MAIN_CODE = "chat_configure_v2";
    /** 子流程编码：起草 + 合规校验 + 线性整改。 */
    static final String DRAFT_CHECK_CODE = "draft_and_check_v2";
    /** 查询复用流程编码（query 场景，方案 §7.2 扶正）。 */
    static final String QUERY_REUSE_CODE = "query_reuse_v2";
    /** 运营问诊流程编码（ops 场景）。 */
    static final String OPS_ANALYSIS_CODE = "ops_analysis_v2";

    private static final long TOOL_TIMEOUT_MS = 20_000L;
    private static final long LLM_TIMEOUT_MS = 60_000L;

    private SceneWorkflowDefinitions() {
    }

    /** 全部场景工作流编码（Seeder 逐个落库 / 测试逐个断言）。 */
    static List<String> allCodes() {
        return List.of(MAIN_CODE, DRAFT_CHECK_CODE, QUERY_REUSE_CODE, OPS_ANALYSIS_CODE);
    }

    /** 按编码取定义（测试与 Seeder 共用唯一入口）。 */
    static Map<String, Object> definition(String code) {
        if (MAIN_CODE.equals(code)) {
            return mainFlow();
        }
        if (DRAFT_CHECK_CODE.equals(code)) {
            return draftAndCheck();
        }
        if (QUERY_REUSE_CODE.equals(code)) {
            return queryReuse();
        }
        if (OPS_ANALYSIS_CODE.equals(code)) {
            return opsAnalysis();
        }
        throw new IllegalArgumentException("未知的场景工作流编码: " + code);
    }

    static Map<String, Object> mainFlow() {
        Map<String, Object> discover = DefinitionTemplates.node("discover", "flow.tool", "智查历史配置",
                new LinkedHashMap<>(Map.of(
                        "toolName", "rd_config_discover",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("question", "{{flow.question}}"),
                                DefinitionTemplates.input("limit", 5)),
                        "onFailure", "continue",
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("history_items", "items"),
                                DefinitionTemplates.output("discover_answer", "nl_answer")))));

        Map<String, Object> reuseCheck = DefinitionTemplates.node("reuse-check", "flow.condition", "复用判定",
                Map.of("branches", DefinitionTemplates.listOf(
                        DefinitionTemplates.branch("reuse", "${flow.reuse_hint} == 'yes'", "复用历史方案"),
                        DefinitionTemplates.branch("draft", "default", "新起草"))));

        Map<String, Object> draftStage = DefinitionTemplates.node("draft-stage", "flow.workflow", "起草并合规校验",
                Map.of("workflow_ref", DRAFT_CHECK_CODE,
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("question", "{{flow.question}}"),
                                DefinitionTemplates.input("history_items", "{{discover.output.history_items}}"),
                                DefinitionTemplates.input("confirmed_params", "{{flow.confirmed_params}}")),
                        "timeoutMs", 120_000L));

        Map<String, Object> confirmGate = DefinitionTemplates.node("confirm-gate", "flow.human", "落库确认",
                Map.of("form_code", "offering_config",
                        "prompt", "配置草稿已生成，请确认后落库提交（可取消）"));

        Map<String, Object> confirmCheck = DefinitionTemplates.node("confirm-check", "flow.condition", "确认判定",
                Map.of("branches", DefinitionTemplates.listOf(
                        DefinitionTemplates.branch("cancel", "${confirm-gate.output.action} == 'cancel'", "用户取消"),
                        DefinitionTemplates.branch("proceed", "default", "确认提交"))));

        Map<String, Object> persist = DefinitionTemplates.node("persist", "flow.tool", "修改草稿",
                new LinkedHashMap<>(Map.of(
                        "toolName", "rd_draft_manage",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("action", "update"),
                                DefinitionTemplates.input("work_order_id", "{{confirm-gate.output.work_order_id}}"),
                                DefinitionTemplates.input("offering_name", "{{confirm-gate.output.offerName}}"),
                                DefinitionTemplates.input("monthly_fee", "{{confirm-gate.output.fixedFeeAmount}}"),
                                DefinitionTemplates.input("question", "{{flow.question}}")),
                        "onFailure", "continue",
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("manage_answer", "nl_answer"),
                                DefinitionTemplates.output("manage_success", "success")))));

        Map<String, Object> order = DefinitionTemplates.node("order", "flow.tool", "提交工单",
                new LinkedHashMap<>(Map.of(
                        "toolName", "rd_draft_manage",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("action", "submit"),
                                DefinitionTemplates.input("work_order_id", "{{confirm-gate.output.work_order_id}}"),
                                DefinitionTemplates.input("session_id", "{{system.execution_id}}")),
                        "onFailure", "continue",
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("order_answer", "nl_answer")))));

        List<Map<String, Object>> nodes = DefinitionTemplates.listOf(
                DefinitionTemplates.node("start", "flow.start", "开始", Map.of()),
                discover,
                reuseCheck,
                draftStage,
                confirmGate,
                confirmCheck,
                persist,
                order,
                DefinitionTemplates.node("end-main", "flow.end", "配置完成", Map.of()),
                DefinitionTemplates.node("end-reuse", "flow.end", "复用历史", Map.of()),
                DefinitionTemplates.node("end-cancelled", "flow.end", "用户取消", Map.of()));

        List<Map<String, Object>> connections = DefinitionTemplates.listOf(
                DefinitionTemplates.edge("start", "discover"),
                DefinitionTemplates.edge("discover", "reuse-check"),
                DefinitionTemplates.branchEdge("reuse-check", "reuse", "end-reuse"),
                DefinitionTemplates.branchEdge("reuse-check", "draft", "draft-stage"),
                DefinitionTemplates.edge("draft-stage", "confirm-gate"),
                DefinitionTemplates.edge("confirm-gate", "confirm-check"),
                DefinitionTemplates.branchEdge("confirm-check", "cancel", "end-cancelled"),
                DefinitionTemplates.branchEdge("confirm-check", "proceed", "persist"),
                DefinitionTemplates.edge("persist", "order"),
                DefinitionTemplates.edge("order", "end-main"));

        return DefinitionTemplates.definition(MAIN_CODE, "智聊配置生成 v2", nodes, connections);
    }

    /**
     * 子流程：LLM 起草 → 合规校验 → 不通过线性整改一次 → 复检；二次仍不过走 rejected 终态
     * （子流程终态仍为 completed，主流程 workflow 节点不断裂，合规状态由确认门呈现）。
     */
    static Map<String, Object> draftAndCheck() {
        Map<String, Object> draftLlm = DefinitionTemplates.node("draft-llm", "flow.llm", "LLM 起草配置",
                Map.of(
                        "prompt", DRAFT_PROMPT,
                        "system_prompt", DRAFT_SYSTEM,
                        "response_format", "json",
                        "json_schema", Map.of(
                                "required", List.of("offering_name", "monthly_fee", "target_user", "config"),
                                "properties", Map.of(
                                        "offering_name", Map.of("type", "string"),
                                        "monthly_fee", Map.of("type", "number"),
                                        "target_user", Map.of("type", "string"),
                                        "config", Map.of("type", "object"))),
                        "output_mode", "flatten",
                        "timeoutMs", LLM_TIMEOUT_MS,
                        "retry", Map.of("maxAttempts", 2)));

        Map<String, Object> compliance = DefinitionTemplates.node("compliance", "flow.tool", "合规校验",
                Map.of(
                        "toolName", "rd_compliance",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("draft", "{{draft-llm.output.response_json}}"),
                                DefinitionTemplates.input("text", "{{flow.question}}")),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("compliance_pass", "compliance_pass"),
                                DefinitionTemplates.output("issues", "issues"),
                                DefinitionTemplates.output("checked_draft", "draft"))));

        Map<String, Object> passCheck = DefinitionTemplates.node("pass-check", "flow.condition", "合规判定",
                Map.of("branches", DefinitionTemplates.listOf(
                        DefinitionTemplates.branch("fix", "${compliance.output.compliance_pass} == false", "整改重起草"),
                        DefinitionTemplates.branch("ok", "default", "合规通过"))));

        Map<String, Object> fixRedraft = DefinitionTemplates.node("fix-redraft", "flow.llm", "按风险整改",
                Map.of(
                        "prompt", FIX_PROMPT,
                        "system_prompt", DRAFT_SYSTEM,
                        "response_format", "json",
                        "json_schema", Map.of(
                                "required", List.of("offering_name", "monthly_fee", "target_user", "config"),
                                "properties", Map.of(
                                        "offering_name", Map.of("type", "string"),
                                        "monthly_fee", Map.of("type", "number"),
                                        "target_user", Map.of("type", "string"),
                                        "config", Map.of("type", "object"))),
                        "output_mode", "flatten",
                        "timeoutMs", LLM_TIMEOUT_MS,
                        "retry", Map.of("maxAttempts", 1)));

        Map<String, Object> complianceRecheck = DefinitionTemplates.node("compliance-recheck", "flow.tool", "整改复检",
                Map.of(
                        "toolName", "rd_compliance",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("draft", "{{fix-redraft.output.response_json}}"),
                                DefinitionTemplates.input("text", "{{flow.question}}")),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("compliance_pass", "compliance_pass"),
                                DefinitionTemplates.output("issues", "issues"),
                                DefinitionTemplates.output("checked_draft", "draft"))));

        Map<String, Object> passCheckRecheck = DefinitionTemplates.node("pass-check-recheck", "flow.condition", "复检判定",
                Map.of("branches", DefinitionTemplates.listOf(
                        DefinitionTemplates.branch("fix-again", "${compliance-recheck.output.compliance_pass} == false", "整改仍未过"),
                        DefinitionTemplates.branch("ok", "default", "复检通过"))));

        List<Map<String, Object>> nodes = DefinitionTemplates.listOf(
                DefinitionTemplates.node("start", "flow.start", "开始", Map.of()),
                draftLlm,
                compliance,
                passCheck,
                fixRedraft,
                complianceRecheck,
                passCheckRecheck,
                DefinitionTemplates.node("end-draft-ok", "flow.end", "起草完成", Map.of()),
                DefinitionTemplates.node("end-draft-rejected", "flow.end", "整改未过", Map.of()));

        List<Map<String, Object>> connections = DefinitionTemplates.listOf(
                DefinitionTemplates.edge("start", "draft-llm"),
                DefinitionTemplates.edge("draft-llm", "compliance"),
                DefinitionTemplates.edge("compliance", "pass-check"),
                DefinitionTemplates.branchEdge("pass-check", "fix", "fix-redraft"),
                DefinitionTemplates.branchEdge("pass-check", "ok", "end-draft-ok"),
                DefinitionTemplates.edge("fix-redraft", "compliance-recheck"),
                DefinitionTemplates.edge("compliance-recheck", "pass-check-recheck"),
                DefinitionTemplates.branchEdge("pass-check-recheck", "fix-again", "end-draft-rejected"),
                DefinitionTemplates.branchEdge("pass-check-recheck", "ok", "end-draft-ok"));

        return DefinitionTemplates.definition(DRAFT_CHECK_CODE, "配置起草合规校验 v2", nodes, connections);
    }

    private static final String DRAFT_SYSTEM =
            "你是电信产商品配置专家。根据用户需求起草套餐配置，只输出 JSON 对象，不要输出其他内容。";

    private static final String DRAFT_PROMPT =
            "用户需求：{{flow.question}}\n"
                    + "已确认要素：{{flow.confirmed_params}}\n"
                    + "历史参考：{{flow.history_items}}\n"
                    + "请起草配置，输出 JSON：{offering_name: 资费名称, monthly_fee: 月费数字, "
                    + "target_user: 目标用户, config: {categoryCode: 品类码, messageRootKey: 报文根键, "
                    + "channelScope: 渠道, fixedFeeAmount: 固费}}";

    private static final String FIX_PROMPT =
            "用户需求：{{flow.question}}\n"
                    + "初稿：{{draft-llm.output.response_json}}\n"
                    + "合规风险：{{compliance.output.issues}}\n"
                    + "请修正全部风险项后重新输出 JSON，结构与初稿一致。";

    /**
     * 查询复用流程（W5，方案 §7.2 query 场景扶正）：
     * 历史检索 → 知识库查询 → 方案对比，全部 fail-fast（任一失败即整单失败）。
     * <p>
     * 线性链路无分支：discover 与 sparql 提供两条互补的证据获取通道（历史配置库 / RDF 知识库），
     * compare 汇总两者做对比推荐；end 节点透传 flow.output 供结论摘要。
     */
    static Map<String, Object> queryReuse() {
        Map<String, Object> discover = DefinitionTemplates.node("discover", "flow.tool", "检索历史配置",
                Map.of(
                        "toolName", "rd_config_discover",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("question", "{{flow.question}}"),
                                DefinitionTemplates.input("limit", 5)),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("history_items", "items"),
                                DefinitionTemplates.output("discover_answer", "nl_answer"))));

        Map<String, Object> sparql = DefinitionTemplates.node("sparql", "flow.tool", "知识库查询",
                Map.of(
                        "toolName", "sparql_query",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("question", "{{flow.question}}"),
                                DefinitionTemplates.input("maxEntities", 20)),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("facts", "raw_results"),
                                DefinitionTemplates.output("sparql_answer", "nl_answer"))));

        Map<String, Object> compare = DefinitionTemplates.node("compare", "flow.tool", "方案对比",
                Map.of(
                        "toolName", "rd_scheme_compare",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("text", "{{flow.question}}"),
                                DefinitionTemplates.input("draft", "{{discover.output.history_items}}"),
                                DefinitionTemplates.input("patches", "{{sparql.output.facts}}")),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("comparisons", "comparisons"),
                                DefinitionTemplates.output("recommended", "recommended"),
                                DefinitionTemplates.output("compare_answer", "nl_answer"))));

        List<Map<String, Object>> nodes = DefinitionTemplates.listOf(
                DefinitionTemplates.node("start", "flow.start", "开始", Map.of()),
                discover,
                sparql,
                compare,
                DefinitionTemplates.node("end-query", "flow.end", "查询完成", Map.of()));

        List<Map<String, Object>> connections = DefinitionTemplates.listOf(
                DefinitionTemplates.edge("start", "discover"),
                DefinitionTemplates.edge("discover", "sparql"),
                DefinitionTemplates.edge("sparql", "compare"),
                DefinitionTemplates.edge("compare", "end-query"));

        return DefinitionTemplates.definition(QUERY_REUSE_CODE, "查询复用 v2", nodes, connections);
    }

    /**
     * 运营问诊流程（W5，方案 §4.1 ops 分析问诊）：知识库查询 → 根因归因 → 风险稽核 → 本体解释，
     * 全部 fail-fast（任一失败即整单失败）。
     * <p>
     * 线性链路：sparql 先取事实数据，root-cause 基于问题语义归因，risk-audit 全量筛查风险，
     * explain 补充本体概念解释；无人工节点，completed 即产出完整问诊结论。
     */
    static Map<String, Object> opsAnalysis() {
        Map<String, Object> sparql = DefinitionTemplates.node("sparql", "flow.tool", "事实数据查询",
                Map.of(
                        "toolName", "sparql_query",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("question", "{{flow.question}}"),
                                DefinitionTemplates.input("maxEntities", 20)),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("facts", "raw_results"),
                                DefinitionTemplates.output("sparql_answer", "nl_answer"))));

        Map<String, Object> rootCause = DefinitionTemplates.node("root-cause", "flow.tool", "根因归因",
                Map.of(
                        "toolName", "swrl_root_cause",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("offering", "{{sparql.output.facts}}"),
                                DefinitionTemplates.input("question", "{{flow.question}}")),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("root_cause", "nl_answer"))));

        Map<String, Object> riskAudit = DefinitionTemplates.node("risk-audit", "flow.tool", "风险稽核",
                Map.of(
                        "toolName", "swrl_risk_audit",
                        "inputParams", DefinitionTemplates.listOf(),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("risk_items", "items"),
                                DefinitionTemplates.output("risk_answer", "nl_answer"))));

        Map<String, Object> explain = DefinitionTemplates.node("explain", "flow.tool", "本体解释",
                Map.of(
                        "toolName", "ontology_explain",
                        "inputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.input("concept", "{{flow.question}}")),
                        "timeoutMs", TOOL_TIMEOUT_MS,
                        "outputParams", DefinitionTemplates.listOf(
                                DefinitionTemplates.output("explain_answer", "natural_language"))));

        List<Map<String, Object>> nodes = DefinitionTemplates.listOf(
                DefinitionTemplates.node("start", "flow.start", "开始", Map.of()),
                sparql,
                rootCause,
                riskAudit,
                explain,
                DefinitionTemplates.node("end-ops", "flow.end", "问诊完成", Map.of()));

        List<Map<String, Object>> connections = DefinitionTemplates.listOf(
                DefinitionTemplates.edge("start", "sparql"),
                DefinitionTemplates.edge("sparql", "root-cause"),
                DefinitionTemplates.edge("root-cause", "risk-audit"),
                DefinitionTemplates.edge("risk-audit", "explain"),
                DefinitionTemplates.edge("explain", "end-ops"));

        return DefinitionTemplates.definition(OPS_ANALYSIS_CODE, "运营问诊 v2", nodes, connections);
    }
}
