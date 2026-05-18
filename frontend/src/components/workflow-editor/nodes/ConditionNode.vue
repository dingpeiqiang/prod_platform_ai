<template>
  <div
    ref="nodeRef"
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
                v-if="branchIndex > 0 && branch.type !== 'else'"
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
      <!-- 头部区域 -->
      <div class="node-header">
        <span class="node-icon">🔀</span>
        <span class="node-title">{{ data.label || '条件分支' }}</span>
      </div>

      <!-- 紧凑模式内容 -->
      <div v-if="compact" class="node-compact-body">
        <div class="compact-summary-row">
          <span class="compact-summary">{{ localBranches.length }} 个分支</span>
          <div class="compact-dots">
            <span
              v-for="(_, index) in localBranches"
              :key="index"
              class="compact-dot"
              :class="'dot-' + index"
            ></span>
          </div>
        </div>
        <span class="compact-hint">双击配置</span>
      </div>

      <!-- 非紧凑模式内容 -->
      <div v-else class="node-body">
        <span class="node-desc">连接多个下游分支进行条件判断</span>
        <!-- 分支条件列表 -->
        <div class="branch-conditions">
          <div
            v-for="(branch, branchIndex) in localBranches"
            :key="branchIndex"
            class="branch-condition-item"
          >
            <div class="branch-content-row">
              <div class="branch-indicator" :class="'indicator-' + branchIndex"></div>
              <div class="branch-info">
                <span class="branch-name">{{ getBranchTitle(branchIndex) }}</span>
                <div class="condition-summary">
                  <span v-if="hasConditions(branch)" class="condition-text">
                    {{ getConditionSummary(branch) }}
                  </span>
                  <span v-else class="condition-empty">未设置条件</span>
                </div>
              </div>
              <div class="branch-arrow" :class="'arrow-' + branchIndex">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 连接线Handle -->
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <!-- 动态生成与分支数量对应的输出连接点 - 使用wrapper实现精确定位 -->
    <div
      v-for="(branch, index) in localBranches"
      :key="'handle-wrapper-' + index"
      v-if="!configMode"
      class="handle-position-wrapper"
      :data-branch-index="index"
    >
      <Handle
        type="source"
        :position="Position.Right"
        :id="'branch_' + index"
        :class="'handle-branch-' + index"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue';
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
const nodeRef = ref(null); // 节点DOM引用

// 分支数据结构
const localBranches = ref([]);

// 初始化分支数据
const initBranches = () => {
  if (props.data.branches && props.data.branches.length > 0) {
    // 深拷贝数据
    let branches = JSON.parse(JSON.stringify(props.data.branches));
    
    // 确保“否则”分支在最后
    const elseIndex = branches.findIndex(branch => branch.type === 'else');
    if (elseIndex !== -1 && elseIndex !== branches.length - 1) {
      // 移除“否则”分支
      const elseBranch = branches.splice(elseIndex, 1)[0];
      // 添加到末尾
      branches.push(elseBranch);
    }
    
    localBranches.value = branches;
  } else {
    // 默认创建三个分支：如果、否则如果、否则
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
      },
      {
        type: 'else',
        expanded: true,
        conditions: []
      }
    ];
    emitUpdate();
  }
};

// 切换分支展开/折叠
const toggleBranch = (index) => {
  localBranches.value[index].expanded = !localBranches.value[index].expanded;
  // 展开/折叠不改变分支数量，但会改变节点高度，需要重新计算
  setTimeout(() => {
    calculateHandlePositions();
  }, 150);
};

// 切换分支区域展开/折叠
const toggleBranchSection = () => {
  branchSectionExpanded.value = !branchSectionExpanded.value;
};

// 获取分支标题
const getBranchTitle = (index) => {
  const branch = localBranches.value[index];
  if (branch?.type === 'else') return '否则';
  if (index === 0) return '如果';
  return '否则如果';
};

// 获取分支帮助文本
const getBranchHelp = (index) => {
  const branch = localBranches.value[index];
  if (branch?.type === 'else') return '默认分支，当所有条件都不成立时执行';
  if (index === 0) return '第一优先级条件，如果成立则执行此分支';
  return '第二优先级条件，在第一条件不成立时判断';
};

