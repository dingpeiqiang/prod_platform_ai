# prodai-cfg-demo MVP 集成实现计划

## Context

`prodai-cfg-demo` 是一个独立的 Vue 3 demo 应用，演示了"产品智能配置助手"的核心功能：通过聊天窗口（AI智查、AI方案导入、对话式配置三大技能）引导用户完成商品配置，并在右侧面板展示可编辑的配置表单、商品列表管理和智能稽核提交流程。

当前项目 `frontend` 已有成熟的聊天框架（App.vue + ChatAssistant.vue + ChatMessageList.vue + ChatInput.vue + FormPanel.vue + DynamicForm.vue），并已部分集成了 demo 的欢迎卡片（DashboardHome.vue 中已有 AI智查、AI方案导入两个入口）。

**目标**：将 demo 的完整功能以 MVP 形式集成到现有聊天框架中，复用现有 DynamicForm 渲染产品配置表单，使用 mock 数据驱动，最后删除独立的 demo 项目。

## 决策

- **表单方案**：复用现有 DynamicForm（schema 驱动），将 demo 的 4-tab ConfigForm 扁平化为单表单 schema
- **功能范围**：三大技能卡片 + 商品列表管理 + 智能稽核提交流程 + 文件上传解析
- **数据来源**：前端 mock 数据，不依赖后端

## 实现步骤

### 1. 创建 mock 数据与表单 schema 生成器

**新建** `frontend/src/data/productMockData.js`

移植 demo 的 `prodai-cfg-demo/src/data/mockData.js`，并新增 `createProductFormSchema(formData)` 函数，将 demo 的 ConfigForm 字段转换为 DynamicForm 兼容的 schema：

```js
// schema 结构示例
{
  formName: '产品配置',
  formCode: 'productConfig',
  fields: [
    { fieldCode: 'workOrderId', fieldName: '工单编号', fieldType: 'input', required: false },
    { fieldCode: 'prodPrcName', fieldName: '资费名称', fieldType: 'input', required: true },
    { fieldCode: 'effRuleId', fieldName: '订购生效方式', fieldType: 'select', options: [...] },
    { fieldCode: 'effDate', fieldName: '销售开始日期', fieldType: 'date' },
    { fieldCode: 'monthlyFee', fieldName: '套餐固定费', fieldType: 'number', required: true },
    // ... 其余字段
  ]
}
```

包含：
- `mockProducts`（历史商品，2条）
- `scene2Products`（AI导入场景商品，3条）
- `createEmptyFormData()`（空表单数据）
- `createProductFormSchema(formData?)`（生成 DynamicForm schema）
- `SKILL_CONFIG`（三大技能配置：query/file/chat）

### 2. 创建产品配置状态管理 composable

**新建** `frontend/src/composables/useProductConfig.js`

参考 demo 的 `useAppState.js`，适配现有项目的消息流模式。管理：
- `products`（商品列表）
- `currentProductId`（当前编辑商品）
- `formData`（当前表单数据）
- `auditStatus`（稽核状态：pending/pass/fail）

核心方法：
- `handleSkillCard(type)` - 处理技能卡片点击（query/file/chat）
- `simulateQuery(keyword)` - 模拟历史商品查询，返回商品卡片消息
- `handleFileUpload(file)` - 模拟文件解析流程
- `prepareProduct(index)` - 从历史商品复制配置
- `generateProductFromChat(text)` - 对话式生成商品配置
- `saveDraft()` / `runAudit()` / `submitConfig()` - 稽核提交流程
- `buildProductFormCard(product)` - 构建兼容现有 FormPanel 的 formCard 对象

关键适配：将 demo 的 HTML 消息转换为现有项目的消息对象格式（`{ id, role, content, done, type, formCard? }`），formCard 结构复用现有 `activeFormCard` 格式。

### 3. 创建商品列表面板组件

**新建** `frontend/src/components/ProductListPanel.vue`

移植 demo 的 `SlidePanel.vue` + `ProductListItems.vue`，使用 Element Plus 的 `ElDrawer` 替代 demo 的自定义 SlidePanel。功能：
- 商品列表展示（名称、描述、状态标签）
- 复制 / 编辑 / 删除操作按钮
- 空状态提示

Props: `products`, `currentProductId`
Emits: `select`, `copy`, `edit`, `delete`, `close`

### 4. 创建智能稽核面板组件

**新建** `frontend/src/components/AuditPanel.vue`

移植 demo 的稽核流程，使用 Element Plus 组件。功能：
- 稽核进度动画（4个阶段：完整性校验 → 业务规则 → 短信合规 → 结果汇总）
- 稽核结果展示（error/warning/success 分级列表）
- 通过后显示"提交成功"状态

Props: `visible`, `formData`, `productName`
Emits: `close`, `audit-complete`（传递稽核结果）

### 5. 修改 DashboardHome 添加"对话式配置"卡片

**修改** `frontend/src/components/DashboardHome.vue`

