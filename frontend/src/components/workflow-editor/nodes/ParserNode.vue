<template>
  <div class="node parser-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📊</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ parserSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <select v-model="localParserType" @change="onParserChange" class="node-select">
        <option value="json">JSON 解析</option>
        <option value="regex">正则提取</option>
        <option value="jsonpath">JSON Path</option>
        <option value="csv">CSV 解析</option>
        <option value="xml">XML 解析</option>
        <option value="yaml">YAML 解析</option>
        <option value="html">HTML 解析</option>
      </select>

      <div v-if="localParserType === 'regex'" class="parser-content">
        <div class="param-row">
          <label>正则表达式</label>
          <div class="regex-input-row">
            <span class="regex-prefix">/</span>
            <input 
              v-model="localRegexPattern" 
              @input="emitUpdate" 
              placeholder="pattern" 
              class="regex-input" 
            />
            <span class="regex-suffix">/{{ localRegexFlags }}</span>
          </div>
        </div>
        <div class="param-row">
          <label>匹配标志</label>
          <div class="flags-row">
            <label class="flag-label">
              <input v-model="localRegexGlobal" @change="updateFlags" type="checkbox" />
              <span>g (全局)</span>
            </label>
            <label class="flag-label">
              <input v-model="localRegexIgnoreCase" @change="updateFlags" type="checkbox" />
              <span>i (忽略大小写)</span>
            </label>
            <label class="flag-label">
              <input v-model="localRegexMultiline" @change="updateFlags" type="checkbox" />
              <span>m (多行)</span>
            </label>
          </div>
        </div>
        <div class="param-row">
          <label>捕获组索引</label>
          <input 
            v-model.number="localCaptureGroup" 
            @input="emitUpdate" 
            type="number" 
            min="0" 
            class="node-input-small"
          />
        </div>
      </div>

      <div v-else-if="localParserType === 'jsonpath'" class="parser-content">
        <div class="param-row">
          <label>JSON Path</label>
          <input 
            v-model="localJsonPath" 
            @input="emitUpdate" 
            placeholder="$.data.items[*].name" 
            class="node-input" 
          />
        </div>
        <div class="jsonpath-examples">
          <span class="examples-title">示例：</span>
          <button 
            v-for="example in jsonPathExamples" 
            :key="example.path" 
            @click="localJsonPath = example.path"
            class="example-btn"
            :title="example.desc"
          >
            {{ example.label }}
          </button>
        </div>
      </div>

      <div v-else-if="localParserType === 'csv'" class="parser-content">
        <div class="param-row">
          <label>分隔符</label>
          <input 
            v-model="localDelimiter" 
            @input="emitUpdate" 
            placeholder="," 
            class="node-input-small" 
          />
        </div>
        <label class="checkbox-label">
          <input v-model="localHasHeader" @change="emitUpdate" type="checkbox" />
          <span>首行为表头</span>
        </label>
        <label class="checkbox-label">
          <input v-model="localTrimWhitespace" @change="emitUpdate" type="checkbox" />
          <span>去除空白</span>
        </label>
      </div>

      <div v-else-if="localParserType === 'xml'" class="parser-content">
        <div class="param-row">
          <label>XPath</label>
          <input 
            v-model="localXPath" 
            @input="emitUpdate" 
            placeholder="//root/item" 
            class="node-input" 
          />
        </div>
        <label class="checkbox-label">
          <input v-model="localXmlNamespace" @change="emitUpdate" type="checkbox" />
          <span>保留命名空间</span>
        </label>
      </div>

      <div v-else-if="localParserType === 'yaml'" class="parser-content">
        <label class="checkbox-label">
          <input v-model="localYamlSafe" @change="emitUpdate" type="checkbox" />
          <span>安全模式</span>
        </label>
      </div>

      <div v-else-if="localParserType === 'html'" class="parser-content">
        <div class="param-row">
          <label>CSS 选择器</label>
          <input 
            v-model="localCssSelector" 
            @input="emitUpdate" 
            placeholder=".class, #id, tag" 
            class="node-input" 
          />
        </div>
        <select v-model="localExtractType" @change="emitUpdate" class="node-select">
          <option value="text">提取文本</option>
          <option value="html">提取HTML</option>
          <option value="attr">提取属性</option>
          <option value="all">提取全部</option>
        </select>
        <div v-if="localExtractType === 'attr'" class="param-row">
          <label>属性名</label>
          <input 
            v-model="localAttrName" 
            @input="emitUpdate" 
            placeholder="href, src, class" 
            class="node-input" 
          />
        </div>
      </div>

      <!-- 输入参数配置区 - 标准模式 -->
      <div v-if="configMode || showAdvanced" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置解析所需的输入参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click.stop="addInputParam" class="add-param-btn" title="添加输入参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.inputs" class="section-content">
          <div class="input-param-container">
            <!-- 输入参数表头 -->
            <div class="input-param-header">
              <span class="header-col header-name">参数名</span>
              <span class="header-col header-type">类型</span>
              <span class="header-col header-value">值</span>
              <span class="header-col header-action">操作</span>
            </div>
            <template v-for="(param, index) in localInputs" :key="index">
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
          <div v-if="localInputs.some(p => p && !p.name)" class="error-message">参数名不能为空</div>
          <div v-if="localInputs.some(p => p && p.valueType === 'reference' && !p.refValue)" class="error-message">引用变量不能为空</div>
        </div>
      </div>
    </div>

      <!-- 输出参数配置区 -->
      <div v-if="configMode || showAdvanced" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出配置</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输出格式和错误处理">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.outputs" class="section-content">
          <select v-model="localOutputFormat" @change="emitUpdate" class="node-select">
            <option value="auto">自动</option>
            <option value="array">数组</option>
            <option value="object">对象</option>
            <option value="string">字符串</option>
            <option value="number">数字</option>
          </select>

          <label class="checkbox-label">
            <input v-model="localAllowEmpty" @change="emitUpdate" type="checkbox" />
            <span>允许空结果</span>
          </label>

          <label class="checkbox-label">
            <input v-model="localFlatten" @change="emitUpdate" type="checkbox" />
            <span>扁平化数组</span>
          </label>

          <div class="section-title">错误处理</div>
          <select v-model="localErrorAction" @change="emitUpdate" class="node-select">
            <option value="throw">抛出异常</option>
            <option value="return-null">返回 null</option>
            <option value="return-input">返回原始输入</option>
          </select>

          <div v-if="localErrorAction !== 'throw'" class="default-value-row">
            <label>默认值</label>
            <input 
              v-model="localDefaultValue" 
              @input="emitUpdate" 
              placeholder="解析失败时返回的值" 
              class="node-input" 
            />
          </div>
        </div>
      </div>
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
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
  ...nodeDisplayProps
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update']);

