<template>
  <div
    class="node condition-node"
    :class="{
      selected,
      'is-config-mode': configMode,
      'is-compact': compact && !configMode
    }"
  >
    <!-- 配置模式 -->
    <div v-if="configMode" class="condition-node-config">
      <!-- 顶部功能说明 -->
      <div class="node-description">
        连接多个下游分支，如果设立的条件成立，则只运行如果分支，不成立则只运行否则分支。
      </div>
      
      <!-- 节点标题 -->
      <div class="node-title-section">
        <input 
          v-model="localLabel" 
          @input="emitUpdate"
          type="text"
          placeholder="条件分支"
          class="node-title-input"
        />
      </div>

      <!-- 分支列表 -->
      <div class="branch-list">
        <div 
          v-for="(branch, branchIndex) in localBranches" 
          :key="branchIndex"
          class="branch-container"
        >
          <!-- 分支标题 -->
          <div class="branch-header">
            <button @click="toggleBranch(branchIndex)" class="branch-toggle-btn">
              <svg 
                width="14" 
                height="14" 
                viewBox="0 0 24 24" 
                fill="none" 
                stroke="currentColor" 
                stroke-width="2"
                :class="{ rotated: branch.expanded }"
              >
                <polyline points="6 9 12 15 18 9"/>
              </svg>
              <span>{{ getBranchTitle(branchIndex) }}</span>
            </button>
            <div class="branch-actions">
              <button class="help-btn-small" :title="getBranchHelp(branchIndex)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
              <button 
                v-if="branchIndex > 0"
                @click="removeBranch(branchIndex)" 
                class="action-btn delete-branch-btn" 
                title="删除当前分支"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 分支内容 - 条件配置 -->
          <div v-if="branch.expanded" class="branch-content">
            <div v-for="(condition, condIndex) in branch.conditions" :key="condIndex" class="condition-row">
              <div class="condition-grid">
                <!-- 条件标签列 -->
                <div class="grid-cell label-cell">
                  <span class="condition-label-text">条件</span>
                </div>
                
                <!-- 引用变量列 -->
                <div class="grid-cell variable-cell">
                  <select 
                    v-model="condition.variable" 
                    @change="emitUpdate"
                    class="variable-select"
                    placeholder="请选择..."
                  >
                    <option value="" disabled>请选择...</option>
                    <option v-for="variable in availableVariables" :key="variable.id" :value="variable.id">
                      {{ variable.name }}
                    </option>
                  </select>
                </div>
                
                <!-- 选择条件列 -->
                <div class="grid-cell operator-cell">
                  <select 
                    v-model="condition.operator" 
                    @change="emitUpdate"
                    class="operator-select"
                  >
                    <option value="" disabled>请选择条件</option>
                    <option value="==">等于</option>
                    <option value="!=">不等于</option>
                    <option value=">">大于</option>
                    <option value="<">小于</option>
                    <option value=">=">大于等于</option>
                    <option value="<=">小于等于</option>
                    <option value="contains">包含</option>
                    <option value="not_contains">不包含</option>
                    <option value="starts_with">以...开头</option>
                    <option value="ends_with">以...结尾</option>
                    <option value="matches">匹配正则</option>
                    <option value="is_empty">为空</option>
                    <option value="not_empty">不为空</option>
                  </select>
                </div>
                
                <!-- 比较值列 -->
                <div class="grid-cell value-cell">
                  <div class="value-group">
                    <select 
                      v-model="condition.valueType" 
                      @change="emitUpdate"
                      class="value-type-select"
                    >
                      <option value="input">输入</option>
                      <option value="reference">引用变量</option>
                    </select>
                    <input 
                      v-if="condition.valueType === 'input'"
                      v-model="condition.value" 
                      @input="emitUpdate"
                      type="text"
                      placeholder="请输入"
                      class="value-input"
                    />
                    <select 
                      v-else
                      v-model="condition.value" 
                      @change="emitUpdate"
                      class="value-reference-select"
                    >
                      <option value="" disabled>请选择...</option>
                      <option v-for="variable in availableVariables" :key="variable.id" :value="variable.id">
                        {{ variable.name }}
                      </option>
                    </select>
                  </div>
                </div>
                
                <!-- 操作按钮列 -->
                <div class="grid-cell action-cell">
                  <button 
                    v-if="condIndex === branch.conditions.length - 1"
                    @click="addCondition(branchIndex)" 
                    class="action-btn add-condition-btn" 
                    title="添加更多条件"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="12" y1="5" x2="12" y2="19"/>
                      <line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作区 -->
      <div class="node-actions">
        <button @click="addBranch" class="action-btn-inline" title="新增分支">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span>新增分支</span>
        </button>
        <button @click="$emit('close')" class="close-btn-inline">收起</button>
      </div>
    </div>
    
    <!-- 非配置模式 - 画布编辑区显示 -->
    <div v-if="!configMode" class="condition-node-view">
      <!-- 功能说明 -->
      <div class="view-description">
        连接多个下游分支，如果设立的条件成立，则只运行如果分支，不成立则只运行否则分支。
      </div>
      
      <!-- 节点标题 -->
      <div class="view-title">{{ data.label || '条件分支' }}</div>
      
      <!-- 分支列表 -->
      <div class="view-branches">
        <div 
          v-for="(branch, branchIndex) in localBranches" 
          :key="branchIndex"
          class="view-branch-item"
        >
          <div class="view-branch-info">
            <span v-if="branchIndex === 0" class="view-branch-icon">⚙️</span>
            <span class="view-branch-name">{{ getBranchTitle(branchIndex) }}</span>
            <span v-if="branchIndex === 0" class="view-help-icon" title="第一优先级条件">️</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 连接线Handle -->
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <!-- 动态生成与分支数量对应的输出连接点 -->
    <Handle 
      v-for="(branch, index) in localBranches" 
      :key="'branch-' + index"
      v-if="!configMode" 
      type="source" 
      :position="Position.Right" 
      :id="'branch_' + index" 
      :class="'handle-branch-' + index"
      :style="{ top: getHandlePosition(index) + '%' }"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  availableVariables: {
    type: Array,
    default: () => []
  },
  ...nodeDisplayProps
});

