<template>
  <div class="node-panel">
    <h3>节点类型</h3>
    <div v-for="group in nodeGroups" :key="group.id" class="node-group">
      <div class="group-header" @click="toggleGroup(group.id)">
        <svg 
          width="12" 
          height="12" 
          viewBox="0 0 24 24" 
          fill="none" 
          stroke="currentColor" 
          stroke-width="2"
          :class="{ rotated: expandedGroups.includes(group.id) }"
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
        <span>{{ group.name }}</span>
      </div>
      <div v-show="expandedGroups.includes(group.id)" class="node-types">
        <div 
          v-for="nodeType in group.nodes" 
          :key="nodeType.id"
          class="node-type-item"
          :class="{ disabled: disabled }"
          :draggable="!disabled"
          @dragstart="disabled ? undefined : onDragStart($event, nodeType)"
        >
          <span class="node-icon">{{ nodeType.icon }}</span>
          <span class="node-name">{{ nodeType.name }}</span>
        </div>
      </div>
    </div>
    
    <div class="templates-section">
      <h4>快速模板</h4>
      <div class="templates-list">
        <button 
          v-for="template in quickTemplates" 
          :key="template.id" 
          @click="disabled ? undefined : $emit('apply-template', template)" 
          :disabled="disabled"
          class="template-btn"
          :class="{ disabled: disabled }"
        >
          <div class="template-name">{{ template.name }}</div>
          <div class="template-desc" v-if="template.description">{{ template.description }}</div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  quickTemplates: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['drag-start', 'apply-template']);

const expandedGroups = ref(['flow', 'llm', 'tools', 'parser']);

const nodeGroups = ref([
  {
    id: 'flow',
    name: '流程控制',
    nodes: [
      { id: 'start', name: '开始', icon: '🚀', type: 'start' },
      { id: 'end', name: '结束', icon: '🏁', type: 'end' },
      { id: 'condition', name: '条件分支', icon: '🔀', type: 'condition' },
      { id: 'loop', name: '循环', icon: '🔄', type: 'loop' }
    ]
  },
  {
    id: 'llm',
    name: 'LLM 相关',
    nodes: [
      { id: 'prompt', name: '提示词', icon: '📝', type: 'prompt' },
      { id: 'llm', name: 'LLM 调用', icon: '🤖', type: 'llm' }
    ]
  },
  {
    id: 'tools',
    name: '工具与数据',
    nodes: [
      { id: 'tool', name: '工具调用', icon: '🔧', type: 'tool' },
      { id: 'http', name: 'HTTP请求', icon: '🌐', type: 'http' },
      { id: 'code', name: '代码执行', icon: '💻', type: 'code' },
      { id: 'variable', name: '变量赋值', icon: '📦', type: 'variable' },
      { id: 'knowledgeBase', name: '知识库', icon: '📚', type: 'knowledgeBase' }
    ]
  },
  {
    id: 'parser',
    name: '数据处理',
    nodes: [
      { id: 'parser', name: '输出解析', icon: '📊', type: 'parser' }
    ]
  },
  {
    id: 'forms',
    name: '表单操作',
    nodes: [
      { id: 'form', name: '表单节点', icon: '📋', type: 'form' }
    ]
  }
]);

const toggleGroup = (groupId) => {
  const index = expandedGroups.value.indexOf(groupId);
  if (index > -1) {
    expandedGroups.value.splice(index, 1);
  } else {
    expandedGroups.value.push(groupId);
  }
};

const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType));
  event.dataTransfer.effectAllowed = 'move';
  
  // 保存当前元素引用
  const currentElement = event.currentTarget;
  
  // 添加拖动时的视觉反馈
  const dragImage = event.currentTarget.cloneNode(true);
  dragImage.style.position = 'absolute';
  dragImage.style.top = '-1000px';
  dragImage.style.opacity = '0.8';
  document.body.appendChild(dragImage);
  event.dataTransfer.setDragImage(dragImage, 0, 0);
  
  // 添加 dragging 类
  if (currentElement) {
    currentElement.classList.add('dragging');
  }
  
  // 拖拽结束后移除 dragging 类
  setTimeout(() => {
    if (currentElement && currentElement.classList) {
      currentElement.classList.remove('dragging');
    }
    if (document.body.contains(dragImage)) {
      document.body.removeChild(dragImage);
    }
  }, 0);
  
  emit('drag-start', nodeType);
};
</script>

<style scoped>
.node-panel {
  width: 180px;
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.node-panel h3 {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.node-group {
  margin-bottom: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
  color: #475569;
}

.group-header svg {
  transition: transform 0.2s;
}

.group-header svg.rotated {
  transform: rotate(180deg);
}

.node-types {
  padding-left: 8px;
}

.node-type-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: grab;
  user-select: none;
  transition: all 0.2s;
}

.node-type-item:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.node-type-item:active {
  cursor: grabbing;
}

.node-type-item.dragging {
  opacity: 0.5;
}

.node-type-item.disabled {
  cursor: not-allowed;
  opacity: 0.6;
  background-color: #f1f5f9;
  border-color: #e2e8f0;
}

.node-type-item.disabled:hover {
  background-color: #f1f5f9;
  border-color: #e2e8f0;
  transform: none;
  box-shadow: none;
}

.node-icon {
  font-size: 16px;
}

.node-name {
  font-size: 12px;
  color: #334155;
}

.templates-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.templates-section h4 {
  margin: 0 0 10px 0;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

.templates-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.template-btn {
  padding: 8px 10px;
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  color: #334155;
  transition: all 0.2s;
}

.template-btn:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.template-btn.disabled {
  cursor: not-allowed;
  opacity: 0.6;
  background-color: #f1f5f9;
  border-color: #e2e8f0;
}

.template-btn.disabled:hover {
  background-color: #f1f5f9;
  border-color: #e2e8f0;
  transform: none;
  box-shadow: none;
}

.template-name {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 2px;
}

.template-desc {
  font-size: 10px;
  color: #94a3b8;
  line-height: 1.4;
}
</style>