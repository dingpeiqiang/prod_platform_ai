package com.sitech.prodai.service;

import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
import com.sitech.prodai.service.ops.OpsExtractionService;
import com.sitech.prodai.service.ops.OpsGraphSchemaValidator;
import com.sitech.prodai.service.ops.OpsProductGraphLoader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产商品配置与运营本体推理门面（R2 Phase6 拆分后的薄 Facade）。
 * <p>仅做装配与委托，不含业务逻辑：
 * <ul>
 *   <li>{@link OntologyGraphManager}：事实图加载/重载（last-known-good 守卫）、图谱摘要、工单写回协调</li>
 *   <li>{@link ComplianceRuleEngine}：配置合规校验（R-C*）、草稿知识自迭代发布、风险阈值管理</li>
 *   <li>{@link ChatConfigureService}：智查检索、市场洞察、一键复制、多方案对比、聊天配置与审计链</li>
 *   <li>{@link OpsRiskAuditEngine}：运营大屏、异动告警、批量风险稽核（R-B*）、单品归因（R-A*）、假设评估</li>
 *   <li>{@link OpsWorkOrderService} / {@link ConfigDraftService} / {@link ConfigDocImportService}：工单、草稿、文档导入</li>
 * </ul>
 * 规则阈值与启用开关统一读 {@link OpsRulesService}（ops_rules.json）。
 */
@Service
public class ProductOntologyService {

    private static final Logger log = LoggerFactory.getLogger(ProductOntologyService.class);

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsRulesService opsRules;
    private final OpsExtractionService extractionService;
    private final ConfigMessageProjector messageProjector;
    private final OntologyVersionService versionService;
    private final RiskAuditService riskAudit;
    private final TemplateDeriveEngine deriveEngine;
    private final LlmIntentExtractor intentExtractor;
    private final SparqlConfigDiscoverer sparqlDiscoverer;
    private final ObjectProvider<ProductConfigRegressionService> regressionServiceProvider;
    private final ConfigDraftService configDraftService;

    private final OntologyGraphManager graphManager;
    private final ComplianceRuleEngine complianceEngine;
    private final ChatConfigureService chatConfigureService;
    private final OpsRiskAuditEngine riskAuditEngine;
    private final OpsWorkOrderService opsWorkOrderService;
    private final ConfigDocImportService configDocImportService;

    public ProductOntologyService(com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                              ProdAiProperties properties,
                              OpsSwrlReasoner opsSwrlReasoner,
                              OpsRulesService opsRules,
                              OpsProductGraphLoader graphLoader,
                              OpsExtractionService extractionService,
                              ConfigDocumentParser documentParser,
                              ConfigDocumentStorage documentStorage,
                              Rdf4jOntologyStore rdf4jStore,
                              OpsWorkOrderMapper workOrderMapper,
                              ConfigMessageProjector messageProjector,
                              LastKnownGoodGuard lastKnownGoodGuard,
                              OntologyVersionService versionService,
                              RiskAuditService riskAudit,
                              TemplateDeriveEngine deriveEngine,
                              FactGraphSyncService factGraphSync,
                              LlmIntentExtractor intentExtractor,
                              SparqlConfigDiscoverer sparqlDiscoverer,
                              ObjectProvider<ProductConfigRegressionService> regressionServiceProvider,
                              ConfigDraftService configDraftService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsRules = opsRules;
        this.extractionService = extractionService;
        this.messageProjector = messageProjector;
        this.versionService = versionService;
        this.riskAudit = riskAudit;
        this.deriveEngine = deriveEngine;
        this.intentExtractor = intentExtractor;
        this.sparqlDiscoverer = sparqlDiscoverer;
        this.regressionServiceProvider = regressionServiceProvider;
        this.configDraftService = configDraftService;

        this.graphManager = new OntologyGraphManager(objectMapper, properties, graphLoader,
                lastKnownGoodGuard, factGraphSync, messageProjector, regressionServiceProvider);

        this.configDocImportService = new ConfigDocImportService(
                documentParser, documentStorage, objectMapper);
        this.opsWorkOrderService = new OpsWorkOrderService(workOrderMapper);
        this.opsWorkOrderService.setGraphCoordinator(graphManager.buildWorkOrderGraphCoordinator());

        this.complianceEngine = new ComplianceRuleEngine(objectMapper, properties, opsRules,
                riskAudit, versionService, messageProjector, rdf4jStore, deriveEngine,
                this::loadGraph, this::appendConfigAudit);
        this.chatConfigureService = new ChatConfigureService(
                this::toObjectMapper, extractionService, deriveEngine, intentExtractor, sparqlDiscoverer,
                messageProjector, configDraftService, configDocImportService, opsWorkOrderService, versionService,
                this::loadGraph, this::checkCompliance, this::resolveOfferingId,
                this::findShelfOffering, this::createWorkOrder, complianceEngine);
        this.riskAuditEngine = new OpsRiskAuditEngine(objectMapper, opsSwrlReasoner, opsRules,
                riskAudit, versionService, this::loadGraph, this::riskRules,
                () -> workOrderCount(opsWorkOrderService), this::withModeMeta);
    }

