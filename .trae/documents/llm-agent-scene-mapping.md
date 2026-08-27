# 基于三层翻译框架的现有场景重构方案

## 一、背景

基于已定义的 **LLM Agent 三层翻译框架**（Understander → Executor → Presenter），将系统中已有的 **产商品研发助手** 和 **产商品运营助手** 下的所有场景，重新映射到三层架构上。

### 三层框架回顾

```
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Understander  │──→│   Executor    │──→│  Presenter    │
│ (听懂人话)     │   │  (说对话)     │   │  (翻译结果)    │
│               │   │               │   │               │
│ 意图识别       │   │ 工具调度      │   │ LLM 翻译器    │
│ 实体抽取       │   │ 工具执行      │   │ 会话管理      │
│ 查询计划生成   │   │              │   │               │
└───────────────┘   └───────────────┘   └───────────────┘
```

---

## 二、场景全景图

### 2.1 产商品研发助手（/rd）

| 场景 | 场景编码 | 当前 Handler | 业务描述 |
|------|---------|-------------|---------|
| 智聊·对话配置 | `rd.chat` | `FormHandler` + `ConfigureHandler` | NL→表单字段→本体补全→合规校验 |
| 智读·文件配置 | `rd.import` | `FormHandler` + `OpsExtractionService` | 文档→要素抽取→映射草稿→合规 |
| 智查·历史复用 | `rd.query` | `ProductOpsQueryHandler` | 关键词→事实图检索→复制草稿→合规 |
| 智检·合规校验 | `rd.compliance` | `ValidationHandler` | 套餐信息→规则引擎判定→结果 |
| 多方案对比 | `rd.compare` | `ProductOpsCompareHandler` | 方案A/B→合规+收益对比→择优 |

### 2.2 产商品运营助手（/ops）

| 场景 | 场景编码 | 当前 Handler | 业务描述 |
|------|---------|-------------|---------|
| 市场洞察 | `market_insight` | `ProductOpsQueryHandler` | NL→SPARQL→在售商品/增长指标 |
| 立项研判 | `online_check` | `ProductOpsPolicyHandler` | 新品事实→策略集评估→通过/驳回 |
| 运营监控 | `ops_monitor` | `ProductOpsMonitorHandler` | 告警列表+处置工单双页面板 |
| 异动归因 | `root_cause` | `ProductOpsReasonHandler` | 指标异动→SPARQL查事实→SWRL推理→归因路径 |
| 风险稽核 | `risk_audit` | `ProductOpsPolicyHandler` | 在架商品→SWRL风险扫描→风险清单 |
| 规则运营 | `ops_rules` | REST 面板 | 规则目录/阈值覆盖/审计/热重载 |

---

## 三、场景的三层映射全景

### 3.1 异动归因（root_cause）— 运营助手

