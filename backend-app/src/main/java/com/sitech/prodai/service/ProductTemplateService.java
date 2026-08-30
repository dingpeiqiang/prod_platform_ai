package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模板生命周期状态机（P2-5，设计方案 §13.3，独立类禁止并入 ProductOntologyService）。
 * <p>状态机：{@code draft ──review──► review ──publish(dryrun通过)──► published ──deprecate──► deprecated}；
 * rollback 可回退至 published/deprecated 任一历史版本；编辑新 draft 版本号++。
 * <p>双源约定（§13.3）：模板文件内 {@code status} 仅作注册种子初值，运行时生效状态以表 A 为准；
 * 启动时按表 A 最新 published 行重放运行时覆盖，保证重启后表 A 仍是唯一事实源。
 * <p>publish 流程（P1-6 四步守卫）：LOAD（表 A payload）→ VALIDATE（Registry §4.7 候选校验）
 * → SMOKE（P1-7 回归用例集断言 + P2-6 双引擎 diff 门禁，报告落表 B）→ COMMIT（Registry 运行时覆盖
 * + 表 A published 级联弃用旧版 + 表 B publish 日志）。守卫期间旧基线保持 published（并存窗口）。
 * <p>rollback 流程：取表 A 目标版本 payload → 守卫三步（同上）→ 成功记 rollback 日志。
 */
