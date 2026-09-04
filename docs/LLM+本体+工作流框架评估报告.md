# 「LLM + 本体 + 工作流」框架评估报告

> **评估对象**：prod_platform_ai（AI 产商品助手）
> **评估日期**：2026-09-03
> **技术栈**：Java 21 + Spring Boot 3.4 + Spring AI 1.0 + RDF4J 4.3.4 + Openllet + MyBatis Plus + MySQL / Vue3
> **规模**：后端 191 个主代码文件、27 个测试类（约 4800 行）
> **版本**：v1.0

---

## 一、总体结论

**成熟度：中高（架构先行的工程化早期）**

框架核心设计哲学清晰且执行到位：**「LLM 负责听懂与说清，本体负责事实可信、判定可解释，工作流负责确定性执行」**。三要素各司其职、边界清晰，显著优于市场上裸 LLM 编排的方案。

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | ★★★★★ | 确定性守门 + LLM 只进节点，纪律性极强 |
| 可回滚性 | ★★★★☆ | 版本化 + last-known-good，生产可回退 |
| 安全护栏 | ★★★★☆ | 7 层分布式护栏，无单点 |
| 代码健康度 | ★★★☆☆ | 三个上帝类超标，与 AGENTS.md 自设约束冲突 |
| 测试完备性 | ★★★☆☆ | 引擎/本体扎实，编排层盲区 |
| 数据真实性 | ★★☆☆☆ | mock 依赖，未打通生产数据 |

---

## 二、三要素逐项评估

### 2.1 本体体系 —— 双形态设计，工程化程度高

**架构**：两种本体形态并行，各有所长。

- **形态 A：动态表单模板**（`backend-app/src/main/resources/ontologies/`）
  - `offering_config.json`（202 行，v2.2）：表单 Schema 单源，含 `formCode / entities / fields`，字段支持 `fieldType / required / enumConfig / ruleDescription / defaultValue`
  - `templates/` 目录 6+1 份品类模板，采用 **extends 继承式设计**：
    - `commonBasePrc.json` — 基类（公共字段：templateId/prodPrcName/effRuleId/expDate/发布信息等）
    - `personMainPrc.json` / `personAddPrc.json` — 个人主/附加资费
    - `broadBandMainPrc.json` / `broadBandOptSpeedPrc.json` — 宽带主资费/加速包
    - `familyBasePrc.json` / `familyAddPrc.json` — 家庭套餐/附加业务
  - 模板结构：`template_id / extends / sections / fields / derive_rules / compliance_bindings / message_projection`
- **形态 B：OWL 本体 + 规则**（`backend-app/src/main/resources/ontology/`）
  - `product-config.ttl`（1207 行，v2.2）— 产品配置 OWL 本体（类/属性/互斥/依赖公理）
  - `product-ops.ttl` — 运营本体
  - `ops_rules.json`（282 行，OpsRules-v1.2）— 规则单源，阈值外置支持热更
  - `config_message_projection.json`（230 行，v2.2）— 业务四层→报文投影映射
  - RDF4J + Openllet SWRL 推理，**SWRL 失败自动回退 java-rules**，容错设计好

**核心类**（均位于 `backend-app/src/main/java/com/sitech/prodai/service/`）：

| 类 | 行数 | 职责 |
|---|---|---|
| `ProductTemplateRegistry` | 538 | 模板加载、extends 合并、无环校验、按 category 查询、buildFormSchema |
| `TemplateConstraintCompiler` | 230 | 模板→约束元数据编译（刻意不引入 SHACL，留 P3-3 待办） |
| `TemplateDeriveEngine` | 514 | derive_rules 推理引擎（set_default / when→visible/hidden） |
| `TemplateComplianceService` | 273 | 合规裁剪（bindings 裁剪 R-C* + 轻量字段约束 + 等价性报告） |
| `ProductOntologyService` | 3821/4076 | **checkCompliance 主逻辑（R-C01~R-C09）** |
| `OntologyService` | 976 | SPARQL 查询、策略集评估、nlDiscoverAndRetrieve、explain、compareState |
| `OntologyTtlLoader` + `Rdf4jOntologyStore` | — | TTL 导入 RDF4J 内存库 |
| `OpsSwrlReasoner` | — | Openllet 正式 SWRL 推理，失败回退 java-rules |
| `OpsRulesService` | 390 | 规则阈值外置/热更 |
| `LastKnownGoodGuard` | 174 | P1-6 last-known-good 事务式四步守卫：LOAD→VALIDATE→SMOKE→COMMIT |
| `OntologyVersionService` | — | 模板/规则/TTL/投影/ABox 快照**五类资产版本化** |
| `ConfigMessageProjector` | 387 | 业务四层→报文投影 |

