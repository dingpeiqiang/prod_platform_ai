# 产商品研发助手 - 后台运营管理系统规划设计

## 一、项目概述

### 1.1 项目背景

本项目是基于 AI 驱动的动态表单底层框架，当前已具备智能表单识别、字段提取、表单验证、历史数据推荐等核心能力。现需构建**后台运营管理系统**，提供完善的运营管理功能，包括场景管理、会话管理、表单配置、数据管理、系统监控等能力。

**核心理念**：**意图识别即场景识别**。助手的能力边界由场景定义决定，通过场景管理系统可以动态扩展和管理助手的业务能力。

### 1.2 设计目标

| 目标 | 描述 |
|------|------|
| 统一管理入口 | 提供集中的后台管理界面，统一管理场景、表单、会话、用户等资源 |
| 场景管理能力 | 支持场景的创建、配置、启用/停用，动态控制助手的能力边界 |
| 可视化监控 | 提供系统运行状态、会话统计、场景使用情况等可视化仪表盘 |
| 高效配置 | 支持表单 Schema 和场景规则的可视化配置和版本管理 |
| 数据管理 | 支持历史数据导入、分析和推荐引擎配置 |
| 权限控制 | 支持管理员角色和操作权限管理 |

### 1.3 核心价值

- **运营效率提升**：通过可视化管理界面，减少人工操作成本
- **系统可观测性**：实时监控系统运行状态，快速定位问题
- **配置灵活性**：支持场景和表单动态配置，无需代码修改即可扩展业务
- **能力可扩展**：通过场景管理，动态扩展助手的业务能力范围

---

### 1.4 核心概念

#### 核心理念

**场景驱动的工具调度架构**：
1. 助手通过用户输入和上下文识别场景
2. 通过场景提示词驱动大模型
3. 大模型引导用户和使用工具完成场景任务
4. 表单、API、工作流等等都是大模型的工具

```
┌─────────────────────────────────────────────────────────────┐
│                     用户输入/上下文                           │
└─────────────────────────────┬───────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   [1] 场景识别层                              │
│   - 关键词匹配 (Keyword Matching)                            │
│   - LLM 智能识别 (LLM Scene Recognition)                     │
│   - 确定当前场景 (Determine Active Scene)                   │
└─────────────────────────────┬───────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   [2] 场景提示词加载                          │
│   - 加载场景专属提示词 (Load Scene Prompt)                   │
│   - 注入上下文信息 (Inject Context)                          │
│   - 准备工具列表 (Prepare Tool List)                        │
└─────────────────────────────┬───────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   [3] 大模型驱动层                            │
│   - 理解用户需求 (Understand User Intent)                    │
│   - 决策下一步 (Decide Next Step)                            │
│   - 选择工具 (Select Tools to Use)                           │
│   - 引导用户对话 (Guide User Conversation)                   │
└─────────────────────────────┬───────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   [4] 工具执行层                              │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│   │ 表单工具     │  │ API调用工具  │  │ 工作流工具   │        │
│   │ (Form)      │  │ (API)       │  │ (Workflow)  │        │
│   └─────────────┘  └─────────────┘  └─────────────┘        │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│   │ 数据查询工具  │  │ 验证工具     │  │ 推荐引擎工具 │        │
│   │ (Data Query)│  │ (Validation) │  │ (Recommender)│        │
│   └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────┬───────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   [5] 结果输出层                              │
│   - 工具执行结果 (Tool Execution Results)                   │
│   - 表单生成/更新 (Form Generation/Update)                  │
│   - 对话回复 (Conversational Response)                       │
└─────────────────────────────────────────────────────────────┘
```

#### 场景（Scene）

场景是助手能力的基本单元，定义了助手能够识别和处理的业务场景。

| 属性 | 说明 |
|------|------|
| `sceneCode` | 场景唯一编码 |
| `sceneName` | 场景显示名称 |
| `description` | 场景描述 |
| `keywords` | 触发关键词列表 |
| `priority` | 识别优先级（数值越大越优先） |
| `isActive` | 是否启用 |
| `actionPrompt` | 场景动作提示词文件 |
| `availableTools` | 该场景可使用的工具列表 |
| `toolConfig` | 工具配置参数 |

#### 工具（Tool）

工具是大模型可以调用的能力单元，表单、API、工作流都是工具。

| 属性 | 说明 |
|------|------|
| `toolCode` | 工具唯一编码 |
| `toolName` | 工具显示名称 |
| `toolType` | 工具类型 (form/api/workflow/query/validation/recommend) |
| `description` | 工具描述 |
| `parameters` | 工具参数定义 |
| `handler` | 工具执行处理器 |

#### 场景管理的意义

1. **能力边界控制**：通过启用/停用场景，动态控制助手能做什么
2. **业务扩展**：无需修改代码，通过配置新增场景
3. **优先级调整**：调整场景优先级，控制识别顺序
4. **关键词优化**：管理触发关键词，提升识别准确率
5. **工具编排**：为每个场景配置可用的工具和参数

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      前端层 (Vue 3)                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ 仪表盘Dashboard│ │ 场景管理     │ │ 会话管理     │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ 表单管理     │ │ 数据管理     │ │ 系统配置     │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
│  ┌──────────────┐                                                │
│  │ 用户管理     │                                                │
│  └──────────────┘                                                │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP/REST
┌───────────────────────────▼─────────────────────────────────────┐
│                      后端层 (FastAPI)                           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Admin Router (/api/v1/admin)               │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │    │
│  │  │ Scene    │ │ Session  │ │  Form    │ │  Data    │   │    │
│  │  │ Manager  │ │ Manager  │ │ Manager  │ │ Manager  │   │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │    │
│  │  ┌──────────┐ ┌──────────┐                                │    │
│  │  │ System   │ │ User     │                                │    │
│  │  │ Config   │ │ Manager  │                                │    │
│  │  └──────────┘ └──────────┘                                │    │
│  └─────────────────────────────────────────────────────────┘    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ SQLAlchemy
┌───────────────────────────▼─────────────────────────────────────┐
│                       数据层 (SQLite/PostgreSQL)               │
│  scenes | sessions | messages | forms | users | system_config   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

| 模块 | 职责 | 状态 |
|------|------|------|
| Dashboard | 系统概览、实时监控、统计图表（含场景使用统计） | 新增 |
| **SceneManager** | **场景配置、关键词管理、启用/停用、测试识别** | **核心新增** |
| SessionManager | 会话列表、详情查看、批量删除 | 新增 |
| FormManager | 表单 Schema 管理、版本控制、部署 | 增强现有 |
| DataManager | 历史数据导入、分析、推荐配置 | 增强现有 |
| SystemConfig | 系统参数配置、日志查看 | 新增 |
| UserManager | 用户列表、角色权限管理 | 新增 |

