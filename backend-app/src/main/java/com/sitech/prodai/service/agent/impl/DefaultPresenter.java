package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.Presenter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 翻译层可调用的真实工具白名单（与理解层同源语义：防 LLM 编造能力）已收敛至 AgentCapabilityRegistry（工具 getScenes() 自声明）。 */

    private final LlmService llmService;

    /** 已注册工具索引：工具名 → 工具（跟进话术守门：能力承接关系与场景白名单校验） */
    private final Map<String, AgentTool> toolMap;

    /** 能力注册表（单源）：场景 → 可见工具白名单，工具自声明场景后统一读取。 */
    private final com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry capabilityRegistry;

    public DefaultPresenter(LlmService llmService, List<AgentTool> tools,
                            com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry capabilityRegistry) {
        this.llmService = llmService;
        this.toolMap = new LinkedHashMap<>();
        this.capabilityRegistry = capabilityRegistry;
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
            }
        }
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
        // U3 假设透明回显：系统自行推断的取值须在结论中明示，履行回显义务（方案 11.6(c)）
        String assumptionsPrompt = assumptionsPrompt(context);
        if (!assumptionsPrompt.isEmpty()) {
            prompt.append(assumptionsPrompt);
        }

        // 大模型生成最终报告。不可用/返回为空时抛错，不做 fallback 兜底。
        String report = llmService.completePrompt(prompt.toString());
        requireNonBlank(report, "大模型返回为空，无法生成分析报告");
        return report;
    }

    /**
     * 生成澄清追问文案（CLARIFY 分支，设计文档 3.4 节）。
     * <p>
     * 追问文案由 LLM 基于参数契约（业务名 + 说明）生成自然语言（AI 原生：怎么问由 LLM 定）；
     * LLM 不可用/返回为空时回退一句话模板"请补充：{参数业务名}"（确定性守门）。
     */
    private String buildClarifyMessage(SessionContext context) {
        List<String> clarify = context != null ? context.getLastClarifyParams() : null;
        if (clarify == null || clarify.isEmpty()) {
            return "请问您想分析哪个商品/套餐？";
        }
        String generated = llmClarifyMessage(clarify);
        if (generated != null && !generated.isBlank()) {
            return generated.trim();
        }
        StringBuilder sb = new StringBuilder("为了继续处理您的请求，请补充以下信息：\n");
        for (String param : clarify) {
            sb.append("· ").append(businessNameOf(param)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * LLM 生成澄清追问话术：把"缺哪些参数"问得像人话（语言生成为 LLM 本场）。
     * 参数说明来自澄清参数的业务名映射，LLM 失败时返回 null 由调用方回退模板。
     */
    private String llmClarifyMessage(List<String> params) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("用户请求缺少必要信息，需要向用户追问补充。缺失的参数：\n");
            for (String param : params) {
                sb.append("- ").append(businessNameOf(param)).append("\n");
            }
            sb.append("\n请用一句自然、友好的中文向用户追问这些信息，说明为什么需要。")
                    .append("只输出追问话术本身，不要输出其他内容。");
            return llmService.completePrompt(sb.toString());
        } catch (Exception e) {
            log.warn("[DefaultPresenter] 澄清追问文案 LLM 生成失败，回退模板: {}", e.getMessage());
            return null;
        }
    }

    /** 参数内部名 → 业务展示名（通用映射：camelCase/下划线拆词，中文业务名场景由 LLM 生成覆盖）。 */
    private String businessNameOf(String param) {
        if (param == null || param.isBlank()) {
            return param;
        }
        String[] parts = param.replaceAll("([a-z])([A-Z])", "$1 $2").split("[_\\s]+");
        return String.join(" ", parts);
    }

    /**
     * 跟进话术生成（方案 11.2 触点④：任务链感知的下一步）。
     * <p>
     * 优先 LLM 基于"用户问题 + 本轮工具结果 + 工具描述声明的业务承接关系"生成
     * 业务承接话术（归因→建单、稽核→导出、对比→写回草稿等真实业务衔接，而非通用三句）；
     * 守门校验话术所指工具在本场景白名单内（合法能力清单由场景已知工具推导，同理解层
     * sanitizeTools 语义），非法候选剔除；LLM 不可用/合法候选不足时回退
     * 原工具分支词典（@deprecated 过渡保留）。
     */
    @Override
    public List<String> suggestFollowUps(String question, List<ExecutionResult> results, SessionContext context) {
        List<String> generated = llmFollowUps(question, results, context);
        if (generated != null && !generated.isEmpty()) {
            return generated;
        }

        List<String> suggestions = new ArrayList<>();

        // 回退：按工具结果生成建议（词典分支 @deprecated，随 LLM 化稳定后移除）
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

    /**
     * LLM 生成任务链感知的跟进话术：基于本轮结果建议"下一个业务动作"。
     * <p>
     * prompt 注入本轮工具执行结果与场景内全部能力清单（名称 + 描述 + 声明的入参业务含义，
     * 即工具自描述的承接关系），要求每条话术指明承接的工具；守门只保留所指工具在场景
     * 白名单内的候选（防 LLM 编造能力，与理解层白名单语义一致）。
     * 输出解析为 2~3 条短话术；LLM 失败/全部候选被剔除时返回 null（调用方回退词典）。
     */
    private List<String> llmFollowUps(String question, List<ExecutionResult> results, SessionContext context) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        List<AgentTool> allowedTools = allowedToolsOf(context);
        if (allowedTools.isEmpty()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("用户问题：").append(question).append("\n本轮执行结果摘要：\n");
            for (ExecutionResult r : results) {
                sb.append("- ").append(r.getToolName()).append("：")
                        .append(r.isSuccess() ? "成功" : "失败").append('\n');
            }
            sb.append("\n系统当前具备的后续业务能力（话术建议必须承接其中之一，不得虚构其他能力）：\n");
            for (AgentTool tool : allowedTools) {
                sb.append("- ").append(tool.getName()).append("：").append(tool.getDescription()).append('\n');
            }
            sb.append("\n请基于该结果，建议用户接下来最自然的 2~3 个业务动作（如归因后建议建单、")
                    .append("稽核后建议导出清单、对比后建议采用某方案）。")
                    .append("每条一句话、面向业务人员、可直接作为消息发送，且须承接上面列出的某项能力。")
                    .append("\n仅输出 JSON 数组：[{\"text\": \"话术1\", \"tool\": \"承接的工具名\"}, {\"text\": \"话术2\", \"tool\": \"承接的工具名\"}]");
            String raw = llmService.completePrompt(sb.toString());
            if (raw == null || raw.isBlank()) {
                return null;
            }
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return null;
            }
            List<Map<String, Object>> list = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    raw.substring(start, end + 1),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            Set<String> allowedNames = new LinkedHashSet<>();
            for (AgentTool tool : allowedTools) {
                allowedNames.add(tool.getName());
            }
            List<String> out = new ArrayList<>();
            for (Map<String, Object> item : list) {
                if (item == null || out.size() >= 3) {
                    continue;
                }
                Object text = item.get("text");
                Object tool = item.get("tool");
                // 守门：话术所指工具必须在场景白名单内，非法候选剔除（防幻觉能力混入执行链入口）
                if (text instanceof String s && !s.isBlank()
                        && tool instanceof String t && allowedNames.contains(t.trim())) {
                    out.add(s.trim());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[DefaultPresenter] 跟进话术 LLM 生成失败，回退词典: {}", e.getMessage());
            return null;
        }
    }

    /** 场景白名单内的已注册工具（能力清单来源同理解层：工具自声明场景 + 注册表统一读取）。 */
    private List<AgentTool> allowedToolsOf(SessionContext context) {
        boolean rdScene = context != null && "rd".equals(context.getScene());
        String scene = rdScene ? "rd" : com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry.DEFAULT_SCENE;
        List<AgentTool> out = new ArrayList<>();
        for (AgentTool tool : capabilityRegistry.toolsOf(scene)) {
            if (toolMap.containsKey(tool.getName())) {
                out.add(tool);
            }
        }
        return out;
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

    /**
     * U3 假设透明回显：会话中存在系统自行推断的取值时，生成 prompt 注入段，
     * 要求结论正文明示假设（"本次按 [参数=值] 推断执行，如不符请说明"）。
     */
    private String assumptionsPrompt(SessionContext context) {
        List<Map<String, Object>> assumptions = context != null ? context.getAssumptions() : null;
        if (assumptions == null || assumptions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n注意：以下取值为系统自行推断（用户未明确指定），请在结论开头用一句话向用户说明这些推断前提，")
                .append("格式如「本次按 [时间范围=本月] 推断执行，如不符请告诉我」，并列出全部推断项：\n");
        for (Map<String, Object> a : assumptions) {
            sb.append("- ").append(a.get("param")).append("=").append(a.get("value"));
            Object reason = a.get("reason");
            if (reason != null && !String.valueOf(reason).isBlank()) {
                sb.append("（").append(reason).append("）");
            }
            sb.append('\n');
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