**亮点**：
1. **业务规范同构映射**：`docs/产品结构化映射逻辑模型报文规范.xlsx`（13 sheet）→ JSON 模板 → TTL 三者同构，字段枚举（如 calcMode 的 a/1/2/R 码）可溯源至业务规范表
2. **版本化完备**：模板/规则/TTL/投影/ABox 五类资产全部版本化
3. **last-known-good 四步守卫**：事务式更新，图谱坏了可回退
4. 合规规则 R-C01~R-C09（必填/互斥/依赖/白名单）集中于 `checkCompliance`

**问题**：
- ❌ `ProductOntologyService` **3821 行**，上帝类严重（AGENTS.md 要求类方法≤20、函数≤50 行）
- ❌ 刻意未引入 SHACL（留 P3-3 待办），约束编译层较薄
- ❌ 本体**人工维护成本高**（文档自述），xlsx 尾页已记录 2 条与模板不同步的问题——**三处同构存在漂移风险**

### 2.2 工作流体系 —— 引擎扎实，「三条铁律」纪律性强

**架构**：`FlowEngineService`（1014 行）持久化状态机引擎（P2-2），落实「三条铁律」：**全持久化 / LLM 只进节点 / 定义期守门前置**。

**引擎核心**（`service/flow/`，5 类）：

| 类 | 行数 | 职责 |
|---|---|---|
| `FlowEngineService` | 1014+ | 持久化状态机，7 类节点：start/end/tool/llm/condition/human/http；每节点带 timeoutMs/retry/onFailure；human 节点挂起产生 resume_token + form_spec + 必填校验恢复；workflow 节点子流程嵌套防环（MAX_DEPTH=5） |
| `FlowDefinitionValidator` | 321 | **定义期守门**：G1 workflow 引用存在、G2 toolName 已注册、G3 outputParams 契约、G4 human 表单规格、condition 必须有 default 兜底、DAG 无环 |
| `ConditionEvaluator` | 104 | **SpEL 沙箱**（SimpleEvaluationContext 禁类型引用），`${ref}` 变量替换 |
| `EditorDefinitionNormalizer` | 323 | VueFlow 编辑器形态→引擎形态归一化 |
| `FormSchemaPortAdapter` / `FlowEngineGatewayConfig` | 50/75 | G4 表单规格端口、LLM 网关、HTTP 网关注册 |

**状态机**（`docs/固定流程引擎设计文档.md`，292 行）：
- 流程状态：`running → waiting_human / failed / cancelled / completed`
- 节点状态：`pending → running → completed / skipped / failed`
- **每节点一事务 + 乐观锁**；DDL 含上下文 JSON、当前节点、resume_token、status_version、workflow_version

**配置规范**（`docs/工作流配置规范.md`，411 行，v1.1）：
- 7 种节点进引擎 + 5 种暂不进引擎
- `inputParams/outputParams` 数组格式、变量引用 `{{node-id.output.field}}`、命名 snake_case

**双轨路由**（确定性优先）：
- `FlowIntentRouter`（162 行）+ `FlowRouteRegistrar`（96 行）：**关键词匹配路由，无 LLM 参与**；命中→直接调引擎，未命中→走 LLM 理解链路
- `WorkflowService.publishWorkflow`（745 行类）：发布即绿灯——先过 FlowDefinitionValidator，再自动提取 trigger_keywords 注册路由；下线注销

**亮点**：
1. **三条铁律**：把 LLM 关进笼子的架构级约束
2. **定义期守门 G1~G4 + 发布即绿灯**，杜绝运行期才发现配置错误
3. **human 节点挂起/恢复**：resume_token + form_spec，支持人机协同
4. **每节点一事务 + 乐观锁 + 双版本号**，并发安全
5. **SpEL 沙箱**防表达式注入
6. **双轨路由**：关键词能解决的不劳烦 LLM，成本与稳定性双优

**问题**：
- ⚠️ 5 种节点类型暂不进引擎，能力覆盖尚不全
- ⚠️ 测试仅覆盖 `FlowEngineServiceTest`（606 行/15 用例），并发乐观锁冲突、超时+重试叠加等边界场景需补充

