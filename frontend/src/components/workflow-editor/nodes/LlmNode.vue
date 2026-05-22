<template>
  <div class="node llm-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ localModel || '未选择' }}</span>
      <div class="compact-direction-indicator">
        <span class="direction-arrow">⬇️</span>
        <span class="direction-text">输入 → 输出</span>
      </div>
    </div>
    
    <div v-if="configMode" class="llm-node-config">
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('model')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.model }">
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
                <div class="tooltip-header">📌 模型配置说明</div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【模型选择】</div>
                  <div class="tooltip-item">• Qwen-VL-Plus: 多模态任务（图文理解）</div>
                  <div class="tooltip-item">• Qwen-Plus: 通用文本任务</div>
                  <div class="tooltip-item">• GPT-4o/GPT-4: 复杂推理任务</div>
                  <div class="tooltip-item">• GPT-3.5 Turbo: 日常对话（性价比高）</div>
                  <div class="tooltip-item">• Claude 3 Opus: 超长上下文</div>
                  <div class="tooltip-item">• Claude 3 Sonnet: 平衡性能与成本</div>
                  <div class="tooltip-item">• Gemini 1.5 Pro: 多模态长上下文</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【温度值】控制随机性</div>
                  <div class="tooltip-item">• 0.0-0.3: 确定性强（事实问答、代码生成）</div>
                  <div class="tooltip-item">• 0.4-0.7: 平衡创意与稳定（日常对话）</div>
                  <div class="tooltip-item">• 0.8-1.0: 高度随机（创意写作）</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【top_k】固定候选数量</div>
                  <div class="tooltip-item">• 0: 不限制</div>
                  <div class="tooltip-item">• 10-30: 输出聚焦（精确任务）</div>
                  <div class="tooltip-item">• 50+: 增加多样性（创意任务）</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【top_p】核采样（动态候选）</div>
                  <div class="tooltip-item">• 0.1-0.5: 高度聚焦</div>
                  <div class="tooltip-item">• 0.6-0.9: 平衡多样与确定</div>
                  <div class="tooltip-item">• 0.95-1.0: 保留所有可能</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">【maxTokens】最大回复长度</div>
                  <div class="tooltip-item">• 256-512: 简短回答</div>
                  <div class="tooltip-item">• 512-1024: 中等长度</div>
                  <div class="tooltip-item">• 1024-4096: 长文本生成</div>
                </div>
                <div class="tooltip-section">
                  <div class="tooltip-section-title">💡 推荐组合</div>
                  <div class="tooltip-item">• 事实问答: temp=0.1-0.3, top_k=20-50, top_p=0.7-0.9</div>
                  <div class="tooltip-item">• 创意写作: temp=0.7-1.0, top_k=50+, top_p=0.9-1.0</div>
                  <div class="tooltip-item">• 代码生成: temp=0.1-0.3, top_k=30-50, maxTokens=2048</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div v-if="expandedSections.model" class="section-content">
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

            <div class="param-row">
              <label class="param-label">
                top_k
                <span class="help-icon" title="Top-K 采样：从概率最高的 K 个 token 中随机选择。0 表示不限制（默认），建议值：10-50">?</span>
              </label>
              <div class="slider-control">
                <input v-model.number="localTopK" type="range" min="0" max="100" step="1" @input="emitUpdate" class="param-slider"/>
                <div class="slider-value-group">
                  <input v-model.number="localTopK" type="number" min="0" max="100" step="1" @input="emitUpdate" class="value-input"/>
                  <div class="adjust-buttons">
                    <button @click="adjustValue('topK', -1)" class="adjust-btn">-</button>
                    <button @click="adjustValue('topK', 1)" class="adjust-btn">+</button>
                  </div>
                </div>
              </div>
              <div class="param-hint" v-if="localTopK === 0">未限制（从所有 token 中采样）</div>
              <div class="param-hint" v-else>从前 {{ localTopK }} 个 token 中采样</div>
            </div>

            <div class="param-row">
              <label class="param-label">
                top_p
                <span class="help-icon" title="核采样：从累计概率达到 P 的 token 中随机选择。1.0 表示不限制（默认），建议值：0.7-0.9">?</span>
              </label>
              <div class="slider-control">
                <input v-model.number="localTopP" type="range" min="0" max="1" step="0.05" @input="emitUpdate" class="param-slider"/>
                <div class="slider-value-group">
                  <input v-model.number="localTopP" type="number" min="0" max="1" step="0.01" @input="emitUpdate" class="value-input"/>
                  <div class="adjust-buttons">
                    <button @click="adjustValue('topP', -0.05)" class="adjust-btn top-p-btn">-</button>
                    <button @click="adjustValue('topP', 0.05)" class="adjust-btn top-p-btn">+</button>
                  </div>
                </div>
              </div>
              <div class="param-hint" v-if="localTopP >= 1">未限制（从所有 token 中采样）</div>
              <div class="param-hint" v-else>从累计概率 ≥ {{ (localTopP * 100).toFixed(0) }}% 的 token 中采样</div>
            </div>

            <div class="param-row tips-row">
              <div class="param-tips">
                <strong>使用建议：</strong>
                <ul>
                  <li>通常只需要设置其中一个参数</li>
                  <li>top_k 控制候选 token 数量，适合需要精确控制的场景</li>
                  <li>top_p 控制概率分布范围，适合需要多样性的场景</li>
                  <li>同时设置时，会取两者的交集</li>
                </ul>
              </div>
            </div>

            <div class="param-row full-width">
              <label class="param-label">
                最大回复长度
                <span class="help-icon" title="限制最大token数">?</span>
              </label>
              <div class="slider-control">
                <input v-model.number="localMaxTokens" type="range" min="1" max="4096" step="1" @input="emitUpdate" class="param-slider"/>
                <div class="slider-value-group">
                  <input v-model.number="localMaxTokens" type="number" min="1" max="4096" step="1" @input="emitUpdate" class="value-input"/>
                  <div class="adjust-buttons">
                    <button @click="adjustValue('maxTokens', -64)" class="adjust-btn">-</button>
                    <button @click="adjustValue('maxTokens', 64)" class="adjust-btn">+</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('systemPrompt')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.systemPrompt }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>系统提示词</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="设置模型角色和行为规则">
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

      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('inputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.inputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输入参数</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置输入参数">
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
            <template v-for="(param, index) in localInputs" :key="index">
              <div v-if="param" class="input-param-item">
                <input v-model="param.name" @input="emitUpdate" placeholder="参数名" class="param-name-input" :class="{ error: !param.name }"/>
                <select v-model="param.valueType" @change="handleValueTypeChange(index)" class="param-type-select">
                  <option value="input">输入</option>
                  <option value="reference">引用</option>
                </select>
                <input 
                  v-if="param.valueType === 'input'" 
                  v-model="param.defaultValue" 
                  @input="emitUpdate" 
                  placeholder="默认值" 
                  class="param-default-input"
                />
                <el-cascader 
                  v-if="param.valueType === 'reference'"
                  v-model="param.cascaderValue"
                  :options="cascaderOptions"
                  :props="{ expandTrigger: 'click' }"
                  placeholder="请选择变量"
                  class="param-cascader"
                  :popper-append-to-body="true"
                  @change="handleCascaderChange(index, $event)"
                ></el-cascader>
                <button @click="removeInputParam(index)" class="action-btn delete-btn" title="删除">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </template>
            <div v-if="localInputs.some(p => p && !p.name)" class="error-message">参数名不能为空</div>
            <div v-if="localInputs.some(p => p && p.valueType === 'reference' && !p.refValue)" class="error-message">引用变量不能为空</div>
        </div>
      </div>

      <div class="config-section">
        <label class="section-label">提示词内容</label>
        <textarea v-model="localPrompt" @input="emitUpdate" placeholder="可以使用{变量名}引用输入参数" class="answer-textarea" rows="6"></textarea>
        <div v-if="!localPrompt" class="error-message">提示词不能为空</div>
      </div>

      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('outputs')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.outputs }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>输出配置</span>
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
          <div class="history-toggle">
            <label class="toggle-label">
              <input v-model="localKeepHistory" @change="emitUpdate" type="checkbox" class="toggle-checkbox"/>
              <span class="toggle-text" :class="{ active: localKeepHistory }">保留对话历史</span>
            </label>
            <span class="help-icon" title="支持多轮对话"></span>
          </div>
            <template v-for="(param, index) in localOutputs" :key="index">
              <div v-if="param" class="output-param-item">
                <input v-model="param.name" @input="emitUpdate" placeholder="参数名" class="param-name-input"/>
                <select v-model="param.type" @change="emitUpdate" class="param-type-select">
                <option value="string">string</option>
                <option value="number">number</option>
                <option value="boolean">boolean</option>
                <option value="object">object</option>
                <option value="array">array</option>
              </select>
              <button @click="removeOutputParam(index)" class="action-btn delete-btn" title="删除">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
              </div>
            </template>
        </div>
      </div>

      <div class="collapse-btn">
        <button @click="$emit('close')">收起</button>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="targetPosition" id="target" />
    <Handle v-if="!configMode" type="source" :position="sourcePosition" id="source" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  availableVariables: { type: Array, default: () => [] },
  ...nodeDisplayProps
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update', 'close', 'run']);

