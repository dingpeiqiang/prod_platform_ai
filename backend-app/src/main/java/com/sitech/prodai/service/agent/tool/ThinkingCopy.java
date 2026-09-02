package com.sitech.prodai.service.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 思考过程业务化文案词典。
 * <p>
 * 面向业务人员（而非技术人员）组织思考时间线文案：每个环节回答四个问题 ——
 * <b>这步要干什么（title）、为什么做（goal）、我做了什么（content / io）、拿到什么（output summary）</b>；
 * 并为每步提供「人工怎么做」（manualHint），使整条时间线成为一份可人工执行的 SOP ——
 * 即便 AI 不可用，业务人员也能按步骤目标逐环完成同样的任务。
 * <p>
 * 单一职责：仅做「内部码 → 业务文案」的翻译；编排层（AgentOrchestrator）负责在事件中下发。
 */
public final class ThinkingCopy {

    private ThinkingCopy() {
    }

    /** 步骤业务分类（替代技术徽标「工具调用/大模型处理/本体推理」） */
    public enum Category {
        /** 听懂需求：理解用户要什么 */
        UNDERSTAND("理解需求"),
        /** 查资料：检索数据 / 历史 / 事实 */
        LOOKUP("查资料"),
        /** 做校验：规则 / 合规 / 风险判定 */
        VERIFY("做校验"),
        /** 算一算：归因 / 对比 / 评估推理 */
        REASON("分析推理"),
        /** 生成内容：配置草稿 / 报告 / 话术 */
        GENERATE("生成内容");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 单个工具环节的业务文案：标题 / 目标 / 人工做法 / 业务分类 */
    public record ToolCopy(String title, String goal, String manualHint, Category category) {
    }

    /** 工具内部码 → 业务文案（title=这步干什么；goal=为什么做；manualHint=人工替代做法） */
    private static final Map<String, ToolCopy> TOOL_COPY = buildToolCopy();

    private static Map<String, ToolCopy> buildToolCopy() {
        Map<String, ToolCopy> m = new LinkedHashMap<>();
        // ── 运营工具 ──
        m.put("sparql_query", new ToolCopy(
                "查询经营数据",
                "从业务数据库中取出本次分析需要的事实数据（商品、销量、指标等）",
                "在经营分析报表/数据后台，按商品与时间范围导出对应指标数据",
                Category.LOOKUP));
        m.put("swrl_root_cause", new ToolCopy(
                "分析异动原因",
                "按业务归因规则逐条比对指标变化，找出最可能的原因链路",
                "对照《异动归因规则手册》：先看渠道/促销/竞品/客群四个维度哪个变化最大，再按规则定位主因",
                Category.REASON));
        m.put("swrl_risk_audit", new ToolCopy(
                "排查在架风险",
                "用稽核规则集逐个扫描在架商品，标记风险等级与处置建议",
                "在商品管理后台导出在架清单，按《稽核规则集》逐条核对资费/合约/互斥要求，标记违规项",
                Category.VERIFY));
        m.put("rule_explain", new ToolCopy(
                "解释业务规则",
                "把规则编号翻译成业务人员能读懂的含义说明",
                "查阅《业务规则手册》中对应编号的条目原文",
                Category.LOOKUP));
        m.put("ontology_explain", new ToolCopy(
                "解释业务概念",
                "用业务语言说明概念的含义与关联规则",
                "查阅业务知识库/概念词条说明",
                Category.LOOKUP));
        // ── 研发工具 ──
        m.put("rd_config_chat", new ToolCopy(
                "生成配置草稿",
                "把您的需求描述转化为一份可编辑的产商品配置草稿（名称、资费、客群、渠道等）",
                "在配置管理后台新建商品，按需求逐项填写套餐名称、月费、目标客群、销售渠道等字段",
                Category.GENERATE));
        m.put("rd_file_parse", new ToolCopy(
                "解析方案文档",
                "读取上传的方案文档，把里面的套餐信息整理成一条条配置草稿",
                "人工通读方案文档，将其中每个套餐的名称/月费/要素/客群/渠道抄录到配置后台",
                Category.GENERATE));
        m.put("rd_compliance", new ToolCopy(
                "检查配置合规性",
                "用资费与政策规则检查草稿，避免带病提交后被驳回",
                "在配置后台的「合规校验」页对该草稿重跑规则检查，或对照《合规规则集》人工核对",
                Category.VERIFY));
        m.put("rd_config_discover", new ToolCopy(
                "检索历史配置",
                "查找是否已有类似的历史方案可以直接复用，少走弯路",
                "在配置管理后台按关键词/品类搜索历史商品方案",
                Category.LOOKUP));
        m.put("rd_scheme_compare", new ToolCopy(
                "对比候选方案",
                "对每个候选方案分别做合规检查与收益粗算，给出推荐",
                "将各方案逐一跑合规检查，并用收益测算表对比收入/成本后择优",
                Category.REASON));
        m.put("rd_draft_manage", new ToolCopy(
                "管理配置草稿",
                "按您的指令修改/删除/复制/提交配置草稿，工单状态同步流转",
                "在配置管理后台找到对应工单的草稿，人工执行修改/删除/复制/提交操作",
                Category.GENERATE));
        return m;
    }

