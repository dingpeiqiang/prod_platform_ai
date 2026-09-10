package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.impl.DefaultExecutor;
import com.sitech.prodai.service.agent.impl.DefaultUnderstander;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolOutputField;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 智读·文件配置手册（doc-batch-import）直达链路跨工具数据流集成测试。
 * <p>
 * 回归背景：手册步骤曾未声明 input_from，执行层 direct 全参透传下
 * rd_draft_extract 的 document_text 拿到的是用户原话（fillQuestionSlots 补槽）而非
 * rd_doc_parse 解析产出，真实文档导入实测报「未从文档识别到套餐要素（抽取引擎 none）」。
 * <p>
 * 验证口径：真实 DefaultExecutor + 桩工具（记录每次收到的入参），触发词快筛直达手册链路后——
 * ① parse → extract：document_text 承接解析产出（非用户原话）；
 * ② extract → compliance：draft 参数承接草稿条目清单（List 形态触发批量校验分支）；
 * ③ compliance → create：items 承接校验清单（每条自带合规结论）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocBatchImportPlaybookChainTest {

    @Mock
    private Presenter presenter;
    @Mock
    private LlmService llmService;

    private SessionManager sessionManager;
    private AgentOrchestrator orchestrator;

    /** 各工具实际收到的入参（工具名 → 最近一次入参快照）。 */
    private final Map<String, Map<String, Object>> receivedParams = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(Optional.empty());
        AgentTool parseTool = stubTool("rd_doc_parse",
                List.of(ToolParam.builder("file_id").label("文档标识").type("string").build(),
                        ToolParam.builder("document_text").label("文档内容").type("string").source("question").build()),
                List.of(ToolOutputField.builder("document_text", ToolOutputField.Role.OTHER).label("文档文本").build(),
                        ToolOutputField.builder("document", ToolOutputField.Role.OTHER).label("结构化文档").build(),
                        ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY).label("解析摘要").build()),
                params -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("nl_answer", "已解析文档");
                    out.put("document_text", DOC_TEXT_PARSED);
                    out.put("document", DOC_IR_PARSED);
                    return ExecutionResult.ok("rd_doc_parse", out);
                });
        AgentTool extractTool = stubTool("rd_draft_extract",
                List.of(ToolParam.builder("document_text").label("文档内容").type("string").source("question").build(),
                        ToolParam.builder("document").label("结构化文档").type("object").build()),
                List.of(ToolOutputField.builder("items", ToolOutputField.Role.ITEMS).label("配置草稿").build(),
                        ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY).label("抽取摘要").build()),
                params -> {
                    List<Map<String, Object>> items = new ArrayList<>();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("index", 1);
                    item.put("draft", new LinkedHashMap<>(Map.of("offeringName", "智慧社区套餐A", "monthlyFee", 198)));
                    item.put("compliancePass", true);
                    item.put("issues", List.of());
                    items.add(item);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("nl_answer", "已抽取 1 条套餐草稿");
                    out.put("items", items);
                    return ExecutionResult.ok("rd_draft_extract", out);
                });
        AgentTool complianceTool = stubTool("rd_compliance",
                List.of(ToolParam.builder("draft").label("配置草稿").type("object").build()),
                List.of(ToolOutputField.builder("items", ToolOutputField.Role.ITEMS).label("校验清单").build(),
                        ToolOutputField.builder("draft", ToolOutputField.Role.OTHER).label("配置草稿").build(),
                        ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY).label("校验摘要").build()),
                params -> {
                    // 桩内复刻批量分支语义：清单入参逐条回填，单草稿入参单条透出
                    List<Map<String, Object>> outItems = new ArrayList<>();
                    if (params.get("draft") instanceof List<?> list) {
                        int idx = 0;
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> m) {
                                idx++;
                                Map<String, Object> entry = new LinkedHashMap<>((Map<String, Object>) m);
                                entry.put("index", idx);
                                entry.put("status", "通过");
                                outItems.add(entry);
                            }
                        }
                    }
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("nl_answer", "已校验 " + outItems.size() + " 条草稿");
                    out.put("items", outItems);
                    if (!outItems.isEmpty()) {
                        out.put("draft", outItems.get(0).get("draft"));
                    }
                    return ExecutionResult.ok("rd_compliance", out);
                });
        AgentTool createTool = stubTool("rd_workorder_create",
                List.of(ToolParam.builder("items").label("待开单草稿").type("list").build(),
                        ToolParam.builder("session_id").label("会话标识").type("string").build()),
                List.of(ToolOutputField.builder("workOrders", ToolOutputField.Role.ITEMS).label("配置工单").build(),
                        ToolOutputField.builder("nl_answer", ToolOutputField.Role.SUMMARY).label("开单摘要").build()),
                params -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("nl_answer", "已批量创建 1 个配置工单");
                    out.put("workOrders", List.of(Map.of("workOrderId", "WO-1")));
                    return ExecutionResult.ok("rd_workorder_create", out);
                });

        DefaultExecutor executor = new DefaultExecutor(List.of(parseTool, extractTool, complianceTool, createTool));
        orchestrator = new AgentOrchestrator(new DefaultUnderstander(null, null, null, null, null,
                        null, null, null), executor, presenter, sessionManager,
                Optional.empty(), Optional.of(llmService), List.of(parseTool, extractTool, complianceTool, createTool),
                null,
                new com.sitech.prodai.service.agent.flow.SceneFlowRouter(
                        new com.sitech.prodai.config.ProdAiProperties(), null,
                        new com.sitech.prodai.service.agent.playbook.PlaybookRegistry()));
        when(presenter.present(any(), anyList(), any(com.sitech.prodai.service.agent.model.SessionContext.class)))
                .thenReturn("手册执行完毕");
        when(presenter.suggestFollowUps(any(), anyList(), any(com.sitech.prodai.service.agent.model.SessionContext.class)))
                .thenReturn(List.of());
    }

    private static final String DOC_TEXT_PARSED = "套餐A：月费198，流量40GB，500M宽带";

    /** 解析环节产出的结构化文档 IR（DocumentIR.toMap 形态）。 */
    private static final Map<String, Object> DOC_IR_PARSED = Map.of(
            "engine", "text",
            "blocks", List.of(Map.of("type", "paragraph", "text", DOC_TEXT_PARSED)));

    private AgentTool stubTool(String name,
                               List<ToolParam> params,
                               List<ToolOutputField> outputs,
                               java.util.function.Function<Map<String, Object>, ExecutionResult> handler) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name + " 桩";
            }

            @Override
            public String getLabel() {
                return name;
            }

            @Override
            public java.util.Set<String> getScenes() {
                return java.util.Set.of("rd");
            }

            @Override
            public List<ToolParam> getParams() {
                return params;
            }

            @Override
            public List<ToolOutputField> getOutputFields() {
                return outputs;
            }

            @Override
            public ExecutionResult execute(Map<String, Object> toolParams) {
                receivedParams.put(name, toolParams);
                return handler.apply(toolParams);
            }
        };
    }

    @Test
    void playbookChainWiresDocumentTextIntoExtractAndItemsIntoCreate() {
        // 附件-only 交互变更（豆包式）：「导入文档：xxx」+ file_id 属于附件-only 场景，
        // 编排层先解析再追问，不再直达手册全链；显式指令话术仍走触发词快筛直达手册链路
        orchestrator.processStream("导入文档并批量配置：智慧社区融合方案.csv", "s-chain1", new java.util.HashMap<>(Map.of("file_id", "f-1")), "rd",
                (event, data) -> { });

        // ① parse → extract：document_text 承接解析产出，而非用户原话（根因回归点）
        Map<String, Object> extractParams = receivedParams.get("rd_draft_extract");
        assertNotNull(extractParams, "rd_draft_extract 应被执行");
        assertEquals(DOC_TEXT_PARSED, extractParams.get("document_text"),
                "抽取环节的 document_text 必须承接 rd_doc_parse 解析产出（input_from 跨工具数据流）");
        assertEquals(DOC_IR_PARSED, extractParams.get("document"),
                "抽取环节的 document（结构化 IR）必须承接 rd_doc_parse 产出（v1.2 表格直通入口）");

        // ② extract → compliance：清单形态触发批量校验
        Map<String, Object> complianceParams = receivedParams.get("rd_compliance");
        assertNotNull(complianceParams, "rd_compliance 应被执行");
        assertTrue(complianceParams.get("draft") instanceof List<?> complianceItems && !complianceItems.isEmpty(),
                "合规环节应承接 rd_draft_extract.items 清单形态");

        // ③ compliance → create：items 承接校验清单
        Map<String, Object> createParams = receivedParams.get("rd_workorder_create");
        assertNotNull(createParams, "rd_workorder_create 应被执行");
        assertTrue(createParams.get("items") instanceof List<?> createItems && !createItems.isEmpty(),
                "开单环节应承接 rd_compliance.items 校验清单");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) createParams.get("items");
        assertEquals("智慧社区套餐A", ((Map<String, Object>) items.get(0).get("draft")).get("offeringName"),
                "开单条目应携带抽取出的草稿内容");
        assertNotNull(createParams.get("session_id"), "工单需绑定会话");
    }

    @Test
    void playbookPlanCarriesInputFromMappings() {
        orchestrator.process("导入文档：智慧社区融合方案.csv", "s-chain2", new java.util.HashMap<>(Map.of("file_id", "f-2")), "rd");

        // 计划层契约：手册步骤的 input_from 已转为 ExecStep.paramMappings（result: 来源）
        // 这里借执行层收到的参数间接断言（真实 executor 非桩，无法 captor 计划），
        // 直接验证手册装载产物：
        var book = orchestratorPlaybook("doc-batch-import");
        assertNotNull(book, "doc-batch-import 手册应已装载");
        assertTrue(book.get("steps") instanceof List<?> steps && ((List<?>) book.get("steps")).size() == 4, "四步链");
        List<?> stepList = (List<?>) book.get("steps");
        boolean extractHasInputFrom = false;
        boolean createHasInputFrom = false;
        for (Object o : stepList) {
            if (o instanceof Map<?, ?> step) {
                if ("rd_draft_extract".equals(step.get("tool"))
                        && step.get("input_from") instanceof Map<?, ?> from
                        && "rd_doc_parse.document_text".equals(from.get("document_text"))) {
                    extractHasInputFrom = true;
                }
                if ("rd_workorder_create".equals(step.get("tool"))
                        && step.get("input_from") instanceof Map<?, ?> from
                        && "rd_compliance.items".equals(from.get("items"))) {
                    createHasInputFrom = true;
                }
            }
        }
        assertTrue(extractHasInputFrom, "extract 步骤应声明 document_text ← rd_doc_parse.document_text");
        assertTrue(createHasInputFrom, "create 步骤应声明 items ← rd_compliance.items");
    }

    private Map<String, Object> orchestratorPlaybook(String code) {
        com.sitech.prodai.service.agent.playbook.PlaybookRegistry registry =
                new com.sitech.prodai.service.agent.playbook.PlaybookRegistry();
        registry.init();
        return registry.get(code);
    }
}
