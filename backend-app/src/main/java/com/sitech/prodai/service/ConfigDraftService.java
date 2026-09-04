package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置草稿持久化服务（R2 Phase2 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：草稿 CRUD（pd_ai_ontology_instance，ontology_code=offering_config）、
 * 提交闭环（合规 → 发布 → 状态沉淀）与摘要视图。
 * 合规校验与本体发布分别经 {@link DraftComplianceChecker} / {@link DraftPublisher}
 * 两个函数式接口回调宿主，避免环依赖与图缓存所有权外泄。
 */
@Service
public class ConfigDraftService {

    private static final Logger log = LoggerFactory.getLogger(ConfigDraftService.class);

    private static final String OFFERING_CONFIG_CODE = "offering_config";
    private static final String DRAFT_JSON_KEY = "_draft_json";
    private static final String CLIENT_ID_KEY = "client_id";

    private final ObjectMapper objectMapper;
    private final OntologyInstanceMapper instanceMapper;
    private final ConfigMessageProjector messageProjector;
    private final DraftComplianceChecker complianceChecker;
    private final DraftPublisher draftPublisher;

    /** 合规校验回调：入参草稿，出参含 compliancePass / issues。 */
    public interface DraftComplianceChecker {
        Map<String, Object> checkCompliance(Map<String, Object> draft);
    }

    /** 本体沉淀回调：入参草稿，出参含 success / offeringId / trace_id。 */
    public interface DraftPublisher {
        Map<String, Object> publishConfigDraft(Map<String, Object> draft);
    }

    public ConfigDraftService(ObjectMapper objectMapper,
                              OntologyInstanceMapper instanceMapper,
                              ConfigMessageProjector messageProjector,
                              DraftComplianceChecker complianceChecker,
                              DraftPublisher draftPublisher) {
        this.objectMapper = objectMapper;
        this.instanceMapper = instanceMapper;
        this.messageProjector = messageProjector;
        this.complianceChecker = complianceChecker;
        this.draftPublisher = draftPublisher;
    }