### 2.3 场景管理核心流程

```
┌─────────────────────────────────────────────────────────────┐
│                    场景识别流程                                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  用户输入                                                     │
│     │                                                         │
│     ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  1. 关键词匹配（按优先级降序）                        │    │
│  │     - 遍历所有启用的场景                              │    │
│  │     - 检查用户输入是否包含关键词                      │    │
│  └─────────────────────────────────────────────────────┘    │
│     │ 匹配成功？                                              │
│     ├─ 是 ───────────────────────────────────────────────────┤
│     │ 否                                                      │
│     ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  2. LLM 智能识别                                      │    │
│  │     - 将用户输入 + 场景列表发送给 LLM                 │    │
│  │     - LLM 返回最匹配的场景编码                        │    │
│  └─────────────────────────────────────────────────────┘    │
│     │ 识别成功？                                              │
│     ├─ 是 ───────────────────────────────────────────────────┤
│     │ 否                                                      │
│     ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  3. 默认场景（generic）                              │    │
│  └─────────────────────────────────────────────────────┘    │
│     │                                                         │
│     ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  4. 意图路由                                          │    │
│  │     - 根据场景配置的 intentType 查找处理器            │    │
│  │     - 分发到对应处理器执行                            │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.4 场景与其他模块的关系

```
┌─────────────────────────────────────────────────────────────┐
│                        场景管理模块                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  场景配置数据                                          │  │
│  │  { sceneCode, sceneName, keywords, intentType, ... }  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
          │                    │                    │
          │ 关联              │ 关联              │ 关联
          ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   意图处理器    │  │   表单Schema    │  │  推荐引擎数据   │
│  (Handler)      │  │  (Ontology)     │  │  (History)      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### 2.3 关键技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 前端框架 | Vue | 3.x | 主框架 |
| UI组件 | Element Plus | 2.x | UI组件库 |
| 图表库 | ECharts | 5.x | 数据可视化 |
| 后端框架 | FastAPI | 0.100+ | API框架 |
| 数据库 | SQLite/PostgreSQL | - | 数据存储 |
| 状态管理 | Pinia | 2.x | 前端状态管理 |

---

## 三、功能设计

### 3.1 场景管理 (SceneManager)

#### 3.1.1 功能概述

场景管理是后台系统的核心模块，负责管理助手的业务能力边界。通过场景管理可以：创建新场景、配置识别规则、关联表单、启用/停用场景、测试场景识别效果。

#### 3.1.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 场景列表 | 分页展示所有场景，支持按状态、名称筛选 | `GET /api/v1/admin/scenes` |
| 场景详情 | 查看单个场景的完整配置 | `GET /api/v1/admin/scenes/{sceneCode}` |
| 创建场景 | 新建场景配置 | `POST /api/v1/admin/scenes` |
| 编辑场景 | 修改场景配置（关键词、优先级、关联等） | `PUT /api/v1/admin/scenes/{sceneCode}` |
| 删除场景 | 删除场景（软删除，保留历史记录） | `DELETE /api/v1/admin/scenes/{sceneCode}` |
| 启用/停用 | 切换场景的启用状态 | `PATCH /api/v1/admin/scenes/{sceneCode}/status` |
| 测试识别 | 输入测试文本，验证场景识别效果 | `POST /api/v1/admin/scenes/test` |
| 导出配置 | 导出场景配置为 JSON | `GET /api/v1/admin/scenes/export` |
| 导入配置 | 从 JSON 导入场景配置 | `POST /api/v1/admin/scenes/import` |
| 场景统计 | 查看场景使用次数、成功率等统计 | `GET /api/v1/admin/scenes/stats` |

#### 3.1.3 场景配置表单设计

**基础信息**：
- `sceneCode`: 场景编码（英文唯一标识）
- `sceneName`: 场景名称（中文显示）
- `description`: 场景描述
- `isActive`: 是否启用

**识别规则**：
- `keywords`: 触发关键词列表（支持批量添加）
- `priority`: 识别优先级（0-100，数值越大越优先）
- `intentType`: 关联的意图处理器类型（下拉选择）
- `formCode`: 关联的表单编码（下拉选择）

**高级配置**：
- `llmPrompt`: 自定义 LLM 识别提示词（可选）
- `fallbackScene`: 识别失败时的降级场景（可选）

#### 3.1.4 页面布局

```
┌─────────────────────────────────────────────────────────────┐
│  场景管理                                                    │
├─────────────────────────────────────────────────────────────┤
│  [搜索场景...]  [状态: 全部▼]  [新建场景]  [导出]  [导入]    │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐ │
│  │  场景列表                                              │ │
│  │  ┌───┬──────────┬──────────┬──────────┬──────┬───────┐ │ │
│  │  │ ✓ │ 场景编码  │ 场景名称  │ 优先级  │ 状态 │ 操作  │ │ │
│  │  ├───┼──────────┼──────────┼──────────┼──────┼───────┤ │ │
│  │  │   │ tariff_  │ 资费备案  │ 10       │ 启用 │ 编辑  │ │ │
│  │  │   │ filing   │ 公示     │          │      │ 测试  │ │ │
│  │  │   │          │          │          │      │ 删除  │ │ │
│  │  └───┴──────────┴──────────┴──────────┴──────┴───────┘ │ │
│  └───────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  <  1  2  3  4  >  共 12 个场景                             │
└─────────────────────────────────────────────────────────────┘
```

#### 3.1.5 场景识别测试页面

```
┌─────────────────────────────────────────────────────────────┐
│  场景识别测试                                                │
├─────────────────────────────────────────────────────────────┤
│  输入测试文本：                                              │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 我要备案套餐 P000111                                   │ │
│  └───────────────────────────────────────────────────────┘ │
│  [开始识别]                                                 │
├─────────────────────────────────────────────────────────────┤
│  识别结果：                                                  │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ ✅ 识别成功                                            │ │
│  │ 场景编码: tariff_filing_publicity                      │ │
│  │ 场景名称: 资费备案公示                                 │ │
│  │ 识别方法: 关键词匹配                                  │ │
│  │ 置信度: 0.95                                           │ │
│  │ 匹配关键词: 资费备案, P000111                         │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  所有匹配场景（按优先级排序）：                              │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 1. 资费备案公示 (0.95, 关键词匹配)                     │ │
│  │ 2. 通用表单 (0.30, 默认)                              │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 仪表盘 (Dashboard)

#### 3.2.1 功能概述

提供系统运行状态概览，包括会话统计、场景使用情况、表单使用情况、系统健康度等核心指标。

#### 3.2.2 核心指标

| 指标 | 说明 | 数据来源 |
|------|------|----------|
| 今日会话数 | 今日新增会话数量 | sessions 表 |
| 活跃用户数 | 今日活跃用户数 | users 表 |
| 表单填写量 | 今日表单提交数量 | messages 表 |
| 场景识别次数 | 今日场景识别总次数 | scenes_stats 表 |
| 场景识别准确率 | 场景识别成功占比 | scenes_stats 表 |
| 系统健康度 | API 响应状态、数据库连接状态 | 系统监控 |
| 推荐命中率 | 历史数据推荐命中率 | recommendation 日志 |

#### 3.2.3 页面布局

```
┌─────────────────────────────────────────────────────┐
│  仪表盘 Dashboard                                   │
├─────────────────────────────────────────────────────┤
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │ 会话数│ │用户数│ │表单量│ │健康度│ │命中率│     │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌─────────────────────┐ │
│  │ 会话趋势图           │  │ 表单使用排行        │ │
│  │ (近7日)              │  │                     │ │
│  └──────────────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌─────────────────────┐ │
│  │ 系统告警             │  │ 最近活跃会话        │ │
│  │                     │  │                     │ │
│  └──────────────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 3.2 会话管理 (Session Management)

