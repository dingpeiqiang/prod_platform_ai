# LLM Agent 翻译层 — 产商品运营助手场景实现方案

## 一、场景背景

### 1.1 现有场景架构

项目已有完整的场景体系：

```
前端路由: /rd (研发助手)  /ops (运营助手)
  │
  ├─ 前端场景配置: assistantModes.js → sceneShortcuts
  │   ├─ 研发助手: rd.chat, rd.import, rd.query, rd.compliance, rd.compare
  │   └─ 运营助手: market_insight, online_check, ops_monitor, root_cause, risk_audit, ops_rules
  │
  ├─ 后端场景配置: scene_mapping.json → sceneMappings[]
  │   ├─ sceneCode, sceneName, keywords, workflows, prompts
  │   └─ 关联场景提示词文件: prompts/scenes/{sceneCode}_prompt.txt
  │
  ├─ SceneService: 场景识别、场景树构建、提示词生成
  ├─ IntentRecognitionSupport: 意图识别流水线 (meta → whitelist → LLM → keyword fallback → scene default)
  ├─ ToolRegistry + FunctionCallingService: 工具注册与 Function Calling 编排
  ├─ IntentHandlerRegistry + BaseIntentHandler: 按 intentType 分发到 Handler
  └─ 各场景 Handler: ProductOpsReasonHandler, ProductOpsPolicyHandler, ProductOpsQueryHandler 等
```

### 1.2 现有场景提示词模式

以 `offering_config_prompt.txt` 为例，已明确描述翻译层分工：

```
你（大模型）：理解自然语言、追问澄清、把本体推理结果说成人话
本体推理引擎：字段缺省补全、互斥/依赖/零元/必填合规校验 —— 权威结论不可改写
```

但当前只有**配置场景**明确定义了这种分工，**运营场景**（归因、风险、查询）缺少统一的翻译层抽象。

### 1.3 用户需求

**LLM Agent 充当「翻译层」**：

```
用户说自然语言 → [LLM Agent 翻译层] → 查 RDF 知识库 (SPARQL)
                                      → 触发前向链推理 (SWRL/Openllet)
                                      → 收集事实 + 推理结果
                                      → 翻译回人类可读诊断报告
```

**LLM 负责听懂人话，SPARQL 推理负责说对话。**

---

## 二、核心设计：三层翻译架构

### 2.1 架构分层

```
┌──────────────────────────────────────────────────────────────────┐
│  Layer 1: 理解层 (Understander) — 听懂人话                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────┐            │
│  │ 意图识别  │──→│ 实体抽取  │──→│ 查询计划生成      │            │
│  │ (做什么)  │   │ (涉及谁)  │   │ (怎么做 → 工具列表)│            │
│  └──────────┘   └──────────┘   └────────┬─────────┘            │
└──────────────────────────────────────────┼──────────────────────┘
                                           │
┌──────────────────────────────────────────┼──────────────────────┐
│  Layer 2: 执行层 (Executor) — 说对话      │                      │
│                                          ▼                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  工具调度器 (ToolScheduler)                                │  │
│  │  根据查询计划，编排一个或多个工具的执行顺序                    │  │
│  └────────┬──────────┬──────────┬───────────┬───────────────┘  │
│           ▼          ▼          ▼           ▼                  │
│  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ SPARQL 查询│ │ SWRL 归因│ │ SWRL 风险│ │ 规则/本体解释  │   │
│  │ 工具       │ │ 推理工具  │ │ 稽核工具  │ │ 工具          │   │
│  └────────────┘ └──────────┘ └──────────┘ └──────────────┘   │
└──────────────────────────────────────────────────────────────────┘
                                           │
┌──────────────────────────────────────────┼──────────────────────┐
│  Layer 3: 表达层 (Presenter) — 翻译结果    │                      │
│                                          ▼                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  LLM 翻译器 (LLMTranslator)                                │  │
│  │  将工具执行结果翻译为自然语言诊断报告                          │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  会话管理器 (SessionManager)                                │  │
│  │  维护多轮对话上下文，支持追问                                │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 核心接口

```java
// 查询计划 = 翻译层的"中间语言"
class QueryPlan {
    String intent;              // SPARQL_QUERY | SWRL_INFER | RULE_EXPLAIN | GENERAL_CHAT
    List<String> tools;         // 需要调用的工具列表
    Map<String, Object> params; // 工具参数
    String userQuestion;        // 原始问题
}

// 工具接口（所有工具都实现此接口）
interface AgentTool {
    String getName();
    String getDescription();
    ExecutionResult execute(Map<String, Object> params);
}

