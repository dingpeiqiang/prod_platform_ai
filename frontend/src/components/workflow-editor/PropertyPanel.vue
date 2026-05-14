<template>
  <div class="properties-panel" :class="{ collapsed: !expanded }">
    <div class="panel-header">
      <h3>属性</h3>
      <button @click="$emit('toggle')" class="panel-close-btn">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/>
          <line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    <div class="panel-content">
      <div v-if="nodeData" class="properties-content">
        <div class="property-section">
          <h4>基本信息</h4>
          <div class="property-group">
            <label>节点名称</label>
            <input
              v-model="localLabel"
              type="text"
              class="property-input"
              @input="onLabelChange"
            />
          </div>
          <div class="property-group">
            <label>节点类型</label>
            <span class="property-value">{{ nodeTypeLabel }}</span>
          </div>
          <div class="property-group">
            <label>节点ID</label>
            <span class="property-value mono">{{ nodeData.id }}</span>
          </div>
        </div>

        <div v-if="nodeData.type === 'llm'" class="property-section">
          <h4>LLM 配置</h4>
          <div class="property-group">
            <label>模型</label>
            <select :value="nodeData.model" class="property-select" @change="onModelChange">
              <option value="qwen-vl-plus">Qwen-VL-Plus</option>
              <option value="gpt-4o">GPT-4o</option>
              <option value="claude-3-opus">Claude 3 Opus</option>
            </select>
          </div>
          <div class="property-group">
            <label>温度: {{ nodeData.temperature || 0.7 }}</label>
            <input
              :value="nodeData.temperature || 0.7"
              type="range"
              min="0"
              max="2"
              step="0.1"
              class="property-range"
              @input="onTemperatureChange"
            />
          </div>
        </div>

        <div v-if="nodeData.type === 'prompt'" class="property-section">
          <h4>提示词配置</h4>
          <div class="property-group">
            <label>提示词内容</label>
            <textarea
              :value="nodeData.prompt || ''"
              class="property-textarea"
              rows="4"
              @input="onPromptChange"
              placeholder="请输入提示词..."
            ></textarea>
          </div>
        </div>

        <div v-if="nodeData.type === 'tool'" class="property-section">
          <h4>工具配置</h4>
          <div class="property-group">
            <label>工具类型</label>
            <select :value="nodeData.toolType" class="property-select" @change="onToolTypeChange">
              <option value="web_search">网页搜索</option>
              <option value="database">数据库查询</option>
              <option value="api">API调用</option>
            </select>
          </div>
        </div>

        <div v-if="nodeData.type === 'condition'" class="property-section">
          <h4>条件配置</h4>
          <div class="property-group">
            <label>条件表达式</label>
            <input
              :value="nodeData.condition || ''"
              type="text"
              class="property-input"
              @input="onConditionChange"
              placeholder="输入条件表达式..."
            />
          </div>
        </div>

        <div v-if="nodeData.type === 'loop'" class="property-section">
          <h4>循环配置</h4>
          <div class="property-group">
            <label>迭代次数: {{ nodeData.iterations || 3 }}</label>
            <input
              :value="nodeData.iterations || 3"
              type="number"
              min="1"
              max="100"
              class="property-input"
              @input="onIterationsChange"
            />
          </div>
        </div>

        <div v-if="nodeData.type === 'variable'" class="property-section">
          <h4>变量配置</h4>
          <div class="property-group">
            <label>变量名</label>
            <input
              :value="nodeData.variableName || ''"
              type="text"
              class="property-input"
              @input="onVariableNameChange"
              placeholder="变量名"
            />
          </div>
          <div class="property-group">
            <label>变量值</label>
            <input
              :value="nodeData.variableValue || ''"
              type="text"
              class="property-input"
              @input="onVariableValueChange"
              placeholder="变量值"
            />
          </div>
        </div>

        <div v-if="nodeData.type === 'http'" class="property-section">
          <h4>HTTP配置</h4>
          <div class="property-group">
            <label>请求方法</label>
            <select :value="nodeData.method || 'GET'" class="property-select" @change="onHttpMethodChange">
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
            </select>
          </div>
          <div class="property-group">
            <label>URL</label>
            <input
              :value="nodeData.url || ''"
              type="text"
              class="property-input"
              @input="onUrlChange"
              placeholder="输入URL..."
            />
          </div>
        </div>

        <div v-if="nodeData.type === 'code'" class="property-section">
          <h4>代码配置</h4>
          <div class="property-group">
            <label>代码内容</label>
            <textarea
              :value="nodeData.code || ''"
              class="property-textarea code"
              rows="6"
              @input="onCodeChange"
              placeholder="输入JavaScript代码..."
            ></textarea>
          </div>
        </div>

        <div v-if="nodeData.type === 'parser'" class="property-section">
          <h4>解析配置</h4>
          <div class="property-group">
            <label>解析格式</label>
            <select :value="nodeData.format || 'json'" class="property-select" @change="onFormatChange">
              <option value="json">JSON</option>
              <option value="text">文本</option>
              <option value="xml">XML</option>
            </select>
          </div>
        </div>
      </div>
      <div v-else class="empty-properties">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="6"/>
          <circle cx="12" cy="12" r="2"/>
        </svg>
        <p>选择一个节点查看属性</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  nodeData: {
    type: Object,
    default: null
  },
  expanded: {
    type: Boolean,
    default: true
  },
  nodeTypeLabel: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['toggle', 'update']);

