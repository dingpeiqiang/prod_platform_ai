package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.impl.DefaultPresenter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentCapabilityRegistry;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.SparqlQueryTool;
import com.sitech.prodai.service.agent.tool.SwrlRootCauseTool;
import com.sitech.prodai.service.agent.tool.SwrlRiskAuditTool;
import com.sitech.prodai.service.agent.tool.rd.RdCategoryResolveTool;
import com.sitech.prodai.service.agent.tool.rd.RdComplianceTool;
import com.sitech.prodai.service.agent.tool.rd.RdConfigSearchTool;
import com.sitech.prodai.service.agent.tool.rd.RdDocParseTool;
import com.sitech.prodai.service.agent.tool.rd.RdDraftExtractTool;
import com.sitech.prodai.service.agent.tool.rd.RdDraftManageTool;
import com.sitech.prodai.service.agent.tool.rd.RdSchemeCompareTool;
import com.sitech.prodai.service.agent.tool.rd.RdWorkorderCreateTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

/**
 * 跟进话术任务链化 + 白名单守门测试（任务 5.4 / 方案 11.2 触点④实现约束）。
 * <p>
 * 验证：LLM 生成话术所指工具必须在场景白名单内，非法候选剔除；
 * 全部剔除/LLM 失败时回退 handoffs 确定性兜底；prompt 中注入场景内真实能力清单、
 * 结果关键内容、承接链与近期会话动作（去重参照）；守门剔除后补位重试；失败分支修复性建议。
 */
@ExtendWith(MockitoExtension.class)
class FollowUpGateTest {

    @Mock
    private LlmService llmService;

    private DefaultPresenter opsPresenter() {
        return new DefaultPresenter(llmService, List.of(
                new SparqlQueryTool(null),
                new SwrlRootCauseTool(null),
                new SwrlRiskAuditTool(null)
        ), new AgentCapabilityRegistry(List.of(
                new SparqlQueryTool(null),
                new SwrlRootCauseTool(null),
                new SwrlRiskAuditTool(null)
        )));
    }

    private DefaultPresenter rdPresenter() {
        return new DefaultPresenter(llmService, List.of(
                new RdCategoryResolveTool(null),
                new RdDocParseTool(null),
                new RdComplianceTool(null, null),
                new RdConfigSearchTool(null),
                new RdSchemeCompareTool(null),
                new RdDraftExtractTool(null, null, null, null, null),
                new RdWorkorderCreateTool(null),
                new RdDraftManageTool(null, null, null, null)
        ), new AgentCapabilityRegistry(List.of(
                new RdCategoryResolveTool(null),
                new RdDocParseTool(null),
                new RdComplianceTool(null, null),
                new RdConfigSearchTool(null),
                new RdSchemeCompareTool(null),
                new RdDraftExtractTool(null, null, null, null, null),
                new RdWorkorderCreateTool(null),
                new RdDraftManageTool(null, null, null, null)
        )));
    }

