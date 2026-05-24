<template>
  <div class="node form-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📋</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>

    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ nodeSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>

    <div v-if="!compact || configMode" class="node-body">
      <!-- 本体选择 -->
      <div class="section-title">选择本体</div>
      <div v-if="loadingOntologies" class="loading-state">
        <span class="loading-text">加载本体中...</span>
      </div>
      <select v-model="localOntologyCode" @change="onOntologyChange" class="node-select">
        <option value="">选择本体</option>
        <option v-for="ontology in ontologies" :key="ontology.ontologyCode" :value="ontology.ontologyCode">
          {{ ontology.ontologyName }}
        </option>
      </select>

      <!-- MCP 工具选择 -->
      <div class="section-title">选择提交工具</div>
      <div v-if="loadingMcpTools" class="loading-state">
        <span class="loading-text">加载工具中...</span>
      </div>
      <select v-model="localToolName" @change="onToolChange" class="node-select">
        <option value="">选择 MCP 工具</option>
        <optgroup v-for="(tools, cat) in groupedTools" :key="cat" :label="getCategoryDisplayName(cat)">
          <option v-for="tool in tools" :key="tool.name" :value="tool.name">
            {{ tool.name }}
          </option>
        </optgroup>
      </select>

      <!-- 工具描述 -->
      <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>

      <!-- 大模型配置 -->
      <div class="section-title">大模型校验配置</div>
      <div class="llm-config-section">
        <div class="llm-config-row">
          <label class="config-label">启用校验</label>
          <input 
            v-model="localEnableValidation" 
            @change="emitUpdate" 
            type="checkbox" 
            class="config-checkbox"
          />
        </div>
        
        <div v-if="localEnableValidation" class="llm-config-panel">
          <div class="llm-config-row">
            <label class="config-label">模型选择</label>
            <select v-model="localModel" @change="emitUpdate" class="node-select">
              <option value="qwen-vl-plus">Qwen-VL-Plus</option>
              <option value="qwen-plus">Qwen-Plus</option>
              <option value="gpt-4o">GPT-4o</option>
              <option value="gpt-4">GPT-4</option>
              <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
              <option value="claude-3-opus">Claude 3 Opus</option>
              <option value="claude-3-sonnet">Claude 3 Sonnet</option>
            </select>
          </div>
          
          <div class="llm-config-row">
            <label class="config-label">温度值</label>
            <input 
              v-model.number="localTemperature" 
              @input="emitUpdate" 
              type="number" 
              min="0" 
              max="1" 
              step="0.1" 
              class="config-input-small"
            />
          </div>
          
          <div class="llm-config-row">
            <label class="config-label">校验提示词</label>
            <textarea 
              v-model="localValidationPrompt" 
              @input="emitUpdate" 
              placeholder="输入大模型校验提示词，支持 {{ontology_code}}、{{form_data}} 等变量"
              class="config-textarea"
              rows="3"
            ></textarea>
          </div>
          
          <div class="llm-config-row">
            <label class="config-label">输入变量</label>
            <VariableCascader
              v-model="localInputVariable"
              :available-variables="availableVariables"
              placeholder="选择输入变量"
              class="param-value-cascader"
              @change="emitUpdate"
            />
          </div>
        </div>
      </div>

      <!-- 高级配置：参数编辑 -->
      <div v-if="(configMode || showAdvanced) && localToolName" class="advanced-panel">
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
              readonly
            />
            <select v-model="param.type" @change="emitUpdate" class="param-type" disabled>
              <option value="string">字符串</option>
              <option value="number">数字</option>
              <option value="boolean">布尔值</option>
              <option value="array">数组</option>
              <option value="object">对象</option>
              <option value="variable">变量引用</option>
            </select>
            <input
              v-if="param.type !== 'variable'"
              v-model="param.value"
              @input="emitUpdate"
              :placeholder="getParamPlaceholder(param.type)"
              class="param-value"
            />
            <VariableCascader
              v-else
              v-model="param.value"
              :available-variables="availableVariables"
              placeholder="请选择变量"
              class="param-value-cascader"
              @change="emitUpdate"
            />
          </div>
          <div v-if="localParams.length === 0" class="no-params-hint">
            此工具无需参数
          </div>
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
      </div>
    </div>

    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { Handle } from '@vue-flow/core'