#### 3.2.1 功能概述

管理用户会话，支持会话列表查看、详情查看、批量删除等操作。

#### 3.2.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 会话列表 | 分页展示所有会话，支持按用户、时间筛选 | `GET /api/v2/chat/sessions` |
| 会话详情 | 查看单会话完整消息历史 | `GET /api/v2/chat/sessions/{id}/messages` |
| 删除会话 | 删除指定会话及关联消息 | `DELETE /api/v2/chat/sessions/{id}` |
| 批量删除 | 批量删除选中会话 | `DELETE /api/v2/chat/sessions/batch` |
| 会话统计 | 单会话统计信息 | `GET /api/v2/chat/sessions/{id}/stats` |

#### 3.2.3 页面布局

```
┌─────────────────────────────────────────────────────┐
│  会话管理                                            │
├─────────────────────────────────────────────────────┤
│  搜索框 [用户ID] [时间范围] [搜索按钮]               │
├─────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────┐  │
│  │ 会话列表表格                                   │  │
│  │ ID | 用户 | 标题 | 创建时间 | 消息数 | 操作    │  │
│  └───────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│  分页控件                                           │
└─────────────────────────────────────────────────────┘
```

### 3.3 表单管理 (Form Management)

#### 3.3.1 功能概述

管理表单 Schema 配置，支持创建、编辑、部署、版本管理等操作。

#### 3.3.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 表单列表 | 展示所有已部署表单 | `GET /api/v1/chat/history/list` |
| 表单详情 | 查看表单 Schema 详情 | `GET /api/v1/form/schema/{form_code}` |
| 创建表单 | 新建表单 Schema | `POST /api/v1/chat/deploy-config` |
| 编辑表单 | 修改表单配置 | `POST /api/v1/chat/deploy-config` |
| 删除表单 | 删除表单及版本备份 | `POST /api/v1/chat/delete-form` |
| 版本列表 | 查看表单历史版本 | `GET /api/v1/chat/form-versions/{form_code}` |
| 版本回退 | 回退到指定版本 | `POST /api/v1/chat/rollback-form` |

#### 3.3.3 本体编辑器设计

> **说明**：在本项目中，**Schema 编辑器即是本体编辑器**。表单配置文件存储在 `backend/config/ontologies/{form_code}.json`，采用"实体-字段"层级结构。

本体编辑器采用 JSON 可视化编辑，支持：
- **实体(Entity)管理**：增删改实体，设置实体编码和名称
- **字段(Field)管理**：增删改字段、拖拽排序
- **字段类型选择**：string、input、textarea、integer、number、boolean、date、datetime、email、phone、enum 等
- **验证规则配置**：必填设置、规则描述
- **实时预览**：即时查看表单效果
- **历史版本对比**：与历史版本进行差异对比

### 3.4 数据管理 (Data Management)

#### 3.4.1 功能概述

管理历史数据导入和推荐引擎配置。

#### 3.4.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 数据文件列表 | 展示可导入的数据文件 | `GET /api/v1/chat/history/list` |
| 数据分析 | 分析数据质量和字段分布 | `POST /api/v1/chat/history/analyze` |
| 数据导入 | 将历史数据导入推荐引擎 | `POST /api/v1/chat/history/import` |
| 推荐配置 | 配置推荐引擎参数 | 新增接口 |
| 数据预览 | 预览历史数据内容 | 新增接口 |

#### 3.4.3 推荐引擎配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| recommendationLimit | 推荐数量上限 | 5 |
| historyQueryLimit | 查询历史数量 | 1000 |
| countScoreWeight | 频率权重 | 0.4 |
| userScoreWeight | 用户个性化权重 | 0.4 |
| timeScoreWeight | 时间衰减权重 | 0.2 |
| timeDecayDays | 时间衰减天数 | 30 |

### 3.5 系统配置 (System Config)

#### 3.5.1 功能概述

管理系统级配置参数和日志查看。

#### 3.5.2 功能列表

| 功能 | 说明 |
|------|------|
| 系统参数 | 配置系统运行参数（LLM 超时、并发限制等） |
| LLM 配置 | 配置 LLM 提供商、API Key、模型选择 |
| 护栏配置 | 配置输入输出护栏规则 |
| 日志查看 | 查看系统运行日志 |
| 缓存管理 | 手动清理缓存 |

### 3.6 用户管理 (User Management)

#### 3.6.1 功能概述

管理系统用户和角色权限。

#### 3.6.2 功能列表

| 功能 | 说明 |
|------|------|
| 用户列表 | 展示所有用户 |
| 用户详情 | 查看用户信息和会话记录 |
| 创建用户 | 新增系统用户 |
| 修改密码 | 重置用户密码 |
| 角色管理 | 定义角色和权限 |

---

## 四、数据库设计

### 4.1 现有数据表

根据项目实际数据库迁移文件，现有数据表结构如下：

| 表名 | 说明 | 状态 |
|------|------|------|
| `chat_sessions` | 会话表（session_id、user_id、title、status） | 已存在 |
| `chat_messages` | 消息表（message_id、session_id、role、content、content_type） | 已存在 |
| `chat_message_metadata` | 消息元数据表（KV扩展，与业务解耦） | 已存在 |
| `form_templates` | 表单模板表（form_code、form_name、schema、version） | 已存在 |
| `form_instances` | 表单实例表（form_id、template_id、data、status） | 已存在 |
| `form_history` | 表单历史表（form_instance_id、field_code、field_value） | 已存在 |
| `ontologies` | 本体表（ontology_code、ontology_name、entities、version） | 已存在 |
| `system_logs` | 系统日志表（log_type、module、message、log_metadata） | 已存在 |

### 4.2 场景管理相关数据表（新增）

