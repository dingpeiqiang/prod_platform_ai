package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.domain.entity.OpsWorkOrder;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
import com.sitech.prodai.service.ProductOntologyService;
import com.sitech.prodai.service.agent.model.ExecutionResult;
import com.sitech.prodai.service.ops.OpsExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * rd_draft_manage 提交参数回归：确认门值与草稿一致；字段缺省不误改；应答成功/失败透传
 * （语义承接手册 chat-configure 的落库开单环节：凭 work_order_id 反查草稿后更新/提交）。
 */
@ExtendWith(MockitoExtension.class)
class RdDraftManageToolTest {

    @Mock
    private ProductOntologyService productOntologyService;
    @Mock
    private OpsWorkOrderMapper workOrderMapper;
    @Mock
    private OntologyInstanceMapper instanceMapper;
    @Mock
    private OpsExtractionService extractionService;

    private RdDraftManageTool tool;

    @BeforeEach
    void setUp() {
        tool = new RdDraftManageTool(productOntologyService, workOrderMapper, instanceMapper, extractionService);
    }

    @Test
    void updateWithNoIdentifiedChangeReturnsSuccessWithoutSave() {
        stubWorkOrderWithDraft(Map.of("offeringName", "家庭融合套餐-158元", "monthlyFee", "158"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("action", "update");
        params.put("work_order_id", "WO1");
        // 显式字段与草稿一致（无变化）；question 原话由 LLM 抽取也无新字段（extractionService 默认返回空 Map）
        params.put("offering_name", "家庭融合套餐-158元");
        params.put("monthly_fee", "158");
        params.put("question", "给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售");

        ExecutionResult result = tool.execute(params);

        assertTrue(result.isSuccess(), () -> "空变更应返回成功: " + result.getErrorMessage());
        assertEquals("确认值与草稿当前值一致，无需修改（工单 WO1 草稿保持不变）",
                String.valueOf(result.getData().get("nl_answer")));
        // 空变更不触发草稿回写
        verify(productOntologyService, never()).saveConfigDraft(any());
    }

    @Test
    void updateWithRealChangeStillSavesDraft() {
        stubWorkOrderWithDraft(Map.of("offeringName", "旧套餐名", "monthlyFee", "99"));
        when(productOntologyService.saveConfigDraft(any())).thenReturn(Map.of("success", true));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("action", "update");
        params.put("work_order_id", "WO1");
        params.put("monthly_fee", "158");

        ExecutionResult result = tool.execute(params);

        assertTrue(result.isSuccess(), () -> "有实际变更应返回成功: " + result.getErrorMessage());
        assertEquals(Map.of("monthlyFee", "158"), result.getData().get("changed_fields"));
        verify(productOntologyService).saveConfigDraft(any());
    }

    /** 工单定位打桩：payload.draftId=7，草稿内容可配置。 */
    private void stubWorkOrderWithDraft(Map<String, Object> draft) {
        OpsWorkOrder wo = new OpsWorkOrder();
        wo.setWorkOrderId("WO1");
        wo.setOfferingName(String.valueOf(draft.get("offeringName")));
        wo.setStatus("open");
        wo.setPayload(Map.of("draftId", 7));
        lenient().when(workOrderMapper.selectOne(any())).thenReturn(wo);
        lenient().when(productOntologyService.getConfigDraft(7L))
                .thenReturn(Map.of("success", true, "draft", new LinkedHashMap<>(draft)));
    }
}
