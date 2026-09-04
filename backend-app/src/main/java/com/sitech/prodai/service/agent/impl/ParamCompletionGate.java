package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数补全门（R2 拆分：原 DefaultUnderstander.validateParams 及其辅助方法）。
 * <p>
 * 参数完整性校验（设计文档 3.4 节）：
 * 校验优先级：params 已填 → context.cachedEvidence / resolvedParams 缓存 → defaultValue。
 * 仍缺失的必填参数 → 生成 CLARIFY 意图；超过澄清上限则按缺省值继续（防死循环）。
 */
@Component
public class ParamCompletionGate {

    private static final Logger log = LoggerFactory.getLogger(ParamCompletionGate.class);

    /**
     * 参数完整性校验：回填缓存/缺省值，仍缺失则生成 CLARIFY 澄清计划。
     *
     * @param toolMap  工具注册表（工具名 → 工具定义），用于读取必填参数契约
     * @param plan     待校验的查询计划（回填结果写回 plan.params）
     * @param context  会话上下文（可为 null，测试/评测场景）
     * @param question 用户原始问题（CLARIFY 计划透传）
     * @return 校验后的计划；缺失必填参数时返回 INTENT_CLARIFY 计划
     */
    public QueryPlan validateParams(Map<String, AgentTool> toolMap, QueryPlan plan,
                                    SessionContext context, String question) {
        List<String> tools = plan.getTools();
        if (tools == null || tools.isEmpty()) {
            return plan;
        }

        List<String> missing = new ArrayList<>();
        Map<String, Map<String, Object>> missingContracts = new LinkedHashMap<>();
        List<String> fromCache = new ArrayList<>();
        List<String> fromDefault = new ArrayList<>();
        for (String toolName : tools) {
            AgentTool tool = toolMap.get(toolName);
            if (tool == null) {
                continue;
            }
            for (ToolParam param : tool.getParams()) {
                if (!param.isRequired()) {
                    continue;
                }
                if (hasValue(plan.getParams().get(param.getName()))) {
                    continue;
                }
                // 缓存优先级：resolvedParams（用户已补齐） > cachedEvidence（上轮证据）
                Object cached = context != null ? context.getResolvedParams().get(param.getName()) : null;
                if (!hasValue(cached) && context != null) {
                    cached = context.getCachedEvidence().get(param.getName());
                }
                if (hasValue(cached)) {
                    plan.getParams().put(param.getName(), cached);
                    fromCache.add(paramDisplay(param));
                    continue;
                }
                // 有缺省值则不阻塞（U3：缺省回填属系统自行推断，记录假设供表达层回显）
                if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
                    plan.getParams().put(param.getName(), param.getDefaultValue());
                    if (context != null) {
                        context.recordAssumption(param.getName(), param.getDefaultValue(), "未指定，按缺省值推断");
                    }
                    fromDefault.add(paramDisplay(param));
                    continue;
                }
                if (!missing.contains(param.getName())) {
                    missing.add(param.getName());
                    missingContracts.put(param.getName(), paramContract(param));
                }
            }
        }
        // 推理留痕：参数回填来源（缓存复用 / 缺省推断），让数据流可追溯
        if (!fromCache.isEmpty()) {
            plan.addTrace("params", "复用会话中已确认的参数：" + String.join("、", fromCache));
        }
        if (!fromDefault.isEmpty()) {
            plan.addTrace("params", "未指定的参数按缺省值推断：" + String.join("、", fromDefault));
        }

        if (missing.isEmpty()) {
            if (context != null) {
                context.resetClarifyRounds();
                // 本轮参数齐备：清空上一轮遗留假设，避免过期假设污染本轮结论
                context.clearAssumptions();
            }
            return plan;
        }


        // 超过澄清上限：按缺省值继续（缺省缺失时放弃该参数），防死循环（U3：明示该假设）
        if (context != null && context.exceedClarifyLimit()) {
            log.info("[ParamCompletionGate] 澄清轮次已达上限，按缺省继续: {}", missing);
            context.resetClarifyRounds();
            for (String name : missing) {
                context.recordAssumption(name, "（未提供）", "澄清超限，未按该参数过滤结果");
            }
            return plan;
        }
        if (context != null) {
            context.incrementClarifyRounds();
        }

        // 生成 CLARIFY 澄清计划
        QueryPlan clarifyPlan = new QueryPlan();
        clarifyPlan.setIntent(QueryPlan.INTENT_CLARIFY);
        clarifyPlan.setTools(List.of());
        clarifyPlan.setClarify(missing);
        clarifyPlan.setClarifyContracts(missingContracts);
        clarifyPlan.setParams(new LinkedHashMap<>(plan.getParams()));
        clarifyPlan.setUserQuestion(question);
        log.info("[ParamCompletionGate] 必填参数缺失，生成澄清计划: {}", missing);
        return clarifyPlan;
    }

    /** 参数展示名：优先业务 label，无则用参数名。 */
    private String paramDisplay(ToolParam param) {
        return param.getLabel() != null && !param.getLabel().isBlank()
                ? param.getLabel() : param.getName();
    }

    /** 缺失参数的展示契约：业务名 / 说明 / 候选选项（供前端渲染选择题补参）。 */
    private Map<String, Object> paramContract(ToolParam param) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (param.getLabel() != null && !param.getLabel().isBlank()) {
            m.put("label", param.getLabel());
        }
        if (param.getDescription() != null && !param.getDescription().isBlank()) {
            m.put("description", param.getDescription());
        }
        if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
            m.put("options", param.getEnumValues());
        }
        return m;
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value);
        return !s.isBlank() && !"null".equals(s);
    }
}
