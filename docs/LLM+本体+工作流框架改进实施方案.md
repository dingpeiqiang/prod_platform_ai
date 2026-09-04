# 「LLM + 本体 + 工作流」框架改进实施方案

> **配套文档**：[LLM+本体+工作流框架评估报告.md](./LLM+本体+工作流框架评估报告.md)
> **编制日期**：2026-09-03
> **覆盖范围**：评估报告第五章风险项（P0～P3 共 7 项，LLM 容灾项经评审确认不实施）
> **排期组织**：迭代制（3 个迭代，每迭代 2 周）
> **版本**：v1.0

---

## 〇、方案总览

### 0.1 风险项与迭代映射

| 风险项 | 优先级 | 所属迭代 | 责任方向 | 工作量估算 |
|--------|--------|----------|----------|------------|
| R1. LLM 评测体系缺失 | P0 | 迭代一 | 测试/平台 | 5 人日 |
| R2. 上帝类拆分（3 个） | P0 | 迭代一~二 | 后端架构 | 13 人日 |
| R3. 提示词外置化 | P1 | 迭代一 | 后端 | 3 人日 |
| R4. xlsx→JSON→TTL 同构漂移治理 | P1 | 迭代二 | 后端/工具链 | 5 人日 |
| R5. 生产 ABox 数据打通 | P1 | 迭代二~三 | 后端/数据 | 8 人日 |
| R7. SHACL 引入 | P2 | 迭代三 | 后端架构 | 5 人日 |
| R8. 编排层集成测试补齐 | P3 | 迭代三 | 测试 | 4 人日 |

### 0.2 迭代目标

| 迭代 | 主题 | 出口标准 |
|------|------|----------|
| 迭代一（周 1~2） | **质量基线**：评测体系立起来 + 提示词可配置 + 开始拆类 | 黄金评测集 CI 化运行；提示词热更；ProductOntologyService 拆出 2 个模块 |
| 迭代二（周 3~4） | **数据与单源**：同构单源化 + 生产数据接入 + 拆类收尾 | 单源生成链落地；生产 ABox 只读打通；三个上帝类全部达标 |
| 迭代三（周 5~6） | **韧性收尾**：SHACL + 编排层测试 | SHACL 试点规则 ≥3 条；编排层测试覆盖关键链路 |

---

## 一、R1（P0）：LLM 评测体系建立

### 1.1 目标

为提示词改动、模型切换、路由策略变更提供**自动化回归基线**，纳入 CI 门禁。

### 1.2 现状依据

- `docs/LLM+本体融合推理-尝试与结论汇报.md` 自述「评测体系缺失」
- 现有 27 个测试类全部针对确定性代码（意图守门、流程引擎、本体合规），LLM 输出质量零基线
- `LlmIntentExtractor`（182 行）LLM 失败有 fallbackExtract，但 fallback 正确率无度量

### 1.3 实施步骤

**Step 1：建立黄金评测集（Golden Set）**

- 位置：`backend-app/src/test/resources/eval/golden/`
- 格式（JSONL，每行一个用例）：

```json
{
  "case_id": "rd_001",
  "scene": "rd",
  "input": "帮我配一个个人主资费，月费39元，包含10GB流量",
  "expect": {
    "intent_type": "product_config",
    "tools": ["rd_config_chat"],
    "params_contain": {"prodPrcName": "39元套餐"},
    "forbid_tools": ["flow_execute"]
  },
  "scoring": "exact_intent + tool_match + param_fuzzy"
}
```

- 用例规模建议：**首批 ≥60 条**，覆盖 5 类归一化意图（product_ops_query / policy / reason / monitor / compare）+ chat + CLARIFY 轮次 + 混合意图拆分，每类 ≥8 条；另设 10 条对抗用例（幻觉工具名、越权 flow_execute、超长输入）

**Step 2：实现评测执行器**

- 新增 `backend-app/src/test/java/com/sitech/prodai/eval/LlmEvalRunner.java`
- 核心逻辑：读取 golden JSONL → 逐条调 `LlmIntentExtractor` + `DefaultUnderstander.understandAll`（**mock 掉 LlmService 网络调用可做离线录制回放**，见 Step 3）→ 按期望断言 → 输出报告
- 评分维度：
  - 意图类型准确率（目标 ≥95%）
  - 工具选择准确率（目标 ≥98%，白名单过滤后）
  - 参数抽取 F1（字段名精确匹配 + 值模糊匹配，目标 ≥90%）
  - CLARIFY 触发正确率（缺参数场景目标 100%）

