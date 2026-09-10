package com.sitech.prodai.service.agent.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sitech.prodai.domain.entity.Workflow;
import com.sitech.prodai.mapper.WorkflowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已发布流程注册表（单源）—— 理解层注入已发布流程清单供 LLM 选择 flow_execute，
 * 并作为 flow_execute 守门（workflow_code 白名单）的唯一数据源。
 * <p>
 * 数据直接查 DB（is_active=true 的已发布工作流），无内存注册态：发布/下线即时生效，
 * 无需钩子同步（去旧留新：原关键词路由已退役，工作流识别统一走 LLM 理解层）。
 * <p>
 * 边界：本类只做"清单供数 + 守门"，不做意图路由——工作流与普通工具同权，
 * 由 LLM 理解层（需求识别）动态选中后经 DefaultExecutor → FlowExecuteTool 执行。
 */
@Component
public class PublishedFlowRegistry {

    private static final Logger log = LoggerFactory.getLogger(PublishedFlowRegistry.class);

    /** 已发布流程条目：编码 + 显示名（关键词维度退役，话术匹配交给 LLM 语义理解）。 */
    public record PublishedFlow(String workflowCode, String displayName) {
    }

    private final WorkflowMapper workflowMapper;

    public PublishedFlowRegistry(WorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

    /** 已发布流程清单（workflow_code → 条目），按编码排序保证 prompt 注入稳定。 */
    public Map<String, PublishedFlow> listPublished() {
        try {
            List<Workflow> workflows = workflowMapper.selectList(
                    new LambdaQueryWrapper<Workflow>().eq(Workflow::getIsActive, true));
            Map<String, PublishedFlow> out = new LinkedHashMap<>();
            workflows.stream()
                    .sorted(java.util.Comparator.comparing(Workflow::getWorkflowCode))
                    .forEach(w -> out.put(w.getWorkflowCode(),
                            new PublishedFlow(w.getWorkflowCode(), w.getWorkflowName())));
            return out;
        } catch (Exception e) {
            // 清单供数失败按空表降级：LLM 不注入流程段、flow_execute 全拦（fail-safe，不阻断对话）
            log.error("[PublishedFlowRegistry] 读取已发布流程清单失败，按空表降级", e);
            return Map.of();
        }
    }

    /** 守门校验：workflow_code 是否命中已发布流程。 */
    public boolean contains(String workflowCode) {
        if (workflowCode == null || workflowCode.isBlank()) {
            return false;
        }
        try {
            return workflowMapper.selectCount(
                    new LambdaQueryWrapper<Workflow>()
                            .eq(Workflow::getWorkflowCode, workflowCode.trim())
                            .eq(Workflow::getIsActive, true)) > 0;
        } catch (Exception e) {
            log.error("[PublishedFlowRegistry] 守门查询失败，按未注册拦截: {}", workflowCode, e);
            return false;
        }
    }
}
