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

// 参数定义（工具显式声明入参规范，供理解层 / 前端生成追问与校验）
class ToolParam {
    String name;                  // 参数名（同 params 的 key）
    String label;                 // 业务展示名（如 "商品/套餐"）
    String description;           // 说明
    boolean required;             // 是否必填
    String type;                  // string | number | boolean | date | list
    String format;                // 格式约束，如 yyyy-MM、URI、regex
    String defaultValue;          // 缺省值（缺省时使用，降低追问频率）
    List<String> enumValues;      // 可选枚举（有界取值时约束）
    String source;                // 取值来源：question 抽取 | context 缓存 | 前序工具输出
}

// 增强版工具接口：暴露参数规范（向后兼容，旧工具可不实现返回空列表）
interface AgentTool {
    String getName();
    String getDescription();
    List<ToolParam> getParams();  // 新增：入参规范声明
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

**各工具的入参规范（getParams 声明示例）**：

| 工具 | 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|------|
| `sparql_query` | `question` | ✅ | string | 自然语言查询语句（NL→SPARQL 入口） |
| | `maxEntities` | ❌ | number | 返回实体数上限，默认 20 |
| `swrl_root_cause` | `offering` | ❌* | string | 分析对象商品/套餐；缺省时从 `question` 语义解析 |
| | `question` | ❌ | string | 原始问题（归因文本） |
| `swrl_risk_audit` | `offeringIds` | ❌ | list | 限定风险筛查范围；缺省全量筛查 |
| `rule_explain` | `ruleId` | ✅ | string | 规则编号，如 `R-A01` |
| `ontology_explain` | `concept` | ✅ | string | 本体概念名 |

> `*`：`offering` 标注为条件必填——当 `question` 中无法解析出明确对象、且 `context.cachedEvidence` 无上轮对象时，才判定缺失并触发澄清追问。

### 3.3 表达层 (Presenter) — 翻译结果

将工具执行结果翻译为自然语言。

```java
// 表达层接口
interface Presenter {
    String present(String question, List<ExecutionResult> results, SessionContext context);
    List<String> suggestFollowUps(String question, List<ExecutionResult> results);
}
```

### 3.4 参数完整性：缺失判定 → 澄清追问（而非直接报错）

**原则**：参数缺失不应该在 `execute` 阶段抛错，而应在**理解层生成计划时**就判断，并转入"澄清分支"（复用追问流程），提升对话自然度。

**流程**：

```
[DefaultUnderstander]
  1. 意图 → 工具映射后，依据各 AgentTool.getParams() 校验 required 参数
  2. 校验对象优先级：params 已填 → context.cachedEvidence 缓存 → 缺省值(defaultValue)
  3. 仍缺失的必填参数 → 生成 CLARIFY 意图
       QueryPlan { intent: "CLARIFY", clarify: ["offering"], params: {...}, tools: [] }
       │
       ▼
[Presenter]  请用户补充（示例）：
   "请问您想分析哪个商品/套餐？例如：5G套餐、家庭融合畅享128"
       │
       ▼
用户补充后（携带 session_id）→ 重新进入理解层，用补充值补全 params
```

**判定规则**：

| 条件 | 行为 |
|------|------|
| 必填参数已提供或可从 `question` 抽取 | 正常执行 |
| 必填参数缺失，但 `context.cachedEvidence` 有上轮对象 | 自动复用上轮对象，不打扰用户 |
| 必填参数缺失且无缓存 | 生成 `CLARIFY` 意图，提示用户补充 |
| 非必填参数缺失 | 使用 `defaultValue` 或工具内部默认，不阻塞 |

**查询计划模型扩展**（新增 `clarify` 字段支持澄清分支）：

```java
class QueryPlan {
    String intent;              // SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | CLARIFY | CHAT | ...
    List<String> tools;
    Map<String, Object> params;
    List<String> clarify;       // 新增：需向用户补充的参数名列表（intent=CLARIFY 时非空）
    String userQuestion;
}
```

### 3.5 工具间数据传递：执行步骤（ExecStep）与依赖编排

当前 `Executor` 仅将同一份 `plan.params` 原样传入所有工具，**无法表达"前一个工具的输出作为后一个工具入参"**（如 `sparql_query` 的 RDF facts → `swrl_root_cause`）。引入**执行步骤（ExecStep）**支撑依赖编排。

```java
// 执行步骤：声明某个工具执行时所需的参数来源
class ExecStep {
    String tool;                       // 工具名
    Map<String, String> paramMappings; // 参数名 → 取值来源（见下）
    Map<String, Object> literalParams; // 直接给定的字面参数（来自理解层抽取）
}

// 取值来源（paramMappings 的值）
//  "direct:<name>"        → 用 plan.params.<name>
//  "result:<X>.<key>"     → 用前序步骤 X 的 ExecutionResult.data.<key>
//  "evidence:<key>"       → 用 context.cachedEvidence.<key>
//  "default:<value>"      → 用默认值

// 查询计划持有编排好的步骤
class QueryPlan {
    String intent;
    List<ExecStep> steps;      // 新增：有序执行步骤（替代原 tools 展开）
    Map<String, Object> params;
    List<String> clarify;
    String userQuestion;
    // 兼容：tools 保留为步骤 tool 名的扁平视图
}
```

**执行编排（DefaultExecutor）**：

```
sparql_query.execute({question})
   │  success → data（含实体/RDF 事实）
   ▼
swrl_root_cause.execute({
   offering: evidence:lastOffering | direct:offering,
   facts:    result:sparql_query.raw_results   // 前序工具的输出注入
})
   │
   ▼
聚合 List<ExecutionResult>
```

**依赖失败语义**：当步骤 X 依赖的前序步骤失败时，默认策略为 **中止后续依赖链**，并将已成功步骤结果交由表达层生成"部分结论 + 失败原因说明"。

### 3.6 执行失败降级策略

单个工具失败不应使整轮回答失败，按优先级降级：

| 级别 | 场景 | 处理 |
|------|------|------|
| 1 | 某工具执行失败 | 该工具返回 `ExecutionResult.fail`，标记 `success=false`；依赖它的下游步骤中止；其他独立工具照常执行 |
| 2 | 部分工具失败，仍有成功结果 | 表达层基于成功结果生成部分结论，并说明哪些工具失败、可能原因 |
| 3 | 全部工具失败 或 查询计划无工具 | 表达层直接 LLM 回答（不调用工具），或返回友好兜底文案 |
| 4 | 理解层 LLM 不可用 | `IntentRecognitionSupport` 关键词兜底（已实现） |
| 5 | 工具未注册 / 工具名非法 | 返回 `未找到工具`，由表达层翻译为友好提示（防 LLM 编造工具名 → 白名单过滤） |

**关键点**：失败信息通过 `ExecutionResult.errorMessage` 与 `success` 标志透传给表达层，由表达层组装为"哪里失败、卡在哪、建议怎么办"，而非直接抛异常给用户。

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

### 4.4 追问决策逻辑（证据复用 / 补充数据 / 澄清补参的判定）

多轮对话的关键在于**如何判定"当前证据是否够用"**。采用"LLM 判定 + 规则兜底"两级策略：

```
用户追问 + session_id
        │
        ▼
[Understander]
  1. 读取 SessionContext（history + cachedEvidence + lastIntent/lastTools/lastParams）
  2. 判定属哪种情况：
     ├─ A. 仅需对上轮已有证据做再解释/下钻
     │      →  直接走表达层（不调用工具，REUSE_EVIDENCE）
     ├─ B. 需补充新数据（时间对比、新增维度、切换对象）
     │      →  生成带工具的执行计划（COMPARE / 新查询）
     ├─ C. 缺失必填参数（依据 getParams() 校验）
     │      →  生成 CLARIFY 澄清，向用户追问
     └─ D. 普通闲聊
            →  LLM 直接回答（CHAT）
```

**判定信号**（供理解层参考）：
- **是否需新数据**：由 LLM 判断追问是否引用了当前 `cachedEvidence`、`lastParams.time` 之外的事实（如"上月"→ 触发新查询）。
- **是否可复用证据**：追问维度（channel）落在 `cachedEvidence` 已有键内 → 复用。
- **是否缺参**：连续 `N` 次进入 `CLARIFY` 仍未补齐 → 限次后降级为"按缺省值继续"或友好提示放弃，防止死循环。

**会话上下文结构（SessionContext）**：

```java
class SessionContext {
    String sessionId;
    // 多轮对话历史（role + content），供 LLM 上下文
    List<Map<String, Object>> history;

    // 证据缓存：toolName → 该工具最近一次成功 data
    // 追问无需新查询时，直接从此取数，避免重复调用成本
    Map<String, Object> cachedEvidence;

    // 最近一轮识别的意图 / 工具 / 参数（供追问补全与上下文衔接）
    String lastIntent;
    List<String> lastTools;
    Map<String, Object> lastParams;

    // 已澄清参数缓存：被用户补齐的必填参数（offering 等），跨轮复用
    Map<String, Object> resolvedParams;

    // 会话级附加元数据（分析对象、对比周期等，前端上下文标签展示）
    Map<String, Object> meta;
}
```

**上下文窗口与遗忘**：`history` 采用滑动窗口截断（如最近 `K` 轮 + 摘要），超长就绪时对早前对话做 LLM 摘要压缩，避免 token 无限膨胀；`SessionManager` 仅保留相关键（evidence、resolvedParams、meta）作为长期记忆，`history` 仅保留近轮。

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

**流式返回（SSE）协议** — 与前端 9.x 渐进式渲染对齐：

```
POST /api/v1/agent/chat/stream    ← 流式入口（推荐前端使用）
Header: Accept: text/event-stream

事件按执行阶段串行推送（每条 message 为 JSON）:

event: thinking        // 理解层产物（思考步骤 / 意图 / 查询计划）
event: tool            // 执行层产物（工具名 / 状态 / 结果摘要 / 证据）
event: text            // 表达层流式正文片段（多次推送，按块增量）
event: text_done       // 正文完成
event: done            // 结尾，附带完整结构化结果（session_id / evidence / conclusion / nextSteps）
```

```
具体事件体示例：

event: thinking
data: {"steps":[{"label":"正在理解您的需求..."},
                {"label":"已确认业务意图：异动归因","meta":{"confidence":0.92}}],
       "intent":"SWRL_INFER","queryPlan":{"tools":["sparql_query","swrl_root_cause"]}}

event: tool
data: {"name":"sparql_query","status":"running"}
event: tool
data: {"name":"sparql_query","status":"done","durationMs":1200,"summary":"15 条记录"}
event: tool
data: {"name":"swrl_root_cause","status":"done","durationMs":3500,
       "summary":"渠道A订购量下降(贡献度40%)是主因","evidence":{...}}

event: text
data: {"chunk":"家庭融合畅享128本月收入下滑30%，"}
event: text
data: {"chunk":"主要原因是渠道A订购量下降，贡献度40%..."}

event: done
data: {"session_id":"session_x","conclusion":"渠道A订购量下降是主因",
       "evidence":[...],"suggested_follow_ups":[...],"elapsed_ms":5200}
```

**设计要点**：
- 前端按 `event` 类型分流渲染（thinking→思考面板、tool→工具卡片、text→正文打字机）。
- 失败时推送 `event: error`（含阶段的工具名与 `errorMessage`），前端据此展示失败卡片而非空白。
- 流式与一次性 `/chat` 共用同一 `AgentOrchestrator` 编排，仅在表达层差异（流式增量 vs 一次性完整）。

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

---

## 十、安全与鉴权

翻译层同时触发 LLM 与知识库/推理引擎，需约束入口风险：

1. **工具权限控制**
   - 将工具按用户角色分级（如 `swrl_risk_audit` / 下架流程类仅运营角色可用），执行层在调度前校验当前用户权限。
   - `DefaultUnderstander` 的工具白名单（`KNOWN_TOOLS`）已防 LLM 编造工具名，进一步收紧为「白名单 × 角色可见」双重要求。

2. **输入校验与注入防护**
   - `params` 中的 `offering`、`ruleId` 等属业务标识，需做长度、字符集校验；`sparql_query` 内部经 `OntologyService` 生成 SPARQL，严禁将用户输入以字符串直拼进 SPARQL/SWRL。
   - 对 LLM 返回的 `tools`/`params` 做 Schema 校验（工具名在 `KNOWN_TOOLS`、参数类型符合 `ToolParam.type`），拒绝非法结构。

3. **敏感操作审计**
   - 记录每次工具调用（用户、时间、工具、参数摘要、结果状态）到审计日志，供追责与异常回溯。

4. **Session 防篡改**
   - `session_id` 应绑定创建者，服务端校验归属，防止跨用户读取他人 `cachedEvidence`/`history`。

---

## 十一、成本与性能控制

翻译链路多次调用 LLM（理解+表达），需控制成本与耗时：

| 手段 | 说明 |
|------|------|
| 缓存复用 | 追问时优先复用 `cachedEvidence`（REUSE_EVIDENCE），避免重复查询/推理 |
| 参数缺省优先 | `ToolParam.defaultValue` 降低澄清往返次数，减少额外轮次成本 |
| 模型分级 | 理解层/表达层可按重要性选用低/高配模型（如查询用小模型、报告生成用大模型） |
| 上下文窗口管理 | `history` 滑动窗口 + 摘要压缩（见 4.4），限制单次 prompt token 上限 |
| 超时与限流 | 每工具调用设超时；按用户/接口做 QPS 限流，防单会话打爆资源 |
| 结果大小约束 | 工具返回 `raw_results` 设条数上限（如 `maxEntities`），避免大结果灌入 prompt |

---

## 十二、可观测性、测试与版本管理

### 12.1 可观测性（监控埋点）

| 层 | 监控指标 | 用途 |
|----|---------|------|
| 理解层 | 意图识别准确率、实体抽取命中率、LLM 降级次数 | 评估理解质量 |
| 执行层 | 各工具成功率、耗时 P95、失败原因分布 | 定位底层服务问题 |
| 表达层 | 报告生成成功率、平均 token、流式首字节时延 | 评估表达成本与体验 |
| 整链路 | 响应耗时、session 会话数、追问复用率 | 用户体验与资源 |

所有指标带 `intent`、`tool`、`sessionId` 维度，接入统一日志/监控大盘（Prometheus/ELK 等）。

### 12.2 测试策略

- **单元测试**：三层（Understander/Executor/Presenter）各自独立测试；`AgentTool` 参数校验、依赖编排、降级分支。
- **契约测试**：`QueryPlan` / `ExecutionResult` / SSE 事件体的 JSON 结构做契约锁定。
- **整链路测试**：对照「八、验证方式」各场景（SPARQL/SWRL/规则解释/追问/澄清/失败降级）做端到端用例。
- **LLM 结果测试**：意图归一化与实体抽取用固定样例集做回归（mock LLM 或 golden 集比对）。

### 12.3 版本与兼容

- `QueryPlan` / `ToolParam` / SSE 事件体新增字段**向前兼容**（新增字段可缺省，旧结构照常解析）。
- 工具参数变更（增删必填项）需同步更新 `AgentTool.getParams()` 与理解层 `intent→工具` 映射，并走版本号管理。
- 新旧意图命名（`SPARQL_QUERY` 旧枚举 ↔ `product_ops_*` 新业务意图）已在 `DefaultUnderstander.parseLlmResult` 做兼容归一，文档保持该映射说明避免后续破坏。

---

## 十三、缺口补齐清单（相对当前实现）

以下为上述设计相较现有代码库的增量项，作为后续落地 checklist：

| # | 缺口 | 设计章节 | 当前实现状态 |
|---|------|---------|-------------|
| 1 | `AgentTool.getParams()` 参数元数据 | 3.2 / 3.4 | 未实现（仅 getName/getDescription/execute） |
| 2 | 必填参数缺失→CLARIFY 澄清追问 | 3.4 | 未实现（缺失时直接走工具/报错） |
| 3 | 工具间数据传递（ExecStep 依赖编排） | 3.5 | 未实现（当前所有工具共享同一 params） |
| 4 | 工具失败依赖链降级/部分结论 | 3.6 | 部分（有 errorMessage 但无依赖链语义） |
| 5 | `SessionContext` 扩展（resolvedParams/meta） | 4.4 | 部分（已有 history/evidence/last*） |
| 6 | SSE 流式传输协议 | 5.2 | 未接入（当前为一次性 POST /chat） |
| 7 | 安全鉴权 / 成本控制 / 监控埋点 | 十/十一/十二 | 未实现 |
| 8 | `FunctionsCallingService` 复用编排 | 5.1 | 待评估接入 |

> 注：本清单仅记录设计层面的增量，是否落地实现由后续迭代按优先级推进。