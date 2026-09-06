package com.sitech.prodai.service.agent.workflow;

import com.sitech.prodai.service.agent.model.QueryPlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作流视图构建器：把一轮对话的实际处理流程建模为「节点 + 分支条件 + 数据流」视图，
 * 随 SSE workflow 事件一次性下发，供前端思考面板渲染链路。
 * <p>
 * 去旧留新（智聊重设计 W3-2）：场景链路已迁至引擎固化工作流（SceneFlowRouter →
 * FlowEngineService），本类仅保留动态编排侧的「本轮视图」产出与分支文案职责，
 * 不再承载任何执行语义。
 */
public final class WorkflowGraphView {

    private WorkflowGraphView() {
    }

    /** 节点 id 常量（与 SSE thinking 步骤 id 对齐，前端据此关联）。 */
    public static final String N_UNDERSTAND = "understand";
    public static final String N_PLAN = "plan";
    public static final String N_EXECUTE = "execute";
    public static final String N_SUMMARIZE = "summarize";
    public static final String N_CLARIFY = "clarify";
    public static final String N_CONFIRM = "confirm";

    /**
     * 构建本轮工作流视图：依据 QueryPlan 的实际意图，标注本轮真实走过的分支路径（taken 分支）。
     *
     * @param plan        理解层产出的查询计划（null 时只给理解节点）
     * @param takenBranch 本轮实际命中的分支（CLARIFY / CONFIRM / MULTI / EXECUTE），null 视为未知
     * @return 可序列化视图（id/title/nodes/edges）
     */
    public static Map<String, Object> build(QueryPlan plan, String takenBranch) {
        WorkflowGraph g = new WorkflowGraph("turn", "本轮处理工作流");
        boolean rd = plan != null && String.valueOf(plan.getParams().get("intent_type"))
                .startsWith("RD_");
        String understandTitle = rd ? "识别配置需求" : "识别分析需求";

        // ① 理解节点：输入=用户原文，输出=结构化意图（供下游全部节点承接）
        g.node(N_UNDERSTAND, understandTitle, "intent",
                "输入用户原始话术，输出结构化意图（动作/客群/资费/渠道等要素）");
        g.edge(N_UNDERSTAND, N_CLARIFY, "意图=CLARIFY：必填要素缺失");
        g.edge(N_UNDERSTAND, N_CONFIRM, "意图=CONFIRM：需求存在多种解读");
        g.edge(N_UNDERSTAND, N_PLAN, "意图明确：可执行");

        // 澄清/确认分支节点（仅 CLARIFY/CONFIRM 轮真实走到）
        g.node(N_CLARIFY, "组织追问", "branch",
                "输入=①的意图要素缺口，输出=补充信息追问；用户补参后回到①");
        g.node(N_CONFIRM, "歧义确认", "branch",
                "输入=①的多解读候选，输出=候选确认卡片；用户选定后回到①");

        // ② 方案节点：输入=①的结构化意图
        g.node(N_PLAN, "定下处理方案", "plan",
                "输入=①的结构化意图，输出=工具执行链与分支安排");
        g.edge(N_PLAN, N_EXECUTE, "单意图：直接执行");
        g.edge(N_PLAN, N_EXECUTE, "多意图：逐子计划独立执行");

        // ③ 执行节点：输入=②的工具链 + ①的要素参数；逐工具依赖编排
        g.node(N_EXECUTE, "执行处理", "tool",
                "输入=②的工具链与①的要素参数，逐工具执行；上游工具输出经 result: 注入下游入参");
        g.edge(N_EXECUTE, N_EXECUTE, "存在工具链：按声明依赖逐工具执行");
        g.edge(N_EXECUTE, N_SUMMARIZE, "全部工具完成（某工具失败则中止其依赖链，其余照常）");

        // ④ 汇总节点：输入=③的全部工具输出
        g.node(N_SUMMARIZE, "汇总结果", "summarize",
                "输入=③的全部工具输出，输出=最终结论与建议");

        return g.toView();
    }

    /**
     * 判定本轮实际命中的分支（供节点上标注「本轮走到哪条分支、为什么」）。
     *
     * @return CLARIFY / CONFIRM / EXECUTE / null（未知）
     */
    public static String takenBranch(QueryPlan plan) {
        if (plan == null) {
            return null;
        }
        if (QueryPlan.INTENT_CLARIFY.equals(plan.getIntent())) {
            return "CLARIFY";
        }
        if (QueryPlan.INTENT_CONFIRM.equals(plan.getIntent())) {
            return "CONFIRM";
        }
        return "EXECUTE";
    }

    /** 分支条件业务文案（edge.when → 人话）。 */
    public static String branchLabel(String taken) {
        if (taken == null) {
            return "";
        }
        return switch (taken) {
            case "CLARIFY" -> "必填要素缺失 → 先追问补充";
            case "CONFIRM" -> "需求存在多种解读 → 先请您确认";
            case "MULTI" -> "包含多个子需求 → 拆分逐个处理";
            case "EXECUTE" -> "意图明确 → 直接执行";
            default -> taken;
        };
    }

    /** 工作流节点标题（rd 场景差异文案收敛在此）。 */
    public static String nodeTitle(String nodeId, boolean rdScene) {
        return switch (nodeId) {
            case N_UNDERSTAND -> rdScene ? "识别配置需求" : "识别分析需求";
            case N_PLAN -> "定下处理方案";
            case N_EXECUTE -> "执行处理";
            case N_SUMMARIZE -> "汇总结果";
            case N_CLARIFY -> "组织追问";
            case N_CONFIRM -> "歧义确认";
            default -> nodeId;
        };
    }
}
