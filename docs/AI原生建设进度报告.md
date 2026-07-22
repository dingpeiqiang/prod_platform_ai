# AI 原生建设进度报告

> 文档版本：v1.0  
> 更新日期：2026-07-18  
> 编写：AI Assistant  
> 说明：全面梳理项目 AI 原生建设的已完成工作、进行中任务和后续规划

---

## 目录

1. [项目概述](#一项目概述)
2. [已完成模块](#二已完成模块)
3. [进行中工作](#三进行中工作)
4. [后续计划](#四后续计划)
5. [技术亮点](#五技术亮点)
6. [风险与建议](#六风险与建议)
7. [附录：模块清单](#七附录模块清单)

---

## 一、项目概述

### 1.1 项目定位

`prod_platform_ai` 是一个面向电信运营商的**产商品智能平台**，通过 AI 原生架构实现产商品全生命周期的智能化管理，覆盖研发配置、运营洞察、风险稽核、合规校验等核心业务场景。

### 1.2 技术架构演进

| 阶段 | 技术栈 | 状态 |
|------|--------|------|
| v1.0 | Python FastAPI + Flask | 已废弃，仅作历史对照 |
| v2.0 | Spring Boot 3.4 + Spring AI Claude | **当前主路径** |
| v3.0 | 待定（Function Calling / RAG / Agent） | 规划中 |

### 1.3 核心能力矩阵

| 能力 | 状态 | 说明 |
|------|------|------|
| AI 意图识别 | ✅ 已完成 | LLM 驱动的意图识别，5 种意图类型 |
| 流式 SSE 输出 | ✅ 已完成 | 思考过程 + 流式文本 + 结构化事件 |
| 本体推理引擎 | ✅ 已完成 | RDF4J + SPARQL + LLM 增强 |
| 策略集评估 | ✅ 已完成 | 4 套业务规则集 |
| 对话持久化 | ✅ 已完成 | JPA + MySQL，会话/消息 CRUD |
| 前端 AI 交互 | ✅ 已完成 | Vue 3 + SSE 流式消息 |
| 多模型支持 | ✅ 已完成 | 环境变量 + 数据库 + 请求级配置 |
| Function Calling | ⏸️ 规划中 | LLM 主动调用工具 |
| RAG 检索增强 | ⏸️ 规划中 | 知识库 + 向量检索 |

---

## 二、已完成模块

### 2.1 AI 原生架构层

#### 2.1.1 Spring Boot 3.4 基础框架

**技术栈：** Java 17 + Spring Boot 3.4 + Spring AI Claude + Maven

| 文件 | 说明 | 状态 |
|------|------|------|
| `ProdAiApplication.java` | 应用入口 | ✅ |
| `ProdAiProperties.java` | 配置属性类 | ✅ |
| `ConfigLoader.java` | 配置加载器 | ✅ |
| `WebConfig.java` | Web 配置（CORS） | ✅ |
| `JpaConfig.java` | JPA 配置 | ✅ |
| `RequestLoggingFilter.java` | 请求日志过滤器 | ✅ |

#### 2.1.2 AI 原生 SSE 流式控制器

**核心文件：** `ChatStreamController.java`

```
POST /api/v1/chat/agent/stream
├── 意图识别（LLM）
├── 意图分发（Registry）
├── 流式输出（SSE）
└── 对话持久化（JPA）
```

**完整流程：**
1. 接收用户消息 + 历史上下文
2. 构建意图识别 Prompt（`IntentPromptManager`）
3. 调用 LLM 进行意图识别
4. 解析 JSON 结果，归一化意图类型
5. 构建 `IntentContext` 数据袋
6. 分发到对应处理器（`IntentHandlerRegistry`）
7. 发送 SSE 事件流（thinking → intent → text → stats → done）
8. 异步持久化对话（ChatPersistenceService）

#### 2.1.3 意图识别 Prompt 管理

**核心文件：** `IntentPromptManager.java`

- 从 `classpath:prompts/intent_recognition_prompt.txt` 加载模板
- 支持 `{{sceneHint}}`、`{{historyBlock}}`、`{{lastUserMessage}}` 占位符
- 支持热替换（调用 `/api/v1/prompts/reload`）
- 5 种意图类型：`chat` / `product_ops_query` / `product_ops_policy` / `product_ops_reason` / `product_ops_compare`

#### 2.1.4 意图处理器注册中心

**核心文件：** `IntentHandlerRegistry.java`

- Spring 自动注入所有 `BaseIntentHandler` 实现
- 按 `intentType` 注册到 ConcurrentHashMap
- 支持处理器列表、检查、获取
- 未注册意图自动降级到 `ChatHandler`

#### 2.1.5 SSE 事件协议

**核心文件：** `SseUtils.java`

| 事件类型 | 说明 | 数据格式 |
|---------|------|---------|
| `thinking` | 推理过程 | `{content, metadata}` |
| `text_start` | 文本开始 | - |
| `text` | 流式文本 | `{content}` |
| `text_end` | 文本结束 | - |
| `intent` | 意图事件 | `{intentType, action, data}` |
| `stats` | 统计信息 | `{elapsed, tokens, cost}` |
| `done` | 完成事件 | `{intentType, isForm, stats}` |
| `error` | 错误信息 | `{error}` |

#### 2.1.6 流式输出统计

**核心文件：** `StreamStats.java`

- Token 估算（中文 1 字 ≈ 2 token，英文 1 词 ≈ 1.3 token）
- 成本估算（以 gpt-4o-mini 为例：$0.15/1M input, $0.60/1M output）
- 性能指标（elapsed, tokensPerSecond, charsPerSecond）

---

### 2.2 LLM 服务层

#### 2.2.1 LLM 服务

**核心文件：** `LlmService.java`

**已实现能力：**

| 方法 | 说明 | 用途 |
|------|------|------|
| `complete()` | 同步补全 | 意图识别 |
| `completePrompt()` | 单 Prompt 补全 | 简单查询 |
| `completeMessages()` | 多轮对话补全 | 上下文对话 |
| `streamChatText()` | 流式文本输出 | 聊天回复 |
| `streamWithMessages()` | 多轮流式输出 | 带上下文的聊天 |
| `streamEvents()` | 流式事件输出 | 完整 SSE 流程 |

**技术细节：**
- 使用 Spring AI `ChatClient`，基于 OpenAI 兼容 API
- `ChatClient` 缓存（按 `baseUrl + apiKey` 缓存，避免重复创建）
- 请求超时：连接 30s，读取 120s
- 支持多模型配置优先级：请求级 > 数据库级 > 环境变量级

#### 2.2.2 LLM 配置服务

**核心文件：** `LlmConfigService.java`

- 用户级 LLM 配置管理（`LlmUserConfig` 实体）
- 激活配置查询
- 配置保存/更新

---

### 2.3 意图处理器层

#### 2.3.1 通用聊天处理器

**核心文件：** `ChatHandler.java`

- 意图类型：`chat`
- 功能：通用 AI 对话，流式输出
- 系统提示词：`"你是产商品智能助手，基于上下文为用户提供建议和帮助。"`
- 支持最近 20 条历史消息上下文
- 降级：LLM 不可用时返回固定提示

#### 2.3.2 市场洞察处理器

**核心文件：** `ProductOpsQueryHandler.java`

- 意图类型：`product_ops_query`
- 功能：NL 查询 + 本体检索
- 流程：LLM 实体发现 → SPARQL 构建 → 本体检索 → 结果格式化
- 降级：LLM 不可用时使用关键词匹配

#### 2.3.3 立项研判处理器

**核心文件：** `ProductOpsPolicyHandler.java`

- 意图类型：`product_ops_policy`
- 功能：规则引擎评估
- 策略集：`PS_PRODUCT_ONLINE_V1` / `PS_PRODUCT_RISK_V1` / `PS_MARKETING_RECOMMEND_V1` / `PS_BILLING_REFUND_V1`
- 输出：verdict（allow/deny/review）+ reason + triggered rules

#### 2.3.4 异动归因处理器

**核心文件：** `ProductOpsReasonHandler.java`

- 意图类型：`product_ops_reason`
- 功能：证据链追溯
- 流程：NL 查询 → 解释生成 → 规则引用 → 证据关联
- 输出：explanation + referenced rules + evidence count

#### 2.3.5 假设分析处理器

**核心文件：** `ProductOpsCompareHandler.java`

- 意图类型：`product_ops_compare`
- 功能：场景模拟与变更影响评估
- 流程：获取当前事实 → 构建假设变更 → evaluate → 对比结果
- 支持启发式变更提取（从自然语言推断变更参数）

#### 2.3.6 意图上下文构建器

**核心文件：** `BaseIntentContextBuilder.java`

- `extractPolicyFacts()` - 从意图上下文提取策略事实
- `resolvePolicySetId()` - 根据上下文解析策略集 ID
- `resolveExpectationType()` - 根据上下文解析期望类型

---

### 2.4 本体服务层

#### 2.4.1 本体服务

**核心文件：** `OntologyService.java`

**已实现能力：**

| 方法 | 说明 |
|------|------|
| `retrieve()` | 实体检索（RDF4J） |
| `evaluate()` | 策略集评估 |
| `explain()` | 解释生成 |
| `nlQuery()` | NL → SPARQL 查询 |
| `nlDiscoverAndRetrieve()` | LLM 增强实体发现 + 检索 |
| `compareState()` | 状态对比与假设分析 |
| `getTrace()` | 审计追踪查询 |
| `importTtl()` | TTL 本体导入 |
| `sparqlQuery()` | SPARQL 查询执行 |

**技术细节：**
- 基于 RDF4J 的本体存储（内存模式）
- 支持 SPARQL 1.1 查询
- LLM 实体发现：从自然语言提取实体类型 + 筛选条件 → 构建 SPARQL
- 降级策略：LLM 不可用时使用关键词匹配

#### 2.4.2 RDF4J 本体存储

**核心文件：** `Rdf4jOntologyStore.java`

- 内存 RDF4J 存储
- TTL 导入/解析
- SPARQL 查询执行
- 实例/类/属性查询

---

### 2.5 对话持久化层

#### 2.5.1 对话持久化服务

**核心文件：** `ChatPersistenceService.java`

**已实现能力：**

| 方法 | 说明 |
|------|------|
| `getOrCreateSession()` | 获取或创建会话 |
| `updateSessionTitle()` | 更新会话标题 |
| `archiveSession()` | 归档会话 |
| `getRecentSessions()` | 获取最近会话列表 |
| `saveMessage()` | 保存单条消息 |
| `saveMessages()` | 批量保存消息 |
| `getSessionMessages()` | 获取会话所有消息 |
| `getRecentMessages()` | 获取最近 N 条消息 |
| `getMessageCount()` | 获取消息数量 |
| `searchMessages()` | 按关键词搜索消息 |

#### 2.5.2 JPA 实体

**核心文件：**
- `ChatSession.java` - 会话实体
- `ChatMessage.java` - 消息实体
- `ChatMessageMetadata.java` - 消息 KV 扩展实体

#### 2.5.3 JPA Repository

**核心文件：**
- `ChatSessionRepository.java`
- `ChatMessageRepository.java`
- `ChatMessageMetadataRepository.java`

---

### 2.6 管理后台层

#### 2.6.1 管理控制器

**核心文件：** `AdminController.java`

**三组端点：**

| 模块 | 端点 | 说明 |
|------|------|------|
| 场景管理 | `/api/v1/scenes/*` | 场景树、CRUD、测试 |
| 提示词管理 | `/api/v1/prompts/*` | CRUD、版本、预览、生成、优化 |
| 本体管理 | `/api/v1/ontologies/*` | CRUD、分类、状态切换 |

#### 2.6.2 场景服务

**核心文件：** `SceneService.java`

- 场景树查询
- 场景 CRUD
- 场景统计
- 场景测试

#### 2.6.3 提示词服务

**核心文件：** `PromptService.java`

- 提示词 CRUD
- 版本管理
- 预览渲染
- AI 生成/优化

---

### 2.7 前端 AI 原生交互层

#### 2.7.1 路由架构

**核心文件：** `frontend/src/router/index.js`

| 路径 | 组件 | 说明 |
|------|------|------|
| `/rd` | `RdAssistantPage.vue` | 研发配置助手（默认） |
| `/ops` | `OpsAssistantPage.vue` | 运营助手 |

#### 2.7.2 研发助手页面

**核心文件：** `RdAssistantPage.vue`

**快捷场景：**
- 对话配置（`rd.chat`）- 自然语言配置产商品
- 批量生成（`rd.import`）- 导入配置方案
- AI 智查（`market_insight`）- 查询配置数据
- 合规校验（`online_check`）- 校验在架规则

#### 2.7.3 运营助手页面

**核心文件：** `OpsAssistantPage.vue`

**快捷场景：**
- 市场洞察（`market_insight`）- 查询在售商品、增长指标
- 立项研判（`online_check`）- 评估新品上市门槛
- 异动归因（`root_cause`）- 追溯收入下滑根因
- 风险稽核（`risk_audit`）- 筛查零资费风险商品

#### 2.7.4 SSE 流式消息处理

**核心文件：** `useChatStream.js`

**已实现能力：**

| 功能 | 说明 |
|------|------|
| 消息管理 | `pushUserMessage` / `upsertAssistantMessage` |
| 意图事件处理 | `applyIntentEvent` |
| 会话管理 | `loadSessions` / `switchSession` / `newSession` |
| 流式发送 | `sendMessage`（SSE 流式解析） |
| 中断控制 | `stop`（AbortController） |

**SSE 事件处理：**
- `thinking` → 推理步骤（折叠显示）
- `text` → 流式文本（打字机效果）
- `stats` → 统计信息
- `done` → 完成事件（触发后处理器）
- `intent` → 意图事件（路由到面板组件）

#### 2.7.5 意图事件注册器

**核心文件：** `useIntentRegistry.js`

- 事件处理器注册（`registerEventHandler`）
- 后处理器注册（`registerPostProcessor`）
- 面板组件映射（`getEventPanel`）
- 支持动态扩展

#### 2.7.6 会话 API

**核心文件：** `chatApi.js`

- 会话 CRUD（`createSession` / `getSessions` / `loadMessages`）
- AI 原生流式发送（`sendMessageWithModel`）
- 支持 AbortController 中断

---

### 2.8 异常处理层

#### 2.8.1 全局异常处理器

**核心文件：** `GlobalExceptionHandler.java`

| 异常类型 | HTTP 状态码 | 说明 |
|---------|-----------|------|
| `IllegalArgumentException` | 400 | 参数错误 |
| `IllegalStateException` | 503 | 服务不可用（LLM 未启用） |
| `MethodArgumentNotValidException` | 400 | 校验失败 |
| `CompletionException` | 500 | 异步任务失败 |
| `IOException` | 500 | SSE 客户端断开 |
| `Exception` | 500 | 未捕获异常 |

---

### 2.9 数据模型层

#### 2.9.1 JPA 实体

| 实体 | 说明 |
|------|------|
| `ChatSession` | 会话 |
| `ChatMessage` | 消息 |
| `ChatMessageMetadata` | 消息 KV 扩展 |
| `LlmUserConfig` | LLM 用户配置 |
| `Prompt` | 提示词 |
| `PromptTemplate` | 提示词模板 |
| `PromptVersion` | 提示词版本 |
| `Scene` | 场景 |
| `SceneHistory` | 场景历史 |
| `OntologyInstance` | 本体实例 |
| `OntologyInstanceHistory` | 本体实例历史 |
| `McpToolDefinition` | MCP 工具定义 |
| `McpCallLog` | MCP 调用日志 |
| `McpToolStats` | MCP 工具统计 |
| `Trace` | 追踪 |
| `Span` | 跨度 |
| `Workflow` | 工作流 |
| `WorkflowExecution` | 工作流执行 |
| `WorkflowHistory` | 工作流历史 |
| `ModelProvider` | 模型提供商 |

---

## 三、进行中工作

### 3.1 未提交的新文件

**后端新增：**
- `ChatStreamController.java` - AI 原生流式控制器
- `ChatV2Controller.java` - 会话 v2 API
- `ChatHistoryController.java` - 历史管理
- `AdminController.java` - 管理后台
- `ChatPersistenceService.java` - 对话持久化
- `IntentPromptManager.java` - Prompt 模板管理
- `ProductOpsCompareHandler.java` - 假设分析处理器
- `intent_recognition_prompt.txt` - 意图识别 Prompt

**前端新增：**
- `RdAssistantPage.vue` - 研发助手页面
- `OpsAssistantPage.vue` - 运营助手页面
- `useChatStream.js` - 流式消息 composable
- `chatApi.js` - 会话 API

### 3.2 已修改的文件

**后端修改：**
- `ChatStreamController.java` - 多次优化（意图分发、持久化、异常处理）
- `GlobalExceptionHandler.java` - SSE 异常处理增强
- `SseUtils.java` - SSE 工具增强（intent 事件、stats 事件）
- `StreamStats.java` - Token 估算与成本统计
- `ChatHandler.java` - 流式输出优化（多轮对话、降级处理）
- `ProductOpsPolicyHandler.java` - 策略评估优化
- `ProductOpsQueryHandler.java` - 查询优化（LLM 实体发现）
- `ProductOpsReasonHandler.java` - 归因优化（证据链追溯）
- `OntologyService.java` - 本体服务增强（NL 查询、实体发现、状态对比）

**前端修改：**
- `ProductOpsPanel.vue` - 面板优化

---

## 四、后续计划

### 4.1 短期（1-2 周）

#### 4.1.1 完善意图处理器

| 处理器 | 意图类型 | 优先级 | 说明 |
|--------|---------|--------|------|
| `FormHandler` | `form` | P0 | 表单生成意图 |
| `ValidationHandler` | `validate` | P0 | 校验意图 |
| `ConfigureHandler` | `configure` | P1 | 配置意图 |
| `ManageHistoryHandler` | `manage_history` | P2 | 历史管理意图 |
| `DeleteFormHandler` | `delete_form` | P2 | 删除表单意图 |

#### 4.1.2 增强 LLM 服务

| 功能 | 优先级 | 说明 |
|------|--------|------|
| Function Calling | P0 | LLM 主动调用工具（Tools 注册与调用） |
| 多模型切换 | P1 | 支持按场景/用户切换模型 |
| Token 精度优化 | P1 | 使用 tiktoken 替代字符估算 |

#### 4.1.3 前端交互优化

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 意图面板组件 | P0 | ProductOpsPanel、FormIntentPanel 等 |
| 消息卡片渲染 | P0 | 表格、图表、表单、代码块 |
| 思考过程折叠 | P1 | thinking 事件折叠/展开 |
| 会话侧边栏 | P1 | 会话列表、新建、切换 |

### 4.2 中期（2-4 周）

#### 4.2.1 本体推理引擎

| 功能 | 优先级 | 说明 |
|------|--------|------|
| IntegrativeReasonEngine | P0 | Java 版集成推理引擎 |
| SWRL 规则推理 | P1 | 基于 OWL 的规则推理 |
| 假设分析增强 | P1 | 反事实推理、多场景模拟 |

#### 4.2.2 知识图谱可视化

| 功能 | 优先级 | 说明 |
|------|--------|------|
| SchemaGraph 组件 | P0 | 节点/边渲染（vis-network） |
| 本体实例浏览 | P1 | 实例列表、详情、编辑 |
| SPARQL 查询可视化 | P2 | 查询结果图谱化 |

#### 4.2.3 工作流引擎

| 功能 | 优先级 | 说明 |
|------|--------|------|
| WorkflowService | P0 | 工作流定义与执行 |
| LangChain 风格编排 | P1 | 节点拖拽、连线、配置 |
| 工作流可视化编辑器 | P1 | VueFlow 组件 |

### 4.3 长期（1-2 月）

#### 4.3.1 生产化部署

| 功能 | 优先级 | 说明 |
|------|--------|------|
| JWT 鉴权 | P0 | 用户认证 |
| RBAC 权限 | P0 | 角色权限控制 |
| 审计日志 | P1 | 操作审计、合规追溯 |
| 多租户隔离 | P1 | 数据隔离、配置隔离 |

#### 4.3.2 性能优化

| 功能 | 优先级 | 说明 |
|------|--------|------|
| LLM 调用缓存 | P0 | 语义相似度匹配，命中缓存直接返回 |
| 本体查询缓存 | P1 | 热点数据预加载 |
| SSE 连接管理 | P1 | 心跳、重连、负载均衡 |

#### 4.3.3 智能增强

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 推荐引擎 | P1 | 历史数据导入 + 协同过滤 |
| 意图识别监控 | P1 | 准确率、召回率、混淆矩阵 |
| LLM 输出评估 | P2 | 幻觉检测、一致性校验 |

---

## 五、技术亮点

### 5.1 AI 原生架构

从传统 REST API 转向 **SSE 流式意图处理**，用户输入直接触发 LLM 推理，无需人工配置路由。

```
用户输入 → LLM 意图识别 → 处理器分发 → 流式输出
```

### 5.2 意图驱动

LLM 自动识别用户意图（5 种类型），分发到对应处理器，新增意图类型只需实现接口并注册。

### 5.3 本体约束

基于 RDF4J 的本体存储，确保 AI 输出符合业务规则，无幻觉。LLM 实体发现 + SPARQL 查询双重保障。

### 5.4 流式体验

完整的事件流：`thinking → intent → text_start → text → text_end → stats → done`

用户可实时看到推理过程、流式文本、结构化数据、统计信息。

### 5.5 可扩展性

处理器注册中心模式，新增意图类型只需：
1. 实现 `BaseIntentHandler` 接口
2. 添加 `@Component` 注解
3. 实现 `getIntentType()` 和 `handle()` 方法

### 5.6 降级策略

LLM 不可用时自动降级：
- 意图识别 → 关键词匹配
- 策略评估 → 规则引擎兜底
- 对话回复 → 固定提示

---

## 六、风险与建议

### 6.1 风险

| 风险 | 等级 | 说明 |
|------|------|------|
| LLM 依赖 | 高 | 当前强依赖 LLM 服务，不可用时功能降级 |
| Token 成本 | 中 | 意图识别 + 业务处理双重 LLM 调用 |
| 测试覆盖 | 中 | 新增模块缺乏单元测试 |
| 文档同步 | 低 | README 和接口文档需更新 |

### 6.2 建议

1. **增加降级策略**：为每个意图处理器增加规则引擎兜底，减少 LLM 依赖
2. **缓存意图识别**：对常见问题缓存意图识别结果，降低 Token 成本
3. **补充测试用例**：为核心处理器（ChatHandler、ProductOpsQueryHandler）编写单元测试
4. **更新文档**：同步更新 README、接口契约清单、数据库设计文档
5. **性能监控**：接入 Prometheus + Grafana，监控 LLM 调用耗时、成功率、Token 消耗

---

## 七、附录：模块清单

### 7.1 后端模块

| 模块 | 路径 | 文件数 |
|------|------|--------|
| 控制器 | `controller/` | 8 |
| 服务 | `service/` | 12 |
| 意图处理器 | `intent/handlers/` | 8 |
| 意图框架 | `intent/` | 5 |
| 数据模型 | `domain/entity/` | 20 |
| DTO | `dto/` | 8 |
| Repository | `repository/` | 18 |
| 配置 | `config/` | 5 |
| 异常处理 | `exception/` | 1 |
| 过滤器 | `filter/` | 1 |
| 公共类 | `common/` | 2 |

### 7.2 前端模块

| 模块 | 路径 | 文件数 |
|------|------|--------|
| 页面组件 | `components/` | 35 |
| Composables | `composables/` | 8 |
| 服务 | `services/` | 12 |
| 状态管理 | `stores/` | 5 |
| 路由 | `router/` | 1 |
| 工具函数 | `utils/` | 4 |
| 样式 | `styles/` | 3 |

### 7.3 配置文件

| 文件 | 说明 |
|------|------|
| `intent_recognition_prompt.txt` | 意图识别 Prompt 模板 |
| `offering_config.json` | 产品配置本体 |
| `tariff_filing_publicity.json` | 资费公示本体 |
| `scene_mapping.json` | 场景映射配置 |
| `application.yml` | Spring Boot 配置 |
| `application-dev.yml` | 开发环境配置 |

---

**文档结束**

> 本文档基于代码库实际状态生成，反映截至 2026-07-18 的项目进展。  
> 如有疑问，请联系项目负责人或查阅代码库中的相关文件。
