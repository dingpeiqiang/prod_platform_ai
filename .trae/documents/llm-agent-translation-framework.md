# LLM Agent 翻译层 — 技术框架方案

## 一、核心理念

```
自然语言输入 ─→ [LLM Agent 翻译层] ─→ 知识库/推理引擎
                  ↑ 听懂人话            ↓ 说对话
                   └──── 翻译结果 ←─────┘
```

**LLM 负责听懂人话，SPARQL/SWRL 负责说对话。**

翻译层是一个通用框架，不是特定功能。任何自然语言查询 → 翻译层 → 知识库/推理引擎 → 结果 → 翻译层 → 自然语言回答。框架的核心是将"理解"、"执行"、"表达"三个职责解耦，每层只做一件事，可独立扩展。

---

## 二、框架分层架构

```
┌─────────────────────────────────────────────────────────────────────┐
│  Layer 1: API 层 (Controller)                                        │
│  POST /api/v1/agent/chat  ← 统一入口，前端只需传 question              │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────────────┐
│  Layer 2: 翻译层核心            │                                      │
│                                 ▼                                    │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ 2.1 理解层 (Understander)                                      │  │
│  │  ┌───────────┐    ┌───────────┐    ┌──────────────────┐      │  │
│  │  │ 意图识别   │───→│ 实体抽取   │───→│ 查询计划生成     │      │  │
│  │  │ (做什么)   │    │ (涉及谁)   │    │ (怎么做)         │      │  │
│  │  └───────────┘    └───────────┘    └────────┬─────────┘      │  │
│  └──────────────────────────────────────────────┼──────────────────┘  │
│                                                 │                     │
│  ┌──────────────────────────────────────────────┼──────────────────┐  │
│  │ 2.2 执行层 (Executor)                        │                  │  │
│  │                                              ▼                  │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  工具调度器 (ToolScheduler)                               │  │  │
│  │  │  根据查询计划，编排一个或多个工具的执行顺序                   │  │  │
│  │  └────────┬──────────┬──────────┬───────────┬───────────────┘  │  │
│  │           ▼          ▼          ▼           ▼                  │  │
│  │  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │  │
│  │  │ SPARQL     │ │ SWRL 归因│ │ SWRL 风险│ │ 规则/本体解释  │   │  │
│  │  │ 查询工具   │ │ 推理工具  │ │ 稽核工具  │ │ 工具          │   │  │
│  │  └────────────┘ └──────────┘ └──────────┘ └──────────────┘   │  │
│  │                                              ← 可扩展工具接口  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                 │                     │
│  ┌──────────────────────────────────────────────┼──────────────────┐  │
│  │ 2.3 表达层 (Presenter)                       │                  │  │
│  │                                              ▼                  │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  LLM 翻译器 (LLMTranslator)                              │  │  │
│  │  │  将工具执行结果翻译为自然语言                               │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  会话管理器 (SessionManager)                              │  │  │
│  │  │  维护多轮对话上下文，支持追问                              │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────────────────┐
│  Layer 3: 基础设施 (已存在)     │                                          │
│                                 ▼                                        │
│  ┌──────────────────┐  ┌────────────────────┐  ┌──────────────────────┐  │
│  │ Rdf4jOntology    │  │ OpsSwrlReasoner    │  │ LlmService          │  │
│  │ Store            │  │ (Openllet SWRL)    │  │ (LLM 调用)          │  │
│  │ (RDF 知识库)     │  │ (前向链推理)       │  │                     │  │
│  └──────────────────┘  └────────────────────┘  └──────────────────────┘  │
│  ┌──────────────────┐  ┌────────────────────┐  ┌──────────────────────┐  │
│  │ OntologyService  │  │ OpsRulesService    │  │ ProductOntology      │  │
│  │ (NL→SPARQL查询)  │  │ (规则配置读取)     │  │ Service (归因/风险)  │  │
│  └──────────────────┘  └────────────────────┘  └──────────────────────┘  │
│  ┌──────────────────┐  ┌────────────────────┐  ┌──────────────────────┐  │
│  │ IntentRecognition│  │ ToolRegistry +     │  │ IntentHandler        │  │
│  │ Support (意图识别)│  │ FunctionCalling    │  │ Registry (意图分发)  │  │
│  └──────────────────┘  └────────────────────┘  └──────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 三、三层核心接口设计

### 3.1 理解层 (Understander) — 听懂人话

将自然语言翻译为结构化查询计划。查询计划是翻译层的"中间语言"，描述用户想做什么、涉及什么、需要调用哪些工具。

```java
// 查询计划 = 翻译层的"中间语言"
class QueryPlan {
    String intent;              // 用户意图 (SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | ...)
    List<String> tools;         // 需要调用的工具列表
    Map<String, Object> params; // 工具参数
    String userQuestion;        // 原始问题
}