    /**
     * 持久化配置草稿（pd_ai_ontology_instance），绑定 session/user，刷新可恢复。
     */
    @Transactional
    public Map<String, Object> saveConfigDraft(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draftInput = req.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) req.get("draft")
                : (req.containsKey("offeringName") || req.containsKey("offerName") ? req : Map.of());
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null || draftInput.isEmpty() ? Map.of() : deepCopy(draftInput));
        String sessionId = str(firstNonEmpty(req.get("sessionId"), req.get("session_id")));
        String userId = str(firstNonEmpty(req.get("userId"), req.get("user_id"), "anonymous"));
        String clientId = str(firstNonEmpty(req.get("clientId"), req.get("client_id"), draft.get("clientId")));
        Long draftId = parseLong(req.get("draftId") != null ? req.get("draftId") : req.get("draft_id"));

        // 语义清晰化：带 draftId 入参 → 按主键更新（查不到即报错，草稿已不存在，不静默换行）；
        // 未带 draftId → 直接新增。clientId 仅作归属记录，不再用于反查复用。
        OntologyInstance entity;
        if (draftId != null) {
            entity = instanceMapper.selectById(draftId);
            if (entity == null || !OFFERING_CONFIG_CODE.equals(entity.getOntologyCode())) {
                return Map.of("success", false, "message", "草稿不存在或已删除: " + draftId, "draftId", draftId);
            }
            entity.setUserId(userId);
            if (!sessionId.isBlank()) {
                entity.setSessionId(sessionId);
            }
            if (!"submitted".equals(entity.getStatus())) {
                entity.setStatus("draft");
            }
        } else {
            entity = new OntologyInstance();
            entity.setOntologyCode(OFFERING_CONFIG_CODE);
            entity.setStatus("draft");
            entity.setUserId(userId);
            if (!sessionId.isBlank()) {
                entity.setSessionId(sessionId);
            }
        }

        Map<String, Object> store = new LinkedHashMap<>();
        store.put(CLIENT_ID_KEY, clientId.isBlank() ? "P" + Instant.now().toEpochMilli() : clientId);
        store.put("offeringName", String.valueOf(firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), "")));
        store.put("monthlyFee", String.valueOf(firstNonEmpty(draft.get("monthlyFee"), draft.get("fixedFeeAmount"), "")));
        store.put("bizScenario", str(draft.get("bizScenario")));
        store.put("channelScope", str(draft.get("channelScope")));
        store.put("compliancePass", String.valueOf(req.getOrDefault("compliancePass", draft.get("compliancePass"))));
        try {
            store.put(DRAFT_JSON_KEY, objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            throw new IllegalStateException("serialize draft failed: " + e.getMessage(), e);
        }
        entity.setData(store);
        if (entity.getId() == null) {
            instanceMapper.insert(entity);
        } else {
            instanceMapper.updateById(entity);
        }
        OntologyInstance saved = entity;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("draftId", saved.getId());
        body.put("clientId", store.get(CLIENT_ID_KEY));
        body.put("status", saved.getStatus());
        body.put("sessionId", saved.getSessionId());
        body.put("draft", draft);
        body.put("message", "配置草稿已持久化");
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listConfigDrafts(String sessionId, String userId, String status) {
        List<OntologyInstance> rows;
        if (sessionId != null && !sessionId.isBlank()) {
            rows = findTop50Instances(w -> w.eq(OntologyInstance::getSessionId, sessionId.trim()));
        } else if (userId != null && !userId.isBlank()) {
            rows = findTop50Instances(w -> w.eq(OntologyInstance::getUserId, userId.trim()));
        } else if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            rows = findTop50Instances(w -> w.eq(OntologyInstance::getStatus, status.trim()));
        } else {
            rows = findTop50Instances(w -> {});
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)
                && (sessionId != null && !sessionId.isBlank() || userId != null && !userId.isBlank())) {
            String st = status.trim();
            rows = rows.stream().filter(r -> st.equalsIgnoreCase(r.getStatus())).collect(Collectors.toList());
        }
        List<Map<String, Object>> items = rows.stream().map(this::toDraftSummary).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("items", items);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConfigDraft(Long draftId) {
        OntologyInstance entity = findInstanceByIdAndOntologyCode(draftId);
        if (entity == null) {
            return Map.of("success", false, "message", "草稿不存在: " + draftId);
        }
        Map<String, Object> body = new LinkedHashMap<>(toDraftSummary(entity));
        body.put("success", true);
        body.put("draft", readDraftJson(entity));
        return body;
    }

    @Transactional
    public Map<String, Object> deleteConfigDraft(Long draftId) {
        OntologyInstance entity = findInstanceByIdAndOntologyCode(draftId);
        if (entity == null) {
            return Map.of("success", false, "message", "草稿不存在: " + draftId);
        }
        instanceMapper.deleteById(entity.getId());
        return Map.of("success", true, "message", "草稿已删除", "draftId", draftId);
    }

    /**
     * 智检通过后闭环：合规 → 沉淀本体。
     */
    @Transactional
    public Map<String, Object> submitConfigDraft(Map<String, Object> request) {
        Map<String, Object> req = request == null ? Map.of() : request;
        @SuppressWarnings("unchecked")
        Map<String, Object> draftInput = req.get("draft") instanceof Map<?, ?>
                ? (Map<String, Object>) req.get("draft")
                : Map.of();
        Long draftId = parseLong(req.get("draftId") != null ? req.get("draftId") : req.get("draft_id"));
        Map<String, Object> draft = deepCopy(draftInput);
        if (draft.isEmpty() && draftId != null) {
            OntologyInstance existing = findInstanceByIdAndOntologyCode(draftId);
            if (existing != null) {
                draft = readDraftJson(existing);
            }
        }
        if (draft.isEmpty()) {
            return Map.of("success", false, "message", "缺少可提交的配置草稿");
        }

        Map<String, Object> persistReq = new LinkedHashMap<>(req);
        persistReq.put("draft", draft);
        Map<String, Object> saved = saveConfigDraft(persistReq);
        Long persistedId = parseLong(saved.get("draftId"));

        Map<String, Object> compliance = complianceChecker.checkCompliance(draft);
        if (!Boolean.TRUE.equals(compliance.get("compliancePass"))) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "合规未通过，拒绝提交");
            fail.put("issues", compliance.get("issues"));
            fail.put("compliancePass", false);
            fail.put("draftId", persistedId);
            return fail;
        }

        if (empty(draft.get("offeringId"))) {
            draft.put("offeringId", "OF-DRAFT-" + Instant.now().toEpochMilli());
        }
        Map<String, Object> published = draftPublisher.publishConfigDraft(draft);
        if (!Boolean.TRUE.equals(published.get("success"))) {
            Map<String, Object> fail = new LinkedHashMap<>(published);
            fail.put("draftId", persistedId);
            return fail;
        }

        String offeringId = str(published.get("offeringId"));

        final Map<String, Object> draftSnapshot = deepCopy(draft);
        if (persistedId != null) {
            OntologyInstance draftEntity = findInstanceByIdAndOntologyCode(persistedId);
            if (draftEntity != null) {
                OntologyInstance entity = draftEntity;
                entity.setStatus("submitted");
                entity.setSubmittedAt(LocalDateTime.now());
                Map<String, Object> store = new LinkedHashMap<>();
                if (entity.getData() != null) {
                    entity.getData().forEach(store::put);
                }
                store.put("offeringId", offeringId);
                store.put("compliancePass", "true");
                try {
                    store.put(DRAFT_JSON_KEY, objectMapper.writeValueAsString(draftSnapshot));
                } catch (Exception ignored) {
                    // keep previous json
                }
                entity.setData(store);
                instanceMapper.updateById(entity);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "已提交：合规通过 → 沉淀本体");
        body.put("draftId", persistedId);
        body.put("offeringId", offeringId);
        body.put("published", published);
        body.put("compliancePass", true);
        body.put("status", "submitted");
        body.put("trace_id", published.get("trace_id"));
        return body;
    }

    private OntologyInstance findInstanceByIdAndOntologyCode(Long id) {
        if (id == null) {
            return null;
        }
        OntologyInstance entity = instanceMapper.selectById(id);
        if (entity == null || !OFFERING_CONFIG_CODE.equals(entity.getOntologyCode())) {
            return null;
        }
        return entity;
    }

    private List<OntologyInstance> findTop50Instances(
            java.util.function.Consumer<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OntologyInstance>> extra) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OntologyInstance> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OntologyInstance>()
                        .eq(OntologyInstance::getOntologyCode, OFFERING_CONFIG_CODE)
                        .orderByDesc(OntologyInstance::getId)
                        .last("LIMIT 50");
        extra.accept(wrapper);
        return instanceMapper.selectList(wrapper);
    }

    private Map<String, Object> toDraftSummary(OntologyInstance entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("draftId", entity.getId());
        row.put("status", entity.getStatus());
        row.put("sessionId", entity.getSessionId());
        row.put("userId", entity.getUserId());
        row.put("submittedAt", entity.getSubmittedAt() == null ? null : entity.getSubmittedAt().toString());
        Map<String, String> data = entity.getData() == null ? Map.of() : entity.getData();
        row.put("clientId", data.get(CLIENT_ID_KEY));
        row.put("offeringName", data.getOrDefault("offeringName", ""));
        row.put("monthlyFee", data.getOrDefault("monthlyFee", ""));
        row.put("bizScenario", data.getOrDefault("bizScenario", ""));
        row.put("channelScope", data.getOrDefault("channelScope", ""));
        row.put("compliancePass", "true".equalsIgnoreCase(data.get("compliancePass")));
        row.put("offeringId", data.get("offeringId"));
        row.put("workOrderId", data.get("workOrderId"));
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDraftJson(OntologyInstance entity) {
        if (entity.getData() == null) {
            return new LinkedHashMap<>();
        }
        String json = entity.getData().get(DRAFT_JSON_KEY);
        if (json == null || json.isBlank()) {
            Map<String, Object> flat = new LinkedHashMap<>();
            entity.getData().forEach((k, v) -> {
                if (!DRAFT_JSON_KEY.equals(k) && !CLIENT_ID_KEY.equals(k)) {
                    flat.put(k, v);
                }
            });
            return flat;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("parse draft json failed id={}: {}", entity.getId(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return objectMapper.convertValue(source, new TypeReference<>() {});
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean empty(Object value) {
        return value == null || str(value).isBlank();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isEmptyStr(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private Object firstNonEmpty(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (!isEmptyStr(v)) {
                return v;
            }
        }
        return values.length > 0 ? values[values.length - 1] : null;
    }
}
