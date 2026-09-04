package com.sitech.prodai.service;

import com.sitech.prodai.domain.entity.OpsWorkOrder;
import com.sitech.prodai.mapper.OpsWorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营处置工单服务（R2 Phase1 从 {@link ProductOntologyService} 拆出）。
 * <p>职责单一：工单持久化（pd_ai_ops_work_orders）、状态机流转、改名同步、会话/全局列表查询与计数。
 * 图回写通过 {@link WorkOrderGraphCoordinator} 回调宿主，不持有事实图缓存所有权。
 * <p>对外 API 语义与拆分前一致；ProductOntologyService 保留薄委托 Facade。
 * <p>协调器为宿主方法引用（容器无 bean 定义），经 {@link #setGraphCoordinator} 在宿主装配完成后注入，
 * 调用点判空兜底，避免构造期环依赖与 NoSuchBeanDefinitionException。
 */
@Service
public class OpsWorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(OpsWorkOrderService.class);

    private static final Set<String> ALLOWED_STATUS = Set.of("open", "in_progress", "done", "cancelled");

    private final OpsWorkOrderMapper workOrderMapper;
    /** 宿主回调惰性持有：构造期宿主尚在装配中，须延迟到调用点解析。 */
    private WorkOrderGraphCoordinator graphCoordinator;

    public OpsWorkOrderService(OpsWorkOrderMapper workOrderMapper) {
        this.workOrderMapper = workOrderMapper;
    }

    /** 宿主回调注入点：宿主装配完成后回调此方法，打破构造期环依赖。 */
    public void setGraphCoordinator(WorkOrderGraphCoordinator graphCoordinator) {
        this.graphCoordinator = graphCoordinator;
    }

    /**
     * 生成处置工单：持久化到 DB，并经协调器回写内存事实图 dispositionStatus。
     */
    public Map<String, Object> createWorkOrder(Map<String, Object> request) {
        if (graphCoordinator == null) {
            log.warn("[OpsWorkOrderService] 协调器未注入（宿主装配中），拒绝开单");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "服务初始化中，请稍后重试");
            return body;
        }
        Map<String, Object> req = request == null ? Map.of() : request;
        String offeringId = str(req.getOrDefault("offeringId", req.get("offering_id")));
        String source = str(req.getOrDefault("source", "manual"));
        String sessionId = str(req.getOrDefault("sessionId", req.get("session_id")));
        String title = str(req.get("title"));
        String summary = str(req.getOrDefault("summary", req.getOrDefault("anomalySummary", "")));
        List<Object> actions = castList(req.get("actions")).stream()
                .map(this::str)
                .filter(s -> !s.isBlank())
                .map(s -> (Object) s)
                .collect(Collectors.toList());
        if (actions.isEmpty() && req.get("action") != null) {
            actions = List.of(str(req.get("action")));
        }

        Map<String, Object> offering = graphCoordinator.findShelfOffering(offeringId);
        String offeringName = (offering == null || offering.isEmpty())
                ? str(req.getOrDefault("offeringName", offeringId))
                : str(offering.getOrDefault("offeringName", offeringId));
        if (title.isBlank()) {
            title = offeringName + ("risk".equals(source) || source.contains("risk")
                    ? "风险处置工单" : "产品优化工单");
        }
        if (actions.isEmpty()) {
            actions = List.of("跟进处置", "同步渠道与产品运营复核");
        }

        String woId = "WO" + Instant.now().toEpochMilli();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (req.get("impacts") != null) {
            payload.put("impacts", req.get("impacts"));
        }
        if (req.get("rootCauses") != null) {
            payload.put("rootCauses", req.get("rootCauses"));
        }
        if (req.get("hypoMode") != null) {
            payload.put("hypoMode", req.get("hypoMode"));
        }
        // 关联配置草稿：工单卡删除/复制操作按工单号反查草稿的唯一凭据
        String draftIdLink = str(firstNonEmpty(req.get("draftId"), req.get("draft_id")));
        if (!draftIdLink.isBlank() && !"null".equals(draftIdLink)) {
            payload.put("draftId", draftIdLink);
        }
        // 工单关联触发提交的配置工单（前端合并展示/高亮来源）
        String relatedWo = str(firstNonEmpty(req.get("relatedWorkOrderId"), req.get("related_work_order_id")));
        if (!relatedWo.isBlank() && !"null".equals(relatedWo)) {
            payload.put("relatedWorkOrderId", relatedWo);
        }
        // 稽核结果随单：工单卡直接展示草稿合规结论（issues 为稽核规则问题明细）
        if (req.get("compliancePass") != null) {
            payload.put("compliancePass", req.get("compliancePass"));
        }
        if (req.get("complianceIssues") != null) {
            payload.put("complianceIssues", req.get("complianceIssues"));
        }

        OpsWorkOrder entity = new OpsWorkOrder();
        entity.setWorkOrderId(woId);
        entity.setTitle(title);
        entity.setOfferingId(offeringId);
        entity.setOfferingName(offeringName);
        entity.setSummary(summary.isBlank() ? title : summary);
        entity.setActions(actions);
        entity.setStatus("open");
        entity.setSource(source.isBlank() ? "ops_assistant" : source);
        entity.setSessionId(sessionId.isBlank() ? null : sessionId);
        entity.setHypoMode(str(req.get("hypoMode")));
        entity.setPayload(payload);
        workOrderMapper.insert(entity);
        OpsWorkOrder saved = entity;

        Map<String, Object> wo = toWorkOrderMap(saved);
        // 回写内存图（工单闭环可见）
        graphCoordinator.onWorkOrderCreated(offeringId, woId, wo);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "处置工单已持久化并回写本体事实");
        body.put("workOrder", wo);
        body.put("persisted", true);
        return withModeMeta(body);
    }

    public Map<String, Object> listWorkOrders() {
        return listWorkOrders(null, null);
    }

    public Map<String, Object> listWorkOrders(String status, String sessionId) {
        return listWorkOrders(status, sessionId, null, null, null);
    }

    /**
     * 工单列表查询（会话维度 / 全局）：支持状态过滤 + 关键词匹配（工单号/标题/商品名/商品编码）+ 分页。
     * <p>
     * 大批量文件解析一次可开数百单，前端消息窗工单卡按页拉取，避免一次渲染全部条目。
     * page 从 1 开始；size 缺省 20；q 为空时不过滤关键词；无分页参数时保持旧行为（Top50 全量）。
     */
    public Map<String, Object> listWorkOrders(String status, String sessionId, Integer page, Integer size, String q) {
        String sid = sessionId == null ? "" : sessionId.trim();
        String st = status == null ? "" : status.trim();
        String kw = q == null ? "" : q.trim();
        boolean byStatus = !st.isBlank() && !"all".equalsIgnoreCase(st);

        List<OpsWorkOrder> rows;
        // 无分页参数 → 旧行为（Top50，保持历史调用兼容）
        boolean paged = page != null || size != null;
        if (paged) {
            int pageNum = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

            boolean hasKw = !kw.isBlank();
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<OpsWorkOrder> mpPage =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder>()
                            .orderByDesc(OpsWorkOrder::getCreatedAt);
            if (!sid.isBlank()) {
                wrapper.eq(OpsWorkOrder::getSessionId, sid);
            }
            if (byStatus) {
                wrapper.eq(OpsWorkOrder::getStatus, st.toLowerCase(Locale.ROOT));
            }
            if (hasKw) {
                String like = "%" + kw.toLowerCase(Locale.ROOT) + "%";
                wrapper.and(w -> w
                        .apply("LOWER(COALESCE(work_order_id, '')) LIKE {0}", like)
                        .or().apply("LOWER(COALESCE(title, '')) LIKE {0}", like)
                        .or().apply("LOWER(COALESCE(offering_name, '')) LIKE {0}", like)
                        .or().apply("LOWER(COALESCE(offering_id, '')) LIKE {0}", like));
            }
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<OpsWorkOrder> result =
                    workOrderMapper.selectPage(mpPage, wrapper);
            List<Map<String, Object>> items = result.getRecords().stream()
                    .map(this::toWorkOrderMap).collect(Collectors.toList());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("total", result.getTotal());
            body.put("page", pageNum);
            body.put("size", pageSize);
            body.put("pages", result.getPages());
            body.put("statusFilter", byStatus ? st : "all");
            body.put("sessionId", sid.isBlank() ? null : sid);
            body.put("q", kw.isBlank() ? null : kw);
            body.put("items", items);
            body.put("counts", workOrderCounts(sid));
            return withModeMeta(body);
        }

        if (!sid.isBlank() && byStatus) {
            rows = findTop50WorkOrders(w -> w.eq(OpsWorkOrder::getSessionId, sid)
                    .eq(OpsWorkOrder::getStatus, st.toLowerCase(Locale.ROOT)));
        } else if (!sid.isBlank()) {
            rows = findTop50WorkOrders(w -> w.eq(OpsWorkOrder::getSessionId, sid));
        } else if (byStatus) {
            rows = findTop50WorkOrders(w -> w.eq(OpsWorkOrder::getStatus, st.toLowerCase(Locale.ROOT)));
        } else {
            rows = findTop50WorkOrders(w -> {});
        }
        List<Map<String, Object>> items = rows.stream().map(this::toWorkOrderMap).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("total", items.size());
        body.put("statusFilter", byStatus ? st : "all");
        body.put("sessionId", sid.isBlank() ? null : sid);
        body.put("items", items);
        body.put("counts", workOrderCounts(sid));
        return withModeMeta(body);
    }

    /**
     * 工单状态流转：open → in_progress → done / cancelled。
     * 完成后经协调器回写货架 dispositionStatus=work_order_done。
     */
    public Map<String, Object> updateWorkOrderStatus(String workOrderId, String status, String remark) {
        String wid = workOrderId == null ? "" : workOrderId.trim();
        String next = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (wid.isBlank()) {
            return withModeMeta(Map.of("success", false, "message", "workOrderId 不能为空"));
        }
        if (!ALLOWED_STATUS.contains(next)) {
            return withModeMeta(Map.of(
                    "success", false,
                    "message", "非法状态，允许：open / in_progress / done / cancelled"
            ));
        }

        OpsWorkOrder entity = findWorkOrderByWorkOrderId(wid);
        if (entity == null) {
            return withModeMeta(Map.of("success", false, "message", "工单不存在: " + wid));
        }
        String prev = entity.getStatus();
        if (!isValidTransition(prev, next)) {
            return withModeMeta(Map.of(
                    "success", false,
                    "message", "不允许从 " + prev + " 流转到 " + next,
                    "workOrder", toWorkOrderMap(entity)
            ));
        }

        entity.setStatus(next);
        Map<String, Object> payload = entity.getPayload() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
        List<Object> history = castList(payload.get("statusHistory"));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("from", prev);
        step.put("to", next);
        step.put("at", Instant.now().toString());
        if (remark != null && !remark.isBlank()) {
            step.put("remark", remark);
        }
        history = new ArrayList<>(history);
        history.add(step);
        payload.put("statusHistory", history);
        if (remark != null && !remark.isBlank()) {
            payload.put("lastRemark", remark);
        }
        entity.setPayload(payload);
        workOrderMapper.updateById(entity);
        OpsWorkOrder saved = entity;

        graphCoordinator.syncWorkOrderToGraph(toWorkOrderMap(saved), saved.getStatus());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", statusLabel(next));
        body.put("workOrder", toWorkOrderMap(saved));
        body.put("previousStatus", prev);
        return withModeMeta(body);
    }

    /**
     * 草稿改名后同步更新工单的资费名称与标题（卡片展示列），并经协调器同步图谱。
     * 摘要中内嵌的旧月费金额同步替换（月费联动时传入 newFee）。
     * 名称未变化或工单不存在时静默返回（success=false 不阻断修改主流程）。
     */
    public Map<String, Object> renameWorkOrder(String workOrderId, String offeringName, String newFee) {
        String wid = workOrderId == null ? "" : workOrderId.trim();
        String name = offeringName == null ? "" : offeringName.trim();
        if (wid.isBlank() || name.isBlank()) {
            return withModeMeta(Map.of("success", false, "message", "workOrderId / offeringName 不能为空"));
        }
        OpsWorkOrder entity = findWorkOrderByWorkOrderId(wid);
        if (entity == null) {
            return withModeMeta(Map.of("success", false, "message", "工单不存在: " + wid));
        }
        if (name.equals(entity.getOfferingName())) {
            return withModeMeta(Map.of("success", true, "message", "名称未变化", "workOrder", toWorkOrderMap(entity)));
        }
        entity.setOfferingName(name);
        // 标题与开单规则保持一致：名称 + "配置工单"
        entity.setTitle(name + "配置工单");
        // 摘要随月费联动刷新（开单时固化的「月费=158.0，场景=…」文案），fee 为空则原样保留
        String fee = newFee == null ? "" : newFee.trim();
        if (!fee.isBlank() && entity.getSummary() != null) {
            entity.setSummary(entity.getSummary().replaceAll("月费=[\\d.]+", "月费=" + fee));
        }
        workOrderMapper.updateById(entity);
        OpsWorkOrder saved = entity;
        graphCoordinator.syncWorkOrderToGraph(toWorkOrderMap(saved), saved.getStatus());
        return withModeMeta(Map.of("success", true, "message", "工单名称已同步更新", "workOrder", toWorkOrderMap(saved)));
    }

    private OpsWorkOrder findWorkOrderByWorkOrderId(String workOrderId) {
        return workOrderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder>()
                .eq(OpsWorkOrder::getWorkOrderId, workOrderId)
                .last("LIMIT 1"));
    }

    private List<OpsWorkOrder> findTop50WorkOrders(
            java.util.function.Consumer<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder>> extra) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder>()
                        .orderByDesc(OpsWorkOrder::getCreatedAt)
                        .last("LIMIT 50");
        extra.accept(wrapper);
        return workOrderMapper.selectList(wrapper);
    }

    private long countWorkOrdersByStatus(String status) {
        return workOrderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpsWorkOrder>()
                .eq(OpsWorkOrder::getStatus, status));
    }

    /** 会话/全局工单状态计数（会话维度按 sessionId 过滤，全局按状态列统计）。 */
    private Map<String, Object> workOrderCounts(String sid) {
        Map<String, Object> counts = new LinkedHashMap<>();
        if (sid != null && !sid.isBlank()) {
            List<OpsWorkOrder> all = findTop50WorkOrders(w -> w.eq(OpsWorkOrder::getSessionId, sid));
            counts.put("open", all.stream().filter(i -> "open".equals(i.getStatus())).count());
            counts.put("in_progress", all.stream().filter(i -> "in_progress".equals(i.getStatus())).count());
            counts.put("done", all.stream().filter(i -> "done".equals(i.getStatus())).count());
            counts.put("cancelled", all.stream().filter(i -> "cancelled".equals(i.getStatus())).count());
        } else {
            counts.put("open", countWorkOrdersByStatus("open"));
            counts.put("in_progress", countWorkOrdersByStatus("in_progress"));
            counts.put("done", countWorkOrdersByStatus("done"));
            counts.put("cancelled", countWorkOrdersByStatus("cancelled"));
        }
        return counts;
    }

    private boolean isValidTransition(String from, String to) {
        if (from == null || from.isBlank()) {
            from = "open";
        }
        if (from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "open" -> Set.of("in_progress", "cancelled", "done").contains(to);
            case "in_progress" -> Set.of("done", "cancelled", "open").contains(to);
            case "done", "cancelled" -> Set.of("open", "in_progress").contains(to); // 允许重开
            default -> true;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "open" -> "工单已重开/待处理";
            case "in_progress" -> "工单已进入处理中";
            case "done" -> "工单已完成，处置结果已回写本体";
            case "cancelled" -> "工单已取消";
            default -> "工单状态已更新";
        };
    }

    private Map<String, Object> toWorkOrderMap(OpsWorkOrder e) {
        Map<String, Object> wo = new LinkedHashMap<>();
        wo.put("id", e.getId());
        wo.put("workOrderId", e.getWorkOrderId());
        wo.put("title", e.getTitle());
        wo.put("offeringId", e.getOfferingId());
        wo.put("offeringName", e.getOfferingName());
        wo.put("summary", e.getSummary());
        wo.put("actions", e.getActions() == null ? List.of() : e.getActions());
        wo.put("status", e.getStatus());
        wo.put("source", e.getSource());
        wo.put("sessionId", e.getSessionId());
        wo.put("hypoMode", e.getHypoMode());
        if (e.getPayload() != null) {
            wo.putAll(e.getPayload());
        }
        wo.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : Instant.now().toString());
        wo.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        return wo;
    }

    /** 在响应中标注是否演示模式及数据来源（与宿主 ProductOntologyService 口径一致）。 */
    private Map<String, Object> withModeMeta(Map<String, Object> body) {
        Map<String, Object> result = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (graphCoordinator != null) {
            result.putAll(graphCoordinator.modeMeta());
        }
        return result;
    }

    private List<Object> castList(Object value) {
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) list;
            return result;
        }
        return List.of();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean empty(Object value) {
        return value == null || str(value).isBlank();
    }

    private Object firstNonEmpty(Object... values) {
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
}
