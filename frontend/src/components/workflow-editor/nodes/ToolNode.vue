<template>
  <div class="node tool-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <!-- 非配置模式 -->
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

    <!-- 非配置模式的详细视图 -->
    <div v-if="!compact && !configMode" class="node-body">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <span class="loading-text">加载工具中...</span>
      </div>

      <!-- 工具选择器 -->
      <div v-else class="tool-selector">
        <select v-model="localCategory" @change="onCategoryChange" class="node-select category-select">
          <option value="">全部分类</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ getCategoryDisplayName(cat) }}</option>
        </select>
        <select v-model="localToolName" @change="onToolChange" class="node-select">
          <option value="">选择 MCP 工具</option>
          <optgroup v-for="(tools, cat) in groupedTools" :key="cat" :label="getCategoryDisplayName(cat)">
            <option v-for="tool in tools" :key="tool.name" :value="tool.name">
              {{ tool.name }}
            </option>
          </optgroup>
        </select>
      </div>

      <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>

      <!-- 高级配置预览 -->
      <div v-if="localToolName && showAdvanced" class="advanced-panel">
        <div class="section-title">输入参数</div>
        <div v-if="localParams.length === 0" class="no-params-hint">此工具无需参数</div>
        <div v-else class="params-preview">
          <div v-for="param in localParams" :key="param.name" class="param-preview-item">
            <span class="param-name">{{ param.description || param.name }}</span>
            <span class="param-value">{{ param.value || param.refValue || '未设置' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 配置模式 -->
    <div v-if="configMode" class="tool-node-config">
      <!-- 工具选择器 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('tool')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.tool }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>工具选择</span>
          </button>
        </div>
        <div v-if="expandedSections.tool" class="section-content">
          <!-- 加载状态 -->
          <div v-if="loading" class="loading-state">
            <span class="loading-text">加载工具中...</span>
          </div>

          <div v-else>
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

            <!-- 工具描述 -->
            <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>
          </div>
        </div>
      </div>

      <!-- 输入参数配置 -->
      <div v-if="localToolName" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输入参数，根据工具的入参自动生成">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
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
            </div>
            <!-- 参数行 -->
            <div
              v-for="(param, index) in localParams"
              :key="index"
              class="param-row"
            >
              <span class="param-col param-code">{{ param.name }}</span>
              <span class="param-col param-name">{{ param.description || param.title || param.name }}</span>
              <span class="param-col param-type">{{ getTypeDisplayName(param.schemaType) }}</span>
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
                  :placeholder="getParamPlaceholder(param.schemaType)"
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
            </div>
            <div v-if="localParams.length === 0" class="no-params-hint">
              此工具无需参数
            </div>
          </div>
        </div>
      </div>

      <!-- 输出参数配置 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输出参数，选择工具出参节点">
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
            <!-- 输出参数表头 -->
            <div class="output-param-header">
              <span class="header-col header-source">工具出参</span>
              <span class="header-col header-type">类型</span>
              <span class="header-col header-name">参数名/变量</span>
              <span class="header-col header-data-type">数据类型</span>
              <span class="header-col header-desc">描述</span>
              <span class="header-col header-action">操作</span>
            </div>
            
            <template v-for="(param, index) in localOutputMappings" :key="index">
              <div v-if="param" class="output-param-item">
                <div class="output-schema-tree-wrapper">
                  <VariableCascader
                    :key="'output-source-' + index + '-' + cascaderRefreshKey"
                    v-model="param.source"
                    :available-variables="outputSchemaVariables"
                    placeholder="选择工具出参"
                    class="param-source-cascader"
                    @change="emitUpdate"
                  />
                </div>
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
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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

      <!-- 收起按钮 -->
      <div class="collapse-section">
        <button @click="$emit('close')" class="collapse-all-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="18 15 12 9 6 15"/>
          </svg>
          <span>收起</span>
        </button>
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

const emit = defineEmits(['update', 'close'])



// MCP 工具数据
const mcpTools = ref([])
const mcpToolMap = ref({})
const loading = ref(false)
const showAdvanced = ref(false)

// 展开状态
const expandedSections = ref({
  inputs: true,
  outputs: true
})

// 选择状态（对应后端 toolType 字段）
const localCategory = ref('')
const localToolName = ref(props.data.tool_type || props.data.tool_name || '')
const localParams = ref([])
const localOutputMappings = ref([])

// 强制 VariableCascader 刷新 key，当 availableVariables 变化时重新挂载
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

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

// 输出schema属性
const outputSchemaProperties = computed(() => {
  if (!selectedTool.value) return {}
  const schema = selectedTool.value.output_schema || selectedTool.value.outputSchema || selectedTool.value.schema || {}
  const properties = schema.properties || {}
  return properties
})

// 工具出参级联选项（用于输出参数"工具出参"列的 VariableCascader）
const outputSchemaVariables = computed(() => {
  if (!selectedTool.value) return []
  const schema = selectedTool.value.output_schema || selectedTool.value.outputSchema || selectedTool.value.schema || {}
  const properties = schema.properties || {}
  if (Object.keys(properties).length === 0) return []

  const nodeId = props.data.id
  const nodeName = localToolName.value || '工具节点'

  return Object.entries(properties).map(([name, prop]) => ({
    id: name,
    name: `${name} (${prop.type || 'any'})`,
    nodeId: nodeId,
    nodeType: 'tool',
    nodeName: nodeName,
    sourceNodeType: 'tool',
    sourceNodeName: nodeName,
    varName: name
  }))
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
  const schema = tool.input_schema || tool.inputSchema || tool.schema || {}
  const properties = schema.properties || {}
  const requiredFields = schema.required || []

  // 保留已有值，只在必要时新增/删除行
  const existing = {}
  for (const p of localParams.value) {
    existing[p.name] = p
  }

  localParams.value = Object.entries(properties).map(([name, prop]) => {
    const schemaType = prop.type || 'string'
    const inferType = (t) => {
      if (t === 'number' || t === 'integer') return 'number'
      if (t === 'boolean') return 'boolean'
      if (t === 'array') return 'array'
      if (t === 'object') return 'object'
      return 'string'
    }

    const existingParam = existing[name]
    return {
      name,
      title: prop.title || '',
      description: prop.description || '',
      schemaType,
      type: existingParam?.type || inferType(schemaType),
      required: requiredFields.includes(name),
      sourceType: existingParam?.sourceType || 'input',
      value: existingParam?.value || prop.default || '',
      refValue: existingParam?.refValue || ''
    }
  })
}

// 切换展开状态
const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section]
}

