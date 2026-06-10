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

    <div v-if="!compact || configMode" class="form-node-config">
      <!-- 本体和工具选择 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('basic')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.basic }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>基础配置</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置本体和提交工具">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="expandedSections.basic" class="section-content">
          <div class="param-grid">
            <!-- 本体选择 -->
            <div class="param-row">
              <label class="param-label">选择本体</label>
              <div v-if="loadingOntologies" class="loading-state">
                <span class="loading-text">加载本体中...</span>
              </div>
              <select v-model="localOntologyCode" @change="onOntologyChange" class="param-select" :disabled="loadingOntologies">
                <option value="">选择本体</option>
                <option v-for="ontology in ontologies" :key="ontology.ontologyCode" :value="ontology.ontologyCode">
                  {{ ontology.ontologyName }}
                </option>
              </select>
            </div>

            <!-- MCP 工具选择 -->
            <div class="param-row">
              <label class="param-label">选择提交工具</label>
              <div v-if="loadingMcpTools" class="loading-state">
                <span class="loading-text">加载工具中...</span>
              </div>
              <select v-model="localToolName" @change="onToolChange" class="param-select" :disabled="loadingMcpTools">
                <option value="">选择 MCP 工具</option>
                <optgroup v-for="(tools, cat) in groupedTools" :key="cat" :label="getCategoryDisplayName(cat)">
                  <option v-for="tool in tools" :key="tool.name" :value="tool.name">
                    {{ tool.name }}
                  </option>
                </optgroup>
              </select>
            </div>
          </div>
          
          <!-- 工具描述 -->
          <div v-if="selectedTool" class="tool-desc">{{ selectedTool.description }}</div>
        </div>
      </div>

      <!-- 大模型配置 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('llm')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.llm }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>模型配置</span>
          </button>
          <div class="header-actions">
            <div class="help-container">
              <button 
                class="help-btn" 
                @mouseenter="handleTooltipEnter" 
                @mouseleave="handleTooltipLeave"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
              <div v-if="showModelTooltip" class="model-tooltip" @mouseenter="handleTooltipEnter" @mouseleave="handleTooltipLeave">
                <div class="tooltip-header">📌 大模型配置说明</div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【模型选择】</div>
                  <div class="tooltip-item">• Qwen-VL-Plus: 多模态任务（图文理解）</div>
                  <div class="tooltip-item">• Qwen-Plus: 通用文本任务</div>
                  <div class="tooltip-item">• GPT-4o/GPT-4: 复杂推理任务</div>
                  <div class="tooltip-item">• GPT-3.5 Turbo: 日常对话（性价比高）</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【温度值】控制随机性</div>
                  <div class="tooltip-item">• 0.0-0.3: 确定性强（事实问答、智能校验）</div>
                  <div class="tooltip-item">• 0.4-0.7: 平衡创意与稳定</div>
                  <div class="tooltip-item">• 0.8-1.0: 高度随机（创意写作）</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【输入变量】</div>
                  <div class="tooltip-item">• 选择作为表单智能推荐的输入数据源</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div v-if="expandedSections.llm" class="section-content">
          <div class="param-grid">
            <div class="param-row">
              <label class="param-label">模型选择</label>
              <select v-model="localModel" @change="emitUpdate" class="param-select" :disabled="modelsLoading">
                <option value="" disabled>请选择模型</option>
                <option
                  v-for="opt in modelOptions"
                  :key="opt.value"
                  :value="opt.value"
                >{{ opt.label }}</option>
              </select>
            </div>

            <div class="param-row">
              <label class="param-label">
                温度值
                <span class="help-icon" title="控制生成文本的随机性">?</span>
              </label>
              <div class="slider-control">
                <input v-model.number="localTemperature" type="range" min="0" max="1" step="0.1" @input="emitUpdate" class="param-slider"/>
                <div class="slider-value-group">
                  <input v-model.number="localTemperature" type="number" min="0" max="1" step="0.1" @input="emitUpdate" class="value-input"/>
                  <div class="adjust-buttons">
                    <button @click="adjustValue('temperature', -0.1)" class="adjust-btn">-</button>
                    <button @click="adjustValue('temperature', 0.1)" class="adjust-btn">+</button>
                  </div>
                </div>
              </div>
            </div>

            <div class="param-row full-width">
              <label class="param-label">输入变量</label>
              <VariableCascader
                v-model="localInputVariable"
                :available-variables="availableVariables"
                placeholder="选择输入变量"
                class="param-cascader"
                @change="emitUpdate"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 系统提示词 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('systemPrompt')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.systemPrompt }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>系统提示词</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="设置模型角色和行为规则（表单智能推荐）">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.systemPrompt" class="section-content">
          <textarea v-model="localSystemPrompt" @input="emitUpdate" placeholder="设置模型的角色和行为规则" class="multiline-input" rows="4"></textarea>
          <div v-if="!localSystemPrompt" class="weak-hint">建议配置系统提示词</div>
        </div>
      </div>

      <!-- 用户提示词 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('prompt')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.prompt }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>用户提示词</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="输入发送给模型的用户提示词，支持使用{变量名}或输入参数编码引用">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="expandedSections.prompt" class="section-content">
          <textarea v-model="localPrompt" @input="emitUpdate" placeholder="可以使用{变量名}引用输入参数或输入变量" class="answer-textarea" rows="6"></textarea>
        </div>
      </div>

      <!-- 输入参数配置 -->
      <div class="config-section collapsible-section">
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
          <div class="input-param-container">
            <!-- 参数表头 -->
            <div class="input-param-header">
              <span class="header-col header-code">参数编码</span>
              <span class="header-col header-name">参数名称</span>
              <span class="header-col header-type">参数类型</span>
              <span class="header-col header-required">是否必填</span>
              <span class="header-col header-source">取值来源</span>
              <span class="header-col header-value">取值内容</span>
              <span class="header-col header-action">操作</span>
            </div>
            <!-- 参数行 -->
            <template v-for="(param, index) in localInputs" :key="index">
              <div v-if="param" class="input-param-item">
                <div class="param-code-cell">
                  <input v-model="param.name" @input="emitUpdate" placeholder="参数编码" class="param-code-input" :class="{ error: !param.name }"/>
                </div>
                <div class="param-name-cell">
                  <input v-model="param.description" @input="emitUpdate" placeholder="参数名称" class="param-name-input"/>
                </div>
                <div class="param-type-cell">
                  <select v-model="param.type" @change="emitUpdate" class="param-type-select">
                    <option value="string">字符串</option>
                    <option value="number">数字</option>
                    <option value="boolean">布尔值</option>
                    <option value="array">数组</option>
                    <option value="object">对象</option>
                  </select>
                </div>
                <div class="param-required-cell">
                  <span>{{ param.required ? '是' : '否' }}</span>
                </div>
                <div class="param-source-cell">
                  <select v-model="param.sourceType" @change="handleSourceTypeChange(index)" class="param-source-select">
                    <option value="input">自定义</option>
                    <option value="ref">引用</option>
                  </select>
                </div>
                <div class="param-value-cell">
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
                    class="param-cascader"
                    @change="emitUpdate"
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
            <div v-if="localInputs.length === 0" class="no-params-hint">暂无输入参数，点击上方"+"添加</div>
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

      <!-- 工具参数配置 -->
      <div v-if="localToolName" class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('toolParams')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.toolParams }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>工具参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置工具执行参数">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="expandedSections.toolParams" class="section-content">
          <div class="tool-param-container">
            <div
              v-for="(param, index) in localParams"
              :key="index"
              class="tool-param-item"
            >
              <input
                v-model="param.name"
                @input="emitUpdate"
                placeholder="参数名"
                class="param-name-input"
                readonly
              />
              <select v-model="param.type" @change="emitUpdate" class="param-type-select" disabled>
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
                class="param-value-input"
              />
              <VariableCascader
                v-else
                v-model="param.value"
                :available-variables="availableVariables"
                placeholder="请选择变量"
                class="param-cascader"
                @change="emitUpdate"
              />
            </div>
            <div v-if="localParams.length === 0" class="no-params-hint">此工具无需参数</div>
          </div>

          <!-- 执行配置 -->
          <div class="execution-config">
            <div class="param-row">
              <label class="param-label">
                超时时间
                <span class="help-icon" title="工具执行超时时间（秒）">?</span>
              </label>
              <div class="slider-control">
                <input v-model.number="localTimeout" type="range" min="1" max="600" step="1" @input="emitUpdate" class="param-slider"/>
                <div class="slider-value-group">
                  <input v-model.number="localTimeout" type="number" min="1" max="600" step="1" @input="emitUpdate" class="value-input"/>
                  <div class="adjust-buttons">
                    <button @click="adjustValue('timeout', -10)" class="adjust-btn">-</button>
                    <button @click="adjustValue('timeout', 10)" class="adjust-btn">+</button>
                  </div>
                  <span class="unit-text">秒</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 收起按钮 -->
      <div v-if="configMode" class="collapse-section">
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
import { ref, reactive, watch, computed, onMounted, onUnmounted } from 'vue'
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

