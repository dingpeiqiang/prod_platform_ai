package com.sitech.prodai.service.appstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 产销品加载 AI 应用 · 内存 Mock 数据中心（11 个插件对接接口共用）。
 * <p>
 * 纯内存实现，用于平台插件联调与 POC 演示；后续替换为 MyBatis-Plus 持久化时，
 * 仅需将本类改为仓储实现，接口契约（snake_case 出参）保持不变。
 * <p>
 * 线程安全：ConcurrentHashMap + AtomicLong 序号；写入类接口幂等键由各 Service 自行维护。
 */
@Service
public class AppMockStore {

    private static final Logger log = LoggerFactory.getLogger(AppMockStore.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ObjectMapper objectMapper;

    /** 产销品配置（接口1 查询源；接口2/3 生成后回写状态） */
    private final Map<String, Map<String, Object>> products = new ConcurrentHashMap<>();
    /** CRM 配置（接口2 产物） */
    private final Map<String, Map<String, Object>> crmConfigs = new ConcurrentHashMap<>();
    /** 计费配置（接口3 产物） */
    private final Map<String, Map<String, Object>> billingConfigs = new ConcurrentHashMap<>();
    /** 测试用例（接口6 产物，接口7 执行输入） */
    private final Map<String, Map<String, Object>> testCases = new ConcurrentHashMap<>();
    /** 用例执行任务（接口7 异步 task_id，接口7b 回查） */
    private final Map<String, Map<String, Object>> executeTasks = new ConcurrentHashMap<>();
    /** 受理验证订单（接口8） */
    private final Map<String, Map<String, Object>> verifyOrders = new ConcurrentHashMap<>();
    /** 审批单（接口9） */
    private final Map<String, Map<String, Object>> approvals = new ConcurrentHashMap<>();
    /** 告警（接口11 产物，接口10 alarm_list 数据源） */
    private final List<Map<String, Object>> alerts = new ArrayList<>();
    /** 幂等记录：idempotency_key -> 响应快照 */
    private final Map<String, Map<String, Object>> idempotency = new ConcurrentHashMap<>();

    private final AtomicLong seqProduct = new AtomicLong(20260000L);
    private final AtomicLong seqCrm = new AtomicLong(1000L);
    private final AtomicLong seqBilling = new AtomicLong(2000L);
    private final AtomicLong seqCase = new AtomicLong(9000L);
    private final AtomicLong seqTask = new AtomicLong(1L);
    private final AtomicLong seqOrder = new AtomicLong(5000L);
    private final AtomicLong seqApproval = new AtomicLong(7000L);
    private final AtomicLong seqAlert = new AtomicLong(3000L);

    public AppMockStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        seed();
    }

    /* ---------------- 统一响应 ---------------- */

    /** 统一成功响应：{code:0, msg:"success", ...业务字段}，契约与插件出参定义一致 */
    public Map<String, Object> ok(String msgKey, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        if (value != null) {
            body.put(msgKey, value);
        }
        return body;
    }

    /** 统一失败响应：{code:<非0>, msg:<原因>} */
    public Map<String, Object> fail(int code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        return body;
    }

    /* ---------------- 幂等 ---------------- */

    /**
     * 写入类接口幂等：命中相同 idempotency_key 直接返回上次结果。
     *
     * @return null 表示首次执行；否则返回快照
     */
    public Map<String, Object> idempotentReplay(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Map<String, Object> snapshot = idempotency.get(key);
        if (snapshot != null) {
            log.info("[AppMockStore] 幂等命中: {}", key);
        }
        return snapshot;
    }

    public void idempotentSave(String key, Map<String, Object> response) {
        if (key != null && !key.isBlank()) {
            idempotency.put(key, response);
        }
    }

    /* ---------------- ID 生成 ---------------- */

    public String nextProductId() {
        return "P" + seqProduct.incrementAndGet();
    }

    public String nextCrmConfigId() {
        return "CRM" + seqCrm.incrementAndGet();
    }

    public String nextBillingConfigId() {
        return "BILL" + seqBilling.incrementAndGet();
    }

    public String nextCaseId() {
        return "TC" + seqCase.incrementAndGet();
    }

    public String nextTaskId() {
        return "T" + LocalDateTime.now().format(TS) + seqTask.incrementAndGet();
    }

    public String nextOrderId() {
        return "ORD" + seqOrder.incrementAndGet();
    }

    public String nextApprovalId() {
        return "AP" + seqApproval.incrementAndGet();
    }

    public String nextAlertId() {
        return "AL" + seqAlert.incrementAndGet();
    }

    public String now() {
        return LocalDateTime.now().toString();
    }

    /* ---------------- 产销品（接口1/2/3/5） ---------------- */

    public void putProduct(Map<String, Object> product) {
        products.put(MapOps.str(product.get("product_id")), product);
    }

