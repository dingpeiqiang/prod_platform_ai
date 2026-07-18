# prodai-cfg-demo MVP 集成（续接）

## 背景与现状

用户原始请求：「分析 prodai-cfg-demo，在当前项目的聊天窗口框架中实现 MVP 版本，最后删除 prodai-cfg-demo」。

### 已完成（来自上一会话，代码已落盘）

| # | 文件 | 状态 |
|---|------|------|
| 1 | [frontend/src/data/productMockData.js](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/data/productMockData.js) | ✅ 已创建（mockProducts / scene2Products / SKILL_CONFIG / createEmptyFormData / CAMPUS_PRODUCT_DATA / createProductFormSchema） |
| 2 | [frontend/src/composables/useProductConfig.js](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/composables/useProductConfig.js) | ✅ 已创建（detectScenario / simulateQuery / prepareProduct / simulateFileParse / generateProductFromChat / selectProduct / copyProduct / deleteProduct / saveDraft / runAudit / buildProductFormCard） |
| 3 | [frontend/src/components/ProductListPanel.vue](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/ProductListPanel.vue) | ✅ 已创建（ElDrawer 抽屉，商品列表 + 复制/编辑/删除） |

### 待完成（本计划范围）

5 个任务：创建 AuditPanel → 改造 DashboardHome → 改造 ChatInput → 改造 ChatAssistant + ChatMessageList → 删除 prodai-cfg-demo。

## 探索结论（决策依据）

