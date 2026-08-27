package com.sitech.prodai.service.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数定义：显式声明入参规范，供理解层 / 前端生成追问与校验。
 * <p>
 * 对应设计文档 3.2 节 ToolParam 模型。
 */
public class ToolParam {

    /** 参数名（同 params 的 key） */
    private final String name;

    /** 业务展示名（如 "商品/套餐"） */
    private final String label;

    /** 说明 */
    private final String description;

    /** 是否必填 */
    private final boolean required;

    /** 类型：string | number | boolean | date | list */
    private final String type;

    /** 格式约束，如 yyyy-MM、URI、regex */
    private final String format;

    /** 缺省值（缺省时使用，降低追问频率） */
    private final String defaultValue;

    /** 可选枚举（有界取值时约束） */
    private final List<String> enumValues;

    /** 取值来源：question 抽取 | context 缓存 | 前序工具输出 */
    private final String source;

    private ToolParam(Builder builder) {
        this.name = builder.name;
        this.label = builder.label;
        this.description = builder.description;
        this.required = builder.required;
        this.type = builder.type;
        this.format = builder.format;
        this.defaultValue = builder.defaultValue;
        this.enumValues = builder.enumValues;
        this.source = builder.source;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public String getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public String getSource() {
        return source;
    }

    /**
     * 序列化为 Map（供 SSE 事件 / 前端契约使用）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        if (label != null) m.put("label", label);
        if (description != null) m.put("description", description);
        m.put("required", required);
        if (type != null) m.put("type", type);
        if (format != null) m.put("format", format);
        if (defaultValue != null) m.put("default_value", defaultValue);
        if (enumValues != null && !enumValues.isEmpty()) m.put("enum_values", enumValues);
        if (source != null) m.put("source", source);
        return m;
    }

    public static class Builder {
        private final String name;
        private String label;
        private String description;
        private boolean required;
        private String type = "string";
        private String format;
        private String defaultValue;
        private List<String> enumValues;
        private String source;

        private Builder(String name) {
            this.name = name;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public ToolParam build() {
            return new ToolParam(this);
        }
    }
}
