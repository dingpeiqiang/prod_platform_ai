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

      <!-- 大模型配置（用于表单智能推荐和智能校验） -->
      <div class="section-title">大模型配置</div>
      <div class="llm-config-section">
        <div class="llm-config-row">
          <label class="config-label">模型选择</label>
          <select v-model="localModel" @change="emitUpdate" class="node-select" :disabled="modelsLoading">
            <option value="" disabled>请选择模型</option>
            <option
              v-for="opt in modelOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</option>
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

      <!-- 输入参数配置 -->
      <div v-if="(configMode || showAdvanced)" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输入参数，支持输入固定值或引用变量">
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
          <div class="params-container">
            <!-- 参数表头 -->
            <div class="param-header-row">
              <span class="param-col param-code">参数编码</span>
              <span class="param-col param-name">参数名称</span>
              <span class="param-col param-type">参数类型</span>
              <span class="param-col param-required">是否必填</span>
              <span class="param-col param-source">取值来源</span>
              <span class="param-col param-content">取值内容</span>
              <span class="param-col param-action">操作</span>
            </div>
            <!-- 参数行 -->
            <template v-for="(param, index) in localInputs" :key="index">
              <div v-if="param" class="param-row">
                <input v-model="param.name" @input="emitUpdate" placeholder="参数编码" class="param-col param-code-input" />
                <input v-model="param.description" @input="emitUpdate" placeholder="参数名称" class="param-col param-name-input" />
                <select v-model="param.type" @change="emitUpdate" class="param-col param-type-select">
                  <option value="string">字符串</option>
                  <option value="number">数字</option>
                  <option value="boolean">布尔值</option>
                  <option value="array">数组</option>
                  <option value="object">对象</option>
                </select>
                <span class="param-col param-required">{{ param.required ? '是' : '否' }}</span>
                <select v-model="param.sourceType" @change="handleSourceTypeChange(index)" class="param-col param-source-select">
                  <option value="input">自定义</option>
                  <option value="ref">引用</option>
                </select>
                <div class="param-col param-content">
                  <input
                    v-if="param.sourceType !== 'ref'"
                    v-model="param.value"
                    @input="emitUpdate"
                    :placeholder="getParamPlaceholder(param.type)"
                    class="param-value-input"
                  />
                  <VariableCascader
                    v-else
                    :key="'input-ref-' + index + '-' + cascaderRefreshKey"
                    v-model="param.refValue"
                    :available-variables="availableVariables"
                    placeholder="请选择变量"
                    class="param-value-cascader"
                    @change="emitUpdate"
                  />
                </div>
                <div class="param-col param-action">
                  <button @click="removeInputParam(index)" class="action-btn delete-btn" title="删除">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </template>
            <div v-if="localInputs.length === 0" class="no-params-hint">暂无输入参数，点击上方"+"添加</div>
          </div>
        </div>
      </div>

      <!-- 输出参数配置 -->
      <div v-if="(configMode || showAdvanced)" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输出参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
            <button @click.stop="addOutputParam" class="add-param-btn" title="添加输出参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.outputs" class="section-content">
          <div class="output-param-container">
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
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                  </button>
                </div>
              </div>
            </template>
          </div>
          <div v-if="localOutputs.length === 0" class="no-params-hint">暂无输出参数，点击上方"+"添加</div>
        </div>
      </div>

      <!-- 高级配置：工具参数 -->
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
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import { Handle } from '@vue-flow/core'
import { nodeDisplayProps } from './nodeDisplayProps.js'
import { useNodeAnchorMode } from './useHandlePosition.js'
import VariableCascader from '../VariableCascader.vue'
import { useModelsStore } from '@/stores/models.js'
import { useWorkflowDataStore } from '@/stores/workflowData.js'

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

const modelsStore = useModelsStore()
const workflowDataStore = useWorkflowDataStore()

const isUnmounted = ref(false)
const showAdvanced = ref(false)

// 展开状态
const expandedSections = ref({
  inputs: true,
  outputs: true
})

const localOntologyCode = ref(props.data.ontologyCode || '')
const localToolName = ref(props.data.toolType || props.data.toolName || '')
const localParams = ref([])
const localTimeout = ref(props.data.timeout || 60)

const localModel = ref(props.data.model || '')
const localTemperature = ref(props.data.temperature || 0.3)
const localInputVariable = ref(props.data.inputVariable || '')

// 输入输出参数
const localInputs = ref((props.data.inputParams && Array.isArray(props.data.inputParams)) ? props.data.inputParams.map(p => ({
  name: p.name || '',
  description: p.description || '',
  type: p.type || 'string',
  required: !!p.required,
  sourceType: p.sourceType || ((p.value && p.value.startsWith('{{')) ? 'ref' : 'input'),
  value: (p.sourceType !== 'ref' || !p.value?.startsWith('{{')) ? (p.value || '') : '',
  refValue: (p.sourceType === 'ref' || p.value?.startsWith('{{')) ? (p.value || '') : ''
})) : [])

