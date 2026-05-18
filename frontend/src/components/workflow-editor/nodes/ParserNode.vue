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

      <div v-if="configMode || showAdvanced" class="advanced-panel">
        <div class="section-title">输出配置</div>
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
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  ...nodeDisplayProps
});

const emit = defineEmits(['update']);

const showAdvanced = ref(false);

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

const onParserChange = () => {
  emitUpdate();
};

const updateFlags = () => {
  emitUpdate();
};

const emitUpdate = () => {
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
    defaultValue: localDefaultValue.value
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
}, { deep: true });
</script>

<style scoped>
.parser-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 240px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.parser-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.parser-node.is-compact {
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