```
用户: "分析家庭融合畅享128本月收入下滑原因"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `IntentRecognitionSupport` 关键词匹配 → `product_ops_reason` / `root_cause` | **意图识别**: `ROOT_CAUSE` → `IntentRecognitionSupport.tryKeywordFallback()` |
| | `ProductOpsReasonHandler` 提取 target + offeringHint | **实体抽取**: `{offering: "家庭融合畅享128", metric: "收入", time: "本月"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "SWRL_INFER", tools: ["sparql_query", "swrl_root_cause"]}` |
| **Executor** | `ProductOntologyService.analyzeRootCause(offeringHint, target)` | **工具1**: `SparqlQueryTool.execute({question: "家庭融合畅享128本月收入数据"})` → `OntologyService.nlDiscoverAndRetrieve()` |
| | 内部: 加载RDF事实 → `OpsSwrlReasoner.reasonRootCause()` | **工具2**: `SwrlRootCauseTool.execute({offering: "家庭融合畅享128", facts: RDF数据})` → `ProductOntologyService.analyzeRootCause()` |
| | 返回: `{success, anomalies, paths, appliedRules, ...}` | **执行结果**: `[{tool: "sparql_query", data: RDF事实}, {tool: "swrl_root_cause", data: {paths, anomalies, rules}}]` |
| **Presenter** | `ProductOpsReasonHandler.buildAfterEvents()` 手动格式化 Markdown 文本 | **LLM 翻译器**: `prompt(事实证据 + 推理结果 + 场景提示词)` |
| | 输出: 模板化 Markdown 文本（"### 异动根因分析\n**异动结论**...") | **输出**: LLM 润色的自然语言诊断报告（结构化 + 口语化） |
| | 无会话管理，不支持追问 | **会话管理**: `SessionManager.save(session_id, {evidence, turn, ...})` |

**重构价值**：
- 当前：手动模板生成文本，格式固定，缺乏灵活性
- 重构后：LLM 根据证据自动生成报告，支持追问（"具体哪个渠道？"）和递进式回答

### 3.2 风险稽核（risk_audit）— 运营助手

```
用户: "筛查所有在架的0元资费风险商品"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `IntentRecognitionSupport` 关键词匹配 → `product_ops_policy` / `risk_audit` | **意图识别**: `RISK_AUDIT` |
| | `ProductOpsPolicyHandler` 判断 expectationType = "risk_audit" | **实体抽取**: `{risk_type: "zero_fee", scope: "all"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "SWRL_INFER", tools: ["swrl_risk_audit"]}` |
| **Executor** | `ProductOntologyService.auditRisks(null)` | **工具**: `SwrlRiskAuditTool.execute({scope: "all", risk_type: "zero_fee"})` → `ProductOntologyService.auditRisks()` |
| | 内部: 加载在架清单 → `OpsSwrlReasoner.reasonRiskOffering()` | 内部不变 |
| | 返回: `{success, items, highCount, mediumCount, ...}` | **执行结果**: `[{tool: "swrl_risk_audit", data: {items, counts, ...}}]` |
| **Presenter** | `ProductOpsPolicyHandler.buildRiskAuditEvents()` 手动格式化表格 | **LLM 翻译器**: `prompt(风险清单 + 规则命中 + 场景提示词)` |
| | 输出: Markdown 表格（"| 高风险 | 中风险 |...|") | **输出**: LLM 润色的风险报告 + 处置建议 + 追问方向 |
| | 不支持追问 | **会话管理**: 支持追问（"高风险商品有哪些？"） |

### 3.3 市场洞察（market_insight）— 运营助手

```
用户: "查一下在售5G套餐的增长趋势和风险商品"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `IntentRecognitionSupport` 关键词匹配 → `product_ops_query` / `query` | **意图识别**: `SPARQL_QUERY` |
| | `ProductOpsQueryHandler` 处理 | **实体抽取**: `{offering_type: "5G套餐", metrics: ["增长趋势", "风险"]}` |
| | 无显式查询计划 | **查询计划**: `{intent: "SPARQL_QUERY", tools: ["sparql_query"]}` |
| **Executor** | `OntologyService.nlDiscoverAndRetrieve(question, 20)` | **工具**: `SparqlQueryTool.execute({question: "在售5G套餐的增长趋势和风险商品", maxResults: 20})` → `OntologyService.nlDiscoverAndRetrieve()` |
| | 返回: `{success, results, ...}` | **执行结果**: `[{tool: "sparql_query", data: {results, ...}}]` |
| **Presenter** | 结果直接返回（原始格式） | **LLM 翻译器**: `prompt(SPARQL查询结果 + 场景提示词)` |
| | 无 LLM 润色 | **输出**: LLM 润色的市场洞察报告 + 可追问方向 |

### 3.4 立项研判（online_check）— 运营助手

```
用户: "新品5G套餐立项：目标市场个人客户约8万户，对比方案A 39元与方案B 59元能否通过审核"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `IntentRecognitionSupport` 关键词匹配 → `product_ops_policy` / `online_check` | **意图识别**: `POLICY_EVALUATE` |
| | `ProductOpsPolicyHandler` 判断 expectationType = "online_check" | **实体抽取**: `{offering: "5G套餐", market_size: "8万户", plans: ["A:39元", "B:59元"]}` |
| | 无显式查询计划 | **查询计划**: `{intent: "POLICY_EVALUATE", tools: ["sparql_query", "policy_evaluate"]}` |
| **Executor** | `OntologyService.evaluate(facts, policySetId, "online_check", ...)` | **工具1**: `SparqlQueryTool.execute({question: "5G套餐相关数据"})` |
| | 返回: `{decision: {verdict, reason, triggered_rules, ...}}` | **工具2**: `PolicyEvaluateTool.execute({facts, policySetId: "PS_PRODUCT_ONLINE_V1", ...})` |
| **Presenter** | `ProductOpsPolicyHandler.buildPolicyEvents()` 手动格式化 | **LLM 翻译器**: `prompt(评估结果 + 规则命中 + 场景提示词)` |
| | 输出: Markdown 模板文本 | **输出**: LLM 润色的立项评估报告 + 择优推荐 + 追问方向 |

