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
     * LLM 基于「用户问题 + 本轮工具结果关键内容 + 工具自声明的业务承接链（handoffs）+
     * 会话近期动作」生成业务承接话术（归因→建单、稽核→导出、对比→采用方案等真实业务衔接）；
     * 守门校验话术所指工具在本场景白名单内（防 LLM 编造能力，同理解层 sanitizeTools 语义），
     * 并排除与近期已执行动作重复的建议（防原地打转）；LLM 不可用/合法候选不足时
     * 回退承接链推导的最小建议集（确定性兜底，不再使用 @deprecated 固定词典）。
     */
    @Override
    public List<String> suggestFollowUps(String question, List<ExecutionResult> results, SessionContext context) {
        List<String> generated = llmFollowUps(question, results, context);
        if (generated != null && !generated.isEmpty()) {
            return generated;
        }

        // 确定性兜底：由本轮工具的 handoffs（业务承接链自声明）推导承接动作，
        // 无声明时按成败给通用指引（失败→修复重试，成功→继续探索）
        return fallbackFollowUps(results, context);
    }

    /**
     * 确定性兜底建议：本轮工具声明了 handoffs 时给承接话术（用工具业务标签组织），
     * 失败结果给修复性指引；无任何依据时给探索性通用建议。仍排除与上轮重复的建议。
     */
    private List<String> fallbackFollowUps(List<ExecutionResult> results, SessionContext context) {
        List<String> out = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            return List.of("查看其他相关数据", "切换分析视角");
        }
        for (ExecutionResult result : results) {
            if (!result.isSuccess()) {
                out.add("换一种说法重试刚才的操作");
                continue;
            }
            AgentTool tool = toolMap.get(result.getToolName());
            if (tool == null) {
                continue;
            }
            for (String next : tool.getHandoffs()) {
                AgentTool nextTool = toolMap.get(next);
                if (nextTool != null) {
                    out.add("接下来" + nextTool.getLabel() + "试试");
                }
            }
        }
        out.removeIf(s -> s.isBlank() || recentHistoryTexts(context).contains(s));
        if (out.isEmpty()) {
            out.add("查看其他相关数据");
        }
        return out.stream().distinct().limit(3).toList();
    }

    /**
     * LLM 生成任务链感知的跟进话术：基于本轮结果建议"下一个业务动作"。
     * <p>
     * prompt 注入：
     * <ul>
     *   <li>本轮工具执行结果（含关键输出内容摘要，建议可引用具体数字/对象，与数据强相关）</li>
     *   <li>场景内全部能力清单（名称 + 描述 + 自声明承接链 handoffs，即任务链方向）</li>
     *   <li>会话近期用户已问/系统已建议的内容（明确禁止重复，防原地打转）</li>
     * </ul>
     * 守门：话术所指工具须在场景白名单内（防幻觉）；与近期动作重复的候选剔除。
     * 剔除后不足 2 条时回喂剔除原因补位重生成一次，仍不足返回已有候选（空则 null）。
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
            List<String> recent = recentHistoryTexts(context);
            List<String> first = generateFollowUpsOnce(question, results, allowedTools, recent, null);
            if (first == null) {
                return null;
            }
            // 补位重试：守门剔除导致候选不足时，把剔除原因回喂 LLM 再生成一次
            if (first.size() < 2 && !first.isEmpty()) {
                List<String> retry = generateFollowUpsOnce(question, results, allowedTools, recent,
                        "上一轮仅产出 " + first.size() + " 条有效建议（其余因引用了清单外能力或与近期动作重复被剔除），"
                                + "请补足到 2~3 条且避免同类问题，可基于本轮结果深挖新角度。");
                if (retry != null) {
                    List<String> merged = new ArrayList<>(first);
                    for (String s : retry) {
                        if (!merged.contains(s)) {
                            merged.add(s);
                        }
                        if (merged.size() >= 3) {
                            break;
                        }
                    }
                    return merged;
                }
            }
            return first;
        } catch (Exception e) {
            log.warn("[DefaultPresenter] 跟进话术 LLM 生成失败，回退确定性兜底: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 单次 LLM 跟进话术生成与守门。retryHint 非空时为补位重试（注入剔除原因）。
     * 返回守门后的候选（可能为空列表）；LLM 调用/解析失败返回 null。
     */
    private List<String> generateFollowUpsOnce(String question, List<ExecutionResult> results,
                                               List<AgentTool> allowedTools,
                                               List<String> recent, String retryHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(question).append("\n本轮执行结果（关键内容）：\n");
        for (ExecutionResult r : results) {
            if (r.isSuccess()) {
                sb.append("- ").append(r.getToolName()).append("：成功，")
                        .append(summarizeForFollowUp(r)).append('\n');
            } else {
                sb.append("- ").append(r.getToolName()).append("：失败（")
                        .append(r.getErrorMessage() == null ? "原因未知" : r.getErrorMessage()).append("）\n");
            }
        }
        sb.append("\n系统当前具备的后续业务能力（话术建议必须承接其中之一，不得虚构其他能力）：\n");
        for (AgentTool tool : allowedTools) {
            sb.append("- ").append(tool.getName()).append("：").append(tool.getDescription());
            List<String> handoffs = tool.getHandoffs();
            if (!handoffs.isEmpty()) {
                sb.append("（典型后续动作：").append(String.join(" → ", handoffs)).append("）");
            }
            sb.append('\n');
        }
        if (!recent.isEmpty()) {
            sb.append("\n本会话近期用户已问过/系统已建议过的内容（禁止重复推荐这些动作或其同义改写）：\n");
            for (String s : recent) {
                sb.append("- ").append(s).append('\n');
            }
        }
        boolean hasFailure = results.stream().anyMatch(r -> !r.isSuccess());
        if (hasFailure) {
            sb.append("\n注意：本轮有环节执行失败，至少一条建议应针对失败给出修复路径（换说法重试/补充缺失信息/缩小范围），")
                    .append("而不是继续推进后续业务动作。");
        }
        if (retryHint != null) {
            sb.append('\n').append(retryHint).append('\n');
        }
        sb.append("\n请基于该结果，建议用户接下来最自然的 2~3 个业务动作（如归因后建议对影响最大的对象建单、")
                .append("稽核后建议导出清单、对比后建议采用推荐方案；话术应结合上面给出的结果关键内容，")
                .append("能引用具体对象/数字则引用）。")
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
        List<Map<String, Object>> list;
        try {
            list = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    raw.substring(start, end + 1),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[DefaultPresenter] 跟进话术 JSON 解析失败: {}", e.getMessage());
            return null;
        }
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
            if (!(text instanceof String s) || s.isBlank()
                    || !(tool instanceof String t) || !allowedNames.contains(t.trim())) {
                continue;
            }
            String candidate = s.trim();
            // 守门：与近期会话动作重复的候选剔除（防原地打转）
            if (isDuplicateOfRecent(candidate, recent)) {
                continue;
            }
            out.add(candidate);
        }
        return out;
    }

    /**
     * 会话近期动作文本（用户提问 + 助手结论尾部截断），供 LLM 去重参照与守门比对。
     * 只取最近 6 条、每条截断 80 字，控制 prompt 体量。
     */
    private List<String> recentHistoryTexts(SessionContext context) {
        List<Map<String, Object>> history = context != null ? context.getHistory() : null;
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - 6);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> entry : history.subList(from, history.size())) {
            Object content = entry == null ? null : entry.get("content");
            if (content == null) {
                continue;
            }
            String s = String.valueOf(content).replaceAll("\\s+", " ").trim();
            if (s.length() > 80) {
                s = s.substring(0, 80);
            }
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * 候选是否与近期会话动作重复：话术与任一近期文本包含关系命中即视为重复
     * （长度均≥6 时做包含判定，避免短词误杀）。
     */
    private boolean isDuplicateOfRecent(String candidate, List<String> recent) {
        if (candidate.length() < 6) {
            return false;
        }
        for (String s : recent) {
            if (s.length() >= 6 && (candidate.contains(s) || s.contains(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 工具结果 → 跟进建议用的关键内容摘要（一行）：优先工具自描述契约的
     * 摘要/结论/计数（ToolOutputRenderer），回落 formatData 通用关键字段提取。
     */
    private String summarizeForFollowUp(ExecutionResult result) {
        AgentTool tool = toolMap.get(result.getToolName());
        if (tool != null && result.getData() != null) {
            String summary = com.sitech.prodai.service.agent.tool.ToolOutputRenderer.summary(tool, result.getData());
            if (summary != null && !summary.isBlank() && !"执行完成".equals(summary)) {
                String conclusion = com.sitech.prodai.service.agent.tool.ToolOutputRenderer.conclusion(tool, result.getData());
                return conclusion != null && !conclusion.isBlank() && !conclusion.equals(summary)
                        ? summary + "；" + conclusion
                        : summary;
            }
        }
        String formatted = formatData(result.getData());
        String oneLine = formatted.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) : oneLine;
    }

    /**
     * 场景白名单内的已注册工具（能力清单来源同理解层：工具自声明场景 + 注册表统一读取）。
     * <p>
     * 修复（超级助手 P0）：原实现对非 rd 场景一律回落运营白名单（二值兜底），
     * 导致 query 场景在表达层丢失自有工具（如产品档案/比对）；现按会话场景透传，
     * 空白场景由 {@code AgentCapabilityRegistry#toolsOf} 归一化回落默认场景，语义不变。
     */
    private List<AgentTool> allowedToolsOf(SessionContext context) {
        String scene = context == null ? null : context.getScene();
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

        // 批量开单结果（rd_file_parse 解析即开单）：工单数与失败数是用户最关心的落地结果，
        // 必须进入 prompt 供 LLM 写进报告正文（nl_answer 摘要已含，此处确保数字随结构化指标再次强调）
        if (data.containsKey("workOrderCount")) {
            sb.append("批量创建配置工单：").append(data.get("workOrderCount")).append(" 个\n");
        }
        if (data.get("workOrderFailures") instanceof List<?> woFails && !workOrderFailsEmpty(woFails)) {
            sb.append("开单失败：").append(woFails.size()).append(" 条\n");
        }

        return sb.length() > 0 ? sb.toString() : data.toString();
    }

    /** 工单失败列表非空判定（剔除 null/空串占位项）。 */
    private boolean workOrderFailsEmpty(List<?> fails) {
        return fails.stream().allMatch(f -> f == null || String.valueOf(f).isBlank());
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