// 理解层接口
interface Understander {
    QueryPlan understand(String question, SessionContext context);
}
```

**理解过程**：
1. **意图识别** → 用户想做什么（查数据？做推理？问规则？）
2. **实体抽取** → 涉及哪些实体（商品名、渠道、时间范围）
3. **查询计划生成** → 需要调用哪些工具，按什么顺序

**复用现有组件**：`IntentRecognitionSupport` 的关键词匹配 + `LlmService` 的 LLM 补充识别。

### 3.2 执行层 (Executor) — 说对话

执行查询计划，调用底层工具。每个工具包装一个现有服务的能力。

```java
// 执行结果
class ExecutionResult {
    boolean success;
    String toolName;              // 哪个工具执行的
    Map<String, Object> data;     // 原始数据
    String errorMessage;
}

// 工具接口（所有工具都实现此接口）
interface AgentTool {
    String getName();             // 工具名称
    String getDescription();      // 工具描述
    ExecutionResult execute(Map<String, Object> params);
}

// 执行层接口
interface Executor {
    List<ExecutionResult> execute(QueryPlan plan);
}
```

**内置工具**：

| 工具名 | 职责 | 底层调用 |
|--------|------|---------|
| `sparql_query` | 自然语言 → SPARQL → 查询 RDF 知识库 | `OntologyService.nlDiscoverAndRetrieve()` |
| `swrl_root_cause` | 触发 SWRL 归因推理 | `ProductOntologyService.analyzeRootCause()` |
| `swrl_risk_audit` | 触发 SWRL 风险稽核 | `ProductOntologyService.auditRisks()` |
| `rule_explain` | 解释规则含义 | `OpsRulesService.formatRuleLabel()` |
| `ontology_explain` | 解释本体概念 | `OntologyService.explain()` |

### 3.3 表达层 (Presenter) — 翻译结果

将工具执行结果翻译为自然语言。

```java
// 表达层接口
interface Presenter {
    String present(String question, List<ExecutionResult> results, SessionContext context);
    List<String> suggestFollowUps(String question, List<ExecutionResult> results);
}
```

---

## 四、翻译流程全链路

### 4.1 新查询流程

```
用户: "5G套餐销量为什么下降了？"
         │
         ▼
[Understander]
  1. 意图识别: ROOT_CAUSE
  2. 实体抽取: {offering: "5G套餐", metric: "销量", time: "近期"}
  3. 查询计划:
     {intent: "SWRL_INFER", tools: ["sparql_query", "swrl_root_cause"],
      params: {offering: "5G套餐", rule_set: "R-A01~A05"}}
         │
         ▼
[Executor]
  1. SparqlQueryTool({question: "5G套餐的销量数据"})
     → OntologyService.nlDiscoverAndRetrieve() → RDF 事实数据
  2. SwrlRootCauseTool({offering: "5G套餐", facts: RDF数据})
     → ProductOntologyService.analyzeRootCause() → SWRL 归因路径
         │
         ▼
[Presenter]
  1. LLM 翻译器 + 场景提示词:
     prompt(事实证据 + 推理结果 + 场景提示词)
     → "5G套餐销量下降30%，主要原因是渠道A订购量下降，贡献度40%..."
  2. SessionManager: 保存到 session_xxx
         │
         ▼
返回: { session_id, report, evidence, conclusion, suggested_follow_ups }
```

### 4.2 追问流程

```
用户: "具体哪个渠道？"  + session_id: "session_xxx"
         │
         ▼
