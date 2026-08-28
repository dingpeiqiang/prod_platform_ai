package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品研发 - 智聊对话配置工具。
 * <p>
 * 将用户自然语言需求翻译为产商品配置草稿（包装 product-ontology/config/chat 后端能力）。
 */
@Component
public class RdConfigChatTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdConfigChatTool.class);

    private final ProductOntologyService productOntologyService;

    public RdConfigChatTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "rd_config_chat";
    }

    @Override
    public String getDescription() {
        return "根据用户自然语言，生成产商品对话配置草稿（业务场景、套餐、资费等字段）";
    }

    @Override
    public String getLabel() {
        return "对话配置生成";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("text")
                        .label("配置需求")
                        .description("用户对产商品配置的自然语言描述（对话配置入口）")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("draft")
                        .label("已有草稿")
                        .description("待补充/润色的已有配置草稿（可为空）")
                        .type("object")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("配置结果摘要").type("string")
                        .description("本次生成的配置结果摘要").build(),
                ToolOutputField.builder("draft", ToolOutputField.Role.OTHER)
                        .label("配置草稿").type("object")
                        .description("本次生成的完整配置草稿").build(),
                ToolOutputField.builder("config", ToolOutputField.Role.OTHER)
                        .label("配置内容").type("object").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String text = params != null ? String.valueOf(params.getOrDefault("text", "")) : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = params != null && params.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) params.get("draft") : null;

        log.info("[AgentTool] rd_config_chat 执行: text={}", text);
        if (text == null || text.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少配置需求描述");
        }
        try {
            Map<String, Object> resp = productOntologyService.chatConfigure(text, draft);
            return ExecutionResult.ok(getName(), normalize(resp));
        } catch (Exception e) {
            log.error("[AgentTool] rd_config_chat 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "配置生成失败: " + e.getMessage());
        }
    }

    /** 将 service 响应规范化为工具 output 契约（抽取摘要 + 保留明细）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回配置结果");
            return out;
        }
        Object draft = resp.get("draft");
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        if (summary == null && draft instanceof Map<?, ?> d) {
            Object name = d.get("offeringName");
            if (name == null) name = d.get("name");
            summary = name != null ? "已生成配置草稿：" + name : "已生成配置草稿";
        }
        if (summary == null) summary = resp.get("nl_answer");
        out.put("nl_answer", summary != null ? String.valueOf(summary) : "已生成配置草稿");
        if (draft != null) out.put("draft", draft);
        out.put("config", resp);
        return out;
    }
}
