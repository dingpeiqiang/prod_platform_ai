package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.domain.entity.OntologyVersionLog;
import com.sitech.prodai.repository.OntologyAssetVersionRepository;
import com.sitech.prodai.repository.OntologyVersionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2-5 模板生命周期状态机验证（§13.3）：
 * <ul>
 *   <li>全流程：saveDraft（版本号++）→ submit-review → publish（守卫四步 + Registry 覆盖）
 *       → rollback（回旧版）→ deprecate；</li>
 *   <li>门禁：非 review 态拒发布；§4.7 校验失败阻断发布且 Registry 保留现行（last-known-good）；</li>
 *   <li>双源约定：表 A 状态为准，publish 后运行态版本与表 A 一致。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProductTemplateServiceTest {

    private static final String FAMILY_TEMPLATE = "classpath:ontologies/templates/familyBasePrc.json";

    @Mock
    private OntologyAssetVersionRepository versionRepository;
    @Mock
    private OntologyVersionLogRepository logRepository;

    private ObjectMapper mapper;
    private ProductTemplateRegistry registry;
    private ProductTemplateService templateService;
    private final AtomicLong rowSeq = new AtomicLong();
    private final AtomicLong logSeq = new AtomicLong();
    private final Map<Long, OntologyAssetVersion> rowStore = new LinkedHashMap<>();
    private final Map<Long, List<OntologyVersionLog>> logStore = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        // 表 A 内存仓储：save 分配 id，findBy* 按存储过滤
        lenient().doAnswer(inv -> {
            OntologyAssetVersion row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(rowSeq.incrementAndGet());
            }
            rowStore.put(row.getId(), row);
            return row;
        }).when(versionRepository).save(any());
        lenient().when(versionRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(rowStore.get(inv.getArgument(0, Long.class))));
        lenient().when(versionRepository.findByAssetTypeAndAssetCodeAndVersion(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> rowStore.values().stream()
                        .filter(r -> r.getAssetType().equals(inv.getArgument(0))
                                && r.getAssetCode().equals(inv.getArgument(1))
                                && r.getVersion().equals(inv.getArgument(2)))
                        .findFirst());
        lenient().when(versionRepository.findByAssetTypeAndAssetCodeOrderByCreatedAtDesc(anyString(), anyString()))
                .thenAnswer(inv -> rowStore.values().stream()
                        .filter(r -> r.getAssetType().equals(inv.getArgument(0))
                                && r.getAssetCode().equals(inv.getArgument(1)))
                        .collect(Collectors.toList()));
        lenient().when(versionRepository.findFirstByAssetTypeAndAssetCodeAndStatusOrderByPublishedAtDesc(
                        anyString(), anyString(), anyString()))
                .thenAnswer(inv -> rowStore.values().stream()
                        .filter(r -> r.getAssetType().equals(inv.getArgument(0))
                                && r.getAssetCode().equals(inv.getArgument(1))
                                && r.getStatus().equals(inv.getArgument(2)))
                        .findFirst());
        lenient().doAnswer(inv -> {
            OntologyVersionLog row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(logSeq.incrementAndGet());
            }
            logStore.computeIfAbsent(row.getVersionId(), k -> new ArrayList<>()).add(row);
            return row;
        }).when(logRepository).save(any());
        lenient().when(logRepository.findByVersionIdOrderByCreatedAtDesc(anyLong()))
                .thenAnswer(inv -> List.copyOf(logStore.getOrDefault(inv.getArgument(0, Long.class), List.of())));

        OntologyVersionService versionService = new OntologyVersionService(versionRepository, logRepository);
        registry = new ProductTemplateRegistry(mapper);
        registry.init();
        ProductConfigRegressionService regression = mock(ProductConfigRegressionService.class);
        lenient().when(regression.smokeAgainstGraph(any())).thenReturn(List.of());
        templateService = new ProductTemplateService(
                versionService, new LastKnownGoodGuard(versionService), registry, regression, mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lifecycleShouldCoverDraftReviewPublishRollbackDeprecate() throws Exception {
        Map<String, Object> basePayload = mapper.readValue(
                new DefaultResourceLoader().getResource(FAMILY_TEMPLATE).getInputStream(),
                new TypeReference<Map<String, Object>>() { });

        // 1. 基线发布：saveDraft(1.0.0，无历史行用内建版本) → review → publish
        Map<String, Object> draft0 = templateService.saveDraft("familyBasePrc", basePayload, "tester", "种子登记");
        assertEquals("1.0.0", draft0.get("version"));
        templateService.submitReview("familyBasePrc", "1.0.0", "op");
        Map<String, Object> publish0 = templateService.publish("familyBasePrc", "1.0.0", "op");
        assertTrue(Boolean.TRUE.equals(publish0.get("success")), "基线发布应通过守卫: " + publish0);

        // 2. 编辑新 draft 版本号++ → 1.0.1，再发布（1.0.0 级联弃用，单活版本语义）
        Map<String, Object> draft1 = templateService.saveDraft("familyBasePrc", basePayload, "tester", "调整资费");
        assertEquals("1.0.1", draft1.get("version"), "编辑应版本号++");
        templateService.submitReview("familyBasePrc", "1.0.1", "op");
        Map<String, Object> publishReport = templateService.publish("familyBasePrc", "1.0.1", "op");
        assertTrue(Boolean.TRUE.equals(publishReport.get("success")),
                "publish 应通过守卫: " + publishReport);
        assertEquals("1.0.1", registry.findByCategory("familyBasePrc").orElseThrow().get("version"),
                "运行态应切换为新版（双源约定：表 A 为准）");
        assertEquals(OntologyVersionService.STATUS_PUBLISHED, rowStatus("1.0.1"));
        assertEquals(OntologyVersionService.STATUS_DEPRECATED, rowStatus("1.0.0"), "旧基线级联弃用");

        // 3. 门禁：新 draft 未提审直接发布被拒
        templateService.saveDraft("familyBasePrc", basePayload, "tester", "第三版");
        Map<String, Object> earlyPublish = templateService.publish("familyBasePrc", "1.0.2", "op");
        assertFalse(Boolean.TRUE.equals(earlyPublish.get("success")), "非 review 态应拒发布");

        // 4. rollback → 1.0.0（published/deprecated 历史版本可回退）：运行态回退、目标行 re-publish
        Map<String, Object> rollbackReport = templateService.rollback("familyBasePrc", "1.0.0", "op");
        assertTrue(Boolean.TRUE.equals(rollbackReport.get("success")),
                "rollback 应通过守卫: " + rollbackReport);
        assertEquals("1.0.0", registry.findByCategory("familyBasePrc").orElseThrow().get("version"));
        assertEquals(OntologyVersionService.STATUS_PUBLISHED, rowStatus("1.0.0"));
        assertEquals(OntologyVersionService.STATUS_DEPRECATED, rowStatus("1.0.1"));

        // 5. deprecate：published → deprecated
        assertTrue(Boolean.TRUE.equals(templateService
                .deprecate("familyBasePrc", "1.0.0", "op", "下架演练").get("success")));
        assertEquals(OntologyVersionService.STATUS_DEPRECATED, rowStatus("1.0.0"));

        // 6. 表 B 动作日志可追溯
        Map<String, Object> versionView = templateService.versions("familyBasePrc");
        List<Map<String, Object>> versions = (List<Map<String, Object>>) versionView.get("versions");
        assertEquals(3, versions.size());
        assertTrue(versions.stream().flatMap(v -> ((List<Map<String, Object>>) v.get("logs")).stream())
                        .anyMatch(l -> "rollback".equals(l.get("action"))),
                "rollback 日志应落表 B");
    }

    @Test
    void publishShouldBeBlockedWhenValidationFailsAndKeepLastKnownGood() throws Exception {
        Map<String, Object> broken = new LinkedHashMap<>(mapper.readValue(
                new DefaultResourceLoader().getResource(FAMILY_TEMPLATE).getInputStream(),
                new TypeReference<Map<String, Object>>() { }));
        broken.put("fields", List.of()); // §4.7：fields 缺失或非法

        templateService.saveDraft("familyBasePrc", broken, "tester", "坏模板");
        templateService.submitReview("familyBasePrc", "1.0.0", "op");

        Map<String, Object> report = templateService.publish("familyBasePrc", "1.0.0", "op");
        assertFalse(Boolean.TRUE.equals(report.get("success")), "§4.7 校验失败应阻断发布");
        assertEquals("VALIDATE", report.get("step"), "应在 VALIDATE 步拦截");
        assertEquals("1.0.0", registry.findByCategory("familyBasePrc").orElseThrow().get("version"),
                "Registry 应保留现行版本（last-known-good）");
        assertEquals(OntologyVersionService.STATUS_REVIEW, rowStatus("1.0.0"), "失败行保留 review 态供复盘");
    }

    @Test
    void runtimeOverrideShouldRejectIllegalCandidate() throws Exception {
        Map<String, Object> basePayload = mapper.readValue(
                new DefaultResourceLoader().getResource(FAMILY_TEMPLATE).getInputStream(),
                new TypeReference<Map<String, Object>>() { });
        Map<String, Object> illegal = new LinkedHashMap<>(basePayload);
        illegal.put("derive_rules", List.of(Map.of(
                "when", Map.of("notExistField", "x"), "hidden", List.of("notExistField"))));

        Map<String, Object> report = registry.applyOverride(illegal);
        assertFalse(Boolean.TRUE.equals(report.get("success")), "非法候选应被拒");
        assertEquals(Boolean.TRUE, report.get("keptLastKnownGood"));
        assertNotEquals(illegal, registry.findByCategory("familyBasePrc").orElseThrow(),
                "运行态不得被非法候选污染");
        assertFalse(registry.validateCandidate(illegal).isEmpty(), "候选校验应报错");
        assertTrue(registry.validateCandidate(basePayload).isEmpty(), "合法候选应通过 §4.7 校验");
    }

    private String rowStatus(String version) {
        return rowStore.values().stream()
                .filter(r -> "familyBasePrc".equals(r.getAssetCode()) && version.equals(r.getVersion()))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }
}
