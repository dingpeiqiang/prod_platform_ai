package com.sitech.prodai.service.changesub;

import com.sitech.prodai.config.ProdAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品变更检测服务（方案 §6-C3，对应缺口 C5）：货架快照字段级 diff。
 * <p>
 * 事件源：图谱重载链 COMMIT 成功后由 {@code ProductOntologyService} 回调
 * {@link #detect(String, List, List)}——对比新旧货架快照的
 * 月费（fee_change）与状态（state_change）两个字段（目录级可观察字段；
 * 下架→on_shelf 缺失/出现由快照集差推导）。
 * <p>
 * 纯函数对比（DeriveDiffUtil 风格）：输入新旧快照清单，输出变更条目清单；
 * 检测异常/开关关闭均静默跳过，不影响图谱重载主流程（检测可靠性不绑通知可靠性）。
 * <p>
 * 数据源策略：检测仅依赖调用方喂入的两份快照（与 ProductOntologyService 装配解耦），
 * 生产替换快照供给（mock→jdbc 实源）时本类零改动。
 */
@Service
public class ChangeDetectService {

    private static final Logger log = LoggerFactory.getLogger(ChangeDetectService.class);

    /** 变更类型：月费调整。 */
    public static final String TYPE_FEE_CHANGE = "fee_change";
    /** 变更类型：状态流转（on_shelf/on_sale 等枚举间变化或下架/上架）。 */
    public static final String TYPE_STATE_CHANGE = "state_change";

    private final ProdAiProperties properties;

    public ChangeDetectService(ProdAiProperties properties) {
        this.properties = properties;
    }

    /**
     * 对比新旧货架快照，产出字段级变更清单（不含订阅过滤——订阅匹配在监听者层）。
     *
     * @param version 本次检测对应的快照版本号（图谱重载版本 r...）
     * @param before  旧快照货架条目（offeringId/monthlyFee/state/...）
     * @param after   新快照货架条目
     * @return 变更条目清单（offering_id/offering_name/change_type/old_value/new_value）；无变更返回空
     */
    public List<Map<String, Object>> detect(String version, List<Map<String, Object>> before,
                                            List<Map<String, Object>> after) {
        if (!properties.getChangeSub().isDetectEnabled()) {
            return List.of();
        }
        try {
            Map<String, Map<String, Object>> beforeIdx = indexByOfferingId(before);
            Map<String, Map<String, Object>> afterIdx = indexByOfferingId(after);
            List<Map<String, Object>> changes = new ArrayList<>();
            int maxAlerts = properties.getChangeSub().getMaxAlertsPerDetect();
            // 新快照全量扫描：月费/状态字段级 diff + 新上架（旧快照缺失）
            for (Map.Entry<String, Map<String, Object>> entry : afterIdx.entrySet()) {
                if (changes.size() >= maxAlerts) {
                    log.warn("[变更检测] 提醒产出达护栏 {}，截断（version={}）", maxAlerts, version);
                    break;
                }
                String offeringId = entry.getKey();
                Map<String, Object> afterRow = entry.getValue();
                Map<String, Object> beforeRow = beforeIdx.get(offeringId);
                if (beforeRow == null) {
                    // 新上架：旧快照无此商品（状态=在架语义）
                    changes.add(change(offeringId, afterRow, TYPE_STATE_CHANGE, "", str(afterRow.get("state"))));
                    continue;
                }
                Double oldFee = castDouble(beforeRow.get("monthlyFee"));
                Double newFee = castDouble(afterRow.get("monthlyFee"));
                if (oldFee != null && newFee != null && Double.compare(oldFee, newFee) != 0) {
                    changes.add(change(offeringId, afterRow, TYPE_FEE_CHANGE, trimFee(oldFee), trimFee(newFee)));
                }
                String oldState = str(beforeRow.get("state"));
                String newState = str(afterRow.get("state"));
                if (!oldState.isBlank() && !newState.isBlank() && !oldState.equals(newState)) {
                    changes.add(change(offeringId, afterRow, TYPE_STATE_CHANGE, oldState, newState));
                }
            }
            // 下架检测：旧快照有、新快照无（下架=状态离开在架枚举）
            for (Map.Entry<String, Map<String, Object>> entry : beforeIdx.entrySet()) {
                if (changes.size() >= maxAlerts) {
                    break;
                }
                if (!afterIdx.containsKey(entry.getKey())) {
                    Map<String, Object> beforeRow = entry.getValue();
                    changes.add(change(entry.getKey(), beforeRow, TYPE_STATE_CHANGE,
                            str(beforeRow.get("state")), "off_shelf"));
                }
            }
            if (!changes.isEmpty()) {
                log.info("[变更检测] 检出商品变更: version={} count={}", version, changes.size());
            }
            return changes;
        } catch (Exception e) {
            // 检测异常不阻断图谱重载主流程（last-known-good 已提交，提醒属旁路）
            log.warn("[变更检测] 检测异常（跳过本轮，不影响图谱重载）: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> change(String offeringId, Map<String, Object> row,
                                       String type, String oldValue, String newValue) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("offering_id", offeringId);
        c.put("offering_name", str(row.get("offeringName")));
        c.put("change_type", type);
        c.put("old_value", oldValue);
        c.put("new_value", newValue);
        return c;
    }

    /** 货架条目按 offeringId 建索引（脏条目/无编码跳过）。 */
    private Map<String, Map<String, Object>> indexByOfferingId(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> idx = new LinkedHashMap<>();
        if (items == null) {
            return idx;
        }
        for (Map<String, Object> item : items) {
            String id = str(item.get("offeringId"));
            if (!id.isBlank()) {
                idx.put(id, item);
            }
        }
        return idx;
    }

    /** 月费展示口径：整数值去小数点（128.0 → 128）。 */
    private String trimFee(Double fee) {
        return fee == null ? "" : (fee == Math.floor(fee) ? String.valueOf(fee.longValue()) : String.valueOf(fee));
    }

    private Double castDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
