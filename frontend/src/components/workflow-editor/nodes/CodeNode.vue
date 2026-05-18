<template>
  <div class="node code-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">💻</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ codeSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <div class="code-header">
        <select v-model="localLanguage" @change="emitUpdate" class="lang-select">
          <option value="javascript">JavaScript</option>
          <option value="python">Python</option>
          <option value="bash">Bash</option>
          <option value="json">JSON</option>
          <option value="sql">SQL</option>
          <option value="yaml">YAML</option>
          <option value="xml">XML</option>
          <option value="html">HTML</option>
        </select>
        <button @click="showTemplates = !showTemplates" class="template-btn">
          📋 模板
        </button>
      </div>

      <textarea 
        v-model="localCode" 
        @input="emitUpdate" 
        :placeholder="getPlaceholder()"
        class="code-textarea"
        spellcheck="false"
      ></textarea>

      <div v-if="showTemplates" class="templates-panel">
        <div class="section-title">代码模板</div>
        <div class="template-list">
          <button 
            v-for="template in codeTemplates" 
            :key="template.name" 
            @click="applyTemplate(template)"
            class="template-item"
          >
            <span class="template-name">{{ template.name }}</span>
            <span class="template-desc">{{ template.description }}</span>
          </button>
        </div>
      </div>

      <div v-if="configMode || showAdvanced" class="advanced-panel">
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
          <input v-model="localSandbox" @change="emitUpdate" type="checkbox" />
          <span>沙箱模式</span>
        </label>

        <div class="section-title">输出配置</div>
        <select v-model="localOutputType" @change="emitUpdate" class="node-select">
          <option value="auto">自动检测</option>
          <option value="string">字符串</option>
          <option value="json">JSON对象</option>
          <option value="number">数字</option>
          <option value="array">数组</option>
        </select>

        <label class="checkbox-label">
          <input v-model="localReturnJson" @change="emitUpdate" type="checkbox" />
          <span>返回JSON格式</span>
        </label>

        <div class="section-title">环境变量</div>
        <div class="env-container">
          <div 
            v-for="(env, index) in localEnvVars" 
            :key="index" 
            class="env-row"
          >
            <input 
              v-model="env.name" 
              @input="emitUpdate" 
              placeholder="变量名" 
              class="env-name" 
            />
            <input 
              v-model="env.value" 
              @input="emitUpdate" 
              placeholder="变量值" 
              class="env-value" 
            />
            <button @click="removeEnv(index)" class="remove-env-btn">✕</button>
          </div>
          <button @click="addEnv" class="add-env-btn">+ 添加环境变量</button>
        </div>
      </div>
    </div>
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  ...nodeDisplayProps
});

const emit = defineEmits(['update']);

const showAdvanced = ref(false);
const showTemplates = ref(false);

const localLanguage = ref(props.data.language || 'javascript');
const localCode = ref(props.data.code || '');
const localTimeout = ref(props.data.timeout || 30);
const localAsync = ref(props.data.isAsync || false);
const localSandbox = ref(props.data.sandbox !== false);
const localOutputType = ref(props.data.outputType || 'auto');
const localReturnJson = ref(props.data.returnJson || false);
const localEnvVars = ref(props.data.envVars || []);

const codeSummary = computed(() => {
  const lang = localLanguage.value || 'javascript';
  const lines = (localCode.value || '').split('\n').length;
  return `${lang} · ${lines} 行`;
});