import { nodeDisplayProps } from './nodeDisplayProps.js'
import { useNodeAnchorMode } from './useHandlePosition.js'
import VariableCascader from '../VariableCascader.vue'
import * as ontologyApi from '@/services/ontologyApi'
import * as mcpApi from '@/services/mcpManagementApi'

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
})

const { targetPosition, sourcePosition } = useNodeAnchorMode(props)

const emit = defineEmits(['update'])

// 状态管理
const ontologies = ref([])
const mcpTools = ref([])
const mcpToolMap = ref({})
const loadingOntologies = ref(false)
const loadingMcpTools = ref(false)
const showAdvanced = ref(false)

// 本地数据
const localOntologyCode = ref(props.data.ontologyCode || '')
const localToolName = ref(props.data.toolType || props.data.toolName || '')
const localParams = ref([])
const localTimeout = ref(props.data.timeout || 60)

// 大模型校验配置
const localEnableValidation = ref(props.data.enableValidation || false)
const localModel = ref(props.data.model || 'qwen-plus')
const localTemperature = ref(props.data.temperature || 0.3)
const localValidationPrompt = ref(props.data.validationPrompt || '')
const localInputVariable = ref(props.data.inputVariable || '')

// 分类
const categories = computed(() => {
  const cats = [...new Set(mcpTools.value.map(t => t.metadata?.category || 'general'))]
  return cats.sort()
})

// 按分类分组工具
const groupedTools = computed(() => {
  const groups = {}
  for (const tool of mcpTools.value) {
    const cat = tool.metadata?.category || 'general'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(tool)
  }
  return groups
})

// 当前选中的工具
const selectedTool = computed(() => {
  return mcpToolMap.value[localToolName.value] || null
})

// 节点摘要
const nodeSummary = computed(() => {
  const parts = []
  if (localOntologyCode.value) {
    const ontology = ontologies.value.find(o => o.ontologyCode === localOntologyCode.value)
    parts.push(ontology?.ontologyName || localOntologyCode.value)
  }
  if (localToolName.value) {
    const tool = mcpToolMap.value[localToolName.value]
    parts.push(tool?.name || localToolName.value)
  }
  return parts.length > 0 ? parts.join(' → ') : '未配置'
})

// 分类显示名
const categoryNames = {
  form: '表单工具',
  kb: '知识库工具',
  llm: 'LLM 工具',
  system: '系统工具',
  tariff: '资费工具',
  workflow: '工作流工具',
  general: '通用工具',
  external: '外部工具'
}

const getCategoryDisplayName = (cat) => categoryNames[cat] || cat

// 加载本体列表
const loadOntologies = async () => {
        loadingOntologies.value = true
        try {
            const res = await ontologyApi.listOntologies(true)
            if (res && res.success) {
                ontologies.value = res.data || res.ontologies || []
            } else if (Array.isArray(res)) {
                ontologies.value = res
            }
        } catch (e) {
            console.error('加载本体列表失败:', e)
        } finally {
            loadingOntologies.value = false
        }
    }

// 加载 MCP 工具列表
const loadMCPTools = async () => {
  loadingMcpTools.value = true
  try {
    const res = await mcpApi.listTools()
    if (res.success) {
      mcpTools.value = res.tools || []
      const map = {}
      for (const t of mcpTools.value) {
        map[t.name] = t
      }
      mcpToolMap.value = map

      if (localToolName.value && map[localToolName.value]) {
        syncParamsFromSchema(map[localToolName.value])
      }
    }
  } catch (e) {
    console.error('加载 MCP 工具失败:', e)
  } finally {
    loadingMcpTools.value = false
  }
}

const onOntologyChange = () => {
  emitUpdate()
}

const onToolChange = () => {
  const tool = mcpToolMap.value[localToolName.value]
  if (tool) {
    syncParamsFromSchema(tool)
  } else {
    localParams.value = []
  }
  emitUpdate()
}

