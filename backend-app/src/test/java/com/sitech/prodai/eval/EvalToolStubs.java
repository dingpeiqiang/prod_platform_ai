package com.sitech.prodai.eval;

import com.sitech.prodai.service.agent.tool.AgentTool;
import com.sitech.prodai.service.agent.tool.ToolParam;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

/**
 * 评测专用工具桩：按能力注册表的真实工具名单提供轻量 AgentTool 实现，
 * 供理解链路（DefaultUnderstander）做白名单/参数契约校验。
 * <p>
 * 只声明契约（名称/描述/参数/场景），execute 永不执行（评测止步于理解层）。
 */
public final class EvalToolStubs {

    private EvalToolStubs() {
    }

    /** 全量工具桩（ops 5 + rd 6 + 跨场景 flow_execute），与真实 AgentTool 名单对齐。 */
    public static List<AgentTool> all() {
        return List.of(
                stub("sparql_query", "SPARQL 事实查询", Set.of("ops"),
                        List.of(param("offering", "商品/套餐", true))),
                stub("swrl_root_cause", "异动根因推理", Set.of("ops"), List.of()),
                stub("swrl_risk_audit", "风险稽核", Set.of("ops"), List.of()),
                stub("rule_explain", "规则解释", Set.of("ops"),
                        List.of(param("rule_id", "规则编号", false))),
                stub("ontology_explain", "本体解释", Set.of("ops"), List.of()),
                stub("rd_config_chat", "对话生成配置草稿", Set.of("rd"),
                        List.of(param("question", "需求描述", true),
                                param("session_id", "会话ID", false))),
                stub("rd_config_discover", "检索历史配置", Set.of("rd"),
                        List.of(param("question", "检索话术", true))),
                stub("rd_draft_manage", "工单草稿管理", Set.of("rd"),
                        List.of(param("work_order_id", "工单号", true),
                                paramWithEnum("action", "动作", true,
                                        List.of("delete", "copy", "update", "submit")))),
                stub("rd_compliance", "配置合规校验", Set.of("rd"), List.of()),
                stub("rd_file_parse", "文档解析", Set.of("rd"), List.of()),
                stub("rd_scheme_compare", "方案对比", Set.of("rd"), List.of()),
                stub("flow_execute", "执行已发布流程", Set.of("ops", "rd"),
                        List.of(param("workflow_code", "流程编码", true))));
    }

    private static AgentTool stub(String name, String description, Set<String> scenes,
                                  List<ParamLite> params) {
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getName()).thenReturn(name);
        Mockito.when(tool.getDescription()).thenReturn(description);
        Mockito.when(tool.getLabel()).thenReturn(name);
        Mockito.when(tool.getScenes()).thenReturn(scenes);
        List<ToolParam> toolParams = params.stream()
                .<ToolParam>map(p -> {
                    ToolParam.Builder builder = ToolParam.builder(p.name())
                            .label(p.label())
                            .description(p.description())
                            .enumValues(p.enumValues());
                    if (p.required()) {
                        builder.required();
                    }
                    return builder.build();
                })
                .toList();
        Mockito.when(tool.getParams()).thenReturn(toolParams);
        Mockito.when(tool.getOutputFields()).thenReturn(List.of());
        return tool;
    }

    private static ParamLite param(String name, String description, boolean required) {
        return new ParamLite(name, name, description, required, List.of());
    }

    private static ParamLite paramWithEnum(String name, String description, boolean required,
                                           List<String> enumValues) {
        return new ParamLite(name, name, description, required, enumValues);
    }

    private record ParamLite(String name, String label, String description, boolean required,
                             List<String> enumValues) {
    }
}
