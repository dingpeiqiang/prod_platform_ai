package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.AttributionChannelEvidence;
import com.sitech.prodai.domain.entity.AttributionCompetitor;
import com.sitech.prodai.domain.entity.AttributionMetric;
import com.sitech.prodai.domain.entity.AttributionOffering;
import com.sitech.prodai.domain.entity.AttributionPromotion;
import com.sitech.prodai.domain.entity.AttributionUserBehavior;
import com.sitech.prodai.mapper.AttributionChannelEvidenceMapper;
import com.sitech.prodai.mapper.AttributionCompetitorMapper;
import com.sitech.prodai.mapper.AttributionMetricMapper;
import com.sitech.prodai.mapper.AttributionOfferingMapper;
import com.sitech.prodai.mapper.AttributionPromotionMapper;
import com.sitech.prodai.mapper.AttributionUserBehaviorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归因证据查询（pmsdb.attribution_*，本体 sc:v-* 事实来源）。
 * <p>供归因结论追溯：商品全量，渠道/促销/竞品/用户行为/指标按 offerid 查询。
 */
@Service
public class AttributionChannelEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(AttributionChannelEvidenceService.class);

    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final AttributionOfferingMapper offeringMapper;
    private final AttributionChannelEvidenceMapper channelMapper;
    private final AttributionPromotionMapper promotionMapper;
    private final AttributionCompetitorMapper competitorMapper;
    private final AttributionUserBehaviorMapper behaviorMapper;
    private final AttributionMetricMapper metricMapper;

    public AttributionChannelEvidenceService(AttributionOfferingMapper offeringMapper,
                                            AttributionChannelEvidenceMapper channelMapper,
                                            AttributionPromotionMapper promotionMapper,
                                            AttributionCompetitorMapper competitorMapper,
                                            AttributionUserBehaviorMapper behaviorMapper,
                                            AttributionMetricMapper metricMapper) {
        this.offeringMapper = offeringMapper;
        this.channelMapper = channelMapper;
        this.promotionMapper = promotionMapper;
        this.competitorMapper = competitorMapper;
        this.behaviorMapper = behaviorMapper;
        this.metricMapper = metricMapper;
    }

    /** 全量查询归因商品，按 offerid 升序。 */
    public Map<String, Object> listOfferings() {
        List<AttributionOffering> rows = offeringMapper.selectList(
                new LambdaQueryWrapper<AttributionOffering>().orderByAsc(AttributionOffering::getOfferid));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionOffering row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("offerid", row.getOfferid());
            item.put("offering_name", row.getOfferingName());
            item.put("threshold_note", row.getThresholdNote());
            items.add(item);
        }
        log.info("[Attribution] attribution_offering 全量查询命中 {} 条", items.size());
        return wrap(null, items);
    }

    /** 按 offerid 查询渠道证据，按统计日倒序、贡献占比倒序。 */
    public Map<String, Object> listChannel(String offerid, Integer limit) {
        String oid = normalize(offerid);
        if (oid.isEmpty()) {
            return emptyOfferid();
        }
        List<AttributionChannelEvidence> rows = channelMapper.selectList(
                new LambdaQueryWrapper<AttributionChannelEvidence>()
                        .eq(AttributionChannelEvidence::getOfferid, oid)
                        .orderByDesc(AttributionChannelEvidence::getStatDate)
                        .orderByDesc(AttributionChannelEvidence::getContribRatio)
                        .last("LIMIT " + limit(limit)));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionChannelEvidence row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidence_id", row.getEvidenceId());
            item.put("offerid", row.getOfferid());
            item.put("channel_id", row.getChannelId());
            item.put("channel_name", row.getChannelName());
            item.put("order_delta", row.getOrderDelta());
            item.put("contrib_ratio", row.getContribRatio());
            item.put("weight_hint", row.getWeightHint());
            item.put("stat_date", row.getStatDate());
            items.add(item);
        }
        return wrap(oid, items);
    }

    /** 按 offerid 查询促销证据，按统计日倒序、驱动订单占比倒序。 */
    public Map<String, Object> listPromotion(String offerid, Integer limit) {
        String oid = normalize(offerid);
        if (oid.isEmpty()) {
            return emptyOfferid();
        }
        List<AttributionPromotion> rows = promotionMapper.selectList(
                new LambdaQueryWrapper<AttributionPromotion>()
                        .eq(AttributionPromotion::getOfferid, oid)
                        .orderByDesc(AttributionPromotion::getStatDate)
                        .orderByDesc(AttributionPromotion::getDrivenOrderRatio)
                        .last("LIMIT " + limit(limit)));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionPromotion row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidence_id", row.getEvidenceId());
            item.put("offerid", row.getOfferid());
            item.put("promo_id", row.getPromoId());
            item.put("promo_name", row.getPromoName());
            item.put("days_to_expire", row.getDaysToExpire());
            item.put("driven_order_ratio", row.getDrivenOrderRatio());
            item.put("weight_hint", row.getWeightHint());
            item.put("stat_date", row.getStatDate());
            items.add(item);
        }
        return wrap(oid, items);
    }

    /** 按 offerid 查询竞品证据，按统计日倒序、价差比倒序。 */
    public Map<String, Object> listCompetitor(String offerid, Integer limit) {
        String oid = normalize(offerid);
        if (oid.isEmpty()) {
            return emptyOfferid();
        }
        List<AttributionCompetitor> rows = competitorMapper.selectList(
                new LambdaQueryWrapper<AttributionCompetitor>()
                        .eq(AttributionCompetitor::getOfferid, oid)
                        .orderByDesc(AttributionCompetitor::getStatDate)
                        .orderByDesc(AttributionCompetitor::getPriceGapRatio)
                        .last("LIMIT " + limit(limit)));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionCompetitor row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidence_id", row.getEvidenceId());
            item.put("offerid", row.getOfferid());
            item.put("competitor_id", row.getCompetitorId());
            item.put("competitor_name", row.getCompetitorName());
            item.put("price_gap", row.getPriceGap());
            item.put("price_gap_ratio", row.getPriceGapRatio());
            item.put("penetration_delta_pp", row.getPenetrationDeltaPp());
            item.put("weight_hint", row.getWeightHint());
            item.put("stat_date", row.getStatDate());
            items.add(item);
        }
        return wrap(oid, items);
    }

    /** 按 offerid 查询用户行为证据，按统计日倒序、权重提示倒序。 */
    public Map<String, Object> listUserBehavior(String offerid, Integer limit) {
        String oid = normalize(offerid);
        if (oid.isEmpty()) {
            return emptyOfferid();
        }
        List<AttributionUserBehavior> rows = behaviorMapper.selectList(
                new LambdaQueryWrapper<AttributionUserBehavior>()
                        .eq(AttributionUserBehavior::getOfferid, oid)
                        .orderByDesc(AttributionUserBehavior::getStatDate)
                        .orderByDesc(AttributionUserBehavior::getWeightHint)
                        .last("LIMIT " + limit(limit)));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionUserBehavior row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("evidence_id", row.getEvidenceId());
            item.put("offerid", row.getOfferid());
            item.put("behavior_id", row.getBehaviorId());
            item.put("behavior_name", row.getBehaviorName());
            item.put("weight_hint", row.getWeightHint());
            item.put("stat_date", row.getStatDate());
            items.add(item);
        }
        return wrap(oid, items);
    }

    /** 按 offerid 查询指标快照，按统计日倒序、指标编码升序。 */
    public Map<String, Object> listMetric(String offerid, Integer limit) {
        String oid = normalize(offerid);
        if (oid.isEmpty()) {
            return emptyOfferid();
        }
        List<AttributionMetric> rows = metricMapper.selectList(
                new LambdaQueryWrapper<AttributionMetric>()
                        .eq(AttributionMetric::getOfferid, oid)
                        .orderByDesc(AttributionMetric::getStatDate)
                        .orderByAsc(AttributionMetric::getMetricCode)
                        .last("LIMIT " + limit(limit)));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionMetric row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("metric_id", row.getMetricId());
            item.put("offerid", row.getOfferid());
            item.put("metric_code", row.getMetricCode());
            item.put("metric_value", row.getMetricValue());
            item.put("metric_delta", row.getMetricDelta());
            item.put("anomaly_flag", row.getAnomalyFlag());
            item.put("stat_date", row.getStatDate());
            items.add(item);
        }
        return wrap(oid, items);
    }

    private Map<String, Object> wrap(String offerid, List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        if (offerid != null) {
            body.put("offerid", offerid);
        }
        body.put("total", items.size());
        body.put("items", items);
        body.put("generatedAt", Instant.now().toString());
        log.info("[Attribution] offerid={} 命中 {} 条", offerid == null ? "<all>" : offerid, items.size());
        return body;
    }

    private Map<String, Object> emptyOfferid() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", 0);
        body.put("items", List.of());
        body.put("message", "offerid 不能为空");
        return body;
    }

    private String normalize(String offerid) {
        return offerid == null ? "" : offerid.trim();
    }

    private int limit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}