### 3.5 智聊·对话配置（rd.chat）— 研发助手

```
用户: "给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `FormHandler` 处理 `form` 意图 | **意图识别**: `FORM_CONFIG` |
| | `OpsExtractionService.extractSlots()` 抽取槽位 | **实体抽取**: `{scene: "家庭融合", monthlyFee: 158, broadband: "500M", channels: "全渠道"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "FORM_CONFIG", tools: ["slot_extract", "ontology_fill", "compliance_check"]}` |
| **Executor** | 1. 槽位抽取 → 2. 本体补全缺失字段 → 3. 合规校验 | **工具1**: `SlotExtractTool.execute({question: "..."})` → `OpsExtractionService.extractSlots()` |
| | 内部调用: `FormService`, `OntologyService`, `ValidationService` | **工具2**: `OntologyFillTool.execute({slots, ontologyCode})` → 本体补全 |
| | 返回: `{fields, compliancePass, conflicts, ...}` | **工具3**: `ComplianceCheckTool.execute({fields, rules})` → `ValidationService` |
| **Presenter** | `FormHandler` 格式化输出为配置草稿展示 | **LLM 翻译器**: `prompt(槽位 + 补全字段 + 合规结果 + 场景提示词)` |
| | 输出: 配置草稿 + 冲突列表 | **输出**: LLM 润色的配置摘要 + 待确认项 + 建议操作 |

### 3.6 智读·文件配置（rd.import）— 研发助手

```
用户: 粘贴/上传方案文档
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | 检测到文件上传/长文本 → 走 `rd.import` 场景 | **意图识别**: `DOC_IMPORT` |
| | `OpsExtractionService.extractPackages()` 抽取套餐列表 | **实体抽取**: `{doc_type: "text", content: "..."}` |
| | 无显式查询计划 | **查询计划**: `{intent: "DOC_IMPORT", tools: ["doc_parse", "slot_extract", "compliance_check"]}` |
| **Executor** | 1. 文档解析 → 2. 要素抽取 → 3. 映射草稿 → 4. 合规校验 | **工具1**: `DocParseTool.execute({content})` → 文档分段 |
| | 内部调用: `OpsExtractionService`, `FormService`, `ValidationService` | **工具2**: `SlotExtractTool.execute({segments})` → 要素抽取 |
| **Presenter** | 输出映射清单 + 合规结果 | **LLM 翻译器**: `prompt(映射清单 + 合规结果 + 场景提示词)` |
| | | **输出**: LLM 润色的映射摘要 + 待修正项 + 建议操作 |

### 3.7 智查·历史复用（rd.query）— 研发助手

```
用户: "查一下近30天大学生套餐配置"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | 关键词匹配 → `product_ops_query` / `query` | **意图识别**: `HISTORY_QUERY` |
| | 无显式实体抽取 | **实体抽取**: `{offering_type: "大学生套餐", time_range: "近30天"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "HISTORY_QUERY", tools: ["sparql_query", "history_retrieve"]}` |
| **Executor** | `OntologyService.nlDiscoverAndRetrieve()` + 历史检索 | **工具1**: `SparqlQueryTool.execute({question: "近30天大学生套餐配置"})` |
| | | **工具2**: `HistoryRetrieveTool.execute({keywords, timeRange})` → 历史检索 |
| **Presenter** | 结果列表直接返回 | **LLM 翻译器**: `prompt(检索结果 + 场景提示词)` |
| | | **输出**: LLM 润色的历史方案摘要 + 复用建议 + 复制操作 |

### 3.8 智检·合规校验（rd.compliance）— 研发助手

```
用户: "校验校园体验流量包0元是否符合在架规则"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | 关键词匹配 → `validate` 意图 | **意图识别**: `COMPLIANCE_CHECK` |
| | `ValidationHandler` 处理 | **实体抽取**: `{offering: "校园体验流量包", price: "0元", check_type: "online"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "COMPLIANCE_CHECK", tools: ["sparql_query", "compliance_check"]}` |
| **Executor** | `ValidationService.validateForm()` | **工具1**: `SparqlQueryTool.execute({question: "校园体验流量包0元"})` |
| | | **工具2**: `ComplianceCheckTool.execute({facts, rules})` → `ValidationService` |
| **Presenter** | `ValidationHandler` 格式化输出校验结果 | **LLM 翻译器**: `prompt(校验结果 + 规则命中 + 场景提示词)` |
| | | **输出**: LLM 润色的校验报告 + 违规项 + 修正建议 |

