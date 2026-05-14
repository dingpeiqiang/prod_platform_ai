# Vue Flow 工作流编辑器迁移指南

## 概述

我们已经创建了基于 `@vue-flow` 的新版本工作流编辑器，相比原有的自定义实现，具有以下优势：

### ✨ 主要优势

1. **专业的流程图引擎**
   - 内置拖拽、缩放、平移功能
   - 自动连线对齐和路由
   - 高性能渲染（基于 SVG）

2. **更好的用户体验**
   - 流畅的节点拖拽
   - 智能的连接点吸附
   - 内置迷你地图和控制按钮

3. **更易维护的代码**
   - 标准化的 API
   - 丰富的插件生态
   - 活跃的社区支持

4. **更多高级功能**
   - 节点分组
   - 自定义边样式
   - 动画效果
   - 撤销/重做支持

## 文件结构

```
frontend/src/components/intent-panels/
├── LangChainEditorVueFlow.vue      # 主编辑器组件（新版）
├── LangChainEditor.vue              # 原编辑器组件（保留）
└── nodes/                           # 自定义节点组件
    ├── StartNode.vue                # 开始节点
    ├── EndNode.vue                  # 结束节点
    ├── PromptNode.vue               # 提示词节点
    ├── LlmNode.vue                  # LLM调用节点
    ├── ToolNode.vue                 # 工具调用节点
    ├── ConditionNode.vue            # 条件分支节点
    ├── LoopNode.vue                 # 循环节点
    ├── VariableNode.vue             # 变量节点
    ├── HttpNode.vue                 # HTTP请求节点
    ├── CodeNode.vue                 # 代码执行节点
    ├── ParserNode.vue               # 输出解析节点
    └── GenericNode.vue              # 通用节点基类
```

## 快速开始

### 1. 在路由中切换编辑器

找到使用 `LangChainEditor` 的地方，替换为 `LangChainEditorVueFlow`：

```javascript
// 原来
import LangChainEditor from './components/intent-panels/LangChainEditor.vue';

// 改为
import LangChainEditorVueFlow from './components/intent-panels/LangChainEditorVueFlow.vue';
```

### 2. 测试基本功能

启动前端开发服务器：

```bash
cd frontend
npm run dev
```

访问工作流编辑器页面，测试以下功能：
- ✅ 从左侧面板拖拽节点到画布
- ✅ 连接节点（从一个节点的右侧拖到另一个节点的左侧）
- ✅ 拖拽移动节点
- ✅ 缩放画布（鼠标滚轮）
- ✅ 平移画布（按住空格+拖拽）
- ✅ 点击节点查看属性
- ✅ 保存/导出工作流

## 核心概念

### Vue Flow 三要素

1. **Nodes（节点）**
   ```javascript
   {
     id: 'node-1',
     type: 'llm',           // 节点类型
     position: { x: 100, y: 100 },  // 位置
     data: { label: 'LLM', model: 'gpt-4' }  // 自定义数据
   }
   ```

2. **Edges（边/连线）**
   ```javascript
   {
     id: 'edge-1',
     source: 'node-1',      // 源节点ID
     target: 'node-2',      // 目标节点ID
     sourceHandle: 'source-1',  // 源连接点（可选）
     targetHandle: 'target-1'   // 目标连接点（可选）
   }
   ```

3. **Elements（元素集合）**
   ```javascript
   const elements = ref([...nodes, ...edges]);
   ```

### Handle（连接点）

每个节点可以定义输入和输出连接点：

```vue
<Handle type="target" :position="Position.Left" />   <!-- 输入 -->
<Handle type="source" :position="Position.Right" />  <!-- 输出 -->
```

位置选项：`Position.Top`, `Position.Right`, `Position.Bottom`, `Position.Left`

## 自定义节点开发

### 基础模板

```vue
<template>
  <div class="node custom-node" :class="{ selected }">
    <div class="node-header">
      <span>{{ data.label }}</span>
    </div>
    <div class="node-body">
      <!-- 节点内容 -->
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core';

defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});
</script>
```

### 带交互的节点

```vue
<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

const emit = defineEmits(['update']);

// 本地状态
const localValue = ref(props.data.value || '');

// 更新父组件
const emitUpdate = () => {
  emit('update', props.data.id, { value: localValue.value });
};

// 监听数据变化
watch(() => props.data, (newData) => {
  localValue.value = newData.value || '';
}, { deep: true });
</script>
```

## API 参考