const codeTemplates = [
  { name: 'HTTP请求', description: '发送HTTP请求', language: 'javascript', code: "const response = await fetch('https://api.example.com/data', {\n  method: 'GET',\n  headers: { 'Content-Type': 'application/json' }\n});\nconst data = await response.json();\nreturn data;" },
  { name: 'JSON解析', description: '解析JSON字符串', language: 'javascript', code: "const jsonString = '{{input}}';\ntry {\n  const result = JSON.parse(jsonString);\n  return result;\n} catch (e) {\n  console.error('JSON解析失败:', e);\n  return null;\n}" },
  { name: '数组处理', description: '过滤和映射数组', language: 'javascript', code: "const items = {{input}};\nconst filtered = items\n  .filter(item => item.active)\n  .map(item => ({ id: item.id, name: item.name }));\nreturn filtered;" },
  { name: '日期格式化', description: '格式化日期时间', language: 'javascript', code: "const date = new Date();\nconst formatted = date.toLocaleString('zh-CN', {\n  year: 'numeric',\n  month: '2-digit',\n  day: '2-digit',\n  hour: '2-digit',\n  minute: '2-digit'\n});\nreturn formatted;" },
  { name: '数据库查询', description: '执行SQL查询', language: 'sql', code: "SELECT id, name, created_at\nFROM users\nWHERE status = 'active'\nAND created_at >= '{{startDate}}'\nORDER BY created_at DESC\nLIMIT 10;" },
  { name: '文件读取', description: '读取文件内容', language: 'python', code: "with open('{{filePath}}', 'r', encoding='utf-8') as f:\n    content = f.read()\nprint(content)\nreturn content" },
  { name: '环境变量', description: '获取环境变量', language: 'javascript', code: "const apiKey = process.env.API_KEY;\nconst baseUrl = process.env.BASE_URL || 'https://api.example.com';\nreturn { apiKey, baseUrl };" },
  { name: '数据转换', description: '转换数据格式', language: 'javascript', code: "const input = {{input}};\nconst output = {\n  id: input.id,\n  fullName: `${input.firstName} ${input.lastName}`,\n  email: input.email?.toLowerCase(),\n  createdAt: new Date(input.createdAt).toISOString()\n};\nreturn output;" }
];

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const getPlaceholder = () => {
  switch (localLanguage.value) {
    case 'javascript':
      return '// 输入 JavaScript 代码\n// 使用 {{input}} 访问输入数据\n// 使用 return 返回结果';
    case 'python':
      return '# 输入 Python 代码\n# 使用 input 访问输入数据\n# 使用 return 返回结果';
    case 'bash':
      return '# 输入 Bash 命令\n# 使用 $INPUT 访问输入';
    case 'sql':
      return '-- 输入 SQL 查询语句';
    case 'json':
      return '{\n  "key": "value"\n}';
    case 'yaml':
      return 'key:\n  subkey: value';
    case 'xml':
      return '<root>\n  <item>value</item>\n</root>';
    case 'html':
      return '<div>\n  <p>Hello</p>\n</div>';
    default:
      return '输入代码...';
  }
};

const applyTemplate = (template) => {
  if (template.language === localLanguage.value || confirm(`切换语言为 ${template.language}？`)) {
    localLanguage.value = template.language;
    localCode.value = template.code;
    showTemplates.value = false;
    emitUpdate();
  }
};

const addEnv = () => {
  localEnvVars.value.push({ name: '', value: '' });
  emitUpdate();
};

const removeEnv = (index) => {
  if (localEnvVars.value.length > 0) {
    localEnvVars.value.splice(index, 1);
    emitUpdate();
  }
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    language: localLanguage.value,
    code: localCode.value,
    timeout: localTimeout.value,
    isAsync: localAsync.value,
    sandbox: localSandbox.value,
    outputType: localOutputType.value,
    returnJson: localReturnJson.value,
    envVars: localEnvVars.value
  });
};

watch(() => props.data, (d) => {
  localLanguage.value = d.language || 'javascript';
  localCode.value = d.code || '';
  localTimeout.value = d.timeout || 30;
  localAsync.value = d.isAsync || false;
  localSandbox.value = d.sandbox !== false;
  localOutputType.value = d.outputType || 'auto';
  localReturnJson.value = d.returnJson || false;
  localEnvVars.value = d.envVars || [];
}, { deep: true });
</script>

<style scoped>
.code-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 280px;
  min-height: 200px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.code-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.code-node.is-compact {
  min-width: 160px;
}

.node-compact-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compact-summary {
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compact-hint {
  font-size: 10px;
  color: #94a3b8;
}

.code-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #ec4899 0%, #be185d 100%);
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

.code-header {
  display: flex;
  gap: 6px;
  align-items: center;
}

.lang-select {
  flex: 1;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.template-btn {
  padding: 4px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: white;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.template-btn:hover {
  background: #f8fafc;
}

.code-textarea {
  width: 100%;
  min-height: 120px;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
  line-height: 1.4;
  background: #fafafa;
}

.code-textarea:focus {
  outline: none;
  border-color: #3b82f6;
  background: white;
}

.templates-panel {
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
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

.template-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.template-item {
  display: flex;
  flex-direction: column;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  background: white;
  cursor: pointer;
  align-items: flex-start;
}

.template-item:hover {
  background: #dbeafe;
  border-color: #3b82f6;
}

.template-name {
  font-size: 11px;
  font-weight: 500;
  color: #334155;
}

.template-desc {
  font-size: 10px;
  color: #64748b;
}

.advanced-panel {
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #cbd5e1;
  animation: slideDown 0.2s ease;
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

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 8px;
}

.env-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.env-row {
  display: flex;
  gap: 4px;
}

.env-name {
  width: 80px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.env-value {
  flex: 1;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.remove-env-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #fee2e2;
  border-radius: 4px;
  color: #dc2626;
  cursor: pointer;
  font-size: 10px;
}

.add-env-btn {
  padding: 3px 8px;
  border: 1px dashed #cbd5e1;
  border-radius: 3px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.add-env-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
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