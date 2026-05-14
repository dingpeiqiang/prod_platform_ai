import { ref, computed } from 'vue';
import { chat, recognizeIntent, recognizeForm, extractFields, validateForm, runFormAgent, runTaskAgent, runFormWorkflow, healthCheck } from '../services/langchainApi.js';

const isLoading = ref(false);
const error = ref(null);
const chatHistory = ref([]);
const lastResponse = ref(null);

const clearError = () => {
  error.value = null;
};

const handleApiError = (result, defaultMsg = '操作失败') => {
  if (!result.success) {
    error.value = result.error?.message || result.error || defaultMsg;
    console.error('[useLangChain] API Error:', error.value);
    return false;
  }
  return true;
};

const sendChat = async (message, options = {}) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await chat(message, options);
    if (!handleApiError(result, '聊天失败'))
      return null;
    lastResponse.value = result;
    chatHistory.value = [
      ...chatHistory.value,
      { role: 'user', content: message },
      { role: 'assistant', content: result.response }
    ];
    console.log('[useLangChain] chatHistory updated:', chatHistory.value);
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const detectIntent = async (userInput) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await recognizeIntent(userInput);
    if (!handleApiError(result, '意图识别失败'))
      return null;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const identifyForm = async (userInput) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await recognizeForm(userInput);
    if (!handleApiError(result, '表单识别失败'))
      return null;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const getFields = async (userInput, formCode) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await extractFields(userInput, formCode);
    if (!handleApiError(result, '字段提取失败'))
      return null;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const checkForm = async (formData, formCode) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await validateForm(formData, formCode);
    if (!handleApiError(result, '表单验证失败'))
      return null;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const executeFormAgent = async (userInput, options = {}) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await runFormAgent(userInput, options);
    if (!handleApiError(result, '表单代理执行失败'))
      return null;
    lastResponse.value = result;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const executeTaskAgent = async (taskType, taskData) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await runTaskAgent(taskType, taskData);
    if (!handleApiError(result, '任务代理执行失败'))
      return null;
    lastResponse.value = result;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const executeFormWorkflow = async (userInput, options = {}) => {
  isLoading.value = true;
  error.value = null;
  try {
    const result = await runFormWorkflow(userInput, options);
    if (!handleApiError(result, '表单工作流执行失败'))
      return null;
    lastResponse.value = result;
    return result;
  }
  finally {
    isLoading.value = false;
  }
};

const checkHealth = async () => {
  try {
    const result = await healthCheck();
    return result;
  }
  catch (e) {
    console.error('[useLangChain] Health check failed:', e);
    return { success: false, error: e.message };
  }
};

const hasError = computed(() => !!error.value);

export function useLangChain() {
  return {
    isLoading,
    error,
    hasError,
    lastResponse,
    chatHistory,
    clearError,
    sendChat,
    detectIntent,
    identifyForm,
    getFields,
    checkForm,
    executeFormAgent,
    executeTaskAgent,
    executeFormWorkflow,
    checkHealth
  };
}
