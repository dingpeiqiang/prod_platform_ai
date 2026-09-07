package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景工作流路由器（智聊重设计 W3-1，方案 §7.2）—— 路由层的确定性代码。
 * <p>
 * 输入 = 理解层产物 QueryPlan + 会话场景；判定顺序（短路）：
 * <ol>
 *   <li>plan.intent=CLARIFY/CONFIRM/REUSE_EVIDENCE → 不路由（对话编排既有协议轮）</li>
 *   <li>场景未配置工作流（{@code prodai.chat-workflow.scene-workflows}）→ 不路由</li>
 *   <li>命中 → 引擎 startExecution（版本锁定），组装与 FlowIntentRouter 同构的回复</li>
 * </ol>
 * 挂起态短路（判定 0：executionBinding 存在 → resume）由编排器在进入理解层之前完成，
 * 本类职责收敛为「QueryPlan → workflow_code」映射，保持确定性、可审计（LLM 只在节点内）。
 * <p>
 * 去旧留新（W3-2）：路由恒启用（原 enabled Feature Flag 已移除）；
 * 兜底语义 = 场景未配置 / 引擎失败 → 调用方回落 LLM 动态编排。
 */
@Component
public class SceneFlowRouter {

    private static final Logger log = LoggerFactory.getLogger(SceneFlowRouter.class);

    private final ProdAiProperties properties;
    private final FlowEngineService flowEngineService;

    public SceneFlowRouter(ProdAiProperties properties, FlowEngineService flowEngineService) {
        this.properties = properties;
        this.flowEngineService = flowEngineService;
    }

    /**
     * 尝试把理解层计划路由到场景工作流执行。
     *
     * @param plan    理解层 QueryPlan（意图码 = rd 工具名大写 / ops 意图标签）
     * @param context 会话上下文（取 scene 场景键）
     * @param user    触发人
     * @return 命中并执行 → 与 FlowIntentRouter 回复同构的 Map（intent=FLOW_EXEC）；
     *         未命中 → Optional.empty()（调用方走动态编排）
     */
    public java.util.Optional<Map<String, Object>> tryRoute(QueryPlan plan, SessionContext context, String user) {
        String workflowCode = resolveWorkflowCode(plan, context);
        if (workflowCode == null) {
            return java.util.Optional.empty();
        }
        String scene = context == null ? "" : String.valueOf(context.getScene());
        log.info("[SceneFlowRouter] 命中场景工作流: scene={} intent={} workflow={}",
                scene, plan.getIntent(), workflowCode);

        Map<String, Object> inputData = buildInputData(plan);
        ApiResponse<Map<String, Object>> resp = flowEngineService.startExecution(workflowCode, null, inputData, user);
        return java.util.Optional.of(buildReply(workflowCode, plan, resp));
    }

    /** 确定性映射：场景配置命中且 plan 为可执行意图 → workflow_code；否则 null。 */
    private String resolveWorkflowCode(QueryPlan plan, SessionContext context) {
        if (plan == null || context == null) {
            return null;
        }
        // 对话协议轮不进工作流：澄清/确认/证据复用是编排层与用户的往返，非一次执行
        String intent = plan.getIntent();
        if (QueryPlan.INTENT_CLARIFY.equals(intent) || QueryPlan.INTENT_CONFIRM.equals(intent)
                || QueryPlan.INTENT_REUSE_EVIDENCE.equals(intent)) {
            return null;
        }
        if (plan.getTools() == null || plan.getTools().isEmpty()) {
            return null;
        }
        // 智读文件解析不进 chat_configure_v2 固化链路：该工作流是「单草稿起草→合规→落库」链路，
        // 无法承载批量文档解析（每条草稿一单）；RD_FILE_PARSE 走动态编排直达 rd_file_parse 工具
        if ("RD_FILE_PARSE".equals(intent) || plan.getTools().contains("rd_file_parse")) {
            return null;
        }
        return properties.getChatWorkflow().workflowFor(context.getScene());
    }

    /** 流程入参：QueryPlan 参数 + 会话已澄清参数透传（起草节点承接）。 */
    private Map<String, Object> buildInputData(QueryPlan plan) {
        Map<String, Object> inputData = new LinkedHashMap<>();
        if (plan.getParams() != null) {
            inputData.putAll(plan.getParams());
        }
        inputData.putIfAbsent("question", plan.getUserQuestion() == null ? "" : plan.getUserQuestion());
        return inputData;
    }

    /** 引擎结果 → 对话回复（与 FlowIntentRouter.buildReply 同构，前端零新增分支）。 */
    private Map<String, Object> buildReply(String workflowCode, QueryPlan plan,
                                           ApiResponse<Map<String, Object>> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", "FLOW_EXEC");
        out.put("flow_matched", Map.of(
                "workflow_code", workflowCode,
                "display_name", workflowCode,
                "hit_keyword", "scene:" + plan.getIntent()));
        if (resp == null || !resp.isSuccess()) {
            String reason = resp == null ? "引擎无响应" : resp.getMessage();
            out.put("report", "场景工作流「" + workflowCode + "」执行失败：" + reason);
            out.put("conclusion", "");
            out.put("flow_execution", Map.of("status", "failed", "error_message", reason));
            return out;
        }
        Map<String, Object> data = resp.getData();
        String status = String.valueOf(data.getOrDefault("status", "unknown"));
        out.put("flow_execution", data);
        out.put("session_id", data.get("execution_id"));
        if ("completed".equals(status)) {
            out.put("report", "场景工作流已执行完成，耗时详情见执行明细。");
            out.put("conclusion", buildConclusion(data));
        } else if ("waiting_human".equals(status)) {
            out.put("report", "流程在人工节点暂停，请在对话中回复确认（执行 ID：" + data.get("execution_id") + "）。");
            out.put("conclusion", "");
        } else {
            out.put("report", "场景工作流执行状态：" + status
                    + (data.get("error_message") == null ? "" : "，错误：" + data.get("error_message")));
            out.put("conclusion", "");
        }
        out.put("suggested_follow_ups", java.util.List.of("查看执行明细"));
        return out;
    }

    /** 结论摘要：flow.output（end 节点透传）→ output_data → 各节点输出概要。 */
    private String buildConclusion(Map<String, Object> data) {
        Object output = data.get("output_data");
        if (output instanceof Map<?, ?> m && !m.isEmpty()) {
            Object flowScope = m.get("flow");
            if (flowScope instanceof Map<?, ?> fs && fs.get("output") != null) {
                return String.valueOf(fs.get("output"));
            }
            return String.valueOf(m);
        }
        Object context = data.get("context_data");
        if (context instanceof Map<?, ?> cm && !cm.isEmpty()) {
            return "各节点输出：" + cm;
        }
        return "";
    }
}