#### 4.2.1 scenes（场景配置表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| scene_code | VARCHAR(100) | UNIQUE NOT NULL | 场景唯一编码 |
| scene_name | VARCHAR(200) | NOT NULL | 场景显示名称 |
| description | TEXT | NULL | 场景描述 |
| keywords | JSON | NOT NULL | 触发关键词列表 |
| priority | INT | DEFAULT 10 | 识别优先级（0-100） |
| is_active | TINYINT(1) | DEFAULT 1 | 是否启用 |
| intent_type | VARCHAR(50) | NULL | 关联的意图处理器类型 |
| form_code | VARCHAR(100) | NULL | 关联的表单编码 |
| llm_prompt | TEXT | NULL | 自定义 LLM 识别提示词 |
| fallback_scene | VARCHAR(100) | NULL | 识别失败时的降级场景 |
| version | INT | DEFAULT 1 | 版本号 |
| created_by | VARCHAR(100) | NULL | 创建者 |
| updated_by | VARCHAR(100) | NULL | 更新者 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_scenes_scene_code`（唯一）、`idx_scenes_is_active`、`idx_scenes_priority`

#### 4.2.2 scene_versions（场景版本表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| scene_code | VARCHAR(100) | NOT NULL | 场景编码 |
| version | INT | NOT NULL | 版本号 |
| config_data | JSON | NOT NULL | 场景配置快照 |
| change_log | TEXT | NULL | 变更说明 |
| created_by | VARCHAR(100) | NULL | 创建者 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_scene_versions_scene_code`、`idx_scene_versions_version`

#### 4.2.3 scene_stats（场景统计表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| scene_code | VARCHAR(100) | NOT NULL | 场景编码 |
| stat_date | DATE | NOT NULL | 统计日期 |
| recognition_count | INT | DEFAULT 0 | 识别次数 |
| success_count | INT | DEFAULT 0 | 成功次数 |
| keyword_match_count | INT | DEFAULT 0 | 关键词匹配次数 |
| llm_match_count | INT | DEFAULT 0 | LLM 匹配次数 |
| avg_confidence | DECIMAL(5,4) | DEFAULT 0 | 平均置信度 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_scene_stats_scene_code`、`idx_scene_stats_date`、`uk_scene_code_date`（复合唯一）

#### 4.2.4 scene_recognition_logs（场景识别日志表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | NULL | 会话ID |
| user_input | TEXT | NOT NULL | 用户输入 |
| recognized_scene | VARCHAR(100) | NULL | 识别到的场景 |
| recognition_method | VARCHAR(20) | NULL | 识别方法（keyword/llm/default） |
| confidence | DECIMAL(5,4) | NULL | 置信度 |
| matched_keywords | JSON | NULL | 匹配到的关键词 |
| all_candidates | JSON | NULL | 所有候选场景及分数 |
| is_success | TINYINT(1) | DEFAULT 1 | 是否成功 |
| error_message | TEXT | NULL | 错误信息 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_scene_logs_session_id`、`idx_scene_logs_scene`、`idx_scene_logs_created_at`

### 4.4 MySQL 数据库配置

#### 4.4.1 环境变量配置

```env
# MySQL 数据库配置
DATABASE_URL=mysql+pymysql://username:password@localhost:3306/db_name?charset=utf8mb4

# 连接池配置
DB_POOL_SIZE=20
DB_MAX_OVERFLOW=10
DB_POOL_RECYCLE=3600
DB_ECHO=False
```

#### 4.4.2 MySQL 数据表设计

##### admin_users（管理员用户表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| username | VARCHAR(100) | UNIQUE NOT NULL | 用户名 |
| password_hash | VARCHAR(256) | NOT NULL | 密码哈希（bcrypt） |
| email | VARCHAR(128) | NULL | 邮箱 |
| role | VARCHAR(32) | DEFAULT 'operator' | 角色（admin/operator/viewer） |
| status | VARCHAR(20) | DEFAULT 'active' | 状态（active/inactive） |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_admin_users_username`（唯一）、`idx_admin_users_role`

##### admin_permissions（权限表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| role_code | VARCHAR(32) | NOT NULL | 角色编码 |
| resource | VARCHAR(100) | NOT NULL | 资源标识 |
| action | VARCHAR(50) | NOT NULL | 操作权限（read/write/delete） |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_admin_permissions_role_resource`（复合唯一）

##### system_config（系统配置表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| config_key | VARCHAR(128) | UNIQUE NOT NULL | 配置键 |
| config_value | LONGTEXT | NOT NULL | 配置值（JSON格式） |
| description | VARCHAR(256) | NULL | 配置说明 |
| category | VARCHAR(50) | DEFAULT 'system' | 配置分类 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_system_config_key`（唯一）、`idx_system_config_category`

### 4.3 核心表结构详情（MySQL）

#### 4.3.1 chat_sessions（会话表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | UNIQUE NOT NULL | 会话UUID |
| user_id | VARCHAR(100) | NULL | 用户ID |
| title | VARCHAR(200) | NULL | 会话标题 |
| context_tags | JSON | NULL | 上下文标签 |
| session_metadata | JSON | NULL | 会话元数据 |
| status | VARCHAR(20) | DEFAULT 'active' | 状态 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_chat_sessions_session_id`（唯一）、`idx_chat_sessions_user_id`、`idx_chat_sessions_status`

#### 4.3.2 chat_messages（消息表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| message_id | VARCHAR(64) | UNIQUE NOT NULL | 消息UUID |
| session_id | VARCHAR(64) | NOT NULL | 所属会话ID |
| role | VARCHAR(20) | NOT NULL | 角色 |
| content | LONGTEXT | NOT NULL | 消息内容 |
| content_type | VARCHAR(20) | DEFAULT 'text' | 内容类型 |
| parent_id | VARCHAR(64) | NULL | 父消息ID |
| sort_order | INT | NOT NULL | 排序字段 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_chat_messages_message_id`（唯一）、`idx_chat_messages_session_id`、`idx_chat_messages_sort_order`

#### 4.3.3 chat_message_metadata（消息元数据表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| message_id | VARCHAR(64) | NOT NULL | 所属消息ID |
| meta_key | VARCHAR(100) | NOT NULL | 元数据键 |
| value | LONGTEXT | NULL | 元数据值 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_chat_message_metadata_message_id`、`idx_chat_message_metadata_key`、`uq_message_key`（复合唯一）