const emit = defineEmits(['update', 'close'])

const modelsStore = useModelsStore()
const workflowDataStore = useWorkflowDataStore()

const isUnmounted = ref(false)
const showAdvanced = ref(false)

// 展开状态
const expandedSections = reactive({
  basic: true,
  llm: true,
  systemPrompt: true,
  prompt: true,
  inputs: true,
  outputs: true,
  toolParams: true
})

const showModelTooltip = ref(false)
let hideTimer = null

const localOntologyCode = ref(props.data.ontologyCode || '')
const localToolName = ref(props.data.toolType || props.data.toolName || '')
const localParams = ref([])
const localTimeout = ref(props.data.timeout || 60)

const localModel = ref(props.data.model || '')
const localTemperature = ref(props.data.temperature || 0.3)
const localInputVariable = ref(props.data.inputVariable || '')
const localSystemPrompt = ref(props.data.systemPrompt || '')
const localPrompt = ref(props.data.prompt || '')

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

const handleTooltipEnter = () => {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  showModelTooltip.value = true
}

const handleTooltipLeave = () => {
  hideTimer = setTimeout(() => {
    showModelTooltip.value = false
    hideTimer = null
  }, 1000)
}

const adjustValue = (field, delta) => {
  const fieldMap = {
    temperature: localTemperature,
    timeout: localTimeout
  }
  const ref = fieldMap[field]
  if (ref) {
    ref.value = Math.round((ref.value + delta) * 100) / 100
    emitUpdate()
  }
}

