package com.sitech.prodai.service.agent.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程意图路由器（业务场景接入 S1）—— 固定流程引擎的对话侧入口。
 * <p>
 * 职责：把用户自然语言映射到已发布的固定流程（workflow_code），命中即调引擎执行，
 * 未命中返回 empty 让编排器走原 LLM 链路（双轨并行、灰度接管，方案 §8 原则）。
 * <p>
 * 铁律边界（方案 §12.2）：路由是纯配置匹配（关键词注册表），不做自由文本 LLM 判定——
 * LLM 只在流程节点内参与（铁律二），路由层保持确定性、可审计。
 * <p>
 * 注册表来源：内存注册（flow_router_register 工具语义），后续可切 DB 配置表无需改调用方。
 */
@Component
public class FlowIntentRouter {

    private static final Logger log = LoggerFactory.getLogger(FlowIntentRouter.class);

    /** 触发规则：关键词任一命中即路由到 workflowCode（keywords 全小写匹配）。 */
    public record FlowRoute(String workflowCode, String displayName, List<String> keywords) {
    }

    private final Map<String, FlowRoute> routes = new ConcurrentHashMap<>();
    private final FlowEngineService flowEngineService;

    public FlowIntentRouter(FlowEngineService flowEngineService) {
        this.flowEngineService = flowEngineService;
    }

    /** 注册流程路由（发布流程后调用；重复注册同 code 覆盖）。 */
    public void register(String workflowCode, String displayName, List<String> keywords) {
        if (workflowCode == null || workflowCode.isBlank()
                || keywords == null || keywords.isEmpty()) {
            return;
        }
        routes.put(workflowCode, new FlowRoute(workflowCode, displayName, keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::toLowerCase)
                .toList()));
        log.info("[FlowIntentRouter] 注册流程路由: {} keywords={}", workflowCode, keywords);
    }

    public void unregister(String workflowCode) {
        routes.remove(workflowCode);
    }

    public Map<String, FlowRoute> listRoutes() {
        return Map.copyOf(routes);
    }

    /**
     * 尝试路由执行。
     *
     * @param question  用户输入
     * @param params    对话携带的结构化参数（作为流程 input_data 透传）
     * @param user      触发人
     * @return 命中并已执行 → 引擎响应的组装结果；未命中 → Optional.empty()（走原链路）
     */
    public java.util.Optional<Map<String, Object>> tryRoute(String question,
                                                            Map<String, Object> params, String user) {
        if (question == null || question.isBlank() || routes.isEmpty()) {
            return java.util.Optional.empty();
        }
        String lowered = question.toLowerCase();
        FlowRoute matched = null;
        String hitKeyword = null;
        for (FlowRoute route : routes.values()) {
            for (String keyword : route.keywords()) {
                if (lowered.contains(keyword)) {
                    matched = route;
                    hitKeyword = keyword;
                    break;
                }
            }
            if (matched != null) break;
        }
        if (matched == null) {
            return java.util.Optional.empty();
        }

        log.info("[FlowIntentRouter] 命中流程路由: {} (keyword={}), question={}",
                matched.workflowCode(), hitKeyword, question);
        Map<String, Object> inputData = new LinkedHashMap<>();
        if (params != null) {
            inputData.putAll(params);
        }
        inputData.putIfAbsent("question", question);

        ApiResponse<Map<String, Object>> resp =
                flowEngineService.startExecution(matched.workflowCode(), null, inputData, user);
        return java.util.Optional.of(buildReply(matched, resp, hitKeyword));
    }

    /** 引擎结果 → 对话回复（与编排器响应体同构：report/conclusion/intent/flow_execution）。 */
    private Map<String, Object> buildReply(FlowRoute route, ApiResponse<Map<String, Object>> resp,
                                           String hitKeyword) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", "FLOW_EXEC");
        out.put("flow_matched", Map.of(
                "workflow_code", route.workflowCode(),
                "display_name", route.displayName() == null ? route.workflowCode() : route.displayName(),
                "hit_keyword", hitKeyword == null ? "" : hitKeyword));
        if (resp == null || !resp.isSuccess()) {
            String reason = resp == null ? "引擎无响应" : resp.getMessage();
            out.put("report", "流程「" + displayName(route) + "」执行失败：" + reason);
            out.put("conclusion", "");
            out.put("flow_execution", Map.of("status", "failed", "error_message", reason));
            return out;
        }
        Map<String, Object> data = resp.getData();
        String status = String.valueOf(data.getOrDefault("status", "unknown"));
        out.put("flow_execution", data);
        out.put("session_id", data.get("execution_id"));
        if ("completed".equals(status)) {
            out.put("report", "流程「" + displayName(route) + "」已执行完成，耗时详情见执行明细。");
            out.put("conclusion", buildConclusion(data));
        } else if ("waiting_human".equals(status)) {
            out.put("report", "流程「" + displayName(route) + "」在人工节点暂停，请到工作流编辑器中继续处理（执行 ID："
                    + data.get("execution_id") + "）。");
            out.put("conclusion", "");
        } else {
            out.put("report", "流程「" + displayName(route) + "」执行状态：" + status
                    + (data.get("error_message") == null ? "" : "，错误：" + data.get("error_message")));
            out.put("conclusion", "");
        }
        out.put("suggested_follow_ups", List.of("查看执行明细", "重新执行该流程"));
        return out;
    }

    /** 结论摘要：flow.output（end 节点透传）+ 各节点输出概要。 */
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

    private String displayName(FlowRoute route) {
        return route.displayName() == null || route.displayName().isBlank()
                ? route.workflowCode() : route.displayName();
    }
}