const localOutputs = ref((props.data.outputParams || []).map(p => ({
  name: p.name || '',
  nameType: p.nameType || 'input',
  nameRef: p.nameRef || '',
  source: p.source || '',
  type: p.type || 'string',
  description: p.description || p.desc || ''
})))

// 强制 VariableCascader 刷新 key
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

const modelOptions = computed(() => modelsStore.modelOptions)
const modelsLoading = computed(() => modelsStore.loading)

const ontologies = computed(() => workflowDataStore.ontologies)
const mcpTools = computed(() => workflowDataStore.mcpTools)
const mcpToolMap = computed(() => workflowDataStore.mcpToolMap)
const loadingOntologies = computed(() => workflowDataStore.loadingOntologies)
const loadingMcpTools = computed(() => workflowDataStore.loadingMcpTools)
const groupedTools = computed(() => workflowDataStore.groupedTools)
const categories = computed(() => workflowDataStore.categories)

const getCategoryDisplayName = (cat) => workflowDataStore.getCategoryDisplayName(cat)

const selectedTool = computed(() => {
  return mcpToolMap.value[localToolName.value] || null
})

const nodeSummary = computed(() => {
  const parts = []
  if (localOntologyCode.value) {
    const ontology = workflowDataStore.getOntologyByCode(localOntologyCode.value)
    parts.push(ontology?.ontologyName || localOntologyCode.value)
  }
  if (localToolName.value) {
    const tool = workflowDataStore.getToolByName(localToolName.value)
    parts.push(tool?.name || localToolName.value)
  }
  return parts.length > 0 ? parts.join(' → ') : '未配置'
})

const loadOntologies = async () => {
  await workflowDataStore.loadOntologies()
}

const loadMCPTools = async () => {
  const tools = await workflowDataStore.loadMCPTools()
  if (tools && localToolName.value) {
    const tool = workflowDataStore.getToolByName(localToolName.value)
    if (tool) {
      syncParamsFromSchema(tool)
    }
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

// 切换展开状态
const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section]
}

// 添加输入参数
const addInputParam = () => {
  localInputs.value.push({ name: '', description: '', type: 'string', required: false, sourceType: 'input', value: '', refValue: '' })
  emitUpdate()
}

// 处理取值来源变化
const handleSourceTypeChange = (index) => {
  const param = localInputs.value[index]
  const newSourceType = param.sourceType;
  
  if (newSourceType === 'ref') {
    param.value = '';
  } else {
    param.refValue = '';
  }
  
  // 强制触发响应式更新
  localInputs.value = [...localInputs.value];
  emitUpdate();
}

// 删除输入参数
const removeInputParam = (index) => {
  localInputs.value.splice(index, 1)
  emitUpdate()
}

// 添加输出参数
const addOutputParam = () => {
  localOutputs.value.push({ name: '', nameType: 'input', nameRef: '', source: '{{__output__}}', type: 'string', description: '' })
  emitUpdate()
}

// 处理输出参数名称类型变化
const handleOutputNameTypeChange = (index) => {
  const param = localOutputs.value[index]
  if (param.nameType === 'reference') {
    param.name = ''
    param.source = ''
    param.description = ''
  } else {
    param.nameRef = ''
  }
  emitUpdate()
}

// 删除输出参数
const removeOutputParam = (index) => {
  localOutputs.value.splice(index, 1)
  emitUpdate()
}