const safeData = props.data || {};
const localLabel = ref(safeData.label || 'LLM');
const localModel = ref(safeData.model || '');
const localTemperature = ref(safeData.temperature ?? 0.1);
const localTopK = ref(safeData.topK ?? 0);
const localTopP = ref(safeData.topP ?? 1);
const localMaxTokens = ref(safeData.maxTokens ?? 1024);
const localSystemPrompt = ref(safeData.systemPrompt || '');
const localPrompt = ref(safeData.prompt || '');
const localKeepHistory = ref(safeData.keepHistory ?? false);

const localInputs = ref((safeData.inputs && Array.isArray(safeData.inputs)) ? safeData.inputs : []);
const localOutputs = ref((safeData.outputs && Array.isArray(safeData.outputs)) ? safeData.outputs : []);

const expandedSections = ref({
  model: true,
  systemPrompt: true,
  inputs: true,
  outputs: true
});

const showModelTooltip = ref(false);
let hideTimer = null;

// 组件是否已卸载
let isUnmounted = false;

// 可用模型列表（动态从API获取）
const availableModels = ref([]);
const modelsLoading = ref(false);

// 获取可用模型列表
const loadAvailableModels = async () => {
  if (modelsLoading.value) return;
  modelsLoading.value = true;
  try {
    const response = await fetch('/api/v1/chat/model/available');
    // 检查组件是否已卸载
    if (isUnmounted) return;
    const result = await response.json();
    if (isUnmounted) return;
    if (result.success && result.models) {
      availableModels.value = result.models;
    }
  } catch (e) {
    console.error('加载可用模型列表失败:', e);
  } finally {
    if (!isUnmounted) {
      modelsLoading.value = false;
    }
  }
};