**Step 3：录制-回放机制（关键降本设计）**

- LLM 真实调用成本高且结果非确定，采用**录制回放**：
  - `LlmEvalRecordService`：将每次 LLM 请求（messages+model+temperature）与响应存为 `eval/recordings/<case_id>/<hash>.json`
  - 评测运行时优先回放录制响应；无录制或显式指定 `--live` 时才真实调用
- 好处：提示词改动 → 先跑录制回放（秒级、零成本）看守门结果差异；模型切换/大改提示词 → 周期性 `--live` 重录

**Step 4：CI 门禁接入**

- 在 `.github/workflows` 增加 `llm-eval` job：`mvn test -Dtest=LlmEvalRunner`（默认回放模式）
- 阈值门禁：任一评分维度低于目标值即失败；失败时输出 diff 报告（哪些用例从对变错）

### 1.4 涉及改动点

| 文件/位置 | 改动类型 |
|-----------|----------|
| `backend-app/src/test/resources/eval/golden/*.jsonl` | 新增 |
| `com/sitech/prodai/eval/LlmEvalRunner.java` | 新增 |
| `com/sitech/prodai/eval/LlmEvalRecordService.java` | 新增 |
| `LlmService` | 增加可插拔响应拦截点（仅 test profile 生效） |
| `.github/workflows/*.yml` | 新增 eval job |

### 1.5 验收标准

- [ ] 黄金评测集 ≥60 条，5 类意图全覆盖
- [ ] CI 中回放模式评测全绿，耗时 <2 分钟
- [ ] 意图/工具/参数三维评分达标（95%/98%/90%）
- [ ] 提示词改动 PR 必须附评测报告截图

---

## 二、R2（P0）：三个上帝类拆分

### 2.1 目标

拆分后每类 ≤600 行、公共方法 ≤20、单方法 ≤50 行，符合 AGENTS.md 约束，且**行为零变更**（纯结构调整，靠回归测试兜底）。

### 2.2 现状依据（代码实测）

| 类 | 行数 | 实测结构 |
|----|------|----------|
| `ProductOntologyService` | 3821 行 / 208KB | **53 个 public 方法** + 64 个私有方法；职责混杂：图谱加载（loadGraph/reloadGraph）、合规裁决（R-C01~C09）、草稿 CRUD（save/list/get/delete/submitConfigDraft）、文档批量导入（batchFromDocument*）、工单管理（createWorkOrder/listWorkOrders/updateWorkOrderStatus）、风险稽核（runBatchRiskAudit/auditRisks/analyzeRootCause）、规则热更（updateRiskRules）、对话配置（chatConfigure）、运营看板（getOpsDashboard） |
| `AgentOrchestrator` | 1627 行 | 12 个 public 方法 + 40 个私有方法；职责混杂：多事件流协议（processStream/processStreamMulti）、thinking 快照构建（buildReasoningSnapshot/buildThinkingStep 等 10+ 个快照视图方法）、会话持久化（persistTurn 4 个重载）、工具事件视图（buildToolEvent/toolInputView/buildToolOutput） |
| `DefaultUnderstander` | 1055 行 | 2 个 public（understand/understandAll）+ 30 个私有方法；buildSystemPrompt（857 行起）约 100 行硬编码提示词、sanitizeTools（741 行起）、validateParams/CLARIFY 回合管理（232 行起） |

### 2.3 拆分方案

#### 2.3.1 ProductOntologyService（3821 → 5 个协作类）

按**业务能力垂直切分**，原类保留为 Facade（薄门面，只做委托），对外 API 与行为不变：

