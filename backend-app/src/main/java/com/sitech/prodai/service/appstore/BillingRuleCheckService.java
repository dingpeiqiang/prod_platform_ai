package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计费规则校验引擎（接口4）。内置规则可配置化：资费互斥、叠加上限、负资费、边界价差。
 * <p>规则集与知识库同步（POC 阶段内置默认规则，支持运行时增删）。
 */
@Service
public class BillingRuleCheckService {

    /** 叠加优惠数量上限（可配置） */
    private int overlayLimit = 3;
    /** 边界价差阈值（元），资费降幅超过该比例提示风险 */
    private double boundaryPriceRatio = 0.5;
    /** 互斥优惠对（成对记录） */
    private final List<Map<String, Object>> mutexPairs = new ArrayList<>(List.of(
            mutex("discount_type", "limited_time", "discount_type", "long_term", "限时优惠与长期优惠互斥")));
    /** 运行时自定义规则（与知识库同步） */
    private final Map<String, Map<String, Object>> customRules = new ConcurrentHashMap<>();

    /** 校验：check_scene 枚举 fee/overlay/superposition/all */
    public Map<String, Object> verify(Map<String, Object> billingConfig, String checkScene) {
        List<Map<String, Object>> risks = new ArrayList<>();
        String scene = checkScene == null || checkScene.isBlank() ? "all" : checkScene.toLowerCase();

        if ("all".equals(scene) || "fee".equals(scene)) {
            checkNegativeFee(billingConfig, risks);
            checkBoundaryPrice(billingConfig, risks);
        }
        if ("all".equals(scene) || "overlay".equals(scene) || "superposition".equals(scene)) {
            checkOverlay(billingConfig, risks);
            checkMutex(billingConfig, risks);
        }
        checkCustomRules(billingConfig, risks);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put("pass", risks.isEmpty() ? 0 : 1);
        body.put("risk_list", risks);
        return body;
    }

    /** 规则配置更新（与知识库规则同步入口） */
    public Map<String, Object> updateRules(Integer overlayLimit, Double boundaryPriceRatio,
                                           List<Map<String, Object>> newMutexPairs) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (overlayLimit != null && overlayLimit > 0) {
            this.overlayLimit = overlayLimit;
            body.put("overlay_limit", overlayLimit);
        }
        if (boundaryPriceRatio != null && boundaryPriceRatio > 0) {
            this.boundaryPriceRatio = boundaryPriceRatio;
            body.put("boundary_price_ratio", boundaryPriceRatio);
        }
        if (newMutexPairs != null) {
            synchronized (mutexPairs) {
                mutexPairs.clear();
                mutexPairs.addAll(newMutexPairs);
            }
            body.put("mutex_pairs", newMutexPairs.size());
        }
        body.put("code", 0);
        body.put("msg", "success");
        return body;
    }

    private void checkNegativeFee(Map<String, Object> config, List<Map<String, Object>> risks) {
        for (Map<String, Object> fee : feeItems(config)) {
            double amount = MapOps.num(fee.get("amount"), Double.NaN);
            if (!Double.isNaN(amount) && amount < 0) {
                risks.add(risk("negative_fee", "存在负资费: " + MapOps.str(fee.get("fee_name"))
                        + " = " + amount + "元", "修正为非负金额并复核计费事件"));
            }
        }
    }

    private void checkBoundaryPrice(Map<String, Object> config, List<Map<String, Object>> risks) {
        for (Map<String, Object> fee : feeItems(config)) {
            double base = MapOps.num(fee.get("base_amount"), -1);
            double amount = MapOps.num(fee.get("amount"), -1);
            if (base > 0 && amount >= 0 && (base - amount) / base > boundaryPriceRatio) {
                risks.add(risk("boundary_price_gap", "资费边界价差过大: "
                        + MapOps.str(fee.get("fee_name")) + " 降幅超过 "
                        + Math.round(boundaryPriceRatio * 100) + "%", "复核优惠配置与审批依据"));
            }
        }
    }

    private void checkOverlay(Map<String, Object> config, List<Map<String, Object>> risks) {
        List<Map<String, Object>> discounts = MapOps.castListOfMaps(config.get("discount_rules"));
        if (discounts.size() > overlayLimit) {
            risks.add(risk("overlay_limit_exceeded", "叠加优惠数量 " + discounts.size()
                    + " 超过上限 " + overlayLimit, "裁剪低优先级优惠规则"));
        }
    }

    private void checkMutex(Map<String, Object> config, List<Map<String, Object>> risks) {
        List<Map<String, Object>> discounts = MapOps.castListOfMaps(config.get("discount_rules"));
        List<Map<String, Object>> pairs;
        synchronized (mutexPairs) {
            pairs = new ArrayList<>(mutexPairs);
        }
        for (Map<String, Object> pair : pairs) {
            String leftKey = MapOps.str(pair.get("left_key"));
            String leftValue = MapOps.str(pair.get("left_value"));
            String rightKey = MapOps.str(pair.get("right_key"));
            String rightValue = MapOps.str(pair.get("right_value"));
            boolean hasLeft = discounts.stream().anyMatch(d -> leftValue.equals(MapOps.str(d.get(leftKey))));
            boolean hasRight = discounts.stream().anyMatch(d -> rightValue.equals(MapOps.str(d.get(rightKey))));
            if (hasLeft && hasRight) {
                risks.add(risk("overlap_conflict", "互斥优惠同时存在: "
                        + MapOps.str(pair.get("desc")), "移除其中一方叠加规则"));
            }
        }
    }

    private void checkCustomRules(Map<String, Object> config, List<Map<String, Object>> risks) {
        for (Map<String, Object> rule : customRules.values()) {
            String field = MapOps.str(rule.get("field"));
            String op = MapOps.str(rule.get("op"));
            Object expect = rule.get("value");
            Object actual = config.get(field);
            boolean violated = switch (op) {
                case "not_null" -> MapOps.empty(actual);
                case "equals" -> !String.valueOf(expect).equals(MapOps.str(actual));
                default -> false;
            };
            if (violated) {
                risks.add(risk("custom_rule", MapOps.str(rule.get("desc")),
                        MapOps.str(rule.get("suggest"))));
            }
        }
    }

    private List<Map<String, Object>> feeItems(Map<String, Object> config) {
        List<Map<String, Object>> items = MapOps.castListOfMaps(config.get("fee_items"));
        return items.isEmpty() && !config.isEmpty() ? List.of(config) : items;
    }

    private Map<String, Object> risk(String type, String desc, String suggest) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("risk_type", type);
        r.put("risk_desc", desc);
        r.put("suggest", suggest);
        return r;
    }

    private static Map<String, Object> mutex(String lk, String lv, String rk, String rv, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("left_key", lk);
        m.put("left_value", lv);
        m.put("right_key", rk);
        m.put("right_value", rv);
        m.put("desc", desc);
        return m;
    }
}