#### 4.3.4 form_templates（表单模板表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_code | VARCHAR(100) | UNIQUE NOT NULL | 表单编码 |
| form_name | VARCHAR(200) | NOT NULL | 表单名称 |
| schema | LONGTEXT | NOT NULL | 表单Schema（JSON） |
| version | INT | DEFAULT 1 | 版本号 |
| is_active | TINYINT(1) | DEFAULT 1 | 是否启用 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_form_templates_form_code`（唯一）、`idx_form_templates_is_active`

#### 4.3.5 ontologies（本体表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| ontology_code | VARCHAR(100) | UNIQUE NOT NULL | 本体编码 |
| ontology_name | VARCHAR(200) | NOT NULL | 本体名称 |
| entities | LONGTEXT | NOT NULL | 实体定义（JSON） |
| form_code | VARCHAR(100) | NULL | 关联表单编码 |
| form_name | VARCHAR(200) | NULL | 关联表单名称 |
| description | VARCHAR(500) | NULL | 描述 |
| version | INT | DEFAULT 1 | 版本号 |
| is_active | TINYINT(1) | DEFAULT 1 | 是否启用 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_ontologies_ontology_code`（唯一）、`idx_ontologies_is_active`

#### 4.3.6 form_instances（表单实例表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_id | VARCHAR(50) | UNIQUE NOT NULL | 表单实例ID |
| template_id | BIGINT | NOT NULL | 关联模板ID |
| data | LONGTEXT | NOT NULL | 表单数据（JSON） |
| version | INT | DEFAULT 1 | 版本号 |
| status | VARCHAR(50) | DEFAULT 'draft' | 状态 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| submitted_at | DATETIME | NULL | 提交时间 |

**索引**：`idx_form_instances_form_id`（唯一）、`idx_form_instances_template_id`、`idx_form_instances_status`

#### 4.3.7 form_history（表单历史表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_instance_id | BIGINT | NOT NULL | 关联实例ID |
| field_code | VARCHAR(100) | NOT NULL | 字段编码 |
| field_value | LONGTEXT | NOT NULL | 字段值 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_form_history_instance_id`、`idx_form_history_field_code`

#### 4.3.8 system_logs（系统日志表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| log_type | VARCHAR(50) | NOT NULL | 日志类型 |
| module | VARCHAR(100) | NOT NULL | 模块名 |
| message | LONGTEXT | NOT NULL | 日志消息 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| log_metadata | JSON | NULL | 元数据 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_system_logs_log_type`、`idx_system_logs_module`、`idx_system_logs_created_at`

---

## 五、API 接口设计

### 5.1 新增接口列表

| API 路径 | 方法 | 说明 |
|----------|------|------|
| `/api/v1/admin/scenes` | GET | 分页获取场景列表 |
| `/api/v1/admin/scenes` | POST | 创建新场景 |
| `/api/v1/admin/scenes/{sceneCode}` | GET | 获取场景详情 |
| `/api/v1/admin/scenes/{sceneCode}` | PUT | 更新场景配置 |
| `/api/v1/admin/scenes/{sceneCode}` | DELETE | 删除场景 |
| `/api/v1/admin/scenes/{sceneCode}/status` | PATCH | 切换场景启用状态 |
| `/api/v1/admin/scenes/test` | POST | 测试场景识别 |
| `/api/v1/admin/scenes/export` | GET | 导出场景配置 |
| `/api/v1/admin/scenes/import` | POST | 导入场景配置 |
| `/api/v1/admin/scenes/stats` | GET | 获取场景统计数据 |
| `/api/v1/admin/scenes/{sceneCode}/versions` | GET | 获取场景版本历史 |
| `/api/v1/admin/scenes/{sceneCode}/rollback` | POST | 回滚到指定版本 |
| `/api/v1/admin/scenes/logs` | GET | 获取场景识别日志 |
| `/api/v1/admin/sessions` | GET | 分页获取会话列表 |
| `/api/v1/admin/sessions/batch` | DELETE | 批量删除会话 |
| `/api/v1/admin/users` | GET | 获取用户列表 |
| `/api/v1/admin/users` | POST | 创建用户 |
| `/api/v1/admin/users/{id}` | GET | 获取用户详情 |
| `/api/v1/admin/users/{id}` | PUT | 更新用户信息 |
| `/api/v1/admin/users/{id}` | DELETE | 删除用户 |
| `/api/v1/admin/system/config` | GET | 获取系统配置 |
| `/api/v1/admin/system/config` | POST | 更新系统配置 |
| `/api/v1/admin/system/logs` | GET | 分页获取日志 |
| `/api/v1/admin/dashboard/stats` | GET | 获取仪表盘统计数据 |

### 5.2 场景管理接口详细设计

#### 5.2.1 获取场景列表

**请求**：
```
GET /api/v1/admin/scenes?page=1&limit=20&isActive=true&keyword=
```

**响应**：
```json
{
  "success": true,
  "data": {
    "list": [
      {
        "id": 1,
        "sceneCode": "tariff_filing_publicity",
        "sceneName": "资费备案公示",
        "description": "资费备案公示表单场景",
        "keywords": ["资费备案", "资费", "备案"],
        "priority": 10,
        "isActive": true,
        "intentType": "tariff_filing",
        "formCode": "tariff_filing_publicity",
        "version": 1,
        "createdAt": "2024-01-01T10:00:00",
        "updatedAt": "2024-01-01T10:00:00"
      }
    ],
    "total": 12,
    "page": 1,
    "limit": 20
  }
}
```

#### 5.2.2 创建场景

**请求**：
```
POST /api/v1/admin/scenes
Content-Type: application/json

{
  "sceneCode": "leave_application",
  "sceneName": "请假申请",
  "description": "员工请假申请场景",
  "keywords": ["请假", "休假", "事假", "病假", "年假"],
  "priority": 15,
  "isActive": true,
  "intentType": "form",
  "formCode": "leave_application",
  "llmPrompt": null,
  "fallbackScene": "generic"
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "id": 13,
    "sceneCode": "leave_application",
    "sceneName": "请假申请",
    "description": "员工请假申请场景",
    "keywords": ["请假", "休假", "事假", "病假", "年假"],
    "priority": 15,
    "isActive": true,
    "intentType": "form",
    "formCode": "leave_application",
    "version": 1,
    "createdAt": "2024-01-01T10:00:00"
  }
}
```

#### 5.2.3 测试场景识别

**请求**：
```
POST /api/v1/admin/scenes/test
Content-Type: application/json

{
  "userInput": "我要请年假"
}
```

**响应**：
```json
{
  "success": true,
  "data": {
    "success": true,
    "sceneCode": "leave_application",
    "sceneName": "请假申请",
    "confidence": 0.95,
    "method": "keyword",
    "matchedKeywords": ["年假"],
    "candidates": [
      {
        "sceneCode": "leave_application",
        "sceneName": "请假申请",
        "confidence": 0.95,
        "method": "keyword"
      },
      {
        "sceneCode": "generic",
        "sceneName": "通用表单",
        "confidence": 0.30,
        "method": "default"
      }
    ]
  }
}
```

#### 5.2.4 获取场景统计