```
ProductOntologyService (Facade, ≤300行)
├── OntologyGraphManager        图谱加载/重载/摘要/元数据（loadGraph/reloadGraph/getGraphSummary/getOntologyMeta + LastKnownGoodGuard 编排）
├── ComplianceRuleEngine        R-C01~C09 合规裁决（checkCompliance 两重载 + 规则器内部按 R-C 编号拆私有类）
├── ConfigDraftService          草稿全生命周期（copyAsDraft/save/list/get/delete/submit/publishConfigDraft/getConfigTrace/explainConfig）
├── ConfigDocImportService      文档批量导入（batchFromDocumentBytes/uploadConfigDocument/batchFromUploadedFile/batchFromDocument）
├── OpsWorkOrderService         工单 + 风险稽核 + 看板（createWorkOrder/listWorkOrders/updateWorkOrderStatus/runBatchRiskAudit/auditRisks/analyzeRootCause/getOpsDashboard/getOpsRevenueOverview/updateRiskRules/resetRiskRules）
└── ChatConfigureService        对话配置（chatConfigure/parseSlotsFromText/resolveOfferingId/compareConfigSchemes/evaluateHypothetical）
```

拆分顺序（**每步一个提交，跑通回归再下一步**）：
1. 先拆 `OpsWorkOrderService`（与主链路耦合最低，风险最小）
2. 再拆 `ConfigDraftService`、`ConfigDocImportService`
3. 然后拆 `ChatConfigureService`、`OntologyGraphManager`
4. 最后精拆 `ComplianceRuleEngine`（核心资产，放最后，配 ProductConfigRegressionTest 全绿后合并）
5. `@Transactional`（实测 5 处）随方法归属迁移；`synchronized` 方法归属新类并保留并发语义

#### 2.3.2 AgentOrchestrator（1627 → 3 个协作类）

```
AgentOrchestrator (流程编排核心, ≤700行)
├── StreamEventPublisher    SSE 多事件协议封装（thinking/tool/workflow/done/error/warning/text/text_done 的事件构建与发射）
└── TraceSnapshotBuilder    thinking 快照/工具视图/traceView 等 10+ 个纯视图构建方法（全部为 static 可迁移，零风险）
```

要点：`TraceSnapshotBuilder` 中的方法多为 `static` 纯函数（实测 buildWorkflow/traceView/planStepCount 等均为 static），机械迁移即可；`persistTurn` 4 个重载迁入 `SessionManager`（会话持久化本就是其职责）。

#### 2.3.3 DefaultUnderstander（1055 → 3 个协作类）

```
DefaultUnderstander (understand/understandAll 主流程, ≤450行)
├── IntentPromptAssembler   buildSystemPrompt 提示词组装（与 R3 提示词外置化合并实施，见第三章）
└── ParamCompletionGate     validateParams + CLARIFY 回合管理（incrementClarifyRounds/exceedClarifyLimit/clarifyPlan 构建）
```

`sanitizeTools` 保留在主类（与守门语义强绑定，仅 20 行）。

### 2.4 兜底保障

- **拆分全程以现有测试为安全网**：`ProductConfigRegressionTest`、`ZhiduBatchRegressionTest`、`AgentToolContractTest`、`FlowEngineServiceTest` 必须每步全绿
- R1 评测体系若在迭代一先行落地，拆分期间每步跑一次评测回放，确保 LLM 链路行为不变
- Facade 类上添加 `@Deprecated` 说明注释指向新类（供新代码直接引用新类，旧调用方逐步迁移）

### 2.5 验收标准

- [ ] 三个类行数均 ≤600（Facade 可放宽至 ≤700 且无业务逻辑，仅委托）
- [ ] `mvn test` 全绿；评测回放全绿
- [ ] 对外 REST API 请求/响应报文 diff 为零（可用现有接口 smoke）
- [ ] 新增类均有 JUnit 冒烟测试（可从原测试类平移）

---

## 三、R3（P1）：提示词外置化

### 3.1 目标

意图识别提示词从 `DefaultUnderstander.buildSystemPrompt`（857 行起，约 100 行硬编码）迁出为外部资源，支持不发版修改 + 按场景变量化。

### 3.2 现状依据

- `prompts/intent_recognition_prompt.txt` 为 **0 字节占位文件**
- 场景动作提示词已有外置机制：`ScenePromptManager`（外部目录 `app.prompt-scenes-dir` 优先 → classpath 回退）——**复用该机制，不另起炉灶**

### 3.3 实施步骤

**Step 1：提示词模板化抽离**