const showAdvanced = ref(false);
const expandedSections = ref({ inputs: true, outputs: true });

const parserTypeLabels = {
  json: 'JSON 解析',
  regex: '正则提取',
  jsonpath: 'JSON Path',
  csv: 'CSV 解析',
  xml: 'XML 解析',
  yaml: 'YAML 解析',
  html: 'HTML 解析'
};

const localParserType = ref(props.data.parserType || 'json');
const parserSummary = computed(() => parserTypeLabels[localParserType.value] || localParserType.value);
const localRegexPattern = ref(props.data.regexPattern || '');
const localRegexGlobal = ref(props.data.regexGlobal || false);
const localRegexIgnoreCase = ref(props.data.regexIgnoreCase || false);
const localRegexMultiline = ref(props.data.regexMultiline || false);
const localCaptureGroup = ref(props.data.captureGroup || 0);
const localJsonPath = ref(props.data.jsonPath || '');
const localDelimiter = ref(props.data.delimiter || ',');
const localHasHeader = ref(props.data.hasHeader !== false);
const localTrimWhitespace = ref(props.data.trimWhitespace !== false);
const localXPath = ref(props.data.xpath || '');
const localXmlNamespace = ref(props.data.xmlNamespace || false);
const localYamlSafe = ref(props.data.yamlSafe !== false);
const localCssSelector = ref(props.data.cssSelector || '');
const localExtractType = ref(props.data.extractType || 'text');
const localAttrName = ref(props.data.attrName || '');
const localOutputFormat = ref(props.data.outputFormat || 'auto');
const localAllowEmpty = ref(props.data.allowEmpty || false);
const localFlatten = ref(props.data.flatten || false);
const localErrorAction = ref(props.data.errorAction || 'throw');
const localDefaultValue = ref(props.data.defaultValue || '');

// 输入参数 - 标准格式
const localInputs = ref((props.data.inputParams || []).map(p => ({
  name: p.name || '',
  valueType: p.valueType || 'input',
  defaultValue: p.defaultValue || p.value || '',
  refValue: p.refValue || ''
})));

const jsonPathExamples = [
  { label: '根节点', path: '$', desc: '获取根对象' },
  { label: '属性', path: '$.name', desc: '获取name属性' },
  { label: '数组', path: '$.items[*]', desc: '获取数组所有元素' },
  { label: '索引', path: '$.items[0]', desc: '获取第一个元素' },
  { label: '过滤', path: '$.items[?(@.active)]', desc: '过滤活跃项' },
  { label: '嵌套', path: '$.data.items[*].id', desc: '嵌套属性' }
];

const localRegexFlags = computed(() => {
  let flags = '';
  if (localRegexGlobal.value) flags += 'g';
  if (localRegexIgnoreCase.value) flags += 'i';
  if (localRegexMultiline.value) flags += 'm';
  return flags;
});

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

const onParserChange = () => {
  emitUpdate();
};

const updateFlags = () => {
  emitUpdate();
};

