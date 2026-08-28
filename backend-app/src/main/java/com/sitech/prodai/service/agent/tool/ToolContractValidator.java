package com.sitech.prodai.service.agent.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具自描述契约校验器。
 * <p>
 * 对已注册工具做启动期契约校验：工具名非空、输出字段名非空且唯一、语义角色合法、
 * 业务实体标识/名称语义完整（成对出现），并将发现的问题以日志告警暴露。
 * <p>
 * 对应设计文档「工具执行结果 Schema 契约」的落地（契约校验 + 契约测试）。
 */
public final class ToolContractValidator {

    private ToolContractValidator() {
    }

    /**
     * 校验单个工具的自描述契约。
     *
     * @param tool 待校验工具
     * @return 校验发现的问题列表（空表示通过）
     */
    public static List<String> validate(AgentTool tool) {
        List<String> problems = new ArrayList<>();
        if (tool == null) {
            return List.of("工具为空");
        }
        String name = tool.getName();
        if (name == null || name.isBlank()) {
            problems.add("工具名称不能为空");
        }

        List<ToolOutputField> fields = tool.getOutputFields();
        if (fields == null || fields.isEmpty()) {
            return problems;
        }

        List<String> names = new ArrayList<>();
        boolean hasEntityId = false;
        boolean hasEntityName = false;
        for (ToolOutputField field : fields) {
            if (field.getName() == null || field.getName().isBlank()) {
                problems.add("工具 " + name + " 存在输出字段名为空");
                continue;
            }
            if (names.contains(field.getName())) {
                problems.add("工具 " + name + " 输出字段重复: " + field.getName());
            }
            names.add(field.getName());

            if (field.getRole() == null) {
                problems.add("工具 " + name + " 输出字段缺少角色: " + field.getName());
            } else {
                if (field.getRole() == ToolOutputField.Role.BUSINESS_ENTITY_ID) {
                    hasEntityId = true;
                }
                if (field.getRole() == ToolOutputField.Role.BUSINESS_ENTITY_NAME) {
                    hasEntityName = true;
                }
            }
        }

        if (hasEntityId != hasEntityName) {
            problems.add("工具 " + name + " 的业务实体标识/名称应成对声明（BUSINESS_ENTITY_ID 与 BUSINESS_ENTITY_NAME）");
        }
        return problems;
    }

    /**
     * 汇总校验一批工具，返回所有问题（供启动期统一告警）。
     */
    public static List<String> validateAll(List<AgentTool> tools) {
        List<String> problems = new ArrayList<>();
        if (tools == null) {
            return problems;
        }
        for (AgentTool tool : tools) {
            problems.addAll(validate(tool));
        }
        return problems;
    }
}