**请求**：
```
GET /api/v1/admin/scenes/stats?startDate=2024-01-01&endDate=2024-01-31
```

**响应**：
```json
{
  "success": true,
  "data": {
    "totalRecognitions": 1250,
    "successRate": 0.92,
    "topScenes": [
      {
        "sceneCode": "tariff_filing_publicity",
        "sceneName": "资费备案公示",
        "count": 450,
        "successRate": 0.95
      },
      {
        "sceneCode": "validation_demo",
        "sceneName": "校验演示",
        "count": 320,
        "successRate": 0.88
      }
    ],
    "dailyTrend": [
      {
        "date": "2024-01-01",
        "count": 50,
        "successRate": 0.94
      }
    ]
  }
}
```

### 5.3 其他接口详细设计

#### 5.2.1 获取会话列表

**请求**：
```
GET /api/v1/admin/sessions?page=1&limit=20&user_id=&start_time=&end_time=
```

**响应**：
```json
{
  "success": true,
  "data": {
    "list": [
      {
        "session_id": "xxx",
        "user_id": "user1",
        "title": "新对话",
        "message_count": 10,
        "created_at": "2024-01-01T10:00:00",
        "updated_at": "2024-01-01T10:30:00"
      }
    ],
    "total": 100,
    "page": 1,
    "limit": 20
  }
}
```

#### 5.2.2 获取仪表盘统计

**请求**：
```
GET /api/v1/admin/dashboard/stats
```

**响应**：
```json
{
  "success": true,
  "data": {
    "today_sessions": 120,
    "active_users": 45,
    "form_submissions": 234,
    "health_status": "healthy",
    "recommendation_hit_rate": 0.78,
    "session_trend": [100, 120, 95, 130, 115, 125, 120],
    "form_usage": [
      {"form_code": "tariff_filing", "count": 80},
      {"form_code": "survey", "count": 60}
    ]
  }
}
```

---

## 六、前端页面设计

### 6.1 页面结构

```
┌─────────────────────────────────────────────────────────┐
│  顶部导航栏                                              │
│  Logo | 导航菜单 | 用户信息                               │
├──────────────┬──────────────────────────────────────────┤
│  侧边菜单    │  主内容区                                  │
│  ├─ 仪表盘   │                                          │
│  ├─ 场景管理 │                                          │
│  ├─ 会话管理 │                                          │
│  ├─ 表单管理 │                                          │
│  ├─ 数据管理 │                                          │
│  ├─ 系统配置 │                                          │
│  └─ 用户管理 │                                          │
└──────────────┴──────────────────────────────────────────┘
```

### 6.2 页面列表

| 页面路径 | 组件 | 功能 |
|----------|------|------|
| `/admin/dashboard` | Dashboard.vue | 仪表盘 |
| `/admin/scenes` | SceneList.vue | 场景列表 |
| `/admin/scenes/create` | SceneEditor.vue | 创建场景 |
| `/admin/scenes/:code/edit` | SceneEditor.vue | 编辑场景 |
| `/admin/scenes/test` | SceneTest.vue | 场景识别测试 |
| `/admin/sessions` | SessionList.vue | 会话列表 |
| `/admin/sessions/:id` | SessionDetail.vue | 会话详情 |
| `/admin/forms` | FormList.vue | 表单列表 |
| `/admin/forms/create` | FormEditor.vue | 表单创建 |
| `/admin/forms/:code/edit` | FormEditor.vue | 表单编辑 |
| `/admin/data` | DataManager.vue | 数据管理 |
| `/admin/system` | SystemConfig.vue | 系统配置 |
| `/admin/users` | UserList.vue | 用户列表 |
| `/admin/users/create` | UserEditor.vue | 用户创建 |

### 6.3 组件设计

#### 6.3.1 侧边栏组件 (AdminSidebar.vue)

- 固定左侧，包含导航菜单
- 高亮当前激活菜单项
- 响应式折叠

#### 6.3.2 统计卡片组件 (StatCard.vue)

- 展示单个统计指标
- 支持图标、数值、变化趋势
- 统一样式

#### 6.3.3 表格组件 (BaseTable.vue)

- 支持分页、排序、筛选
- 支持批量操作
- 统一表格样式

---

## 七、安全设计

### 7.1 权限控制

| 角色 | 权限 |
|------|------|
| admin | 全部权限（包括场景管理、用户管理、系统配置） |
| operator | 场景管理、会话管理、表单管理、数据管理 |
| viewer | 只读权限（查看场景、会话、表单、数据） |

#### 场景管理权限说明

- **admin/operator**：可以创建、编辑、删除场景，启用/停用场景，配置场景规则，测试场景识别
- **viewer**：只能查看场景配置和统计信息，不能进行修改操作

### 7.2 API 安全

- 使用 JWT Token 认证
- 接口访问日志记录
- 敏感操作二次确认

---

## 八、场景动作提示词设计

### 8.1 核心理念

场景动作提示词是连接场景识别与能力调度的核心，它定义了大模型在识别到特定场景后应该如何思考、决策和执行操作。

**关键原则**：
1. **职责明确**：每个场景的提示词明确告诉大模型它的角色和目标
2. **流程清晰**：将复杂任务分解为清晰的步骤
3. **容错设计**：考虑异常情况，定义失败时的处理策略
4. **用户体验**：用友好的语言解释操作，让用户理解发生了什么

### 8.2 场景动作配置扩展

#### 8.2.1 扩展的场景配置结构

在原有的场景配置基础上，增加以下字段：

```json
{
  "sceneCode": "tariff_filing_publicity",
  "sceneName": "资费备案公示",
  "description": "资费备案公示表单场景",
  "keywords": ["资费备案", "资费", "备案"],
  "priority": 10,
  "isActive": true,
  "intentType": "tariff_filing",
  "formCode": "tariff_filing_publicity",
  
  "actionType": "form_with_mcp",
  "actionPrompt": "tariff_filing_publicity_prompt.txt",
  "requiredTools": ["query_tariff_by_code"],
  "preActionSteps": [
    {
      "stepType": "extract_param",
      "paramName": "tariff_code",
      "extractionRule": "从用户输入中提取套餐编码，格式为P开头后跟数字"
    },
    {
      "stepType": "call_tool",
      "toolName": "query_tariff_by_code",
      "paramMapping": {
        "tariff_code": "tariff_code"
      },
      "onSuccess": "merge_fields",
      "onFailure": "continue_manual"
    }
  ],
  "postActionSteps": [
    {
      "stepType": "validate",
      "rules": ["check_required_fields", "validate_data_types"]
    },
    {
      "stepType": "recommend",
      "strategy": ["user_history", "frequency", "time_decay"]
    }
  ]
}
```

