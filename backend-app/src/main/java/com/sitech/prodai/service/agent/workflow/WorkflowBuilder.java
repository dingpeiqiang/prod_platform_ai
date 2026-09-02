package com.sitech.prodai.service.agent.workflow;

import com.sitech.prodai.service.agent.model.QueryPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流构建器：把一轮对话的实际处理流程显式建模为「节点 + 分支条件 + 数据流」。
 * <p>
 * 真实执行流程（与 AgentOrchestrator 的编排一致）：
 * <pre>
 * ① understand（识别需求）
 *     ├─ 分支[意图=CLARIFY] → clarify（组织追问）→ 等用户补参后回到 ①
 *     ├─ 分支[意图=CONFIRM] → confirm（歧义确认）→ 等用户选定后回到 ①
 *     └─ 分支[意图=其他]   → ② plan
 * ② plan（定下处理方案：依据 ① 的结构化意图选择工具链与分支）
 *     ├─ 分支[多意图] → 每个子计划独立走 ③④⑤（segment 分组）
 *     └─ 分支[单意图] → ③ execute
 * ③ execute（逐工具执行：按 plan.steps 声明的依赖编排，上游输出注入下游入参）
 * ④ summarize（汇总结果：输入=③ 全部工具输出）
 * </pre>
 * <p>
 * 设计原则（对应工作流化改造）：
 * - 每个节点的输入来源显式声明为上游节点的输出（input_from），不再是每步重复拼用户原文；
 * - 分支条件显式建模（intent / intent_count / step 失败），前端可渲染「走到哪条分支、为什么」。
 */
public final class WorkflowBuilder {

    private WorkflowBuilder() {
    }

    /** 节点 id 常量（与 SSE thinking 步骤 id 对齐，前端据此关联）。 */
    public static final String N_UNDERSTAND = "understand";
    public static final String N_PLAN = "plan";
    public static final String N_EXECUTE = "execute";
    public static final String N_SUMMARIZE = "summarize";
    public static final String N_CLARIFY = "clarify";
    public static final String N_CONFIRM = "confirm";

    /**
     * 构建本轮工作流图：依据 QueryPlan 的实际意图，标注本轮真实走过的分支路径（taken 分支）。
     *
     * @param plan       理解层产出的查询计划（null 时只给理解节点）
     * @param takenBranch 本轮实际命中的分支（CLARIFY / CONFIRM / MULTI / EXECUTE），null 视为未知
     * @return 工作流图
     */
    public static WorkflowGraph build(QueryPlan plan, String takenBranch) {
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

        return g;
    }

    /**
     * 判定本轮实际命中的分支（供节点上标注「本轮走到哪条分支、为什么」）。
     *
     * @return CLARIFY / CONFIRM / MULTI / EXECUTE / null（未知）
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

    /**
     * 节点输出摘要视图：{summary: String, branch: String(可选)}。
     * 编排层为各节点回填输出时统一使用，保证前端渲染同构。
     */
    public static Map<String, Object> outputSummary(String summary, String branchTaken) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (summary != null) {
            out.put("summary", summary);
        }
        if (branchTaken != null && !branchTaken.isBlank()) {
            out.put("branch_taken", branchTaken);
        }
        return out;
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
