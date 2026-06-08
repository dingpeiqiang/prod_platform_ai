<template>
  <div class="node code-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">💻</span>
      <span class="node-title">{{ data.label }}</span>
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
          <div class="input-param-container">
            <!-- 输入参数表头 -->
            <div class="input-param-header">
              <span class="header-col header-name">参数名</span>
              <span class="header-col header-type">类型</span>
              <span class="header-col header-value">值</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localInputParams" :key="index">
              <div v-if="param" class="input-param-item">
                <div class="param-name-cell">
                  <input v-model="param.name" @input="emitUpdate" placeholder="参数名" class="param-name-input" :class="{ error: !param.name }"/>
                </div>
                <div class="param-type-cell">
                  <select v-model="param.valueType" @change="handleValueTypeChange(index)" class="param-type-select">
                    <option value="input">自定义</option>
                    <option value="reference">引用</option>
                  </select>
                </div>
                <div class="param-value-cell">
                  <input 
                    v-if="param.valueType === 'input'" 
                    v-model="param.defaultValue" 
                    @input="emitUpdate" 
                    placeholder="默认值" 
                    class="param-default-input"
                  />
                  <VariableCascader
                    v-if="param.valueType === 'reference'"
                    v-model="param.refValue"
                    :available-variables="availableVariables"
                    placeholder="请选择变量"
                    class="param-cascader"
                    @change="(val) => handleCascaderChange(index, val)"
                  />
                </div>
                <div class="param-action-cell">
                  <button @click="removeInputParam(index)" class="action-btn delete-btn" title="删除">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </template>
          </div>
          <div v-if="localInputParams.some(p => p && !p.name)" class="error-message">参数名不能为空</div>
          <div v-if="localInputParams.some(p => p && p.valueType === 'reference' && !p.refValue)" class="error-message">引用变量不能为空</div>
        </div>
      </div>

      <!-- 代码编辑区 -->
      <div class="config-section">
        <label class="section-label">编程语言</label>
        <div class="config-select disabled">Python</div>
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
                  <line x1="5" y1="12"/>
                </svg>
              </button>
              <button @click="autoDetectOutputParams" class="auto-detect-btn" title="从代码中自动识别输出参数">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                </svg>
                自动识别
              </button>
          </div>
        </div>
        
        <div v-if="outputSectionExpanded" class="section-content">
          <div v-if="detectedParams.length > 0" class="detected-params-banner">
            <span class="detected-count">检测到 {{ detectedParams.length }} 个输出字段</span>
            <button @click="applyDetectedParams" class="apply-btn">应用检测结果</button>
          </div>
          <!-- 输出参数容器 -->
          <div class="output-param-container">
            <!-- 输出参数表头 -->
            <div class="output-param-header">
              <span class="header-col header-type">类型</span>
              <span class="header-col header-name">参数名/变量</span>
              <span class="header-col header-data-type">数据类型</span>
              <span class="header-col header-desc">描述</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localOutputs" :key="index">
            <div v-if="param" class="output-param-item">
              <select v-model="param.nameType" @change="handleOutputNameTypeChange(index)" class="param-name-type-select">
                <option value="input">自定义</option>
                <option value="reference">引用</option>
              </select>
              <div class="param-name-group">
                <input
                  v-if="param.nameType === 'input'"
                  v-model="param.name"
                  @input="emitUpdate"
                  type="text"
                  placeholder="参数名"
                  class="param-name-input"
                />
                <VariableCascader
                  v-else
                  :key="'output-ref-' + index + '-' + cascaderRefreshKey"
                  v-model="param.nameRef"
                  :available-variables="availableVariables"
                  placeholder="选择变量"
                  class="param-name-cascader"
                  @change="emitUpdate"
                />
              </div>
              <select v-model="param.type" @change="emitUpdate" class="param-type-select">
                <option value="string">string</option>
                <option value="json">json</option>
              </select>
              <input v-model="param.description" @input="emitUpdate" placeholder="描述" class="param-desc-input" />
              <div class="param-action-cell">
                <button @click="removeOutputParam(index)" class="action-btn delete-btn" title="删除">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </div>
          </template>
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
          <option value="python">Python</option>
          <option value="javascript">JavaScript</option>
          <option value="bash">Bash</option>
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
    </div>
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';
import VariableCascader from '../VariableCascader.vue';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  availableVariables: {
    type: Array,
    default: () => []
  },
  availableNodes: {
    type: Array,
    default: () => []
  },
  ...nodeDisplayProps
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update']);

