<template>
  <div class="node http-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🌐</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleAdvanced" class="advanced-toggle" :class="{ active: showAdvanced }">
        ⚙
      </button>
    </div>
    <div class="node-body">
      <div class="method-url-row">
        <select v-model="localMethod" @change="emitUpdate" class="method-select">
          <option value="GET">GET</option>
          <option value="POST">POST</option>
          <option value="PUT">PUT</option>
          <option value="DELETE">DELETE</option>
          <option value="PATCH">PATCH</option>
          <option value="HEAD">HEAD</option>
        </select>
        <input v-model="localUrl" @input="emitUpdate" placeholder="URL" class="url-input" />
      </div>

      <div v-if="showAdvanced" class="advanced-panel">
        <div class="section-title">认证方式</div>
        <select v-model="localAuthType" @change="emitUpdate" class="node-select">
          <option value="none">无</option>
          <option value="basic">Basic Auth</option>
          <option value="bearer">Bearer Token</option>
          <option value="api-key">API Key</option>
        </select>

        <div v-if="localAuthType === 'basic'" class="auth-fields">
          <input v-model="localAuthUser" @input="emitUpdate" placeholder="用户名" class="node-input" />
          <input v-model="localAuthPass" @input="emitUpdate" type="password" placeholder="密码" class="node-input" />
        </div>

        <div v-if="localAuthType === 'bearer'" class="auth-fields">
          <input v-model="localBearerToken" @input="emitUpdate" placeholder="Bearer Token" class="node-input" />
        </div>

        <div v-if="localAuthType === 'api-key'" class="auth-fields">
          <input v-model="localApiKeyName" @input="emitUpdate" placeholder="Key 名称" class="node-input" />
          <input v-model="localApiKeyValue" @input="emitUpdate" type="password" placeholder="Key 值" class="node-input" />
          <select v-model="localApiKeyLocation" @change="emitUpdate" class="node-select">
            <option value="header">请求头</option>
            <option value="query">URL 参数</option>
          </select>
        </div>

        <div class="section-title">请求头</div>
        <div class="headers-container">
          <div 
            v-for="(header, index) in localHeaders" 
            :key="index" 
            class="header-row"
          >
            <input 
              v-model="header.key" 
              @input="emitUpdate" 
              placeholder="Key" 
              class="header-key" 
            />
            <input 
              v-model="header.value" 
              @input="emitUpdate" 
              placeholder="Value" 
              class="header-value" 
            />
            <button @click="removeHeader(index)" class="remove-header-btn">✕</button>
          </div>
          <button @click="addHeader" class="add-header-btn">+ 添加请求头</button>
        </div>

        <div class="section-title">请求体</div>
        <select v-model="localBodyType" @change="emitUpdate" class="node-select">
          <option value="none">无</option>
          <option value="json">JSON</option>
          <option value="form">表单数据</option>
          <option value="raw">原始文本</option>
        </select>

        <div v-if="localBodyType !== 'none'" class="body-content">
          <textarea 
            v-model="localBody" 
            @input="emitUpdate" 
            :placeholder="getBodyPlaceholder()"
            class="node-textarea"
          ></textarea>
        </div>

        <div class="section-title">超时配置</div>
        <div class="timeout-row">
          <input 
            v-model.number="localTimeout" 
            @input="emitUpdate" 
            type="number" 
            min="1" 
            max="300" 
            class="timeout-input"
          />
          <span class="timeout-unit">秒</span>
        </div>

        <div class="section-title">高级选项</div>
        <label class="checkbox-label">
          <input v-model="localFollowRedirects" @change="emitUpdate" type="checkbox" />
          <span>跟随重定向</span>
        </label>
        <label class="checkbox-label">
          <input v-model="localVerifySSL" @change="emitUpdate" type="checkbox" />
          <span>验证 SSL 证书</span>
        </label>
      </div>
    </div>
    <Handle type="target" :position="Position.Left" id="target" />
    <Handle type="source" :position="Position.Right" id="source" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
});

const emit = defineEmits(['update']);

const showAdvanced = ref(false);

