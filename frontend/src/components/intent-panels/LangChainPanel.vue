<template>
  <div class="langchain-panel">
    <div class="panel-header">
      <h2>LangChain 集成测试</h2>
      <div class="health-status" :class="healthStatus">
        {{ healthText }}
      </div>
    </div>

    <div class="tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.name }}
      </button>
    </div>

    <div class="tab-content">
      <!-- 聊天测试 -->
      <div v-if="activeTab === 'chat'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="chatInput" 
            type="text" 
            placeholder="输入消息..."
            @keyup.enter="sendChatMessage"
          />
          <button @click="sendChatMessage" :disabled="isLoading">发送</button>
        </div>
        <div class="chat-history">
          <div 
            v-for="(msg, idx) in chatHistory" 
            :key="idx"
            :class="['message', msg.role]"
          >
            <span class="role">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
            <span class="content">{{ msg.content }}</span>
          </div>
        </div>
      </div>

      <!-- 意图识别 -->
      <div v-if="activeTab === 'intent'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="intentInput" 
            type="text" 
            placeholder="输入文本识别意图..."
            @keyup.enter="detectIntentHandler"
          />
          <button @click="detectIntentHandler" :disabled="isLoading">识别</button>
        </div>
        <div v-if="intentResult" class="result-card">
          <h3>识别结果</h3>
          <pre>{{ JSON.stringify(intentResult, null, 2) }}</pre>
        </div>
      </div>

      <!-- 表单识别 -->
      <div v-if="activeTab === 'form'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="formInput" 
            type="text" 
            placeholder="输入文本识别表单..."
            @keyup.enter="identifyFormHandler"
          />
          <button @click="identifyFormHandler" :disabled="isLoading">识别</button>
        </div>
        <div v-if="formResult" class="result-card">
          <h3>表单识别结果</h3>
          <pre>{{ JSON.stringify(formResult, null, 2) }}</pre>
        </div>
      </div>

      <!-- 字段提取 -->
      <div v-if="activeTab === 'extract'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="extractInput" 
            type="text" 
            placeholder="输入文本..."
          />
          <input 
            v-model="formCodeInput" 
            type="text" 
            placeholder="表单编码 (如: tariff_filing_publicity)"
          />
          <button @click="extractFieldsHandler" :disabled="isLoading">提取</button>
        </div>
        <div v-if="extractResult" class="result-card">
          <h3>字段提取结果</h3>
          <pre>{{ JSON.stringify(extractResult, null, 2) }}</pre>
        </div>
      </div>

      <!-- 表单代理 -->
      <div v-if="activeTab === 'agent'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="agentInput" 
            type="text" 
            placeholder="输入表单相关请求..."
            @keyup.enter="executeFormAgentHandler"
          />
          <button @click="executeFormAgentHandler" :disabled="isLoading">执行</button>
        </div>
        <div v-if="agentResult" class="result-card">
          <h3>表单代理结果</h3>
          <pre>{{ JSON.stringify(agentResult, null, 2) }}</pre>
        </div>
      </div>

      <!-- 工作流 -->
      <div v-if="activeTab === 'workflow'" class="tab-pane">
        <div class="input-section">
          <input 
            v-model="workflowInput" 
            type="text" 
            placeholder="输入表单请求..."
            @keyup.enter="executeWorkflowHandler"
          />
          <button @click="executeWorkflowHandler" :disabled="isLoading">执行工作流</button>
        </div>
        <div v-if="workflowResult" class="result-card">
          <h3>工作流执行结果</h3>
          <pre>{{ JSON.stringify(workflowResult, null, 2) }}</pre>
        </div>
      </div>
    </div>

    <div v-if="error" class="error-message">
      ⚠️ {{ error }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useLangChain } from '../../composables/useLangChain.js';

const { 
  isLoading, 
  error, 
  chatHistory,
  sendChat, 
  detectIntent, 
  identifyForm, 
  getFields, 
  executeFormAgent, 
  executeFormWorkflow,
  checkHealth 
} = useLangChain();