// 切换展开状态
const toggleSection = (section) => {
  expandedSections[section] = !expandedSections[section]
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
    .filter(p => p)
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
    inputVariable: localInputVariable.value,
    systemPrompt: localSystemPrompt.value,
    prompt: localPrompt.value
  });
}

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value
}

watch(() => props.data, (d) => {
  localOntologyCode.value = d.ontologyCode || ''
  localToolName.value = d.toolType || d.toolName || ''
  localTimeout.value = d.timeout || 60

  // 模型配置
  localModel.value = d.model || ''
  localTemperature.value = d.temperature || 0.3
  localInputVariable.value = d.inputVariable || ''
  localSystemPrompt.value = d.systemPrompt || ''
  localPrompt.value = d.prompt || ''

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
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
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
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
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
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.form-node-config {
  padding: 0;
  background: #fff;
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

/* 配置区域样式 */
.config-section {
  padding: 0;
  border-bottom: 1px solid #f0f0f0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  cursor: pointer;
}

.section-header:hover {
  background: #f5f5f5;
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
  transition: transform 0.2s;
}

.section-toggle-btn svg.rotated {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #999;
  cursor: help;
  border-radius: 50%;
  transition: all 0.2s;
}

.help-btn:hover {
  background: #f0f0f0;
  color: #666;
}

.help-container {
  position: relative;
}

.model-tooltip {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  width: 400px;
  max-height: 500px;
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 16px;
  z-index: 1000;
  font-size: 13px;
  line-height: 1.6;
}

.model-tooltip::before {
  content: '';
  position: absolute;
  top: -8px;
  right: 12px;
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-bottom: 8px solid #e8e8e8;
}

.model-tooltip::after {
  content: '';
  position: absolute;
  top: -6px;
  right: 14px;
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-bottom: 6px solid #ffffff;
}

.tooltip-header {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.tooltip-section {
  margin-bottom: 12px;
}

.tooltip-section:last-child {
  margin-bottom: 0;
}

.tooltip-section-title {
  font-weight: 500;
  color: #3b82f6;
  margin-bottom: 6px;
}

.tooltip-item {
  color: #475569;
  padding-left: 8px;
  margin-bottom: 4px;
}

.tooltip-item:last-child {
  margin-bottom: 0;
}

.add-param-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: #3b82f6;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #2563eb;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
}

.section-content {
  padding: 16px;
  animation: slideDown 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 参数网格布局 */
.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-row.full-width {
  grid-column: 1 / -1;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #e8e8e8;
  color: #999;
  font-size: 11px;
  cursor: help;
  transition: all 0.2s;
}

.help-icon:hover {
  background: #3b82f6;
  color: white;
}

.param-select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  transition: all 0.2s;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 8px center;
  background-repeat: no-repeat;
  background-size: 12px;
  padding-right: 28px;
  box-sizing: border-box;
}

.param-select:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-select:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
  opacity: 0.7;
}

/* 滑块控件 */
.slider-control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-slider {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: #e8e8e8;
  outline: none;
  -webkit-appearance: none;
}

.param-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #3b82f6;
  cursor: pointer;
  transition: all 0.2s;
}

.param-slider::-webkit-slider-thumb:hover {
  transform: scale(1.2);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
}

.slider-value-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.value-input {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  text-align: center;
}

.value-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.adjust-buttons {
  display: flex;
  gap: 4px;
}

.adjust-btn {
  width: 24px;
  height: 24px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.adjust-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #e6f7ff;
}

.unit-text {
  font-size: 13px;
  color: #666;
}

.multiline-input {
  width: 100%;
  padding: 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  transition: all 0.2s;
  box-sizing: border-box;
}

.multiline-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.answer-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  transition: all 0.2s;
  box-sizing: border-box;
}

