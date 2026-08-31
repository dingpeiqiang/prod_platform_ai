package com.sitech.prodai.service.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具输出通用渲染器。
 * <p>
 * 依据工具自描述的输出字段契约（{@link ToolOutputField}）对执行结果做通用提取，
 * 取代原先编排层对具体工具名 / 输出键的字符串硬编码。旧工具未声明契约时
 * （{@code getOutputFields()} 为空）自动回落至旧行为（{@code nl_answer}/{@code answer}/
 * {@code conclusion}/{@code contribution}）。
 */
public final class ToolOutputRenderer {

    /** 兼容旧输出键 */
    private static final String LEGACY_SUMMARY_KEYS = "nl_answer,answer";
    private static final String LEGACY_CONCLUSION_KEY = "conclusion";

    private ToolOutputRenderer() {
    }

    /** 工具显示标签。 */
    public static String label(AgentTool tool) {
        return tool != null ? tool.getLabel() : "";
    }

    /** 提取工具执行结果的摘要。 */
    public static String summary(AgentTool tool, Map<String, Object> data) {
        if (tool == null || data == null) {
            return "执行完成";
        }
        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields != null) {
            for (ToolOutputField field : fields) {
                if (field.getRole() == ToolOutputField.Role.SUMMARY
                        && hasValue(data.get(field.getName()))) {
                    return String.valueOf(data.get(field.getName()));
                }
            }
        }
        // 旧行为回落：nl_answer / answer
        String legacy = firstPresent(data, LEGACY_SUMMARY_KEYS.split(","));
        if (legacy != null) {
            return legacy;
        }
        // 依据声明的业务实体名 + 计数生成粗粒度摘要
        return composeFallbackSummary(tool, data);
    }

    /** 提取工具执行结果的最终结论（无可返回 null）。 */
    public static String conclusion(AgentTool tool, Map<String, Object> data) {
        if (tool == null || data == null) {
            return null;
        }
        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields != null) {
            for (ToolOutputField field : fields) {
                if (field.getRole() == ToolOutputField.Role.CONCLUSION
                        && hasValue(data.get(field.getName()))) {
                    return String.valueOf(data.get(field.getName()));
                }
            }
        }
        // 旧行为回落：conclusion
        Object legacy = data.get(LEGACY_CONCLUSION_KEY);
        return hasValue(legacy) ? String.valueOf(legacy) : null;
    }

    /** 提取业务实体缓存信息（{id, name}，缺则空 map）。 */
    public static Map<String, Object> businessEntity(AgentTool tool, Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (tool == null || data == null) {
            return out;
        }
        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields == null) {
            return out;
        }
        String id = null;
        String name = null;
        for (ToolOutputField field : fields) {
            if (field.getRole() == ToolOutputField.Role.BUSINESS_ENTITY_ID && hasValue(data.get(field.getName()))) {
                id = String.valueOf(data.get(field.getName()));
            }
            if (field.getRole() == ToolOutputField.Role.BUSINESS_ENTITY_NAME && hasValue(data.get(field.getName()))) {
                name = String.valueOf(data.get(field.getName()));
            }
        }
        if (id != null) {
            out.put("id", id);
        }
        if (name != null) {
            out.put("name", name);
        }
        return out;
    }

    /** 提取计数指标列表：{name, outputKey, label, value}，供工具执行面板做关键指标展示。 */
    public static List<Map<String, Object>> counts(AgentTool tool, Map<String, Object> data) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (tool == null || data == null) {
            return out;
        }
        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields == null) {
            return out;
        }
        for (ToolOutputField field : fields) {
            if (field.getRole() == ToolOutputField.Role.COUNT
                    && data.get(field.getName()) != null) {
                Object raw = data.get(field.getName());
                long count = raw instanceof java.util.Collection<?> c ? c.size()
                        : raw instanceof java.util.Map<?, ?> ? ((Map<?, ?>) raw).size()
                        : (long) toLong(raw);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", field.getName());
                item.put("outputKey", field.getOutputKey());
                item.put("label", field.getLabel() != null ? field.getLabel() : field.getOutputKey());
                item.put("value", count);
                out.add(item);
            }
        }
        return out;
    }

    /**
     * 构建下发前端 tool 事件 output 对象（含关键计数指标、业务实体名与显式 outputKey 的辅助字段）。
     * <p>
     * 依据工具自描述契约通用组装，兼容前端既有的 output 键契约（如 pathCount / offeringName /
     * remark）。
     */
    public static Map<String, Object> outputEntries(AgentTool tool, Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (tool == null || data == null) {
            return out;
        }
        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields == null) {
            return out;
        }
        out.put("summary", summary(tool, data));
        for (Map<String, Object> c : counts(tool, data)) {
            out.put(String.valueOf(c.get("outputKey")), c.get("value"));
        }
        // 依据工具自描述输出契约，将声明字段的完整取值一并下发（keyed by outputKey），
        // 供前端驱动结构化面板（如研发侧 OfferingCanvas/对比面板/批次清单）。
        // 对既有运营工具为增量数据（额外暴露明细字段），不改变既有 summary/count 语义。
        // COUNT 字段已在上方按计数投影（如 paths→pathCount=N），此处不覆盖既有键，避免原始列表顶掉计数契约
        for (ToolOutputField field : fields) {
            if (field.getOutputKey() == null) {
                continue;
            }
            if (hasValue(data.get(field.getName())) && !out.containsKey(field.getOutputKey())) {
                out.put(field.getOutputKey(), data.get(field.getName()));
            }
        }
        return out;
    }

    private static String composeFallbackSummary(AgentTool tool, Map<String, Object> data) {
        Map<String, Object> entity = businessEntity(tool, data);
        Object name = entity.get("name");
        List<Map<String, Object>> counts = counts(tool, data);
        if (hasValue(name)) {
            String entityName = String.valueOf(name);
            if (!counts.isEmpty()) {
                StringBuilder sb = new StringBuilder(entityName).append("处理完成（");
                for (int i = 0; i < counts.size(); i++) {
                    if (i > 0) {
                        sb.append("，");
                    }
                    sb.append(counts.get(i).get("label")).append(' ').append(counts.get(i).get("value"));
                }
                sb.append('）');
                return sb.toString();
            }
            return entityName + "处理完成";
        }
        if (!counts.isEmpty()) {
            StringBuilder sb = new StringBuilder("处理完成（");
            for (int i = 0; i < counts.size(); i++) {
                if (i > 0) {
                    sb.append("，");
                }
                sb.append(counts.get(i).get("label")).append(' ').append(counts.get(i).get("value"));
            }
            sb.append('）');
            return sb.toString();
        }
        return "执行完成";
    }

    private static String firstPresent(Map<String, Object> data, String[] keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (hasValue(data.get(key))) {
                return String.valueOf(data.get(key));
            }
        }
        return null;
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value);
        return !s.isBlank() && !"null".equals(s);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