const getNodeLabelById = (nodeId) => {
  if (nodeId.startsWith('start')) return '开始节点';
  if (nodeId.startsWith('variable')) return '变量节点';
  if (nodeId.startsWith('llm')) return 'LLM节点';
  if (nodeId.startsWith('prompt')) return '提示词节点';
  if (nodeId.startsWith('tool')) return '工具节点';
  if (nodeId.startsWith('http')) return 'HTTP节点';
  if (nodeId.startsWith('code')) return '代码节点';
  if (nodeId.startsWith('parser')) return '解析节点';
  if (nodeId.startsWith('condition')) return '条件节点';
  if (nodeId.startsWith('userInput')) return '用户输入节点';
  return nodeId;
};

const cascaderOptions = computed(() => {
  if (!props.availableVariables || !Array.isArray(props.availableVariables)) return [];
  
  const nodeMap = new Map();
  
  props.availableVariables.forEach(variable => {
    if (variable && variable.nodeId && variable.id && variable.name) {
      if (!nodeMap.has(variable.nodeId)) {
        let nodeLabel = getNodeLabelById(variable.nodeId);
        
        if (variable.nodeType === 'start') {
          nodeLabel = '开始节点';
        } else {
          const namePart = variable.name.split('(')[0].trim();
          if (namePart && !namePart.includes('输出') && !namePart.includes('入参')) {
            nodeLabel = namePart;
          }
        }
        
        nodeMap.set(variable.nodeId, {
          value: variable.nodeId,
          label: nodeLabel,
          children: []
        });
      }
      nodeMap.get(variable.nodeId).children.push({
        value: variable.id,
        label: variable.name
      });
    }
  });
  
  return Array.from(nodeMap.values());
});