// 理解层接口
interface Understander {
    QueryPlan understand(String question, SessionContext context);
}

// 执行层接口
interface Executor {
    List<ExecutionResult> execute(QueryPlan plan);
}

// 表达层接口
interface Presenter {
    String present(String question, List<ExecutionResult> results, SessionContext context);
    List<String> suggestFollowUps(String question, List<ExecutionResult> results);
}
```

### 2.3 内置工具

| 工具名 | 职责 | 底层调用 |
|--------|------|---------|
| `sparql_query` | 自然语言 → SPARQL → 查询 RDF 知识库 | `OntologyService.nlDiscoverAndRetrieve()` |
| `swrl_root_cause` | 触发 SWRL 归因推理 | `ProductOntologyService.analyzeRootCause()` |
| `swrl_risk_audit` | 触发 SWRL 风险稽核 | `ProductOntologyService.auditRisks()` |
| `rule_explain` | 解释规则含义 | `OpsRulesService.formatRuleLabel()` |
| `ontology_explain` | 解释本体概念 | `OntologyService.explain()` |

---

## 三、场景配置

### 3.1 前端场景配置 (assistantModes.js)

在运营助手 `ops.sceneShortcuts` 中新增场景：

```javascript
// 运营助手 ops 模式下新增
withWelcome({
  label: '智能诊断',
  scene: 'agent_diagnose',
  desc: 'LLM 驱动的智能诊断翻译层',
  text: '分析5G套餐销量下降原因',
  placeholder: '描述运营分析需求，例如：分析5G套餐销量下降原因',
  welcome: [
    '### 智能诊断',
    '',
    '用自然语言描述运营问题，系统自动查询 RDF 知识库、触发 SWRL 推理，',
    '并将结果翻译为人类可读的诊断报告。',
    '',
    '**我会帮你做什么**',
    '- 理解你的运营问题，自动定位相关商品和指标',
    '- 查询 RDF 知识库获取事实数据',
    '- 触发 SWRL 规则引擎进行归因/风险推理',
    '- 将技术和推理结果翻译为业务人员可读的报告',
    '',
    '**你可以这样问**',
    '1. 异动归因：「5G套餐销量为什么下降了？」',
    '2. 风险稽核：「查一下有哪些高风险商品」',
    '3. 事实查询：「有哪些在售5G套餐？」',
    '4. 规则解释：「R-A01规则是什么？」',
    '5. 追问：「具体哪个渠道？」「和上月比呢？」',
    '',
    '**试试这些话术**',
  ].join('\n'),
  nextSteps: [
    '分析5G套餐销量下降原因',
    '查一下有哪些高风险商品',
    '有哪些在售5G套餐？',
  ],
})
```

### 3.2 后端场景配置 (scene_mapping.json)

在 `sceneMappings` 中新增场景：

```json
{
  "sceneCode": "agent_diagnose",
  "sceneName": "智能诊断",
  "type": "scene",
  "parentId": "ops",
  "isActive": true,
  "priority": 60,
  "keywords": ["诊断", "智能诊断", "翻译", "分析", "为什么", "原因", "风险", "稽核", "查询", "规则"],
  "description": "LLM 驱动的智能诊断翻译层",
  "config": {
    "workflows": [
      {
        "code": "agent_diagnose",
        "name": "智能诊断翻译层",
        "description": "NL → 理解 → 执行(SPARQL/SWRL) → 报告",
        "isDefault": true
      }
    ]
  },
  "promptCode": "agent_diagnose_prompt"
}
```

### 3.3 场景提示词 (prompts/scenes/agent_diagnose_prompt.txt)

```text
# 智能诊断翻译层 - 场景提示词

你是产商品运营智能诊断翻译层助手。

## 核心理念

你（大模型）负责：理解自然语言 → 决定调用哪些工具 → 将工具结果翻译成人类可读报告
SPARQL/SWRL 推理引擎负责：提供准确的事实数据和推理结论

## 翻译流程

1. **理解用户意图**：判断用户想做什么
   - 异动归因 → 需要 SPARQL 查事实 + SWRL 归因推理
   - 风险稽核 → 需要 SWRL 风险稽核
   - 事实查询 → 需要 SPARQL 查询
   - 规则解释 → 需要规则说明
   - 追问 → 基于已有上下文回答

