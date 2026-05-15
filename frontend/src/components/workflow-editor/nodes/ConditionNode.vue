<template>
  <div class="node condition-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔀</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div class="node-body">
      <div class="condition-builder">
        <div class="builder-row">
          <select v-model="localLeftType" @change="emitUpdate" class="operand-select">
            <option value="variable">变量</option>
            <option value="constant">常量</option>
            <option value="expression">表达式</option>
          </select>
          <input 
            v-model="localLeftValue" 
            @input="emitUpdate" 
            :placeholder="getPlaceholder(localLeftType)"
            class="operand-input" 
          />
        </div>

        <select v-model="localOperator" @change="emitUpdate" class="operator-select">
          <option value="==">等于 (==)</option>
          <option value="!=">不等于 (!=)</option>
          <option value=">">大于 (>)</option>
          <option value="<">小于 (<)</option>
          <option value=">=">大于等于 (>=)</option>
          <option value="<=">小于等于 (<=)</option>
          <option value="contains">包含 (contains)</option>
          <option value="not_contains">不包含 (not contains)</option>
          <option value="starts_with">以...开头</option>
          <option value="ends_with">以...结尾</option>
          <option value="matches">匹配正则</option>
          <option value="is_empty">为空</option>
          <option value="not_empty">不为空</option>
          <option value="is_true">为真</option>
          <option value="is_false">为假</option>
        </select>

        <div v-if="!isUnaryOperator" class="builder-row">
          <select v-model="localRightType" @change="emitUpdate" class="operand-select">
            <option value="variable">变量</option>
            <option value="constant">常量</option>
            <option value="expression">表达式</option>
          </select>
          <input 
            v-model="localRightValue" 
            @input="emitUpdate" 
            :placeholder="getPlaceholder(localRightType)"
            class="operand-input" 
          />
        </div>
      </div>

      <div class="condition-labels">
        <span class="label-true">✓ 满足</span>
        <span class="label-false">✗ 不满足</span>
      </div>

      <div v-if="showAdvanced" class="advanced-panel">
        <div class="section-title">逻辑操作</div>
        <select v-model="localLogicType" @change="emitUpdate" class="node-select">
          <option value="single">单条件</option>
          <option value="and">与条件 (AND)</option>
          <option value="or">或条件 (OR)</option>
        </select>

        <div v-if="localLogicType !== 'single'" class="nested-conditions">
          <div 
            v-for="(condition, index) in localNestedConditions" 
            :key="index" 
            class="nested-condition"
          >
            <div class="nested-header">
              <span>条件 {{ index + 1 }}</span>
              <button @click="removeNestedCondition(index)" class="remove-btn">✕</button>
            </div>
            <select v-model="condition.leftType" @change="emitUpdate" class="mini-select">
              <option value="variable">变量</option>
              <option value="constant">常量</option>
            </select>
            <input 
              v-model="condition.leftValue" 
              @input="emitUpdate" 
              placeholder="左操作数" 
              class="mini-input" 
            />
            <select v-model="condition.operator" @change="emitUpdate" class="mini-select">
              <option value="==">=</option>
              <option value="!=">!=</option>
              <option value=">">></option>
              <option value="<"><</option>
              <option value="contains">contains</option>
            </select>
            <input 
              v-model="condition.rightValue" 
              @input="emitUpdate" 
              placeholder="右操作数" 
              class="mini-input" 
            />
          </div>
          <button @click="addNestedCondition" class="add-condition-btn">+ 添加条件</button>
        </div>

        <div class="section-title">高级选项</div>
        <label class="checkbox-label">
          <input v-model="localCaseSensitive" @change="emitUpdate" type="checkbox" />
          <span>大小写敏感</span>
        </label>
        <label class="checkbox-label">
          <input v-model="localTrimWhitespace" @change="emitUpdate" type="checkbox" />
          <span>忽略首尾空白</span>
        </label>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
    <Handle type="source" :position="Position.Right" id="true" class="handle-true" />
    <Handle type="source" :position="Position.Right" id="false" class="handle-false" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update']);