    private List<ExecutionResult> rootCauseResults() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("offeringName", "畅享128");
        return List.of(ExecutionResult.ok("swrl_root_cause", data));
    }

    @Test
    void gateDropsSuggestionsReferencingUnknownTools() {
        when(llmService.completePrompt(contains("swrl_root_cause"))).thenReturn(
                "[{\"text\":\"生成处置工单\",\"tool\":\"swrl_root_cause\"},"
                        + "{\"text\":\"导出稽核清单\",\"tool\":\"fabricated_tool\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), new SessionContext());

        // 非法工具话术剔除，仅保留白名单内候选
        assertEquals(1, followUps.size());
        assertEquals("生成处置工单", followUps.get(0));
    }

    @Test
    void gateDropsAllWhenNoValidToolFallsBackToHandoffs() {
        when(llmService.completePrompt(contains("swrl_root_cause"))).thenReturn(
                "[{\"text\":\"幻觉能力A\",\"tool\":\"no_such_tool\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), new SessionContext());

        // 全部被守门剔除 → 回退 handoffs 确定性兜底（swrl_root_cause 声明承接 sparql_query/swrl_risk_audit）
        assertEquals(2, followUps.size());
        assertTrue(followUps.stream().anyMatch(s -> s.contains("风险稽核")));
        assertTrue(followUps.stream().anyMatch(s -> s.contains("数据查询")));
    }

    @Test
    void rdSceneWhitelistExcludesOpsTools() {
        when(llmService.completePrompt(contains("rd_compliance"))).thenReturn(
                "[{\"text\":\"对草稿重跑合规校验\",\"tool\":\"rd_compliance\"},"
                        + "{\"text\":\"发起风险稽核\",\"tool\":\"swrl_risk_audit\"}]");
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "生成家庭套餐配置", List.of(ExecutionResult.ok("rd_draft_generate", Map.of("nl_answer", "已生成"))),
                rdContext());

        // 运营工具 swrl_risk_audit 不在研发场景白名单内，被剔除
        assertEquals(1, followUps.size());
        assertEquals("对草稿重跑合规校验", followUps.get(0));
    }

    @Test
    void promptContainsCapabilityListFromToolDescriptions() {
        when(llmService.completePrompt(contains("rd_config_search"))).thenReturn(
                "[{\"text\":\"检索历史方案\",\"tool\":\"rd_config_search\"}]");
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "解析方案文档",
                List.of(ExecutionResult.ok("rd_doc_parse", Map.of("nl_answer", "解析出 3 个草稿"))),
                rdContext());

        assertEquals(List.of("检索历史方案"), followUps);
    }

    // ── 优化后新增覆盖：结果内容注入 / 承接链提示 / 历史去重 / 补位重试 / 失败分支 ──

    @Test
    void promptInjectsResultKeyContent() {
        // 结果关键内容（nl_answer）须进入 prompt，LLM 才能引用具体数字/对象
        when(llmService.completePrompt(contains("已创建 2 个配置工单"))).thenReturn(
                "[{\"text\":\"提交这 2 个工单\",\"tool\":\"rd_draft_manage\"}]");
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "批量开单",
                List.of(ExecutionResult.ok("rd_workorder_create", Map.of("nl_answer", "已创建 2 个配置工单"))),
                rdContext());

        assertEquals(List.of("提交这 2 个工单"), followUps);
    }

    @Test
    void promptInjectsHandoffChains() {
        // 工具自声明承接链（getHandoffs）须注入 prompt，引导任务链方向
        when(llmService.completePrompt(contains("典型后续动作"))).thenReturn(
                "[{\"text\":\"对高风险商品发起稽核\",\"tool\":\"swrl_risk_audit\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "查一下畅享128的数据",
                List.of(ExecutionResult.ok("sparql_query", Map.of("nl_answer", "共 5 条记录"))),
                new SessionContext());

        assertEquals(List.of("对高风险商品发起稽核"), followUps);
    }

    @Test
    void duplicateSuggestionsOfRecentHistoryAreDropped() {
        // 会话近期已建议过的内容，本轮 LLM 再生成时须被守门剔除
        SessionContext context = new SessionContext();
        context.addHistoryEntry("assistant", "对影响最大的渠道A生成处置工单");

        when(llmService.completePrompt(contains("禁止重复"))).thenReturn(
                "[{\"text\":\"对影响最大的渠道A生成处置工单\",\"tool\":\"swrl_root_cause\"},"
                        + "{\"text\":\"查看高风险商品详情\",\"tool\":\"swrl_risk_audit\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), context);

        // 重复候选被剔除，仅保留不重复的
        assertEquals(List.of("查看高风险商品详情"), followUps);
    }

    @Test
    void retryBoostsCandidatesWhenGateLeavesTooFew() {
        // 第一轮 3 条中 2 条被守门剔除 → 剩 1 条触发补位重试，重试轮补足
        when(llmService.completePrompt(contains("swrl_root_cause")))
                .thenReturn("[{\"text\":\"生成处置工单\",\"tool\":\"fabricated_x\"},"
                        + "{\"text\":\"和上月对比呢\",\"tool\":\"nope_y\"},"
                        + "{\"text\":\"生成处置工单\",\"tool\":\"swrl_root_cause\"}]",
                        "[{\"text\":\"查看渠道明细\",\"tool\":\"sparql_query\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), new SessionContext());

        // 第一轮守门剩 1 条 + 重试轮补 1 条（去重合并）
        assertEquals(List.of("生成处置工单", "查看渠道明细"), followUps);
    }

    @Test
    void failureResultPromptsRecoveryAdvice() {
        // 失败结果：prompt 要求修复性建议，LLM 正常返回时守门放行
        when(llmService.completePrompt(contains("修复路径"))).thenReturn(
                "[{\"text\":\"换个说法重试\",\"tool\":\"swrl_root_cause\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑",
                List.of(ExecutionResult.fail("swrl_root_cause", "推理引擎超时")),
                new SessionContext());

        assertEquals(List.of("换个说法重试"), followUps);
    }

    @Test
    void failureFallbackGivesRetryAdvice() {
        // LLM 失败 + 结果失败 → 确定性兜底给修复性指令
        when(llmService.completePrompt(contains("swrl_root_cause"))).thenThrow(new IllegalStateException("llm down"));
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑",
                List.of(ExecutionResult.fail("swrl_root_cause", "推理引擎超时")),
                new SessionContext());

        assertEquals(List.of("换一种说法重新发起刚才的查询"), followUps);
    }

    @Test
    void handoffFallbackUsesToolLabels() {
        // LLM 不可用 → handoffs 兜底话术由承接工具的业务标签组成（指令式：动作开头）
        when(llmService.completePrompt(contains("rd_workorder_create"))).thenThrow(new IllegalStateException("llm down"));
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "解析方案文档",
                List.of(ExecutionResult.ok("rd_doc_parse", Map.of("nl_answer", "解析完成"))),
                rdContext());

        // rd_doc_parse 声明承接 rd_draft_extract（标签：套餐抽取）与 rd_workorder_create（标签：批量落库开单）
        assertEquals(2, followUps.size());
        assertTrue(followUps.get(0).contains("套餐抽取") || followUps.get(0).contains("批量落库开单"));
    }

    @Test
    void openEndedSuggestionsAreDroppedByGate() {
        // 守门剔除开放式追问/方向性暗示：这些话术系统无法直接处理，不是明确指令
        when(llmService.completePrompt(contains("swrl_root_cause"))).thenReturn(
                "[{\"text\":\"您想继续做什么呢？\",\"tool\":\"swrl_root_cause\"},"
                        + "{\"text\":\"可以看看别的数据\",\"tool\":\"sparql_query\"},"
                        + "{\"text\":\"对畅享128发起风险稽核\",\"tool\":\"swrl_risk_audit\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), new SessionContext());

        // 仅明确指令（动词+对象）保留，开放式问题与暗示剔除
        assertEquals(List.of("对畅享128发起风险稽核"), followUps);
    }

    private SessionContext rdContext() {
        SessionContext context = new SessionContext();
        context.setScene("rd");
        return context;
    }
}