- 将 `buildSystemPrompt(rdScene)` 内容迁入 `resources/prompts/intent/intent_system_prompt.txt`
- 场景差异部分用模板变量：`{{rd_scene_block}}`、`{{ops_scene_block}}`，拆分为：
  - `prompts/intent/intent_base_prompt.txt`（公共骨架：CONFIRM 判定规则、输出格式契约）
  - `prompts/intent/intent_rd_block.txt`（rd 场景：工单实时状态注入、查已有 vs 造新分流、工单操作铁律）
  - `prompts/intent/intent_ops_block.txt`（ops 场景块）
- 变量注入点（工单实时状态等运行时数据）保留在 `IntentPromptAssembler` 中以 `${...}` 占位符替换

**Step 2：IntentPromptAssembler（R2 中已拆出的类）实现加载逻辑**

- 复用 `ScenePromptManager` 的「外部目录优先 → classpath 回退」策略，目录约定为 `app.prompt-intent-dir`（默认 `./prompts/intent/`）
- 缓存 + 文件修改时间检测（mtime 变化自动重载），实现**热更**

**Step 3：删除 0 字节占位文件**，更新 `docs/工作流配置规范.md` 同级新增《提示词配置说明》小节

**Step 4：接入 R1 评测**：提示词热更后跑一次评测回放，确认守门结果无回归

### 3.4 涉及改动点

| 文件/位置 | 改动类型 |
|-----------|----------|
| `resources/prompts/intent/*.txt` | 新增（3 个模板文件） |
| `resources/prompts/intent_recognition_prompt.txt` | 删除 |
| `IntentPromptAssembler`（新类） | 新增 |
| `DefaultUnderstander` | 删除 buildSystemPrompt 硬编码，改为调用 Assembler |
| `application.yml` | 新增 `app.prompt-intent-dir` 配置 |

### 3.5 验收标准

- [ ] `DefaultUnderstander` 中不再有 >10 行的提示词字面量
- [ ] 修改外部目录提示词文件，不重启生效（mtime 检测）
- [ ] rd/ops 两场景提示词渲染结果与改造前 diff 为零（用录制回放对比 LLM 输入）

---

## 四、R4（P1）：xlsx→JSON→TTL 同构漂移治理

### 4.1 目标

建立**单一事实源（SSOT）**与自动生成链，消除三处同构的手工维护漂移。

### 4.2 现状依据

- `docs/产品结构化映射逻辑模型报文规范.xlsx`（13 sheet）是业务源头，但其与 `ontologies/templates/*.json` 已知 2 条未同步（xlsx 尾页「问题待办」记录：checkbox 前端不校验、地市数量不展示）
- TTL、`config_message_projection.json` 与 JSON 模板靠人工保持一致

### 4.3 单源选型

**推荐方案：以 JSON 模板为 SSOT**（xlsx 降级为「需求导入格式」），理由：
1. JSON 已是运行时直接消费的格式（ProductTemplateRegistry 加载），反向生成无歧义
2. xlsx 是业务人员视角，字段语义在 JSON 中已有更精确的表达（extends/derive_rules/compliance_bindings 无法用 xlsx 表达）
3. 工具链只需「xlsx→JSON 导入器」+「JSON→TTL/投影生成器」两个单向转换器

### 4.4 实施步骤

**Step 1：JSON Schema 固化**

- 为 `templates/*.json` 定义 JSON Schema（`ontologies/templates/schema/template.schema.json`），模板文件 CI 中先过 schema 校验

**Step 2：xlsx→JSON 导入器**

- 新增 `backend-app/scripts/import_template_from_xlsx.py`（Python，openpyxl，读取 13 sheet 的「一~六级节点/取值说明/默认值/是否必填」列结构）
- 输出为 JSON 模板草案，**只增不覆盖**：对已存在字段做 diff 报告（人工确认后合并），避免工具直接改坏模板

**Step 3：JSON→TTL / 投影生成器**

- 新增 `TemplateTtlGenerator`（Java，放在 `service/ontologygen/` 或 scripts）：从合并后的 JSON 模板生成 `product-config.ttl` 中的类/属性/互斥/依赖公理片段
- `ConfigMessageProjector` 的投影配置改为由 `message_projection` 段生成（消除 config_message_projection.json 手工维护）
- 生成物标记 `# GENERATED FROM templates/*.json DO NOT EDIT`

**Step 4：CI 一致性校验**

- 新增 CI job：重新生成 TTL/投影 → 与仓库中文件 diff → 不一致即失败（防手改生成物）
- xlsx 尾页 2 条已知问题：随本次导入器首次运行修复回写