const showAdvanced = ref(false);

const localLeftType = ref(props.data.leftType || 'variable');
const localLeftValue = ref(props.data.leftValue || '');
const localOperator = ref(props.data.operator || '==');
const localRightType = ref(props.data.rightType || 'constant');
const localRightValue = ref(props.data.rightValue || '');
const localLogicType = ref(props.data.logicType || 'single');
const localNestedConditions = ref(props.data.nestedConditions || []);
const localCaseSensitive = ref(props.data.caseSensitive !== false);
const localTrimWhitespace = ref(props.data.trimWhitespace !== false);

const unaryOperators = ['is_empty', 'not_empty', 'is_true', 'is_false'];

const isUnaryOperator = computed(() => unaryOperators.includes(localOperator.value));

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const getPlaceholder = (type) => {
  switch (type) {
    case 'variable':
      return '{{变量名}}';
    case 'constant':
      return '常量值';
    case 'expression':
      return '表达式';
    default:
      return '';
  }
};

const addNestedCondition = () => {
  localNestedConditions.value.push({
    leftType: 'variable',
    leftValue: '',
    operator: '==',
    rightValue: ''
  });
  emitUpdate();
};

const removeNestedCondition = (index) => {
  if (localNestedConditions.value.length > 1) {
    localNestedConditions.value.splice(index, 1);
    emitUpdate();
  }
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    leftType: localLeftType.value,
    leftValue: localLeftValue.value,
    operator: localOperator.value,
    rightType: localRightType.value,
    rightValue: localRightValue.value,
    logicType: localLogicType.value,
    nestedConditions: localNestedConditions.value,
    caseSensitive: localCaseSensitive.value,
    trimWhitespace: localTrimWhitespace.value
  });
};

watch(() => props.data, (d) => {
  localLeftType.value = d.leftType || 'variable';
  localLeftValue.value = d.leftValue || '';
  localOperator.value = d.operator || '==';
  localRightType.value = d.rightType || 'constant';
  localRightValue.value = d.rightValue || '';
  localLogicType.value = d.logicType || 'single';
  localNestedConditions.value = d.nestedConditions || [];
  localCaseSensitive.value = d.caseSensitive !== false;
  localTrimWhitespace.value = d.trimWhitespace !== false;
}, { deep: true });
</script>

<style scoped>
.condition-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 220px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.condition-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%);
  border-bottom: 1px solid #e2e8f0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  color: white;
  flex: 1;
}

.advanced-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.advanced-toggle:hover,
.advanced-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.node-body {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-builder {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.builder-row {
  display: flex;
  gap: 4px;
}

.operand-select {
  width: 65px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 10px;
}

.operand-input {
  flex: 1;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.operator-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.condition-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}

.label-true {
  color: #166534;
  background-color: #dcfce7;
  padding: 2px 6px;
  border-radius: 3px;
}

.label-false {
  color: #991b1b;
  background-color: #fee2e2;
  padding: 2px 6px;
  border-radius: 3px;
}

.advanced-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.nested-conditions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.nested-condition {
  padding: 6px;
  background: #f8fafc;
  border-radius: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.nested-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.nested-header span {
  font-size: 10px;
  color: #64748b;
  font-weight: 500;
}

.remove-btn {
  width: 20px;
  height: 20px;
  border: none;
  background: #fee2e2;
  border-radius: 3px;
  color: #dc2626;
  cursor: pointer;
  font-size: 9px;
}

.mini-select {
  width: 60px;
  padding: 3px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 10px;
}

.mini-input {
  width: calc(50% - 8px);
  padding: 3px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 10px;
}

.add-condition-btn {
  padding: 3px 8px;
  border: 1px dashed #cbd5e1;
  border-radius: 3px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.add-condition-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
  margin-bottom: 4px;
}

.checkbox-label input {
  width: 14px;
  height: 14px;
}

.handle-true {
  top: 35%;
  background-color: #22c55e;
}

.handle-false {
  top: 65%;
  background-color: #ef4444;
}

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.5) !important;
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