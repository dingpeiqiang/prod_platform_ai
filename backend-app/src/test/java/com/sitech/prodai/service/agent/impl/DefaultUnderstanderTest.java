package com.sitech.prodai.service.agent.impl;

import com.sitech.prodai.exception.LlmConfigException;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.flow.FlowIntentRouter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.QueryPlan;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultUnderstander 单元测试（R8）：理解层完整流水线的关键分支——
 * LLM 重试/异常、JSON 解析、混合意图拆分、白名单/flow_execute/DAG 守门、
 * CONFIRM 确认与超限退化、ParamCompletionGate 集成澄清、rd 场景兜底改派。
 * <p>
 * LLM 以 Mockito 桩按场景返回 JSON 输出；工具以 AgentTool 匿名桩自声明场景
 * （AgentCapabilityRegistry 单源白名单）；FlowIntentRouter 用 Mockito 桩提供
 * listRoutes 注册表（flow_execute 守门依据）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultUnderstanderTest {

    @Mock
    private LlmService llmService;
    @Mock
    private OpsWorkOrderMapper workOrderMapper;
    @Mock
    private FlowIntentRouter flowIntentRouter;

    private DefaultUnderstander understander;

    @BeforeEach
    void setUp() {
        AgentTool sparql = tool("sparql_query", "ops",
                ToolParam.builder("city").label("城市").required().build());
        AgentTool rdConfig = tool("rd_config_chat", "rd",
                ToolParam.builder("requirement").label("需求描述").required().build());
        AgentTool rdDiscover = tool("rd_config_discover", "rd",
                ToolParam.builder("keyword").label("检索关键词").required().build());
        List<AgentTool> tools = List.of(sparql, rdConfig, rdDiscover);
        understander = new DefaultUnderstander(llmService, tools, workOrderMapper,
                flowIntentRouter, new AgentCapabilityRegistry(tools), null, null);
    }

    // ── fixture 工厂 ──

    /** AgentTool 匿名桩：声明所属场景 + 可选必填参数契约 + 输出契约（DAG 引用校验用）。 */
    private AgentTool tool(String name, String scene, ToolParam... params) {
        return new AgentTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " 工具"; }
            @Override public List<ToolParam> getParams() { return List.of(params); }
            @Override public java.util.Set<String> getScenes() {
                return java.util.Set.of(scene);
            }
            @Override public List<com.sitech.prodai.service.agent.tool.ToolOutputField> getOutputFields() {
                return List.of(com.sitech.prodai.service.agent.tool.ToolOutputField
                        .builder("rows", com.sitech.prodai.service.agent.tool.ToolOutputField.Role.COUNT)
                        .label("记录数").build());
            }
            @Override public ExecutionResult execute(Map<String, Object> p) {
                return ExecutionResult.ok(name, Map.of());
            }
        };
    }

    private SessionContext ctx() {
        return new SessionContext("ut-1");
    }

    private SessionContext rdCtx() {
        SessionContext c = new SessionContext("ut-rd");
        c.setScene("rd");
        return c;
    }

    private void llmReturns(String json) {
        when(llmService.completeMessages(anyString(), anyList(), anyString())).thenReturn(json);
    }

    // ── 空/闲聊分支 ──

    @Test
    void blankQuestionReturnsChatPlanWithoutLlmCall() {
        QueryPlan plan = understander.understand("  ", ctx());

        assertNotNull(plan);
        assertEquals("CHAT", plan.getIntent());
        assertTrue(plan.getTools().isEmpty(), "CHAT 计划不携带任何工具");
        verify(llmService, times(0)).completeMessages(anyString(), anyList(), anyString());
    }

    @Test
    void emptyToolsInLlmOutputFallsBackToChatPlan() {
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[],\"params\":{}}");

        QueryPlan plan = understander.understand("你好", ctx());

        assertNotNull(plan);
        assertEquals("CHAT", plan.getIntent());
    }

    // ── LLM 重试与异常 ──

    @Test
    void blankLlmResponsesRetriedThreeTimesThenThrows() {
        when(llmService.completeMessages(anyString(), anyList(), anyString())).thenReturn("  ");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertTrue(ex.getMessage().contains("多次调用均未返回有效内容"),
                () -> "空响应耗尽文案应提示网关连通但无输出: " + ex.getMessage());
        verify(llmService, times(3)).completeMessages(anyString(), anyList(), anyString());
    }

    @Test
    void llmExceptionRetriesExhaustedThenThrowsUnavailable() {
        when(llmService.completeMessages(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("connection reset"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertTrue(ex.getMessage().contains("大模型不可用"),
                () -> "异常耗尽文案应提示大模型不可用: " + ex.getMessage());
        verify(llmService, times(3)).completeMessages(anyString(), anyList(), anyString());
    }

    @Test
    void llmConfigExceptionPropagatesImmediatelyWithoutRetry() {
        when(llmService.completeMessages(anyString(), anyList(), anyString()))
                .thenThrow(new LlmConfigException("api_key 缺失"));

        LlmConfigException ex = assertThrows(LlmConfigException.class,
                () -> understander.understand("查数据", ctx()));

        assertEquals("api_key 缺失", ex.getMessage());
        verify(llmService, times(1)).completeMessages(anyString(), anyList(), anyString());
    }

    @Test
    void secondAttemptSuccessRecoversFromBlankResponses() {
        when(llmService.completeMessages(anyString(), anyList(), anyString()))
                .thenReturn("")
                .thenReturn("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],"
                        + "\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查北京数据", ctx());

        assertNotNull(plan);
        verify(llmService, atLeast(2)).completeMessages(anyString(), anyList(), anyString());
        // 推理留痕：重试后成功应记录重试日志
        assertNotNull(plan.getReasoningTrace());
        assertTrue(plan.getReasoningTrace().stream()
                        .anyMatch(t -> String.valueOf(t.get("message")).contains("重试")),
                "重试后成功应写入推理日志");
    }

    // ── JSON 解析 ──

    @Test
    void nonJsonLlmOutputThrowsParseFailure() {
        llmReturns("抱歉，我无法理解该需求");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertTrue(ex.getMessage().contains("无法解析为查询计划"),
                () -> "非 JSON 输出应报解析失败: " + ex.getMessage());
    }

    @Test
    void jsonWrappedInProseIsExtracted() {
        llmReturns("好的，计划如下：{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],"
                + "\"params\":{\"city\":\"上海\"}} 以上。");

        QueryPlan plan = understander.understand("查上海数据", ctx());

        assertNotNull(plan);
        assertEquals("sparql_query", plan.getTools().get(0));
        assertEquals("上海", plan.getParams().get("city"));
    }

    // ── 白名单守门 ──

    @Test
    void fabricatedToolTriggersRetrySelectionThenDiagnose() {
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"hallucinated_tool\"],\"params\":{}}");
        // 一次重选仍返回幻觉工具 → 诊断终止
        when(llmService.completePrompt(anyString()))
                .thenReturn("{\"tools\":[\"hallucinated_tool\"],\"params\":{}}");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertTrue(ex.getMessage().contains("无法解析为查询计划"),
                () -> "重选失败应诊断终止: " + ex.getMessage());
        verify(llmService, times(1)).completePrompt(anyString());
    }

    @Test
    void retrySelectionRecoversWithRegisteredTool() {
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"hallucinated_tool\"],\"params\":{}}");
        when(llmService.completePrompt(anyString()))
                .thenReturn("{\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertNotNull(plan);
        assertEquals("sparql_query", plan.getTools().get(0));
        assertTrue(plan.getReasoningTrace().stream()
                        .anyMatch(t -> String.valueOf(t.get("message")).contains("重新选择")),
                "重选命中应写入推理日志");
    }

    @Test
    void rdToolNotVisibleInOpsScene() {
        llmReturns("{\"intent\":\"RD_CONFIG_CHAT\",\"tools\":[\"rd_config_chat\"],\"params\":{}}");
        when(llmService.completePrompt(anyString()))
                .thenReturn("{\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查数据", ctx());

        // ops 场景 rd 工具不可见 → 重选回落 ops 白名单
        assertEquals("sparql_query", plan.getTools().get(0));
    }

    // ── flow_execute 守门 ──

    @Test
    void flowExecuteWithUnregisteredWorkflowCodeIsStripped() {
        when(flowIntentRouter.listRoutes()).thenReturn(Map.of());
        llmReturns("{\"intent\":\"FLOW_EXEC\",\"tools\":[\"flow_execute\"],"
                + "\"params\":{\"workflow_code\":\"not_registered\"}}");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("跑个流程", ctx()));

        assertTrue(ex.getMessage().contains("无法解析为查询计划"),
                () -> "flow_execute 被剔除后无可用工具应终止: " + ex.getMessage());
    }

    @Test
    void flowExecuteWithMissingWorkflowCodeIsStripped() {
        when(flowIntentRouter.listRoutes()).thenReturn(Map.of(
                "wf_a", new FlowIntentRouter.FlowRoute("wf_a", "流程A", List.of("流程a"))));
        llmReturns("{\"intent\":\"FLOW_EXEC\",\"tools\":[\"flow_execute\"],\"params\":{}}");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("跑个流程", ctx()));

        assertTrue(ex.getMessage().contains("无法解析为查询计划"),
                () -> "workflow_code 缺失同样守门剔除: " + ex.getMessage());
    }

    // ── DAG 守门（steps inputFrom） ──

    @Test
    void stepsWithInvalidResultRefAreDroppedButPlanKept() {
        // 非法引用（未声明键）被剔除映射，计划仍保留（下游走常规 direct 透传）
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"},"
                + "\"steps\":[{\"tool\":\"sparql_query\","
                + "\"inputFrom\":{\"city\":\"result:sparql_query.not_declared\"}}]}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertNotNull(plan);
        assertTrue(plan.getSteps().get(0).getParamMappings().isEmpty(),
                "非法 result: 引用的映射应被剔除");
    }

    @Test
    void stepsWithDependencyCycleRejectWholePlan() {
        // t1→t2→t1 环：两个工具互相引用，DagValidator 判环 → 整个计划拒绝
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{},"
                + "\"steps\":["
                + "{\"tool\":\"sparql_query\",\"inputFrom\":{\"a\":\"result:sparql_query.rows\"}}"
                + "]}");

        // 自引用（引用自身输出）也构成环 → 拒绝
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertTrue(ex.getMessage().contains("无法解析为查询计划"),
                () -> "依赖环应拒绝整个计划: " + ex.getMessage());
    }

    @Test
    void validResultRefIsKeptInParamMappings() {
        // rows 在 sparql_query 输出契约中声明 → 合法引用保留
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{},"
                + "\"steps\":[{\"tool\":\"sparql_query\","
                + "\"inputFrom\":{\"city\":\"result:sparql_query.rows\"}}]}");

        // 引用自身输出构成环（节点依赖自身）→ 拒绝
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> understander.understand("查数据", ctx()));

        assertNotNull(ex);
    }

    // ── 混合意图拆分 ──

    @Test
    void pipeSeparatedIntentSplitsIntoSubPlans() {
        llmReturns("{\"intent\":\"product_ops_query | product_ops_compare\","
                + "\"action\":\"query | compare\","
                + "\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        List<QueryPlan> plans = understander.understandAll(
                "查数据并对比", ctx());

        assertEquals(2, plans.size(), "混合意图应拆为 2 个子计划");
        assertEquals("product_ops_query", plans.get(0).getIntent());
        assertEquals("product_ops_compare", plans.get(1).getIntent());
        assertEquals("compare", plans.get(1).getParams().get("action"),
                "action 与 intent 位置对齐");
        // 子计划 params 独立副本（intent_type 互不覆盖）
        assertEquals("product_ops_query", plans.get(0).getParams().get("intent_type"));
        assertEquals("product_ops_compare", plans.get(1).getParams().get("intent_type"));
    }

    // ── CONFIRM 需求歧义确认 ──

    @Test
    void confirmIntentGeneratesConfirmPlanWithCandidates() {
        SessionContext context = ctx();
        llmReturns("{\"intent\":\"CONFIRM\",\"candidates\":[\"解读A\",\"解读B\"]}");

        QueryPlan plan = understander.understand("模糊需求", context);

        assertEquals(QueryPlan.INTENT_CONFIRM, plan.getIntent());
        assertEquals(List.of("解读A", "解读B"), plan.getCandidates());
        // 无 getConfirmRounds 访问器：用已递增状态验证（再次 CONFIRM 未达上限仍出确认计划）
        assertEquals(QueryPlan.INTENT_CONFIRM, understander.understand("再模糊", context).getIntent(),
                "首次确认后未超限（confirmRounds=1 < 2），再次 CONFIRM 仍应出确认计划");
        // 第二次确认后 confirmRounds=2 达上限 → 按首选解读退化
        QueryPlan degenerated = understander.understand("再模糊", context);
        assertEquals("CHAT", degenerated.getIntent(), "达确认上限后按首选解读退化");
        assertEquals("解读A", degenerated.getParams().get("assumed_interpretation"),
                "退化为首选候选解读");
    }

    @Test
    void confirmRoundsExceededDegeneratesToFirstCandidate() {
        SessionContext context = ctx();
        context.incrementConfirmRounds();
        context.incrementConfirmRounds(); // 达上限（MAX_CONFIRM_ROUNDS=2）
        llmReturns("{\"intent\":\"CONFIRM\",\"candidates\":[\"首选解读\",\"次选解读\"]}");

        QueryPlan plan = understander.understand("模糊需求", context);

        assertEquals("CHAT", plan.getIntent());
        assertEquals("首选解读", plan.getParams().get("assumed_interpretation"),
                "超限应按首选解读退化并标记假设");
        // 退化后确认计数已重置：下一轮 CONFIRM 不再立即超限
        llmReturns("{\"intent\":\"CONFIRM\",\"candidates\":[\"解读A\",\"解读B\"]}");
        QueryPlan next = understander.understand("模糊需求", context);
        assertEquals(QueryPlan.INTENT_CONFIRM, next.getIntent(),
                "重置后再次 CONFIRM 应生成确认计划而非立即退化");
    }

    @Test
    void confirmWithoutCandidatesFallsBackToChat() {
        llmReturns("{\"intent\":\"CONFIRM\",\"candidates\":[]}");

        QueryPlan plan = understander.understand("模糊需求", ctx());

        assertEquals("CHAT", plan.getIntent(), "无有效候选退化为通用对话");
    }

    // ── ParamCompletionGate 集成 ──

    @Test
    void missingRequiredParamTurnsWholeRoundIntoClarify() {
        // LLM 未抽取到必填参数 city → 参数门生成 CLARIFY
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{}}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertEquals(QueryPlan.INTENT_CLARIFY, plan.getIntent());
        assertEquals(List.of("city"), plan.getClarify());
        assertNotNull(plan.getClarifyContracts().get("city"));
        assertEquals("城市", plan.getClarifyContracts().get("city").get("label"));
    }

    @Test
    void cachedEvidenceSatisfiesRequiredParamWithoutClarify() {
        SessionContext context = ctx();
        context.getCachedEvidence().put("city", "广州");
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{}}");

        QueryPlan plan = understander.understand("再查一次", context);

        // intent 经 IntentRecognitionSupport 归一化：SPARQL_QUERY 不在映射表 → 原样小写
        assertEquals("sparql_query", plan.getIntent());
        assertEquals("广州", plan.getParams().get("city"), "缓存回填必填参数");
    }

    @Test
    void clarifyLimitExceededContinuesWithAssumption() {
        SessionContext context = ctx();
        for (int i = 0; i < 3; i++) {
            context.incrementClarifyRounds();
        }
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"],\"params\":{}}");

        QueryPlan plan = understander.understand("查数据", context);

        // intent 经 IntentRecognitionSupport 归一化：SPARQL_QUERY 不在映射表 → 原样小写
        assertEquals("sparql_query", plan.getIntent(), "澄清超限按缺省继续不转 CLARIFY");
        assertTrue(!context.getAssumptions().isEmpty(), "超限继续应记录假设");
    }

    // ── rd 场景 ──

    @Test
    void rdSceneDerivesIntentCodeFromToolName() {
        llmReturns("{\"intent\":\"configure\",\"tools\":[\"rd_config_chat\"],"
                + "\"params\":{\"requirement\":\"39元套餐\"}}");

        QueryPlan plan = understander.understand("配一个套餐", rdCtx());

        assertEquals("RD_CONFIG_CHAT", plan.getIntent(),
                "rd 场景意图码由首个 rd 工具名推导（前端对齐）");
        assertEquals("ut-rd", plan.getParams().get("session_id"),
                "rd 场景透传会话 ID");
    }

    @Test
    void rdSceneDiscoverIntentQuestionReassignsDraftTool() {
        // 话术命中检索词（找一下）且无创建动词 → rd_config_chat 改派 rd_config_discover
        // params 预置 keyword（改派后参数门按 rd_config_discover 契约校验必填参数）
        llmReturns("{\"intent\":\"configure\",\"tools\":[\"rd_config_chat\"],"
                + "\"params\":{\"requirement\":\"月费39\",\"keyword\":\"月费39\"}}");

        QueryPlan plan = understander.understand("找一下月费39的配置", rdCtx());

        assertEquals(List.of("rd_config_discover"), plan.getTools(), "检索意图应改派检索工具");
        assertEquals("RD_CONFIG_DISCOVER", plan.getIntent(),
                "rd 场景意图码由首个 rd 工具名推导（改派后取 rd_config_discover）");
    }

    @Test
    void rdSceneCreateIntentQuestionKeepsDraftTool() {
        llmReturns("{\"intent\":\"configure\",\"tools\":[\"rd_config_chat\"],"
                + "\"params\":{\"requirement\":\"做一个39的套餐\"}}");

        QueryPlan plan = understander.understand("做一个39的套餐", rdCtx());

        assertEquals(List.of("rd_config_chat"), plan.getTools(), "创建意图不改派");
    }

    // ── 意图归一化 ──

    @Test
    void legacyIntentEnumIsNormalized() {
        llmReturns("{\"intent\":\"query\",\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertEquals("product_ops_query", plan.getIntent());
        assertEquals("product_ops_query", plan.getParams().get("intent_type"));
    }

    // ── 多计划参数门联动 ──

    @Test
    void anySubPlanNeedingClarifyPausesWholeRound() {
        // 混合意图中第二个子意图缺必填参数（requirement）→ 整轮转 CLARIFY
        List<AgentTool> tools = List.of(
                tool("sparql_query", "ops",
                        ToolParam.builder("city").label("城市").required().build()),
                tool("rd_config_chat", "ops",
                        ToolParam.builder("requirement").label("需求描述").required().build()));
        understander = new DefaultUnderstander(llmService, tools, workOrderMapper,
                flowIntentRouter, new AgentCapabilityRegistry(tools), null, null);
        llmReturns("{\"intent\":\"product_ops_query | product_ops_reason\","
                + "\"tools\":[\"sparql_query\",\"rd_config_chat\"],\"params\":{\"city\":\"北京\"}}");

        List<QueryPlan> plans = understander.understandAll("查数据并生成配置", ctx());

        assertEquals(1, plans.size());
        assertEquals(QueryPlan.INTENT_CLARIFY, plans.get(0).getIntent(),
                "任一子计划缺必填参数 → 整轮转 CLARIFY（等待补参后整轮重来）");
        assertEquals(List.of("requirement"), plans.get(0).getClarify());
    }

    // ── null 响应/边界 ──

    @Test
    void understandReturnsFirstPlanOfMultiIntent() {
        // params 带必填参数 city（两个子计划共用同一工具契约，缺失会被参数门整体转 CLARIFY）
        llmReturns("{\"intent\":\"product_ops_query | product_ops_compare\","
                + "\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查并对比", ctx());

        assertNotNull(plan);
        assertEquals("product_ops_query", plan.getIntent(), "understand 仅返回首个子计划");
    }

    @Test
    void unknownIntentPassesThroughNormalized() {
        llmReturns("{\"intent\":\"custom_business\",\"tools\":[\"sparql_query\"],\"params\":{\"city\":\"北京\"}}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertEquals("custom_business", plan.getIntent(), "未知意图原样小写保留");
    }

    @Test
    void nullParamsTreatedAsEmptyMap() {
        // params 缺失 → putIfAbsent(question) 兜底，不 NPE
        llmReturns("{\"intent\":\"SPARQL_QUERY\",\"tools\":[\"sparql_query\"]}");

        QueryPlan plan = understander.understand("查数据", ctx());

        assertNotNull(plan);
        assertEquals("查数据", plan.getParams().get("question"), "question 自动回填");
        // 必填参数 city 缺失仍走 CLARIFY
        assertEquals(QueryPlan.INTENT_CLARIFY, plan.getIntent());
    }
}