#### 8.2.2 动作类型（ActionType）

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| form_generation | 标准表单生成 | 简单的表单填写场景，无需复杂工具调用 |
| form_with_mcp | 表单生成+MCP工具调用 | 需要先查询外部数据再填充表单的场景 |
| workflow | 复杂工作流执行 | 多步骤、条件判断的复杂业务流程 |
| chat | 对话式处理 | 需要多轮对话来收集信息的场景 |
| analysis | 数据分析类 | 需要进行数据查询、统计、分析的场景 |
| custom | 自定义处理逻辑 | 完全自定义的处理流程 |

#### 8.2.3 步骤类型（StepType）

| 类型 | 说明 | 参数 |
|------|------|------|
| extract_param | 提取关键参数 | paramName, extractionRule |
| call_tool | 调用MCP工具 | toolName, paramMapping, onSuccess, onFailure |
| validate | 数据验证 | rules |
| recommend | 推荐数据 | strategy |
| condition | 条件判断 | condition, trueStep, falseStep |
| ask_user | 询问用户 | question, fieldName |

### 8.3 场景动作提示词结构

#### 8.3.1 标准结构

每个场景动作提示词文件应包含以下部分：

```
1. 场景概述
   - 场景编码
   - 场景名称
   - 关联表单

2. 核心目标
   - 这个场景要完成什么任务

3. 处理流程
   - 步骤1：理解用户需求
   - 步骤2：提取关键参数
   - 步骤3：调用工具（如需要）
   - 步骤4：表单生成和字段推荐

4. 工具调用规范（如适用）
   - 工具名称
   - 调用条件
   - 参数映射
   - 成功处理
   - 失败处理

5. 注意事项
   - 必填字段检查
   - 参数格式验证
   - 容错设计
   - 用户体验

6. 输出要求
   - 输出格式
   - 必须包含的字段
```

#### 8.3.2 提示词模板

**标准表单生成模板**：
```
你是专业的{场景名称}助手。

## 场景概述
场景编码：{scene_code}
场景名称：{scene_name}
关联表单：{form_code}

## 核心目标
帮助用户完成{场景名称}表单的填写，包括：
1. 准确理解用户的需求
2. 智能提取表单字段
3. 填写完整的表单
4. 提供必要的字段推荐和校验

## 处理流程

### 第一步：理解用户需求
仔细分析用户输入，确定：
- 用户想要填写的表单类型
- 用户已经提供了哪些信息
- 还需要哪些信息需要收集

### 第二步：字段提取
从用户输入中提取表单字段：
- 优先提取本体中定义的字段
- 注意字段类型转换
- 日期格式统一为YYYY-MM-DD格式

### 第三步：表单生成和字段推荐
- 为未填充的必填字段提示用户
- 为所有字段提供基于历史数据的推荐值
- 确保字段校验

## 注意事项

1. **必填字段检查**：缺失时要询问用户
2. **字段类型验证**：确保数据类型正确
3. **用户体验**：用友好的语言解释每一步操作

## 输出要求

请按照标准的 intentType="form" 格式输出JSON结果，
确保包含 formCode="{form_code}"。
```

**带MCP工具调用模板**：
```
你是专业的{场景名称}助手。

## 场景概述
场景编码：{scene_code}
场景名称：{scene_name}
关联表单：{form_code}

## 核心目标
帮助用户完成{场景名称}表单的填写，包括：
1. 准确理解用户的需求
2. 提取关键参数并调用MCP工具查询数据
3. 使用查询结果预填充表单
4. 提供必要的字段推荐和校验

## 处理流程

### 第一步：理解用户需求
仔细分析用户输入，确定：
- 用户想要填写的表单类型
- 用户提供的关键参数信息
- 是否需要调用MCP工具

### 第二步：提取关键参数
从用户输入中提取关键参数：
- 参数名称：{param_name}
- 提取规则：{extraction_rule}
- 如果提取失败，需要询问用户

### 第三步：调用MCP工具
当获取到关键参数后：
1. 调用 `{tool_name}` 工具
2. 参数：{param_mapping}
3. 工具返回的字段会自动填充到表单的 extractedFields 中
4. 成功处理：{on_success}
5. 失败处理：{on_failure}

### 第四步：表单生成和字段推荐
- 如果工具调用成功：使用返回的数据预填充表单
- 如果工具调用失败：{failure_strategy}
- 为未填充的字段提供基于历史数据的推荐值

## 注意事项

1. **必填参数检查**：关键参数缺失时要询问用户
2. **参数格式验证**：确保参数格式正确
3. **容错处理**：工具调用失败时不要中断流程
4. **用户体验**：用友好的语言解释每一步操作

## 输出要求

请按照标准的 intentType="form" 格式输出JSON结果，
确保包含 formCode="{form_code}"，
如果需要调用工具，在 tool_calls 中添加相应的工具调用。
```

### 8.4 实际示例 - 资费备案公示场景

#### 8.4.1 场景提示词文件

```
你是专业的资费备案公示场景助手。

## 场景概述
场景编码：tariff_filing_publicity
场景名称：资费备案公示
关联表单：tariff_filing_publicity

## 核心目标
帮助用户完成资费备案公示表单的填写，包括：
1. 准确理解用户的备案需求
2. 智能提取套餐编码并查询详细信息
3. 填写完整的备案表单
4. 提供必要的字段推荐和校验

## 处理流程

### 第一步：理解用户需求
仔细分析用户输入，确定：
- 用户想要备案的套餐编码
- 是否需要新增备案、修改备案或校验备案
- 用户是否提供了足够的信息

### 第二步：套餐编码提取
从用户输入中提取套餐编码：
- 编码格式：P开头，后跟6位或更多数字
- 示例：P000111、P123456789
- 如果用户没有明确提供编码，但提到了"套餐"、"资费"等词汇，需要询问用户

### 第三步：调用MCP工具查询套餐信息
当获取到套餐编码后：
1. 调用 `query_tariff_by_code` 工具，参数为 {tariff_code: "提取的编码"}
2. 工具返回的字段会自动填充到表单的 extractedFields 中
3. 重点字段包括：
   - bossid: 套餐编码
   - tariff_name: 套餐名称
   - tariff_type: 套餐类型
   - price: 价格
   - description: 描述
   - 等等...

### 第四步：表单生成和字段推荐
- 如果工具调用成功：使用返回的数据预填充表单
- 如果工具调用失败：生成空表单，提示用户手动填写
- 为未填充的字段提供基于历史数据的推荐值

## 注意事项

1. **必填字段检查**：bossid（套餐编码）是必填字段，缺失时要询问用户
2. **编码格式验证**：确保提取的编码符合P+数字格式
3. **容错处理**：工具调用失败时不要中断流程，改为手动填写模式
4. **用户体验**：用友好的语言解释每一步操作，让用户知道发生了什么

## 输出要求

请按照标准的 intentType="form" 格式输出JSON结果，
确保包含 formCode="tariff_filing_publicity"，
如果需要调用工具，在 tool_calls 中添加 query_tariff_by_code 调用。
```

