package com.sitech.prodai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import com.sitech.prodai.domain.entity.OntologyAssetVersion;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合规规则引擎（R2 Phase6 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：配置合规校验 R-C03/04/05/06/07/08/09（checkCompliance 及图谱参数化变体）、
 * 智能入口（checkComplianceSmart）、合规通过的草稿知识自迭代沉淀（publishConfigDraft）、
 * 风险阈值规则管理（updateRiskRules/resetRiskRules/admin 视图）。
 * <p>事实图读取经 {@link GraphSupplier} 回调由宿主提供；配置审计链经 {@link AuditAppender} 回调。
 */
public class ComplianceRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleEngine.class);

    private final ObjectMapper objectMapper;
    private final ProdAiProperties properties;
    private final OpsRulesService opsRules;
    private final RiskAuditService riskAudit;
    private final OntologyVersionService versionService;
    private final ConfigMessageProjector messageProjector;
    private final Rdf4jOntologyStore rdf4jStore;
    private final TemplateDeriveEngine deriveEngine;

    /** 事实图读取回调（宿主 graphCache 单源）。 */
    @FunctionalInterface
    public interface GraphSupplier {
        Map<String, Object> loadGraph();
    }

    /** 配置审计链追加回调（traceId, step）。 */
    @FunctionalInterface
    public interface AuditAppender {
        void append(String traceId, Map<String, Object> step);
    }

    private final GraphSupplier graphSupplier;
    private final AuditAppender auditAppender;

    /** SHACL 校验委托（R7 转正，可选依赖；setter 注入避免破坏既有装配）。 */
    private volatile ShaclValidationDelegate shaclDelegate;

    public ComplianceRuleEngine(ObjectMapper objectMapper,
                                ProdAiProperties properties,
                                OpsRulesService opsRules,
                                RiskAuditService riskAudit,
                                OntologyVersionService versionService,
                                ConfigMessageProjector messageProjector,
                                Rdf4jOntologyStore rdf4jStore,
                                TemplateDeriveEngine deriveEngine,
                                GraphSupplier graphSupplier,
                                AuditAppender auditAppender) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.opsRules = opsRules;
        this.riskAudit = riskAudit;
        this.versionService = versionService;
        this.messageProjector = messageProjector;
        this.rdf4jStore = rdf4jStore;
        this.deriveEngine = deriveEngine;
        this.graphSupplier = graphSupplier;
        this.auditAppender = auditAppender;
    }

    private Map<String, Object> loadGraph() {
        return graphSupplier.loadGraph();
    }

    private void appendConfigAudit(String traceId, Map<String, Object> step) {
        auditAppender.append(traceId, step);
    }

    /** 宿主注入 SHACL 委托（R7 转正；为 null 时合规链路退回纯 Java，行为不变）。 */
    public void setShaclDelegate(ShaclValidationDelegate shaclDelegate) {
        this.shaclDelegate = shaclDelegate;
    }

    public Map<String, Object> checkCompliance(Map<String, Object> draftInput) {
        return checkCompliance(draftInput, null);
    }

    /** 图谱参数化变体（P1-7 SMOKE 回接）：用 pending 图谱跑合规断言，不触碰现行 graphCache。 */
    public Map<String, Object> checkCompliance(Map<String, Object> draftInput, Map<String, Object> graphOverride) {
        Map<String, Object> graph = graphOverride != null ? graphOverride : loadGraph();
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null ? Map.of() : draftInput);
        List<Map<String, Object>> issues = new ArrayList<>();

        if (opsRules.isConfigEnabled("R-C06")) {
            List<String[]> required = List.of(
                    new String[]{"offeringName", "资费名称"},
                    new String[]{"messageRootKey", "产品品类"},
                    new String[]{"fixedFeeAmount", "固费金额"},
                    new String[]{"channelScope", "销售渠道"}
            );
            for (String[] item : required) {
                Object val = draft.get(item[0]);
                if ("fixedFeeAmount".equals(item[0])) {
                    val = MapOps.firstNonEmpty(draft.get("fixedFeeAmount"), draft.get("monthlyFee"),
                            MapOps.castMap(draft.get("chargePlan")).get("fixedFeeAmount"));
                }
                if ("offeringName".equals(item[0])) {
                    val = MapOps.firstNonEmpty(draft.get("offeringName"), draft.get("offerName"));
                }
                if ("channelScope".equals(item[0])) {
                    val = MapOps.firstNonEmpty(draft.get("channelScope"),
                            MapOps.castMap(draft.get("releaseScope")).get("channelScope"));
                }
                if (MapOps.empty(val)) {
                    issues.add(issue("R-C06", "必填缺失", "MEDIUM", item[0],
                            "缺少必填字段：" + item[1], List.of(item[0] + "=empty"), null));
                }
            }
            // 附加品类需依赖主资费
            if (Boolean.FALSE.equals(draft.get("isMainOffer"))
                    || "false".equalsIgnoreCase(MapOps.str(draft.get("isMainOffer")))) {
                if (MapOps.empty(draft.get("dependOn")) && MapOps.empty(draft.get("sourceOfferRef"))) {
                    // 由 R-C04 专门处理，此处不重复
                }
            }
        }

        String mutexGroup = MapOps.str(MapOps.firstNonEmpty(draft.get("mutexGroup"), "MAIN_PKG"));
        Object bindId = draft.get("bindExistingMainPkg");
        Map<String, Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings")).stream()
                .collect(Collectors.toMap(o -> MapOps.str(o.get("offeringId")), o -> o, (a, b) -> a, LinkedHashMap::new));
        if (opsRules.isConfigEnabled("R-C03") && !MapOps.empty(bindId) && shelf.containsKey(MapOps.str(bindId))) {
            Map<String, Object> existing = shelf.get(MapOps.str(bindId));
            String offeringType = MapOps.str(draft.get("offeringType"));
            boolean typeMatch = offeringType.isEmpty()
                    || "main_pkg".equals(offeringType)
                    || "fusion".equals(offeringType);
            if (mutexGroup.equals(MapOps.str(existing.get("mutexGroup"))) && typeMatch) {
                List<Map<String, Object>> triples = new ArrayList<>();
                triples.add(MapOps.triple(MapOps.firstNonEmpty(draft.get("offeringName"), "当前草稿"), "mutexGroup", mutexGroup));
                triples.add(MapOps.triple(bindId, "mutexGroup", existing.get("mutexGroup")));
                triples.add(MapOps.triple(MapOps.firstNonEmpty(draft.get("offeringName"), "当前草稿"),
                        "blocksCombination", existing.get("offeringName")));
                issues.add(issue("R-C03", "资费/关系冲突", "HIGH", "mutexGroup",
                        "与在架商品 " + existing.get("offeringName") + "(" + bindId + ") 同属互斥组 "
                                + mutexGroup + "，不可同时上架",
                        List.of(
                                "当前草稿—互斥组—" + mutexGroup,
                                bindId + "—互斥组—" + existing.get("mutexGroup"),
                                "当前草稿—冲突对象—" + existing.get("offeringName")
                        ),
                        triples));
            }
        }

        boolean isAddon = "addon".equals(MapOps.str(draft.get("offeringType")))
                || Boolean.FALSE.equals(draft.get("isMainOffer"))
                || "false".equalsIgnoreCase(MapOps.str(draft.get("isMainOffer")));
        if (opsRules.isConfigEnabled("R-C04")
                && isAddon && MapOps.empty(draft.get("dependOn")) && MapOps.empty(draft.get("sourceOfferRef"))) {
            issues.add(issue("R-C04", "规则漏洞", "HIGH", "dependOn",
                    "附加资费缺少依赖的主资费/相容关系（dependOn 或 sourceOfferRef）",
                    List.of("isMainOffer=false", "dependOn=empty"), null));
        }

        double monthly = MapOps.resolveFixedFee(draft);
        double oneTime = MapOps.num(draft.get("oneTimeFee"), 0);
        String scenario = MapOps.str(draft.get("bizScenario"));
        List<Object> whitelist = MapOps.castList(graph.get("equityGiftWhitelist"));
        if (opsRules.isConfigEnabled("R-C05")
                && monthly == 0 && oneTime == 0 && !MapOps.truthy(draft.get("hasContract"))) {
            if (!whitelist.contains(scenario) && !"内部验证".equals(MapOps.str(draft.get("channelScope")))) {
                issues.add(issue("R-C05", "高风险资费", "HIGH", "fixedFeeAmount",
                        "固费/一次性费均为0且无合约，非权益赠送白名单",
                        List.of("fixedFeeAmount=0", "oneTimeFee=0", "hasContract=0"), null));
            }
        }

        double discount = MapOps.num(MapOps.firstNonEmpty(draft.get("discountPercent"), draft.get("prefDiscount")), -1);
        if (discount > 1 && discount <= 100) {
            // 百分数转比例，兼容旧草稿
        } else if (discount > 0 && discount <= 1) {
            discount = discount * 100;
        }
        boolean repeatable = MapOps.truthy(draft.get("repeatable"))
                || "是".equals(MapOps.str(draft.get("repeatChargeFlag")));
        if (opsRules.isConfigEnabled("R-C07")
                && discount == 100 && repeatable) {
            issues.add(issue("R-C07", "异常优惠漏洞", "HIGH", "prefDiscount",
                    "优惠折扣100%且可重复订购，存在异常优惠漏洞",
                    List.of("prefDiscount=100", "repeatable=true"), null));
        }

        // R-C09 ≈ 方案 R-CONF-001：资费上下限
        if (opsRules.isConfigEnabled("R-C09") && monthly >= 0) {
            double minFee = opsRules.configDefaultNum("monthlyFeeMin", 9);
            double maxFee = opsRules.configDefaultNum("monthlyFeeMax", 599);
            if (monthly < minFee || monthly > maxFee) {
                issues.add(issue("R-C09", "资费区间违规", "HIGH", "fixedFeeAmount",
                        "固费取值超出合规范围，允许区间为" + (int) minFee + "-" + (int) maxFee + "元",
                        List.of("fixedFeeAmount=" + monthly, "min=" + minFee, "max=" + maxFee), null));
            }
        }

        // R-C03 扩展 ≈ 方案 R-CONF-002：折扣与赠费并存需复核
        double freeFee = MapOps.num(MapOps.firstNonEmpty(draft.get("freeFeeAmount"), draft.get("giftFee"), draft.get("prefFee")), -1);
        if (opsRules.isConfigEnabled("R-C03") && discount > 0 && freeFee > 0) {
            issues.add(issue("R-C03", "资费冲突", "MEDIUM", "prefDiscount",
                    "同时配置了折扣与赠费，存在资费冲突风险，需人工复核（方案别名 R-CONF-002）",
                    List.of("prefDiscount=" + discount, "prefFee=" + freeFee), null));
        }

        issues = applyShaclAuthoritative(draft, graph, issues);

        boolean hasHigh = issues.stream().anyMatch(i -> "HIGH".equals(i.get("issueLevel")));
        boolean requiredOk = issues.stream().noneMatch(i -> "R-C06".equals(i.get("ruleId")));
        boolean compliancePass = !hasHigh && requiredOk;

        List<String> applied = compliancePass
                ? (opsRules.isConfigEnabled("R-C08") ? List.of("R-C08") : List.of())
                : issues.stream().map(i -> MapOps.str(i.get("ruleId"))).distinct().sorted().collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("issues", issues);
        body.put("compliancePass", compliancePass);
        body.put("appliedRules", applied);
        body.put("canSubmit", compliancePass);
        body.put("messageRootKey", draft.get("messageRootKey"));
        return body;
    }

    /**
     * SHACL 转正覆盖（R7 §6.3 第 5 步）：试点规则（R-C06/R-C03/R-C05）命中以 SHACL 引擎为准，
     * Java 同规则结果被替换；SHACL 关闭/缺失/失败时原样返回 Java 结果（永不中断合规链路）。
     * <p>issue 结构对齐：SHACL issue 补 proposalAlias（与 Java issue() 契约同构），
     * R-C06 保持 MEDIUM 级语义（SHACL 引擎侧 HIGH 仅作内部信号，对外级别以存量契约为准）。
     */
    private List<Map<String, Object>> applyShaclAuthoritative(Map<String, Object> draft,
                                                              Map<String, Object> graph,
                                                              List<Map<String, Object>> javaIssues) {
        ShaclValidationDelegate delegate = this.shaclDelegate;
        if (delegate == null || !properties.getOntology().isShaclAuthoritative()) {
            return javaIssues;
        }
        Map<String, Object> shaclBody;
        try {
            shaclBody = delegate.validate(draft, graph);
        } catch (Exception e) {
            log.warn("[合规] SHACL 转正校验异常，回退 Java 结果: {}", e.getMessage());
            return javaIssues;
        }
        if (!Boolean.TRUE.equals(shaclBody.get("success"))) {
            // 引擎异常（Lite 兜底也失败）：回退 Java，保证合规链路不中断
            return javaIssues;
        }
        if (Boolean.TRUE.equals(shaclBody.get("exempt"))) {
            // 白名单豁免：剔除试点规则全部 Java 命中（豁免语义单点在 SHACL 投影层）
            return javaIssues.stream()
                    .filter(i -> !ShaclValidationDelegate.isPilotRuleId(MapOps.str(i.get("ruleId"))))
                    .collect(Collectors.toList());
        }
        Set<String> shaclHits = MapOps.castListOfMaps(shaclBody.get("issues")).stream()
                .map(i -> MapOps.str(i.get("ruleId")))
                .filter(ShaclValidationDelegate::isPilotRuleId)
                .collect(Collectors.toSet());
        // 试点规则以 SHACL 为准：Java 同规则命中被剔除，SHACL 命中（含否决/报出）全量替换；
        // 非试点规则保持 Java 结果。SHACL 未报即规则未命中——语义单源化到 shapes。
        List<Map<String, Object>> merged = javaIssues.stream()
                .filter(i -> !ShaclValidationDelegate.isPilotRuleId(MapOps.str(i.get("ruleId"))))
                .collect(Collectors.toCollection(ArrayList::new));
        for (Map<String, Object> row : MapOps.castListOfMaps(shaclBody.get("issues"))) {
            merged.add(alignShaclIssue(row));
        }
        log.debug("[合规] SHACL 转正生效: shaclHits={}, javaIssueCount={}, mergedCount={}",
                shaclHits, javaIssues.size(), merged.size());
        return merged;
    }

    /** SHACL issue 行对齐 Java 契约：补 proposalAlias，R-C06 级别降为 MEDIUM（存量语义）。 */
    private Map<String, Object> alignShaclIssue(Map<String, Object> row) {
        Map<String, Object> aligned = new LinkedHashMap<>(row);
        String ruleId = MapOps.str(row.get("ruleId"));
        String alias = opsRules.configProposalAlias(ruleId);
        if (!alias.isBlank() && !aligned.containsKey("proposalAlias")) {
            aligned.put("proposalAlias", alias);
        }
        if ("R-C06".equals(ruleId)) {
            aligned.put("issueLevel", "MEDIUM");
        }
        return aligned;
    }

    /**
     * 按套餐信息做合规校验，支持已入库（在架）与未入库（草稿）两类来源。
     * <ul>
     *   <li>文案含「当前配置/当前草稿/未入库」且草稿有实质字段 → 校验未入库草稿</li>
     *   <li>能解析到在架编码/名称 → 校验已入库套餐</li>
     *   <li>否则若草稿有实质字段 → 校验未入库草稿</li>
     * </ul>
     */
    public Map<String, Object> checkComplianceSmart(String offeringId, String text, Map<String, Object> draftInput,
                                                    java.util.function.BiFunction<String, String, String> resolveOfferingIdFn,
                                                    java.util.function.Function<String, Map<String, Object>> findShelfOffering) {
        Map<String, Object> draft = draftInput == null ? Map.of() : draftInput;
        String q = text == null ? "" : text.trim();
        boolean preferDraft = q.matches("(?s).*(当前配置|当前草稿|未入库|校验当前).*")
                || "校验当前配置是否符合在架规则".equals(q);
        boolean forceShelf = q.matches("(?s).*(已入库|在架商品|在架套餐).*");

        String oid = resolveOfferingIdFn.apply(offeringId, q);
        Map<String, Object> targetDraft;
        String source;
        String sourceLabel;
        String resolvedId = null;
        String resolvedName = null;

        if (!forceShelf && preferDraft && hasDraftContent(draft)) {
            targetDraft = new LinkedHashMap<>(draft);
            source = "draft";
            sourceLabel = "未入库草稿";
            resolvedName = MapOps.str(MapOps.firstNonEmpty(draft.get("offeringName"), "当前草稿"));
        } else if (oid != null) {
            Map<String, Object> shelf = findShelfOffering.apply(oid);
            if (shelf == null) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("success", false);
                fail.put("message", "已解析到编码 " + oid + "，但图谱中无对应在架套餐");
                fail.put("offeringId", oid);
                fail.put("query", q);
                return fail;
            }
            targetDraft = shelfOfferingToDraft(shelf);
            source = "shelf";
            sourceLabel = "已入库（在架）";
            resolvedId = oid;
            resolvedName = MapOps.str(shelf.get("offeringName"));
        } else if (hasDraftContent(draft)) {
            targetDraft = new LinkedHashMap<>(draft);
            source = "draft";
            sourceLabel = "未入库草稿";
            resolvedName = MapOps.str(MapOps.firstNonEmpty(draft.get("offeringName"), "当前草稿"));
        } else {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", MapOps.empty(q)
                    ? "请提供套餐名称/编码，或先通过智聊/智读生成未入库草稿后再校验"
                    : "未能解析套餐，也未找到可校验的未入库草稿。可试：「校验校园体验流量包0元是否符合在架规则」，或先配置后再说「校验当前配置」");
            fail.put("query", q);
            fail.put("hintExamples", List.of(
                    "校验校园体验流量包0元是否符合在架规则",
                    "校验当前配置是否符合在架规则"
            ));
            return fail;
        }

        Map<String, Object> compliance = checkCompliance(targetDraft);
        Map<String, Object> body = new LinkedHashMap<>(compliance);
        body.put("success", true);
        body.put("source", source);
        body.put("sourceLabel", sourceLabel);
        body.put("offeringId", resolvedId);
        body.put("offeringName", resolvedName);
        body.put("draft", targetDraft);
        body.put("query", q.isEmpty() ? null : q);
        body.put("intent", "compliance_check");
        return body;
    }

    private boolean hasDraftContent(Map<String, Object> draft) {
        if (draft == null || draft.isEmpty()) {
            return false;
        }
        return !MapOps.empty(draft.get("offeringName"))
                || !MapOps.empty(draft.get("offerName"))
                || !MapOps.empty(draft.get("monthlyFee"))
                || !MapOps.empty(draft.get("fixedFeeAmount"))
                || !MapOps.empty(draft.get("bizScenario"))
                || !MapOps.empty(draft.get("messageRootKey"))
                || !MapOps.empty(draft.get("categoryCode"))
                || !MapOps.empty(draft.get("targetUser"))
                || !MapOps.empty(draft.get("channelScope"))
                || !MapOps.empty(draft.get("offeringType"))
                || !MapOps.empty(draft.get("includeBroadband"))
                || !MapOps.empty(draft.get("bindExistingMainPkg"));
    }

    /** 将已入库在架套餐映射为合规校验所需的配置字段。 */
    public Map<String, Object> shelfOfferingToDraft(Map<String, Object> shelf) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("offeringId", shelf.get("offeringId"));
        draft.put("offeringName", shelf.get("offeringName"));
        draft.put("offerName", shelf.get("offeringName"));
        draft.put("offeringType", MapOps.firstNonEmpty(shelf.get("offeringType"), "main_pkg"));
        draft.put("monthlyFee", shelf.get("monthlyFee"));
        draft.put("fixedFeeAmount", MapOps.firstNonEmpty(shelf.get("fixedFeeAmount"), shelf.get("monthlyFee")));
        draft.put("oneTimeFee", MapOps.firstNonEmpty(shelf.get("oneTimeFee"), 0));
        draft.put("mutexGroup", MapOps.firstNonEmpty(shelf.get("mutexGroup"), "MAIN_PKG"));
        draft.put("hasContract", shelf.get("hasContract"));
        draft.put("discountPercent", shelf.get("discountPercent"));
        draft.put("repeatable", shelf.get("repeatable"));
        draft.put("messageRootKey", shelf.get("messageRootKey"));
        draft.put("categoryCode", shelf.get("categoryCode"));
        draft.put("categoryName", shelf.get("categoryName"));
        draft.put("productLine", shelf.get("productLine"));
        draft.put("chargePlan", shelf.get("chargePlan"));
        draft.put("releaseScope", shelf.get("releaseScope"));
        draft.put("familyOfferPolicy", shelf.get("familyOfferPolicy"));
        draft.put("networkCapability", shelf.get("networkCapability"));
        draft.put("workOrderId", shelf.get("workOrderId"));
        if (!MapOps.empty(shelf.get("dependOn"))) {
            draft.put("dependOn", shelf.get("dependOn"));
        }
        if (!MapOps.empty(shelf.get("bindExistingMainPkg"))) {
            draft.put("bindExistingMainPkg", shelf.get("bindExistingMainPkg"));
        }

        String whitelistTag = MapOps.str(shelf.get("whitelistTag"));
        String bizScenario = MapOps.str(MapOps.firstNonEmpty(shelf.get("bizScenario"), whitelistTag));
        if (bizScenario.isEmpty() && "whitelist".equals(MapOps.str(shelf.get("category")))) {
            bizScenario = "权益赠送";
        }
        draft.put("bizScenario", bizScenario);

        String targetUser = MapOps.str(MapOps.firstNonEmpty(shelf.get("targetUser"), shelf.get("targetCustomerGroup")));
        if (targetUser.isEmpty()) {
            String name = MapOps.str(shelf.get("offeringName"));
            if (name.contains("家庭")) {
                targetUser = "家庭";
            } else if (name.contains("校园")) {
                targetUser = "校园";
            } else {
                targetUser = "个人";
            }
        }
        draft.put("targetUser", targetUser);
        draft.put("channelScope", MapOps.firstNonEmpty(shelf.get("channelScope"),
                MapOps.castMap(shelf.get("releaseScope")).get("channelScope"), "全渠道"));
        draft.put("state", shelf.get("state"));
        draft.put("fillSources", Map.of("_source", "shelf"));
        return messageProjector.applyCategoryDefaults(draft);
    }

    /**
     * 知识自迭代：合规通过的草稿沉淀至事实图 + RDF ConfigScheme。
     */
    public synchronized Map<String, Object> publishConfigDraft(Map<String, Object> draftInput,
                                                               GraphMutator graphMutator) {
        Map<String, Object> draft = messageProjector.applyCategoryDefaults(
                draftInput == null ? Map.of() : MapOps.deepCopy(objectMapper, draftInput));
        String newId = MapOps.str(draft.get("offeringId"));
        if (newId.isBlank()) {
            newId = "OF-DRAFT-" + Instant.now().toEpochMilli();
        }
        Map<String, Object> compliance = checkCompliance(draft);
        if (!Boolean.TRUE.equals(compliance.get("compliancePass"))) {
            // P1-6 持久快照：合规失败行留 review 态供复盘
            registerDraftVersion(draft, newId, false, Map.of(
                    "step", "compliance",
                    "issues", compliance.get("issues") == null ? List.of() : compliance.get("issues")));
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("success", false);
            fail.put("message", "合规未通过，拒绝沉淀至本体");
            fail.put("issues", compliance.get("issues"));
            fail.put("compliancePass", false);
            return fail;
        }

        Map<String, Object> graph = loadGraph();
        List<Map<String, Object>> shelf = MapOps.castListOfMaps(graph.get("shelfOfferings"));
        double fee = MapOps.resolveFixedFee(draft);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("offeringId", newId);
        row.put("offeringName", MapOps.firstNonEmpty(draft.get("offeringName"), draft.get("offerName"), newId));
        row.put("state", "上架");
        row.put("monthlyFee", fee >= 0 ? fee : draft.get("monthlyFee"));
        row.put("fixedFeeAmount", fee >= 0 ? fee : draft.get("fixedFeeAmount"));
        row.put("oneTimeFee", MapOps.firstNonEmpty(draft.get("oneTimeFee"), 0));
        row.put("mutexGroup", MapOps.firstNonEmpty(draft.get("mutexGroup"), "MAIN_PKG"));
        row.put("offeringType", MapOps.firstNonEmpty(draft.get("offeringType"), "main_pkg"));
        row.put("shelfDays", 0);
        row.put("salesCnt30d", 0);
        row.put("revenue30d", 0);
        row.put("hasContract", draft.get("hasContract"));
        row.put("strategicTag", false);
        row.put("category", "normal");
        row.put("bizScenario", draft.get("bizScenario"));
        row.put("targetUser", draft.get("targetUser"));
        row.put("channelScope", draft.get("channelScope"));
        row.put("regionScope", draft.get("regionScope"));
        row.put("discountPercent", MapOps.firstNonEmpty(draft.get("discountPercent"), draft.get("prefDiscount")));
        row.put("basedOnTemplate", draft.get("basedOnTemplate"));
        row.put("copiedFrom", draft.get("copiedFrom"));
        row.put("messageRootKey", draft.get("messageRootKey"));
        row.put("categoryCode", draft.get("categoryCode"));
        row.put("categoryName", draft.get("categoryName"));
        row.put("productLine", draft.get("productLine"));
        row.put("workOrderId", draft.get("workOrderId"));
        row.put("chargePlan", draft.get("chargePlan"));
        row.put("releaseScope", draft.get("releaseScope"));
        row.put("familyOfferPolicy", draft.get("familyOfferPolicy"));
        row.put("networkCapability", draft.get("networkCapability"));
        row.put("printNotice", draft.get("printNotice"));
        row.put("smsNotice", draft.get("smsNotice"));
        row.put("dependOn", draft.get("dependOn"));
        shelf.add(0, row);
        graph.put("shelfOfferings", shelf);

        List<Map<String, Object>> schemes = MapOps.castListOfMaps(graph.get("configSchemes"));
        Map<String, Object> schemeRow = new LinkedHashMap<>();
        schemeRow.put("schemeId", newId);
        schemeRow.put("schemeName", row.get("offeringName"));
        schemeRow.put("status", "已上线");
        schemeRow.put("messageRootKey", row.get("messageRootKey"));
        schemeRow.put("categoryCode", row.get("categoryCode"));
        schemeRow.put("categoryName", row.get("categoryName"));
        schemeRow.put("productLine", row.get("productLine"));
        schemeRow.put("fixedFeeAmount", row.get("fixedFeeAmount"));
        schemeRow.put("monthlyFee", row.get("monthlyFee"));
        schemeRow.put("bizScenario", row.get("bizScenario"));
        schemeRow.put("channelScope", row.get("channelScope"));
        schemeRow.put("basedOnTemplate", row.get("basedOnTemplate"));
        schemeRow.put("workOrderId", row.get("workOrderId"));
        schemeRow.put("chargePlan", row.get("chargePlan"));
        schemeRow.put("releaseScope", row.get("releaseScope"));
        schemes.add(0, schemeRow);
        graph.put("configSchemes", schemes);
        graphMutator.accept(graph);
        syncFactGraphToRdf();

        Map<String, Object> messagePreview = messageProjector.toMessage(draft);

        String baseIri = properties.getOntology().normalizedBaseIri() + "config/";
        String uri = baseIri + newId;
        // P3-2 ① ABox 扩列：以完整草稿字段为底（含业务子对象内的标量），再压入规范化运行字段，
        // 使 SPARQL/SWRL 可对完整字段检索，不再只见约 10 项子集。
        Map<String, Object> facts = new LinkedHashMap<>(draft);
        facts.put("schemeId", newId);
        facts.put("workOrderId", draft.get("workOrderId"));
        facts.put("status", "已上线");
        facts.put("version", MapOps.firstNonEmpty(draft.get("version"), "V1.0"));
        facts.put("messageRootKey", draft.get("messageRootKey"));
        facts.put("categoryCode", draft.get("categoryCode"));
        facts.put("fixedFeeAmount", row.get("fixedFeeAmount"));
        facts.put("channelScope", row.get("channelScope"));
        facts.put("appliesScene", draft.get("bizScenario"));
        Object copiedFrom = draft.get("copiedFrom");
        if (!MapOps.empty(copiedFrom)) {
            facts.put("similarTo", baseIri + copiedFrom);
        }
        rdf4jStore.addClass("ConfigScheme");
        rdf4jStore.addProperty("similarTo");
        rdf4jStore.addProperty("appliesScene");
        rdf4jStore.addProperty("messageRootKey");
        rdf4jStore.addProperty("fixedFeeAmount");
        rdf4jStore.addInstance(uri, "ConfigScheme", facts);

        String traceId = "cfg-publish-" + Instant.now().toEpochMilli();
        appendConfigAudit(traceId, Map.of(
                "step", "knowledge_iterate",
                "offering_id", newId,
                "uri", uri,
                "message_root_key", MapOps.str(draft.get("messageRootKey")),
                "timestamp", Instant.now().toString()
        ));
        // P1-6 持久快照：发布成功登记 published（表 A + 表 B）
        registerDraftVersion(draft, newId, true, Map.of(
                "uri", uri,
                "trace_id", traceId,
                "message_root_key", MapOps.str(draft.get("messageRootKey"))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("offeringId", newId);
        body.put("schemeId", newId);
        body.put("uri", uri);
        body.put("shelfCount", shelf.size());
        body.put("trace_id", traceId);
        body.put("messageRootKey", draft.get("messageRootKey"));
        body.put("messagePreview", messagePreview);
        body.put("message", "已沉淀至事实图与配置本体 ConfigScheme，并生成报文投影");
        return body;
    }

    /** 发布后图缓存提交回调（宿主提供，含 syncFactGraphToRdf 时机控制）。 */
    @FunctionalInterface
    public interface GraphMutator {
        void accept(Map<String, Object> graph);
    }

    /** 事实图 → 本体图同步（发布后调用，回调宿主图谱管理器）。 */
    @FunctionalInterface
    public interface FactGraphSyncer {
        void sync();
    }

    private FactGraphSyncer factGraphSyncer;

    /** 宿主注入：发布后事实图同步动作。 */
    public void setFactGraphSyncer(FactGraphSyncer factGraphSyncer) {
        this.factGraphSyncer = factGraphSyncer;
    }

    private void syncFactGraphToRdf() {
        if (factGraphSyncer != null) {
            factGraphSyncer.sync();
        }
    }

    /** P1-6 持久快照：发布成功登记 published / 合规失败登记 review，均落版本库表 A + 表 B（登记失败不阻断主流程）。 */
    private void registerDraftVersion(Map<String, Object> draft, String newId, boolean success, Map<String, Object> detail) {
        try {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(draft);
            } catch (Exception e) {
                log.warn("[版本库] 草稿序列化失败，跳过登记: {}", e.getMessage());
                return;
            }
            OntologyAssetVersion row = versionService.register(
                    OntologyVersionService.TYPE_ABOX_SNAPSHOT, newId,
                    String.valueOf(MapOps.firstNonEmpty(draft.get("version"), "V1.0")),
                    success ? OntologyVersionService.STATUS_PUBLISHED : OntologyVersionService.STATUS_REVIEW,
                    "config_publish", "知识自迭代配置发布草稿", payload);
            Map<String, Object> logDetail = new LinkedHashMap<>(detail == null ? Map.of() : detail);
            logDetail.put("success", success);
            versionService.log(row.getId(), "publish", "config_publish", logDetail);
        } catch (Exception e) {
            log.warn("[版本库] 配置草稿登记失败（不影响发布结果）: {}", e.getMessage());
        }
    }

    // ── 风险阈值规则管理 ──

    public Map<String, Object> riskRulesAdminView(Map<String, Object> riskRules) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("riskEffective", riskRules);
        view.put("riskDefaults", opsRules.riskDefaults());
        view.put("riskOverrides", riskAudit.overrides());
        // P3-5 ① 审计链：优先表 B risk 域回读，空则回退内存态
        List<Map<String, Object>> domainLogs = versionService.riskAuditLogs();
        view.put("riskAuditLog", domainLogs.isEmpty() ? riskAudit.snapshotAudit() : domainLogs);
        view.put("opsRulesVersion", opsRules.version());
        view.put("rulesPath", properties.getOntology().getRulesPath());
        view.put("swrlEnabled", properties.getOntology().isSwrlEnabled());
        return view;
    }

    public Map<String, Object> updateRiskRules(Map<String, Object> overrides, Map<String, Object> riskRules,
                                               AuditAction action) {
        Set<String> allowed = Set.of(
                "zeroSalesShelfDays", "zeroSalesDaysWindow",
                "highRiskReviewDays", "lowRevenuePercentile", "ruleVersion"
        );
        Map<String, Object> applied = riskAudit.apply(overrides, allowed);
        if (!applied.isEmpty()) {
            action.accept("update", applied);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules);
        return body;
    }

    public Map<String, Object> resetRiskRules(Map<String, Object> riskRules, AuditAction action) {
        Map<String, Object> before = riskAudit.clear();
        action.accept("reset", Map.of("cleared", before));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("riskRules", riskRules);
        return body;
    }

    /** 审计动作回调（action, detail）。 */
    @FunctionalInterface
    public interface AuditAction {
        void accept(String action, Map<String, Object> detail);
    }

    private Map<String, Object> issue(String ruleId, String issueType, String level, String field,
                                      String message, List<String> evidence, List<Map<String, Object>> triples) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", ruleId);
        String alias = opsRules.configProposalAlias(ruleId);
        if (!alias.isBlank()) {
            row.put("proposalAlias", alias);
        }
        row.put("issueType", issueType);
        row.put("issueLevel", level);
        row.put("field", field);
        row.put("message", message);
        row.put("evidence", evidence);
        if (triples != null) {
            row.put("triples", triples);
        }
        return row;
    }
}
