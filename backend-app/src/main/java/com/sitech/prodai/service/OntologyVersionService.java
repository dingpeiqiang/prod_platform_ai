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

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_REVIEW = "review";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_DEPRECATED = "deprecated";

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

    public List<OntologyVersionLog> logsOf(Long versionId) {
        return logRepository.findByVersionIdOrderByCreatedAtDesc(versionId);
    }
}