### 3.9 多方案对比（rd.compare）— 研发助手

```
用户: "对比方案A 39元与方案B 59元，目标市场约15万户"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | `IntentRecognitionSupport` 关键词匹配 → `product_ops_compare` / `compare` | **意图识别**: `COMPARE` |
| | `ProductOpsCompareHandler` 处理 | **实体抽取**: `{planA: "39元", planB: "59元", market_size: "15万户"}` |
| | 无显式查询计划 | **查询计划**: `{intent: "COMPARE", tools: ["policy_evaluate", "compare_state"]}` |
| **Executor** | `OntologyService.compareState(snapshotId, patches, ...)` | **工具1**: `PolicyEvaluateTool.execute({planA facts})` |
| | | **工具2**: `PolicyEvaluateTool.execute({planB facts})` |
| | | **工具3**: `CompareStateTool.execute({current, proposed, policySetId})` |
| **Presenter** | `ProductOpsCompareHandler` 格式化输出对比结果 | **LLM 翻译器**: `prompt(方案A结果 + 方案B结果 + 对比分析 + 场景提示词)` |
| | | **输出**: LLM 润色的对比报告 + 择优推荐 + 追问方向 |

### 3.10 运营监控（ops_monitor）— 运营助手

```
用户: "打开运营监控告警列表"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | 白名单精确匹配 → `product_ops_monitor` / `ops_monitor` | **意图识别**: 白名单精确匹配，不经过 LLM |
| | 无实体抽取 | **实体抽取**: 无 |
| | 无查询计划 | **查询计划**: `{intent: "PANEL_OPEN", tools: []}` — 直接返回面板数据 |
| **Executor** | `ProductOpsMonitorHandler` 返回告警列表数据 | 不经过工具调度，直接返回面板数据 |
| **Presenter** | 直接返回 SSE 事件流 → 前端渲染面板 | **Presenter**: 不经过 LLM 翻译，直接返回结构化数据给前端渲染 |

**说明**：运营监控是一个以面板为主的场景，NL→LLM→报告 的翻译流程不适用。更适合直接返回结构化数据给前端渲染。

### 3.11 规则运营（ops_rules）— 运营助手

```
用户: "打开规则运营面板"
```

| 层 | 当前实现 | 框架映射 |
|---|---------|---------|
| **Understander** | 关键词匹配 → 走 REST 面板路径 | **意图识别**: REST 面板，不经过 LLM |
| **Executor** | 直接查 `OpsRulesService` → 返回规则目录/阈值/审计 | 不经过工具调度，直接 REST 查询 |
| **Presenter** | 前端渲染面板 | 不经过 LLM 翻译 |

**说明**：规则运营也是面板场景，直接 REST 查询返回数据给前端渲染。

---

## 四、场景分类与框架适配策略

### 4.1 三类场景

| 类型 | 特点 | 适配策略 | 涉及场景 |
|------|------|---------|---------|
| **A类：推理报告型** | 需要 SPARQL 查事实 + SWRL 推理 + LLM 报告 | 完整三层框架 | root_cause, risk_audit, market_insight, online_check |
| **B类：配置工作流型** | 多步骤工作流（槽位抽取→本体补全→合规校验→LLM 展示） | 三层框架 + 工作流工具链 | rd.chat, rd.import, rd.compliance, rd.compare, rd.query |
| **C类：面板展示型** | 以 UI 面板为主，NL 只做触发 | 仅识别意图，返回结构化数据 | ops_monitor, ops_rules |

### 4.2 A类场景的三层映射共性

```
Understander:
  └─ IntentRecognitionSupport 关键词匹配 → 意图 intent
  └─ LLM 实体抽取 → {offering, metrics, time}
  └─ 查询计划 → {intent, tools: ["sparql_query", "swrl_xxx"]}

Executor:
  └─ SparqlQueryTool → OntologyService.nlDiscoverAndRetrieve()
  └─ SwrlRootCauseTool / SwrlRiskAuditTool → ProductOntologyService.xxx()
  └─ RuleExplainTool → OpsRulesService.formatRuleLabel()

Presenter:
  └─ LLM 翻译器: prompt(事实证据 + 推理结果 + 场景提示词)
  └─ → 结构化诊断报告
  └─ SessionManager: 保存上下文，支持追问
```

### 4.3 B类场景的三层映射共性