const showAdvanced = ref(false);
const showTemplates = ref(false);
const inputSectionExpanded = ref(true);
const outputSectionExpanded = ref(true);

const localLanguage = ref('python');
const localCode = ref(props.data.code || '');
// 输入参数 - 标准格式
const localInputParams = ref((props.data.inputParams || []).map(p => ({
  name: p.name || '',
  valueType: p.valueType || p.sourceType || 'input',
  defaultValue: p.defaultValue || p.value || '',
  refValue: p.refValue || ''
})));
const localOutputs = ref((props.data.outputParams || []).map(p => ({
  name: p.name || '',
  nameType: p.nameType || 'input',
  nameRef: p.nameRef || '',
  source: p.source || '',
  type: p.type || 'string',
  description: p.description || p.desc || ''
})));
const detectedParams = ref([]);

// 强制 VariableCascader 刷新 key，当 availableVariables 变化时重新挂载
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

const codeSummary = computed(() => {
  const lines = (localCode.value || '').split('\n').length;
  return `Python · ${lines} 行`;
});

const codeTemplates = [
  { name: '数据处理', description: 'Python数据处理示例', language: 'python', code: "# 输入数据处理\ninput_data = input\nresult = {\n    'count': len(input_data) if isinstance(input_data, list) else 1,\n    'processed': True,\n    'timestamp': __import__('datetime').datetime.now().isoformat()\n}" },
  { name: 'HTTP请求', description: '发送HTTP请求', language: 'python', code: "import requests\nresponse = requests.get('https://api.example.com/data')\nresult = response.json()" },
  { name: 'JSON解析', description: '解析JSON字符串', language: 'python', code: "import json\njson_string = '{{input}}'\ntry:\n    result = json.loads(json_string)\nexcept Exception as e:\n    result = {'error': str(e)}" },
  { name: '变量提取', description: '从输入数据中提取变量', language: 'python', code: "# 从输入数据中提取变量\ninput_data = input\ntariff_code = input_data.get('tariff_code', '')\nresult = {\n    'tariff_code': tariff_code\n}" },
  { name: '数据过滤', description: '过滤和转换数据', language: 'python', code: "# 过滤和转换数据\ninput_data = input\n\n# 过滤空值\nfiltered = {k: v for k, v in input_data.items() if v is not None and v != ''}\n\n# 添加时间戳\nfiltered['processed_at'] = __import__('datetime').datetime.now().isoformat()\n\nresult = filtered" },
  { name: '列表处理', description: '处理列表数据', language: 'python', code: "# 处理列表数据\ninput_list = input\n\n# 假设输入是列表\nif isinstance(input_list, list):\n    processed = []\n    for item in input_list:\n        # 处理每个元素\n        processed.append({\n            'value': item,\n            'processed': True\n        })\n    result = {'items': processed, 'count': len(processed)}\nelse:\n    result = {'error': '输入不是列表'}" }
];

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
  localInputParams.value.push({ name: '', valueType: 'input', defaultValue: '', refValue: '' });
  emitUpdate();
};

const removeInputParam = (index) => {
  if (localInputParams.value.length > 0) {
    localInputParams.value.splice(index, 1);
    emitUpdate();
  }
};

const handleValueTypeChange = (index) => {
  const param = localInputParams.value[index];
  if (param.valueType === 'reference') {
    param.defaultValue = '';
  } else {
    param.refValue = '';
  }
  emitUpdate();
};

const handleCascaderChange = (index, value) => {
  localInputParams.value[index].refValue = value;
  emitUpdate();
};

const addOutputParam = () => {
  localOutputs.value.push({ name: '', nameType: 'input', nameRef: '', source: '{{__output__}}', type: 'string', description: '' });
  emitUpdate();
};

const handleOutputNameTypeChange = (index) => {
  const param = localOutputs.value[index];
  if (param.nameType === 'reference') {
    param.name = '';
    param.source = '';
    param.description = '';
  } else {
    param.nameRef = '';
  }
  emitUpdate();
};

const removeOutputParam = (index) => {
  if (localOutputs.value.length > 0) {
    localOutputs.value.splice(index, 1);
    emitUpdate();
  }
};

const autoDetectOutputParams = () => {
  const code = localCode.value || '';
  const detected = [];
  detectPythonVariables(code, detected);
  detectedParams.value = detected;
};