### useVueFlow Hook

```javascript
import { useVueFlow } from '@vue-flow/core';

const {
  addNodes,        // 添加节点
  addEdges,        // 添加边
  removeNodes,     // 删除节点
  removeEdges,     // 删除边
  updateNodeData,  // 更新节点数据
  project,         // 坐标转换
  viewport         // 视口控制
} = useVueFlow();
```

### VueFlow 组件事件

```vue
<VueFlow
  @connect="onConnect"           // 连接创建时
  @node-drag-stop="onDragStop"   // 节点拖拽结束
  @pane-click="onPaneClick"      // 画布点击
  @move="onMove"                 // 视口移动
>
```

## 迁移检查清单

### 功能对比

| 功能 | 原版 | Vue Flow版 | 状态 |
|------|------|-----------|------|
| 节点拖拽 | ✅ | ✅ | 完成 |
| 节点连接 | ✅ | ✅ | 完成 |
| 画布缩放 | ✅ | ✅ | 完成 |
| 画布平移 | ❌ | ✅ | 新增 |
| 撤销/重做 | ⚠️ 基础 | ❌ 待实现 | 待完善 |
| 节点属性编辑 | ✅ | ✅ | 完成 |
| 工作流保存 | ✅ | ✅ | 完成 |
| 工作流导出 | ✅ | ✅ | 完成 |
| 工作流导入 | ✅ | ⚠️ 简化 | 待完善 |
| 运行工作流 | ⚠️ 模拟 | ❌ 待实现 | 待完善 |
| 迷你地图 | ❌ | ✅ | 新增 |
| 控制按钮 | ❌ | ✅ | 新增 |

### 待完善功能

- [ ] 实现完整的撤销/重做功能
- [ ] 完善工作流导入对话框
- [ ] 实现工作流运行逻辑
- [ ] 添加节点复制/粘贴功能
- [ ] 添加键盘快捷键支持
- [ ] 优化多端口节点（条件分支等）
- [ ] 添加节点分组功能
- [ ] 实现边的自定义样式

## 性能优化建议

1. **虚拟滚动**：对于大型工作流（100+节点），考虑启用虚拟滚动
2. **节点懒加载**：只渲染可见区域内的节点
3. **防抖更新**：节点拖拽时使用防抖减少更新频率
4. **Web Worker**：复杂计算放到 Web Worker

```javascript
// 示例：防抖更新
import { debounce } from 'lodash-es';

const onNodeDrag = debounce((event) => {
  // 处理拖拽
}, 16); // 约60fps
```

## 常见问题

### Q: 如何自定义连线样式？

```vue
<VueFlow>
  <template #edge-custom="props">
    <BaseEdge
      :id="props.id"
      :source-x="props.sourceX"
      :source-y="props.sourceY"
      :target-x="props.targetX"
      :target-y="props.targetY"
      :style="{ stroke: 'red', strokeWidth: 3 }"
    />
  </template>
</VueFlow>
```

### Q: 如何禁用某些交互？

```vue
<VueFlow
  :nodes-draggable="false"    // 禁用节点拖拽
  :nodes-connectable="false"  // 禁用节点连接
  :zoom-on-scroll="false"     // 禁用滚轮缩放
/>
```

### Q: 如何编程式添加节点？

```javascript
import { useVueFlow } from '@vue-flow/core';

const { addNodes } = useVueFlow();

addNodes([{
  id: 'new-node',
  type: 'default',
  position: { x: 100, y: 100 },
  data: { label: '新节点' }
}]);
```

## 资源链接

- [Vue Flow 官方文档](https://vueflow.dev/)
- [Vue Flow GitHub](https://github.com/bcakmakoglu/vue-flow)
- [示例代码](https://vueflow.dev/examples.html)
- [API 参考](https://vueflow.dev/api/)

## 下一步计划

1. **Phase 1**（已完成）
   - ✅ 搭建 Vue Flow 基础框架
   - ✅ 创建所有节点组件
   - ✅ 实现基本交互功能

2. **Phase 2**（进行中）
   - [ ] 完善撤销/重做
   - [ ] 实现工作流运行
   - [ ] 优化UI/UX

3. **Phase 3**（规划中）
   - [ ] 添加高级功能（分组、子流程等）
   - [ ] 性能优化
   - [ ] 编写单元测试

---

**创建日期**: 2026-05-14  
**版本**: v1.0  
**技术栈**: Vue 3 + @vue-flow/core ^1.48.2
