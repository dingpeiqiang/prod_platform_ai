<template>
  <div class="node user-input-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">👤</span>
      <span class="node-title">{{ data.label || '用户输入' }}</span>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ localInputType === 'text' ? '文本输入' : localInputType === 'select' ? '下拉选择' : '确认框' }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    
    <div v-if="configMode" class="user-input-config">
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
            <button class="help-btn" title="配置用于构建提示词的输入参数">
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
      <!-- 用户输入提示配置 -->
      <div class="config-section collapsible-section">
        <div class="section-header">
          <button @click="toggleSection('prompt')" class="section-toggle-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: expandedSections.prompt }">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
            <span>用户输入提示</span>
          </button>
          <div class="header-actions">
            <button class="help-btn" title="配置用户输入的提示信息">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-if="expandedSections.prompt" class="section-content">
          <div class="param-row">
            <label class="param-label">提示消息</label>
            <textarea v-model="localPrompt" @input="emitUpdate" placeholder="请输入提示用户的消息内容..." class="multiline-input" rows="4"></textarea>
          </div>

          <div class="param-row">
            <label class="param-label">输入类型</label>
            <select v-model="localInputType" @change="emitUpdate" class="param-select">
              <option value="text">文本输入</option>
              <option value="select">下拉选择</option>
              <option value="confirm">确认框（是/否）</option>
            </select>
          </div>

          <div v-if="localInputType === 'select'" class="param-row">
            <label class="param-label">选项列表</label>
            <textarea v-model="localOptions" @input="emitUpdate" placeholder="每行一个选项，例如：
选项1
选项2
选项3" class="multiline-input" rows="4"></textarea>
          </div>

          <div class="param-row">
            <label class="param-label">
              <input v-model="localValidationEnabled" @change="emitUpdate" type="checkbox" class="checkbox-input"/>
              启用输入校验
            </label>
          </div>

          <div v-if="localValidationEnabled" class="param-row">
            <label class="param-label">校验失败提示</label>
            <input v-model="localValidationErrorMessage" @input="emitUpdate" placeholder="您的输入不符合要求，请重新输入" class="param-input"/>
          </div>

          <div v-if="localValidationEnabled" class="param-row">
            <label class="param-label">校验规则（JSON格式）</label>
            <div class="validation-rules-container">
              <textarea v-model="localValidationRulesJson" @input="emitUpdate" placeholder='[{"type": "required", "message": "必填"}, {"type": "minLength", "value": 2, "message": "至少2个字符"}]' class="multiline-input" rows="3"></textarea>
              <button @click="showAiGenerator = !showAiGenerator" class="ai-generate-btn" :title="showAiGenerator ? '关闭' : 'AI生成校验规则'">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 18h.01M12 6v8M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                </svg>
                AI生成
              </button>
            </div>
            <div v-if="showAiGenerator" class="ai-generator-panel">
              <textarea v-model="aiDescription" @input="onAiDescriptionInput" placeholder="请描述校验规则要求，例如：用户输入必须是有效的手机号码，11位数字，以1开头..." class="multiline-input ai-desc-input" rows="2"></textarea>
              <div class="ai-generator-actions">
                <button @click="generateValidationRules" :disabled="aiGenerating || !aiDescription.trim()" class="generate-btn">
                  <span v-if="aiGenerating" class="loading-spinner"></span>
                  {{ aiGenerating ? '生成中...' : '生成' }}
                </button>
                <button @click="showAiGenerator = false" class="cancel-btn">取消</button>
              </div>
            </div>
          </div>

          <div class="param-row">
            <label class="param-label">
              <input v-model="localParseWithLLM" @change="emitUpdate" type="checkbox" class="checkbox-input"/>
              启用智能输入解析
            </label>
          </div>

          <div v-if="localParseWithLLM" class="param-row">
            <label class="param-label">解析提示词</label>
            <textarea v-model="localParsePrompt" @input="emitUpdate" placeholder="将用户输入解析为结构化数据，提取name和age字段" class="multiline-input" rows="3"></textarea>
          </div>

          <div v-if="localParseWithLLM" class="param-row">
            <label class="param-label">输出JSON Schema</label>
            <textarea v-model="localParseSchemaJson" @input="emitUpdate" placeholder='{"type": "object", "properties": {"name": {"type": "string"}}}' class="multiline-input" rows="3"></textarea>
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
          <div v-if="localOutputs.length === 0" class="weak-hint">默认会将用户输入保存到 user_input 变量</div>
        </div>
      </div>

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
import { ref, watch, computed } from 'vue';
import { Handle } from '@vue-flow/core';
import { nodeDisplayProps } from './nodeDisplayProps.js';
import { useNodeAnchorMode } from './useHandlePosition.js';
import VariableCascader from '../VariableCascader.vue';
import { workflowApi } from '@/services/workflowApi.js';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  availableVariables: { type: Array, default: () => [] },
  ...nodeDisplayProps
});

const { targetPosition, sourcePosition } = useNodeAnchorMode(props);

const emit = defineEmits(['update', 'close']);

