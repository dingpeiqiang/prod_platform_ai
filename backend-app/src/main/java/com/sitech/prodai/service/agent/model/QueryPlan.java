package com.sitech.prodai.service.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询计划 = 翻译层的"中间语言"。
 * 描述用户想做什么、涉及什么、需要调用哪些工具。
 */
public class QueryPlan {

    /** 澄清意图：需向用户补充参数（设计文档 3.4 节） */
    public static final String INTENT_CLARIFY = "CLARIFY";

    /** 复用证据：仅对上轮已有证据做再解释/下钻（设计文档 4.4 节） */
    public static final String INTENT_REUSE_EVIDENCE = "REUSE_EVIDENCE";

    /** 用户意图 (SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | CLARIFY | ...) */
    private String intent;

    /** 需要调用的工具列表（兼容：steps 的扁平视图） */
    private List<String> tools;

    /** 工具参数 */
    private Map<String, Object> params;

    /** 需向用户补充的参数名列表（intent=CLARIFY 时非空） */
    private List<String> clarify;

    /** 有序执行步骤（替代 tools 展开，支撑依赖编排） */
    private List<ExecStep> steps;

    /** 原始问题 */
    private String userQuestion;

    public QueryPlan() {
        this.params = new LinkedHashMap<>();
    }

    public QueryPlan(String intent, List<String> tools, Map<String, Object> params, String userQuestion) {
        this.intent = intent;
        this.tools = tools;
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        this.userQuestion = userQuestion;
        this.steps = tools != null
                ? tools.stream().map(ExecStep::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new))
                : new ArrayList<>();
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    }

    public List<String> getClarify() {
        return clarify;
    }

    public void setClarify(List<String> clarify) {
        this.clarify = clarify;
    }

    public List<ExecStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ExecStep> steps) {
        this.steps = steps;
        // 保持 tools 为步骤 tool 名的扁平视图（兼容）
        if (steps != null) {
            this.tools = steps.stream().map(ExecStep::getTool).toList();
        }
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    @Override
    public String toString() {
        return "QueryPlan{intent='" + intent + "', tools=" + tools
                + ", clarify=" + clarify + ", params=" + params + "}";
    }
}