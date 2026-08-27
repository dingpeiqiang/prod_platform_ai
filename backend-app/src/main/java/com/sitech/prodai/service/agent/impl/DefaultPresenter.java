package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Presenter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认表达层实现。
 * <p>
 * 将工具执行结果翻译为自然语言，并生成追问建议。
 */
@Component
public class DefaultPresenter implements Presenter {

    private static final Logger log = LoggerFactory.getLogger(DefaultPresenter.class);

    private final LlmService llmService;

    public DefaultPresenter(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String present(String question, List<ExecutionResult> results, SessionContext context) {
        if (results == null || results.isEmpty()) {
            // 无工具调用，直接 LLM 回复
            try {
                return llmService.completeMessages(
                        "你是一个智能助手，请友好地回答用户的问题。",
                        toHistory(context),
                        question
                );
            } catch (Exception e) {
                log.warn("[DefaultPresenter] LLM 回复失败: {}", e.getMessage());
                return "抱歉，我暂时无法回答这个问题，请稍后再试。";
            }
        }

        // 构建提示词，将工具执行结果提供给 LLM
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个业务分析助手，请根据以下工具执行结果，用自然语言回答用户的问题。\n\n");
        prompt.append("用户问题：").append(question).append("\n\n");

        for (ExecutionResult result : results) {
            prompt.append("工具：").append(result.getToolName()).append("\n");
            prompt.append("状态：").append(result.isSuccess() ? "成功" : "失败").append("\n");
            if (result.isSuccess() && result.getData() != null) {
                prompt.append("结果：").append(formatData(result.getData())).append("\n");
            }
            if (!result.isSuccess() && result.getErrorMessage() != null) {
                prompt.append("错误：").append(result.getErrorMessage()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("请用中文回答，语言简洁明了，重点突出。如果涉及数据，请用具体数字说明。");

        try {
            return llmService.completePrompt(prompt.toString());
        } catch (Exception e) {
            log.warn("[DefaultPresenter] LLM 报告生成失败: {}", e.getMessage());
            return buildFallbackReport(question, results);
        }
    }

    @Override
    public List<String> suggestFollowUps(String question, List<ExecutionResult> results) {
        List<String> suggestions = new ArrayList<>();

        // 根据工具执行结果生成追问建议
        if (results != null) {
            for (ExecutionResult result : results) {
                if (result.isSuccess() && "swrl_root_cause".equals(result.getToolName())) {
                    suggestions.add("具体哪个渠道影响最大？");
                    suggestions.add("和上月对比呢？");
                    suggestions.add("生成产品优化工单");
                    break;
                }
                if (result.isSuccess() && "swrl_risk_audit".equals(result.getToolName())) {
                    suggestions.add("查看高风险商品详情");
                    suggestions.add("导出风险报告");
                    suggestions.add("发起批量下架流程");
                    break;
                }
                if (result.isSuccess() && "sparql_query".equals(result.getToolName())) {
                    suggestions.add("查看详细数据");
                    suggestions.add("分析变化趋势");
                    suggestions.add("导出数据报表");
                    break;
                }
            }
        }

        // 兜底建议
        if (suggestions.isEmpty()) {
            suggestions.add("查看其他相关数据");
            suggestions.add("切换分析视角");
        }

        return suggestions;
    }

    @SuppressWarnings("unchecked")
    private String formatData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "（无数据）";
        StringBuilder sb = new StringBuilder();

        // 优先输出关键字段
        for (String key : List.of("nl_answer", "answer", "summary", "message", "conclusion")) {
            if (data.containsKey(key)) {
                sb.append(String.valueOf(data.get(key))).append("\n");
            }
        }

        // 输出结果数量
        if (data.containsKey("entity_count")) {
            sb.append("涉及实体数：").append(data.get("entity_count")).append("\n");
        }
        if (data.containsKey("triggeredRules") || data.containsKey("triggered_rules")) {
            Object rules = data.getOrDefault("triggeredRules", data.get("triggered_rules"));
            sb.append("触发规则数：").append(rules).append("\n");
        }
        if (data.containsKey("raw_results")) {
            Object raw = data.get("raw_results");
            if (raw instanceof List<?> list) {
                sb.append("查询结果：").append(list.size()).append(" 条记录\n");
            }
        }

        return sb.length() > 0 ? sb.toString() : data.toString();
    }

    private String buildFallbackReport(String question, List<ExecutionResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("已为您完成查询分析。\n\n");

        for (ExecutionResult result : results) {
            if (result.isSuccess()) {
                sb.append("✅ ").append(result.getToolName()).append(" 执行成功");
                if (result.getData() != null && !result.getData().isEmpty()) {
                    sb.append("，返回 ").append(result.getData().size()).append(" 项数据");
                }
                sb.append("\n");
            } else {
                sb.append("❌ ").append(result.getToolName()).append(" 执行失败：")
                        .append(result.getErrorMessage()).append("\n");
            }
        }

        return sb.toString();
    }

    private List<Map<String, String>> toHistory(SessionContext context) {
        if (context == null || context.getHistory() == null) {
            return null;
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> entry : context.getHistory()) {
            Map<String, String> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : entry.entrySet()) {
                converted.put(e.getKey(), String.valueOf(e.getValue()));
            }
            result.add(converted);
        }
        return result;
    }
}