[Understander]
  1. 读取 session_xxx 上下文 → 上轮已有渠道数据
  2. 查询计划: {intent: "REUSE_EVIDENCE", tools: [], params: {dimension: "channel"}}
         │
         ▼
[Presenter]
  1. LLM 翻译器（基于历史上下文）:
     "上轮分析提到渠道A、B、C，用户想具体了解哪个渠道影响最大"
     → "渠道A影响最大，订购量下降30%，贡献占比40%..."
         │
         ▼
返回: { session_id, turn: 2, report: "...", evidence: {...} }
```

### 4.3 追问需补充数据时

```
用户: "和上月对比呢？"  + session_id: "session_xxx"
         │
         ▼
[Understander]
  1. 读取 session_xxx 上下文 → 需要对比上月数据，当前证据不足
  2. 查询计划: {intent: "COMPARE", tools: ["sparql_query"],
                 params: {query: "上个月5G套餐的渠道销量数据"}}
         │
         ▼
[Executor]
  1. SparqlQueryTool({question: "上个月5G套餐各渠道销量"})
     → 返回上月数据
         │
         ▼
[Presenter]
  1. LLM 翻译器（基于历史 + 新数据）:
     "对比分析：上月渠道A下降20%，本月加剧到30%..."
         │
         ▼
返回: { session_id, turn: 3, report: "...", evidence: {...} }
```

---

## 五、与现有系统集成

### 5.1 复用现有组件

| 翻译层组件 | 复用的现有代码 | 关系 |
|-----------|---------------|------|
| Understander | `IntentRecognitionSupport` | 意图识别的关键词降级逻辑 |
| Understander | `LlmService.completeMessages()` | LLM 意图识别 + 实体抽取 |
| Executor 工具 | `OntologyService.nlDiscoverAndRetrieve()` | `sparql_query` 工具包装 |
| Executor 工具 | `ProductOntologyService.analyzeRootCause()` | `swrl_root_cause` 工具包装 |
| Executor 工具 | `ProductOntologyService.auditRisks()` | `swrl_risk_audit` 工具包装 |
| Executor 工具 | `OntologyService.explain()` | `ontology_explain` 工具包装 |
| Executor 工具 | `OpsRulesService.formatRuleLabel()` | `rule_explain` 工具包装 |
| Presenter | `LlmService.completePrompt()` | LLM 报告生成 |
| 工具调度 | `FunctionCallingService` | 可复用工具调用编排 |

### 5.2 API 设计

```
统一入口:
  POST /api/v1/agent/chat    ← 所有自然语言查询的翻译层入口
  Body: { "question": "...", "session_id": "..." }
  Response: { "session_id", "report", "evidence", "conclusion", "suggested_follow_ups" }

现有端点（不变，可选保留）:
  POST /api/v1/product-ontology/ops/chat          → 配置对话（现有）
  POST /api/v1/product-ontology/graph             → 知识图谱（现有）
```

### 5.3 不修改的基础设施

以下组件作为基础设施保持不变，通过 AgentTool 工具包装调用：

- `Rdf4jOntologyStore` — RDF 知识库存储
- `OpsSwrlReasoner` — Openllet SWRL 推理引擎
- `LlmService` — LLM 调用服务
- `OntologyService` — NL→SPARQL 查询服务
- `ProductOntologyService` — 归因分析、风险稽核服务
- `IntentRecognitionSupport` — 意图识别辅助
- `ToolRegistry` / `FunctionCallingService` — 工具注册与编排

---

## 六、可扩展性

### 6.1 新增工具

只需实现 `AgentTool` 接口，Executor 自动发现并注册，无需修改框架核心：

```java
@Component
class MyNewTool implements AgentTool {
    @Override
    public String getName() { return "my_new_tool"; }
    @Override
    public String getDescription() { return "新工具描述"; }
    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        // 实现逻辑
    }
}
```

### 6.2 新增查询类型

只需修改 `Understander` 的意图识别映射，新增 `intent → tools` 的映射规则：

```java
// DefaultUnderstander 中新增映射规则
private QueryPlan mapIntentToPlan(String intent, Map<String, Object> entities) {
    return switch (intent) {
        case "MY_NEW_QUERY" -> new QueryPlan(intent, List.of("my_new_tool"), entities, question);
        // ... 已有映射
    };
}
```

---

## 七、包结构

```
service/agent/                    ← 翻译层核心包
  ├── AgentOrchestrator.java      — 翻译层编排入口
  ├── SessionManager.java         — 会话管理器
  ├── Understander.java           — 理解层接口
  ├── Executor.java               — 执行层接口
  ├── Presenter.java              — 表达层接口
  ├── model/
  │   ├── QueryPlan.java          — 查询计划模型
  │   ├── ExecutionResult.java    — 执行结果模型
  │   └── SessionContext.java     — 会话上下文模型
  ├── impl/
  │   ├── DefaultUnderstander.java— 理解层实现
  │   ├── DefaultExecutor.java    — 执行层实现
  │   └── DefaultPresenter.java   — 表达层实现
  └── tool/
      ├── AgentTool.java          — 工具接口
      ├── SparqlQueryTool.java    — SPARQL 查询工具
      ├── SwrlRootCauseTool.java  — SWRL 归因工具
      ├── SwrlRiskAuditTool.java  — SWRL 风险稽核工具
      ├── RuleExplainTool.java    — 规则解释工具
      └── OntologyExplainTool.java— 本体解释工具

