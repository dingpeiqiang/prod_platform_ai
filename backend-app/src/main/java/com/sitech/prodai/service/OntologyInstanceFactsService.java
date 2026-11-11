package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.domain.entity.OntologyInstance;
import com.sitech.prodai.mapper.OntologyInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体实例事实（ABox）实时读取服务。
 * <p>
 * 存储位置：H2 / MySQL 的 {@code pd_ai_ontology_instance}（{@code data_json} 单 JSON 列）。
 * 本服务只做「库 → Map 事实」的读取与解析，不做任何推理或断言——
 * 事实注入本体由 {@link OpsSwrlReasoner} 按 {@code ops_rules.config.propertyMapping}
 * 声明式翻译完成，两者职责分离。
 * <p>
 * 读取策略：不缓存，每次推理前按主键实时查询，保证看到最新草稿数据
 * （对应 prodai.ontology 的实时查询语义）。
 */
@Service
public class OntologyInstanceFactsService {

    private static final Logger log = LoggerFactory.getLogger(OntologyInstanceFactsService.class);

    /** 配置草稿在 pd_ai_ontology_instance 中的 ontology_code。 */
    public static final String OFFERING_CONFIG_CODE = "offering_config";
    /** 草稿全量 JSON 在 data_json 中的键（其余键为平铺摘要列）。 */
    private static final String DRAFT_JSON_KEY = "_draft_json";

    private final OntologyInstanceMapper instanceMapper;
    private final ObjectMapper objectMapper;

    public OntologyInstanceFactsService(OntologyInstanceMapper instanceMapper, ObjectMapper objectMapper) {
        this.instanceMapper = instanceMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按草稿主键实时读取实例事实。
     *
     * @param draftId pd_ai_ontology_instance.id
     * @return 事实 Map（草稿全量 JSON 展开）；不存在或解析失败返回空 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> loadConfigFacts(Long draftId) {
        if (draftId == null) {
            return Map.of();
        }
        OntologyInstance entity = instanceMapper.selectById(draftId);
        if (entity == null || !OFFERING_CONFIG_CODE.equals(entity.getOntologyCode())) {
            log.debug("[InstanceFacts] 实例不存在或非配置草稿 draftId={}", draftId);
            return Map.of();
        }
        return factsFrom(entity);
    }

    /** 实体 → 事实 Map：优先展开 _draft_json，缺失时退回平铺摘要列。 */
    private Map<String, Object> factsFrom(OntologyInstance entity) {
        Map<String, String> data = entity.getData() == null ? Map.of() : entity.getData();
        String json = data.get(DRAFT_JSON_KEY);
        Map<String, Object> facts = new LinkedHashMap<>();
        if (json != null && !json.isBlank()) {
            try {
                facts.putAll(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("[InstanceFacts] 草稿 JSON 解析失败 id={}: {}", entity.getId(), e.getMessage());
            }
        }
        data.forEach((k, v) -> {
            if (!DRAFT_JSON_KEY.equals(k) && !facts.containsKey(k)) {
                facts.put(k, v);
            }
        });
        facts.putIfAbsent("draftId", entity.getId());
        return facts;
    }
}
