package com.sitech.prodai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.AttributionChannelEvidence;
import com.sitech.prodai.mapper.AttributionChannelEvidenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道归因证据查询（外部库 newpmsdb.attribution_channel_evidence，本体 sc:v-CH-* 事实来源）。
 * <p>按 offerid 只读查询单商品的渠道异动证据快照，供归因结论追溯。
 */
@Service
public class AttributionChannelEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(AttributionChannelEvidenceService.class);

    private static final int MAX_LIMIT = 500;

    private final AttributionChannelEvidenceMapper evidenceMapper;

    public AttributionChannelEvidenceService(AttributionChannelEvidenceMapper evidenceMapper) {
        this.evidenceMapper = evidenceMapper;
    }

    /**
     * 按 offerid 查询渠道归因证据，按统计日倒序、贡献占比倒序返回。
     *
     * @param offerid 商品编码（必填）
     * @param limit   返回条数上限（默认 100，上限 {@value MAX_LIMIT}）
     */
    public Map<String, Object> listByOfferid(String offerid, Integer limit) {
        String oid = offerid == null ? "" : offerid.trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        if (oid.isEmpty()) {
            body.put("total", 0);
            body.put("items", List.of());
            body.put("message", "offerid 不能为空");
            return body;
        }
        int size = limit == null || limit <= 0 ? 100 : Math.min(limit, MAX_LIMIT);

        LambdaQueryWrapper<AttributionChannelEvidence> wrapper = new LambdaQueryWrapper<AttributionChannelEvidence>()
                .eq(AttributionChannelEvidence::getOfferid, oid)
                .orderByDesc(AttributionChannelEvidence::getStatDate)
                .orderByDesc(AttributionChannelEvidence::getContribRatio)
                .last("LIMIT " + size);
        List<AttributionChannelEvidence> rows = evidenceMapper.selectList(wrapper);

        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (AttributionChannelEvidence row : rows) {
            items.add(toItem(row));
        }
        body.put("offerid", oid);
        body.put("total", items.size());
        body.put("items", items);
        body.put("generatedAt", Instant.now().toString());
        log.info("[AttributionChannelEvidence] offerid={} 命中 {} 条", oid, items.size());
        return body;
    }

    private Map<String, Object> toItem(AttributionChannelEvidence row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evidence_id", row.getEvidenceId());
        item.put("offerid", row.getOfferid());
        item.put("channel_id", row.getChannelId());
        item.put("channel_name", row.getChannelName());
        item.put("order_delta", row.getOrderDelta());
        item.put("contrib_ratio", row.getContribRatio());
        item.put("weight_hint", row.getWeightHint());
        item.put("stat_date", row.getStatDate());
        return item;
    }
}