const safeData = props.data || {};
const localLabel = ref(safeData.label || '用户输入');
const localPrompt = ref(safeData.prompt || safeData.message || '');
const localInputType = ref(safeData.inputType || 'text');
const localOptions = ref(safeData.options || '');
const localValidationEnabled = ref(safeData.validationEnabled ?? safeData.validation_enabled ?? false);
const localValidationErrorMessage = ref(safeData.validationErrorMessage || safeData.validation_error_message || '');
const localValidationRulesJson = ref('');
const localParseWithLLM = ref(safeData.parseWithLLM ?? safeData.parse_with_llm ?? false);
const localParsePrompt = ref(safeData.parsePrompt || safeData.parse_prompt || '');
const localParseSchemaJson = ref('');

const localInputs = ref((safeData.inputParams && Array.isArray(safeData.inputParams)) ? safeData.inputParams : []);
const localOutputs = ref((safeData.outputParams || []).map(p => ({
  name: p.name || '',
  nameType: p.nameType || 'input',
  nameRef: p.nameRef || '',
  source: p.source || '',
  type: p.type || 'string',
  description: p.description || p.desc || ''
})));

// AI 校验规则生成
const showAiGenerator = ref(false);
const aiDescription = ref('');
const aiGenerating = ref(false);

// 强制 VariableCascader 刷新 key，当 availableVariables 变化时重新挂载
const cascaderRefreshKey = ref(0);
watch(() => props.availableVariables, () => {
  cascaderRefreshKey.value++;
}, { deep: true });

const expandedSections = ref({
  inputs: true,
  prompt: true,
  outputs: true
});

// 初始化JSON字段
const initJsonFields = () => {
  if (safeData.validationRules || safeData.validation_rules) {
    const rules = safeData.validationRules || safeData.validation_rules;
    if (typeof rules === 'string') {
      localValidationRulesJson.value = rules;
    } else if (Array.isArray(rules)) {
      try {
        localValidationRulesJson.value = JSON.stringify(rules, null, 2);
      } catch (e) {
        localValidationRulesJson.value = '';
      }
    }
  }
  
  if (safeData.parseSchema || safeData.parse_schema) {
    const schema = safeData.parseSchema || safeData.parse_schema;
    if (typeof schema === 'string') {
      localParseSchemaJson.value = schema;
    } else if (schema && typeof schema === 'object') {
      try {
        localParseSchemaJson.value = JSON.stringify(schema, null, 2);
      } catch (e) {
        localParseSchemaJson.value = '';
      }
    }
  }
};

initJsonFields();

const toggleSection = (section) => {
  expandedSections.value[section] = !expandedSections.value[section];
};

const parseJson = (jsonStr) => {
  if (!jsonStr || !jsonStr.trim()) return null;
  try {
    return JSON.parse(jsonStr);
  } catch (e) {
    return null;
  }
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

const handleCascaderChange = (index, value) => {
  const param = localInputs.value[index];
  if (param) {
    param.refValue = value || '';
    emitUpdate();
  }
};

const removeInputParam = (index) => {
  localInputs.value.splice(index, 1);
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
  localOutputs.value.splice(index, 1);
  emitUpdate();
};

// AI生成校验规则
const onAiDescriptionInput = () => {
  // 自动调整 textarea 高度
};

const generateValidationRules = async () => {
  if (!aiDescription.value.trim() || aiGenerating.value) return;
  
  aiGenerating.value = true;
  try {
    const response = await workflowApi.generateValidationRules(
      aiDescription.value.trim(),
      localInputType.value
    );
    
    if (response.success && response.rules && response.rules.length > 0) {
      localValidationRulesJson.value = JSON.stringify(response.rules, null, 2);
      if (response.errorMessage) {
        localValidationErrorMessage.value = response.errorMessage;
      }
      showAiGenerator.value = false;
      aiDescription.value = '';
      emitUpdate();
    } else {
      console.warn('AI生成校验规则失败:', response.message);
    }
  } catch (error) {
    console.error('AI生成校验规则出错:', error);
  } finally {
    aiGenerating.value = false;
  }
};

const emitUpdate = () => {
  const validationRules = parseJson(localValidationRulesJson.value);
  const parseSchema = parseJson(localParseSchemaJson.value);
  
  emit('update', props.data.id, {
    label: localLabel.value,
    prompt: localPrompt.value,
    message: localPrompt.value,
    inputType: localInputType.value,
    options: localOptions.value,
    validationEnabled: localValidationEnabled.value,
    validation_enabled: localValidationEnabled.value,
    validationErrorMessage: localValidationErrorMessage.value,
    validation_error_message: localValidationErrorMessage.value,
    validationRules: validationRules,
    validation_rules: validationRules,
    parseWithLLM: localParseWithLLM.value,
    parse_with_llm: localParseWithLLM.value,
    parsePrompt: localParsePrompt.value,
    parse_prompt: localParsePrompt.value,
    parseSchema: parseSchema,
    parse_schema: parseSchema,
    inputs: localInputs.value,
    inputParams: localInputs.value.length > 0 ? localInputs.value : undefined,
    outputParams: localOutputs.value
      .filter(p => p)
      .map(p => ({
        name: p.name || '',
        nameType: p.nameType || 'input',
        nameRef: p.nameRef || '',
        source: p.source || '',
        type: p.type || 'string',
        description: p.description || ''
      }))
  });
};

watch(() => props.data, (newData) => {
  if (!newData) return;
  localLabel.value = newData.label || '用户输入';
  localPrompt.value = newData.prompt || newData.message || '';
  localInputType.value = newData.inputType || 'text';
  localOptions.value = newData.options || '';
  localValidationEnabled.value = newData.validationEnabled ?? newData.validation_enabled ?? false;
  localValidationErrorMessage.value = newData.validationErrorMessage || newData.validation_error_message || '';
  localParseWithLLM.value = newData.parseWithLLM ?? newData.parse_with_llm ?? false;
  localParsePrompt.value = newData.parsePrompt || newData.parse_prompt || '';
  localInputs.value = (newData.inputParams || newData.inputs || []).map(p => ({
    name: p.name || '',
    valueType: (p.value && p.value.startsWith('{{')) ? 'reference' : 'input',
    defaultValue: (p.value && !p.value.startsWith('{{')) ? p.value : '',
    refValue: (p.value && p.value.startsWith('{{')) ? p.value : '',
    selectedNodeId: '',
    cascaderValue: []
  }));
  localOutputs.value = (newData.outputParams || []).map(p => ({
    name: p.name || '',
    nameType: p.nameType || 'input',
    nameRef: p.nameRef || '',
    source: p.source || '',
    type: p.type || 'string',
    description: p.description || p.desc || ''
  }));
  initJsonFields();
}, { deep: true });
</script>

<style scoped>
.user-input-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 180px;
  min-height: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.user-input-node.selected {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
}

.user-input-node.is-compact {
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

.user-input-node.is-config-mode {
  min-width: unset;
  width: 100%;
  box-shadow: none;
  border-radius: 0;
  background: #ffffff;
  color: #333;
}

.user-input-config {
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

.add-param-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: #f97316;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-param-btn:hover {
  background: #ea580c;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.3);
}

.section-content {
  padding: 16px;
  animation: slideDown 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

.param-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.param-row:last-child {
  margin-bottom: 0;
}

.param-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  cursor: pointer;
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
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
}

.param-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.param-input:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
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
  border-color: #f97316;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.1);
}

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
  background: #f97316;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-all-btn:hover {
  background: #ea580c;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.3);
}

