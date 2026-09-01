package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.domain.entity.OntologyVersionLog;
import com.sitech.prodai.mapper.OntologyAssetVersionMapper;
import com.sitech.prodai.mapper.OntologyVersionLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private OntologyAssetVersionMapper versionMapper;
    @Mock
    private OntologyVersionLogMapper logMapper;

    private final AtomicLong rowSeq = new AtomicLong();
    private final AtomicLong logSeq = new AtomicLong();
    private final Map<Long, OntologyAssetVersion> rowStore = new LinkedHashMap<>();
    private final Map<Long, List<OntologyVersionLog>> logStore = new LinkedHashMap<>();

    private ObjectMapper mapper;
    private ProductTemplateRegistry registry;
    private ProductTemplateService templateService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = new ObjectMapper();
        // 无 Spring 容器：手动初始化 MyBatis Plus 实体元数据缓存（LambdaQueryWrapper 列解析依赖）
        initTableInfoIfAbsent(OntologyAssetVersion.class);
        initTableInfoIfAbsent(OntologyVersionLog.class);
        // 表 A 内存行为：insert 分配 id；selectById/selectOne/selectList 按存储 + Wrapper 条件过滤
        lenient().doAnswer(inv -> {
            OntologyAssetVersion row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(rowSeq.incrementAndGet());
            }
            if (row.getCreatedAt() == null) {
                row.setCreatedAt(java.time.LocalDateTime.now());
            }
            rowStore.put(row.getId(), row);
            return 1;
        }).when(versionMapper).insert(any(OntologyAssetVersion.class));
        lenient().when(versionMapper.updateById(any(OntologyAssetVersion.class))).thenReturn(1);
        lenient().when(versionMapper.selectById(any()))
                .thenAnswer(inv -> rowStore.get(inv.getArgument(0, Long.class)));
        lenient().when(versionMapper.selectOne(any()))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.core.conditions.AbstractWrapper<OntologyAssetVersion, ?, ?> wrapper =
                            inv.getArgument(0);
                    List<OntologyAssetVersion> rows = filterByWrapper(rowStore.values(), wrapper);
                    // 对齐 SQL 语义：orderByDesc(published_at/created_at) + LIMIT 1 → 取首行
                    return rows.isEmpty() ? null : rows.get(0);
                });
        // 内存翻译 Wrapper：eq 条件按 paramNameValuePairs 值过滤，orderByDesc 排序，last("LIMIT n") 截断
        lenient().when(versionMapper.selectList(any()))
                .thenAnswer(inv -> filterByWrapper(rowStore.values(), inv.getArgument(0)));
        // 表 B 内存行为
        lenient().when(logMapper.insert(any(com.sitech.prodai.domain.entity.OntologyVersionLog.class)))
                .thenAnswer(inv -> {
                    OntologyVersionLog row = inv.getArgument(0);
                    if (row.getId() == null) {
                        row.setId(logSeq.incrementAndGet());
                    }
                    if (row.getCreatedAt() == null) {
                        row.setCreatedAt(java.time.LocalDateTime.now());
                    }
                    logStore.computeIfAbsent(row.getVersionId(), k -> new ArrayList<>()).add(row);
                    return 1;
                });
        lenient().when(logMapper.selectList(any()))
                .thenAnswer(inv -> filterLogsByWrapper(logStore.values().stream()
                        .flatMap(List::stream)
                        .collect(java.util.stream.Collectors.toList()), inv.getArgument(0)));

        OntologyVersionService versionService = new OntologyVersionService(versionMapper, logMapper);
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
        return versionMapper.selectList(null).stream()
                .filter(r -> "familyBasePrc".equals(r.getAssetCode()) && version.equals(r.getVersion()))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    /**
     * 内存翻译 LambdaQueryWrapper：eq 条件按 paramNameValuePairs 过滤，
     * orderByDesc(PublishedAt/CreatedAt) 排序，last("LIMIT n") 截断。
     * 仅覆盖 {@code OntologyVersionService} 实际使用的 Wrapper 子集。
     */
    @SuppressWarnings("unchecked")
    private static List<OntologyAssetVersion> filterByWrapper(Collection<OntologyAssetVersion> rows,
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper<OntologyAssetVersion, ?, ?> wrapper) {
        if (wrapper == null) {
            return rows.stream().collect(java.util.stream.Collectors.toList());
        }
        String segment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        List<OntologyAssetVersion> out = rows.stream()
                .filter(r -> matchesAll(r, segment, params))
                .collect(java.util.stream.Collectors.toList());
        if (segment != null) {
            if (segment.contains("PUBLISHED_AT") || segment.contains("published_at")) {
                out.sort(Comparator.comparing(OntologyAssetVersion::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
            } else {
                out.sort(Comparator.comparing(OntologyAssetVersion::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
            }
        }
        if (segment != null && segment.toUpperCase().contains("LIMIT")) {
            int limit = Integer.parseInt(segment.replaceAll("(?i).*LIMIT\\s+(\\d+).*", "$1"));
            if (out.size() > limit) {
                out = new ArrayList<>(out.subList(0, limit));
            }
        }
        return out;
    }

    /**
     * 行与 Wrapper eq 条件按 SQL 段中的列名对位匹配（MPGENVAL 占位符 ↔ paramNameValuePairs）。
     * 仅覆盖 {@code OntologyVersionService} 实际使用的列：asset_type/asset_code/version/status。
     */
    private static boolean matchesAll(OntologyAssetVersion row, String segment,
            Map<String, Object> params) {
        if (segment == null || params.isEmpty()) {
            return true;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w+) = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}").matcher(segment);
        while (m.find()) {
            String column = m.group(1);
            String paramKey = m.group(2);
            Object value = params.get(paramKey);
            if (!matchesColumn(row, column, value)) {
                return false;
            }
        }
        return true;
    }

    /** 列名 → 实体取值 eq 匹配（仅 OntologyVersionService 用到的列）。 */
    private static boolean matchesColumn(OntologyAssetVersion row, String column, Object value) {
        return switch (column) {
            case "asset_type" -> Objects.equals(row.getAssetType(), value);
            case "asset_code" -> Objects.equals(row.getAssetCode(), value);
            case "version" -> Objects.equals(row.getVersion(), value);
            case "status" -> Objects.equals(row.getStatus(), value);
            default -> true;
        };
    }

    /** 表 B 日志内存翻译：version_id/domain/trace_id eq 过滤 + created_at 排序。 */
    @SuppressWarnings("unchecked")
    private static List<OntologyVersionLog> filterLogsByWrapper(List<OntologyVersionLog> rows,
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper<OntologyVersionLog, ?, ?> wrapper) {
        if (wrapper == null) {
            return rows;
        }
        String segment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        List<OntologyVersionLog> out = rows.stream()
                .filter(r -> matchesLogColumns(r, segment, params))
                .collect(java.util.stream.Collectors.toList());
        if (segment != null) {
            if (segment.contains("ASC")) {
                out.sort(Comparator.comparing(OntologyVersionLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
            } else {
                out.sort(Comparator.comparing(OntologyVersionLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
            }
        }
        return out;
    }

    /** 日志行与 Wrapper eq 条件按列名对位匹配（仅 OntologyVersionService 用到的列）。 */
    private static boolean matchesLogColumns(OntologyVersionLog row, String segment,
            Map<String, Object> params) {
        if (segment == null || params.isEmpty()) {
            return true;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w+) = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}").matcher(segment);
        while (m.find()) {
            String column = m.group(1);
            Object value = params.get(m.group(2));
            boolean hit = switch (column) {
                case "version_id" -> Objects.equals(row.getVersionId(), value);
                case "domain" -> Objects.equals(row.getDomain(), value);
                case "trace_id" -> Objects.equals(row.getTraceId(), value);
                default -> true;
            };
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /** 注册 MyBatis Plus 实体元数据（幂等）：让 LambdaQueryWrapper 可解析 SFunction → 列名。 */
    private static void initTableInfoIfAbsent(Class<?> entityType) {
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityType) == null) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new org.apache.ibatis.session.Configuration(), ""),
                    entityType);
        }
    }
}