### 2.3 LLM 集成体系 —— 守门完善，但提示词外置化未完成

**架构**：Spring AI ChatClient 手动构建 + `ModelRouter` 三策略路由（场景/意图/复杂度，model_routing.json）+ 双模型（deepseek-chat + teamshub-qwen3-30b-a3b）。

**模型调用层**：
- `LlmService`（733 行）：clientCache 按连接缓存；Transient 403 重试；`complete / completePrompt / completeMessages / streamEvents`；`getEffectiveConfig` 模型解析（requested > db-active）；OpenAI 兼容 baseUrl 规范化（防 /v1/v1）
- `ModelRouter`（238 行）：`prodai.model-routing.enabled` 开关
- ⚠️ Ollama 本地部署仅存于旧 Python 版文档（`docs/LLM配置指南.md`），Java 后端**零实现**

**提示词管理**：
- `prompts/scenes/`：场景动作提示词框架 + `_templates/`；`ScenePromptManager` 外部目录优先→classpath 回退
- ❌ `prompts/intent_recognition_prompt.txt` **0 字节占位**，意图提示词实际硬编码在 `DefaultUnderstander.buildSystemPrompt`（849-918 行）——**提示词外置化未完成**

**Function Calling（双工具体系）**：
- 体系 1：经典 `ToolRegistry`（`intent/tools/`）— `ToolDefinition`(record) 转 OpenAI 格式；`ToolConfig` 注册 5 个工具：ontology_query / policy_evaluate / form_validate / explain / compare_state
- 体系 2：`AgentTool` 自描述契约（`service/agent/tool/`）— 工具**自声明适用场景**（getScenes）；`AgentCapabilityRegistry`（79 行）场景→工具白名单**单源**；`ToolContractValidator`（84 行）**启动期契约校验**
- 工具清单：ops 场景 5 个（SparqlQueryTool / SwrlRootCauseTool / SwrlRiskAuditTool / OntologyExplainTool / RuleExplainTool）+ rd 场景 6 个（RdConfigChatTool / RdDraftManageTool / RdComplianceTool / RdDiscoverTool / RdFileParseTool / RdSchemeCompareTool）+ 跨场景 1 个（FlowExecuteTool）

**意图识别**（非责任链/策略模式，handlers 死代码已清理）——实际范式为 **「LLM 单次调用 + 确定性守门」**：
1. `LlmIntentExtractor`（182 行）：NL→JSON 意图；LLM 失败回退正则词典
2. `DefaultUnderstander`（1055 行）：CONFIRM / CHAT / 混合意图拆分；`sanitizeTools` **工具白名单过滤**（LLM 幻觉工具直接剔除）；`flow_execute` 守门（仅允许注册表内工作流）；缺参数→CLARIFY 澄清回合（而非硬猜）
3. `IntentRecognitionSupport`（33 行）：归一化为 5 类：product_ops_query / policy / reason / monitor / compare（+ chat）
4. `DefaultExecutor`（186 行）按意图派发工具；编排总控 `AgentOrchestrator`（1627 行）

**流式输出**：`AgentController.chatStream`（SseEmitter 300s）；`AgentOrchestrator.processStream` 多事件协议：`thinking / tool / workflow / done / error / warning / text / text_done`

**亮点**：
1. 双工具体系设计精巧，契约左移到启动期
2. 意图识别 = LLM 单次调用 + 确定性守门，不信任 LLM 输出
3. SSE 多事件协议完整
4. 12 个 Agent 工具按场景隔离

**问题**：
- ❌ `AgentOrchestrator` **1627 行**、`DefaultUnderstander` **1055 行**——编排层上帝类，圈复杂度失控
- ❌ 提示词硬编码，违背自身「配置驱动」原则
- ❌ 仅 2 个云端模型，**无本地模型兜底**——文档自述「LLM 单点风险」未解决

---

## 三、护栏机制评估（7 层，分布式无单点）

| # | 层级 | 机制 | 位置 | 评价 |
|---|------|------|------|------|
| 1 | 模板白名单 | extractableSlotKeys/allowedKeys 过滤 | DefaultUnderstander | ✅ 防字段幻觉 |
| 2 | 合规裁决 | R-C01~R-C09 | ProductOntologyService | ✅ 本体级硬约束 |
| 3 | 场景可见性 | AgentTool.getScenes 白名单 | AgentCapabilityRegistry | ✅ 场景隔离 |
| 4 | 工具白名单 | sanitizeTools 过滤 LLM 输出 | DefaultUnderstander | ✅ 防工具幻觉 |
| 5 | 契约校验 | 启动期+定义期双守门 | ToolContractValidator / FlowDefinitionValidator | ✅ 左移到编译期 |
| 6 | 图谱回退 | last-known-good 四步事务 | LastKnownGoodGuard | ✅ 可回滚 |
| 7 | 执行沙箱 | SpEL 禁类型引用 | ConditionEvaluator | ✅ 防注入 |

