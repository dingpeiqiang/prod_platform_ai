<template>
  <div class="parameter-input-panel">
    <!-- 面板头部 -->
    <div class="panel-header">
      <div class="header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 20h9"/>
          <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
        </svg>
        <span>执行参数配置</span>
      </div>
      <div class="header-right">
        <button @click="$emit('close')" class="btn-close" title="关闭">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- 参数列表 -->
    <div class="parameters-container">
      <div v-if="parameters.length === 0" class="empty-params">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>暂无参数配置</p>
        <span class="hint">点击"添加参数"按钮添加执行参数</span>
      </div>

      <div v-else class="params-list">
        <div 
          v-for="(param, index) in parameters" 
          :key="index"
          class="param-item"
        >
          <div class="param-header">
            <div class="param-name-row">
              <input 
                v-model="param.name" 
                @input="handleParamChange"
                type="text" 
                placeholder="参数名"
                class="param-name-input"
              />
              <select 
                v-model="param.type" 
                @change="handleParamChange"
                class="param-type-select"
              >
                <option value="string">字符串</option>
                <option value="number">数字</option>
                <option value="boolean">布尔值</option>
                <option value="object">对象</option>
                <option value="array">数组</option>
              </select>
            </div>
            <button @click="removeParameter(index)" class="btn-remove" title="删除参数">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
          
          <div class="param-value-section">
            <label class="param-label">参数值</label>
            
            <!-- 字符串输入 -->
            <input 
              v-if="param.type === 'string'"
              v-model="param.value" 
              @input="handleParamChange"
              type="text" 
              placeholder="请输入字符串值"
              class="param-value-input"
            />
            
            <!-- 数字输入 -->
            <input 
              v-else-if="param.type === 'number'"
              v-model.number="param.value" 
              @input="handleParamChange"
              type="number" 
              placeholder="请输入数字值"
              class="param-value-input"
            />
            
            <!-- 布尔值选择 -->
            <select 
              v-else-if="param.type === 'boolean'"
              v-model="param.value" 
              @change="handleParamChange"
              class="param-value-select"
            >
              <option :value="true">true</option>
              <option :value="false">false</option>
            </select>
            
            <!-- 对象/数组 JSON 编辑 -->
            <textarea 
              v-else
              v-model="param.value" 
              @input="handleParamChange"
              placeholder="请输入 JSON 格式"
              class="param-value-textarea"
              rows="3"
            ></textarea>
          </div>

          <!-- 参数描述（可选） -->
          <div class="param-description">
            <input 
              v-model="param.description" 
              @input="handleParamChange"
              type="text" 
              placeholder="参数描述（可选）"
              class="param-desc-input"
            />
          </div>
        </div>
      </div>

      <!-- 添加参数按钮 -->
      <button @click="addParameter" class="btn-add-param">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        <span>添加参数</span>
      </button>
    </div>

    <!-- 底部操作栏 -->
    <div class="panel-footer">
      <button @click="resetParameters" class="btn-reset">
        重置
      </button>
      <button @click="executeWithParams" class="btn-execute" :disabled="!canExecute">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="5 3 19 12 5 21 5 3"/>
        </svg>
        <span>执行工作流</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  initialParameters: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['close', 'execute']);

const parameters = ref(props.initialParameters.map(p => ({ ...p })));

const canExecute = computed(() => {
  return parameters.value.every(p => p.name && p.name.trim() !== '');
});

const addParameter = () => {
  parameters.value.push({
    name: '',
    type: 'string',
    value: '',
    description: ''
  });
};

const removeParameter = (index) => {
  parameters.value.splice(index, 1);
  handleParamChange();
};

const handleParamChange = () => {
  // 触发父组件更新
};

const resetParameters = () => {
  if (confirm('确定要重置所有参数吗？')) {
    parameters.value = [];
  }
};

