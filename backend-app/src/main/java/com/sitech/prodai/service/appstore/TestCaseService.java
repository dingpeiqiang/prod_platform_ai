package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 测试用例生成与执行（接口6/7）。
 * <p>
 * 生成：规则驱动，受理/变更/退订/计费四类场景模板化 + 资费边界（生效日、跨月、叠加）用例；
 * 执行：异步 task_id 模式（execute_mode=async），同步模式直接返回结果；环境隔离 sit/uat/pre，禁止打生产。
 */
@Service
public class TestCaseService {

    private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

    private final AppMockStore store;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "case-execute");
        t.setDaemon(true);
        return t;
    });

    public TestCaseService(AppMockStore store) {
        this.store = store;
    }

    /* ================= 接口6：用例生成 ================= */

    public Map<String, Object> generate(Map<String, Object> crmConfig, Map<String, Object> billingConfig,
                                        String caseType) {
        String type = caseType == null || caseType.isBlank() || "all".equalsIgnoreCase(caseType)
                ? "all" : caseType.toLowerCase();

        List<Map<String, Object>> cases = new ArrayList<>();
        if ("all".equals(type) || "acceptance".equals(type)) {
            cases.add(template(crmConfig, billingConfig, "acceptance", "新受理-正常订购",
                    List.of(step("提交订购请求", "订单创建成功", "订单创建成功"),
                            step("查询订购关系", "订购关系生效", "订购关系生效"),
                            step("查询计费开头", "次日计费开头", "次日计费开头"))));
        }
        if ("all".equals(type) || "change".equals(type)) {
            cases.add(template(crmConfig, billingConfig, "change", "变更-升级叠加",
                    List.of(step("叠加变更请求", "变更订单受理成功", "变更订单受理成功"),
                            step("查询叠加规则", "叠加优惠按新规则生效", "叠加优惠按新规则生效"))));
        }
        if ("all".equals(type) || "cancel".equals(type)) {
            cases.add(template(crmConfig, billingConfig, "cancel", "退订-月底退订",
                    List.of(step("发起退订", "退订订单受理成功", "退订订单受理成功"),
                            step("查询计费截止", "当月计费完整保留", "当月计费完整保留"))));
        }
        if ("all".equals(type) || "billing".equals(type)) {
            cases.add(template(crmConfig, billingConfig, "billing", "计费-月费出账",
                    List.of(step("模拟出账日", "出账金额与资费一致", "出账金额与资费一致"),
                            step("模拟跨月生效", "生效日当天计费边界正确", "生效日当天计费边界正确"))));
        }
        // 边界用例：生效日/跨月/叠加
        cases.add(boundaryCase(crmConfig, billingConfig, "生效日为月末最后一天"));
        cases.add(boundaryCase(crmConfig, billingConfig, "叠加优惠达到上限"));

        List<String> caseIds = new ArrayList<>();
        for (Map<String, Object> tc : cases) {
            store.putCase(tc);
            caseIds.add(MapOps.str(tc.get("case_id")));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put("case_count", caseIds.size());
        body.put("case_ids", caseIds);
        body.put("case_list_json", toJson(cases));
        return body;
    }

    /* ================= 接口7：用例执行 ================= */

    public Map<String, Object> execute(List<String> caseIds, String env, String executeMode) {
        List<String> ids = caseIds == null ? new ArrayList<>() : caseIds;
        List<Map<String, Object>> cases = store.findCases(ids);
        if (cases.isEmpty()) {
            return store.fail(4001, "用例不存在: " + ids);
        }
        String environment = env == null || env.isBlank() ? "sit" : env.toLowerCase();
        if (!Set.of("sit", "uat", "pre").contains(environment)) {
            return store.fail(4002, "非法环境: " + env + "（生产环境禁止执行）");
        }

        boolean async = "async".equalsIgnoreCase(executeMode);
        String taskId = store.nextTaskId();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("task_id", taskId);
        task.put("env", environment);
        task.put("total", cases.size());
        task.put("status", async ? "running" : "finished");
        task.put("created_at", store.now());
        store.putTask(taskId, task);

        if (async) {
            executor.submit(() -> runCases(taskId, cases, environment));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 0);
            body.put("msg", "success");
            body.put("task_id", taskId);
            body.put("status", "running");
            return body;
        }
        return collectResult(runCases(taskId, cases, environment));
    }

    /** 异步任务回查（可选接口：GET /tasks/{task_id}） */
    public Map<String, Object> queryTask(String taskId) {
        Map<String, Object> task = store.findTask(taskId);
        if (task == null) {
            return store.fail(4004, "任务不存在: " + taskId);
        }
        Map<String, Object> body = new LinkedHashMap<>(task);
        body.put("code", 0);
        body.put("msg", "success");
        return body;
    }

    /** 执行用例（Mock：全部通过率 90%，含 10% 概率失败明细定位到步骤） */
    private Map<String, Object> runCases(String taskId, List<Map<String, Object>> cases, String env) {
        int passed = 0;
        List<Map<String, Object>> failDetail = new ArrayList<>();
        for (Map<String, Object> tc : cases) {
            boolean ok = Math.random() > 0.1;
            if (ok) {
                passed++;
            } else {
                List<Map<String, Object>> steps = MapOps.castListOfMaps(tc.get("steps"));
                Map<String, Object> firstStep = steps.isEmpty() ? Map.of() : steps.get(0);
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("case_id", MapOps.str(tc.get("case_id")));
                fail.put("step", MapOps.str(firstStep.get("action")));
                fail.put("expect", MapOps.str(firstStep.get("expect")));
                fail.put("actual", "执行超时/结果不一致（" + env + " 环境模拟）");
                fail.put("reason", "环境数据未就绪，需检查前置配置");
                failDetail.add(fail);
            }
        }
        Map<String, Object> task = store.findTask(taskId);
        if (task != null) {
            task.put("status", "finished");
            task.put("finished_at", store.now());
            task.put("passed", passed);
            task.put("failed", cases.size() - passed);
            task.put("fail_detail", failDetail);
        }
        log.info("[TestCaseService] 任务 {} 完成: passed={} failed={}", taskId, passed, cases.size() - passed);
        return task;
    }

    /** 从任务记录裁剪出契约出参 */
    private Map<String, Object> collectResult(Map<String, Object> task) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "success");
        body.put("task_id", task.get("task_id"));
        body.put("total", task.get("total"));
        body.put("passed", task.get("passed"));
        body.put("failed", task.get("failed"));
        body.put("fail_detail", task.get("fail_detail"));
        return body;
    }

    /* ---------------- 用例模板 ---------------- */

    private Map<String, Object> template(Map<String, Object> crm, Map<String, Object> billing,
                                         String type, String title, List<Map<String, Object>> steps) {
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("case_id", store.nextCaseId());
        tc.put("case_type", type);
        tc.put("case_name", title);
        tc.put("product_id", MapOps.str(billing.get("product_id")));
        tc.put("product_name", MapOps.str(crm.get("product_name")));
        tc.put("steps", steps);
        tc.put("created_at", store.now());
        return tc;
    }

    private Map<String, Object> boundaryCase(Map<String, Object> crm, Map<String, Object> billing, String scene) {
        return template(crm, billing, "billing", "边界场景-" + scene,
                List.of(step("构造边界: " + scene, "计费边界处理正确", "计费边界处理正确")));
    }

    private Map<String, Object> step(String action, String expect, String actual) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("action", action);
        s.put("expect", expect);
        s.put("actual", actual);
        return s;
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