    private static long workOrderCount(OpsWorkOrderService service) {
        Map<String, Object> body = service.listWorkOrders();
        Object total = body.get("total");
        return total instanceof Number n ? n.longValue() : 0L;
    }

    @PostConstruct
    public void init() {
        graphManager.setCallbacks(this::riskRules, this::getOpsRulesCatalog);
        complianceEngine.setFactGraphSyncer(this::syncFactGraphToRdf);
        configDraftService.setCallbacks(this::checkCompliance, this::publishConfigDraft);
        loadGraph();
        opsRules.load();
        syncFactGraphToRdf();
    }

    /** R5：注入 ABox 同步状态回调（由 ABoxSyncScheduler 装配后调用；mock 源不注入）。 */
    public void setAboxSyncStatusSupplier(java.util.function.Supplier<Map<String, Object>> supplier) {
        graphManager.setAboxSyncStatusSupplier(supplier);
    }

    /** 事实图 → 本体图同步；SPARQL 不可读时仅告警降级。 */
    private void syncFactGraphToRdf() {
        try {
            graphManager.syncFactGraphToRdf();
        } catch (Exception e) {
            log.warn("[ProductOntologyService] 事实图→本体图同步失败（SPARQL 不可读时降级）: {}", e.getMessage());
        }
    }

    // ===== 图谱管理（委托 OntologyGraphManager） =====

    public Map<String, Object> withModeMeta(Map<String, Object> body) {
        return graphManager.withModeMeta(body);
    }

    public boolean isDemoEnabled() {
        return properties.getOntology().isDemoEnabled();
    }

    public synchronized Map<String, Object> reloadGraph() {
        return graphManager.reloadGraph();
    }

    public synchronized Map<String, Object> loadGraph() {
        return graphManager.loadGraph();
    }

    public Map<String, Object> getGraphSummary() {
        return graphManager.getGraphSummary();
    }

    public Map<String, Object> getOntologyMeta() {
        return graphManager.getOntologyMeta();
    }

    public String resolveOfferingId(String offeringId, String text) {
        return graphManager.resolveOfferingId(offeringId, text, opsRules);
    }

    private Map<String, Object> findShelfOffering(String offeringId) {
        return graphManager.findShelfOffering(offeringId);
    }

    // ===== 运营规则目录（委托 OpsRulesService + ComplianceRuleEngine） =====

    public Map<String, Object> loadOpsRules() {
        return opsRules.load();
    }

    public Map<String, Object> getOpsRulesCatalog() {
        Map<String, Object> body = new LinkedHashMap<>(opsRules.catalogView());
        body.putAll(complianceEngine.riskRulesAdminView(riskRules()));
        return body;
    }

    private Map<String, Object> riskRules() {
        Map<String, Object> fromFile = opsRules.riskDefaults();
        Map<String, Object> fromGraph = com.sitech.prodai.service.common.MapOps
                .castMap(loadGraph().get("riskRuleDefaults"));
        return riskAudit.effective(fromGraph, fromFile);
    }