const emit = defineEmits(['update', 'close']);

// 本地状态
const localLabel = ref(props.data.label || '判断器');
const branchSectionExpanded = ref(true);

// 分支数据结构
const localBranches = ref([]);

// 初始化分支数据
const initBranches = () => {
  if (props.data.branches && props.data.branches.length > 0) {
    localBranches.value = JSON.parse(JSON.stringify(props.data.branches));
  } else {
    // 默认创建两个分支：如果、否则如果
    localBranches.value = [
      {
        type: 'if',
        expanded: true,
        conditions: [
          {
            variable: '',
            operator: '',
            valueType: 'input',
            value: ''
          }
        ]
      },
      {
        type: 'else_if',
        expanded: true,
        conditions: [
          {
            variable: '',
            operator: '',
            valueType: 'input',
            value: ''
          }
        ]
      }
    ];
    emitUpdate();
  }
};

// 切换分支展开/折叠
const toggleBranch = (index) => {
  localBranches.value[index].expanded = !localBranches.value[index].expanded;
};

// 切换分支区域展开/折叠
const toggleBranchSection = () => {
  branchSectionExpanded.value = !branchSectionExpanded.value;
};

// 获取分支标题
const getBranchTitle = (index) => {
  if (index === 0) return '如果';
  return '否则如果';
};

// 获取分支帮助文本
const getBranchHelp = (index) => {
  if (index === 0) return '第一优先级条件，如果成立则执行此分支';
  return '第二优先级条件，在第一条件不成立时判断';
};

// 添加新分支
const addBranch = () => {
  localBranches.value.push({
    type: 'else_if',
    expanded: true,
    conditions: [
      {
        variable: '',
        operator: '',
        valueType: 'input',
        value: ''
      }
    ]
  });
  emitUpdate();
};

// 删除分支
const removeBranch = (index) => {
  if (localBranches.value.length > 1) {
    localBranches.value.splice(index, 1);
    emitUpdate();
  }
};

// 添加条件到指定分支
const addCondition = (branchIndex) => {
  localBranches.value[branchIndex].conditions.push({
    variable: '',
    operator: '',
    valueType: 'input',
    value: ''
  });
  emitUpdate();
};

// 更新数据
const emitUpdate = () => {
  emit('update', props.data.id, {
    label: localLabel.value,
    branches: localBranches.value
  });
};

// 计算连接点的垂直位置百分比
const getHandlePosition = (index) => {
  const totalBranches = localBranches.value.length;
  if (totalBranches === 1) return 50;
  // 均匀分布，第一个在20%，最后一个在80%，中间均匀分布
  const step = 60 / (totalBranches - 1);
  return 20 + (index * step);
};

