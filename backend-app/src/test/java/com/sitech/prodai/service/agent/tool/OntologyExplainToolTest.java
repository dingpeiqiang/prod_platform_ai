package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TBox 概念解释工具（方案 §6-B5，修正 §5.2 语义错位）单元测试：
 * 概念检索命中（精确/前缀/中文）、TBox 三面（label/comment/层级+关联属性）、
 * 未命中不冒充、缺参拒绝、描述声明与 attribution_query 职责分离。
 */
@ExtendWith(MockitoExtension.class)
class OntologyExplainToolTest {

    @Mock
    private OntologyService ontologyService;

    private OntologyExplainTool tool;

    @BeforeEach
    void setUp() {
        tool = new OntologyExplainTool(ontologyService);
    }

    @Test
    void spiDeclaresScenesAndSeparationFromAttribution() {
        assertEquals("ontology_explain", tool.getName());
        assertTrue(tool.getScenes().contains("query"));
        assertTrue(tool.getScenes().contains("ops"));
        // §5.2 拆分：描述引导审计流水问询去 attribution_query，handoffs 声明承接
        assertTrue(tool.getDescription().contains("attribution_query"),
                "描述须声明评估流水走 attribution_query（职责分离）");
        assertTrue(tool.getHandoffs().contains("attribution_query"));
    }

    @Test
    void failsWithoutConcept() {
        ExecutionResult result = tool.execute(Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("概念名"));
    }

    @Test
    void resolvesByLocalNameExactAndReturnsTBoxFaces() {
        // 概念检索命中（返回行集）；层级/属性查询走 thenAnswer 按查询内容区分（见 hierarchy 用例），
        // 此处统一 stub：概念行含 ConfigScheme，其余查询返回空集
        when(ontologyService.sparqlQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0, String.class);
            if (q.contains("owl:Class") || q.contains("owl:ObjectProperty")) {
                return sparqlResp(row(Map.of("uri", NS + "ConfigScheme", "lbl", "配置方案", "cmt", "方案配置产商品资费")));
            }
            return sparqlResp();
        });

        ExecutionResult result = tool.execute(Map.of("concept", "ConfigScheme"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        assertEquals(NS + "ConfigScheme", data.get("concept_uri"));
        assertEquals("配置方案", data.get("label"));
        assertTrue(String.valueOf(data.get("natural_language")).contains("配置方案"),
                "摘要含中文标签");
    }

    @Test
    void resolvesByChineseKeyword() {
        when(ontologyService.sparqlQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0, String.class);
            if (q.contains("CONTAINS")) {
                return sparqlResp(row(Map.of("uri", NS + "PricingProduct", "lbl", "资费商品", "cmt", "在售资费")));
            }
            return sparqlResp();
        });

        ExecutionResult result = tool.execute(Map.of("concept", "资费"));

        assertTrue(result.isSuccess());
        assertEquals(NS + "PricingProduct", result.getData().get("concept_uri"));
    }

    @Test
    void unknownConceptFailsWithoutFakeData() {
        when(ontologyService.sparqlQuery(anyString())).thenReturn(sparqlResp());

        ExecutionResult result = tool.execute(Map.of("concept", "不存在概念XYZ"));

        assertFalse(result.isSuccess(), "未命中概念必须拒绝，不返回空解释冒充");
        assertTrue(result.getErrorMessage().contains("未找到"));
    }    @Test
    void hierarchyAndRelatedPropertiesPresent() {
        when(ontologyService.sparqlQuery(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0, String.class);
            if (q.contains("subClassOf")) {
                return sparqlResp(row(Map.of("rel", NS + "PreferentialPlan", "lbl", "优惠计划", "cmt", "")));
            }
            if (q.contains("rdfs:domain") || q.contains("rdfs:range")) {
                return sparqlResp(row(Map.of("prop", NS + "hasPreferentialPlan", "role", "domain",
                        "lbl", "方案含优惠", "cmt", "方案含优惠（含子类实例）")));
            }
            return sparqlResp(row(Map.of("uri", NS + "AccountPreferential", "lbl", "账户优惠", "cmt", "按账户维度优惠")));
        });

        ExecutionResult result = tool.execute(Map.of("concept", "AccountPreferential"));

        assertTrue(result.isSuccess());
        Map<?, ?> data = result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> supers = (List<Map<String, Object>>) data.get("super_classes");
        assertFalse(supers.isEmpty(), "父类面呈现");
        assertEquals(NS + "PreferentialPlan", supers.get(0).get("uri"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> props = (List<Map<String, Object>>) data.get("related_properties");
        assertFalse(props.isEmpty(), "关联属性面呈现");
        assertTrue(String.valueOf(data.get("natural_language")).contains("下位概念"),
                "摘要串联层级语义");
    }

    // ── 测试夹具 ──

    private static final String NS = "http://www.telecom-ontology.org/prod-config#";

    /** OntologyService.sparqlQuery 返回 Map 包裹 results 的行集。 */
    private Map<String, Object> sparqlResp(Map<String, Object>... rows) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("results", List.of(rows));
        return resp;
    }

    private Map<String, Object> row(Map<String, Object> kv) {
        return new LinkedHashMap<>(kv);
    }
}