.action-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #f97316;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
  margin: 0 auto;
}

.action-btn:hover:not(:disabled) {
  background: #fff7ed;
}

.action-btn.delete-btn:hover {
  background: #fff1f0;
  color: #f5222d;
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

.param-type-cell {
  width: 90px;
}

.param-value-cell {
  flex: 1;
  min-width: 200px;
  margin-left: 8px;
}

.param-action-cell {
  width: 40px;
  display: flex;
  justify-content: center;
  margin-left: 8px;
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
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.param-name-input.error {
  border-color: #ff4d4f;
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

/* 输出参数表头样式 */
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
  border-color: #f97316;
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
  border-color: #f97316;
}

.param-source-cell {
  width: 180px;
  min-width: 180px;
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
  box-sizing: border-box;
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

.param-name-input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}

.param-name-input:focus {
  outline: none;
  border-color: #f97316;
}

.param-name-input.error {
  border-color: #ff4d4f;
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
  border-color: #f97316;
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
  border-color: #f97316;
}

.param-cascader {
  min-width: 200px;
  font-size: 13px;
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

:deep(.vue-flow__handle) {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid white !important;
  border-radius: 50% !important;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.3) !important;
  cursor: crosshair !important;
  transition: all 0.2s ease !important;
}

:deep(.vue-flow__handle:hover) {
  width: 24px !important;
  height: 24px !important;
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.5) !important;
}

:deep(.vue-flow__handle[type="target"]) {
  background-color: #fb923c !important;
}

:deep(.vue-flow__handle[type="target"]:hover) {
  background-color: #f97316 !important;
}

:deep(.vue-flow__handle[type="source"]) {
  background-color: #f97316 !important;
}

:deep(.vue-flow__handle[type="source"]:hover) {
  background-color: #ea580c !important;
}

/* AI 校验规则生成器样式 */
.validation-rules-container {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.validation-rules-container .multiline-input {
  flex: 1;
}

.ai-generate-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #6366f1;
  background: #f0f0ff;
  color: #6366f1;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  transition: all 0.2s;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-generate-btn:hover {
  background: #6366f1;
  color: white;
}

.ai-generator-panel {
  margin-top: 8px;
  padding: 12px;
  background: #f8f9ff;
  border: 1px solid #e0e0ff;
  border-radius: 8px;
}

.ai-desc-input {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  min-height: 48px;
}

.ai-generator-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  justify-content: flex-end;
}

.generate-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.2s;
}

.generate-btn:hover:not(:disabled) {
  background: #4f46e5;
}

.generate-btn:disabled {
  background: #a5b4fc;
  cursor: not-allowed;
}

.cancel-btn {
  padding: 6px 16px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: #e2e8f0;
}

.loading-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
