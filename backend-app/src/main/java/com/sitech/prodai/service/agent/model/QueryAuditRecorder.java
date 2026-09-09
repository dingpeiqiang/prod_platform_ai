package com.sitech.prodai.service.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询审计记录器（方案 §6-A3，对应缺口 C3/C8）。
 * <p>
 * 每轮查询请求落一条结构化审计记录（userScope 摘要 + 工具链 + 命中数据范围 + 返回条数），
 * 双出口：
 * <ul>
 *   <li>结构化 INFO 日志（{@code AUDIT} 前缀，可接采集管道/合规抽查）；</li>
 *   <li>审计视图 Map（{@link #record} 返回值），由编排层透出响应 metadata，
 *       随 {@code persistTurn} 落库 {@code pd_ai_chat_message_metadata}（audit key），
 *       支撑"越权尝试"对账与查询热度分析（C4 反哺数据源）。</li>
 * </ul>
 * <p>
 * 只记录、不阻断：审计失败不影响主流程（try-catch 收口）。
 * 工具命中范围摘要从 {@link ExecutionResult} 数据提取（COUNT/ITEMS 角色契约键优先，
 * 回落 size/total/series 常见键），不读原始行内容——审计留痕不含敏感明细。
 */
@Component
public class QueryAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(QueryAuditRecorder.class);

    /** 审计视图在消息 metadata / 响应中的 key（与前端 restoreMessageMetadata 约定）。 */
    public static final String AUDIT_KEY = "audit";

    /** 各工具命中条数提取的候选键（按序探测，命中即用）。 */
    private static final List<String> COUNT_KEYS = List.of("entity_ids", "items", "series", "rows", "raw_results");

    /**
     * 构建一轮查询的审计视图。
     *
     * @param scope   本次请求权限上下文（null = 未启用行权限形态）
     * @param scene   助手场景（rd/ops/query）
     * @param plan    查询计划（null = FLOW_EXEC 等非计划链路）
     * @param results 执行结果（null 安全）
     * @param playbook 手册编码（未走手册链路传 null）
     * @return 审计视图（snake_case，非 null）
     */
    public Map<String, Object> build(UserScope scope, String scene, QueryPlan plan,
                                     List<ExecutionResult> results, String playbook) {
        Map<String, Object> audit = new LinkedHashMap<>();
        try {
            audit.put("ts", Instant.now().toString());
            if (scope != null) {
                audit.put("scope_user", scope.getUserId());
                audit.put("scope_channels", scope.getVisibleChannels());
                audit.put("scope_max_sensitivity", scope.getMaxSensitivity());
                audit.put("restricted", !scope.isAllChannels());
            } else {
                audit.put("scope_user", "anonymous");
                audit.put("restricted", false);
            }
            audit.put("scene", scene == null ? "" : scene);
            if (playbook != null && !playbook.isBlank()) {
                audit.put("playbook", playbook);
            }
            if (plan != null) {
                audit.put("intent", plan.getIntent());
                audit.put("tools", plan.getTools());
            }
            audit.put("tool_hits", toolHits(results));
        } catch (Exception e) {
            log.debug("[QueryAuditRecorder] 审计视图构建降级: {}", e.getMessage());
        }
        return audit;
    }

    /**
     * 落审计：结构化日志 + 返回视图供调用方透出/落库。
     * 记录失败不影响主流程。
     */
    public Map<String, Object> record(Map<String, Object> audit) {
        try {
            log.info("[AUDIT] {}", audit);
        } catch (Exception ignored) {
            // 审计日志失败不阻断主流程
        }
        return audit;
    }

    /** 工具命中摘要：[{tool, hits}]（只留条数，不带行内容）。 */
    private List<Map<String, Object>> toolHits(List<ExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(r -> {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("tool", r.getToolName());
                    hit.put("status", r.isSuccess() ? "ok" : "fail");
                    hit.put("hits", extractHitCount(r));
                    return hit;
                })
                .toList();
    }

    /** 从工具输出契约键提取命中条数（无契约键时 -1 表示未知）。 */
    private int extractHitCount(ExecutionResult result) {
        if (!result.isSuccess() || result.getData() == null) {
            return -1;
        }
        Object data = result.getData();
        if (!(data instanceof Map<?, ?> map)) {
            return -1;
        }
        for (String key : COUNT_KEYS) {
            Object val = map.get(key);
            if (val instanceof List<?> list) {
                return list.size();
            }
        }
        if (map.get("total") instanceof Number n) {
            return n.intValue();
        }
        if (map.get("entity_count") instanceof Number n) {
            return n.intValue();
        }
        return -1;
    }
}