controller/
  ├── AgentController.java        — 新增：POST /api/v1/agent/chat
  └── ProductOntologyController.java — 可选：新增委派端点
```

---

## 八、验证方式

| 场景 | 输入 | 验证点 |
|------|------|--------|
| SPARQL 查询 | "有哪些在售5G套餐？" | 调用 sparql_query 工具，返回商品列表 |
| SWRL 归因 | "5G套餐为什么下降？" | 调用 sparql_query + swrl_root_cause，返回归因报告 |
| SWRL 风险 | "查一下高风险商品" | 调用 swrl_risk_audit，返回风险清单 |
| 规则解释 | "R-A01规则是什么？" | 调用 rule_explain，返回规则描述 |
| 追问 | "具体哪个渠道？" + sessionId | 复用上轮上下文，无需新查询直接回答 |
| 追问需补充数据 | "和上月比呢？" + sessionId | 新查询补充数据 + 对比分析 |
| 通用对话 | "你好" | 直接 LLM 回复，不调用工具 |

---

## 九、前端交互设计 — 三阶架构的可视化映射

### 9.1 核心理念：三阶交互可视化

```
架构层             前端展示层
─────────────────────────────────────────────────
                     ┌──────────────────────┐
Understander         │  思考过程面板          │
（理解层）            │  · 意图识别 → 步骤展示  │
                     │  · 实体抽取 → 标签展示  │
                     │  · 查询计划 → 卡片展示  │
                     └──────────────────────┘
                              │
                     ┌──────────────────────┐
Executor             │  工具执行面板          │
（执行层）            │  · 每个工具→独立卡片    │
                     │  · 运行中/完成/错误状态  │
                     │  · 结果摘要 + 证据展开  │
                     └──────────────────────┘
                              │
                     ┌──────────────────────┐
Presenter            │  自然语言报告          │
（表达层）            │  · LLM 流式输出       │
                     │  · 结构化数据面板并行   │
                     │  · 追问建议            │
                     └──────────────────────┘
```

前端将三阶架构的"理解→执行→表达"过程，映射为用户可感知的**渐进式信息流**，让用户能看到 AI 的思考过程、知道工具执行结果、理解最终结论。

### 9.2 消息结构设计

每条 AI 消息承载三层数据，按阶段串行产出：

```typescript
interface AssistantMessage {
  id: string;
  role: 'assistant';
  
  // Layer 1: 理解层产物
  reasoning: ReasoningStep[];       // 思考过程步骤
  intentType: string;               // 识别到的意图
  queryPlan?: {                     // 查询计划（可选展示）
    intent: string;
    tools: string[];
    params: Record<string, any>;
  };
  
  // Layer 2: 执行层产物
  toolResults: ToolResult[];        // 各个工具的执行结果
  evidence: EvidenceItem[];         // 证据摘要
  
  // Layer 3: 表达层产物
  streamText: string;               // 流式输出正文
  content: string;                  // 完成后的完整正文
  nextSteps: string[];             // 追问建议
  