const emitUpdate = () => {
  // 将前端输入参数格式映射为规范的 inputParams 格式
  const inputParams = localInputs.value
    .filter(p => p && p.name)
    .map(p => ({
      name: p.name,
      description: p.description || '',
      type: p.type || 'string',
      required: !!p.required,
      sourceType: p.sourceType || 'input',
      value: p.sourceType === 'ref' ? p.refValue : (p.value || '')
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

  // 工具参数
  const toolInputParams = localParams.value
    .filter(p => p.name)
    .map(p => ({
      name: p.name,
      value: p.value || ''
    }));

  emit('update', props.data.id, {
    ontologyCode: localOntologyCode.value,
    toolType: localToolName.value,
    toolName: localToolName.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined,
    toolInputParams: toolInputParams.length > 0 ? toolInputParams : undefined,
    timeout: localTimeout.value,
    model: localModel.value,
    temperature: localTemperature.value,
    inputVariable: localInputVariable.value
  });
}

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value
}

watch(() => props.data, (d) => {
  localOntologyCode.value = d.ontologyCode || ''
  localToolName.value = d.toolType || d.toolName || ''
  localTimeout.value = d.timeout || 60

  // 大模型配置
  localModel.value = d.model || ''
  localTemperature.value = d.temperature || 0.3
  localInputVariable.value = d.inputVariable || ''

  // 输入参数
  localInputs.value = (d.inputParams && Array.isArray(d.inputParams)) ? d.inputParams.map(p => ({
    name: p.name || '',
    description: p.description || '',
    type: p.type || 'string',
    required: !!p.required,
    sourceType: p.sourceType || ((p.value && p.value.startsWith('{{')) ? 'ref' : 'input'),
    value: (p.sourceType !== 'ref' || !p.value?.startsWith('{{')) ? (p.value || '') : '',
    refValue: (p.sourceType === 'ref' || p.value?.startsWith('{{')) ? (p.value || '') : ''
  })) : []

  // 输出参数
  localOutputs.value = (d.outputParams || []).map(p => ({
    name: p.name || '',
    nameType: p.nameType || 'input',
    nameRef: p.nameRef || '',
    source: p.source || '',
    type: p.type || 'string',
    description: p.description || p.desc || ''
  }))

  if (localToolName.value && mcpToolMap.value[localToolName.value]) {
    const tool = mcpToolMap.value[localToolName.value]
    const schema = tool.input_schema || {}
    const properties = schema.properties || {}

    if ((d.inputParams && Array.isArray(d.inputParams) && d.inputParams.length > 0) || 
        (d.toolInputParams && Array.isArray(d.toolInputParams) && d.toolInputParams.length > 0)) {
      const savedParams = d.toolInputParams || d.inputParams || []
      localParams.value = Object.entries(properties).map(([name, prop]) => {
        const inferType = (p) => {
          const t = p.type || 'string'
          if (t === 'number' || t === 'integer') return 'number'
          if (t === 'boolean') return 'boolean'
          if (t === 'array') return 'array'
          if (t === 'object') return 'object'
          return 'string'
        }
        const inputParam = savedParams.find(p => p.name === name);
        return {
          name,
          type: inferType(prop),
          value: inputParam ? inputParam.value : (prop.default || '')
        }
      })
    }
  }
}, { deep: true })

onMounted(() => {
  isUnmounted.value = false
  loadOntologies()
  loadMCPTools()
  // 加载模型列表（用于表单智能推荐和智能校验）
  modelsStore.loadModels()
})

onUnmounted(() => {
  isUnmounted.value = true
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

/* 配置区域样式 */
.config-section {
  margin-bottom: 8px;
}

.collapsible-section {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #f8fafc;
  cursor: pointer;
  transition: background 0.2s;
}

.section-header:hover {
  background: #f1f5f9;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
}

.section-toggle-btn svg {
  width: 14px;
  height: 14px;
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.help-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: help;
  border-radius: 50%;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #e2e8f0;
  color: #64748b;
}

.add-param-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: #10b981;
  color: white;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #059669;
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.3);
}

.section-content {
  padding: 10px;
  animation: slideDown 0.2s ease;
}

/* 输入参数表格样式 */
.params-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.param-header-row {
  display: flex;
  background: #f1f5f9;
  font-weight: 600;
  font-size: 10px;
  color: #64748b;
}

.param-row {
  display: flex;
  align-items: center;
  border-top: 1px solid #f1f5f9;
}

.param-row:nth-child(odd):not(.param-header-row) {
  background: #fafafa;
}

.param-col {
  padding: 4px 6px;
  display: flex;
  align-items: center;
  min-height: 28px;
  flex-shrink: 0;
}

.param-code {
  width: 75px;
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-code-input {
  width: 75px;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 11px;
  background: white;
}

.param-name {
  width: 90px;
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-name-input {
  width: 90px;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 11px;
  background: white;
}

.param-type {
  width: 55px;
  font-size: 11px;
  color: #475569;
  text-align: center;
}

.param-type-select {
  width: 55px;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 10px;
  background: white;
  color: #475569;
}

.param-required {
  width: 50px;
  font-size: 11px;
  color: #475569;
  text-align: center;
}

.param-source {
  width: 75px;
  font-size: 11px;
  color: #475569;
}

.param-source-select {
  width: 100%;
  min-width: 60px;
  max-width: 80px;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 10px;
  background: white;
  color: #475569;
  box-sizing: border-box;
}

.param-content {
  flex: 1;
  min-width: 100px;
}

.param-value-input {
  width: 100%;
  padding: 3px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 3px;
  font-size: 11px;
  background: white;
}

.param-value-cascader {
  width: 100%;
}

.param-action {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 输出参数样式 */
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
  border-color: #10b981;
}

.param-name-cascader {
  flex: 1;
  min-width: 0;
  font-size: 13px;
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

.param-desc-input:focus {
  outline: none;
  border-color: #10b981;
}

.param-action-cell {
  width: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.action-btn:hover {
  background: #f1f5f9;
}

.delete-btn {
  color: #94a3b8;
}

.delete-btn:hover {
  color: #ef4444;
  background: #fef2f2;
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
