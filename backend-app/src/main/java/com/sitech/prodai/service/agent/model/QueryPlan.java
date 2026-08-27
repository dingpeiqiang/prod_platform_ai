package com.sitech.prodai.service.agent.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询计划 = 翻译层的"中间语言"。
 * 描述用户想做什么、涉及什么、需要调用哪些工具。
 */
public class QueryPlan {

    /** 用户意图 (SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | ...) */
    private String intent;

    /** 需要调用的工具列表 */
    private List<String> tools;

    /** 工具参数 */
    private Map<String, Object> params;

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

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    @Override
    public String toString() {
        return "QueryPlan{intent='" + intent + "', tools=" + tools + ", params=" + params + "}";
    }
}