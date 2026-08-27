package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Presenter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
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
 * <p>
 * 特殊分支（设计文档 3.4 / 3.6 节）：
 * - CLARIFY 意图：生成澄清追问文案（不调用工具）
 * - 部分工具失败：基于成功结果生成部分结论 + 失败原因说明
 */
@Component
public class DefaultPresenter implements Presenter {

    private static final Logger log = LoggerFactory.getLogger(DefaultPresenter.class);

    /** 澄清参数的追问文案模板：参数名 → {业务展示名, 示例} */
    private static final Map<String, String[]> CLARIFY_LABELS = Map.of(
            "offering", new String[]{"商品/套餐", "例如：5G套餐、家庭融合畅享128"},
            "ruleId", new String[]{"规则编号", "例如：R-A01、R-B02"},
            "concept", new String[]{"本体概念", "例如：商品、渠道、订购"},
            "question", new String[]{"查询内容", "请描述您想查询的内容"}
    );

    private final LlmService llmService;

    public DefaultPresenter(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String present(String question, List<ExecutionResult> results, SessionContext context) {
        if (context != null && QueryPlan.INTENT_CLARIFY.equals(context.getLastIntent())) {
            return buildClarifyMessage(context);
        }

        if (results == null || results.isEmpty()) {
            // 无工具调用，直接 LLM 回复。大模型不可用/返回为空时抛错，不做兜底。
            String reply = llmService.completeMessages(
                    "你是一个智能助手，请友好地回答用户的问题。",
                    toHistory(context),
                    question
            );
            requireNonBlank(reply, "大模型返回为空，无法回答该问题");
            return reply;
        }

        // 部分失败场景：成功结果生成部分结论 + 失败说明
        boolean hasFailure = results.stream().anyMatch(r -> !r.isSuccess());
        boolean hasSuccess = results.stream().anyMatch(ExecutionResult::isSuccess);
        if (hasFailure) {
            log.info("[DefaultPresenter] 存在失败工具（成功={}，失败={}），将生成部分结论", hasSuccess, hasFailure);
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
        if (hasFailure) {
            prompt.append("\n注意：部分工具执行失败，请先基于成功的结果给出部分结论，")
                    .append("再简要说明哪些环节失败、可能原因与建议（不要夸大失败影响）。");
        }

        // 大模型生成最终报告。不可用/返回为空时抛错，不做 fallback 兜底。
        String report = llmService.completePrompt(prompt.toString());
        requireNonBlank(report, "大模型返回为空，无法生成分析报告");
        return report;
    }

    /**
     * 生成澄清追问文案（CLARIFY 分支，设计文档 3.4 节）。
     */
    private String buildClarifyMessage(SessionContext context) {
        List<String> clarify = context != null ? context.getLastClarifyParams() : null;
        if (clarify == null || clarify.isEmpty()) {
            return "请问您想分析哪个商品/套餐？例如：5G套餐、家庭融合畅享128";
        }
        StringBuilder sb = new StringBuilder("为了继续处理您的请求，请补充以下信息：\n");
        for (String param : clarify) {
            String[] label = CLARIFY_LABELS.getOrDefault(param, new String[]{param, ""});
            sb.append("· ").append(label[0]);
            if (!label[1].isEmpty()) {
                sb.append("（").append(label[1]).append("）");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public List<String> suggestFollowUps(String question, List<ExecutionResult> results) {
        List<String> suggestions = new ArrayList<>();

        // 根据工具执行结果生成追问建议
        if (results != null) {
            for (ExecutionResult result : results) {
                if (!result.isSuccess()) {
                    suggestions.add("重试刚才的查询");
                    continue;
                }
                if ("swrl_root_cause".equals(result.getToolName())) {
                    suggestions.add("具体哪个渠道影响最大？");
                    suggestions.add("和上月对比呢？");
                    suggestions.add("生成产品优化工单");
                    break;
                }
                if ("swrl_risk_audit".equals(result.getToolName())) {
                    suggestions.add("查看高风险商品详情");
                    suggestions.add("导出风险报告");
                    suggestions.add("发起批量下架流程");
                    break;
                }
                if ("sparql_query".equals(result.getToolName())) {
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

    /**
     * 校验 LLM 输出非空；为空时抛错（去兜底，大模型不可用/无输出即报错）。
     */
    private void requireNonBlank(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(message);
        }
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