const tabs = [
  { id: 'chat', name: '聊天' },
  { id: 'intent', name: '意图识别' },
  { id: 'form', name: '表单识别' },
  { id: 'extract', name: '字段提取' },
  { id: 'agent', name: '表单代理' },
  { id: 'workflow', name: '工作流' }
];

const activeTab = ref('chat');
const healthStatus = ref('unknown');

const healthText = computed(() => {
  const statusMap = {
    healthy: '✓ 服务正常',
    unhealthy: '✗ 服务异常',
    unknown: '? 检查中...'
  };
  return statusMap[healthStatus.value];
});

// 聊天
const chatInput = ref('');

const sendChatMessage = async () => {
  if (!chatInput.value.trim()) return;
  console.log('[LangChainPanel] Before sendChat - chatHistory:', chatHistory.value);
  const result = await sendChat(chatInput.value);
  console.log('[LangChainPanel] After sendChat - chatHistory:', chatHistory.value);
  chatInput.value = '';
};

// 意图识别
const intentInput = ref('');
const intentResult = ref(null);

const detectIntentHandler = async () => {
  if (!intentInput.value.trim()) return;
  intentResult.value = await detectIntent(intentInput.value);
};

// 表单识别
const formInput = ref('');
const formResult = ref(null);

const identifyFormHandler = async () => {
  if (!formInput.value.trim()) return;
  formResult.value = await identifyForm(formInput.value);
};

// 字段提取
const extractInput = ref('');
const formCodeInput = ref('tariff_filing_publicity');
const extractResult = ref(null);

const extractFieldsHandler = async () => {
  if (!extractInput.value.trim() || !formCodeInput.value.trim()) return;
  extractResult.value = await getFields(extractInput.value, formCodeInput.value);
};

// 表单代理
const agentInput = ref('');
const agentResult = ref(null);

const executeFormAgentHandler = async () => {
  if (!agentInput.value.trim()) return;
  agentResult.value = await executeFormAgent(agentInput.value);
};

// 工作流
const workflowInput = ref('');
const workflowResult = ref(null);

const executeWorkflowHandler = async () => {
  if (!workflowInput.value.trim()) return;
  workflowResult.value = await executeFormWorkflow(workflowInput.value);
};

onMounted(async () => {
  const result = await checkHealth();
  healthStatus.value = result.success ? 'healthy' : 'unhealthy';
});
</script>

<style scoped>
.langchain-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  box-sizing: border-box;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
}

.health-status {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
}

.health-status.healthy {
  background-color: #d4edda;
  color: #155724;
}

.health-status.unhealthy {
  background-color: #f8d7da;
  color: #721c24;
}

.health-status.unknown {
  background-color: #fff3cd;
  color: #856404;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  overflow-x: auto;
}

.tab-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  background-color: #e9ecef;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.tab-btn.active {
  background-color: #007bff;
  color: white;
}

.tab-content {
  flex: 1;
  overflow-y: auto;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.input-section {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.input-section input {
  flex: 1;
  min-width: 150px;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.input-section button {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background-color: #007bff;
  color: white;
  cursor: pointer;
}

.input-section button:disabled {
  background-color: #6c757d;
  cursor: not-allowed;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 8px;
}

.message {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.message.user .role {
  color: #007bff;
  font-weight: bold;
}

.message.assistant .role {
  color: #28a745;
  font-weight: bold;
}

.result-card {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 12px;
}

.result-card h3 {
  margin: 0 0 8px 0;
  font-size: 14px;
}

.result-card pre {
  margin: 0;
  font-size: 12px;
  overflow-x: auto;
  white-space: pre-wrap;
}

.error-message {
  margin-top: 12px;
  padding: 8px 12px;
  background-color: #f8d7da;
  color: #721c24;
  border-radius: 4px;
  font-size: 12px;
}
</style>