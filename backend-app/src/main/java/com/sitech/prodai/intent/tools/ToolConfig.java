package com.sitech.prodai.intent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.FormService;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Calling 工具配置 —— 注册所有可被 LLM 调用的工具。
 *
 * <p>每个工具都是一个 {@link ToolDefinition} Bean，
 * 由 {@link ToolRegistry} 自动扫描并注册。
 *
 * <p>已注册的工具：
 * <ul>
 *   <li>{@code ontology_query} - 查询本体数据（NL → SPARQL → 结果）</li>
 *   <li>{@code policy_evaluate} - 评估策略集（规则引擎判定）</li>
 *   <li>{@code form_validate} - 校验表单数据</li>
 *   <li>{@code explain} - 解释本体概念和关系</li>
 *   <li>{@code compare_state} - 假设分析（对比变更前后状态）</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "prodai.llm.enabled", havingValue = "true", matchIfMissing = false)
public class ToolConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolConfig.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * ontology_query - 查询本体数据
     *
     * <p>LLM 传入自然语言问题，工具自动执行 NL → 实体发现 → SPARQL 查询 → 格式化结果。
     */
    @Bean
    public ToolDefinition ontologyQueryTool(OntologyService ontologyService) {
        return new ToolDefinition(
                "ontology_query",
                "查询本体数据。输入自然语言问题，返回结构化的本体查询结果。适用于：查询在售商品、查询资费信息、查询产品配置等。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of(
                                        "type", "string",
                                        "description", "用户的自然语言查询问题，例如：查询所有在售5G套餐"
                                ),
                                "max_results", Map.of(
                                        "type", "integer",
                                        "description", "最大返回结果数，默认20",
                                        "default", 20
                                )
                        ),
                        "required", List.of("question")
                ),
                (args) -> {
                    String question = String.valueOf(args.getOrDefault("question", ""));
                    int maxResults = args.get("max_results") instanceof Number
                            ? ((Number) args.get("max_results")).intValue()
                            : 20;

                    Map<String, Object> result = ontologyService.nlDiscoverAndRetrieve(question, maxResults);
                    return toJsonString(result);
                }
        );
    }

    /**
     * policy_evaluate - 评估策略集
     *
     * <p>LLM 传入事实数据和策略集ID，工具自动执行规则引擎评估。
     */
    @Bean
    public ToolDefinition policyEvaluateTool(OntologyService ontologyService) {
        return new ToolDefinition(
                "policy_evaluate",
                "评估策略集。输入事实数据和策略集ID，返回规则引擎的判定结果（通过/拒绝/待审）。适用于：立项研判、风险稽核、合规校验等。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "policy_set_id", Map.of(
                                        "type", "string",
                                        "description", "策略集ID，如 PS_PRODUCT_ONLINE_V1, PS_PRODUCT_RISK_V1"
                                ),
                                "facts", Map.of(
                                        "type", "object",
                                        "description", "事实数据，如 {\"productType\": \"5G套餐\", \"isZeroFee\": false}"
                                ),
                                "expectation_type", Map.of(
                                        "type", "string",
                                        "description", "期望类型：risk_audit（风险稽核）, online_check（立项校验）, candidate_check（候选评估）",
                                        "default", "risk_audit"
                                )
                        ),
                        "required", List.of("policy_set_id", "facts")
                ),
                (args) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> facts = (Map<String, Object>) args.getOrDefault("facts", Map.of());
                    String policySetId = String.valueOf(args.getOrDefault("policy_set_id", ""));
                    String expectationType = String.valueOf(args.getOrDefault("expectation_type", "risk_audit"));

                    Map<String, Object> result = ontologyService.evaluate(facts, policySetId, expectationType, null, null);
                    return toJsonString(result);
                }
        );
    }

    /**
     * form_validate - 校验表单数据
     *
     * <p>LLM 传入表单数据，工具通过 FormService 加载字段定义后执行 ValidationService 校验。
     */
    @Bean
    public ToolDefinition formValidateTool(FormService formService, ValidationService validationService) {
        return new ToolDefinition(
                "form_validate",
                "校验表单数据是否符合规则。输入表单编码和字段数据，返回校验结果（通过/失败）及错误详情。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "form_code", Map.of(
                                        "type", "string",
                                        "description", "表单编码，如 leave, sales_order, offering_config"
                                ),
                                "form_data", Map.of(
                                        "type", "object",
                                        "description", "表单字段数据，如 {\"leave_type\": \"年假\", \"leave_days\": 3}"
                                )
                        ),
                        "required", List.of("form_code", "form_data")
                ),
                (args) -> {
                    String formCode = String.valueOf(args.getOrDefault("form_code", ""));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> formData = args.get("form_data") instanceof Map<?, ?>
                            ? (Map<String, Object>) args.get("form_data")
                            : Map.of();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("formCode", formCode);

                    Map<String, Object> schema = formService.getFormSchema(formCode);
                    if (!Boolean.TRUE.equals(schema.get("success"))) {
                        result.put("passed", false);
                        result.put("errors", List.of(String.valueOf(schema.getOrDefault("message", "表单不存在"))));
                        result.put("warnings", List.of());
                        return toJsonString(result);
                    }

                    List<Map<String, Object>> fields = new ArrayList<>();
                    Object fieldsObj = schema.get("fields");
                    if (fieldsObj instanceof List<?> raw) {
                        for (Object item : raw) {
                            if (item instanceof Map<?, ?> m) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> cast = (Map<String, Object>) m;
                                fields.add(cast);
                            }
                        }
                    }

                    ValidationService.ValidationResult vr = validationService.validateForm(formData, fields);
                    result.put("passed", vr.valid);
                    result.put("errors", vr.errors);
                    result.put("warnings", List.of());
                    result.put("fieldCount", formData.size());
                    return toJsonString(result);
                }
        );
    }

    /**
     * explain - 解释本体概念和关系
     *
     * <p>LLM 传入实体名称，工具返回该实体的详细解释。
     */
    @Bean
    public ToolDefinition explainTool(OntologyService ontologyService) {
        return new ToolDefinition(
                "explain",
                "解释本体中的概念和关系。输入实体名称，返回该实体的详细解释、属性和关联关系。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "entity_name", Map.of(
                                        "type", "string",
                                        "description", "要解释的实体名称，如 5G套餐, 家庭融合畅享128"
                                ),
                                "context", Map.of(
                                        "type", "string",
                                        "description", "上下文信息，帮助更精确地解释"
                                )
                        ),
                        "required", List.of("entity_name")
                ),
                (args) -> {
                    String entityName = String.valueOf(args.getOrDefault("entity_name", ""));
                    String context = String.valueOf(args.getOrDefault("context", ""));

                    Map<String, Object> result = ontologyService.explain(entityName, context, null);
                    return toJsonString(result);
                }
        );
    }

    /**
     * compare_state - 假设分析
     *
     * <p>LLM 传入当前事实和假设变更，工具对比变更前后的评估结果。
     */
    @Bean
    public ToolDefinition compareStateTool(OntologyService ontologyService) {
        return new ToolDefinition(
                "compare_state",
                "假设分析。对比变更前后的状态，评估变更的影响。适用于：分析某个条件变化对结果的影响。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "current_facts", Map.of(
                                        "type", "object",
                                        "description", "当前事实数据"
                                ),
                                "proposed_changes", Map.of(
                                        "type", "object",
                                        "description", "假设变更，如 {\"isZeroFee\": true}"
                                ),
                                "policy_set_id", Map.of(
                                        "type", "string",
                                        "description", "用于评估的策略集ID"
                                )
                        ),
                        "required", List.of("current_facts", "proposed_changes")
                ),
                (args) -> {
                    String snapshotId = String.valueOf(args.getOrDefault("snapshot_id", "current"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> currentFacts = (Map<String, Object>) args.getOrDefault("current_facts", Map.of());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> proposedChanges = (Map<String, Object>) args.getOrDefault("proposed_changes", Map.of());
                    String policySetId = String.valueOf(args.getOrDefault("policy_set_id", ""));

                    // 将 proposedChanges 转换为 patches 格式
                    List<Map<String, Object>> patches = new ArrayList<>();
                    Map<String, Object> patch = new LinkedHashMap<>();
                    patch.put("description", "假设变更");
                    patch.put("changes", proposedChanges);
                    patches.add(patch);

                    Map<String, Object> result = ontologyService.compareState(snapshotId, patches, policySetId, null, null);
                    return toJsonString(result);
                }
        );
    }

    private String toJsonString(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[ToolConfig] JSON 序列化失败: {}", e.getMessage());
            return "{\"error\": \"serialization_failed\"}";
        }
    }
}