onMounted(() => {
  loadAvailableModels();
});

// 监听 availableVariables 变化，为存量工作流的引用参数重建 cascaderValue
watch(() => props.availableVariables, (newVars) => {
  if (!newVars || newVars.length === 0) return;
  
  localInputs.value.forEach(param => {
    if (param && param.valueType === 'reference' && param.selectedNodeId && param.refValue && !param.cascaderValue?.length) {
      // 查找匹配的变量
      const matchedVar = newVars.find(v => 
        v.nodeId === param.selectedNodeId && 
        (v.id === `${param.selectedNodeId}.${param.refValue}` || v.name.startsWith(param.refValue))
      );
      
      if (matchedVar) {
        param.cascaderValue = [matchedVar.nodeId, matchedVar.id];
      }
    }
  });
}, { immediate: true });

// 监听 cascaderOptions 变化，清除已失效的 cascaderValue
watch(() => cascaderOptions.value, (newOptions) => {
  if (!newOptions || newOptions.length === 0) {
    // 选项为空时清除所有引用参数的 cascaderValue
    localInputs.value.forEach(param => {
      if (param && param.valueType === 'reference' && param.cascaderValue?.length) {
        param.cascaderValue = [];
      }
    });
    return;
  }

  // 检查每个引用参数的 cascaderValue 是否仍然有效
  localInputs.value.forEach(param => {
    if (param && param.valueType === 'reference' && param.cascaderValue?.length === 2) {
      const [nodeId] = param.cascaderValue;
      const nodeExists = newOptions.some(opt => opt.value === nodeId);
      if (!nodeExists) {
        param.cascaderValue = [];
      }
    }
  });
}, { immediate: false });

onUnmounted(() => {
  isUnmounted = true;
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
});

// 模型选项（格式化为 select 需要的选项）
const modelOptions = computed(() => {
  return availableModels.value.map(m => ({
    value: m.id,
    label: `${m.name} (${m.providerName || m.provider})`,
    raw: m
  }));
});

const handleTooltipEnter = () => {
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  showModelTooltip.value = true;
};

const handleTooltipLeave = () => {
  hideTimer = setTimeout(() => {
    showModelTooltip.value = false;
    hideTimer = null;
  }, 1000);
};

const adjustValue = (field, delta) => {
  const constraints = {
    temperature: { ref: localTemperature, min: 0, max: 2, round: 10 },
    topK: { ref: localTopK, min: 0, max: 100, round: 1 },
    topP: { ref: localTopP, min: 0, max: 1, round: 100 },
    maxTokens: { ref: localMaxTokens, min: 128, max: 16384, round: 1 }
  };
  const config = constraints[field];
  if (config) {
    const newValue = config.ref.value + delta;
    const clampedValue = Math.max(config.min, Math.min(config.max, newValue));
    config.ref.value = Math.round(clampedValue * config.round) / config.round;
    emitUpdate();
  }
};

const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

const addInputParam = () => {
  localInputs.value.push({ name: '', valueType: 'input', defaultValue: '', refValue: '', selectedNodeId: '', cascaderValue: [] });
  emitUpdate();
};

const handleValueTypeChange = (index) => {
  const param = localInputs.value[index];
  if (param.valueType === 'reference') {
    param.selectedNodeId = '';
    param.refValue = '';
  }
  emitUpdate();
};

const handleNodeSelect = (index) => {
  const param = localInputs.value[index];
  param.refValue = '';
  emitUpdate();
};

const handleCascaderChange = (index, value) => {
  const param = localInputs.value[index];
  if (param) {
    if (Array.isArray(value) && value.length === 2) {
      param.selectedNodeId = value[0];
      param.refValue = value[1];
      param.cascaderValue = value;
    } else {
      param.selectedNodeId = '';
      param.refValue = '';
      param.cascaderValue = [];
    }
    emitUpdate();
  }
};