#### 8.4.2 执行流程示例

**用户输入**：
```
我要备案套餐 P000111
```

**大模型识别结果**：
```json
{
  "intentType": "form",
  "formCode": "tariff_filing_publicity",
  "confidence": 0.95,
  "extractedFields": {
    "tariff_code": "P000111"
  },
  "reasoning": "用户明确要求备案套餐P000111，识别为资费备案公示场景",
  "tool_calls": [
    {
      "name": "query_tariff_by_code",
      "arguments": {
        "tariff_code": "P000111"
      }
    }
  ]
}
```

**工具返回结果**：
```json
{
  "bossid": "P000111",
  "tariff_name": "4G飞享套餐18元",
  "tariff_type": "个人套餐",
  "price": 18.00,
  "description": "包含300MB流量，100分钟通话"
}
```

**最终生成的表单**：
- 预填充了套餐基本信息
- 为其他字段提供了历史推荐值
- 提示用户补充缺失的必填字段

### 8.5 提示词管理功能

#### 8.5.1 场景提示词编辑器

在场景管理页面中，增加提示词编辑功能：
- 在线编辑提示词文件
- 实时预览效果
- 版本管理和回滚
- 模板化编辑

#### 8.5.2 提示词测试功能

- 输入测试文本
- 查看大模型的思考过程
- 验证输出结果
- A/B 测试不同提示词版本

#### 8.5.3 提示词分析

- 提示词使用统计
- 效果评估
- 优化建议

### 8.6 目录结构

```
backend/config/prompts/scenes/
├── _FRAMEWORK_README.md          # 框架文档
├── _templates/                   # 模板目录
│   ├── form_generation.txt       # 标准表单生成模板
│   ├── form_with_mcp.txt         # 带MCP调用模板
│   ├── workflow.txt              # 工作流模板
│   └── chat.txt                  # 对话模板
├── tariff_filing_publicity_prompt.txt    # 资费备案场景
├── leave_application_prompt.txt          # 请假申请场景
└── ...
```

---

## 九、部署与集成

### 8.1 前端部署

- 构建命令：`npm run build`
- 静态资源部署到 Nginx
- 反向代理配置

### 8.2 后端部署

- FastAPI 应用使用 Uvicorn 运行
- 支持 Docker 容器化部署
- 配置环境变量

---

## 九、开发计划

### 9.1 开发阶段划分

| 阶段 | 时间 | 任务 |
|------|------|------|
| 第一阶段 | 1-2周 | 后端 API 开发（场景管理、会话管理、用户管理） |
| 第二阶段 | 2-3周 | 前端页面开发（仪表盘、场景管理、会话管理） |
| 第三阶段 | 2-3周 | 前端页面开发（表单管理、数据管理） |
| 第四阶段 | 1-2周 | 系统配置、用户管理页面 |
| 第五阶段 | 1周 | 测试、Bug修复、文档完善 |

### 9.2 任务分解

#### 9.2.1 后端任务

| 任务 | 描述 | 预估工时 |
|------|------|----------|
| Admin API Router | 创建管理员路由 | 4h |
| **Scene Management API** | **场景CRUD、版本管理、测试识别、统计** | **16h** |
| Session Admin API | 会话列表、批量删除 | 8h |
| User Management API | 用户CRUD、角色管理 | 8h |
| Dashboard Stats API | 统计数据聚合（含场景统计） | 10h |
| System Config API | 系统配置管理 | 6h |
| 数据库迁移 | 场景表、用户表、配置表 | 8h |

#### 9.2.2 前端任务

| 任务 | 描述 | 预估工时 |
|------|------|----------|
| AdminLayout 布局 | 侧边栏、导航栏 | 6h |
| Dashboard 页面 | 统计卡片、图表（含场景统计） | 10h |
| **SceneList 页面** | **场景列表、筛选、批量操作** | **8h** |
| **SceneEditor 页面** | **场景配置编辑器** | **12h** |
| **SceneTest 页面** | **场景识别测试工具** | **6h** |
| SessionList 页面 | 会话列表、筛选、批量删除 | 8h |
| SessionDetail 页面 | 消息历史展示 | 6h |
| FormList 页面 | 表单列表、版本管理 | 8h |
| FormEditor 页面 | Schema 可视化编辑器 | 12h |
| DataManager 页面 | 数据导入、分析 | 8h |
| SystemConfig 页面 | 系统参数配置 | 6h |
| UserManager 页面 | 用户列表、编辑 | 6h |

---

## 十、风险与应对

| 风险 | 描述 | 应对措施 |
|------|------|----------|
| Schema 编辑器复杂度 | JSON 可视化编辑较为复杂 | 采用成熟的 JSON 编辑器组件 |
| 性能问题 | 大量会话数据查询慢 | 分页查询、索引优化 |
| 权限漏洞 | 越权访问风险 | 完善权限校验、接口日志记录 |
| 数据安全 | 敏感配置信息泄露 | 配置加密存储、最小权限原则 |

---

## 附录：现有接口清单

### 已存在的 Admin API

| API 路径 | 方法 | 说明 | 文件 |
|----------|------|------|------|
| `/api/v1/chat/history/analyze` | POST | 数据分析 | admin.py |
| `/api/v1/chat/history/import` | POST | 数据导入 | admin.py |
| `/api/v1/chat/history/list` | GET | 数据文件列表 | admin.py |
| `/api/v1/chat/history/{form_code}/summary` | GET | 数据摘要 | admin.py |
| `/api/v1/chat/deploy-config` | POST | 部署表单配置 | admin.py |
| `/api/v1/chat/delete-form` | POST | 删除表单 | admin.py |
| `/api/v1/chat/form-versions/{form_code}` | GET | 表单版本列表 | admin.py |
| `/api/v1/chat/rollback-form` | POST | 版本回退 | admin.py |

### 已存在的 Chat API

| API 路径 | 方法 | 说明 | 文件 |
|----------|------|------|------|
| `/api/v2/chat/sessions` | POST | 创建会话 | chat_v2.py |
| `/api/v2/chat/sessions` | GET | 获取会话列表 | chat_v2.py |
| `/api/v2/chat/sessions/{id}` | DELETE | 删除会话 | chat_v2.py |
| `/api/v2/chat/sessions/{id}/messages` | POST | 保存消息 | chat_v2.py |
| `/api/v2/chat/sessions/{id}/messages` | GET | 获取消息列表 | chat_v2.py |

---

**文档版本**: v1.0  
**创建时间**: 2026-05-09  
**作者**: AI Team  
**审核状态**: 待审核