const detectPythonVariables = (code, detected) => {
  const lines = code.split('\n');
  const reservedWords = new Set([
    'True', 'False', 'None', 'if', 'else', 'elif', 'for', 'while', 'def', 
    'class', 'import', 'from', 'return', 'yield', 'try', 'except', 'finally',
    'with', 'as', 'lambda', 'pass', 'break', 'continue', 'and', 'or', 'not',
    'is', 'in', 'del', 'global', 'nonlocal', 'context', 'variables', 'output',
    'input', '__node_output__', 'set_var', 'get_var', 'result'
  ]);
  
  const varPattern = /^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*=/;
  const assignmentPattern = /([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.+)/;
  
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    
    const varMatch = trimmed.match(varPattern);
    if (varMatch) {
      const varName = varMatch[1];
      if (!reservedWords.has(varName)) {
        const typeMatch = trimmed.match(assignmentPattern);
        if (typeMatch) {
          const value = typeMatch[2].trim();
          const inferredType = inferTypeFromValue(value);
          
          if (!detected.find(p => p.name === varName)) {
            detected.push({
              name: varName,
              source: '',
              type: inferredType,
              description: `自动检测: ${varName}`
            });
          }
        }
      }
    }
  }
};

const inferTypeFromValue = (value) => {
  if (!value) return 'string';
  
  if (/^\d+$/.test(value)) return 'int';
  if (/^\d+\.\d+$/.test(value)) return 'float';
  if (/^(true|false)$/i.test(value)) return 'boolean';
  if (/^\[.*\]$/.test(value)) return 'array';
  if (/^\{.*\}$/.test(value)) return 'object';
  if (/^['"].*['"]$/.test(value)) return 'string';
  
  return 'string';
};

const applyDetectedParams = () => {
  localOutputs.value = [...detectedParams.value];
  detectedParams.value = [];
  emitUpdate();
};

const getPlaceholder = () => {
  return '# 输入 Python 代码\n# 使用 input 访问输入数据\n# 设置 result 变量返回结果\n\n# 示例:\n# input_data = input\n# result = {}\n# result[\"output_key\"] = input_data.get(\"key\")';
};

const applyTemplate = (template) => {
  localCode.value = template.code;
  showTemplates.value = false;
  emitUpdate();
};

const emitUpdate = () => {
  // 将前端输入参数格式映射为规范的 inputParams 格式
  const inputParams = localInputParams.value
    .filter(p => p && p.name)
    .map(p => ({
      name: p.name,
      valueType: p.valueType,
      defaultValue: p.valueType === 'input' ? p.defaultValue : undefined,
      refValue: p.valueType === 'reference' ? p.refValue : undefined
    }));

  // 将前端输出参数格式映射为规范的 outputParams 格式
  const outputParams = localOutputs.value
    .filter(p => p)
    .map(p => ({
      name: p.name || '',
      nameType: p.nameType || 'input',
      nameRef: p.nameRef || '',
      source: p.source || '',
      type: p.type || 'string',
      description: p.description || ''
    }));

  emit('update', props.data.id, {
    language: 'python',
    code: localCode.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
};

watch(() => props.data, (d) => {
  localCode.value = d.code || '';
  localInputParams.value = (d.inputParams || []).map(p => ({
    name: p.name || '',
    valueType: p.valueType || p.sourceType || 'input',
    defaultValue: p.defaultValue || p.value || '',
    refValue: p.refValue || ''
  }));
  localOutputs.value = (d.outputParams || []).map(p => ({
    name: p.name || '',
    nameType: p.nameType || 'input',
    nameRef: p.nameRef || '',
    source: p.source || '',
    type: p.type || 'string',
    description: p.description || p.desc || ''
  }));
}, { deep: true });
</script>

<style scoped>
.code-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.code-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.code-node.is-compact {
  min-width: 180px;
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

.auto-detect-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #10b981;
  border-radius: 4px;
  background: white;
  color: #10b981;
  cursor: pointer;
  font-size: 11px;
  transition: all 0.2s;
}

.auto-detect-btn:hover {
  background: #ecfdf5;
  border-color: #059669;
}

.detected-params-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #ecfdf5;
  border: 1px solid #86efac;
  border-radius: 6px;
  margin-bottom: 10px;
}

.detected-count {
  font-size: 12px;
  color: #065f46;
  font-weight: 500;
}

.apply-btn {
  padding: 4px 12px;
  background: #10b981;
  border: none;
  border-radius: 4px;
  color: white;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.apply-btn:hover {
  background: #059669;
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

/* 输入参数容器支持横向滚动 */
.input-param-container {
  overflow-x: auto;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

/* 输入参数表头 */
.input-param-header {
  display: flex;
  align-items: center;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 12px;
  color: #64748b;
  padding: 8px;
  min-width: max-content;
}

.header-col {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.header-col.header-name {
  width: 120px;
}

.input-param-header .header-col.header-type {
  width: 90px;
}

.header-col.header-value {
  flex: 1;
  min-width: 200px;
}

.header-col.header-action {
  width: 40px;
  display: flex;
  justify-content: center;
}

/* 输入参数列表样式 */
.input-param-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border-top: 1px solid #e5e7eb;
  min-width: max-content;
}

.input-param-item:first-child {
  border-top: none;
}

.param-name-cell {
  width: 120px;
}

.param-name-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-name-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.param-name-input.error {
  border-color: #ef4444;
}

.param-type-cell {
  width: 90px;
}

.param-type-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 6px center;
  background-repeat: no-repeat;
  background-size: 12px;
  box-sizing: border-box;
}

.param-type-select:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-value-cell {
  flex: 1;
  min-width: 200px;
  margin-left: 8px;
}

.param-default-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-default-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-cascader {
  width: 100%;
}

.param-action-cell {
  width: 40px;
  display: flex;
  justify-content: center;
  margin-left: 8px;
}

.error-message {
  margin-top: 8px;
  padding: 6px 8px;
  background: #fef2f2;
  border: 1px solid #fee2e2;
  border-radius: 4px;
  font-size: 11px;
  color: #dc2626;
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

/* 输出参数样式 */
/* 输出参数容器支持横向滚动 */
.output-param-container {
  overflow-x: auto;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.output-param-header {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 12px;
  color: #64748b;
  padding: 6px 8px;
  min-width: max-content;
}

.output-param-header .header-col {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.output-param-header .header-type {
  width: 60px;
  min-width: 60px;
}

.output-param-header .header-name {
  width: 200px;
  min-width: 200px;
}

.output-param-header .header-source {
  width: 180px;
  min-width: 180px;
}

.output-param-header .header-data-type {
  width: 90px;
  min-width: 90px;
}

.output-param-header .header-desc {
  width: 150px;
  min-width: 150px;
}

.output-param-header .header-action {
  width: 40px;
  min-width: 40px;
}

.output-param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  min-width: max-content;
  border-bottom: 1px solid #f1f5f9;
}

.output-param-item:last-child {
  border-bottom: none;
}

.param-name-group {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 200px;
  min-width: 200px;
  box-sizing: border-box;
}

.param-name-type-select {
  padding: 8px 4px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  background: white;
  flex-shrink: 0;
  width: 60px;
  min-width: 60px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 4px center;
  background-repeat: no-repeat;
  background-size: 10px;
  padding-right: 18px;
  box-sizing: border-box;
}

.param-name-type-select:focus {
  outline: none;
  border-color: #8b5cf6;
}

.param-name-cascader {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.param-source-cell {
  width: 180px;
  min-width: 180px;
}

.param-source-placeholder {
  width: 100%;
  padding: 8px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  font-size: 13px;
  background: transparent;
  color: #94a3b8;
  text-align: center;
  box-sizing: border-box;
}

.param-type-select {
  width: 90px;
  min-width: 90px;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.param-type-select:focus {
  outline: none;
  border-color: #8b5cf6;
}

.param-desc-input {
  width: 150px;
  min-width: 150px;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-desc-placeholder {
  width: 150px;
  min-width: 150px;
}

.param-action-cell {
  width: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.param-desc-input:focus {
  outline: none;
  border-color: #8b5cf6;
}

.param-source-cell {
  flex: 1;
}

.param-source-readonly {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-size: 13px;
  background: #f9fafb;
  color: #6b7280;
  font-family: monospace;
}

.param-source-placeholder {
  width: 100%;
  padding: 8px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  font-size: 13px;
  background: transparent;
  color: #94a3b8;
  text-align: center;
}

.param-name-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.param-name-input:focus {
  outline: none;
  border-color: #8b5cf6;
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

.no-templates {
  padding: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  background: #f8fafc;
  border-radius: 6px;
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

.lang-badge {
  padding: 3px 8px;
  background: #374151;
  color: white;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
}
</style>