package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SWRL 归因工具：触发 SWRL 归因推理。
 */
@Component
public class SwrlRootCauseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SwrlRootCauseTool.class);

    private final ProductOntologyService productOntologyService;

    public SwrlRootCauseTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "swrl_root_cause";
    }

    @Override
    public String getDescription() {
        return "触发 SWRL 归因推理，分析业务指标异动的根因";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(Map<String, Object> params) {
        String offeringId = params != null ? String.valueOf(params.getOrDefault("offering", "")) : "";
        String text = params != null ? String.valueOf(params.getOrDefault("question", "")) : "";

        log.info("[AgentTool] swrl_root_cause 执行: offeringId={}", offeringId);

        try {
            Map<String, Object> result;
            if (offeringId != null && !offeringId.isBlank() && !"null".equals(offeringId)) {
                result = productOntologyService.analyzeRootCause(offeringId, text);
            } else {
                result = productOntologyService.analyzeRootCause(text);
            }
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] swrl_root_cause 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "归因分析失败: " + e.getMessage());
        }
    }
}