// 添加新分支
const addBranch = () => {
  // 找到“否则”分支的位置
  const elseIndex = localBranches.value.findIndex(branch => branch.type === 'else');
  
  const newBranch = {
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
  };
  
  // 如果存在“否则”分支，插入到它之前
  if (elseIndex !== -1) {
    localBranches.value.splice(elseIndex, 0, newBranch);
  } else {
    // 如果没有“否则”分支，添加到末尾
    localBranches.value.push(newBranch);
  }
  
  emitUpdate();
};

// 删除分支
const removeBranch = (index) => {
  const branch = localBranches.value[index];
  
  // 不允许删除“否则”分支
  if (branch?.type === 'else') {
    return;
  }
  
  // 至少保留一个分支（如果或否则如果）
  const nonElseBranches = localBranches.value.filter(b => b.type !== 'else');
  if (nonElseBranches.length <= 1) {
    return;
  }
  
  localBranches.value.splice(index, 1);
  emitUpdate();
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

// 检查分支是否有条件
const hasConditions = (branch) => {
  if (branch.type === 'else') return true;
  return branch.conditions && branch.conditions.length > 0 &&
         branch.conditions.some(cond => cond.variable && cond.operator);
};

// 获取条件摘要
const getConditionSummary = (branch) => {
  if (branch.type === 'else') return '其他情况';
  if (!branch.conditions || branch.conditions.length === 0) return '';

  const conditions = branch.conditions.filter(cond => cond.variable && cond.operator);
  if (conditions.length === 0) return '';

  const summaries = conditions.map(cond => {
    const varName = getVariableName(cond.variable);
    const operator = getOperatorLabel(cond.operator);
    let value = cond.value;

    if (cond.valueType === 'reference') {
      value = '引用: ' + getVariableName(cond.value);
    }

    return `${varName} ${operator} ${value}`;
  });

  return summaries.join(' && ');
};

// 根据变量ID获取变量名
const getVariableName = (variableId) => {
  if (!variableId) return '';
  const variable = props.availableVariables.find(v => v.id === variableId);
  return variable ? variable.name : variableId;
};

// 获取运算符标签
const getOperatorLabel = (operator) => {
  const operators = {
    '==': '=',
    '!=': '≠',
    '>': '>',
    '<': '<',
    '>=': '≥',
    '<=': '≤',
    'contains': '包含',
    'not_contains': '不包含',
    'starts_with': '以...开头',
    'ends_with': '以...结尾',
    'matches': '匹配',
    'is_empty': '为空',
    'not_empty': '不为空'
  };
  return operators[operator] || operator;
};

// 更新 Handle wrapper 的位置
const updateHandlePositions = () => {
  if (!nodeRef.value) return;
  
  const nodeElement = nodeRef.value;
  const branchItems = nodeElement.querySelectorAll('.branch-condition-item');
  const wrappers = nodeElement.querySelectorAll('.handle-position-wrapper');
  
  branchItems.forEach((item, index) => {
    const wrapper = wrappers[index];
    if (wrapper && item) {
      const itemRect = item.getBoundingClientRect();
      const nodeRect = nodeElement.getBoundingClientRect();
      
      // 计算相对于节点的top位置
      const top = itemRect.top - nodeRect.top + itemRect.height / 2;
      wrapper.style.top = `${top}px`;
    }
  });
};

// 监听数据变化
watch(() => props.data, (newData) => {
  if (newData) {
    localLabel.value = newData.label || '判断器';
    initBranches();
    nextTick(() => updateHandlePositions());
  }
}, { immediate: true, deep: true });

// 监听分支数量变化
watch(() => localBranches.value.length, () => {
  nextTick(() => updateHandlePositions());
});

// 组件挂载后初始化
onMounted(() => {
  nextTick(() => {
    updateHandlePositions();
    
    // 监听节点尺寸变化
    if (nodeRef.value) {
      const resizeObserver = new ResizeObserver(() => {
        updateHandlePositions();
      });
      resizeObserver.observe(nodeRef.value);
    }
  });
});
</script>

<style scoped>
.condition-node {
  background: white;
  border: 1px solid #e2e8f0;
  color: #333;
  border-radius: 8px;
  min-width: 260px;
  min-height: 180px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.condition-node.selected {
  border-color: #ff7a45;
  box-shadow: 0 0 0 3px rgba(255, 122, 69, 0.2);
}

.condition-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.condition-node-config {
  padding: 16px;
  background: #fff;
}

/* 画布编辑区节点样式 */
.condition-node-view {
  padding: 0;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafafa;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  flex: 1;
}

.node-compact-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compact-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.compact-summary {
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-dots {
  display: flex;
  gap: 4px;
}

.compact-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.compact-dot.dot-0 { background-color: #22c55e; }
.compact-dot.dot-1 { background-color: #ef4444; }
.compact-dot.dot-2 { background-color: #3b82f6; }
.compact-dot.dot-3 { background-color: #f59e0b; }
.compact-dot.dot-4 { background-color: #8b5cf6; }

.compact-hint {
  font-size: 10px;
  color: #94a3b8;
}

.node-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-desc {
  font-size: 11px;
  color: #64748b;
}

/* 分支条件列表 */
.branch-conditions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
}

.branch-condition-item {
  display: flex;
  align-items: center;
  position: relative; /* 为Handle提供定位上下文 */
}

.branch-content-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #f8fafc;
  border-radius: 4px;
  border-left: 3px solid transparent;
  width: 100%;
}

.branch-condition-item:nth-child(1) .branch-content-row { border-left-color: #22c55e; }
.branch-condition-item:nth-child(2) .branch-content-row { border-left-color: #ef4444; }
.branch-condition-item:nth-child(3) .branch-content-row { border-left-color: #3b82f6; }
.branch-condition-item:nth-child(4) .branch-content-row { border-left-color: #f59e0b; }
.branch-condition-item:nth-child(5) .branch-content-row { border-left-color: #8b5cf6; }

/* 左侧颜色指示器 */
.branch-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.branch-indicator.indicator-0 { background-color: #22c55e; box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.3); }
.branch-indicator.indicator-1 { background-color: #ef4444; box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.3); }
.branch-indicator.indicator-2 { background-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3); }
.branch-indicator.indicator-3 { background-color: #f59e0b; box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.3); }
.branch-indicator.indicator-4 { background-color: #8b5cf6; box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.3); }

/* 分支信息 */
.branch-info {
  flex: 1;
  min-width: 0;
}

.branch-name {
  font-size: 11px;
  font-weight: 600;
  display: block;
  margin-bottom: 2px;
}

.condition-summary {
  display: block;
}

.condition-text {
  font-size: 10px;
  color: #64748b;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.condition-empty {
  font-size: 10px;
  color: #94a3b8;
  font-style: italic;
}

/* 右侧箭头指示 */
.branch-arrow {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: #e2e8f0;
}

.branch-arrow.arrow-0 { color: #22c55e; }
.branch-arrow.arrow-1 { color: #ef4444; }
.branch-arrow.arrow-2 { color: #3b82f6; }
.branch-arrow.arrow-3 { color: #f59e0b; }
.branch-arrow.arrow-4 { color: #8b5cf6; }

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
  min-height: 50px; /* 确保每个分支有最小高度，便于锚点定位 */
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
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #059669 !important;
}

/* 分支连接点颜色 */
:deep(.vue-flow__handle.handle-branch-0[type="source"]) {
  background-color: #22c55e !important;
}
:deep(.vue-flow__handle.handle-branch-1[type="source"]) {
  background-color: #ef4444 !important;
}
:deep(.vue-flow__handle.handle-branch-2[type="source"]) {
  background-color: #3b82f6 !important;
}
:deep(.vue-flow__handle.handle-branch-3[type="source"]) {
  background-color: #f59e0b !important;
}
:deep(.vue-flow__handle.handle-branch-4[type="source"]) {
  background-color: #8b5cf6 !important;
}

/* Handle 位置包装器 - 通过JS动态定位 */
.handle-position-wrapper {
  position: absolute;
  right: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}
</style>