```
Understander:
  └─ 意图识别 → FORM_CONFIG / DOC_IMPORT / COMPLIANCE_CHECK / COMPARE / HISTORY_QUERY
  └─ 实体抽取 → 槽位/要素
  └─ 查询计划 → {intent, tools: [工作流步骤列表]}

Executor:
  └─ 工作流式工具链: 按顺序调用多个工具
  └─ 工具1: SlotExtractTool / DocParseTool
  └─ 工具2: OntologyFillTool / HistoryRetrieveTool
  └─ 工具3: ComplianceCheckTool / PolicyEvaluateTool
  └─ 工具4: CompareStateTool

Presenter:
  └─ LLM 翻译器: prompt(各步骤结果 + 场景提示词)
  └─ → 配置摘要 / 对比报告 / 校验报告
  └─ SessionManager: 保存上下文，支持追问
```

---

## 五、新增工具设计

基于现有场景，需要新增以下工具（补充框架已有工具）：

| 工具名 | 职责 | 底层调用 | 适用场景 |
|--------|------|---------|---------|
| `policy_evaluate` | 策略集评估（立项/风险） | `OntologyService.evaluate()` | online_check, rd.compare |
| `compliance_check` | 合规校验 | `ValidationService.validateForm()` | rd.compliance, rd.chat |
| `slot_extract` | 槽位抽取 | `OpsExtractionService.extractSlots()` | rd.chat, rd.import |
| `doc_parse` | 文档解析与要素抽取 | `OpsExtractionService.extractPackages()` | rd.import |
| `history_retrieve` | 历史配置检索 | 事实图检索 | rd.query |
| `ontology_fill` | 本体补全缺失字段 | `OntologyService` 补全逻辑 | rd.chat |
| `compare_state` | 假设分析对比 | `OntologyService.compareState()` | rd.compare |

---

## 六、实现计划

### 第一阶段：框架基础设施

1. 新建 `service/agent/` 包结构和接口（Understander, Executor, Presenter, AgentTool）
2. 新建 `model/`（QueryPlan, ExecutionResult, SessionContext）
3. 实现 `DefaultUnderstander`（复用 IntentRecognitionSupport + LLM 补充）
4. 实现 `DefaultExecutor`（工具注册 + 调度）
5. 实现 `DefaultPresenter`（LLM 报告生成）
6. 实现 `AgentOrchestrator`（编排三层流程）
7. 实现 `SessionManager`（会话管理）

### 第二阶段：工具实现

8. 实现 A 类场景工具：SparqlQueryTool, SwrlRootCauseTool, SwrlRiskAuditTool, RuleExplainTool, OntologyExplainTool
9. 实现 B 类场景工具：SlotExtractTool, DocParseTool, OntologyFillTool, ComplianceCheckTool, HistoryRetrieveTool, PolicyEvaluateTool, CompareStateTool

### 第三阶段：场景适配

10. 修改 `assistantModes.js` — 场景配置不变，但底层路由指向 AgentOrchestrator
11. 修改 `scene_mapping.json` — 场景定义不变
12. 新建 `prompts/scenes/` 各场景提示词文件
13. 修改 `ProductOntologyController.java` —  `/ops/agent/chat` 端点委派给 AgentOrchestrator

### 第四阶段：存量 Handler 迁移

14. A 类场景（root_cause, risk_audit, market_insight, online_check）:
    - 保留现有 Handler 实现作为底层调用
    - AgentOrchestrator 通过工具包装调用现有 Handler
    - 逐步将 Presenter 从手动模板迁移到 LLM 生成
15. B 类场景（rd.chat, rd.import, rd.compliance, rd.compare, rd.query）:
    - 保持现有工作流不变
    - 新增工具包装现有步骤
    - Presenter 从手动模板迁移到 LLM 生成
16. C 类场景（ops_monitor, ops_rules）:
    - 保持现状，不走 LLM 翻译层
    - 仅通过意图识别路由到面板

---

## 七、验证方式

| 场景 | 验证点 | 验证方法 |
|------|--------|---------|
| 异动归因 | 三层流程完整，LLM 报告可读 | 调用 `POST /api/v1/agent/chat`，验证返回报告 |
| 风险稽核 | 同上 | 同上 |
| 市场洞察 | SPARQL 查询结果正确，LLM 润色 | 同上 |
| 立项研判 | 策略集评估结果正确，LLM 润色 | 同上 |
| 智聊·对话配置 | 工作流步骤完整，LLM 展示配置摘要 | 同上 |
| 追问 | 会话上下文正确传递 | 传入 sessionId 验证追问 |
| 运营监控 | 面板直接打开 | 不经过 LLM，直接返回结构化数据 |