.answer-textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.weak-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

/* 加载状态 */
.loading-state {
  padding: 8px;
  text-align: center;
}

.loading-text {
  font-size: 11px;
  color: #94a3b8;
}

/* 工具描述 */
.tool-desc {
  font-size: 13px;
  color: #64748b;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 6px;
  line-height: 1.4;
  margin-top: 8px;
}

/* 输入参数容器 */
.input-param-container {
  overflow-x: auto;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

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

.input-param-header .header-code {
  width: 100px;
}

.input-param-header .header-name {
  width: 100px;
}

.input-param-header .header-type {
  width: 70px;
}

.input-param-header .header-required {
  width: 60px;
}

.input-param-header .header-source {
  width: 70px;
}

.input-param-header .header-value {
  flex: 1;
  min-width: 150px;
}

.input-param-header .header-action {
  width: 40px;
  display: flex;
  justify-content: center;
}

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

.param-code-cell {
  width: 100px;
}

.param-code-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-code-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-code-input.error {
  border-color: #ff4d4f;
}

.param-name-cell {
  width: 100px;
}

.param-name-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-name-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-type-cell {
  width: 70px;
}

.param-type-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  background: white;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 4px center;
  background-repeat: no-repeat;
  background-size: 10px;
  box-sizing: border-box;
}

.param-type-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-required-cell {
  width: 60px;
  font-size: 12px;
  color: #64748b;
  text-align: center;
}

.param-source-cell {
  width: 70px;
}

.param-source-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  background: white;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%23666' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 4px center;
  background-repeat: no-repeat;
  background-size: 10px;
  box-sizing: border-box;
}

.param-source-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-value-cell {
  flex: 1;
  min-width: 150px;
  margin-left: 8px;
}

.param-value-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  box-sizing: border-box;
}

.param-value-input:focus {
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
  border: none;
  background: transparent;
  color: #3b82f6;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: #f0f9ff;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
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
  box-sizing: border-box;
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
  box-sizing: border-box;
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
  border-color: #3b82f6;
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
  border-color: #3b82f6;
}

/* 工具参数容器 */
.tool-param-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.tool-param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #fafafa;
  border-radius: 4px;
}

.tool-param-item .param-name-input {
  width: 120px;
  background: #f0f0f0;
  cursor: not-allowed;
}

.tool-param-item .param-type-select {
  width: 90px;
  background: #f0f0f0;
  cursor: not-allowed;
}

.tool-param-item .param-value-input {
  flex: 1;
}

/* 执行配置 */
.execution-config {
  padding-top: 16px;
  border-top: 1px dashed #e8e8e8;
}

/* 无参数提示 */
.no-params-hint {
  font-size: 13px;
  color: #94a3b8;
  padding: 16px;
  text-align: center;
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

/* Handle 样式 */
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