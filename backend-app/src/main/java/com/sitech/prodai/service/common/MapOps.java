package com.sitech.prodai.service.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Map/类型转换静态工具（R2 Phase6 从 ProductOntologyService 提取，多协作类共用）。
 * <p>语义与拆分前逐一平移（行为零变更）：str/num/truthy/empty/firstNonEmpty/castMap/
 * castListOfMaps/castList/deepCopy/resolveFixedFee/triple/toLong。
 * NUM_PATTERN 仅服务 {@link #num(Object, double)} 的数字段提取回退。
 */
public final class MapOps {

    private static final Pattern NUM_PATTERN = Pattern.compile("[\\d.]+");

    private MapOps() {
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean empty(Object value) {
        return value == null || str(value).isBlank();
    }

    public static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String text = str(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("1", "true", "yes", "y", "是").contains(text);
    }

    public static double num(Object value, double defaultValue) {
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = str(value).replace("元", "").replace("/月", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            Matcher m = NUM_PATTERN.matcher(text);
            if (m.find()) {
                return Double.parseDouble(m.group());
            }
            return defaultValue;
        }
    }

    public static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static Object firstNonEmpty(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (!empty(v)) {
                return v;
            }
        }
        return values.length > 0 ? values[values.length - 1] : null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> castListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    public static Map<String, Object> deepCopy(ObjectMapper objectMapper, Map<String, Object> source) {
        return objectMapper.convertValue(source, new TypeReference<>() {});
    }

    /** 草稿固费解析：fixedFeeAmount → monthlyFee → chargePlan.fixedFeeAmount，缺省 -1。 */
    public static double resolveFixedFee(Map<String, Object> draft) {
        Object fee = firstNonEmpty(
                draft.get("fixedFeeAmount"),
                draft.get("monthlyFee"),
                castMap(draft.get("chargePlan")).get("fixedFeeAmount"));
        return num(fee, -1);
    }

    /** 证据三元组（s/p/o），供合规/归因/稽核共用展示结构。 */
    public static Map<String, Object> triple(Object s, Object p, Object o) {
        Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("s", s);
        t.put("p", p);
        t.put("o", o);
        return t;
    }
}
