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
    
    <!-- 配置模式 -->
    <div v-if="configMode" class="code-node-config">
      <!-- 输入参数配置区 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleInputSection" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: inputSectionExpanded }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="定义代码执行时需要的输入变量">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click="addInputParam" class="add-param-btn" title="添加输入参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="inputSectionExpanded" class="section-content">
          <div class="input-params-list">
            <div 
              v-for="(param, index) in localInputParams" 
              :key="index"
              class="input-param-row"
            >
              <input 
                v-model="param.name" 
                @input="emitUpdate"
                class="param-name-input"
                placeholder="参数名"
              />
              <div class="param-value-group">
                <select v-model="param.sourceType" @change="emitUpdate" class="param-source-select">
                  <option value="input">输入</option>
                  <option value="reference">引用</option>
                </select>
                <input 
                  v-model="param.value" 
                  @input="emitUpdate"
                  class="param-value-input"
                  placeholder="请输入"
                />
              </div>
              <button @click="removeInputParam(index)" class="action-btn delete-btn" title="删除">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 代码编辑区 -->
      <div class="config-section">
        <label class="section-label">编程语言</label>
        <select v-model="localLanguage" @change="emitUpdate" class="config-select">
          <option value="javascript">JavaScript</option>
          <option value="python">Python</option>
          <option value="bash">Bash</option>
          <option value="json">JSON</option>
          <option value="sql">SQL</option>
          <option value="yaml">YAML</option>
          <option value="xml">XML</option>
          <option value="html">HTML</option>
        </select>
      </div>

      <div class="config-section">
        <div class="section-header-inline">
          <label class="section-label">代码</label>
          <button @click="showTemplates = !showTemplates" class="template-btn-inline">
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
      </div>

      <!-- 输出参数配置区 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleOutputSection" class="section-toggle-btn">
            <svg 
              width="16" 
              height="16" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              stroke-width="2"
              :class="{ rotated: outputSectionExpanded }"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="定义代码执行完成后输出的变量，必须保证此处定义的变量名、变量类型与代码的return对象中完全一致">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click="addOutputParam" class="add-param-btn" title="添加输出参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="outputSectionExpanded" class="section-content">
          <div class="params-table-wrapper">
            <table class="params-table">
              <thead>
                <tr>
                  <th class="col-name">
                    <span class="required-marker">*</span>参数名
                  </th>
                  <th class="col-type">类型</th>
                  <th class="col-desc">参数描述</th>
                  <th class="col-required">是否必传</th>
                  <th class="col-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <transition-group name="param-list">
                  <tr v-for="(param, index) in localOutputParams" :key="index" class="param-row">
                    <td class="col-name">
                      <input
                        v-model="param.name"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入"
                        class="param-name-input"
                      />
                    </td>
                    <td class="col-type">
                      <select
                        v-model="param.type"
                        @change="emitUpdate"
                        class="param-type-select"
                      >
                        <option value="string">string</option>
                        <option value="int">int</option>
                        <option value="float">float</option>
                        <option value="date">date</option>
                        <option value="datetime">datetime</option>
                        <option value="tel">tel</option>
                        <option value="boolean">boolean</option>
                        <option value="object">object</option>
                        <option value="array">array</option>
                      </select>
                    </td>
                    <td class="col-desc">
                      <input
                        v-model="param.description"
                        @input="emitUpdate"
                        type="text"
                        placeholder="请输入参数说明"
                        class="param-desc-input"
                      />
                    </td>
                    <td class="col-required">
                      <div 
                        class="required-toggle" 
                        :class="{ active: param.required }"
                        @click="toggleOutputRequired(index)"
                      >
                      </div>
                    </td>
                    <td class="col-action">
                      <button 
                        @click="removeOutputParam(index)" 
                        class="action-btn delete-btn" 
                        title="删除"
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <line x1="18" y1="6" x2="6" y2="18"/>
                          <line x1="6" y1="6" x2="18" y2="18"/>
                        </svg>
                      </button>
                    </td>
                  </tr>
                </transition-group>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>
    
    <!-- 非配置模式的普通视图 -->
    <div v-else-if="!compact" class="node-body">
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

      <div v-if="showAdvanced" class="advanced-panel">
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
const inputSectionExpanded = ref(true);
const outputSectionExpanded = ref(true);

const localLanguage = ref(props.data.language || 'javascript');
const localCode = ref(props.data.code || '');
const localTimeout = ref(props.data.timeout || 30);
const localAsync = ref(props.data.isAsync || false);
const localSandbox = ref(props.data.sandbox !== false);
const localOutputType = ref(props.data.outputType || 'auto');
const localReturnJson = ref(props.data.returnJson || false);
const localEnvVars = ref(props.data.envVars || []);
const localInputParams = ref(props.data.inputParams || []);
const localOutputParams = ref(props.data.outputParams || []);

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

const toggleInputSection = () => {
  inputSectionExpanded.value = !inputSectionExpanded.value;
};

const toggleOutputSection = () => {
  outputSectionExpanded.value = !outputSectionExpanded.value;
};

const getTypeDescription = (type) => {
  const descriptions = {
    string: '文本字符串',
    int: '整数',
    float: '浮点数',
    date: '日期 (YYYY-MM-DD)',
    datetime: '日期时间 (YYYY-MM-DD HH:mm:ss)',
    tel: '电话号码',
    boolean: '布尔值 (true/false)',
    object: '对象',
    array: '数组'
  };
  return descriptions[type] || type;
};

const addInputParam = () => {
  localInputParams.value.push({ name: '', sourceType: 'input', value: '' });
  emitUpdate();
};

