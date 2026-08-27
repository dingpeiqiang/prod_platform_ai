package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SWRL 风险稽核工具：触发 SWRL 风险稽核。
 */
@Component
public class SwrlRiskAuditTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SwrlRiskAuditTool.class);

    private final ProductOntologyService productOntologyService;

    public SwrlRiskAuditTool(ProductOntologyService productOntologyService) {
        this.productOntologyService = productOntologyService;
    }

    @Override
    public String getName() {
        return "swrl_risk_audit";
    }

    @Override
    public String getDescription() {
        return "触发 SWRL 风险稽核，筛查高风险商品";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(Map<String, Object> params) {
        log.info("[AgentTool] swrl_risk_audit 执行");

        try {
            Object offeringIdsRaw = params != null ? params.get("offeringIds") : null;
            List<String> offeringIds = null;
            if (offeringIdsRaw instanceof List<?> list) {
                offeringIds = list.stream().map(String::valueOf).toList();
            }
            Map<String, Object> result = productOntologyService.auditRisks(offeringIds);
            return ExecutionResult.ok(getName(), result);
        } catch (Exception e) {
            log.error("[AgentTool] swrl_risk_audit 失败: {}", e.getMessage(), e);
            return ExecutionResult.fail(getName(), "风险稽核失败: " + e.getMessage());
        }
    }
}