### 4.5 验收标准

- [ ] 修改模板 JSON 字段，CI 自动检出 TTL/投影未同步
- [ ] 从 xlsx 新增一个字段并完成导入合并全流程演练（含 diff 报告）
- [ ] xlsx 尾页 2 条待办闭环
- [ ] TTL/投影文件头部带 GENERATED 标记

---

## 五、R5（P1）：生产 ABox 数据打通

### 5.1 目标

本体推理的事实层从 mock 数据切换为生产系统真实数据（ABox），mock 仅保留测试环境。

### 5.2 现状依据

- `mock_graph.json` / `rdf_seed.json` / `kb_seed.json` 为当前事实源；`ProductOntologyService.isDemoEnabled()` 存在 demo 开关
- `docs/LLM+本体融合推理-尝试与结论汇报.md` 自述「mock 数据依赖」为已知风险

### 5.3 实施步骤

**Step 1：数据源适配器抽象**

- 新增 `ABoxSourceProvider` 接口（`loadABox(): List<Statement>` 语义），两个实现：
  - `MockABoxProvider`（现有 seed，`@ConditionalOnProperty(prodai.abox.source=mock)`）
  - `JdbcABoxProvider`（`prodai.abox.source=jdbc`，从生产库视图拉取资费/订购/工单事实）

**Step 2：生产侧数据视图设计（与数据团队协作）**

- 与业务系统约定**只读同步视图**（不直连业务库主表）：资费实例表、订购关系表、告警表
- 增量策略：全量初始化 + 定时增量（`@Scheduled` 间隔可配，初始建议 30 分钟）；增量走「时间戳水位线」

**Step 3：同步安全机制**

- 复用 `LastKnownGoodGuard` 四步守卫：JdbcABoxProvider 拉取的新数据先 VALIDATE（OpsGraphSchemaValidator）→ SMOKE（关键 SPARQL 冒烟）→ COMMIT；失败自动保持旧图谱
- 数据新鲜度可观测：`getGraphSummary()` 增加 `abox_last_synced_at`、`abox_row_count` 字段

**Step 4：环境灰度**

- dev → 测试环境（数据脱敏副本）→ 生产只读灰度（demo 开关与数据源开关解耦：`prodai.abox.source` 独立于 `isDemoEnabled`）

### 5.4 验收标准

- [ ] 测试环境以脱敏生产副本运行，ops 场景 5 个工具（SparqlQuery/SwrlRootCause/SwrlRiskAudit 等）在真实数据上出数
- [ ] 同步失败时图谱自动回退到 last-known-good，且 `abox_last_synced_at` 不刷新、告警可见
- [ ] mock 实现仅测试 profile 生效

---

## 六、R7（P2）：SHACL 引入（约束编译层增强）

### 6.1 目标

按既定 P3-3 路线引入 SHACL，将部分硬编码 R-C 规则外置为本体约束，缩小 `ComplianceRuleEngine` 硬编码面。

### 6.2 路线约束（谨慎试点，不推倒重来）

- RDF4J 4.3.4 **自带 SHACL 引擎**（`org.eclipse.rdf4j:shacl`），零新增依赖
- **只迁移「可声明化」的规则**：必填（R-C06 类）、互斥（R-C03 类）、值域/枚举（R-C05 类）；涉及跨实体计算、白名单逻辑的规则（R-C04 附加依赖、R-C07~C09）保留 Java
- 双轨过渡期：SHACL 结果与 Java 引擎结果**并跑比对**，一致率达标后才切换

### 6.3 实施步骤

1. 选 3 条规则试点：R-C06（必填）、R-C03（互斥）、R-C05（零固费值域）→ 编写 `compliance-shacl.ttl`（sh:NodeShape/sh:PropertyShape）
2. `TemplateConstraintCompiler` 增加 SHACL 编译出口：从模板 `required/enumConfig` 自动生成 shapes 片段
3. `ComplianceRuleEngine` 增加 `ShaclValidationDelegate`：校验时先走 SHACL（`RDF4J ShaclSail` 或 `ShaclValidator`），失败规则映射回 R-C 编号（保持对外结果结构不变，`violation.rule = "R-C06"` 由 shapes 的 `sh:resultMessage` 约定）
4. 并跑比对工具：`ComplianceParityTest`——同一批草稿输入分别跑 Java/SHACL，输出差异清单
5. 一致率 ≥99%（差异仅允许边界场景）后，试点 3 条规则以 SHACL 为准，Java 实现标记 `@Deprecated`