const addInputParam = () => {
  localInputs.value.push({ name: '', valueType: 'input', defaultValue: '', refValue: '' });
};

const removeInputParam = (index) => {
  localInputs.value.splice(index, 1);
  emitUpdate();
};

const handleValueTypeChange = (index) => {
  const param = localInputs.value[index];
  if (param.valueType === 'reference') {
    param.defaultValue = '';
  } else {
    param.refValue = '';
  }
  emitUpdate();
};

const handleCascaderChange = (index, value) => {
  localInputs.value[index].refValue = value;
  emitUpdate();
};

const emitUpdate = () => {
  const inputParams = localInputs.value
    .filter(p => p && p.name)
    .map(p => ({
      name: p.name,
      valueType: p.valueType,
      defaultValue: p.valueType === 'input' ? p.defaultValue : undefined,
      refValue: p.valueType === 'reference' ? p.refValue : undefined
    }));

  emit('update', props.data.id, {
    parserType: localParserType.value,
    regexPattern: localRegexPattern.value,
    regexGlobal: localRegexGlobal.value,
    regexIgnoreCase: localRegexIgnoreCase.value,
    regexMultiline: localRegexMultiline.value,
    captureGroup: localCaptureGroup.value,
    jsonPath: localJsonPath.value,
    delimiter: localDelimiter.value,
    hasHeader: localHasHeader.value,
    trimWhitespace: localTrimWhitespace.value,
    xpath: localXPath.value,
    xmlNamespace: localXmlNamespace.value,
    yamlSafe: localYamlSafe.value,
    cssSelector: localCssSelector.value,
    extractType: localExtractType.value,
    attrName: localAttrName.value,
    outputFormat: localOutputFormat.value,
    allowEmpty: localAllowEmpty.value,
    flatten: localFlatten.value,
    errorAction: localErrorAction.value,
    defaultValue: localDefaultValue.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined
  });
};

watch(() => props.data, (d) => {
  localParserType.value = d.parserType || 'json';
  localRegexPattern.value = d.regexPattern || '';
  localRegexGlobal.value = d.regexGlobal || false;
  localRegexIgnoreCase.value = d.regexIgnoreCase || false;
  localRegexMultiline.value = d.regexMultiline || false;
  localCaptureGroup.value = d.captureGroup || 0;
  localJsonPath.value = d.jsonPath || '';
  localDelimiter.value = d.delimiter || ',';
  localHasHeader.value = d.hasHeader !== false;
  localTrimWhitespace.value = d.trimWhitespace !== false;
  localXPath.value = d.xpath || '';
  localXmlNamespace.value = d.xmlNamespace || false;
  localYamlSafe.value = d.yamlSafe !== false;
  localCssSelector.value = d.cssSelector || '';
  localExtractType.value = d.extractType || 'text';
  localAttrName.value = d.attrName || '';
  localOutputFormat.value = d.outputFormat || 'auto';
  localAllowEmpty.value = d.allowEmpty || false;
  localFlatten.value = d.flatten || false;
  localErrorAction.value = d.errorAction || 'throw';
  localDefaultValue.value = d.defaultValue || '';
  
  if (d.inputParams) {
    localInputs.value = d.inputParams.map(p => ({
      name: p.name || '',
      valueType: p.valueType || 'input',
      defaultValue: p.defaultValue || p.value || '',
      refValue: p.refValue || ''
    }));
  }
}, { deep: true });
</script>

<style scoped>
.parser-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.parser-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.parser-node.is-compact {
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

.parser-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%);
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

.parser-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.param-row label {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.regex-input-row {
  display: flex;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 0 8px;
}

.regex-prefix,
.regex-suffix {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}

.regex-input {
  flex: 1;
  padding: 5px;
  border: none;
  font-size: 12px;
  font-family: monospace;
  background: transparent;
}

.regex-input:focus {
  outline: none;
}

.flags-row {
  display: flex;
  gap: 12px;
}

.flag-label {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: #475569;
  cursor: pointer;
}

.flag-label input {
  width: 13px;
  height: 13px;
}

.node-input {
  width: 100%;
  padding: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.node-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.node-input-small {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.jsonpath-examples {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.examples-title {
  font-size: 10px;
  color: #94a3b8;
}

.example-btn {
  padding: 2px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  font-size: 10px;
}

.example-btn:hover {
  background: #dbeafe;
  border-color: #3b82f6;
  color: #3b82f6;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
}

.checkbox-label input {
  width: 14px;
  height: 14px;
}

/* 配置区域样式 */
.config-section {
  margin-top: 8px;
}

.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
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

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
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
}

.header-col.header-name {
  width: 120px;
}

.header-col.header-type {
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

/* 输入参数项样式 */
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

.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.delete-btn {
  color: #94a3b8;
}

.delete-btn:hover {
  background: #fee2e2;
  color: #ef4444;
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

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.default-value-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 4px;
}

.default-value-row label {
  font-size: 11px;
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