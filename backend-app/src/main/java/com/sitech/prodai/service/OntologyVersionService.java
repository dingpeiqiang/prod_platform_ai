package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.domain.entity.OntologyVersionLog;
import com.sitech.prodai.repository.OntologyAssetVersionRepository;
import com.sitech.prodai.repository.OntologyVersionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本体资产版本库服务（P1-5，独立类，禁止并入 ProductOntologyService）。
 * <p>版本登记（template / ops_rules / ttl，P1 批次）、发布 / 回退 / 弃用状态流转、动作日志落表 B。
 * 状态机完整版（draft→review→publish→deprecated）由 P2-5 ProductTemplateService 收口，此处提供底座。
 */
@Service
public class OntologyVersionService {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionService.class);

    public static final String TYPE_TEMPLATE = "template";
    public static final String TYPE_OPS_RULES = "ops_rules";
    public static final String TYPE_TTL = "ttl";
    public static final String TYPE_MESSAGE_PROJECTION = "message_projection";
    public static final String TYPE_ABOX_SNAPSHOT = "abox_snapshot";
    /** P3-4 双世界收敛：AdminController 本体管理资产（offering_config / tariff_filing_publicity 等）。 */
    public static final String TYPE_ONTOLOGY = "ontology";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_REVIEW = "review";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_DEPRECATED = "deprecated";

    /** P3-5 ① 审计域（表 B domain 列）：version / risk / config / batch */
    public static final String DOMAIN_VERSION = "version";
    public static final String DOMAIN_RISK = "risk";
    public static final String DOMAIN_CONFIG = "config";
    public static final String DOMAIN_BATCH = "batch";

    private final OntologyAssetVersionRepository versionRepository;
    private final OntologyVersionLogRepository logRepository;

    public OntologyVersionService(OntologyAssetVersionRepository versionRepository,
                                  OntologyVersionLogRepository logRepository) {
        this.versionRepository = versionRepository;
        this.logRepository = logRepository;
    }

    /**
     * 登记版本行（按 asset_type+asset_code+version 幂等 upsert；payload 为回滚唯一事实源）。
     * P1 阶段（无状态机）模板落地即默认按 published 注册。
     */
    @Transactional
    public OntologyAssetVersion register(String assetType, String assetCode, String version,
                                         String status, String author, String summary, String payload) {
        String effectiveStatus = status == null || status.isBlank() ? STATUS_PUBLISHED : status;
        OntologyAssetVersion row = versionRepository
                .findByAssetTypeAndAssetCodeAndVersion(assetType, assetCode, version)
                .orElseGet(OntologyAssetVersion::new);
        boolean newRow = row.getId() == null;
        row.setAssetType(assetType);
        row.setAssetCode(assetCode);
        row.setVersion(version);
        row.setStatus(effectiveStatus);
        row.setAuthor(author);
        row.setSummary(summary);
        row.setPayload(payload);
        if (STATUS_PUBLISHED.equals(effectiveStatus) && row.getPublishedAt() == null) {
            row.setPublishedAt(LocalDateTime.now());
        }
        OntologyAssetVersion saved = versionRepository.save(row);
        if (newRow) {
            log(saved.getId(), "publish", author, Map.of(
                    "event", "register",
                    "asset_type", assetType,
                    "asset_code", assetCode,
                    "version", version,
                    "status", effectiveStatus));
            log.info("[版本库] 登记 {}:{} v{} ({})", assetType, assetCode, version, effectiveStatus);
        }
        return saved;
    }

    /** 发布：目标行置 published，同资产其余 published 行级联 deprecated（单活版本语义）。 */
    @Transactional
    public OntologyAssetVersion publish(Long versionId, String operator, Map<String, Object> detail) {
        OntologyAssetVersion row = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本行不存在: " + versionId));
        List<OntologyAssetVersion> siblings = versionRepository
                .findByAssetTypeAndAssetCodeOrderByCreatedAtDesc(row.getAssetType(), row.getAssetCode());
        for (OntologyAssetVersion sibling : siblings) {
            if (!sibling.getId().equals(versionId) && STATUS_PUBLISHED.equals(sibling.getStatus())) {
                sibling.setStatus(STATUS_DEPRECATED);
                sibling.setDeprecatedAt(LocalDateTime.now());
                versionRepository.save(sibling);
                log(sibling.getId(), "deprecate", operator, Map.of("reason", "superseded_by", "version", row.getVersion()));
            }
        }
        row.setStatus(STATUS_PUBLISHED);
        row.setPublishedAt(LocalDateTime.now());
        row.setDeprecatedAt(null);
        OntologyAssetVersion saved = versionRepository.save(row);
        log(versionId, "publish", operator, detail == null ? Map.of() : detail);
        return saved;
    }

    /** 弃用。 */
    @Transactional
    public OntologyAssetVersion deprecate(Long versionId, String operator, String reason) {
        OntologyAssetVersion row = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本行不存在: " + versionId));
        row.setStatus(STATUS_DEPRECATED);
        row.setDeprecatedAt(LocalDateTime.now());
        OntologyAssetVersion saved = versionRepository.save(row);
        log(versionId, "deprecate", operator, reason == null ? Map.of() : Map.of("reason", reason));
        return saved;
    }

    /**
     * 通用状态流转（P2-5 底座）：期望状态不符抛错；流转门控由 {@code ProductTemplateService} 状态机收口。
     */
    @Transactional
    public OntologyAssetVersion transition(Long versionId, String expectedStatus, String targetStatus,
                                           String operator, String action, Map<String, Object> detail) {
        OntologyAssetVersion row = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本行不存在: " + versionId));
        if (expectedStatus != null && !expectedStatus.equals(row.getStatus())) {
            throw new IllegalStateException("状态流转被拒: 期望 " + expectedStatus + " 实际 "
                    + row.getStatus() + "（" + row.getAssetCode() + " v" + row.getVersion() + "）");
        }
        row.setStatus(targetStatus);
        OntologyAssetVersion saved = versionRepository.save(row);
        Map<String, Object> logDetail = new LinkedHashMap<>();
        logDetail.put("from", expectedStatus);
        logDetail.put("to", targetStatus);
        if (detail != null) {
            logDetail.putAll(detail);
        }
        log(versionId, action, operator, logDetail);
        return saved;
    }

    /** 动作日志（publish / rollback / deprecate / reload / override）。 */
    @Transactional
    public void log(Long versionId, String action, String operator, Map<String, Object> detail) {
        OntologyVersionLog row = new OntologyVersionLog();
        row.setVersionId(versionId);
        row.setAction(action);
        row.setOperator(operator);
        row.setDetail(detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail));
        logRepository.save(row);
    }

    /** P3-5 ① 非版本键控审计落盘（config 链路 / risk 覆盖 / batch 稽核，复用表 B 一表）。 */
    @Transactional
    public void recordLog(String domain, String traceId, String action, Map<String, Object> detail) {
        OntologyVersionLog row = new OntologyVersionLog();
        row.setDomain(domain == null ? DOMAIN_VERSION : domain);
        row.setTraceId(traceId);
        row.setAction(action);
        row.setDetail(detail == null ? new LinkedHashMap<>() : new LinkedHashMap<>(detail));
        logRepository.save(row);
    }

    /** config 域链路明细（按 created_at 升序 = 步骤先后序，回退内存态；返回各步 detail 载荷）。 */
    public List<Map<String, Object>> configTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return logRepository.findByDomainAndTraceIdOrderByCreatedAtAsc(DOMAIN_CONFIG, traceId)
                .stream().map(OntologyVersionLog::getDetail)
                .collect(java.util.stream.Collectors.toList());
    }

    /** risk 域最近 50 条审计（回读表 B 覆盖内存 last-50，保持 {at,action,detail,...} 展示形）。 */
    public List<Map<String, Object>> riskAuditLogs() {
        return logRepository.findTop50ByDomainOrderByCreatedAtDesc(DOMAIN_RISK)
                .stream().map(this::describeRow).collect(java.util.stream.Collectors.toList());
    }

    /** batch 域最近一次稽核快照（回读表 B detail 载荷，重启不丢）。 */
    public Optional<Map<String, Object>> latestBatchAudit() {
        return logRepository.findFirstByDomainOrderByCreatedAtDesc(DOMAIN_BATCH)
                .map(OntologyVersionLog::getDetail);
    }

    /** 审计行 → 展示 Map（对外 key 与既有内存视图一致）。 */
    private Map<String, Object> describeRow(OntologyVersionLog row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("at", row.getCreatedAt() == null
                ? ""
                : row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toString());
        out.put("action", row.getAction());
        out.put("domain", row.getDomain());
        out.put("traceId", row.getTraceId());
        out.put("detail", row.getDetail() == null ? Map.of() : new LinkedHashMap<>(row.getDetail()));
        return out;
    }

    /** last-known-good：同资产最新 published 行。 */
    public Optional<OntologyAssetVersion> latestPublished(String assetType, String assetCode) {
        return versionRepository.findFirstByAssetTypeAndAssetCodeAndStatusOrderByPublishedAtDesc(
                assetType, assetCode, STATUS_PUBLISHED);
    }

    public Optional<OntologyAssetVersion> findVersion(String assetType, String assetCode, String version) {
        return versionRepository.findByAssetTypeAndAssetCodeAndVersion(assetType, assetCode, version);
    }

    public List<OntologyAssetVersion> listVersions(String assetType, String assetCode) {
        return versionRepository.findByAssetTypeAndAssetCodeOrderByCreatedAtDesc(assetType, assetCode);
    }

    /** 按资产类型列出全部版本行（P3-4 双世界收敛：AdminController 本体列表聚合）。 */
    public List<OntologyAssetVersion> listByType(String assetType) {
        return versionRepository.findByAssetTypeOrderByCreatedAtDesc(assetType);
    }

    public List<OntologyVersionLog> logsOf(Long versionId) {
        return logRepository.findByVersionIdOrderByCreatedAtDesc(versionId);
    }

    /** 指定类型最新发布行（跨资产取 published_at 最近者；无返回空）。 */
    public Optional<OntologyAssetVersion> latestPublishedByType(String assetType) {
        return versionRepository.findFirstByAssetTypeAndStatusOrderByPublishedAtDesc(assetType, STATUS_PUBLISHED);
    }

    /** 指定类型已发布行数（跨资产）。 */
    public long countPublished(String assetType) {
        return versionRepository.findByAssetTypeAndStatus(assetType, STATUS_PUBLISHED).size();
    }
}