2. **调用工具获取数据**：根据意图调用相应工具
   - 使用 `sparql_query` 查询 RDF 知识库
   - 使用 `swrl_root_cause` 触发归因推理
   - 使用 `swrl_risk_audit` 触发风险稽核
   - 使用 `rule_explain` 解释规则
   - 使用 `ontology_explain` 解释本体概念

3. **翻译结果为报告**：将工具结果组织为结构化报告
   - 概述：一句话总结诊断结论
   - 发现：列出具体发现，引用数据支撑
   - 原因分析：解释根因或风险原因
   - 建议措施：给出可操作的建议

## 输出要求

- 面向业务人员，避免技术术语（SPARQL、SWRL、RDF 等）
- 引用具体数据增强可信度
- 结构清晰：概述 → 发现 → 原因 → 建议
- 如果是多轮对话，结合历史给出递进式回答
- 提供 2-3 个建议追问方向

## 注意事项

- 不得在没有数据支撑的情况下给出确定性结论
- 区分"相关性"和"因果性"，避免过度推断
- 如果有多个可能原因，按影响程度排序
- 工具结果中的数据不可改写，只能翻译和解释
```

---

## 四、新增代码

### 4.1 新建文件

```
service/agent/
  ├── AgentOrchestrator.java       — 翻译层编排入口
  ├── SessionManager.java          — 会话管理器
  ├── Understander.java            — 理解层接口
  ├── Executor.java                — 执行层接口
  ├── Presenter.java               — 表达层接口
  ├── model/
  │   ├── QueryPlan.java           — 查询计划
  │   ├── ExecutionResult.java     — 执行结果
  │   └── SessionContext.java      — 会话上下文
  ├── impl/
  │   ├── DefaultUnderstander.java — 理解层实现（复用 IntentRecognitionSupport）
  │   ├── DefaultExecutor.java     — 执行层实现
  │   └── DefaultPresenter.java    — 表达层实现（LLM 报告生成）
  └── tool/
      ├── AgentTool.java           — 工具接口
      ├── SparqlQueryTool.java     — SPARQL 查询工具
      ├── SwrlRootCauseTool.java   — SWRL 归因工具
      ├── SwrlRiskAuditTool.java   — SWRL 风险稽核工具
      ├── RuleExplainTool.java     — 规则解释工具
      └── OntologyExplainTool.java — 本体解释工具

controller/AgentController.java   — 新增：POST /api/v1/agent/chat
```

### 4.2 修改文件

| 文件 | 修改内容 |
|------|---------|
| `assistantModes.js` | ops 新增 `agent_diagnose` 场景 |
| `scene_mapping.json` | 新增 `agent_diagnose` 场景定义 |
| `prompts/scenes/` | 新增 `agent_diagnose_prompt.txt` |
| `ProductOntologyController.java` | 新增 `POST /ops/agent/chat` 端点 |

### 4.3 不修改文件

- `Rdf4jOntologyStore` — 基础设施
- `OpsSwrlReasoner` — 基础设施
- `LlmService` — 基础设施
- `OntologyService` — 基础设施，工具包装
- `ProductOntologyService` — 基础设施，工具包装
- `IntentRecognitionSupport` — 复用，AgentOrchestrator 包装
- `ToolRegistry` / `FunctionCallingService` — 可复用

---

## 五、翻译流程全链路

### 5.1 新查询

```
用户: "5G套餐销量为什么下降了？"
  │
  ▼ AgentOrchestrator.diagnose()
  │
  ├─ Understander
  │   ├─ IntentRecognitionSupport 关键词匹配 → product_ops_reason / root_cause
  │   ├─ 实体抽取 → {offering: "5G套餐", metric: "销量"}
  │   └─ 查询计划 → {intent: SWRL_INFER, tools: ["sparql_query", "swrl_root_cause"]}
  │
  ├─ Executor
  │   ├─ SparqlQueryTool.execute({question: "5G套餐的销量数据"})
  │   │   → OntologyService.nlDiscoverAndRetrieve() → RDF 事实数据
  │   └─ SwrlRootCauseTool.execute({offering: "5G套餐", facts: {...}})
  │       → ProductOntologyService.analyzeRootCause() → SWRL 归因路径
  │
  ├─ Presenter
  │   ├─ LLM 翻译器: prompt(事实证据 + 推理结果 + 场景提示词)
  │   └─ → "5G套餐销量下降30%，主要原因是渠道A订购量下降，贡献度40%..."
  │
  └─ 返回: { session_id, turn, report, evidence, conclusion, suggestions }
