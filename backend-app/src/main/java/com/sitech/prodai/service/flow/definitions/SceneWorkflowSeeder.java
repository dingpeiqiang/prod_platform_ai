package com.sitech.prodai.service.flow.definitions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.service.WorkflowService;
import com.sitech.prodai.service.flow.FlowDefinitionValidator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景工作流幂等 Seeder（W3-2）：启动时把 {@link SceneWorkflowDefinitions} 定义落库。
 * <p>
 * 幂等语义：复用 {@link WorkflowService#createWorkflow} 的 existsByWorkflowCode 拦截——
 * 已存在的编码直接跳过（不覆盖用户在编辑器中的改动），重启安全。
 * <p>
 * 守门前置：每个定义先过 Spring 装配版 {@link FlowDefinitionValidator}（含 G2/G3 工具契约校验），
 * 校验不过仅记 error 不落库、不阻塞应用启动（缺陷在 CI 测试中暴露，而非拖垮运行时）。
 * <p>
 * 定义常驻、开关控流：落库不影响运行时行为，是否进入新链路由 chat-workflow.enabled 控制。
 */
@Component
public class SceneWorkflowSeeder {

    private static final Logger log = LoggerFactory.getLogger(SceneWorkflowSeeder.class);
    /** 落库操作者标识（区别于真实用户，审计可辨识）。 */
    private static final String SYSTEM_USER = "system";

    private final WorkflowService workflowService;
    private final FlowDefinitionValidator validator;

    public SceneWorkflowSeeder(WorkflowService workflowService, FlowDefinitionValidator validator) {
        this.workflowService = workflowService;
        this.validator = validator;
    }

    /** 启动即播种（幂等）：逐个定义校验 → 落库；失败不阻塞启动。 */
    @PostConstruct
    public void onStartup() {
        seed();
    }

    /** 播种逻辑独立成方法，供测试直接调用（绕开 Spring 生命周期）。 */
    void seed() {
        for (String code : SceneWorkflowDefinitions.allCodes()) {
            try {
                seedOne(code);
            } catch (Exception e) {
                log.error("[SceneWorkflowSeeder] 场景工作流 {} 落库异常（跳过，不阻塞启动）", code, e);
            }
        }
    }

    private void seedOne(String code) {
        Map<String, Object> definition = SceneWorkflowDefinitions.definition(code);
        FlowDefinitionValidator.ValidationResult check = validator.validate(definition);
        if (!check.valid()) {
            log.error("[SceneWorkflowSeeder] 场景工作流 {} 定义校验未通过，拒绝落库: {}", code, check.problems());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowCode", code);
        payload.put("workflowName", definition.get("name"));
        payload.put("description", "智聊场景固定流程定义（W3-2 Seeder 管理）");
        payload.put("category", "rd");
        payload.put("isActive", true);
        payload.put("workflowData", definition);
        var resp = workflowService.createWorkflow(payload, SYSTEM_USER);
        if (resp.isSuccess()) {
            log.info("[SceneWorkflowSeeder] 场景工作流已播种: {}", code);
            return;
        }
        // 已存在 = 幂等播种；但代码定义演进（如新增节点）时需同步升级到最新定义，
        // 否则引擎永远执行旧版流程（历史上曾因此导致新节点不生效）。定义一致则跳过。
        if (definitionEquals(code, definition)) {
            log.info("[SceneWorkflowSeeder] 场景工作流 {} 已存在（定义一致，跳过）: {}", code, resp.getMessage());
            return;
        }
        var updated = workflowService.updateWorkflow(code, Map.of(
                "workflowName", definition.get("name"),
                "workflowData", definition,
                "changeNote", "Seeder 定义升级（代码演进自动同步）"), SYSTEM_USER);
        if (updated.isSuccess()) {
            log.info("[SceneWorkflowSeeder] 场景工作流 {} 定义已升级至最新", code);
        } else {
            log.warn("[SceneWorkflowSeeder] 场景工作流 {} 定义升级失败: {}", code, updated.getMessage());
        }
    }

    /** 判断库内定义与代码定义是否一致（结构语义比较，序列化后对比，忽略键序）。 */
    private boolean definitionEquals(String code, Map<String, Object> definition) {
        try {
            var resp = workflowService.getWorkflow(code);
            if (!resp.isSuccess() || resp.getData() == null
                    || !(resp.getData().get("workflowData") instanceof Map<?, ?> stored)) {
                return false;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode storedTree = mapper.readTree(mapper.writeValueAsString(stored));
            JsonNode codeTree = mapper.readTree(mapper.writeValueAsString(definition));
            return storedTree.equals(codeTree);
        } catch (Exception e) {
            log.warn("[SceneWorkflowSeeder] 定义比对失败（视为不一致并升级）: {}", e.getMessage());
            return false;
        }
    }
}
