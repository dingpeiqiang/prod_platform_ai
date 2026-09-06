package com.sitech.prodai.service.agent.bridge;

import com.sitech.prodai.common.ApiResponse;
import com.sitech.prodai.service.flow.FlowEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话内 human 挂起桥接器（智聊重设计 W2-1）。
 * <p>
 * 职责一（挂起侧）：把引擎挂起响应（execution_map + form_spec）翻译为对话侧绑定信息
 * {@code SessionContext.executionBinding} 与前端可渲染的 clarify_contracts 契约
 * （{param: {label, description, options}}，前端零新组件）。用户视角只有一种交互：
 * 对话中回复文字或点击内联表单。
 * <p>
 * 职责二（恢复侧）：用户下一轮回复时，文本经轻量意图判定映射为表单值
 * （"确认"→confirmed=true，"改成 49 元"→回填参数），调
 * {@link FlowEngineService#resumeFromHuman}。挂起态的回复语义由流程定义，无歧义，
 * 不再过理解层 LLM（跨轮上下文不依赖内存 TTL——挂起态全量持久化于执行实例）。
 */
@Component
public class ChatHumanBridge {

    private static final Logger log = LoggerFactory.getLogger(ChatHumanBridge.class);

    /** FlowEngineService 仅需 resumeFromHuman 一个方法（窄接口，测试易替换）。 */
    private final FlowEngineService flowEngineService;

    public ChatHumanBridge(FlowEngineService flowEngineService) {
        this.flowEngineService = flowEngineService;
    }

    /**
     * 挂起翻译：引擎执行 Map（status=waiting_human）→ 对话绑定信息。
     *
     * @param executionMap 引擎执行实例视图（executionToMap + form_spec 注入后的形态）
     * @return executionBinding（execution_id/resume_token/node_id/form_code/form_spec...）；
     *         非挂起态或缺令牌时返回 null（不绑定，走常规链路）
     */
    public Map<String, Object> buildBinding(Map<String, Object> executionMap) {
        if (executionMap == null || !"waiting_human".equals(executionMap.get("status"))) {
            return null;
        }
        String executionId = str(executionMap.get("execution_id"));
        String resumeToken = str(executionMap.get("resume_token"));
        if (executionId == null || resumeToken == null) {
            log.warn("[ChatHumanBridge] 挂起响应缺 execution_id/resume_token，无法绑定会话");
            return null;
        }
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("execution_id", executionId);
        binding.put("resume_token", resumeToken);
        binding.put("node_id", str(executionMap.get("current_node_id")));
        binding.put("workflow_code", str(executionMap.get("workflow_code")));
        if (executionMap.get("form_spec") instanceof Map<?, ?> spec) {
            binding.put("form_code", str(spec.get("form_code")));
            binding.put("form_spec", spec);
        }
        binding.put("suspended_at", LocalDateTime.now().toString());
        return binding;
    }

    /**
     * form_spec → clarify_contracts 翻译：字段定义 [{field_code, field_name, field_type, required,
     * options...}] → {field_code: {label, description, options}}（与 QueryPlan.clarifyContracts 同构，
     * 前端 InlineFormEditor 零改动渲染）。
     *
     * @param formSpec 引擎表单规格（form_code + fields），可为 null（通用确认门）
     * @return 契约 Map；无字段时返回空 Map（前端退化为纯文字确认）
     */
    public Map<String, Map<String, Object>> toClarifyContracts(Map<String, Object> formSpec) {
        Map<String, Map<String, Object>> contracts = new LinkedHashMap<>();
        if (!(formSpec != null && formSpec.get("fields") instanceof List<?> fields)) {
            return contracts;
        }
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field)) {
                continue;
            }
            String code = firstNonBlank(field.get("field_code"), field.get("fieldCode"));
            if (code == null) {
                continue;
            }
            Map<String, Object> contract = new LinkedHashMap<>();
            String label = firstNonBlank(field.get("field_name"), field.get("fieldName"));
            if (label != null) {
                contract.put("label", label);
            }
            String description = firstNonBlank(field.get("description"), field.get("placeholder"));
            if (description != null) {
                contract.put("description", description);
            }
            Object options = field.get("options");
            if (options instanceof List<?> optionList && !optionList.isEmpty()) {
                contract.put("options", optionList);
            }
            contract.put("required", Boolean.TRUE.equals(field.get("required")));
            contracts.put(code, contract);
        }
        return contracts;
    }

    /**
     * 恢复执行：用户回复文本 + 表单数据 → 轻量意图映射 → 引擎人工节点恢复。
     *
     * @param binding  会话绑定信息（buildBinding 产物）
     * @param question 用户回复文本（可为 null——纯表单提交场景）
     * @param formData 前端内联表单结构化数据（可为 null——纯文本回复场景）
     * @param user     触发人
     * @return 引擎恢复结果（data.status=completed/failed/waiting_human）；binding 为 null 时返回 null
     */
    public ApiResponse<Map<String, Object>> resume(Map<String, Object> binding, String question,
                                                   Map<String, Object> formData, String user) {
        if (binding == null) {
            return null;
        }
        String executionId = str(binding.get("execution_id"));
        String resumeToken = str(binding.get("resume_token"));
        Map<String, Object> merged = mergeFormValues(binding, question, formData);
        log.info("[ChatHumanBridge] 对话内恢复人工节点: execution={} node={} keys={}",
                executionId, binding.get("node_id"), merged.keySet());
        return flowEngineService.resumeFromHuman(executionId, resumeToken, merged, user);
    }

    /**
     * 表单值合并：结构化 formData 优先，用户文本经意图映射回填缺失键
     * （"确认落库"→confirmed=true，"取消"→action=cancel；键值型补参原样回填）。
     */
    private Map<String, Object> mergeFormValues(Map<String, Object> binding, String question,
                                                Map<String, Object> formData) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (formData != null) {
            merged.putAll(formData);
        }
        if (question == null || question.isBlank()) {
            return merged;
        }
        applyTextIntent(merged, question);
        return merged;
    }

    /** 轻量文本意图映射（确定性关键词，无 LLM——挂起态语义由流程定义）。 */
    private void applyTextIntent(Map<String, Object> merged, String question) {
        String lowered = question.trim().toLowerCase();
        // 布尔确认键回填：表单含 confirmed 类字段时，确认/取消文案映射为 true/false
        if (merged.containsKey("confirmed") && !hasValue(merged.get("confirmed"))) {
            if (containsAny(lowered, List.of("确认", "同意", "ok", "可以", "通过", "confirm"))) {
                merged.put("confirmed", true);
                return;
            }
            if (containsAny(lowered, List.of("取消", "不用", "放弃", "cancel"))) {
                merged.put("confirmed", false);
                return;
            }
        }
        // 取消意图：任何确认门均响应"取消/放弃"
        if (containsAny(lowered, List.of("取消", "放弃", "不要了"))) {
            merged.putIfAbsent("action", "cancel");
            return;
        }
        // 兜底：确认语义置 action=confirm（字段级精确回填由流程定义解析 formData）
        if (containsAny(lowered, List.of("确认", "同意", "通过", "confirm"))) {
            merged.putIfAbsent("action", "confirm");
        }
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private boolean hasValue(Object v) {
        return v != null && !String.valueOf(v).isBlank();
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 取首个非空字符串（兼容 snake_case/camelCase 字段键）。 */
    private String firstNonBlank(Object... candidates) {
        for (Object c : candidates) {
            if (c != null && !String.valueOf(c).isBlank()) {
                return String.valueOf(c);
            }
        }
        return null;
    }
}