// 监听数据变化
watch(() => props.data, (newData) => {
  if (newData) {
    localLabel.value = newData.label || '判断器';
    initBranches();
  }
}, { immediate: true, deep: true });
</script>

<style scoped>
.condition-node {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  min-width: 280px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
  padding: 16px;
}

.condition-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.condition-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
  padding: 0;
}

.condition-node-config {
  padding: 16px;
  background: #fff;
}

/* 画布编辑区节点样式 */
.condition-node-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.view-description {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  margin: 0 0 12px 0;
}

.view-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}

.view-branches {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.view-branch-item {
  display: flex;
  align-items: center;
  padding: 6px 0;
}

.view-branch-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.view-branch-icon {
  font-size: 14px;
}

.view-branch-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.view-help-icon {
  font-size: 12px;
  color: #bbb;
  cursor: help;
}

/* ========== 配置面板样式 ========== */

/* 功能说明文字 */
.node-description {
  margin: 0 0 12px 0;
  font-size: 12px;
  color: #999;
  line-height: 1.5;
}

/* 节点标题 */
.node-title-section {
  margin-bottom: 16px;
}

.node-title-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  background: #fafafa;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.node-title-input::placeholder {
  color: #bfbfbf;
  font-style: italic;
}

.node-title-input:focus {
  border-color: #3b82f6;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

/* 分支容器 */
.branch-list {
  margin-bottom: 16px;
}

.branch-container {
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
}

.branch-container:last-child {
  margin-bottom: 0;
}

/* 分支标题 */
.branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.branch-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.branch-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #333;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.branch-toggle-btn:hover {
  background: #f0f0f0;
}

.branch-toggle-btn svg {
  width: 14px;
  height: 14px;
  transition: transform 0.2s;
}

.branch-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.help-btn-small {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #bbb;
  cursor: help;
  border-radius: 50%;
  transition: all 0.2s;
}

.help-btn-small:hover {
  background: #f0f0f0;
  color: #666;
}

/* 分支内容 */
.branch-content {
  padding: 12px;
  background: #fff;
}

/* 条件行 */
.condition-row {
  margin-bottom: 12px;
}

.condition-row:last-child {
  margin-bottom: 0;
}

.condition-grid {
  display: grid;
  grid-template-columns: 60px 1fr 1fr 1.5fr 60px;
  gap: 8px;
  align-items: center;
}

.grid-cell {
  display: flex;
  align-items: center;
}

.label-cell {
  justify-content: center;
}

.condition-label-text {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.variable-select,
.operator-select,
.value-type-select,
.value-reference-select {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.variable-select:focus,
.operator-select:focus,
.value-type-select:focus,
.value-reference-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.value-group {
  display: flex;
  gap: 6px;
  width: 100%;
}

.value-type-select {
  width: 90px;
  flex-shrink: 0;
}

.value-input,
.value-reference-select {
  flex: 1;
}

.value-input {
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.value-input::placeholder {
  color: #bfbfbf;
}

.value-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.action-cell {
  justify-content: center;
  gap: 4px;
}

.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #3b82f6;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: #f0f7ff;
}

.add-condition-btn:hover {
  background: #f0f7ff;
  color: #3b82f6;
}

.delete-branch-btn {
  color: #ff4d4f;
}

.delete-branch-btn:hover {
  background: #fff1f0;
  color: #ff4d4f;
}

.action-btn svg {
  width: 14px;
  height: 14px;
}

/* 底部操作区 */
.node-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #e8e8e8;
}

.action-btn-inline {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  background: #3b82f6;
  color: white;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn-inline:hover {
  background: #2563eb;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
}

.action-btn-inline svg {
  width: 14px;
  height: 14px;
}

.close-btn-inline {
  padding: 6px 16px;
  border: 1px solid #d9d9d9;
  background: #fff;
  color: #666;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn-inline:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

/* Handle样式 */
.handle-branch-0 {
  background-color: #22c55e !important;
}

.handle-branch-1 {
  background-color: #ef4444 !important;
}

.handle-branch-2 {
  background-color: #3b82f6 !important;
}

.handle-branch-3 {
  background-color: #f59e0b !important;
}

.handle-branch-4 {
  background-color: #8b5cf6 !important;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(255, 122, 69, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(255, 122, 69, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #3b82f6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #2563eb !important;
}
</style>