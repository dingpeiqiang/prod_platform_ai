<template>
  <div class="node tool-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔧</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div class="node-body">
      <select v-model="localToolName" @change="onToolChange" class="node-select">
        <option value="">选择工具</option>
        <option value="web_search">网页搜索</option>
        <option value="database_query">数据库查询</option>
        <option value="file_read">文件读取</option>
        <option value="file_write">文件写入</option>
        <option value="api_call">API调用</option>
        <option value="email_send">发送邮件</option>
        <option value="shell_exec">执行命令</option>
        <option value="image_generate">图像生成</option>
        <option value="document_summary">文档摘要</option>
      </select>

      <div v-if="showAdvanced && localToolName" class="advanced-panel">
        <div class="section-title">工具参数</div>
        <div class="params-container">
          <div 
            v-for="(param, index) in localParams" 
            :key="index" 
            class="param-row"
          >
            <input 
              v-model="param.name" 
              @input="emitUpdate" 
              placeholder="参数名" 
              class="param-name" 
            />
            <select v-model="param.type" @change="emitUpdate" class="param-type">
              <option value="string">字符串</option>
              <option value="number">数字</option>
              <option value="boolean">布尔值</option>
              <option value="array">数组</option>
              <option value="object">对象</option>
              <option value="variable">变量引用</option>
            </select>
            <input 
              v-model="param.value" 
              @input="emitUpdate" 
              :placeholder="getParamPlaceholder(param.type)"
              class="param-value" 
            />
            <button @click="removeParam(index)" class="remove-param-btn">✕</button>
          </div>
          <button @click="addParam" class="add-param-btn">+ 添加参数</button>
        </div>

        <div class="section-title">执行配置</div>
        <div class="timeout-row">
          <label>超时时间</label>
          <input 
            v-model.number="localTimeout" 
            @input="emitUpdate" 
            type="number" 
            min="1" 
            max="600" 
            class="timeout-input"
          />
          <span class="timeout-unit">秒</span>
        </div>

        <label class="checkbox-label">
          <input v-model="localAsync" @change="emitUpdate" type="checkbox" />
          <span>异步执行</span>
        </label>

        <label class="checkbox-label">
          <input v-model="localSilent" @change="emitUpdate" type="checkbox" />
          <span>静默模式（不输出日志）</span>
        </label>

        <div v-if="toolDescriptions[localToolName]" class="tool-description">
          <div class="section-title">工具说明</div>
          <p>{{ toolDescriptions[localToolName] }}</p>
        </div>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
    <Handle type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
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

const localToolName = ref(props.data.toolName || '');
const localParams = ref(props.data.params || []);
const localTimeout = ref(props.data.timeout || 30);
const localAsync = ref(props.data.isAsync || false);
const localSilent = ref(props.data.silent || false);

const toolDescriptions = {
  web_search: '使用搜索引擎获取网页信息，支持关键词搜索和结果过滤',
  database_query: '执行 SQL 查询语句，支持 MySQL、PostgreSQL 等主流数据库',
  file_read: '读取本地文件内容，支持文本文件、JSON、CSV 等格式',
  file_write: '写入内容到本地文件，支持创建和追加模式',
  api_call: '调用外部 API 接口，支持自定义请求参数',
  email_send: '发送邮件通知，支持附件和 HTML 格式',
  shell_exec: '执行系统命令，注意安全风险',
  image_generate: '根据文本描述生成图像',
  document_summary: '对文档内容进行摘要提取和关键信息分析'
};

const toolDefaultParams = {
  web_search: [
    { name: 'query', type: 'string', value: '' },
    { name: 'max_results', type: 'number', value: '10' }
  ],
  database_query: [
    { name: 'query', type: 'string', value: '' },
    { name: 'connection', type: 'string', value: 'default' }
  ],
  file_read: [
    { name: 'path', type: 'string', value: '' },
    { name: 'encoding', type: 'string', value: 'utf-8' }
  ],
  file_write: [
    { name: 'path', type: 'string', value: '' },
    { name: 'content', type: 'string', value: '' },
    { name: 'mode', type: 'string', value: 'write' }
  ],
  api_call: [
    { name: 'url', type: 'string', value: '' },
    { name: 'method', type: 'string', value: 'GET' }
  ],
  email_send: [
    { name: 'to', type: 'string', value: '' },
    { name: 'subject', type: 'string', value: '' },
    { name: 'body', type: 'string', value: '' }
  ],
  shell_exec: [
    { name: 'command', type: 'string', value: '' },
    { name: 'cwd', type: 'string', value: '' }
  ],
  image_generate: [
    { name: 'prompt', type: 'string', value: '' },
    { name: 'width', type: 'number', value: '1024' },
    { name: 'height', type: 'number', value: '1024' }
  ],
  document_summary: [
    { name: 'text', type: 'string', value: '' },
    { name: 'max_tokens', type: 'number', value: '300' }
  ]
};

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const onToolChange = () => {
  if (toolDefaultParams[localToolName.value]) {
    localParams.value = JSON.parse(JSON.stringify(toolDefaultParams[localToolName.value]));
  } else {
    localParams.value = [{ name: '', type: 'string', value: '' }];
  }
  emitUpdate();
};

const addParam = () => {
  localParams.value.push({ name: '', type: 'string', value: '' });
  emitUpdate();
};

const removeParam = (index) => {
  if (localParams.value.length > 1) {
    localParams.value.splice(index, 1);
    emitUpdate();
  }
};

const getParamPlaceholder = (type) => {
  switch (type) {
    case 'string':
      return '字符串值';
    case 'number':
      return '数字';
    case 'boolean':
      return 'true/false';
    case 'array':
      return '[...]';
    case 'object':
      return '{...}';
    case 'variable':
      return '{{变量名}}';
    default:
      return '参数值';
  }
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    toolName: localToolName.value,
    params: localParams.value,
    timeout: localTimeout.value,
    isAsync: localAsync.value,
    silent: localSilent.value
  });
};

watch(() => props.data, (d) => {
  localToolName.value = d.toolName || '';
  localParams.value = d.params || [];
  localTimeout.value = d.timeout || 30;
  localAsync.value = d.isAsync || false;
  localSilent.value = d.silent || false;
  
  if (localToolName.value && localParams.value.length === 0) {
    if (toolDefaultParams[localToolName.value]) {
      localParams.value = JSON.parse(JSON.stringify(toolDefaultParams[localToolName.value]));
    }
  }
}, { deep: true });
</script>

<style scoped>
.tool-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 260px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.tool-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
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

.node-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  background: white;
}

.node-select:focus {
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

.params-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.param-row {
  display: flex;
  gap: 4px;
}

.param-name {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.param-type {
  width: 70px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 10px;
}

.param-value {
  flex: 1;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.remove-param-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #fee2e2;
  border-radius: 4px;
  color: #dc2626;
  cursor: pointer;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-param-btn {
  padding: 4px 8px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 11px;
  text-align: left;
}

.add-param-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.timeout-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.timeout-row label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.timeout-input {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.timeout-unit {
  font-size: 11px;
  color: #64748b;
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

.tool-description {
  margin-top: 8px;
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
}

.tool-description p {
  font-size: 11px;
  color: #64748b;
  margin: 0;
  line-height: 1.4;
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