    /** 业务意图内部码 → 业务动作名（兜底用，与前端词典保持一致） */
    private static final Map<String, String> ACTION_DISPLAY = Map.ofEntries(
            Map.entry("query", "数据查询"),
            Map.entry("root_cause", "异动归因"),
            Map.entry("risk_audit", "风险稽核"),
            Map.entry("online_check", "在架检查"),
            Map.entry("ops_monitor", "运营监控"),
            Map.entry("compare", "对比分析"),
            Map.entry("generate", "配置生成"),
            Map.entry("parse", "方案解析"),
            Map.entry("compliance", "合规校验"),
            Map.entry("discover", "配置查询"),
            Map.entry("SPARQL_QUERY", "数据查询"),
            Map.entry("SWRL_INFER", "推理分析"),
            Map.entry("product_ops_policy", "风险稽核"),
            Map.entry("product_ops_reason", "异动归因"),
            Map.entry("RD_CONFIG_CHAT", "对话配置"),
            Map.entry("RD_FILE_PARSE", "方案解析"),
            Map.entry("RD_COMPLIANCE", "合规校验"),
            Map.entry("RD_CONFIG_DISCOVER", "配置查询"),
            Map.entry("RD_SCHEME_COMPARE", "方案对比"),
            Map.entry("RD_DRAFT_MANAGE", "草稿管理"),
            Map.entry("CHAT", "通用对话"),
            Map.entry("CLARIFY", "待补充信息"),
            Map.entry("REUSE_EVIDENCE", "证据复用")
    );

    /** 业务意图 → 该意图下「为什么做」的一句话目标 */
    private static final Map<String, String> INTENT_GOAL = Map.of(
            "SPARQL_QUERY", "先拿到准确的数据，再基于数据回答您的问题",
            "SWRL_INFER", "用归因规则找出指标变化的主因，而不是只给数字",
            "RD_CONFIG_CHAT", "把您的想法落成一份可直接编辑的配置草稿",
            "RD_FILE_PARSE", "把文档里的方案批量转成配置草稿，省去手工录入",
            "RD_COMPLIANCE", "提前发现资费/政策风险，避免提交后被驳回返工",
            "RD_CONFIG_DISCOVER", "先看有没有可复用的历史方案，避免重复建设",
            "RD_SCHEME_COMPARE", "用同一把尺子（合规+收益）衡量每个方案，给出推荐",
            "RD_DRAFT_MANAGE", "把草稿操作落到工单闭环：修改/删除/复制/提交一步到位",
            "CLARIFY", "信息不足时先问清楚，避免答非所问"
    );

    /** 意图内部码 → 业务动作名（用于步骤文案）。 */
    public static String actionDisplay(String intentOrAction) {
        if (intentOrAction == null || intentOrAction.isBlank()) {
            return "分析";
        }
        String label = ACTION_DISPLAY.get(intentOrAction);
        return label != null ? label : intentOrAction;
    }

    /** 业务意图 → 一句话目标（缺省给通用话术）。 */
    public static String intentGoal(String intent) {
        String goal = intent != null ? INTENT_GOAL.get(intent) : null;
        return goal != null ? goal : "按业务流程完成您的请求并给出结论";
    }

    /** 工具内部码 → 业务文案；未知工具返回 null（调用方回退工具 label）。 */
    public static ToolCopy toolCopy(String toolName) {
        return toolName != null ? TOOL_COPY.get(toolName) : null;
    }

    /** 步骤输入区需要隐藏的内部噪声键（对业务无意义，不展示）。
     * <p>
     * 注意：text/draft/product_type 不在隐藏列表 —— 它们是 rd 工具的实际入参
     * （配置需求原文/已有草稿/产品品类），透传后「输入」行才有具体数据流；
     * 真正无业务意义的键（会话号/内部码/分页参数）才隐藏。 */
    public static final List<String> HIDDEN_INPUT_KEYS = List.of(
            "question", "intent_type", "action",
            "config", "maxEntities", "limit",
            "file_id", "file_ids",
            // 系统内部键：会话号对业务阅读无意义（rd 场景经 plan.params 透传给工具）
            "session_id"
    );

    /** 输入参数键 → 业务展示名（仅少量键需要；工具 label 优先）。 */
    private static final Map<String, String> INPUT_KEY_LABELS = Map.ofEntries(
            Map.entry("offering", "分析对象"),
            Map.entry("offeringIds", "商品范围"),
            Map.entry("offering_id", "商品编码"),
            Map.entry("file_name", "文档名称"),
            Map.entry("text", "配置需求"),
            Map.entry("draft", "已有草稿"),
            Map.entry("product_type", "产品品类"),
            Map.entry("patches", "候选方案"),
            Map.entry("document_text", "文档内容"),
            Map.entry("metric", "指标"),
            Map.entry("time", "时间范围"),
            Map.entry("ruleId", "规则编号"),
            Map.entry("concept", "概念"),
            Map.entry("dimension", "分析维度")
    );

    /** 输入参数键 → 业务展示名。 */
    public static String inputKeyLabel(String key) {
        if (key == null) {
            return "";
        }
        String label = INPUT_KEY_LABELS.get(key);
        return label != null ? label : key;
    }

    /** 是否应隐藏该输入键（内部码 / 大对象 / 噪声）。 */
    public static boolean hideInputKey(String key) {
        return key != null && HIDDEN_INPUT_KEYS.contains(key);
    }
}
