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
 * 产商品研发 - 智查历史配置工具。
 * <p>
 * 语义/关键词检索历史产商品配置方案（包装 product-ontology/config/discover 后端能力）。
 */
@Component
public class RdDiscoverTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RdDiscoverTool.class);

    private static final int DEFAULT_LIMIT = 20;

    private final ProductOntologyService productOntologyService;

    public RdDiscoverTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "rd_config_discover";
    }

    @Override
    public String getDescription() {
        return "检索历史产商品配置方案（按语义/关键词），返回匹配的配置清单。"
                + "适用于用户想查找/查看已有方案（如「找一下月费39左右的校园套餐」「查一下有没有家庭融合套餐」），"
                + "即使话术中带月费等套餐要素也用本工具；用户明确要求新建/生成配置时不要使用本工具";
    }

    @Override
    public String getLabel() {
        return "历史配置检索";
    }

    @Override
    public java.util.Set<String> getScenes() {
        return java.util.Set.of("rd");
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("question")
                        .label("检索内容")
                        .description("要检索的历史配置描述或关键词")
                        .required()
                        .type("string")
                        .source("question")
                        .build(),
                ToolParam.builder("limit")
                        .label("返回上限")
                        .description("返回结果条数上限")
                        .type("number")
                        .defaultValue("20")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY)
                        .label("检索摘要").type("string")
                        .description("检索结果摘要").build(),
                ToolOutputField.builder("items", ToolOutputField.Role.ITEMS)
                        .label("配置方案").type("list")
                        .description("匹配的配置方案清单").build(),
                ToolOutputField.builder("entity_ids", ToolOutputField.Role.COUNT)
                        .label("命中数").type("list").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String q = params != null ? String.valueOf(params.getOrDefault("question", "")) : "";
        int limit = DEFAULT_LIMIT;
        if (params != null && params.get("limit") instanceof Number n) {
            limit = n.intValue();
        }
        log.info("[AgentTool] rd_config_discover 执行: q={}, limit={}", q, limit);
        if (q == null || q.isBlank()) {
            return ExecutionResult.fail(getName(), "缺少检索内容");
        }
        try {
            Map<String, Object> resp = productOntologyService.discoverConfigs(q, limit);
            return ExecutionResult.ok(getName(), normalize(resp));
        } catch (Exception e) {
            log.error("[AgentTool] rd_config_discover 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "配置检索失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(Map<String, Object> resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resp == null) {
            out.put("nl_answer", "未返回检索结果");
            return out;
        }
        Object items = resp.get("items");
        if (items == null) items = resp.get("results");
        if (items == null) items = resp.get("configs");
        int count = items instanceof List<?> l ? l.size() : 0;
        Object summary = resp.get("summary");
        if (summary == null) summary = resp.get("message");
        if (summary == null) summary = "检索到 " + count + " 条配置方案";
        out.put("nl_answer", String.valueOf(summary));
        if (items instanceof List<?>) out.put("items", items);
        out.put("entity_ids", items instanceof List<?> l ? l : List.of());
        return out;
    }
}