  // 状态
  loading: boolean;
  done: boolean;
  showReasoning: boolean;
}
```

### 9.3 消息渲染流程（渐进式展示）

```
时间线 →
┌──────────────────────────────────────────────────────────────┐
│  Step 1: 思考过程面板出现（用户知道"AI 正在处理"）            │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 思考过程 3步 含本体环节                        进行中 │    │
│  │  ┌──────┐                                              │    │
│  │  │ ①    │ 正在理解您的需求...                           │    │
│  │  │ ②    │ 已确认业务意图：异动归因  把握度: 92%         │    │
│  │  │ ③    │ 正在分析异动原因...                           │    │
│  │  └──────┘                                              │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  Step 2: 思考完成 → 正文开始流式打出                         │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 家庭融合畅享128本月收入下滑30%，...                    │    │
│  │ 主要原因是...                                         │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  Step 3: 正文打完 → 附属结果面板出现                        │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 🔍 证据摘要                                           │    │
│  │ ┌──────────┐ ┌──────────┐ ┌──────────┐               │    │
│  │ │ 渠道A    │ │ 渠道B    │ │ 渠道C    │               │    │
│  │ │ -30%     │ │ -15%     │ │ -5%      │               │    │
│  │ └──────────┘ └──────────┘ └──────────┘               │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  Step 4: 操作栏 + 追问建议                                   │
│  [👍] [👎] [📋复制] [🔄重试]                                 │
│  [下一步可以：查看渠道A详情] [对比上月数据]                    │
└──────────────────────────────────────────────────────────────┘
```

**关键设计原则**：同一轮对话中，消息气泡内的各元素按**自上而下**顺序渐次出现，避免用户同时看到多个区域的闪烁和跳动。

### 9.4 组件架构与职责

```
AssistantShell.vue（主布局）
├── AssistantNavBar.vue          — 顶部导航栏
├── 左侧 Sidebar                  — 历史对话 + 快捷场景 + 使用提示
├── 中央 Main Area
│   ├── ChatMessageList.vue      — 消息列表容器
│   │   ├── WelcomeCards.vue     — 欢迎页（场景卡片入口）
│   │   └── MessageWrapper       — 每条消息的外层
│   │       ├── AI 消息
│   │       │   ├── 消息头（AI 标识 + 时间）
│   │       │   ├── ThinkingProcessPanel.vue    — 思考过程时间线
│   │       │   │   ├── OntologyReasoningBlock  — 本体内嵌推理
│   │       │   │   └── 步骤详情展开
│   │       │   ├── MessageCard.vue             — 自然语言正文
│   │       │   ├── ToolResultPanel.vue [新增]  — 工具执行结果卡片
│   │       │   ├── IntentPanel.vue             — 意图结果面板（分派器）
│   │       │   │   ├── ProductOpsPanel.vue     — 运营场景面板
│   │       │   │   ├── ValidationResultPanel   — 校验结果面板
│   │       │   │   └── ...（各意图面板）
│   │       │   ├── 消息操作栏（点赞/复制/重试）
│   │       │   └── 追问建议（nextSteps）
│   │       └── 用户消息
│   │           └── 文本 + 附件
│   └── ChatInput.vue            — 输入框（文本 + 文件 + 语音）
└── 右侧 Panel
    ├── 研发助手：配置草稿面板
    └── 运营助手：归因面板 / 风险面板 / 监控面板