在 `welcome-cards-grid` 中新增第三个卡片"对话式配置"：
- 图标：对话气泡
- 标题：对话式配置
- 描述：自然语言描述，智能生成配置
- 点击触发 `handleWelcomeCard('chat')` → emit `send-message` '我想通过对话配置商品'

将 grid 从 2 列改为 3 列（`grid-template-columns: repeat(3, 1fr)`），移动端保持 1 列。

### 6. 修改 ChatInput 添加 chat 技能

**修改** `frontend/src/components/ChatInput.vue`

在 `skillConfig` 对象中新增 chat 技能：
```js
const skillConfig = {
  query: { icon: 'fa-magnifying-glass', label: 'AI智查' },
  file: { icon: 'fa-file-import', label: 'AI方案导入' },
  chat: { icon: 'fa-comments', label: '对话式配置' }  // 新增
}
```

在 `quickActions` 数组中新增"对话配置"快捷操作。

### 7. 在 ChatAssistant 中集成产品配置流程

**修改** `frontend/src/components/ChatAssistant.vue`

这是核心集成点。需要：

1. **导入** useProductConfig composable 和相关组件
2. **拦截技能卡片消息**：在 `handleWelcomeCardClick` 中增加 `chat` 类型处理
3. **拦截特定消息模式**：在 `sendMessage` / `doSendMessage` 流程中，检测是否为产品配置场景（关键词：查询/智查/导入/大学生/校园/套餐等），如果是则走 mock 流程而非调用后端 API
4. **注入 mock 响应**：当检测到产品配置场景时，使用 `useProductConfig` 生成 mock 响应消息（查询结果卡片、配置生成成功等），并构建 formCard 供 FormPanel 渲染
5. **商品列表入口**：在 chat-topbar 添加"商品列表"按钮，点击打开 ProductListPanel
6. **稽核入口**：当 formCard 状态为 filling 时，在 FormPanel 底部添加"智能稽核"按钮，点击打开 AuditPanel

具体集成方式：
- 在 `sendMessage` 函数开头，调用 `useProductConfig.handleUserMessage(text)` 检测是否命中产品配置场景
- 如果命中，走 mock 流程：push 用户消息 → 模拟 AI 多步响应 → 构建 formCard → 触发 FormPanel
- 如果未命中，走原有后端 API 流程

### 8. 删除 prodai-cfg-demo

完成上述集成后，删除整个 `prodai-cfg-demo` 目录：
- `prodai-cfg-demo/src/`（所有源码）
- `prodai-cfg-demo/dist/`（构建产物）
- `prodai-cfg-demo/index.html`、`index_v31.html`
- `prodai-cfg-demo/package.json`、`package-lock.json`
- `prodai-cfg-demo/vite.config.js`、`.gitignore`

## 关键文件清单

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 新建 | `frontend/src/data/productMockData.js` | mock 数据 + schema 生成器 |
| 新建 | `frontend/src/composables/useProductConfig.js` | 产品配置状态管理 |
| 新建 | `frontend/src/components/ProductListPanel.vue` | 商品列表面板 |
| 新建 | `frontend/src/components/AuditPanel.vue` | 智能稽核面板 |
| 修改 | `frontend/src/components/DashboardHome.vue` | 添加"对话式配置"卡片 |
| 修改 | `frontend/src/components/ChatInput.vue` | 添加 chat 技能标签 |
| 修改 | `frontend/src/components/ChatAssistant.vue` | 集成产品配置 mock 流程 |
| 删除 | `prodai-cfg-demo/`（整个目录） | 清理独立 demo |

## 复用的现有代码

- `frontend/src/components/DynamicForm.vue` - 表单渲染（schema 驱动）
- `frontend/src/components/FormPanel.vue` - 右侧表单面板容器
- `frontend/src/composables/useFormHandling.js` - 表单状态管理（activeFormCard 机制）
- `frontend/src/components/ChatMessageList.vue` - 消息列表渲染（已支持 formCard）
- `frontend/src/utils/chatUtils.js` - `genId()` 等工具函数
- `frontend/src/components/fields/BaseField.vue` - 字段渲染（input/number/date/select/textarea）

## 验证方式

1. **启动前端**：`cd frontend && npm run dev`
2. **验证三大技能卡片**：
   - 首页应显示 3 个卡片：AI智查、AI方案导入、对话式配置
   - 点击"AI智查"→ 输入关键词 → 应返回 2 个历史商品卡片
   - 点击"AI方案导入"→ 上传文件 → 应模拟解析并生成 3 个商品
   - 点击"对话式配置"→ 输入"我要一个大学生套餐"→ 应生成配置并打开表单
3. **验证商品列表**：点击顶部"商品列表"按钮 → 应弹出面板显示所有商品
4. **验证表单编辑**：点击商品"编辑"→ 右侧 FormPanel 应显示 DynamicForm 表单
5. **验证智能稽核**：填写表单 → 点击"智能稽核"→ 应显示进度动画和校验结果
6. **验证 demo 已删除**：确认 `prodai-cfg-demo/` 目录不存在，项目正常构建
