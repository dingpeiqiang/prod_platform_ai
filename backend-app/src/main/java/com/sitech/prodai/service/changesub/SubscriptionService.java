package com.sitech.prodai.service.changesub;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.ChangeAlert;
import com.sitech.prodai.domain.entity.ProductSubscription;
import com.sitech.prodai.mapper.ChangeAlertMapper;
import com.sitech.prodai.mapper.ProductSubscriptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品变更订阅与提醒出口（方案 §6-C3，对应缺口 C5）。
 * <p>
 * 三个职责（全部 try-catch 降级——订阅/提醒失败不影响图谱重载与查询主流程）：
 * <ul>
 *   <li>订阅登记/注销（幂等：subscriber+offering_id 唯一，重复订阅回落 active）；</li>
 *   <li>实现 {@link ProductChangeListener}：变更事件 → 命中订阅商品 → 落提醒行
 *       （发布器异步投递，本监听器只做持久化；生产站内信/短信通道另加监听者）；</li>
 *   <li>提醒查询与已读回执（工具 list_alerts 出口；查询即置已读）。</li>
 * </ul>
 * <p>
 * 订阅人只能来自服务端登录态（工具层从 UserScopeContext 取），任何前端透传的
 * subscriber 字段在本层被忽略——与 UserScope 同一信任模型。
 */
@Service
public class SubscriptionService implements ProductChangeListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";

    private final ProductSubscriptionMapper subscriptionMapper;
    private final ChangeAlertMapper alertMapper;

    public SubscriptionService(ProductSubscriptionMapper subscriptionMapper, ChangeAlertMapper alertMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.alertMapper = alertMapper;
    }

    /**
     * 订阅登记（幂等）：同 subscriber+offering_id 已存在时按 cancelled→active 复活。
     *
     * @param subscriber   订阅人（服务端登录态，必填）
     * @param offeringId   商品编码（必填）
     * @param offeringName 商品名称快照（可为空）
     * @return 登记结果视图（snake_case）
     */
    public Map<String, Object> subscribe(String subscriber, String offeringId, String offeringName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subscriber", subscriber);
        out.put("offering_id", offeringId);
        ProductSubscription existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<ProductSubscription>()
                        .eq(ProductSubscription::getSubscriber, subscriber)
                        .eq(ProductSubscription::getOfferingId, offeringId));
        if (existing != null) {
            existing.setStatus(STATUS_ACTIVE);
            if (offeringName != null && !offeringName.isBlank()) {
                existing.setOfferingName(offeringName);
            }
            subscriptionMapper.updateById(existing);
            out.put("status", STATUS_ACTIVE);
            out.put("renewed", true);
            return out;
        }
        ProductSubscription row = new ProductSubscription();
        row.setSubscriber(subscriber);
        row.setOfferingId(offeringId);
        row.setOfferingName(offeringName);
        row.setStatus(STATUS_ACTIVE);
        subscriptionMapper.insert(row);
        out.put("status", STATUS_ACTIVE);
        out.put("renewed", false);
        return out;
    }

    /** 注销订阅（幂等：未登记过也返回成功，不报错）。 */
    public Map<String, Object> unsubscribe(String subscriber, String offeringId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subscriber", subscriber);
        out.put("offering_id", offeringId);
        ProductSubscription existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<ProductSubscription>()
                        .eq(ProductSubscription::getSubscriber, subscriber)
                        .eq(ProductSubscription::getOfferingId, offeringId));
        if (existing != null && STATUS_ACTIVE.equals(existing.getStatus())) {
            existing.setStatus(STATUS_CANCELLED);
            subscriptionMapper.updateById(existing);
            out.put("status", STATUS_CANCELLED);
            out.put("cancelled", true);
        } else {
            out.put("status", existing == null ? STATUS_CANCELLED : existing.getStatus());
            out.put("cancelled", false);
        }
        return out;
    }

    /** 查询用户当前生效的订阅清单。 */
    public List<ProductSubscription> activeSubscriptionsOf(String subscriber) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<ProductSubscription>()
                .eq(ProductSubscription::getSubscriber, subscriber)
                .eq(ProductSubscription::getStatus, STATUS_ACTIVE)
                .orderByDesc(ProductSubscription::getCreatedAt));
    }

    /**
     * 查询用户提醒（自己名下 + 广播行），按时间倒序；
     * 有未读时回置已读（v1 查询即读语义，读失败不影响返回）。
     */
    public List<ChangeAlert> alertsOf(String subscriber, int limit) {
        List<ChangeAlert> alerts = alertMapper.selectList(new LambdaQueryWrapper<ChangeAlert>()
                .and(w -> w.eq(ChangeAlert::getSubscriber, subscriber).or().isNull(ChangeAlert::getSubscriber))
                .orderByDesc(ChangeAlert::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit)));
        markRead(alerts);
        return alerts;
    }

    /** 用户未读提醒数（前端铃铛徽标预留口径）。 */
    public long unreadCount(String subscriber) {
        Long cnt = alertMapper.selectCount(new LambdaQueryWrapper<ChangeAlert>()
                .eq(ChangeAlert::getSubscriber, subscriber)
                .eq(ChangeAlert::getReadFlag, false));
        return cnt == null ? 0 : cnt;
    }

    /** 变更事件 → 命中订阅商品 → 落提醒行（监听器契约实现；异常降级不阻断）。 */
    @Override
    public void onProductChange(ProductChangeEvent event) {
        try {
            for (Map<String, Object> change : event.getChanges()) {
                String offeringId = str(change.get("offering_id"));
                if (offeringId.isBlank()) {
                    continue;
                }
                List<ProductSubscription> subs = subscriptionMapper.selectList(
                        new LambdaQueryWrapper<ProductSubscription>()
                                .eq(ProductSubscription::getOfferingId, offeringId)
                                .eq(ProductSubscription::getStatus, STATUS_ACTIVE));
                for (ProductSubscription sub : subs) {
                    ChangeAlert alert = new ChangeAlert();
                    alert.setOfferingId(offeringId);
                    alert.setOfferingName(str(change.getOrDefault("offering_name", sub.getOfferingName())));
                    alert.setChangeType(str(change.get("change_type")));
                    alert.setOldValue(str(change.get("old_value")));
                    alert.setNewValue(str(change.get("new_value")));
                    alert.setDetectedVersion(event.getVersion());
                    alert.setSubscriber(sub.getSubscriber());
                    alert.setReadFlag(false);
                    alertMapper.insert(alert);
                }
            }
            log.info("[变更订阅] 提醒落库完成: version={} changes={}", event.getVersion(), event.getChanges().size());
        } catch (Exception e) {
            log.warn("[变更订阅] 提醒落库失败（不影响检测流程）: {}", e.getMessage());
        }
    }

    /** 查询即置已读（只标记自己名下，广播行保持可再读）。 */
    private void markRead(List<ChangeAlert> alerts) {
        try {
            for (ChangeAlert alert : alerts) {
                if (alert.getSubscriber() != null && Boolean.FALSE.equals(alert.getReadFlag())) {
                    alert.setReadFlag(true);
                    alertMapper.updateById(alert);
                }
            }
        } catch (Exception e) {
            log.debug("[变更订阅] 已读回执失败（不影响返回）: {}", e.getMessage());
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