const localLabel = ref('');

watch(() => props.nodeData, (data) => {
  if (data) {
    localLabel.value = data.label || '';
  } else {
    localLabel.value = '';
  }
}, { immediate: true });

const updateNodeData = (key, value) => {
  emit('update', { key, value });
};

const onLabelChange = (event) => {
  updateNodeData('label', event.target.value);
};

const onModelChange = (event) => {
  updateNodeData('model', event.target.value);
};

const onTemperatureChange = (event) => {
  updateNodeData('temperature', parseFloat(event.target.value));
};

const onPromptChange = (event) => {
  updateNodeData('prompt', event.target.value);
};

const onToolTypeChange = (event) => {
  updateNodeData('toolType', event.target.value);
};

const onConditionChange = (event) => {
  updateNodeData('condition', event.target.value);
};

const onIterationsChange = (event) => {
  updateNodeData('iterations', parseInt(event.target.value) || 3);
};

const onVariableNameChange = (event) => {
  updateNodeData('variableName', event.target.value);
};

const onVariableValueChange = (event) => {
  updateNodeData('variableValue', event.target.value);
};

const onHttpMethodChange = (event) => {
  updateNodeData('method', event.target.value);
};

const onUrlChange = (event) => {
  updateNodeData('url', event.target.value);
};

const onCodeChange = (event) => {
  updateNodeData('code', event.target.value);
};

const onFormatChange = (event) => {
  updateNodeData('format', event.target.value);
};
</script>

<style scoped>
.properties-panel {
  width: 260px;
  background-color: #f8fafc;
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;
}

.properties-panel.collapsed {
  width: 0;
  overflow: hidden;
  border-left: none;
}

.properties-panel .panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.properties-panel .panel-header h3 {
  margin: 0;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.panel-close-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.panel-close-btn:hover {
  background-color: #e2e8f0;
}

.properties-panel .panel-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.property-section {
  margin-bottom: 16px;
}

.property-section h4 {
  margin: 0 0 8px 0;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

.property-group {
  margin-bottom: 8px;
}

.property-group label {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.property-input, .property-select {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.property-textarea {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  resize: vertical;
}

.property-textarea.code {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
}

.property-range {
  width: 100%;
}

.property-value {
  font-size: 12px;
  color: #334155;
}

.property-value.mono {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  background-color: #0f172a;
  color: #e2e8f0;
  padding: 2px 6px;
  border-radius: 3px;
}

.empty-properties {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.empty-properties p {
  margin-top: 8px;
  font-size: 12px;
}
</style>