    public List<Map<String, Object>> queryProducts(String keyword, String productId, String status) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : products.values()) {
            if (productId != null && !productId.isBlank()
                    && !MapOps.str(p.get("product_id")).contains(productId.trim())) {
                continue;
            }
            if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)
                    && !status.equalsIgnoreCase(MapOps.str(p.get("status")))) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim();
                boolean hit = MapOps.str(p.get("product_name")).contains(kw)
                        || MapOps.str(p.get("product_id")).contains(kw)
                        || MapOps.str(p.get("spec_json")).contains(kw);
                if (!hit) {
                    continue;
                }
            }
            result.add(p);
        }
        result.sort(Comparator.comparing(p -> MapOps.str(p.get("product_id"))));
        return result;
    }

    public Map<String, Object> findProduct(String productId) {
        return products.get(productId);
    }

    /* ---------------- CRM / 计费配置（接口2/3） ---------------- */

    public void putCrmConfig(String key, Map<String, Object> config) {
        crmConfigs.put(key, config);
    }

    public Map<String, Object> findCrmConfigByProduct(String productName) {
        for (Map<String, Object> c : crmConfigs.values()) {
            if (MapOps.str(c.get("product_name")).equals(productName)) {
                return c;
            }
        }
        return null;
    }

    public void putBillingConfig(String key, Map<String, Object> config) {
        billingConfigs.put(key, config);
    }

    public Map<String, Object> findBillingConfig(String billingConfigId) {
        return billingConfigs.get(billingConfigId);
    }

    /* ---------------- 测试用例与任务（接口6/7） ---------------- */

    public void putCase(Map<String, Object> tc) {
        testCases.put(MapOps.str(tc.get("case_id")), tc);
    }

    public Map<String, Object> findCase(String caseId) {
        return testCases.get(caseId);
    }

    public List<Map<String, Object>> findCases(List<String> caseIds) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : caseIds) {
            Map<String, Object> tc = testCases.get(id);
            if (tc != null) {
                list.add(tc);
            }
        }
        return list;
    }

    public void putTask(String taskId, Map<String, Object> task) {
        executeTasks.put(taskId, task);
    }

    public Map<String, Object> findTask(String taskId) {
        return executeTasks.get(taskId);
    }

    /* ---------------- 受理验证 / 审批 / 告警（接口8/9/11） ---------------- */

    public void putVerifyOrder(Map<String, Object> order) {
        verifyOrders.put(MapOps.str(order.get("order_id")), order);
    }

    public Map<String, Object> findLatestVerifyOrder(String productId, String verifyType) {
        Map<String, Object> latest = null;
        for (Map<String, Object> o : verifyOrders.values()) {
            if (!MapOps.str(o.get("product_id")).equals(productId)) {
                continue;
            }
            if (verifyType != null && !verifyType.isBlank()
                    && !verifyType.equalsIgnoreCase(MapOps.str(o.get("verify_type")))) {
                continue;
            }
            if (latest == null || MapOps.str(o.get("created_at"))
                    .compareTo(MapOps.str(latest.get("created_at"))) > 0) {
                latest = o;
            }
        }
        return latest;
    }

    public void putApproval(Map<String, Object> approval) {
        approvals.put(MapOps.str(approval.get("approval_id")), approval);
    }

    public Map<String, Object> findApproval(String approvalId) {
        return approvals.get(approvalId);
    }

    public synchronized void addAlert(Map<String, Object> alert) {
        alerts.add(alert);
    }

    public synchronized List<Map<String, Object>> findAlerts(String productId, String since) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> a : alerts) {
            if (productId != null && !productId.isBlank()
                    && !MapOps.str(a.get("product_id")).equals(productId)) {
                continue;
            }
            if (since != null && !since.isBlank()
                    && MapOps.str(a.get("created_at")).compareTo(since) < 0) {
                continue;
            }
            result.add(a);
        }
        result.sort(Comparator.comparing((Map<String, Object> a) -> MapOps.str(a.get("created_at"))).reversed());
        return result;
    }

    /* ---------------- 内部：种子数据 ---------------- */

    private void seed() {
        putProduct(product("畅享流量包", "10GB国内流量/月，超出5元/GB", "monthly_fee:29元", "anhui-all", "online"));
        putProduct(product("畅享语音包", "300分钟国内通话/月", "monthly_fee:19元", "anhui-all", "online"));
        putProduct(product("5G极速包", "5G网络加速服务", "monthly_fee:10元", "anhui-hefei", "offline"));
        putProduct(product("亲情网", "亲情号码互打免费", "monthly_fee:0元", "anhui-all", "online"));
        log.info("[AppMockStore] 种子产销品 {} 条", products.size());
    }

    private Map<String, Object> product(String name, String spec, String fee, String scope, String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("product_id", nextProductId());
        p.put("product_name", name);
        p.put("spec_json", toJson(Map.of("spec_desc", spec)));
        p.put("fee_json", toJson(Map.of("fee_desc", fee)));
        p.put("sale_scope", scope);
        p.put("status", status);
        p.put("created_at", now());
        return p;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /** JSON 字符串 -> Map（容错：非法 JSON 返回 null） */
    public Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }
}