```

### 9.5 输入区域交互优化

**ChatInput.vue 增强设计**：

```
┌─────────────────────────────────────────────────────────────┐
│  [上下文标签]  当前分析: 家庭融合畅享128  [×]               │
│  [场景标签]    异动归因                                     │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 描述运营分析需求，例如：分析...                        │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  [📎 上传文件]  [🎤 语音输入]          [⚙ 模型配置]  [发送] │
└─────────────────────────────────────────────────────────────┘
```

**优化点**：
1. **上下文标签**：显示当前会话的活跃上下文（如分析对象），用户可一键清除
2. **场景标签**：根据当前场景动态显示，告诉用户"现在处于什么模式"
3. **多模态输入**：支持文件上传 + 语音输入（与后端 Agent 的多模态能力对齐）
4. **提示词优化**：placeholder 根据场景动态变化，引导用户更精准地描述

### 9.6 思考过程面板优化

**ThinkingProcessPanel.vue 当前已实现**：
- 时间线样式，自上而下逐步骤动画播放
- 支持本体推理环节（OntologyReasoningBlock）
- 步骤类型标签（LLM / 知识推理 / 工具调用）
- metadata 标签（意图、把握度、命中数、结论等）
- 步骤耗时显示
- 可展开的详情（SPARQL、LLM 原始返回）

**新增优化建议**：

1. **查询计划卡片**：在思考面板顶部添加一个轻量卡片，展示当前识别的"查询计划"

```
┌──────────────────────────────────────────────────────┐
│ 思考过程 3步                                 进行中  │
│                                                      │
│ ┌─ 查询计划 ──────────────────────────────────────┐  │
│ │ 意图: 异动归因  工具: [sparql_query] [swrl_root] │  │
│ │ 分析对象: 家庭融合畅享128  时间: 本月            │  │
│ └──────────────────────────────────────────────────┘  │
│                                                      │
│ ① 正在理解您的需求...                                │
│ ② 已确认业务意图：异动归因                           │
│ ③ 正在分析异动原因...                                │
└──────────────────────────────────────────────────────┘
```

2. **"跳过思考"按钮**：对已完成的思考过程，提供一键收起按钮

3. **步骤耗时可视化**：在时间线右侧显示耗时进度条

### 9.7 工具执行结果面板（新增）

新增 `ToolResultPanel.vue`，展示 Executor 层各工具的执行结果：

```
┌──────────────────────────────────────────────────────┐
│ 🔧 工具执行结果                                      │
│                                                      │
│ ┌─ sparql_query ───────────────────────────── 1.2s ─┐│
│ │ ✅ 查询完成                                      ││
│ │ 查询: 家庭融合畅享128本月各渠道收入数据            ││
│ │ 结果: 15 条记录                                   ││
│ │ [📄 查看证据]                                     ││
│ └───────────────────────────────────────────────────┘│
│                                                      │
│ ┌─ swrl_root_cause ──────────────────────── 3.5s ─┐ │
│ │ ✅ 推理完成                                      │ │
│ │ 触发规则: R-A01, R-A02, R-A03                    │ │
│ │ 结论: 渠道A订购量下降(贡献度40%)是主因            │ │
│ │ [📄 查看推理路径]                                 │ │
│ └───────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────┘
```

**设计要点**：
- 每个工具一个独立卡片，展示名称、状态、耗时
- 展开后显示输入参数和输出摘要
- 证据/推理路径可展开查看详情
- 工具执行失败时显示错误信息和建议

### 9.8 自然语言报告优化（MessageCard.vue）

**MessageCard.vue 当前已实现**：
- Markdown 渲染
- 代码块提取 + 复制 + 展开/收起
- 表格提取 + 复制 + 行数统计
- 流式加载动画

**新增优化建议**：

1. **结构化数据卡片**：在 Markdown 正文中嵌入可交互的数据卡片

```vue
<!-- 新增：诊断结论卡片 -->
<template>
  <div class="diagnosis-card">
    <div class="dc-header">
      <span class="dc-icon">📊</span>
      <span class="dc-title">归因结论</span>
    </div>
    <div class="dc-body">
      <div class="dc-metric">
        <span class="dc-label">主因</span>
        <span class="dc-value">渠道A订购量下降</span>
        <span class="dc-contribution">贡献度 40%</span>
      </div>
      <div class="dc-metric">
        <span class="dc-label">次因</span>
        <span class="dc-value">渠道B转化率下降</span>
        <span class="dc-contribution">贡献度 25%</span>
      </div>
    </div>
  </div>
