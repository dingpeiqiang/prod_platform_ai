package com.sitech.prodai.service.flow.definitions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景工作流定义常量（W5 存量）：query_reuse_v2 查询复用。
 * <p>
 * 去旧留新：智聊主流程 chat_configure_v2 / 子流程 draft_and_check_v2 已删除——
 * rd 场景三大主意图（RD_CONFIG_CHAT/RD_FILE_PARSE/RD_CONFIG_DISCOVER）现全部命中
 * 手册层（chat-configure/doc-batch-import/discover-history），手册路由优先于场景工作流，
 * 固化工作流被架空。人工门（human gate）执行机制保留在引擎
 * （FlowEngineService/ChatHumanBridge），供手册未来挂载与用户自建流程使用。
 * <p>
 * 去旧留新（运营问诊手册化）：ops_analysis_v2 已删除——ops 场景主链路收拢到
 * 手册层四本入口手册（playbooks/market-insight|root-cause|risk-audit|online-check.yaml）。
 * <p>
 * W5（query 存量）：纯查询分析链路，无人工节点；按用户决策采用
 * <b>全部 fail-fast</b>（任一工具失败即整单失败，不做 onFailure=continue 容错），
 * 语义最严格，失败根因直接呈现在 error_message。
 */
final class SceneWorkflowDefinitions {

    /** 查询复用流程编码（query 场景，方案 §7.2 扶正）。 */
    static final String QUERY_REUSE_CODE = "query_reuse_v2";

    private static final long TOOL_TIMEOUT_MS = 20_000L;

    private SceneWorkflowDefinitions() {
    }

    /** 全部场景工作流编码（Seeder 逐个落库 / 测试逐个断言）。 */
    static List<String> allCodes() {
        return List.of(QUERY_REUSE_CODE);
    }

    /** 按编码取定义（测试与 Seeder 共用唯一入口）。 */
    static Map<String, Object> definition(String code) {
        if (QUERY_REUSE_CODE.equals(code)) {
            return queryReuse();
        }
        throw new IllegalArgumentException("未知的场景工作流编码: " + code);
    }

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
}
