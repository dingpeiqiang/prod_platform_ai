package com.sitech.prodai.service.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行步骤：声明某个工具执行时所需的参数来源，支撑工具间数据传递（设计文档 3.5 节）。
 * <p>
 * 取值来源（paramMappings 的值）约定：
 * <pre>
 *   "direct:&lt;name&gt;"    → 用 plan.params.&lt;name&gt;
 *   "result:&lt;X&gt;.&lt;key&gt;" → 用前序步骤 X 的 ExecutionResult.data.&lt;key&gt;
 *   "evidence:&lt;key&gt;"    → 用 context.cachedEvidence.&lt;key&gt;
 *   "default:&lt;value&gt;"   → 用默认值
 * </pre>
 */
public class ExecStep {

    /** 工具名 */
    private String tool;

    /** 参数名 → 取值来源 */
    private Map<String, String> paramMappings;

    /** 直接给定的字面参数（来自理解层抽取） */
    private Map<String, Object> literalParams;

    public ExecStep() {
        this.paramMappings = new LinkedHashMap<>();
        this.literalParams = new LinkedHashMap<>();
    }

    public ExecStep(String tool) {
        this();
        this.tool = tool;
    }

    public ExecStep(String tool, Map<String, String> paramMappings, Map<String, Object> literalParams) {
        this.tool = tool;
        this.paramMappings = paramMappings != null ? new LinkedHashMap<>(paramMappings) : new LinkedHashMap<>();
        this.literalParams = literalParams != null ? new LinkedHashMap<>(literalParams) : new LinkedHashMap<>();
    }

    /** 便捷工厂：direct 来源映射 */
    public static ExecStep of(String tool, Map<String, String> paramMappings) {
        return new ExecStep(tool, paramMappings, Map.of());
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, String> getParamMappings() {
        return paramMappings;
    }

    public void setParamMappings(Map<String, String> paramMappings) {
        this.paramMappings = paramMappings != null ? new LinkedHashMap<>(paramMappings) : new LinkedHashMap<>();
    }

    public Map<String, Object> getLiteralParams() {
        return literalParams;
    }

    public void setLiteralParams(Map<String, Object> literalParams) {
        this.literalParams = literalParams != null ? new LinkedHashMap<>(literalParams) : new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return "ExecStep{tool='" + tool + "', paramMappings=" + paramMappings + "}";
    }
}
