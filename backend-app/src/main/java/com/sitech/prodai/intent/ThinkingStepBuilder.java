package com.sitech.prodai.intent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景化思考步骤构造器 —— 统一 id/title/type/content/result/details 契约。
 */
public final class ThinkingStepBuilder {

    private ThinkingStepBuilder() {
    }

    /** 运行中步骤（elapsed=-1，phase=running） */
    public static Map<String, Object> running(
            String id,
            String title,
            String content,
            int step,
            int totalSteps,
            Map<String, Object> extraMeta
    ) {
        return build(id, title, "llm", content, null, null, step, totalSteps, -1, "running", extraMeta);
    }

    /** 完成步骤（含 result） */
    public static Map<String, Object> done(
            String id,
            String title,
            String content,
            Object result,
            int step,
            int totalSteps,
            long elapsedMs,
            String details,
            Map<String, Object> extraMeta
    ) {
        return build(id, title, "llm", content, result, details, step, totalSteps, elapsedMs, "done", extraMeta);
    }

    /** 本体/知识推理完成步骤 */
    public static Map<String, Object> doneOntology(
            String id,
            String title,
            String content,
            Object result,
            int step,
            int totalSteps,
            long elapsedMs,
            String details,
            Map<String, Object> extraMeta
    ) {
        return build(id, title, "ontology", content, result, details, step, totalSteps, elapsedMs, "done", extraMeta);
    }

    /** 工具调用步骤 */
    public static Map<String, Object> doneTool(
            String id,
            String title,
            String content,
            Object result,
            int step,
            int totalSteps,
            long elapsedMs,
            String details,
            Map<String, Object> extraMeta
    ) {
        return build(id, title, "tool", content, result, details, step, totalSteps, elapsedMs, "done", extraMeta);
    }

    public static Map<String, Object> build(
            String id,
            String title,
            String type,
            String content,
            Object result,
            String details,
            int step,
            int totalSteps,
            long elapsedMs,
            String phase,
            Map<String, Object> extraMeta
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("step", step);
        meta.put("totalSteps", totalSteps);
        meta.put("scheduleId", id);
        meta.put("phase", phase);
        if (extraMeta != null) {
            for (Map.Entry<String, Object> e : extraMeta.entrySet()) {
                if (e.getValue() != null) {
                    meta.put(e.getKey(), e.getValue());
                }
            }
        }
        return SseUtils.thinkingRich(id, title, type, content, result, meta, elapsedMs, details);
    }
}