1. **FormPanel 复用确认**：[FormPanel.vue](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/FormPanel.vue) 接收 `formSchema` 即可渲染 DynamicForm；`buildProductFormCard()` 已生成兼容的 schema，无需改造 FormPanel。
2. **ChatMessageList 缺口**：[ChatMessageList.vue](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/ChatMessageList.vue) 当前不渲染 `msg.queryResults`，需要在 formCard 块之后追加一个 query-results 块以展示历史商品卡片。
3. **ChatInput skillConfig 缺口**：[ChatInput.vue#L184-L187](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/ChatInput.vue#L184-L187) 只注册了 `query`、`file` 两个技能；需补 `chat`。
4. **DashboardHome 缺口**：[DashboardHome.vue#L15-L38](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/DashboardHome.vue#L15-L38) 只有两张欢迎卡（query/file）；缺第三张「对话式配置」。
5. **ChatAssistant 集成点**：[ChatAssistant.vue#L350-L373](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/frontend/src/components/ChatAssistant.vue#L350-L373) 的 `sendMessage` 是拦截产品配置场景的入口；`handleWelcomeCardClick`（L336-L349）当前只处理 query/file。
6. **demo 稽核流程参考**：[prodai-cfg-demo/src/composables/useAppState.js#L500-L530](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/prodai-cfg-demo/src/composables/useAppState.js#L500-L530) 的 `runAudit` 用 4 阶段定时器（1.5s/3s/4.5s/6s）模拟稽核进度，结果以 HTML 字符串渲染。本项目改用 Vue 组件实现。

## 假设与决策

- **稽核面板形态**：用 `ElDrawer`（rtl, 420px），与 ProductListPanel 风格统一；不混入 FormPanel。
- **稽核触发位置**：在 ChatAssistant 的 `chat-topbar` 增加「智能稽核」按钮，仅当 `activeFormCard` 存在且 `currentProduct` 存在时可点。
- **商品列表入口**：同样在 `chat-topbar` 增加「商品列表」按钮，显示数量徽标。
- **queryResults 渲染**：在 ChatMessageList 追加最小渲染块——商品名 + 描述 + "复制配置" 按钮，点击 emit `query-result-click` 事件给父组件；不引入新依赖。
- **场景拦截策略**：在 `sendMessage` 入口先调用 `detectScenario(text)`；命中则走 mock 流程，不调用后端 SSE。文件上传走 `attachments` 检测，命中 .docx/.pdf/.xlsx/.doc 时触发 `simulateFileParse`。
- **mock 数据生命周期**：`useProductConfig` 在 ChatAssistant 内实例化一次，切换会话时清空 `products`。
- **不持久化产品 mock 状态**：刷新即清空，符合 MVP。
- **AGENTS.md 规范**：JS 2 空格缩进、语句末尾加分号、camelCase、JSON snake_case；不写多余注释。

## 实施步骤

### 步骤 1：新建 `frontend/src/components/AuditPanel.vue`

**功能**：智能稽核抽屉。

- 使用 `ElDrawer`（rtl, 420px）。
- Props: `modelValue: Boolean`、`productName: String`、`results: Array`、`phase: String`（'idle' | 'progress' | 'results'）、`hasError: Boolean`。
- Emits: `update:modelValue`、`close`、`audit-complete`。
- 阶段进度（phase === 'progress'）：4 步定时器（1500ms 间隔），文案依次为「正在执行智能稽核...」→「基础信息完整性校验」→「业务规则校验」→「短信模板合规性检查」→「结果汇总」。父组件控制 phase 切换。
- 结果列表（phase === 'results'）：按 `type` 分级渲染（error/warning/success），每条显示 title + desc。
- 通过时显示「✅ 配置提交成功」横幅 + 关闭按钮；失败时显示「❌ 请修正上述错误项后重新提交」。
- 样式参考 demo 的 `.audit-item` 配色（红/黄/绿）。

### 步骤 2：改造 `frontend/src/components/DashboardHome.vue`

**改动点**：

- L15-L38 的 `welcome-cards-grid` 增加「对话式配置」第三张卡：
  - icon: 对话气泡 svg
  - h4: "对话式配置"
  - p: "自然语言描述需求，AI 生成配置"
  - click: `handleWelcomeCard('chat')`
- L316-L327 的 `handleWelcomeCard` 增加 `case 'chat'`：`text = '我要配置一个大学生套餐'`。
- L445-L449 的 `.welcome-cards-grid` 改 `grid-template-columns: repeat(3, 1fr)`；移动端 `@media (max-width: 768px)` 保留 1 列。

### 步骤 3：改造 `frontend/src/components/ChatInput.vue`

**改动点**：

- L184-L187 的 `skillConfig` 增加 `chat: { icon: 'fa-comments', label: '对话式配置' }`。
- L208-L211 的 `quickActions` 增加 `{ key: 'chat', label: '对话配置', content: '帮我配置一个大学生套餐', color: '#8b5cf6' }`。

### 步骤 4：改造 `frontend/src/components/ChatMessageList.vue`

**改动点**：在 L148 的 `</div>` （form-card 块结尾）之后，追加 query-results 渲染块：

```vue
<!-- 查询结果卡片列表 -->
<div v-if="msg.queryResults?.length" class="query-results">
  <div
    v-for="p in msg.queryResults"
    :key="p.id"
    class="query-result-item"
    @click="$emit('query-result-click', p)"
  >
    <div class="qr-header">
      <span class="qr-name">{{ p.name }}</span>
      <span class="qr-code">{{ p.code }}</span>
    </div>
    <p class="qr-desc">{{ p.desc }}</p>
    <button type="button" class="qr-copy-btn">复制配置</button>
  </div>
</div>
```

- 在 `defineEmits` 数组中追加 `'query-result-click'`。
- 在 `<style scoped>` 中追加 `.query-results` / `.query-result-item` / `.qr-*` 样式（卡片式，hover 高亮，复制按钮右下角）。

### 步骤 5：改造 `frontend/src/components/ChatAssistant.vue`（核心集成）

**改动点**：

1. **import 与实例化**（顶部 script）：
   - `import { useProductConfig } from '../composables/useProductConfig.js'`
   - `import ProductListPanel from './ProductListPanel.vue'`
   - `import AuditPanel from './AuditPanel.vue'`
   - 实例化：`const productConfig = useProductConfig()`，解构 `products, currentProduct, showProductListPanel, showAuditPanel, auditResults, auditPhase, detectScenario, simulateQuery, prepareProduct, simulateFileParse, generateProductFromChat, selectProduct, copyProduct, deleteProduct, saveDraft, runAudit`。

2. **topbar 增加两个按钮**（L4-L13 的 `.topbar-actions`）：
   - 商品列表按钮：显示 `products.length` 徽标，点击 `showProductListPanel = true`。
   - 智能稽核按钮：仅当 `activeFormCard && currentProduct` 时启用，点击 `handleRunAudit`。

3. **ChatMessageList 增加事件绑定**（L19-L24）：
   - 追加 `@query-result-click="handleQueryResultClick"`。

4. ** sendMessage 拦截逻辑**（L350-L373 改造）：
   - 在 `if (!text && ...) return;` 之后、`if (!props.sessionId)` 之前，插入：
     ```js
     const scenario = detectScenario(text)
     if (scenario) {
       await handleProductScenario(scenario, text, attachments)
       return
     }
     if (attachments && attachments.length > 0) {
       const doc = attachments.find(a => /\.(docx|pdf|xlsx|doc)$/i.test(a.name || ''))
       if (doc) {
         await handleFileParseScenario(doc)
         return
       }
     }
     ```
   - 不命中场景才走原有 `doSendMessage` 流程。

5. **新增方法**：
   - `handleProductScenario(scenario, text, attachments)`：
     - push 用户消息到 `messages`，调 `ensureDbSession` 保存。
     - 根据 scenario 调用 `simulateQuery(text)` / `generateProductFromChat(text)`。
     - 把返回的 messages 数组逐条 push 到 `messages`，并保存到 DB。
     - 若返回 `formCard`，设置 `activeFormCard.value = formCard`、`currentFormId.value = formCard.formId`、`currentFormSchema.value = formCard.formSchema`、`activeFormMsgId.value = formCard.msgId`。
     - `simulateQuery` 场景只 push 消息（含 queryResults），不打开 FormPanel。
   - `handleFileParseScenario(doc)`：
     - push 用户消息「上传了文件：{doc.name}」。
     - 调 `simulateFileParse(doc.name, doc.size)`，逐条 push 消息，最后设置 `activeFormCard`。
   - `handleQueryResultClick(product)`：调 `prepareProduct(index)`（index 通过 product.id 在 mockProducts 中查找），push 返回消息，设置 `activeFormCard`。
   - `handleProductSelect(id)` / `handleProductCopy(id)` / `handleProductEdit(id)` / `handleProductDelete(id)`：调用 useProductConfig 对应方法；删除时若有返回的 formCard 则更新 `activeFormCard`。
   - `handleRunAudit()`：
     - 调 `saveDraft()` 同步表单到当前商品。
     - `showAuditPanel.value = true`、`auditPhase.value = 'progress'`。
     - 用 setTimeout 链模拟 4 阶段进度（1500ms/3000ms/4500ms/6000ms），每阶段更新一个本地 `auditProgressText` ref。
     - 6000ms 时调 `runAudit()`，把 `auditPhase` 设为 `'results'`。
     - 通过时 push 一条成功消息到 `messages`，并清空 `activeFormCard`。
   - `handleAuditClose()`：`showAuditPanel.value = false`。

6. **FormPanel 的 field-change 同步**（L46 的 `@field-change`）：当前调用 `handleFormFieldChange`（来自 useFormHandling）；追加调用 `productConfig.updateFormField(fieldCode, value)` 同步到 currentProduct.data。改造为：
   ```js
   @field-change="(code, val) => { handleFormFieldChange(code, val); productConfig.updateFormField(code, val) }"
   ```

7. **模板追加抽屉**（在 `</div>` 闭合 chat-layout 前）：
   ```vue
   <ProductListPanel
     v-model="productConfig.showProductListPanel.value"
     :products="productConfig.products.value"
     :current-product-id="productConfig.currentProductId.value"
     @select="handleProductSelect"
     @copy="handleProductCopy"
     @edit="handleProductSelect"
     @delete="handleProductDelete"
   />
   <AuditPanel
     v-model="productConfig.showAuditPanel.value"
     :product-name="currentProduct?.name || ''"
     :results="auditResults"
     :phase="auditPhase"
     :has-error="auditResults.some(r => r.type === 'error')"
     @close="handleAuditClose"
   />
   ```

8. **会话切换时清空产品状态**（L149 watch sessionId 内）：在 `messages.value = []` 后追加 `productConfig.products.value = []`、`productConfig.currentProductId.value = null`。

### 步骤 6：删除 `prodai-cfg-demo/` 目录

- 使用 PowerShell `Remove-Item -Recurse -Force prodai-cfg-demo` 删除整个目录。
- 删除前确认无其他引用（已通过 Glob 确认 prodai-cfg-demo 是独立目录）。

## 验证步骤

1. **启动前端**：在 `frontend/` 下执行 `npm run dev`，确认无编译错误。
2. **首页欢迎卡**：刷新首页，应看到 3 张欢迎卡（AI智查 / AI方案导入 / 对话式配置）。
3. **AI智查流程**：点「AI智查」→ 输入「动感地带」→ 应看到查询结果卡片，点击「复制配置」应打开 FormPanel 并填充表单。
4. **AI方案导入流程**：点附件上传一个 .docx 文件 → 应看到 4 条解析消息，最后自动打开 FormPanel 显示第一个商品。
5. **对话式配置流程**：点「对话式配置」→ 输入「我要一个大学生套餐」→ 应看到 3 条生成消息，最后自动打开 FormPanel。
6. **商品列表**：点击 topbar 商品列表按钮 → 抽屉应显示已创建的商品，复制/编辑/删除按钮可用。
7. **智能稽核**：在 FormPanel 修改字段后点击 topbar 智能稽核按钮 → 应看到 4 阶段进度动画 → 最终显示稽核结果（通过/失败）。
8. **删除验证**：确认 `prodai-cfg-demo/` 目录已不存在。
9. **git 提交**：按 AGENTS.md 规则，任务完成后执行 `git add -A` → `git commit -m "feat: 在聊天框架中集成 prodai-cfg-demo MVP 版本"` → `git push`。

## 风险与回滚

- **风险 1**：ChatAssistant 集成面较大，可能引入回归。
  - 缓解：拦截逻辑放在 `sendMessage` 最前面，未命中场景时完全走原有流程，不影响现有功能。
- **风险 2**：mock 数据在会话切换时未清空可能导致跨会话污染。
  - 缓解：在 watch sessionId 中显式清空 productConfig 状态。
- **回滚**：所有改动均在新文件或追加式改动，回滚只需 git revert 单次提交。