const removeInputParam = (index) => {
  if (localInputParams.value.length > 0) {
    localInputParams.value.splice(index, 1);
    emitUpdate();
  }
};

const toggleRequired = (index) => {
  if (localInputParams.value[index]) {
    localInputParams.value[index].required = !localInputParams.value[index].required;
    emitUpdate();
  }
};

const addOutputParam = () => {
  localOutputParams.value.push({ name: '', type: 'string', description: '', required: false });
  emitUpdate();
};

const removeOutputParam = (index) => {
  if (localOutputParams.value.length > 0) {
    localOutputParams.value.splice(index, 1);
    emitUpdate();
  }
};

const toggleOutputRequired = (index) => {
  localOutputParams.value[index].required = !localOutputParams.value[index].required;
  emitUpdate();
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
    inputParams: localInputParams.value,
    outputParams: localOutputParams.value
  });
};

watch(() => props.data, (d) => {
  localLanguage.value = d.language || 'javascript';
  localCode.value = d.code || '';
  localInputParams.value = d.inputParams || [];
  localOutputParams.value = d.outputParams || [];
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

/* 配置模式样式 */
.code-node-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 4px;
}

.config-select {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  color: #334155;
  background: white;
  cursor: pointer;
  transition: all 0.2s;
}

.config-select:hover {
  border-color: #cbd5e1;
}

.config-select:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* 可折叠区域样式 */
.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: white;
  border-bottom: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.section-header:hover {
  background: #f8fafc;
}

.collapsible-section .section-content + .section-header,
.section-header:not(:last-child) {
  border-bottom: 1px solid #e2e8f0;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  cursor: pointer;
  padding: 0;
}

.section-toggle-btn svg {
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.help-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: #94a3b8;
  cursor: help;
  border-radius: 4px;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #f1f5f9;
  color: #64748b;
}

.add-param-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #3b82f6;
  border: none;
  color: white;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #2563eb;
}

.section-content {
  padding: 12px;
  animation: slideDown 0.2s ease;
}

/* 输入参数列表样式 */
.input-params-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-param-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.input-param-row .param-name-input {
  width: 140px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.input-param-row .param-name-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-value-group {
  flex: 1;
  display: flex;
  gap: 8px;
}

.param-source-select {
  width: 100px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  background: white;
  cursor: pointer;
}

.param-source-select:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-value-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.param-value-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
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

/* 参数表格样式 */
.params-table-wrapper {
  overflow-x: auto;
}

.params-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.params-table thead {
  background: #f8fafc;
}

.params-table th {
  padding: 8px 10px;
  text-align: left;
  font-weight: 600;
  color: #475569;
  border-bottom: 2px solid #e2e8f0;
  white-space: nowrap;
}

.required-marker {
  color: #ef4444;
  margin-right: 2px;
}

.col-name {
  width: 140px;
}

.col-type {
  width: 100px;
}

.col-desc {
  min-width: 150px;
}

.col-required {
  width: 80px;
  text-align: center;
}

.col-action {
  width: 50px;
  text-align: center;
}

.param-row {
  transition: background 0.2s;
}

.param-row:hover {
  background: #f8fafc;
}

.params-table td {
  padding: 6px 8px;
  border-bottom: 1px solid #f1f5f9;
}

.param-name-input,
.param-desc-input {
  width: 100%;
  padding: 5px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  transition: all 0.2s;
}

.param-name-input:focus,
.param-desc-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-type-select {
  width: 100%;
  padding: 5px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  background: white;
  cursor: pointer;
}

.param-type-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.required-toggle {
  width: 36px;
  height: 20px;
  background: #cbd5e1;
  border-radius: 10px;
  position: relative;
  cursor: pointer;
  transition: all 0.2s;
  margin: 0 auto;
}

.required-toggle.active {
  background: #3b82f6;
}

.required-toggle::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: white;
  border-radius: 50%;
  transition: all 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.required-toggle.active::after {
  left: 18px;
}

.action-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
  margin: 0 auto;
}

.delete-btn {
  color: #94a3b8;
}

.delete-btn:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* 列表动画 */
.param-list-enter-active,
.param-list-leave-active {
  transition: all 0.3s ease;
}

.param-list-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.param-list-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

/* 代码编辑区样式 */
.section-header-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.template-btn-inline {
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: white;
  color: #64748b;
  cursor: pointer;
  font-size: 11px;
  transition: all 0.2s;
}

.template-btn-inline:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.code-textarea {
  width: 100%;
  min-height: 180px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  resize: vertical;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
  line-height: 1.5;
  background: #fafafa;
  transition: all 0.2s;
}

.code-textarea:focus {
  outline: none;
  border-color: #3b82f6;
  background: white;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.templates-panel {
  margin-top: 8px;
  padding: 10px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  animation: slideDown 0.2s ease;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.template-item {
  display: flex;
  flex-direction: column;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  align-items: flex-start;
  transition: all 0.2s;
}

.template-item:hover {
  background: #dbeafe;
  border-color: #3b82f6;
}

.template-name {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 2px;
}

.template-desc {
  font-size: 11px;
  color: #64748b;
}

/* 执行配置样式 */
.config-row {
  margin-bottom: 10px;
}

.config-row:last-child {
  margin-bottom: 0;
}

.config-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 6px;
}

.config-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-number-input {
  width: 80px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.config-number-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.config-unit {
  font-size: 12px;
  color: #64748b;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

/* 环境变量样式 */
.env-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.env-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.env-name {
  width: 120px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.env-name:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.env-value {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
}

.env-value:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.collapse-btn {
  display: flex;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.collapse-btn button {
  padding: 6px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: white;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn button:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

/* 非配置模式的原有样式 */
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

.node-select {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  margin-bottom: 8px;
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