const syncParamsFromSchema = (tool) => {
  const schema = tool.input_schema || {}
  const properties = schema.properties || {}

  const existing = {}
  for (const p of localParams.value) {
    existing[p.name] = p
  }

  localParams.value = Object.entries(properties).map(([name, prop]) => {
    const inferType = (p) => {
      const t = p.type || 'string'
      if (t === 'number' || t === 'integer') return 'number'
      if (t === 'boolean') return 'boolean'
      if (t === 'array') return 'array'
      if (t === 'object') return 'object'
      return 'string'
    }

    return {
      name,
      type: inferType(prop),
      value: existing[name]?.value || prop.default || ''
    }
  })
}

const getParamPlaceholder = (type) => {
  switch (type) {
    case 'string': return '字符串值或 {{变量名}}'
    case 'number': return '数字'
    case 'boolean': return 'true/false'
    case 'array': return '[...]'
    case 'object': return '{...}'
    default: return '参数值'
  }
}

const emitUpdate = () => {
  const params = {}
  for (const p of localParams.value) {
    if (p.name) {
      params[p.name] = p.value
    }
  }

  emit('update', props.data.id, {
    ontologyCode: localOntologyCode.value,
    toolType: localToolName.value,
    toolName: localToolName.value,
    params,
    timeout: localTimeout.value,
    enableValidation: localEnableValidation.value,
    model: localModel.value,
    temperature: localTemperature.value,
    validationPrompt: localValidationPrompt.value,
    inputVariable: localInputVariable.value
  })
}

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value
}

watch(() => props.data, (d) => {
  localOntologyCode.value = d.ontologyCode || ''
  localToolName.value = d.toolType || d.toolName || ''
  localTimeout.value = d.timeout || 60
  
  // 大模型配置
  localEnableValidation.value = d.enableValidation || false
  localModel.value = d.model || 'qwen-plus'
  localTemperature.value = d.temperature || 0.3
  localValidationPrompt.value = d.validationPrompt || ''
  localInputVariable.value = d.inputVariable || ''

  if (localToolName.value && mcpToolMap.value[localToolName.value]) {
    const tool = mcpToolMap.value[localToolName.value]
    const schema = tool.input_schema || {}
    const properties = schema.properties || {}

    if (d.params && Object.keys(d.params).length > 0) {
      localParams.value = Object.entries(properties).map(([name, prop]) => {
        const inferType = (p) => {
          const t = p.type || 'string'
          if (t === 'number' || t === 'integer') return 'number'
          if (t === 'boolean') return 'boolean'
          if (t === 'array') return 'array'
          if (t === 'object') return 'object'
          return 'string'
        }
        return {
          name,
          type: inferType(prop),
          value: d.params[name] !== undefined ? d.params[name] : prop.default || ''
        }
      })
    }
  }
}, { deep: true })

onMounted(() => {
  loadOntologies()
  loadMCPTools()
})
</script>

<style scoped>
.form-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 200px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.form-node.selected {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}

.form-node.is-compact {
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

.form-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
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

.section-title {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 2px;
}

.llm-config-section {
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
  margin-bottom: 8px;
}

.llm-config-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.llm-config-row:last-child {
  margin-bottom: 0;
}

.config-label {
  font-size: 11px;
  color: #64748b;
  min-width: 60px;
  padding-top: 4px;
}

.config-checkbox {
  width: 16px;
  height: 16px;
  margin-top: 4px;
}

.config-input-small {
  width: 80px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.config-textarea {
  flex: 1;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
}

.config-textarea:focus {
  outline: none;
  border-color: #10b981;
}

.llm-config-panel {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #cbd5e1;
}

.loading-state {
  padding: 8px;
  text-align: center;
}

.loading-text {
  font-size: 11px;
  color: #94a3b8;
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
  border-color: #10b981;
}

.tool-desc {
  font-size: 11px;
  color: #64748b;
  padding: 6px 8px;
  background: #f8fafc;
  border-radius: 4px;
  line-height: 1.4;
  max-height: 60px;
  overflow-y: auto;
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

.params-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.param-row {
  display: flex;
  gap: 4px;
  align-items: center;
}

.param-name {
  width: 60px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  background: #f8fafc;
  color: #64748b;
}

.param-type {
  width: 70px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 10px;
  background: #f8fafc;
  color: #64748b;
}

.param-value {
  flex: 1;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.no-params-hint {
  font-size: 11px;
  color: #94a3b8;
  padding: 4px 0;
  text-align: center;
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

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #34d399 !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #10b981 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #059669 !important;
}
</style>