const executeWithParams = () => {
  if (!canExecute.value) {
    alert('请填写所有参数名称');
    return;
  }

  // 验证并转换参数值
  const validatedParams = {};
  for (const param of parameters.value) {
    try {
      let value = param.value;
      
      // 根据类型转换值
      if (param.type === 'number') {
        value = Number(value);
        if (isNaN(value)) {
          alert(`参数 "${param.name}" 的值不是有效数字`);
          return;
        }
      } else if (param.type === 'boolean') {
        value = Boolean(value);
      } else if (param.type === 'object' || param.type === 'array') {
        if (typeof value === 'string' && value.trim()) {
          value = JSON.parse(value);
        }
      }
      
      validatedParams[param.name] = value;
    } catch (error) {
      alert(`参数 "${param.name}" 的 JSON 格式无效: ${error.message}`);
      return;
    }
  }

  emit('execute', validatedParams);
};
</script>

<style scoped>
.parameter-input-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #1e293b;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background-color: #0f172a;
  border-bottom: 1px solid #334155;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left span {
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}

.header-right {
  display: flex;
  align-items: center;
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.btn-close:hover {
  background-color: #334155;
  color: #e2e8f0;
}

.parameters-container {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.empty-params {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #64748b;
}

.empty-params p {
  margin-top: 8px;
  font-size: 13px;
}

.hint {
  margin-top: 4px;
  font-size: 11px;
  color: #475569;
}

.params-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.param-item {
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 10px;
  transition: border-color 0.2s;
}

.param-item:hover {
  border-color: #475569;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.param-name-row {
  display: flex;
  gap: 8px;
  flex: 1;
}

.param-name-input {
  flex: 1;
  background-color: #1e293b;
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 6px 8px;
  color: #e2e8f0;
  font-size: 12px;
  outline: none;
}

.param-name-input:focus {
  border-color: #3b82f6;
}

.param-type-select {
  background-color: #1e293b;
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 6px 8px;
  color: #e2e8f0;
  font-size: 12px;
  outline: none;
  cursor: pointer;
  min-width: 80px;
}

.param-type-select:focus {
  border-color: #3b82f6;
}

.btn-remove {
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  flex-shrink: 0;
}

.btn-remove:hover {
  background-color: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.param-value-section {
  margin-bottom: 8px;
}

.param-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.param-value-input,
.param-value-select,
.param-value-textarea {
  width: 100%;
  background-color: #1e293b;
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 6px 8px;
  color: #e2e8f0;
  font-size: 12px;
  outline: none;
  font-family: inherit;
}

.param-value-input:focus,
.param-value-select:focus,
.param-value-textarea:focus {
  border-color: #3b82f6;
}

.param-value-textarea {
  resize: vertical;
  font-family: 'Monaco', 'Menlo', monospace;
}

.param-description {
  margin-top: 4px;
}

.param-desc-input {
  width: 100%;
  background-color: transparent;
  border: none;
  border-bottom: 1px solid #334155;
  padding: 4px 0;
  color: #64748b;
  font-size: 11px;
  outline: none;
}

.param-desc-input::placeholder {
  color: #475569;
}

.param-desc-input:focus {
  border-bottom-color: #3b82f6;
}

.btn-add-param {
  width: 100%;
  margin-top: 12px;
  padding: 8px;
  background-color: #1e293b;
  border: 1px dashed #475569;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
}

.btn-add-param:hover {
  background-color: #334155;
  border-color: #64748b;
  color: #e2e8f0;
}

.panel-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background-color: #0f172a;
  border-top: 1px solid #334155;
  gap: 8px;
}

.btn-reset {
  padding: 8px 16px;
  background-color: #334155;
  border: none;
  border-radius: 6px;
  color: #e2e8f0;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.btn-reset:hover {
  background-color: #475569;
}

.btn-execute {
  flex: 1;
  padding: 8px 16px;
  background-color: #3b82f6;
  border: none;
  border-radius: 6px;
  color: white;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: background-color 0.2s;
}

.btn-execute:hover:not(:disabled) {
  background-color: #2563eb;
}

.btn-execute:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: #0f172a;
}

::-webkit-scrollbar-thumb {
  background: #475569;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>