```

### 5.2 追问

```
用户: "具体哪个渠道？" + session_id: "xxx"
  │
  ├─ Understander: 读取 session 上下文 → 已有渠道数据 → 无需新查询
  ├─ Presenter: LLM 基于历史上下文生成递进回答
  └─ → "渠道A影响最大，订购量下降30%，贡献占比40%..."
```

### 5.3 追问需补充数据

```
用户: "和上月对比呢？" + session_id: "xxx"
  │
  ├─ Understander: 需要对比数据 → 查询计划 {tools: ["sparql_query"]}
  ├─ Executor: SparqlQueryTool.execute({question: "上个月5G套餐各渠道销量"})
  ├─ Presenter: LLM 基于历史 + 新数据生成对比分析
  └─ → "对比分析：上月渠道A下降20%，本月加剧到30%..."
```

---

## 六、与现有场景的集成

### 6.1 场景识别集成

`agent_diagnose` 场景的关键词策略：
- 关键词覆盖：诊断、分析、为什么、原因、风险、稽核、查询、规则
- 与现有场景的关键词有重叠（如 `root_cause` 也含"为什么"、"原因"）
- 优先级设计：`agent_diagnose` 优先级 60，低于 `root_cause`(80) 和 `risk_audit`(70)

**场景选择策略**：
- 用户明确说"诊断"、"智能诊断" → 走 `agent_diagnose` 场景
- 用户说"归因"、"异动" → 走 `root_cause` 场景（现有 Handler）
- 用户说"风险"、"稽核" → 走 `risk_audit` 场景（现有 Handler）
- 后续可根据用户反馈逐步调整优先级

### 6.2 API 集成

```
现有端点:
  POST /api/v1/product-ontology/ops/chat     → 配置对话
  POST /api/v1/product-ontology/graph        → 知识图谱

新增端点:
  POST /api/v1/agent/chat                    → AgentOrchestrator.diagnose()
  POST /api/v1/product-ontology/ops/agent/chat → 同（委派给 AgentOrchestrator）
```

---

## 七、实现计划

### 第一阶段：核心框架

1. 新建 `service/agent/model/` — QueryPlan, ExecutionResult, SessionContext
2. 新建 `service/agent/tool/AgentTool.java` — 工具接口
3. 新建 `service/agent/Understander.java`, `Executor.java`, `Presenter.java` — 三层接口
4. 新建 `service/agent/impl/DefaultUnderstander.java` — 理解层实现
5. 新建 `service/agent/impl/DefaultExecutor.java` — 执行层实现
6. 新建 `service/agent/impl/DefaultPresenter.java` — 表达层实现
7. 新建 `service/agent/AgentOrchestrator.java` — 编排入口
8. 新建 `service/agent/SessionManager.java` — 会话管理

### 第二阶段：工具实现

9. 新建 `SparqlQueryTool.java` — 包装 OntologyService.nlDiscoverAndRetrieve()
10. 新建 `SwrlRootCauseTool.java` — 包装 ProductOntologyService.analyzeRootCause()
11. 新建 `SwrlRiskAuditTool.java` — 包装 ProductOntologyService.auditRisks()
12. 新建 `RuleExplainTool.java` — 包装 OpsRulesService.formatRuleLabel()
13. 新建 `OntologyExplainTool.java` — 包装 OntologyService.explain()

### 第三阶段：场景配置与 API

14. 新建 `controller/AgentController.java` — REST API
15. 修改 `ProductOntologyController.java` — 新增端点
16. 修改 `assistantModes.js` — 新增场景
17. 修改 `scene_mapping.json` — 新增场景定义
18. 新建 `prompts/scenes/agent_diagnose_prompt.txt` — 场景提示词

---

## 八、验证方式

| 场景 | 输入 | 预期输出 |
|------|------|----------|
| SPARQL 查询 | "有哪些在售5G套餐？" | 调用 sparql_query 工具，返回商品列表 |
| SWRL 归因 | "5G套餐为什么下降？" | 调用 sparql_query + swrl_root_cause，返回归因报告 |
| SWRL 风险 | "查一下高风险商品" | 调用 swrl_risk_audit，返回风险清单 |
| 规则解释 | "R-A01规则是什么？" | 调用 rule_explain，返回规则描述 |
| 追问 | "具体哪个渠道？" + sessionId | 复用上轮上下文，无需新查询直接回答 |
| 追问需补充数据 | "和上月比呢？" + sessionId | 新查询补充数据 + 对比分析 |
| 通用对话 | "你好" | 直接 LLM 回复，不调用工具 |
| 混合查询 | "5G套餐的风险和最近销量" | 同时调用 swrl_risk_audit + sparql_query |