### 6.4 验收标准

- [ ] 试点 3 条规则 SHACL 与 Java 引擎并跑一致率 ≥99%
- [ ] 对外 checkCompliance 响应结构零变更（前端无感）
- [ ] 新增简单值域约束时，仅需改 shapes 不需要写 Java（演练验证）

---

## 八、R8（P3）：编排层集成测试补齐

### 8.1 目标

为最复杂的编排链路建立可回归的测试防线。

### 8.2 现状依据

- `AgentOrchestrator`（1627 行）无专项测试；SSE 多事件协议（8 种事件类型）无快照校验
- 现有测试盲区清单：`processStreamMulti` 事件序列、`sanitizeTools` 过滤行为、`flow_execute` 守门、CLARIFY 超限行为、`persistTurn` 4 重载

### 7.3 实施步骤

1. **契约测试**（R2 拆分后面向新类编写）：
   - `AgentOrchestratorTest`：mock Understander/Executor/Presenter，验证 process 全链路（意图→执行→持久化→返回）
   - `DefaultUnderstanderTest`：CLARIFY 轮次上限、缺省值回填、缓存复用、幻觉工具剔除、flow_execute 白名单外拒绝
2. **事件协议快照测试**：`StreamEventProtocolTest`——固定输入下收集全部 SSE 事件，校验事件类型序列与关键字段（thinking 顺序、tool 事件 payload 结构、done 收尾）；协议变更时快照 diff 一目了然
3. **边界场景补测 FlowEngineService**：并发同 formId 乐观锁冲突（两线程同时 step 推进，仅一个成功）、超时+onFailure 组合、human 节点 resume_token 重放（防重放攻击）
4. CI 中与 R1 评测 job 并列执行

### 7.4 验收标准

- [ ] 编排层新增测试 ≥3 类，覆盖上述 5 个盲区
- [ ] 事件协议快照基线建立，协议变更必须显式更新快照
- [ ] FlowEngine 并发/重放边界用例通过

---

## 八、里程碑与依赖关系

```
迭代一 ──┬─ R1 评测体系 ────────┐
         ├─ R3 提示词外置 ──(依赖 R2.3 的 IntentPromptAssembler 拆分)
         └─ R2 拆分(Phase1: OpsWorkOrder + Draft + DocImport)
                                     │
迭代二 ──┬─ R2 拆分(Phase2: Graph/Chat/Compliance 收尾) ←─ R1 评测兜底
         ├─ R4 单源治理 ──(依赖 R2 拆出 ComplianceRuleEngine 后的稳定接口)
         └─ R5 生产数据(Step1~2: 接口抽象 + 视图设计，Step3~4 顺延迭代三)
                                     │
迭代三 ──┬─ R5 数据打通收尾(灰度上线)
         ├─ R7 SHACL 试点 ──(依赖 R2 的 ComplianceRuleEngine + R4 的单源 shapes 生成)
         └─ R8 编排层测试 ──(依赖 R2 拆分完成后的新类边界)
```

**关键依赖说明**：
1. **R1 最先做**——它是 R2 拆分、R3 提示词改动的共同安全网
2. **R2 拆分跨两个迭代**，每步一个提交、回归全绿再推进；R7/R8 都依赖拆分后的清晰边界，故排在后
3. R5 生产数据打通依赖数据团队协作，接口抽象先行的部分可在迭代二启动

## 九、整体验收清单（DoD）

- [ ] 黄金评测集 CI 门禁生效，三维评分达标
- [ ] 三个上帝类拆分完成，行数/方法数达标，API 报文 diff 为零
- [ ] 意图提示词外部热更可用，渲染 diff 为零
- [ ] 单源生成链落地，CI 一致性校验生效
- [ ] 测试环境生产脱敏数据运行，同步失败自动回退
- [ ] SHACL 试点 3 条规则并跑一致率 ≥99%
- [ ] 编排层测试覆盖 5 个已知盲区
- [ ] 全程遵守 AGENTS.md：命名/复杂度/静态分析（eslint+prettier / flake8+mypy 对应工具链为 Checkstyle+SpotBugs 建议 CI 集成）