**结论**：护栏体系是该框架最大亮点，覆盖「输入→意图→工具→流程→输出」全链路，且多数护栏是**确定性代码而非 LLM 自觉**，符合「不信任 LLM 输出」的正确姿势。

---

## 四、测试与评测体系

- ✅ **确定性部分扎实**（27 个测试类，约 4800 行）：
  - `FlowEngineServiceTest`（606 行/15 用例）：线性流/失败/校验/变量/条件/human 恢复/llm 节点/重试/超时/onFailure/取消
  - `ProductConfigRegressionTest`（199 行）：双品类回归 + SMOKE + last-known-good 守卫
  - `AgentToolContractTest`（111 行）：工具契约启动校验
  - `LastKnownGoodGuardTest` / `OpsSwrlReasonerTest` / `TemplateComplianceServiceTest`：守卫四步/SWRL 推理/合规裁剪
- ❌ **LLM 输出质量无自动化评测基线**（文档自述）：提示词改动只能靠人工回归，**评测体系缺失是最大工程债**
- ❌ `AgentOrchestrator`（1627 行）缺专项集成测试——恰是最复杂、风险最高的层

---

## 五、关键风险清单（按优先级）

| 优先级 | 风险 | 影响 | 建议 |
|--------|------|------|------|
| **P0** | LLM 评测体系缺失 | 提示词/模型切换无回归保障 | 建立黄金评测集（输入→期望意图+工具+参数），CI 中跑 LLM 断言 |
| **P0** | 上帝类 3 个（3821/1627/1055 行） | 圈复杂度失控、可维护性差、违反 AGENTS.md | 按规则域拆分（如 ProductOntologyService → R-C 规则器/校验器/投影器） |
| **P1** | mock 数据依赖，未打通生产数据 | 本体判定可信但事实源不可信 | 对接生产 ABox，mock 仅留测试环境 |
| **P1** | 提示词硬编码（0 字节占位文件） | 改提示词需发版，违背配置驱动 | 完成 IntentPromptManager 外置化，模板变量化 |
| **P1** | xlsx→JSON→TTL 三处同构漂移 | 已发生 2 条未同步 | 建立单源生成链（xlsx→JSON 自动生成，TTL 由 JSON 派生）或以 JSON 为单源反哺 |
| ~~P2~~ | ~~LLM 单点（2 个云端模型，无本地兜底）~~ | ~~供应商故障即全站不可用~~ | 经评审确认**不实施**（双模型 + fallback 机制已够当前阶段使用，暂不引入 Ollama 本地兜底） |
| **P2** | SHACL 未引入 | 约束编译层薄，约束表达靠硬编码 R-C 规则 | 按既定 P3-3 路线引入 SHACL，替代部分硬编码 |
| **P3** | 编排层集成测试缺失 | 回归风险 | AgentOrchestrator 契约测试 + 事件协议快照测试 |

---

## 六、亮点总结（值得保持）

1. **「LLM 只进节点」铁律** —— LLM 永远不做流程决策，只做单点理解/抽取，输出必过守门
2. **确定性优先的双轨路由** —— 关键词能解决的不劳烦 LLM，成本与稳定性双优
3. **契约左移** —— 工具契约启动期校验、流程定义期守门，错误在发布前拦截
4. **五类资产版本化 + last-known-good** —— AI 系统少见的完整可回滚设计
5. **文档与代码同步度高** —— git 提交粒度与文档同步度高，4 个核心方案文档（v4.5）持续演进

---

## 七、一页结论

> 这是一个**「确定性守门为骨、LLM 智能为肉、本体事实为魂」**的架构先行型框架。三要素协同设计成熟度高，护栏体系是同类项目中的标杆水平；主要短板不在架构而在**工程收尾**——评测体系缺失、提示词外置未完成、上帝类待拆分、生产数据未打通。若 P0/P1 风险在下一迭代收敛，该框架具备支撑真实生产的能力。