const removeInputParam = (index) => {
  localInputs.value.splice(index, 1);
  emitUpdate();
};

const addOutputParam = () => {
  localOutputs.value.push({ name: '', type: 'string' });
  emitUpdate();
};

const removeOutputParam = (index) => {
  localOutputs.value.splice(index, 1);
  emitUpdate();
};

const emitUpdate = () => {
  if (!props.data || !props.data.id) return;
  emit('update', props.data.id, {
    label: localLabel.value,
    model: localModel.value,
    temperature: localTemperature.value,
    topK: localTopK.value,
    topP: localTopP.value,
    maxTokens: localMaxTokens.value,
    systemPrompt: localSystemPrompt.value,
    prompt: localPrompt.value,
    keepHistory: localKeepHistory.value,
    inputs: localInputs.value,
    outputs: localOutputs.value
  });
};

watch(() => props.data, (newData) => {
  if (!newData) return;
  localLabel.value = newData.label || 'LLM';
  localModel.value = newData.model || '';
  localTemperature.value = newData.temperature ?? 0.1;
  localTopK.value = Math.round(newData.topK ?? 0);
  localTopP.value = newData.topP ?? 1;
  localMaxTokens.value = newData.maxTokens ?? 1024;
  localSystemPrompt.value = newData.systemPrompt || '';
  localPrompt.value = newData.prompt || '';
  localKeepHistory.value = newData.keepHistory ?? false;
  localInputs.value = newData.inputs || [];
  localOutputs.value = newData.outputs || [];
}, { deep: true });
</script>

<style scoped>
.llm-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px; /* 统一节点最小高度 */
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.llm-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.llm-node.is-compact {
  min-width: 180px;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-size: 12px;
  font-weight: 600;
  flex: 1;
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

.compact-direction-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: #64748b;
}

.direction-arrow {
  font-size: 12px;
}

.direction-text {
  white-space: nowrap;
}

.llm-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.llm-node-config {
  padding: 0;
  background: #fff;
}

.config-section {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.collapsible-section {
  padding: 0;
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
  max-height: 600px;
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

.section-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  background: #fafafa;
  border-radius: 8px;
  margin: 16px;
}

.empty-state svg {
  margin-bottom: 12px;
  opacity: 0.6;
}

.empty-text {
  font-size: 13px;
  color: #999;
  margin: 0;
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
  margin: 0 auto;
}

.action-btn:hover:not(:disabled) {
  background: #f0f9ff;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
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

.collapse-btn {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
}

.collapse-btn button {
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

.collapse-btn button:hover {
  background: #2563eb;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

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

.weak-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}

.error-message {
  margin-top: 6px;
  font-size: 12px;
  color: #ff4d4f;
}

.input-param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: nowrap;
}

.output-param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
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
  border-color: #3b82f6;
}

.param-name-input.error {
  border-color: #ff4d4f;
}

.param-type-select {
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
}

.param-type-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-ref-select {
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
  min-width: 150px;
}

.param-ref-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-default-input {
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  background: white;
  min-width: 120px;
}

.param-default-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.param-cascader {
  min-width: 200px;
  width: 100%;
  flex-shrink: 0;
  font-size: 13px;
}

.history-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: #f5f5f5;
  border-radius: 4px;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.toggle-checkbox {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.toggle-text {
  font-size: 13px;
  color: #666;
  transition: color 0.2s;
}

.toggle-text.active {
  color: #3b82f6;
  font-weight: 500;
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

.param-hint {
  font-size: 12px;
  color: #666;
  margin-top: 4px;
}

.param-row.tips-row {
  padding: 10px;
  background: #f8fafc;
  border-radius: 4px;
  border-left: 3px solid #3b82f6;
}

.param-tips {
  font-size: 12px;
  color: #666;
}

.param-tips strong {
  color: #333;
  font-size: 13px;
}

.param-tips ul {
  margin: 8px 0 0 0;
  padding-left: 20px;
  list-style-type: disc;
}

.param-tips li {
  margin-bottom: 4px;
}
</style>