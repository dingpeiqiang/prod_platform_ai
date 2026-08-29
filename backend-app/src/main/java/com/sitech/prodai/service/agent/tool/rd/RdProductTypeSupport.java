package com.sitech.prodai.service.agent.tool.rd;

import com.sitech.prodai.service.ProductTemplateRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §9.4 翻译层工具入参 product_type（=category_code）支撑：
 * 显式入参优先（需为已注册品类），未识别由 matchers 关键词兜底；
 * 解析结果注入草稿 categoryCode/messageRootKey（仅当草稿未提供时，不覆盖用户口径）。
 */
final class RdProductTypeSupport {

    private RdProductTypeSupport() {
    }

    /** 解析产品品类：显式 product_type 优先，否则按文本 matchers 兜底；无法识别返回 null。 */
    static String resolve(ProductTemplateRegistry registry, String productType, String text) {
        if (productType != null && !productType.isBlank() && !"null".equalsIgnoreCase(productType)) {
            if (registry.findByCategory(productType.trim()).isPresent()) {
                return productType.trim();
            }
        }
        return registry.matchCategory(text);
    }

    /** 将解析出的品类注入草稿（仅补缺，不覆盖既有值），返回新草稿对象。 */
    static Map<String, Object> applyToDraft(Map<String, Object> draft, String category) {
        if (category == null) {
            return draft;
        }
        Map<String, Object> merged = draft == null ? new LinkedHashMap<>() : new LinkedHashMap<>(draft);
        if (empty(merged.get("categoryCode"))) {
            merged.put("categoryCode", category);
        }
        if (empty(merged.get("messageRootKey"))) {
            merged.put("messageRootKey", category);
        }
        return merged;
    }

    private static boolean empty(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
