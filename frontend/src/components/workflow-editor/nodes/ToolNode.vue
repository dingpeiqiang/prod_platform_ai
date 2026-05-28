<template>
  <div class="node tool-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🔌</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>

    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ toolDisplayName }}</span>
      <span class="compact-hint">双击配置</span>
    </div>

    <div v-if="!compact || configMode" class="node-body">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-text">加载工具中...</span>
      </div>

      <!-- 工具选择器 -->
      <div v-else class="tool-selector">
        <!-- 分类选择 -->
        <select v-model="localCategory" @change="onCategoryChange" class="node-select category-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ getCategoryDisplayName(cat) }}</option>
        </select>

        <!-- 工具选择 -->
        <select v-model="localToolName" @change="onToolChange" class="node-select">
          <option value="">选择 MCP 工具</option>
          <optgroup v-for="(tools, cat) in groupedTools" :key="cat" :label="getCategoryDisplayName(cat)">
            <option v-for="tool in tools" :key="tool.name" :value="tool.name">
              {{ tool.name }}
            </option>
          </optgroup>
        </select>
      </div>

      <!-- 工具描述 -->
      <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>

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

        <label class="checkbox-label">
          <input v-model="localAsync" @change="emitUpdate" type="checkbox" />
          <span>异步执行</span>
        </label>

        <label class="checkbox-label">
          <input v-model="localSilent" @change="emitUpdate" type="checkbox" />
          <span>静默模式（不输出日志）</span>
        </label>
      </div>
    </div>

    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { nodeDisplayProps } from './nodeDisplayProps.js'
import { useNodeAnchorMode } from './useHandlePosition.js'
import VariableCascader from '../VariableCascader.vue'
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

// MCP 工具数据
const mcpTools = ref([])
const mcpToolMap = ref({})
const loading = ref(false)
const showAdvanced = ref(false)

// 选择状态（对应后端 toolType 字段）
const localCategory = ref('')
const localToolName = ref(props.data.tool_type || props.data.tool_name || '')
const localParams = ref([])
const localTimeout = ref(props.data.timeout || 60)
const localAsync = ref(props.data.isAsync || false)
const localSilent = ref(props.data.silent || false)

// 分类
const categories = computed(() => {
  const cats = [...new Set(mcpTools.value.map(t => t.metadata?.category || 'general'))]
  return cats.sort()
})

// 按分类分组
const groupedTools = computed(() => {
  const filtered = localCategory.value
    ? mcpTools.value.filter(t => (t.metadata?.category || 'general') === localCategory.value)
    : mcpTools.value

  const groups = {}
  for (const tool of filtered) {
    const cat = tool.metadata?.category || 'general'
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(tool)
  }
  return groups
})

// 当前选中的工具定义
const selectedTool = computed(() => {
  return mcpToolMap.value[localToolName.value] || null
})

// 显示名称
const toolDisplayName = computed(() => {
  if (!localToolName.value) return '未选择工具'
  const tool = mcpToolMap.value[localToolName.value]
  return tool?.name || localToolName.value
})

// 加载 MCP 工具列表
const loadMCPTools = async () => {
  loading.value = true
  try {
    const res = await mcpApi.listTools()
    if (res.success) {
      mcpTools.value = res.tools || []
      // 构建 name -> tool 映射
      const map = {}
      for (const t of mcpTools.value) {
        map[t.name] = t
      }
      mcpToolMap.value = map

      // 如果已有选中工具且在列表中找到，同步参数
      if (localToolName.value && map[localToolName.value]) {
        syncParamsFromSchema(map[localToolName.value])
      }
    }
  } catch (e) {
    console.error('加载 MCP 工具失败:', e)
  } finally {
    loading.value = false
  }
}

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

// 分类切换时清空工具选择
const onCategoryChange = () => {
  localToolName.value = ''
  localParams.value = []
  emitUpdate()
}

// 工具切换时从 schema 生成参数行
const onToolChange = () => {
  const tool = mcpToolMap.value[localToolName.value]
  if (tool) {
    syncParamsFromSchema(tool)
  } else {
    localParams.value = []
  }
  emitUpdate()
}

// 根据 input_schema 同步参数行
const syncParamsFromSchema = (tool) => {
  const schema = tool.input_schema || {}
  const properties = schema.properties || {}

  // 保留已有值，只在必要时新增/删除行
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
  // 转换为 params 对象（key-value 形式）供后端使用
  const params = {}
  for (const p of localParams.value) {
    if (p.name) {
      params[p.name] = p.value
    }
  }

  emit('update', props.data.id, {
    tool_type: localToolName.value,
    tool_name: localToolName.value,
    params,
    timeout: localTimeout.value,
    isAsync: localAsync.value,
    silent: localSilent.value
  })
}

watch(() => props.data, (d) => {
  localToolName.value = d.tool_type || d.tool_name || ''
  localTimeout.value = d.timeout || 60
  localAsync.value = d.isAsync || false
  localSilent.value = d.silent || false

  // 同步参数
  if (localToolName.value && mcpToolMap.value[localToolName.value]) {
    const tool = mcpToolMap.value[localToolName.value]
    const schema = tool.input_schema || {}
    const properties = schema.properties || {}

    if (d.params && Object.keys(d.params).length > 0) {
      // 复用已有值
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

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value
}

onMounted(() => {
  loadMCPTools()
})
</script>

<style scoped>
.tool-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 200px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.tool-node.selected {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.15);
}

.tool-node.is-compact {
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

.tool-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
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

.loading-state {
  padding: 12px;
  text-align: center;
}

.loading-text {
  font-size: 12px;
  color: #94a3b8;
}

.tool-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-select {
  font-size: 11px;
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
  border-color: #8b5cf6;
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

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(139, 92, 246, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #a78bfa !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #7c3aed !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #8b5cf6 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #7c3aed !important;
}
</style>