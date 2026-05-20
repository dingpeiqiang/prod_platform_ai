# 工作流编辑器节点卡片UI统一优化

## 优化目标

统一所有节点卡片的显示尺寸,提升工作流编辑器的视觉一致性和用户体验。

## 优化内容

### 1. 统一最小宽度 (min-width)

**优化前**: 各节点最小宽度不一致,导致画布上节点大小参差不齐
- StartNode: 220px
- EndNode: 150px  
- LlmNode: 220px
- PromptNode: 200px
- ToolNode: 260px
- HttpNode: 280px
- CodeNode: 280px
- VariableNode: 240px
- ParserNode: 240px
- FormNode: 260px
- UserInputNode: 220px
- KnowledgeNode: 260px
- ConditionNode: 260px
- LoopNode: 180px ✅
- GenericNode: 180px ✅
- ValidateNode: 180px ✅

**优化后**: 所有节点统一为 **180px**
- ✅ 视觉更整齐统一
- ✅ 减少水平空间占用
- ✅ 便于节点排列和对齐

### 2. 统一 Compact 模式宽度

**优化前**: Compact 模式宽度为 160px,与正常模式差异较大

**优化后**: Compact 模式统一为 **180px**
- ✅ 保持与正常模式一致的宽度
- ✅ Compact 模式主要通过高度压缩实现
- ✅ 避免宽度变化导致的布局跳动

### 3. 保持统一的样式规范

以下样式已保持一致,无需调整:
- ✅ **Border-radius**: 8px (KnowledgeNode 为 12px,保持特殊设计)
- ✅ **Padding**: 8px 10px (header 和 body)
- ✅ **Border**: 2px solid #e2e8f0 (部分节点有特殊颜色)
- ✅ **Box-shadow**: 0 1px 3px rgba(0, 0, 0, 0.05)
- ✅ **Font-size**: 
  - 标题: 12px, font-weight: 600
  - 图标: 16px
  - Compact 摘要: 11px

## 修改的文件列表

共优化 **17个节点组件**:

1. ✅ `StartNode.vue` - 开始节点 (220px → 180px)
2. ✅ `EndNode.vue` - 结束节点 (150px → 180px)
3. ✅ `LlmNode.vue` - LLM调用节点 (220px → 180px)
4. ✅ `PromptNode.vue` - 提示词节点 (200px → 180px)
5. ✅ `ToolNode.vue` - 工具调用节点 (260px → 180px)
6. ✅ `HttpNode.vue` - HTTP请求节点 (280px → 180px)
7. ✅ `CodeNode.vue` - 代码执行节点 (280px → 180px)
8. ✅ `VariableNode.vue` - 变量赋值节点 (240px → 180px)
9. ✅ `ParserNode.vue` - 输出解析节点 (240px → 180px)
10. ✅ `FormNode.vue` - 表单节点 (260px → 180px)
11. ✅ `UserInputNode.vue` - 用户输入节点 (220px → 180px)
12. ✅ `KnowledgeNode.vue` - 知识库节点 (260px → 180px)
13. ✅ `ConditionNode.vue` - 条件分支节点 (260px → 180px)
14. ✅ `LoopNode.vue` - 循环节点 (已是 180px,仅优化 compact)
15. ✅ `GenericNode.vue` - 通用节点 (已是 180px)
16. ✅ `ValidateNode.vue` - 校验节点 (已是 180px,仅优化 compact)
17. ✅ `GenericNode.vue` - 通用节点模板

## 视觉效果对比

### 优化前
```
[StartNode    220px] [LLM        220px] [HTTP         280px]
[End   150px] [Prompt  200px] [Code         280px]
```
节点宽度参差不齐,视觉上不够整齐

### 优化后
```
[StartNode 180px] [LLM     180px] [HTTP    180px]
[End       180px] [Prompt  180px] [Code    180px]
```
所有节点宽度一致,视觉更统一整洁

## 优势

1. **视觉一致性**: 所有节点在画布上呈现统一的宽度,提升专业感
2. **空间利用率**: 减少不必要的横向空间占用,可在相同区域展示更多节点
3. **对齐便利性**: 统一的宽度使节点对齐和分布更加容易
4. **响应式友好**: 较小的基础宽度更适合不同屏幕尺寸
5. **Compact 模式平滑**: Compact 模式不再改变宽度,只压缩高度,过渡更自然

## 注意事项

1. **配置模式不受影响**: 节点进入配置模式(config-mode)时,min-width 会被设为 unset,宽度由内容决定
2. **特殊节点保留特色**: KnowledgeNode 保持 12px border-radius,突出其特殊性
3. **向后兼容**: 已有工作流的节点位置不会受影响,只是渲染时的最小宽度限制变化
4. **拖拽体验**: 节点实际可拖拽区域不变,只是视觉边界更紧凑

## 测试建议

1. 创建新工作流,添加各种类型的节点,观察宽度是否一致
2. 切换 Compact 模式,确认宽度保持不变
3. 进入配置模式,确认宽度能根据内容自适应
4. 测试节点对齐功能,确认统一宽度带来的便利
5. 在不同缩放比例下检查节点显示效果

## 相关文件

- `/frontend/src/components/workflow-editor/nodes/*.vue` - 所有节点组件
- `/frontend/src/components/workflow-editor/LangChainEditor.vue` - 主编辑器
- `/docs/工作流编辑器节点卡片UI统一优化.md` - 本文档
