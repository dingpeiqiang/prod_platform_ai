package com.sitech.prodai.service.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具输出字段契约：声明工具执行结果 {@code data} 中的关键字段及其语义。
 * <p>
 * 用于工具自描述化：编排层 / 表达层依据工具声明的输出契约做通用渲染，
 * 而非依赖分散的字符串硬编码（如 {@code nl_answer}、{@code conclusion}）。
 * <p>
 * 对应设计文档「工具执行结果 Schema 契约」缺口项的落地。
 */
public class ToolOutputField {

    /** 字段语义角色：驱动编排层通用渲染 */
    public enum Role {
        /** 自然语言结果摘要（表达层正文摘要 / 证据标签） */
        SUMMARY,
        /** 最终结论 */
        CONCLUSION,
        /** 业务实体标识（如商品编码，缓存供追问复用） */
        BUSINESS_ENTITY_ID,
        /** 业务实体名称（缓存供追问复用与展示） */
        BUSINESS_ENTITY_NAME,
        /** 计数类指标（工具执行面板展示） */
        COUNT,
        /** 明细列表（如风险商品清单），逐条展开为证据条目 */
        ITEMS,
        /** 其他业务输出字段 */
        OTHER
    }

    /** 字段名（同 data 的 key） */
    private final String name;

    /** 下发到前端 tool 事件 output 对象时使用的键（缺省同 name；用于兼容前端既有契约，如 pathCount） */
    private final String outputKey;

    /** 业务展示名 */
    private final String label;

    /** 类型：string | number | boolean | list | object | null */
    private final String type;

    /** 说明 */
    private final String description;

    /** 语义角色 */
    private final Role role;

    private ToolOutputField(Builder builder) {
        this.name = builder.name;
        this.outputKey = builder.outputKey != null ? builder.outputKey : builder.name;
        this.label = builder.label;
        this.type = builder.type;
        this.description = builder.description;
        this.role = builder.role;
    }

    public static Builder builder(String name, Role role) {
        return new Builder(name, role);
    }

    public String getName() {
        return name;
    }

    /** 下发到前端 output 对象的键（缺省同 {@link #getName()}）。 */
    public String getOutputKey() {
        return outputKey;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Role getRole() {
        return role;
    }

    /** 序列化为 Map（供前端契约 / 工具描述查看）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        if (outputKey != null && !outputKey.equals(name)) {
            m.put("output_key", outputKey);
        }
        if (label != null) {
            m.put("label", label);
        }
        if (type != null) {
            m.put("type", type);
        }
        if (description != null) {
            m.put("description", description);
        }
        m.put("role", role.name().toLowerCase(java.util.Locale.ROOT));
        return m;
    }

    public static class Builder {
        private final String name;
        private final Role role;
        private String outputKey;
        private String label;
        private String type = "object";
        private String description;

        private Builder(String name, Role role) {
            this.name = name;
            this.role = role;
        }

        public Builder outputKey(String outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ToolOutputField build() {
            return new ToolOutputField(this);
        }
    }
}
