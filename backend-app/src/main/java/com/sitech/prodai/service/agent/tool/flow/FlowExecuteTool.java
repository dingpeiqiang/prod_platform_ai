package com.sitech.prodai.service.agent.tool.flow;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行已发布的固定业务流程（工作流引擎对话侧工具）。
 * <p>
 * 让 LLM 在理解层从「已发布工作流清单」中选定 workflow_code 后经本工具进入
 * FlowEngineService——与关键词路由（FlowIntentRouter）双轨互补：
 * 关键词直达为快路径，本工具为语义兜底慢路径（长尾自然语言）。
 * <p>
 * 确定性锚点：workflow_code + 引擎版本锁定（startExecution 保证同版本同路径），
 * 不违反「路由层确定性」铁律——LLM 只做能力选择，不做流程内判定。
 * <p>
 * 依赖注入说明：FlowEngineService → ToolExecutionService → List&lt;AgentTool&gt;（含本工具）
 * 构成构造期环，故用 ObjectProvider 延迟解析引擎（运行期首次 execute 时才 getIfAvailable）。
 */
@Component
public class FlowExecuteTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FlowExecuteTool.class);

    private final ObjectProvider<FlowEngineService> flowEngineServiceProvider;

    public FlowExecuteTool(ObjectProvider<FlowEngineService> flowEngineServiceProvider) {
        this.flowEngineServiceProvider = flowEngineServiceProvider;
    }

    @Override
    public String getName() {
        return "flow_execute";
    }

    @Override
    public String getDescription() {
        return "执行已发布的固定业务流程（如审批、稽核、下架/提交等流转类流程），需提供流程编码 workflow_code 与流程入参 input_data；仅可执行已发布（激活）的工作流";
    }

    @Override
    public String getLabel() {
        return "执行固定流程";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("ops", "rd");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("workflow_code")
                        .label("流程编码")
                        .description("已发布工作流的编码（workflow_code），必须来自能力清单中列出的流程，严禁编造")
                        .type("string")
                        .required()
                        .build(),
                ToolParam.builder("input_data")
                        .label("流程入参")
                        .description("流程开始节点的结构化入参（对象，可选）；未提供的字段由流程定义的默认值承接")
                        .type("object")
                        .build(),
                ToolParam.builder("question")
                        .label("原始需求")
                        .description("用户原始话术（透传给流程做审计留痕）")
                        .type("string")
                        .source("question")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("执行摘要").type("string")
                        .description("流程执行结果摘要").build(),
                ToolOutputField.builder("status", ToolOutputField.Role.OTHER)
                        .label("执行状态").type("string")
                        .description("completed / waiting_human / failed / running").build(),
                ToolOutputField.builder("execution_id", ToolOutputField.Role.BUSINESS_ENTITY_ID)
                        .label("执行 ID").type("string")
                        .description("流程执行实例 ID，可用于续跑/取消/查明细").build(),
                ToolOutputField.builder("conclusion", ToolOutputField.Role.CONCLUSION)
                        .label("流程结论").type("string")
                        .description("end 节点透传的流程输出结论").build(),
                ToolOutputField.builder("flow_execution", ToolOutputField.Role.OTHER)
                        .label("执行明细").type("object")
                        .description("引擎返回的完整执行明细（节点输出/耗时/错误信息）").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String workflowCode = params != null ? String.valueOf(params.getOrDefault("workflow_code", "")).trim() : "";
        if (workflowCode.isEmpty() || "null".equalsIgnoreCase(workflowCode)) {
            return ExecutionResult.fail(getName(), "缺少流程编码 workflow_code（请从可用流程清单中选择）");
        }
        Object question = params != null ? params.get("question") : null;

        Map<String, Object> inputData = new LinkedHashMap<>();
        Object rawData = params != null ? params.get("input_data") : null;
        if (rawData instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    inputData.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        if (question != null && !String.valueOf(question).isBlank()) {
            inputData.putIfAbsent("question", String.valueOf(question));
        }

        log.info("[AgentTool] flow_execute 执行: workflowCode={}, inputKeys={}",
                workflowCode, inputData.keySet());
        try {
            FlowEngineService flowEngineService = flowEngineServiceProvider.getIfAvailable();
            if (flowEngineService == null) {
                return ExecutionResult.fail(getName(), "流程引擎不可用");
            }
            ApiResponse<Map<String, Object>> resp =
                    flowEngineService.startExecution(workflowCode, null, inputData, null);
            if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                String reason = resp == null ? "流程引擎无响应" : resp.getMessage();
                log.warn("[AgentTool] flow_execute 失败: workflowCode={}, reason={}", workflowCode, reason);
                return ExecutionResult.fail(getName(), "流程「" + workflowCode + "」执行失败：" + reason);
            }
            return ExecutionResult.ok(getName(), normalize(resp.getData(), workflowCode));
        } catch (Exception e) {
            log.error("[AgentTool] flow_execute 异常: workflowCode={}", workflowCode, e);
            return ExecutionResult.fail(getName(), "流程执行异常: " + e.getMessage());
        }
    }

    /** 引擎响应 → 工具输出契约（summary/conclusion/businessEntity 供编排层通用渲染）。 */
    private Map<String, Object> normalize(Map<String, Object> data, String workflowCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        String status = String.valueOf(data.getOrDefault("status", "unknown"));
        out.put("status", status);
        out.put("execution_id", data.get("execution_id"));
        out.put("flow_execution", data);

        String conclusion = extractConclusion(data);
        out.put("conclusion", conclusion);
        out.put("nl_answer", "流程「" + workflowCode + "」" + statusText(status, data) + conclusion);
        return out;
    }

    private String statusText(String status, Map<String, Object> data) {
        return switch (status) {
            case "completed" -> "已执行完成。";
            case "waiting_human" -> "在人工节点暂停（执行 ID：" + data.get("execution_id") + "），请到工作流编辑器中继续处理。";
            case "failed" -> "执行失败" + (data.get("error_message") == null ? "" : "：" + data.get("error_message"));
            default -> "执行状态：" + status;
        };
    }

    /** 结论摘要：flow.output（end 节点透传）优先，回落节点输出概要。 */
    private String extractConclusion(Map<String, Object> data) {
        Object output = data.get("output_data");
        if (output instanceof Map<?, ?> m) {
            Object flowScope = m.get("flow");
            if (flowScope instanceof Map<?, ?> fs && fs.get("output") != null) {
                return String.valueOf(fs.get("output"));
            }
        }
        return "";
    }
}