// 获取类型显示名称
const getTypeDisplayName = (type) => {
  const typeNames = {
    string: '字符串',
    number: '数字',
    integer: '整数',
    boolean: '布尔值',
    array: '数组',
    object: '对象',
    null: '空值',
    any: '任意'
  }
  return typeNames[type] || type
}

// 处理取值来源变化
const handleSourceTypeChange = (index) => {
  const param = localParams.value[index]
  const newSourceType = param.sourceType;
  
  if (newSourceType === 'ref') {
    param.value = '';
  } else {
    param.refValue = '';
  }
  
  // 强制触发响应式更新
  localParams.value = [...localParams.value];
  emitUpdate();
}

// 添加输出参数
const addOutputParam = () => {
  localOutputMappings.value.push({ name: '', nameType: 'input', nameRef: '', source: '', type: 'string', description: '' })
  emitUpdate()
}

const handleOutputNameTypeChange = (index) => {
  const param = localOutputMappings.value[index]
  if (param.nameType === 'reference') {
    param.name = ''
  } else {
    param.nameRef = ''
  }
  emitUpdate()
}

// 删除输出参数
const removeOutputParam = (index) => {
  localOutputMappings.value.splice(index, 1)
  emitUpdate()
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
  // 转换为规范的 inputParams 数组格式
  const inputParams = localParams.value
    .filter(p => p.name)
    .map(p => ({
      name: p.name,
      sourceType: p.sourceType || 'input',
      value: p.sourceType === 'ref' ? p.refValue : (p.value || '')
    }));

  // 转换为规范的 outputParams 数组格式
  const outputParams = localOutputMappings.value
    .filter(m => m)
    .map(m => ({
      name: m.name || '',
      nameType: m.nameType || 'input',
      nameRef: m.nameRef || '',
      source: m.source || '',
      type: m.type || 'string',
      description: m.description || ''
    }));

  emit('update', props.data.id, {
    tool_type: localToolName.value,
    tool_name: localToolName.value,
    inputParams: inputParams.length > 0 ? inputParams : undefined,
    outputParams: outputParams.length > 0 ? outputParams : undefined
  });
}

watch(() => props.data, (d) => {
  localToolName.value = d.tool_type || d.tool_name || ''
  localOutputMappings.value = (d.outputParams && Array.isArray(d.outputParams))
    ? d.outputParams.map(m => ({
      name: m.name || m.target || '',
      nameType: m.nameType || 'input',
      nameRef: m.nameRef || '',
      source: m.source || '',
      type: m.type || 'string',
      description: m.description || m.desc || ''
    }))
    : []

  // 同步参数
  if (localToolName.value && mcpToolMap.value[localToolName.value]) {
    const tool = mcpToolMap.value[localToolName.value]
    syncParamsFromSchema(tool)
    
    // 如果有已保存的参数值，覆盖默认值
    if (d.inputParams && Array.isArray(d.inputParams) && d.inputParams.length > 0) {
      for (const inputParam of d.inputParams) {
        const existingParam = localParams.value.find(p => p.name === inputParam.name);
        if (existingParam) {
          // 优先使用后端返回的 sourceType，否则根据值推断
          const savedSourceType = inputParam.sourceType || ((inputParam.value && inputParam.value.startsWith('{{')) ? 'ref' : 'input');
          existingParam.sourceType = savedSourceType;
          existingParam.value = inputParam.value || '';
          if (existingParam.sourceType === 'ref') {
            existingParam.refValue = inputParam.value || '';
          }
        }
      }
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

.tool-node-config {
  padding: 0;
  background: #fff;
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

.param-name {
  width: 90px;
  font-size: 11px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-type {
  width: 55px;
  font-size: 11px;
  color: #475569;
  text-align: center;
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

.no-params-hint {
  font-size: 11px;
  color: #94a3b8;
  padding: 4px 0;
  text-align: center;
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
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.section-toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.section-toggle-btn:hover {
  background: #f0f0f0;
}

.section-toggle-btn svg {
  width: 16px;
  height: 16px;
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
  background: #8b5cf6;
  color: white;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #7c3aed;
  box-shadow: 0 2px 4px rgba(139, 92, 246, 0.3);
}

.section-content {
  padding: 10px;
  animation: slideDown 0.2s ease;
}

.section-subtitle {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
  margin-bottom: 6px;
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
  width: 64px;
  min-width: 64px;
}

.output-param-header .header-name {
  width: 200px;
  min-width: 200px;
}

.output-param-header .header-source {
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
  width: 64px;
  min-width: 64px;
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

.param-source-cell {
  width: 200px;
  min-width: 200px;
}

.output-schema-tree-wrapper {
  width: 200px;
  min-width: 200px;
  flex-shrink: 0;
}

.param-source-cascader {
  width: 100%;
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

.param-value-cascader {
  flex: 1;
  font-size: 11px;
}

/* 收起按钮 */
.collapse-section {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
}

.collapse-all-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 48px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-all-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}
</style>