</template>
```

2. **证据标签**：在正文中识别关键数据点，添加可点击的"证据"标签

```
家庭融合畅享128本月收入下滑30% [📄 证据]，主要原因是...
```

3. **对比表格增强**：表格支持行/列高亮、排序、导出

### 9.9 右侧面板联动

右侧面板根据当前场景和消息内容动态展示：

| 场景 | 右侧面板内容 | 触发时机 |
|------|-------------|---------|
| 智聊·对话配置 | 配置草稿表单 | 识别到 formCard |
| 异动归因 | 归因路径图 + 证据链 | 工具执行完成 |
| 风险稽核 | 风险清单 + 筛选 | 工具执行完成 |
| 运营监控 | 告警列表 + 工单 | 点击"打开监控面板" |
| 规则运营 | 规则目录 + 覆盖 | 点击"打开规则运营" |

**优化点**：
- 右侧面板标题跟随消息关联，显示当前分析对象名称
- 面板内容随消息自动更新，无需手动刷新
- 支持多面板堆叠（如归因面板 + 证据面板）

### 9.10 多轮对话上下文管理

**当前实现**：session_id 管理，追问时复用上下文

**前端优化**：

1. **上下文标签栏**：在输入框上方显示当前会话上下文

```
[当前分析: 家庭融合畅享128] [对比周期: 本月 vs 上月] [清除上下文]
```

2. **会话 Timeline**：左侧历史列表中显示每条会话的摘要标签

```
┌──────────────────────────────────────────────┐
│ 历史对话                                      │
│ ┌──────────────────────────────────────────┐  │
│ │ 分析家庭融合畅享128本月收入下滑原因      │  │
│ │ 10:30  ▸ 2轮对话                        │  │
│ └──────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────┐  │
│ │ 筛查0元资费风险商品                      │  │
│ │ 昨天  ▸ 5轮对话                          │  │
│ └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

3. **追问建议增强**：根据当前上下文动态生成追问建议

```typescript
// 上下文感知的追问建议
const ctx = {
  currentIntent: 'ROOT_CAUSE',
  analyzedEntity: '家庭融合畅享128',
  availableDimensions: ['channel', 'region', 'time'],
};

// 生成的追问建议
nextSteps = [
  '具体哪个渠道影响最大？',       // 维度下钻
  '和上月对比呢？',              // 时间对比
  '生成产品优化工单',            // 后续动作
  '查看其他风险商品',            // 切换分析对象
];
```

### 9.11 响应式与可访问性

**当前已实现**：
- 桌面端 3 栏布局
- 移动端适配（断点 768px / 480px）
- 触摸优化（coarse pointer）
- 小高度屏幕优化
- prefers-reduced-motion

**新增优化建议**：

1. **可折叠侧栏**：左侧/右侧面板支持折叠，给中间对话区更多空间

2. **键盘快捷键**：
   - `Ctrl+Enter` 发送
   - `Esc` 取消/停止
   - `↑/↓` 切换历史输入

3. **内容焦点管理**：
   - 新消息自动滚动到底部
   - 追问建议点击后自动聚焦输入框
   - 代码块复制后显示"已复制"提示

### 9.12 消息渲染性能优化

**当前已实现**：
- requestAnimationFrame 调度滚动
- ResizeObserver 监听高度变化
- 流式打字机效果（chunk = 8, delay = 16ms）
- 思考过程 TransitionGroup 动画

**优化建议**：

1. **虚拟滚动**：当消息超过 50 条时启用虚拟滚动，只渲染可见区域

2. **大内容懒渲染**：代码块 > 100 行时默认折叠，点击展开时才渲染

3. **SSE 流式渲染优化**：流式数据按段落分块渲染，避免频繁 DOM 更新

### 9.13 组件新增/修改清单

| 组件 | 操作 | 说明 |
|------|------|------|
| `ToolResultPanel.vue` | **新增** | 展示工具执行结果卡片 |
| `EvidenceCard.vue` | **新增** | 证据摘要卡片 |
| `QueryPlanCard.vue` | **新增** | 查询计划展示卡片 |
| `ContextBar.vue` | **新增** | 输入框上方上下文标签栏 |
| `MessageCard.vue` | 修改 | 新增结构化数据卡片支持 |
| `ChatMessageList.vue` | 修改 | 集成 ToolResultPanel |
| `ChatInput.vue` | 修改 | 添加上下文标签、多模态入口 |
| `AssistantShell.vue` | 修改 | 可折叠侧栏、键盘快捷键 |
| `ThinkingProcessPanel.vue` | 修改 | 查询计划卡片 + 跳过按钮 |