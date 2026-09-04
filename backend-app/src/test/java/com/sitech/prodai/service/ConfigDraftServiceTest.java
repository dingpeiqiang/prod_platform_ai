package com.sitech.prodai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 Phase2 拆分回归：配置草稿域服务单测（保存/列表/删除/提交闭环）。
 */
@ExtendWith(MockitoExtension.class)
class ConfigDraftServiceTest {

    @Mock
    private OntologyInstanceMapper instanceMapper;

    private ConfigDraftService service;

    /** 合规回调桩：默认通过，可注入失败场景。 */
    private boolean compliancePass = true;
    /** 发布回调桩：记录最近一次发布草稿。 */
    private Map<String, Object> lastPublished;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        ConfigMessageProjector projector = new ConfigMessageProjector(mapper, new DefaultResourceLoader());
        projector.init();
        service = new ConfigDraftService(
                mapper,
                instanceMapper,
                projector,
                draft -> Map.of("compliancePass", compliancePass, "issues", List.of()),
                draft -> {
                    lastPublished = draft;
                    return Map.of("success", true,
                            "offeringId", draft.getOrDefault("offeringId", "OF-X"),
                            "trace_id", "cfg-publish-1");
                });
        lenient().when(instanceMapper.insert(any(OntologyInstance.class))).thenAnswer(inv -> {
            OntologyInstance e = inv.getArgument(0);
            e.setId(77L);
            return 1;
        });
        lenient().when(instanceMapper.updateById(any(OntologyInstance.class))).thenReturn(1);
    }

    @Test
    void saveConfigDraftShouldInsertNewDraftWithDefaults() {
        Map<String, Object> req = new LinkedHashMap<>();
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "测试套餐");
        draft.put("monthlyFee", 59);
        req.put("draft", draft);
        req.put("sessionId", "s-1");
        req.put("userId", "u-1");

        Map<String, Object> body = service.saveConfigDraft(req);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(77L, ((Number) body.get("draftId")).longValue());
        assertEquals("draft", body.get("status"));
        assertEquals("s-1", body.get("sessionId"));

        ArgumentCaptor<OntologyInstance> captor = ArgumentCaptor.forClass(OntologyInstance.class);
        verify(instanceMapper).insert(captor.capture());
        assertEquals("offering_config", captor.getValue().getOntologyCode());
        assertEquals("测试套餐", captor.getValue().getData().get("offeringName"));
    }

    @Test
    void saveConfigDraftShouldRejectMissingDraftOnUpdate() {
        when(instanceMapper.selectById(9L)).thenReturn(null);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("draftId", 9);
        req.put("draft", Map.of("offeringName", "x"));

        Map<String, Object> body = service.saveConfigDraft(req);

        assertFalse(Boolean.TRUE.equals(body.get("success")));
        verify(instanceMapper, never()).updateById(any(OntologyInstance.class));
    }

    @Test
    void getConfigDraftShouldReturnNotFoundForMissing() {
        when(instanceMapper.selectById(404L)).thenReturn(null);

        Map<String, Object> body = service.getConfigDraft(404L);

        assertFalse(Boolean.TRUE.equals(body.get("success")));
    }

    @Test
    void deleteConfigDraftShouldRemoveExisting() {
        OntologyInstance entity = new OntologyInstance();
        entity.setId(5L);
        entity.setOntologyCode("offering_config");
        when(instanceMapper.selectById(5L)).thenReturn(entity);

        Map<String, Object> body = service.deleteConfigDraft(5L);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        verify(instanceMapper).deleteById(5L);
    }

    @Test
    void submitConfigDraftShouldRejectWhenComplianceFails() {
        compliancePass = false;
        Map<String, Object> req = new LinkedHashMap<>();
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "违规套餐");
        req.put("draft", draft);

        Map<String, Object> body = service.submitConfigDraft(req);

        assertFalse(Boolean.TRUE.equals(body.get("success")));
        assertEquals(false, body.get("compliancePass"));
        assertTrue(body.containsKey("issues"));
    }

    @Test
    void submitConfigDraftShouldCloseLoopOnCompliancePass() {
        // insert 已被 setUp 桩为回填 id=77；selectById 需按主键返回对应实体以支撑提交后状态回写
        Map<Long, OntologyInstance> store = new LinkedHashMap<>();
        lenient().when(instanceMapper.selectById(any())).thenAnswer(inv -> store.get(((Number) inv.getArgument(0)).longValue()));
        lenient().when(instanceMapper.insert(any(OntologyInstance.class))).thenAnswer(inv -> {
            OntologyInstance e = inv.getArgument(0);
            e.setId(77L);
            store.put(77L, e);
            return 1;
        });

        Map<String, Object> req = new LinkedHashMap<>();
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringName", "合规套餐");
        draft.put("monthlyFee", 39);
        req.put("draft", draft);

        Map<String, Object> body = service.submitConfigDraft(req);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals("submitted", body.get("status"));
        assertEquals(true, body.get("compliancePass"));
        // 草稿无 offeringId 时应生成 OF-DRAFT-* 并回传
        assertTrue(String.valueOf(body.get("offeringId")).startsWith("OF-DRAFT-"));
        // 发布回调收到生成/透传的 offeringId
        assertTrue(lastPublished.containsKey("offeringId"));
        // 草稿状态回写 submitted
        ArgumentCaptor<OntologyInstance> captor = ArgumentCaptor.forClass(OntologyInstance.class);
        verify(instanceMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertEquals("submitted", captor.getValue().getStatus());
    }

    @Test
    void submitConfigDraftShouldFailWithoutDraft() {
        Map<String, Object> body = service.submitConfigDraft(Map.of());

        assertFalse(Boolean.TRUE.equals(body.get("success")));
        verify(instanceMapper, never()).insert(any(OntologyInstance.class));
    }

    @Test
    void listConfigDraftsShouldReturnEmptyList() {
        when(instanceMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> body = service.listConfigDrafts(null, null, null);

        assertTrue(Boolean.TRUE.equals(body.get("success")));
        assertEquals(0, ((Number) body.get("total")).intValue());
    }
}