    /**
     * 热加载运营规则 ops_rules.json（P1-6 守卫化：失败保留旧规则集）。
     */
    public Map<String, Object> reloadOpsRules() {
        String version = "r" + Instant.now().toEpochMilli();
        LastKnownGoodGuard.GuardRequest request = LastKnownGoodGuard.GuardRequest
                .builder(OntologyVersionService.TYPE_OPS_RULES, "ops_rules",
                        opsRules::loadPending,
                        opsRules::swap)
                .validator(pending -> pending == null || pending.get("version") == null
                        ? List.of("ops_rules missing required key: version")
                        : List.of())
                .version(version)
                .summary("ops_rules 热加载：last-known-good 守卫化")
                .payloadFrom(pending -> {
                    try {
                        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(pending);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .build();
        Map<String, Object> report = new LastKnownGoodGuard(versionService).execute(request);
        if (Boolean.TRUE.equals(report.get("success"))) {
            riskAudit.append("reload_file", Map.of(
                    "rulesPath", properties.getOntology().getRulesPath(),
                    "version", opsRules.version()
            ), riskRules());
        }
        Map<String, Object> body = new LinkedHashMap<>(report);
        if (Boolean.TRUE.equals(report.get("success"))) {
            body.putAll(getOpsRulesCatalog());
        }
        return body;
    }

    public Map<String, Object> updateRiskRules(Map<String, Object> overrides) {
        Map<String, Object> body = complianceEngine.updateRiskRules(overrides, riskRules(),
                (action, detail) -> riskAudit.append(action, detail, riskRules()));
        body.put("riskRules", riskRules());
        body.putAll(complianceEngine.riskRulesAdminView(riskRules()));
        return body;
    }

    public Map<String, Object> resetRiskRules() {
        Map<String, Object> body = complianceEngine.resetRiskRules(riskRules(),
                (action, detail) -> riskAudit.append(action, detail, riskRules()));
        body.put("riskRules", riskRules());
        body.putAll(complianceEngine.riskRulesAdminView(riskRules()));
        return body;
    }

    // ===== 配置合规（委托 ComplianceRuleEngine） =====

    public Map<String, Object> checkCompliance(Map<String, Object> draftInput) {
        return complianceEngine.checkCompliance(draftInput);
    }

    public Map<String, Object> checkCompliance(Map<String, Object> draftInput, Map<String, Object> graphOverride) {
        return complianceEngine.checkCompliance(draftInput, graphOverride);
    }

    public Map<String, Object> checkComplianceSmart(String offeringId, String text, Map<String, Object> draftInput) {
        return complianceEngine.checkComplianceSmart(offeringId, text, draftInput,
                this::resolveOfferingId, this::findShelfOffering);
    }

    public synchronized Map<String, Object> publishConfigDraft(Map<String, Object> draftInput) {
        return complianceEngine.publishConfigDraft(draftInput, graph -> {
            synchronized (this) {
                graphManager.publishGraph(graph);
            }
        });
    }

    // ===== 聊天配置（委托 ChatConfigureService） =====

    public Map<String, Object> discoverConfigs(String query, int limit) {
        return chatConfigureService.discoverConfigs(query, limit);
    }

    public Map<String, Object> marketInsight(String question, int limit) {
        return chatConfigureService.marketInsight(question, limit);
    }

    public Map<String, Object> copyAsDraft(String offeringId, String text) {
        return chatConfigureService.copyAsDraft(offeringId, text);
    }

    public Map<String, Object> copyAsDraft(String offeringId, String text, String sessionId) {
        return chatConfigureService.copyAsDraft(offeringId, text, sessionId);
    }

    public Map<String, Object> copyAsDraft(String offeringId, String text, String sessionId, String requirement) {
        return chatConfigureService.copyAsDraft(offeringId, text, sessionId, requirement);
    }

    public Map<String, Object> compareConfigSchemes(Map<String, Object> request) {
        return chatConfigureService.compareConfigSchemes(request);
    }

    public Map<String, Object> chatConfigure(String text, Map<String, Object> draft) {
        return chatConfigureService.chatConfigure(text, draft);
    }

    public Map<String, Object> getConfigTrace(String traceId) {
        return chatConfigureService.getConfigTrace(traceId);
    }

    public Map<String, Object> explainConfig(String traceId, String audience) {
        return chatConfigureService.explainConfig(traceId, audience);
    }

    public Map<String, Object> explainFieldDefault(String field) {
        return chatConfigureService.explainFieldDefault(field);
    }

    private void appendConfigAudit(String traceId, Map<String, Object> step) {
        chatConfigureService.appendConfigAudit(traceId, step);
    }

    // ===== 文档导入（委托 ConfigDocImportService） =====

    public Map<String, Object> batchFromDocumentBytes(byte[] bytes, String fileName) {
        return configDocImportService.batchFromDocumentBytes(bytes, fileName,
                this::loadGraph, extractionService, deriveEngine, this::checkCompliance,
                messageProjector, opsRules, this::appendConfigAudit);
    }

    public Map<String, Object> uploadConfigDocument(org.springframework.web.multipart.MultipartFile file) {
        return configDocImportService.uploadConfigDocument(file);
    }

    public ConfigDocumentStorage documentStorage() {
        return configDocImportService.documentStorage();
    }

    public Map<String, Object> batchFromUploadedFile(String fileId, String fileName) {
        return configDocImportService.batchFromUploadedFile(fileId, fileName,
                this::loadGraph, extractionService, deriveEngine, this::checkCompliance,
                messageProjector, opsRules, this::appendConfigAudit);
    }

    public Map<String, Object> batchFromDocument(String documentText, List<Map<String, Object>> packages) {
        return configDocImportService.batchFromDocument(documentText, packages,
                this::loadGraph, extractionService, deriveEngine, this::checkCompliance,
                messageProjector, opsRules);
    }

    // ===== 草稿 CRUD（委托 ConfigDraftService） =====

    @Transactional
    public Map<String, Object> saveConfigDraft(Map<String, Object> request) {
        return configDraftService.saveConfigDraft(request);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listConfigDrafts(String sessionId, String userId, String status) {
        return configDraftService.listConfigDrafts(sessionId, userId, status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConfigDraft(Long draftId) {
        return configDraftService.getConfigDraft(draftId);
    }

    @Transactional
    public Map<String, Object> deleteConfigDraft(Long draftId) {
        return configDraftService.deleteConfigDraft(draftId);
    }

    @Transactional
    public Map<String, Object> submitConfigDraft(Map<String, Object> request) {
        return configDraftService.submitConfigDraft(request);
    }

    // ===== 工单（委托 OpsWorkOrderService） =====

    public Map<String, Object> createWorkOrder(Map<String, Object> request) {
        return opsWorkOrderService.createWorkOrder(request);
    }

    public Map<String, Object> listWorkOrders() {
        return opsWorkOrderService.listWorkOrders();
    }

    public Map<String, Object> listWorkOrders(String status, String sessionId) {
        return opsWorkOrderService.listWorkOrders(status, sessionId);
    }

    public Map<String, Object> listWorkOrders(String status, String sessionId, Integer page, Integer size, String q) {
        return opsWorkOrderService.listWorkOrders(status, sessionId, page, size, q);
    }

    public Map<String, Object> updateWorkOrderStatus(String workOrderId, String status, String remark) {
        return opsWorkOrderService.updateWorkOrderStatus(workOrderId, status, remark);
    }

    public Map<String, Object> renameWorkOrder(String workOrderId, String offeringName, String newFee) {
        return opsWorkOrderService.renameWorkOrder(workOrderId, offeringName, newFee);
    }

    // ===== 运营稽核（委托 OpsRiskAuditEngine） =====

    public Map<String, Object> getOpsDashboard() {
        return riskAuditEngine.getOpsDashboard();
    }

    public Map<String, Object> getOpsRevenueOverview() {
        return riskAuditEngine.getOpsRevenueOverview();
    }

    public Map<String, Object> listOpsAlerts(String offeringId) {
        return riskAuditEngine.listOpsAlerts(offeringId);
    }

    public Map<String, Object> runBatchRiskAudit(String trigger) {
        return riskAuditEngine.runBatchRiskAudit(trigger);
    }

    public Map<String, Object> getLastBatchAudit() {
        return riskAuditEngine.getLastBatchAudit();
    }

    public Map<String, Object> analyzeRootCause(String offeringId) {
        return riskAuditEngine.analyzeRootCause(offeringId);
    }

    public Map<String, Object> analyzeRootCause(String offeringId, String text) {
        return riskAuditEngine.analyzeRootCause(offeringId, text);
    }

    public Map<String, Object> auditRisks(List<String> offeringIds) {
        return riskAuditEngine.auditRisks(offeringIds);
    }

    public Map<String, Object> evaluateHypothetical(List<Map<String, Object>> patches, String mode) {
        return riskAuditEngine.evaluateHypothetical(patches, mode);
    }

    /** @deprecated 使用 {@link OpsExtractionService#extractSlots(String)}；保留兼容调用。 */
    @Deprecated
    public Map<String, Object> parseSlotsFromText(String text) {
        return extractionService.extractSlots(text == null ? "" : text).slots();
    }

    private com.fasterxml.jackson.databind.ObjectMapper toObjectMapper() {
        return objectMapper;
    }
}
