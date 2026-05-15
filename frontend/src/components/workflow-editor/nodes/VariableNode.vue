<template>
  <div class="node variable-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📦</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div class="node-body">
      <div class="var-name-row">
        <input 
          v-model="data.varName" 
          class="var-name-input" 
          placeholder="变量名"
        />
        <select v-model="data.varType" class="var-type-select">
          <option value="string">字符串</option>
          <option value="number">数字</option>
          <option value="boolean">布尔值</option>
          <option value="array">数组</option>
          <option value="object">对象</option>
          <option value="date">日期</option>
          <option value="json">JSON</option>
        </select>
      </div>

      <select v-model="data.valueSource" class="value-source-select">
        <option value="constant">常量值</option>
        <option value="expression">表达式</option>
        <option value="reference">引用变量</option>
        <option value="function">函数调用</option>
        <option value="json-path">JSON路径</option>
      </select>

      <textarea 
        v-model="data.varValue" 
        class="var-value-textarea"
        :placeholder="getPlaceholder()"
      ></textarea>

      <div v-if="showAdvanced" class="advanced-panel">
        <div class="section-title">数据绑定</div>
        <div class="bind-options">
          <label class="checkbox-label">
            <input v-model="data.bindToInput" type="checkbox" />
            <span>绑定到输入</span>
          </label>
          <label class="checkbox-label">
            <input v-model="data.bindToOutput" type="checkbox" />
            <span>绑定到输出</span>
          </label>
        </div>

        <div class="section-title">表达式函数</div>
        <div class="function-list">
          <button 
            v-for="func in availableFunctions" 
            :key="func.name" 
            @click="insertFunction(func)"
            class="function-btn"
            :title="func.description"
          >
            {{ func.name }}
          </button>
        </div>

        <div class="section-title">变量作用域</div>
        <select v-model="data.scope" class="node-select">
          <option value="workflow">工作流级别</option>
          <option value="step">步骤级别</option>
          <option value="global">全局</option>
        </select>

        <div class="section-title">高级选项</div>
        <label class="checkbox-label">
          <input v-model="data.required" type="checkbox" />
          <span>必填</span>
        </label>
        <label class="checkbox-label">
          <input v-model="data.readonly" type="checkbox" />
          <span>只读</span>
        </label>

        <div v-if="data.required" class="default-value">
          <label>默认值</label>
          <input 
            v-model="data.defaultValue" 
            class="node-input" 
            placeholder="默认值" 
          />
        </div>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
    <Handle type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script>
import { ref } from 'vue';
import { Handle, Position } from '@vue-flow/core';

export default {
  name: 'VariableNode',
  components: { Handle },
  props: {
    data: { type: Object, required: true },
    selected: { type: Boolean, default: false }
  },
  setup() {
    const showAdvanced = ref(false);

    const availableFunctions = [
      { name: '{{now()}}', description: '获取当前时间' },
      { name: '{{uuid()}}', description: '生成UUID' },
      { name: '{{len()}}', description: '获取长度' },
      { name: '{{trim()}}', description: '去除空白' },
      { name: '{{upper()}}', description: '转大写' },
      { name: '{{lower()}}', description: '转小写' },
      { name: '{{json()}}', description: 'JSON序列化' },
      { name: '{{parseJson()}}', description: 'JSON解析' },
      { name: '{{env()}}', description: '读取环境变量' },
      { name: '{{random()}}', description: '生成随机数' }
    ];

    const toggleAdvanced = () => {
      showAdvanced.value = !showAdvanced.value;
    };

    const getPlaceholder = () => {
      const valueSource = this?.data?.valueSource || 'constant';
      switch (valueSource) {
        case 'constant': return '输入常量值...';
        case 'expression': return '输入表达式，如: {{var1 + var2}}';
        case 'reference': return '引用变量，如: {{otherVar}}';
        case 'function': return '输入函数调用，如: {{now()}}';
        case 'json-path': return '输入JSON路径，如: $.data.items[0]';
        default: return '输入变量值...';
      }
    };

    const insertFunction = (func) => {
      if (!this.data.varValue) this.data.varValue = '';
      this.data.varValue += func.name;
    };

    return { 
      Position, 
      showAdvanced, 
      availableFunctions,
      toggleAdvanced, 
      getPlaceholder, 
      insertFunction 
    };
  }
};
</script>

<style scoped>
.variable-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 240px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.variable-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
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

.var-name-row {
  display: flex;
  gap: 6px;
}

.var-name-input {
  flex: 1;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.var-name-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.var-type-select {
  width: 80px;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.value-source-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.var-value-textarea {
  width: 100%;
  min-height: 60px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: monospace;
}

.var-value-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

.advanced-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.bind-options {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
}

.checkbox-label input {
  width: 14px;
  height: 14px;
}

.function-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.function-btn {
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.function-btn:hover {
  background: #dbeafe;
  border-color: #3b82f6;
  color: #3b82f6;
}

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 8px;
}

.node-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.default-value {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}

.default-value label {
  font-size: 10px;
  color: #64748b;
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
