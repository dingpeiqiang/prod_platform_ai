# LLM 诊断 Agent — 翻译层方案

## 一、现状

现有能力已覆盖大部分需求，但缺少一个"翻译层"把它们串起来：

| 现有组件 | 能做什么 | 缺什么 |
|----------|----------|--------|
| `OntologyService.nlDiscoverAndRetrieve()` | NL→LLM 实体发现→SPARQL→RDF 结果 | 结果原始，不触发推理 |
| `OpsSwrlReasoner.reasonRootCause()` | Openllet SWRL 归因推理 | 需程序化调用，无 NL 入口 |
| `OpsSwrlReasoner.reasonRiskOffering()` | Openllet SWRL 风险稽核 | 同上 |
| `LlmService.completeMessages()` | 多轮 LLM 对话 | 无领域知识注入 |

## 二、目标

新增 `DiagnosticAgentService`，薄翻译层：

```
用户说人话 → [LLM 翻译层] → SPARQL 查知识库 + 触发 SWRL 推理
                         → 收集结果
                         → LLM 翻译回人话
```

一句话：**LLM 负责听懂人话，SPARQL/SWRL 负责说对话**。

## 三、核心设计

### 3.1 单次诊断流程

```
diagnose(question, sessionId?) → 
  1. LLM 从 question 中提取意图 + 实体（offeringId, 商品名等）
  2. 根据意图执行：
     - ROOT_CAUSE → SPARQL 查事实 + OpsSwrlReasoner.reasonRootCause()
     - RISK_AUDIT → SPARQL 查事实 + OpsSwrlReasoner.reasonRiskOffering()
     - FACT_QUERY → SPARQL 查 RDF 知识库
     - RULE_EXPLAIN → 读 ops_rules.json 规则描述
  3. 收集证据（事实 + 推理结果 + 规则）
  4. LLM 生成自然语言诊断报告
  5. 返回 { report, evidence, session_id, suggested_follow_ups }
```

### 3.2 多轮对话

复用 `LlmService.completeMessages()` 已有的多轮能力：
- 前端传入 `session_id` 做会话标识
- 后端将历史 `{question, report}` 对作为 messages 传给 LLM
- 追问时 LLM 自然理解上下文，无需额外追问意图分类

### 3.3 LLM 调用次数

每次诊断最多 2 次 LLM 调用（意图识别 + 报告生成），追问时仅 1 次（直接生成）。

## 四、文件修改

### 4.1 新建文件

**`DiagnosticAgentService.java`**
- 路径: `backend-app/.../service/DiagnosticAgentService.java`
- 核心方法:
  - `diagnose(question, sessionId)` → `DiagnosticResult`
  - 内部: `extractIntent(question)` → `{intent, entities}`
  - 内部: `executeIntent(intent, entities)` → `{facts, inference, rules}`
  - 内部: `generateReport(question, evidence, history)` → `report`
  - 内部: `suggestFollowUps(report)` → `[suggestions]`
- 依赖: `LlmService`, `OntologyService`, `OpsSwrlReasoner`, `OpsRulesService`

**`DiagnosticAgentController.java`**
- 路径: `backend-app/.../controller/DiagnosticAgentController.java`
- 端点:
  - `POST /api/v1/agent/diagnose` — body: `{question, session_id?}`

### 4.2 修改文件

**`ProductOntologyController.java`**
- 新增 `POST /ops/agent/diagnose` → 委派给 DiagnosticAgentService

## 五、Prompt 设计

**意图提取 Prompt**:
```
你是一个产商品运营助手。从用户问题中提取意图和实体。
意图可选: ROOT_CAUSE(归因/异动/为什么下降), RISK_AUDIT(风险/稽核), FACT_QUERY(查商品/查信息), RULE_EXPLAIN(规则说明)
输出JSON: {"intent":"...", "entities":{"offering_id":"...", "offering_name":"...", "time_range":"..."}}
问题: {question}
```

**报告生成 Prompt**:
```
你是一个产商品运营诊断报告生成专家。根据以下证据，生成面向业务人员的自然语言诊断报告。

诊断类型: {intent}
用户问题: {question}

事实证据: {evidence}
推理结果: {inference_results}
触发规则: {applied_rules}

要求：
- 面向业务人员，避免技术术语
- 引用具体数据
- 结构：概述 → 发现 → 原因 → 建议
- 中文
- 如有多轮对话历史，结合历史做递进回答
```

## 六、验证

1. 调用 `POST /api/v1/agent/diagnose`，传入 `"5G套餐销量为什么下降了"`
2. 验证返回包含自然语言报告和证据
3. 传入 `session_id` 做追问，验证多轮对话连贯性