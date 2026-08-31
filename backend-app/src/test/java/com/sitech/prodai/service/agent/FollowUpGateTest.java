package com.sitech.prodai.service.agent;

import com.sitech.prodai.service.LlmService;
import com.sitech.prodai.service.agent.impl.DefaultPresenter;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.agent.model.SessionContext;
import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.SparqlQueryTool;
import com.sitech.prodai.service.agent.tool.SwrlRootCauseTool;
import com.sitech.prodai.service.agent.tool.SwrlRiskAuditTool;
import com.sitech.prodai.service.agent.tool.rd.RdConfigChatTool;
import com.sitech.prodai.service.agent.tool.rd.RdComplianceTool;
import com.sitech.prodai.service.agent.tool.rd.RdDiscoverTool;
import com.sitech.prodai.service.agent.tool.rd.RdFileParseTool;
import com.sitech.prodai.service.agent.tool.rd.RdSchemeCompareTool;
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
 * 全部剔除/LLM 失败时回退词典；prompt 中注入场景内真实能力清单。
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
        ));
    }

    private DefaultPresenter rdPresenter() {
        return new DefaultPresenter(llmService, List.of(
                new RdConfigChatTool(null, null),
                new RdFileParseTool(null, null),
                new RdComplianceTool(null, null),
                new RdDiscoverTool(null),
                new RdSchemeCompareTool(null)
        ));
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
    void gateDropsAllWhenNoValidToolFallsBackToDictionary() {
        when(llmService.completePrompt(contains("swrl_root_cause"))).thenReturn(
                "[{\"text\":\"幻觉能力A\",\"tool\":\"no_such_tool\"}]");
        DefaultPresenter presenter = opsPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "畅享128为什么下滑", rootCauseResults(), new SessionContext());

        // 全部被守门剔除 → 回退 @deprecated 词典分支
        assertTrue(followUps.contains("具体哪个渠道影响最大？"));
        assertTrue(followUps.contains("生成产品优化工单"));
    }

    @Test
    void rdSceneWhitelistExcludesOpsTools() {
        when(llmService.completePrompt(contains("rd_compliance"))).thenReturn(
                "[{\"text\":\"对草稿重跑合规校验\",\"tool\":\"rd_compliance\"},"
                        + "{\"text\":\"发起风险稽核\",\"tool\":\"swrl_risk_audit\"}]");
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "生成家庭套餐配置", List.of(ExecutionResult.ok("rd_config_chat", Map.of("nl_answer", "已生成"))),
                rdContext());

        // 运营工具 swrl_risk_audit 不在研发场景白名单内，被剔除
        assertEquals(1, followUps.size());
        assertEquals("对草稿重跑合规校验", followUps.get(0));
    }

    @Test
    void promptContainsCapabilityListFromToolDescriptions() {
        when(llmService.completePrompt(contains("rd_config_discover"))).thenReturn(
                "[{\"text\":\"检索历史方案\",\"tool\":\"rd_config_discover\"}]");
        DefaultPresenter presenter = rdPresenter();

        List<String> followUps = presenter.suggestFollowUps(
                "解析方案文档",
                List.of(ExecutionResult.ok("rd_file_parse", Map.of("nl_answer", "解析出 3 个草稿"))),
                rdContext());

        assertEquals(List.of("检索历史方案"), followUps);
    }

    private SessionContext rdContext() {
        SessionContext context = new SessionContext();
        context.setScene("rd");
        return context;
    }
}
