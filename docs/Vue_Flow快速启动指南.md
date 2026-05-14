# Vue Flow 工作流编辑器 - 快速启动指南

## 🚀 5分钟快速开始

### 步骤1: 查看新版本

新版编辑器文件位置：
```
frontend/src/components/intent-panels/
├── LangChainEditorVueFlow.vue    ← 新版（推荐使用）
└── nodes/                         ← 自定义节点组件
```

### 步骤2: 在应用中集成

找到 `App.vue` 或路由配置，将编辑器引用改为新版本：

```javascript
// 原来
import LangChainEditor from './components/intent-panels/LangChainEditor.vue'

// 改为
import LangChainEditor from './components/intent-panels/LangChainEditorVueFlow.vue'
```

### 步骤3: 启动开发服务器

```bash
cd frontend
npm run dev
```

访问应用，打开工作流编辑器页面即可看到新版本！

## 📦 已安装的功能

### 核心功能 ✅
- [x] 拖拽节点到画布
- [x] 连接节点（自动对齐）
- [x] 移动节点
- [x] 缩放画布（鼠标滚轮）
- [x] 平移画布（按住空格+拖拽）
- [x] 删除节点和连线
- [x] 节点属性编辑
- [x] 保存/导出工作流
- [x] 应用模板

### 增强功能 ✨
- [x] 网格背景
- [x] 迷你地图（右下角）
- [x] 控制按钮（缩放、适应视图）
- [x] 流畅的动画效果
- [x] 专业的视觉设计

## 🎯 基本操作指南

### 添加节点
1. 从左侧面板选择节点类型
2. 拖拽到画布上
3. 松开鼠标

### 连接节点
1. 鼠标悬停在节点的右侧圆点（输出端口）
2. 按住并拖拽到另一个节点的左侧圆点（输入端口）
3. 松开鼠标完成连接

### 移动节点
- 直接拖拽节点到新位置

### 缩放画布
- 鼠标滚轮上下滚动
- 或使用右下角控制按钮

### 平移画布
- 按住空格键 + 拖拽鼠标
- 或在空白处拖拽（如果启用）

### 删除元素
- 选中节点或连线，按 Delete 键
- 或右键点击选择删除

### 保存工作流
- 点击顶部工具栏的"保存"按钮
- 数据自动保存到浏览器本地存储

## 📝 可用的节点类型

### 流程控制
- 🚀 **开始节点** - 工作流入口
- 🏁 **结束节点** - 工作流出口
- 🔀 **条件分支** - if/else 逻辑
- 🔄 **循环** - for/while 循环

### LLM 相关
- 📝 **提示词** - 定义 prompt 模板
- 🤖 **LLM调用** - 调用大语言模型

### 工具与数据
- 🔧 **工具调用** - 调用外部工具
- 🌐 **HTTP请求** - API 调用
- 💻 **代码执行** - 运行自定义代码
- 📦 **变量赋值** - 定义变量

### 数据处理
- 📊 **输出解析** - JSON/正则解析

## 🛠️ 自定义节点开发

### 创建新节点类型

1. 在 `nodes/` 目录创建新文件，如 `CustomNode.vue`

2. 使用基础模板：

```vue
<template>
  <div class="node custom-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🎯</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <!-- 你的自定义内容 -->
      <input v-model="localValue" @input="emitUpdate" />
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

const emit = defineEmits(['update']);
const localValue = ref(props.data.value || '');

const emitUpdate = () => {
  emit('update', props.data.id, { value: localValue.value });
};

watch(() => props.data, (d) => {
  localValue.value = d.value || '';
}, { deep: true });
</script>

<style scoped>
.custom-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 150px;
}
/* 更多样式... */
</style>
```

3. 在主编辑器中注册：

```javascript
// LangChainEditorVueFlow.vue
import CustomNode from './nodes/CustomNode.vue';

// 在 VueFlow 组件中添加模板
<VueFlow>
  <template #node-custom="props">
    <CustomNode :data="props.data" :selected="props.selected" @update="updateNodeData" />
  </template>
</VueFlow>
```

4. 添加到节点面板：

```javascript
const nodeGroups = ref([
  {
    id: 'custom',
    name: '自定义',
    nodes: [
      { id: 'custom', name: '自定义节点', icon: '🎯', type: 'custom' }
    ]
  }
]);
```

## 🔧 常见问题

### Q1: 如何修改节点样式？

编辑对应的节点组件文件，如 `nodes/LlmNode.vue`，修改 `<style>` 部分。

### Q2: 如何添加新的连线样式？

```vue
<VueFlow :default-edge-options="{ 
  type: 'smoothstep',
  style: { stroke: '#3b82f6', strokeWidth: 2 }
}">
```

### Q3: 如何禁用某些交互？

```vue
<VueFlow
  :nodes-draggable="false"    // 禁用拖拽
  :nodes-connectable="false"  // 禁用连接
  :zoom-on-scroll="false"     // 禁用缩放
/>
```

### Q4: 如何编程式添加节点？

```javascript
import { useVueFlow } from '@vue-flow/core';

const { addNodes } = useVueFlow();

addNodes([{
  id: 'new-node',
  type: 'llm',
  position: { x: 100, y: 100 },
  data: { label: '新节点', model: 'gpt-4' }
}]);
```

### Q5: 如何获取当前工作流数据？

```javascript
// 获取所有节点
const nodes = elements.value.filter(el => !el.source && !el.target);

// 获取所有连线
const edges = elements.value.filter(el => el.source && el.target);
```

## 📚 学习资源

### 官方文档
- [Vue Flow 文档](https://vueflow.dev/)
- [API 参考](https://vueflow.dev/api/)
- [示例代码](https://vueflow.dev/examples.html)

### 本项目文档
- [迁移指南](./Vue_Flow工作流编辑器迁移指南.md)
- [版本对比](./工作流编辑器版本对比.md)
- [连线优化说明](./工作流编辑器连线优化说明.md)

## 🐛 问题反馈

如果遇到问题：

1. 检查浏览器控制台是否有错误
2. 确认依赖已正确安装：`npm install`
3. 查看官方文档和示例
4. 在项目 issues 中搜索类似问题

## 🎉 下一步

- [ ] 完善撤销/重做功能
- [ ] 实现工作流运行逻辑
- [ ] 添加键盘快捷键
- [ ] 支持节点分组
- [ ] 优化性能（虚拟滚动）

---

**祝使用愉快！** 🚀

如有任何问题，请查阅文档或联系开发团队。