const localMethod = ref(props.data.httpMethod || 'GET');
const localUrl = ref(props.data.httpUrl || '');
const localAuthType = ref(props.data.authType || 'none');
const localAuthUser = ref(props.data.authUser || '');
const localAuthPass = ref(props.data.authPass || '');
const localBearerToken = ref(props.data.bearerToken || '');
const localApiKeyName = ref(props.data.apiKeyName || '');
const localApiKeyValue = ref(props.data.apiKeyValue || '');
const localApiKeyLocation = ref(props.data.apiKeyLocation || 'header');
const localHeaders = ref(props.data.headers || [{ key: '', value: '' }]);
const localBodyType = ref(props.data.bodyType || 'none');
const localBody = ref(props.data.body || '');
const localTimeout = ref(props.data.timeout || 30);
const localFollowRedirects = ref(props.data.followRedirects !== false);
const localVerifySSL = ref(props.data.verifySSL !== false);

const toggleAdvanced = () => {
  showAdvanced.value = !showAdvanced.value;
};

const addHeader = () => {
  localHeaders.value.push({ key: '', value: '' });
  emitUpdate();
};

const removeHeader = (index) => {
  if (localHeaders.value.length > 1) {
    localHeaders.value.splice(index, 1);
    emitUpdate();
  }
};

const getBodyPlaceholder = () => {
  switch (localBodyType.value) {
    case 'json':
      return '{"key": "value"}';
    case 'form':
      return 'key1=value1&key2=value2';
    case 'raw':
      return '输入原始文本...';
    default:
      return '';
  }
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    httpMethod: localMethod.value,
    httpUrl: localUrl.value,
    authType: localAuthType.value,
    authUser: localAuthUser.value,
    authPass: localAuthPass.value,
    bearerToken: localBearerToken.value,
    apiKeyName: localApiKeyName.value,
    apiKeyValue: localApiKeyValue.value,
    apiKeyLocation: localApiKeyLocation.value,
    headers: localHeaders.value,
    bodyType: localBodyType.value,
    body: localBody.value,
    timeout: localTimeout.value,
    followRedirects: localFollowRedirects.value,
    verifySSL: localVerifySSL.value
  });
};

watch(() => props.data, (d) => {
  localMethod.value = d.httpMethod || 'GET';
  localUrl.value = d.httpUrl || '';
  localAuthType.value = d.authType || 'none';
  localAuthUser.value = d.authUser || '';
  localAuthPass.value = d.authPass || '';
  localBearerToken.value = d.bearerToken || '';
  localApiKeyName.value = d.apiKeyName || '';
  localApiKeyValue.value = d.apiKeyValue || '';
  localApiKeyLocation.value = d.apiKeyLocation || 'header';
  localHeaders.value = d.headers || [{ key: '', value: '' }];
  localBodyType.value = d.bodyType || 'none';
  localBody.value = d.body || '';
  localTimeout.value = d.timeout || 30;
  localFollowRedirects.value = d.followRedirects !== false;
  localVerifySSL.value = d.verifySSL !== false;
}, { deep: true });
</script>

<style scoped>
.http-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 280px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.http-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);
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

.method-url-row {
  display: flex;
  gap: 6px;
}

.method-select {
  width: 80px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: white;
}

.method-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.url-input {
  flex: 1;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.url-input:focus {
  outline: none;
  border-color: #3b82f6;
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

.auth-fields {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.node-input {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
}

.node-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.headers-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.header-row {
  display: flex;
  gap: 4px;
}

.header-key {
  width: 80px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.header-value {
  flex: 1;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.remove-header-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #fee2e2;
  border-radius: 4px;
  color: #dc2626;
  cursor: pointer;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-header-btn {
  padding: 4px 8px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 11px;
  text-align: left;
}

.add-header-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.body-content {
  margin-bottom: 8px;
}

.node-textarea {
  width: 100%;
  min-height: 80px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  resize: vertical;
  font-family: monospace;
}

.node-textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

.timeout-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.timeout-input {
  width: 80px;
  padding: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
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