@Service
public class ProductTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ProductTemplateService.class);

    private final OntologyVersionService versionService;
    private final LastKnownGoodGuard guard;
    private final ProductTemplateRegistry templateRegistry;
    private final ProductConfigRegressionService regressionService;
    private final ObjectMapper objectMapper;

    public ProductTemplateService(OntologyVersionService versionService,
                                  LastKnownGoodGuard guard,
                                  ProductTemplateRegistry templateRegistry,
                                  ProductConfigRegressionService regressionService,
                                  ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.guard = guard;
        this.templateRegistry = templateRegistry;
        this.regressionService = regressionService;
        this.objectMapper = objectMapper;
    }

    /** 双源约定落地：启动时按表 A 最新 published 模板重放运行时覆盖（失败不阻断启动）。 */
    @PostConstruct
    public void reapplyPublishedOverrides() {
        try {
            for (Map<String, Object> template : templateRegistry.allResolved()) {
                String templateId = String.valueOf(template.get("template_id"));
                versionService.latestPublished(OntologyVersionService.TYPE_TEMPLATE, templateId)
                        .filter(row -> row.getPayload() != null && !row.getPayload().isBlank())
                        .ifPresent(row -> {
                            Map<String, Object> candidate = parsePayload(row);
                            if (candidate != null) {
                                Map<String, Object> report = templateRegistry.applyOverride(candidate);
                                log.info("[状态机] 启动重放 published 覆盖: {} v{} success={}",
                                        templateId, row.getVersion(), report.get("success"));
                            }
                        });
            }
        } catch (Exception e) {
            // 版本库不可用（如无 DB 的演示态）不阻断启动，classpath 种子继续生效
            log.info("[状态机] 启动重放 published 覆盖跳过: {}", e.getMessage());
        }
    }

    /**
     * 编辑新 draft：版本号++（patch 位）；同版本既有 draft 行允许覆盖更新。
     */
    public Map<String, Object> saveDraft(String templateId, Map<String, Object> payload,
                                         String author, String summary) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (templateId == null || templateId.isBlank() || payload == null) {
            body.put("success", false);
            body.put("message", "templateId 与 payload 均必填");
            return body;
        }
        String payloadId = String.valueOf(payload.get("template_id"));
        if (!templateId.equals(payloadId)) {
            body.put("success", false);
            body.put("message", "payload.template_id 与路径不一致: " + payloadId + " != " + templateId);
            return body;
        }
        List<OntologyAssetVersion> rows = versionService.listVersions(OntologyVersionService.TYPE_TEMPLATE, templateId);
        String nextVersion = nextVersion(rows, payload);
        Map<String, Object> draftPayload = new LinkedHashMap<>(payload);
        draftPayload.put("version", nextVersion);
        OntologyAssetVersion row = versionService.register(OntologyVersionService.TYPE_TEMPLATE, templateId,
                nextVersion, OntologyVersionService.STATUS_DRAFT, author, summary,
                toJson(draftPayload));
        body.put("success", true);
        body.put("templateId", templateId);
        body.put("version", nextVersion);
        body.put("status", OntologyVersionService.STATUS_DRAFT);
        body.put("versionRowId", row.getId());
        body.put("message", "draft 已登记（编辑版本号++）");
        return body;
    }

    /** draft ──review──► review。 */
    public Map<String, Object> submitReview(String templateId, String version, String operator) {
        OntologyAssetVersion row = requireRow(templateId, version);
        try {
            versionService.transition(row.getId(), OntologyVersionService.STATUS_DRAFT,
                    OntologyVersionService.STATUS_REVIEW, operator, "submit-review", null);
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        }
        return ok(templateId, version, "draft 已提交评审（review）");
    }

    /**
     * review ──publish(dryrun通过)──► published：P1-6 四步守卫 + Registry 运行时覆盖。
     */
    public Map<String, Object> publish(String templateId, String version, String operator) {
        OntologyAssetVersion row = requireRow(templateId, version);
        if (!OntologyVersionService.STATUS_REVIEW.equals(row.getStatus())) {
            return fail("仅 review 态可发布（当前 " + row.getStatus() + "），请先 submit-review");
        }
        Map<String, Object> candidate = parsePayload(row);
        if (candidate == null) {
            return fail("表 A payload 解析失败，无法发布");
        }
        Map<String, Object> dryrun = dryrunReport();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder(OntologyVersionService.TYPE_TEMPLATE, templateId,
                        () -> candidate,
                        pending -> commitPublish(row, (Map<String, Object>) pending, operator, dryrun))
                .version(version)
                .author(operator)
                .summary("template publish: " + templateId)
                .payload(row.getPayload())
                .validator(pending -> {
                    List<String> errors = new ArrayList<>(templateRegistry.validateCandidate(pending));
                    if (!templateId.equals(String.valueOf(pending.get("template_id")))) {
                        errors.add("候选 template_id 与资产编码不一致: "
                                + pending.get("template_id") + " != " + templateId);
                    }
                    return errors;
                })
                .smoke(pending -> smoke(dryrun))
                .build();
        Map<String, Object> report = guard.execute(request);
        report.put("templateId", templateId);
        report.put("version", version);
        return report;
    }

    /**
     * rollback：取表 A 目标版本 payload → 守卫三步 → 成功记 rollback 日志；
     * 目标版本重新 published，现行 published 级联弃用（单活版本语义）。
     */
    public Map<String, Object> rollback(String templateId, String toVersion, String operator) {
        OntologyAssetVersion target = requireRow(templateId, toVersion);
        Map<String, Object> candidate = parsePayload(target);
        if (candidate == null) {
            return fail("目标版本 payload 解析失败，无法回滚: v" + toVersion);
        }
        Map<String, Object> dryrun = dryrunReport();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder(OntologyVersionService.TYPE_TEMPLATE, templateId,
                        () -> candidate,
                        pending -> commitRollback(target, operator, dryrun))
                .version(toVersion)
                .author(operator)
                .summary("template rollback: " + templateId + " -> " + toVersion)
                .payload(target.getPayload())
                .validator(templateRegistry::validateCandidate)
                .smoke(pending -> smoke(dryrun))
                .build();
        Map<String, Object> report = guard.execute(request);
        report.put("templateId", templateId);
        report.put("rolledBackTo", toVersion);
        return report;
    }

    /** published ──deprecate──► deprecated。 */
    public Map<String, Object> deprecate(String templateId, String version, String operator, String reason) {
        OntologyAssetVersion row = requireRow(templateId, version);
        try {
            versionService.transition(row.getId(), OntologyVersionService.STATUS_PUBLISHED,
                    OntologyVersionService.STATUS_DEPRECATED, operator, "deprecate",
                    reason == null ? null : Map.of("reason", reason));
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        }
        return ok(templateId, version, "模板已弃用（deprecated）");
    }

    /** 版本列表 + 逐行动作日志（表 A/表 B 视图）。 */
    public Map<String, Object> versions(String templateId) {
        List<OntologyAssetVersion> rows = versionService.listVersions(
                OntologyVersionService.TYPE_TEMPLATE, templateId);
        List<Map<String, Object>> items = rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("version", row.getVersion());
            item.put("status", row.getStatus());
            item.put("author", row.getAuthor());
            item.put("summary", row.getSummary());
            item.put("createdAt", String.valueOf(row.getCreatedAt()));
            item.put("publishedAt", String.valueOf(row.getPublishedAt()));
            item.put("deprecatedAt", String.valueOf(row.getDeprecatedAt()));
            item.put("logs", versionService.logsOf(row.getId()).stream()
                    .map(l -> Map.of("action", String.valueOf(l.getAction()),
                            "operator", String.valueOf(l.getOperator()),
                            "createdAt", String.valueOf(l.getCreatedAt())))
                    .collect(Collectors.toList()));
            return item;
        }).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("templateId", templateId);
        body.put("versions", items);
        return body;
    }

    // ------------------------------------------------------------------
    // COMMIT 步（守卫 SMOKE 通过后执行；旧基线在此前保持 published）
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void commitPublish(OntologyAssetVersion row, Map<String, Object> candidate,
                               String operator, Map<String, Object> dryrun) {
        Map<String, Object> overrideReport = templateRegistry.applyOverride(candidate);
        if (!Boolean.TRUE.equals(overrideReport.get("success"))) {
            throw new IllegalStateException("Registry 运行时覆盖被拒: " + overrideReport.get("errors"));
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dryrun", dryrun);
        versionService.publish(row.getId(), operator, detail);
    }

    private void commitRollback(OntologyAssetVersion target, String operator, Map<String, Object> dryrun) {
        Map<String, Object> candidate = parsePayload(target);
        Map<String, Object> overrideReport = templateRegistry.applyOverride(candidate);
        if (!Boolean.TRUE.equals(overrideReport.get("success"))) {
            throw new IllegalStateException("Registry 运行时覆盖被拒: " + overrideReport.get("errors"));
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dryrun", dryrun);
        versionService.publish(target.getId(), operator, detail);
        versionService.log(target.getId(), "rollback", operator, Map.of(
                "event", "rollback",
                "templateId", target.getAssetCode(),
                "toVersion", target.getVersion(),
                "dryrun", dryrun));
    }

    /**
     * SMOKE 步（P2-7 回接：P1-7 回归断言，同步将报告落 dryrun 供 COMMIT 时落表 B）。
     * <p>P2-6 双引擎 diff 门禁已随 Java inferFields 分支清理一并下线（P2-7），
     * derive_rules 引擎成为唯一推理实现，回归断言即守卫。
     */
    private List<Map<String, Object>> smoke(Map<String, Object> dryrun) {
        List<Map<String, Object>> failures = new ArrayList<>(
                regressionService.smokeAgainstGraph(null));
        dryrun.put("smoke", true);
        return failures;
    }

    /** dryrun（§13.3）：P1-7 回归用例集（SMOKE 步执行）。 */
    private Map<String, Object> dryrunReport() {
        Map<String, Object> dryrun = new LinkedHashMap<>();
        dryrun.put("mode", "regression_cases");
        dryrun.put("note", "SMOKE 步执行 P1-7 回归断言，报告随本节点落表 B");
        return dryrun;
    }

    private OntologyAssetVersion requireRow(String templateId, String version) {
        return versionService.findVersion(OntologyVersionService.TYPE_TEMPLATE, templateId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "模板版本行不存在: " + templateId + " v" + version));
    }

    /** 编辑新 draft 版本号++：取最大语义化版本 patch 位 +1（无历史行用 payload 内建版本）。 */
    private String nextVersion(List<OntologyAssetVersion> rows, Map<String, Object> payload) {
        int maxPatch = -1;
        String base = null;
        for (OntologyAssetVersion row : rows) {
            String[] parts = row.getVersion().split("\\.");
            if (parts.length == 3 && parts[0].matches("\\d+") && parts[1].matches("\\d+")
                    && parts[2].matches("\\d+")) {
                int patch = Integer.parseInt(parts[2]);
                if (base == null) {
                    base = parts[0] + "." + parts[1];
                }
                maxPatch = Math.max(maxPatch, patch);
            }
        }
        if (base == null) {
            String builtIn = String.valueOf(payload.get("version"));
            return builtIn == null || builtIn.isBlank() || "null".equals(builtIn) ? "1.0.0" : builtIn;
        }
        return base + "." + (maxPatch + 1);
    }

    private Map<String, Object> parsePayload(OntologyAssetVersion row) {
        try {
            return objectMapper.readValue(row.getPayload(), new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("[状态机] payload 解析失败: {} v{} - {}", row.getAssetCode(), row.getVersion(), e.getMessage());
            return null;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("payload 序列化失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> ok(String templateId, String version, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("templateId", templateId);
        body.put("version", version);
        body.put("message", message);
        return body;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
