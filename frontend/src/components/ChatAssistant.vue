<template>
  <div class="chat-layout">
    <div class="chat-main">
      <div class="chat-topbar">
        <span class="session-name">{{ sessionTitle }}</span>
        <div class="topbar-actions">
          <button
            class="icon-btn product-list-btn"
            title="商品列表"
            @click="productConfig.showProductListPanel.value = true"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="6" x2="21" y2="6"/>
              <line x1="8" y1="12" x2="21" y2="12"/>
              <line x1="8" y1="18" x2="21" y2="18"/>
              <line x1="3" y1="6" x2="3.01" y2="6"/>
              <line x1="3" y1="12" x2="3.01" y2="12"/>
              <line x1="3" y1="18" x2="3.01" y2="18"/>
            </svg>
            <span v-if="productConfig.products.value.length" class="badge">{{ productConfig.products.value.length }}</span>
          </button>
          <button
            class="icon-btn audit-btn"
            title="智能稽核"
            :disabled="!canRunAudit"
            @click="handleRunAudit"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
          </button>
          <button class="icon-btn" title="清空记录" @click="clearHistory">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4h6v2"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 欢迎卡片 -->
      <WelcomeCards v-if="showWelcome" @card-click="handleWelcomeCardClick" />

      <ChatMessageList
        ref="messageListRef"
        :messages="messages"
        @form-card-click="handleFormCardClick"
        @intent-action="handleIntentEvent"
        @query-result-click="handleQueryResultClick"
      />

      <ChatInput
        ref="inputRef"
        v-model="inputText"
        :disabled="isStreaming"
        :current-skill="currentSkill"
        @send="sendMessage"
        @stop="stopStream"
        @quick-action="sendSuggestion"
        @remove-skill="currentSkill = ''"
      />
    </div>

    <FormPanel
      :formSchema="activeFormCard?.formSchema"
      :formId="activeFormCard?.formId"
      :formSubmitted="activeFormCard?.status === 'submitted'"
      :formCancelled="activeFormCard?.status === 'cancelled'"
      @submit="handleFormSubmit"
      @cancel="handleFormCancel"
      @field-change="handleFormFieldChangeWithSync"
      @confirm-submit="(data) => handleConfirmSubmit(data).then(msg => msg && doSendMessageAfterHome(msg))"
    />

    <ProductListPanel
      v-model="productConfig.showProductListPanel.value"
      :products="productConfig.products.value"
      :current-product-id="productConfig.currentProductId.value"
      @select="handleProductSelect"
      @copy="handleProductCopy"
      @edit="handleProductSelect"
      @delete="handleProductDelete"
    />

    <AuditPanel
      v-model="productConfig.showAuditPanel.value"
      :product-name="productConfig.currentProduct.value?.name || ''"
      :results="productConfig.auditResults.value"
      :phase="productConfig.auditPhase.value"
      :has-error="productConfig.auditResults.value.some(r => r.type === 'error')"
      @close="handleAuditClose"
      @audit-complete="handleAuditComplete"
    />
  </div>
</template>

<script setup>import { ref, computed, watch, onMounted, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import ChatMessageList from './ChatMessageList.vue';
import ChatInput from './ChatInput.vue';
import FormPanel from './FormPanel.vue';
import WelcomeCards from './WelcomeCards.vue';
import ProductListPanel from './ProductListPanel.vue';
import AuditPanel from './AuditPanel.vue';
import { genId, formatTime, getFormStatusText } from '../utils/chatUtils.js';
import { createSession as apiCreateSession, saveMessage, loadMessages as apiLoadMessages } from '../services/chatApi.js';
import { registerEventHandler, registerPostProcessor } from '../composables/useIntentRegistry.js';
import { useChatStream } from '../composables/useChatStream.js';
import { useFormHandling } from '../composables/useFormHandling.js';
import { useIntentHandlers } from '../composables/useIntentHandlers.js';
import { useProductConfig } from '../composables/useProductConfig.js';
import { mockProducts } from '../data/productMockData.js';
const props = defineProps({
 sessionId: { type: String, required: true },
 dbSessionId: { type: String, default: '' },
 userId: { type: String, default: '' },
 sessionTitle: { type: String, default: '新对话' },
 modelConfig: { type: Object, default: null }
});
const emit = defineEmits(['title-update', 'session-init', 'create-session-from-home']);
const messages = ref([]);
const inputText = ref('');
const currentDbSessionId = ref('');
const currentSkill = ref('');
let isCreatingFromHome = false;
const dbLoaded = ref(false);
const messageListRef = ref(null);
const inputRef = ref(null);
const { isStreaming, stopStream, sendStreamMessage } = useChatStream(messages, currentDbSessionId);
const { currentFormId, currentFormSchema, currentFormSubmitted, activeFormCard, activeFormMsgId, pendingConfirmForm, generateForm, updateFormFields, handleConfirmSubmit, checkUserConfirmation, handleDoConfirmSubmit, handleCancelSubmit, handleFormSubmit, handleFormCancel, handleConfirmSubmitForActiveForm, handleFormFieldChange, updateFormCardStatus } = useFormHandling(messages, currentDbSessionId);
const { handleIntentEvent: handleIntentAction } = useIntentHandlers(messages, currentDbSessionId, emit);
const productConfig = useProductConfig();
const canRunAudit = computed(() => !!(activeFormCard.value && productConfig.currentProduct.value));
const auditTimers = ref([]);
const suggestions = [
 { key: 'help', icon: '💬', text: '我能为你做什么？' },
];
const pendingWorkflowResume = ref(null);
const storageKey = computed(() => `chat_session_${props.sessionId}`);
const formStorageKey = computed(() => `chat_form_${props.sessionId}`);
const saveMessages = () => {
 try {
 const toSave = messages.value.filter(m => m.done !== false);
 localStorage.setItem(storageKey.value, JSON.stringify(toSave));
 }
 catch { }
};
const saveFormState = () => {
 try {
 localStorage.setItem(formStorageKey.value, JSON.stringify({
 formId: currentFormId.value,
 formSchema: currentFormSchema.value
 }));
 }
 catch { }
};
const loadFormState = () => {
 try {
 const raw = localStorage.getItem(formStorageKey.value);
 if (raw) {
 const state = JSON.parse(raw);
 currentFormId.value = state.formId || '';
 currentFormSchema.value = state.formSchema || null;
 }
 else {
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 }
 }
 catch {
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 }
};
watch(messages, saveMessages, { deep: true });
watch([currentFormId, currentFormSchema], () => {
 saveFormState();
}, { deep: true });
const ensureDbSession = async (localSessionId) => {
 if (currentDbSessionId.value)
 return currentDbSessionId.value;
 if (props.dbSessionId) {
 currentDbSessionId.value = props.dbSessionId;
 return currentDbSessionId.value;
 }
 try {
 const result = await apiCreateSession(props.userId || null, '新对话');
 if (result.success && result.session_id) {
 currentDbSessionId.value = result.session_id;
 emit('session-init', { localId: localSessionId, dbSessionId: result.session_id });
 }
 }
 catch (e) {
 console.warn('[ChatAssistant] 创建 DB 会话失败:', e);
 }
 return currentDbSessionId.value;
};
watch(() => props.sessionId, async (newSessionId, oldSessionId) => {
 if (isStreaming.value && oldSessionId) {
 if (stopStream)
 stopStream();
 isStreaming.value = false;
 }
 if (isCreatingFromHome) {
 isCreatingFromHome = false;
 currentDbSessionId.value = props.dbSessionId || '';
 currentFormSubmitted.value = false;
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 loadFormState();
 return;
 }
 messages.value = [];
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 // 清空产品配置 mock 状态
 productConfig.products.value = [];
 productConfig.currentProductId.value = null;
 clearAuditTimers();
 productConfig.showProductListPanel.value = false;
 productConfig.showAuditPanel.value = false;
 productConfig.auditPhase.value = 'idle';
 productConfig.auditResults.value = [];
 if (!newSessionId) {
 currentDbSessionId.value = '';
 currentFormSubmitted.value = false;
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 loadFormState();
 return;
 }
 if (props.dbSessionId) {
 currentDbSessionId.value = props.dbSessionId;
 }
 else {
 currentDbSessionId.value = '';
 }
 if (!currentDbSessionId.value) {
 const result = await apiCreateSession(props.userId || null, '新对话');
 if (result.session_id) {
 currentDbSessionId.value = result.session_id;
 emit('session-init', { localId: props.sessionId, dbSessionId: result.session_id });
 }
 }
 if (currentDbSessionId.value) {
 try {
 const dbMsgs = await apiLoadMessages(currentDbSessionId.value);
 // 如果消息列表已经有消息（从首页发送过来），则不覆盖
 // 否则使用数据库中的消息
 if (messages.value.length === 0) {
 messages.value = dbMsgs;
 } else {
 // 如果有数据库消息但本地已有消息，说明是新会话，不需要合并
 console.log('[ChatAssistant] 消息列表已存在，跳过数据库加载');
 }
 let lastFillingCard = null;
 for (let i = dbMsgs.length - 1; i >= 0; i--) {
 const msg = dbMsgs[i];
 if (msg.formCard && msg.formCard.status === 'filling' && msg.formCard.formSchema) {
 lastFillingCard = msg.formCard;
 break;
 }
 if (!lastFillingCard && msg.formId !== undefined && msg.formSchema !== null) {
 lastFillingCard = {
 msgId: msg.id,
 formId: msg.formId,
 formSchema: msg.formSchema,
 formName: msg.formSchema.formName || '',
 formCode: msg.formSchema.formCode || '',
 status: msg.formSubmitted ? 'submitted' : 'filling',
 fieldCount: msg.formSchema.fields?.length || 0,
 createdAt: msg.createdAt || new Date().toISOString(),
 formData: {}
 };
 }
 }
 if (lastFillingCard) {
 activeFormCard.value = lastFillingCard;
 activeFormMsgId.value = lastFillingCard.msgId || '';
 currentFormId.value = lastFillingCard.formId;
 currentFormSchema.value = lastFillingCard.formSchema;
 currentFormSubmitted.value = lastFillingCard.status === 'submitted';
 }
 else {
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 currentFormSubmitted.value = false;
 }
 }
 catch (e) {
 console.warn('[ChatAssistant] 加载消息失败:', e);
 messages.value = [];
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 currentFormSubmitted.value = false;
 }
 }
 else {
 loadFormState();
 }
});
watch(() => props.dbSessionId, async (newId) => {
 if (!newId)
 return;
 if (currentDbSessionId.value === newId)
 return;
 currentDbSessionId.value = newId;
 messages.value = [];
 try {
 const dbMsgs = await apiLoadMessages(newId);
 messages.value = dbMsgs;
 let lastFormId = '';
 let lastFormSchema = null;
 for (let i = dbMsgs.length - 1; i >= 0; i--) {
 const msg = dbMsgs[i];
 if (msg.formId !== undefined && msg.formSchema !== null) {
 lastFormId = msg.formId;
 lastFormSchema = msg.formSchema;
 break;
 }
 }
 if (lastFormId || lastFormSchema) {
 currentFormId.value = lastFormId;
 currentFormSchema.value = lastFormSchema;
 }
 else {
 loadFormState();
 }
 }
 catch (e) {
 console.warn('[ChatAssistant] 加载消息失败:', e);
 messages.value = [];
 loadFormState();
 }
 scrollToBottom();
 nextTick(() => inputRef.value?.focus());
});
const focusFormPanel = () => {
 const formPanel = document.querySelector('.form-panel');
 if (formPanel) {
 formPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
 const collapseBtn = formPanel.querySelector('.collapse-btn');
 if (collapseBtn) {
 const isCollapsed = formPanel.classList.contains('collapsed');
 if (isCollapsed)
 collapseBtn.click();
 }
 }
};
const handleFormCardClick = (msg) => {
 if (msg.formCard && msg.formCard.formSchema) {
 if (activeFormCard.value?.msgId === msg.formCard.msgId) {
 focusFormPanel();
 return;
 }
 activeFormCard.value = msg.formCard;
 activeFormMsgId.value = msg.id;
 currentFormId.value = msg.formCard.formId;
 currentFormSchema.value = msg.formCard.formSchema;
 currentFormSubmitted.value = msg.formCard.status === 'submitted';
 focusFormPanel();
 ElMessage({ message: `已切换到「${msg.formCard.formName}」`, duration: 1500, plain: true });
 }
};
const clearHistory = async () => {
 try {
 await ElMessageBox.confirm('确定清空本次对话记录吗？', '清空记录', {
 confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning'
 });
 messages.value = [];
 currentFormId.value = '';
 currentFormSchema.value = null;
 currentFormSubmitted.value = false;
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 localStorage.removeItem(storageKey.value);
 localStorage.removeItem(formStorageKey.value);
 ElMessage({ message: '已清空', type: 'success', duration: 1500, plain: true });
 }
 catch { }
};
const sendSuggestion = (text) => {
 inputText.value = text;
 sendMessage();
};
const showWelcome = computed(() => messages.value.length === 0);
const handleWelcomeCardClick = (type) => {
 currentSkill.value = type;
 let text = '';
 switch (type) {
 case 'query':
 text = '我想查询历史商品';
 break;
 case 'file':
 text = '我想导入配置方案';
 break;
 case 'chat':
 text = '我要配置一个大学生套餐';
 break;
 }
 inputText.value = text;
 sendMessage();
};
const sendMessage = async (messageData) => {
 const text = typeof messageData === 'string' ? messageData : (messageData?.text || inputText.value.trim());
 const attachments = typeof messageData === 'object' ? messageData.attachments : [];
 console.log('[ChatAssistant] sendMessage called:', { text, attachments: attachments?.length || 0, sessionId: props.sessionId, isStreaming: isStreaming.value, messagesLength: messages.value.length });
 if (!text && (!attachments || attachments.length === 0) || isStreaming.value)
 return;
 // 产品配置场景拦截（mock MVP）
 const scenario = productConfig.detectScenario(text);
 if (scenario) {
 inputText.value = '';
 currentSkill.value = '';
 await handleProductScenario(scenario, text);
 return;
 }
 if (attachments && attachments.length > 0) {
 const doc = attachments.find(a => /\.(docx|pdf|xlsx|doc)$/i.test(a.name || ''));
 if (doc) {
 inputText.value = '';
 currentSkill.value = '';
 await handleFileParseScenario(doc);
 return;
 }
 }
 if (!props.sessionId) {
 isCreatingFromHome = true;
 messages.value.push({ id: genId(), role: 'user', content: text, attachments, done: true });
 emit('create-session-from-home', { text, attachments });
 return;
 }
 if (pendingWorkflowResume.value) {
 const resumeData = { ...pendingWorkflowResume.value };
 resumeData.form_data = {
 ...resumeData.form_data,
 user_input: text
 };
 pendingWorkflowResume.value = null;
 await doSendMessageAfterHome(text, { workflowResume: resumeData, attachments });
 return;
 }
 await doSendMessage(text, attachments);
};
const sendMessageAfterSessionCreated = async (text, sessionId, attachments = []) => {
 isCreatingFromHome = true;
 await new Promise(resolve => setTimeout(resolve, 50));
 await doSendMessageAfterHome(text, { attachments });
};
const doSendMessage = async (text, attachments = []) => {
 if (pendingConfirmForm.value && checkUserConfirmation(text)) {
 await handleDoConfirmSubmit();
 return;
 }
 const hasFormToHandle = pendingConfirmForm.value || (activeFormCard.value?.status === 'filling');
 if (hasFormToHandle) {
 const lowerText = text.toLowerCase();
 const hasExplicitCancel = lowerText.includes('取消') || lowerText.includes('算了') || lowerText.includes('放弃');
 const hasExplicitSubmit = lowerText.includes('完成') || lowerText.includes('提交') || lowerText.includes('确认');
 if (pendingConfirmForm.value) {
 if (hasExplicitCancel) {
 messages.value.push({ id: genId(), role: 'user', content: text, attachments, done: true });
 scrollToBottom();
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, { role: 'user', content: text, attachments }).catch(() => { });
 }
 await handleCancelSubmit();
 return;
 }
 if (checkUserConfirmation(text)) {
 await handleDoConfirmSubmit();
 return;
 }
 await doSendMessageAfterHome(text, { attachments });
 return;
 }
 if (activeFormCard.value?.status === 'filling') {
 if (hasExplicitCancel) {
 messages.value.push({ id: genId(), role: 'user', content: text, attachments, done: true });
 scrollToBottom();
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, { role: 'user', content: text, attachments }).catch(() => { });
 }
 await handleFormCancel();
 return;
 }
 if (hasExplicitSubmit) {
 handleConfirmSubmitForActiveForm();
 return;
 }
 await doSendMessageAfterHome(text, { attachments });
 return;
 }
 }
 await doSendMessageAfterHome(text, { attachments });
};
const doSendMessageAfterHome = async (text, { skipUserPush = false, formCode = null, formData = null, workflowResume = null, attachments = [] } = {}) => {
 console.log('[ChatAssistant] doSendMessageAfterHome called:', { text, skipUserPush, sessionId: props.sessionId, currentDbSessionId: currentDbSessionId.value, attachments: attachments?.length || 0 });
 await ensureDbSession(props.sessionId);
 console.log('[ChatAssistant] after ensureDbSession:', { currentDbSessionId: currentDbSessionId.value });
 if (!skipUserPush) {
 console.log('[ChatAssistant] pushing user message:', { text });
 messages.value.push({ id: genId(), role: 'user', content: text, attachments, done: true });
 console.log('[ChatAssistant] messages after push:', messages.value.length);
 scrollToBottom();
 }
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, {
 role: 'user',
 content: text,
 attachments
 });
 }
 if (messages.value.filter(m => m.role === 'user').length === 1) {
 const titleText = text?.slice(0, 20) || (attachments?.length ? `发送了${attachments.length}个文件` : '新对话');
 emit('title-update', props.sessionId, titleText);
 }
 scrollToBottom();
 await sendStreamMessage(text, { formCode, formData, modelConfig: props.modelConfig, workflowResume });
};
const scrollToBottom = (smooth = false) => {
 if (messageListRef.value) {
 messageListRef.value.scrollToBottom(smooth);
 }
};
const handleIntentEvent = (event) => {
 handleIntentAction(event, inputText, sendMessage, currentFormId, currentFormSchema);
};
registerEventHandler('delete_form', (data, msg) => {
 if (!msg._intentData)
 msg._intentData = {};
 const d = data.content || {};
 msg._intentData['delete_form'] = {
 ...d,
 showVersionHistory: true,
 versionList: d.versionList || [],
 loadingVersions: false,
 rollingBack: false,
 rollbackResult: null
 };
 scrollToBottom();
}, { panel: null });
registerEventHandler('manage_history', (data, msg) => {
 const historyPayload = data.content || data.data || {};
 if (!historyPayload.action) {
 if (historyPayload.qualityScore !== undefined)
 historyPayload.action = 'analyze';
 else if (historyPayload.generatedCount !== undefined)
 historyPayload.action = 'generate';
 else if (historyPayload.importedCount !== undefined)
 historyPayload.action = 'import';
 else if (historyPayload.downloadUrl !== undefined)
 historyPayload.action = 'export';
 else if (historyPayload.totalRecords !== undefined)
 historyPayload.action = 'status';
 else
 historyPayload.action = 'status';
 }
 if (!msg._intentData)
 msg._intentData = {};
 if (historyPayload.action === 'export') {
 msg._historyRaw = historyPayload;
 }
 else {
 msg._intentData['manage_history'] = {
 ...historyPayload,
 importing: false,
 importResult: null
 };
 }
 scrollToBottom();
}, { panel: null });
const _handleValidationResult = (data, msg) => {
 if (!msg._intentData)
 msg._intentData = {};
 msg._intentData[data.type] = {
 formCode: data.form_code || '',
 passed: data.type === 'validation_pass',
 errors: data.errors || [],
 warnings: data.warnings || [],
 step: data.step || '',
 rule_engine_passed: data.rule_engine_passed || false,
 validationTable: data.validationTable || null
 };
 scrollToBottom();
};
registerEventHandler('validation_fail', _handleValidationResult, { panel: 'ValidationResultPanel' });
registerEventHandler('validation_pass', _handleValidationResult, { panel: 'ValidationResultPanel' });
registerPostProcessor('form_update', async (msg, intentData) => {
 await updateFormFields(intentData);
});
registerPostProcessor('delete_form', (msg, intentData) => {
 msg.content = msg.streamText || '';
 const deletedCode = intentData?.formCode || msg.deleteFormData?.formCode;
 if (deletedCode && currentFormSchema.value?.formCode === deletedCode) {
 currentFormId.value = null;
 currentFormSchema.value = null;
 }
});
registerPostProcessor('manage_history', (msg) => {
 const hd = msg._historyRaw || msg.historyData;
 if (hd?.action === 'export' && hd.downloadUrl) {
 msg.content = `文件已准备好导出：${hd.filename}（共${hd.recordCount}条记录）\n点击下载：${hd.downloadUrl}`;
 }
 else {
 msg.content = msg.streamText || '';
 }
});
registerPostProcessor('form', async (msg, intentData) => {
 const hasActiveForm = activeFormCard.value?.status === 'filling';
 const hasPendingConfirm = !!pendingConfirmForm.value;
 const validationPass = msg._intentData?.['validation_pass'];
 if (hasPendingConfirm && validationPass) {
 msg.streamText = (msg.streamText || '') + '\n\n✅ 校验通过，正在提交表单...';
 msg.content = msg.streamText;
 delete msg._intentData['validation_pass'];
 await new Promise(resolve => setTimeout(resolve, 300));
 await handleDoConfirmSubmit();
 return;
 }
 if (hasPendingConfirm) {
 msg.content = msg.streamText || '';
 return;
 }
 if (hasActiveForm) {
 const formName = activeFormCard.value?.formName || '当前表单';
 const warnMsg = {
 id: genId(), role: 'assistant',
 content: `⚠️ 检测到你有一个未完成的「${formName}」，请先完成或关闭后再发起新任务。\n\n你可以说「完成」或「提交」来完成当前表单，或者「取消」放弃当前表单。`,
 done: true, type: 'chat'
 };
 messages.value.push(warnMsg);
 scrollToBottom();
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, {
 role: 'assistant',
 content: warnMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 return;
 }
 if (intentData?.formCode) {
 await generateForm({
 formCode: intentData.formCode,
 extractedFields: intentData.extractedFields || {},
 fieldRecommendations: intentData.fieldRecommendations
 });
 }
 else {
 msg.content = msg.streamText;
 }
});
registerPostProcessor('validate', async (msg, intentData) => {
 const validationPass = msg._intentData?.['validation_pass'];
 const validationFail = msg._intentData?.['validation_fail'];
 if (validationFail) {
 pendingConfirmForm.value = null;
 msg.content = msg.streamText || '';
 }
 else if (validationPass) {
 if (pendingConfirmForm.value) {
 const schema = pendingConfirmForm.value.schema;
 let previewLines = [];
 if (schema && schema.fields) {
 for (const field of schema.fields) {
 const val = pendingConfirmForm.value.data[field.fieldCode];
 if (val !== undefined && val !== null && val !== '') {
 const displayVal = Array.isArray(val) ? val.join(', ') : String(val);
 previewLines.push(`- **${field.fieldName}**: ${displayVal}`);
 }
 }
 }
 const submitSummary = previewLines.length > 0 ? `\n\n提交内容：\n${previewLines.join('\n')}` : '';
 msg.streamText = (msg.streamText || '') + `\n\n---\n\n✅ 校验通过！正在提交表单...${submitSummary}`;
 msg.content = msg.streamText;
 await new Promise(resolve => setTimeout(resolve, 300));
 await handleDoConfirmSubmit();
 }
 else {
 msg.content = msg.streamText || '';
 }
 }
 else {
 if (pendingConfirmForm.value) {
 msg.streamText = (msg.streamText || '') + '\n\n---\n\n校验完成，正在提交表单...';
 msg.content = msg.streamText;
 await new Promise(resolve => setTimeout(resolve, 300));
 await handleDoConfirmSubmit();
 }
 else {
 msg.content = msg.streamText || '';
 }
 }
});
registerPostProcessor('workflow', async (msg, intentData) => {
 const workflowWaiting = intentData?.workflow_waiting;
 if (workflowWaiting) {
 msg.content = workflowWaiting.message || '请提供以下信息';
 pendingWorkflowResume.value = {
 execution_id: workflowWaiting.execution_id,
 form_data: {}
 };
 }
 else {
 msg.content = msg.streamText || '';
 }
});
// ========== 产品配置场景处理（mock MVP） ==========
const handleFormFieldChangeWithSync = (fieldCode, value) => {
 handleFormFieldChange(fieldCode, value);
 productConfig.updateFormField(fieldCode, value);
};
const pushProductMessages = async (msgs) => {
 for (const m of msgs) {
 messages.value.push(m);
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, {
 role: m.role,
 content: m.content,
 reasoning: [],
 formCard: m.formCard,
 queryResults: m.queryResults
 }).catch(() => { });
 }
 }
 scrollToBottom();
};
const applyFormCard = (formCard) => {
 if (!formCard) return;
 activeFormCard.value = formCard;
 activeFormMsgId.value = formCard.msgId;
 currentFormId.value = formCard.formId;
 currentFormSchema.value = formCard.formSchema;
 currentFormSubmitted.value = false;
};
const handleProductScenario = async (scenario, text) => {
 if (!props.sessionId) {
 isCreatingFromHome = true;
 messages.value.push({ id: genId(), role: 'user', content: text, done: true });
 emit('create-session-from-home', { text, attachments: [] });
 return;
 }
 await ensureDbSession(props.sessionId);
 messages.value.push({ id: genId(), role: 'user', content: text, done: true });
 scrollToBottom();
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, { role: 'user', content: text }).catch(() => { });
 }
 if (messages.value.filter(m => m.role === 'user').length === 1) {
 emit('title-update', props.sessionId, text?.slice(0, 20) || '产品配置');
 }
 if (scenario === 'query') {
 const keyword = text.replace(/查询|智查|历史商品/g, '').trim() || '动感地带';
 const msgs = productConfig.simulateQuery(keyword);
 await pushProductMessages(msgs);
 return;
 }
 if (scenario === 'chat-generate') {
 const result = productConfig.generateProductFromChat(text);
 await pushProductMessages(result.messages);
 if (result.formCard) applyFormCard(result.formCard);
 return;
 }
 // file-parse 场景由 sendMessage 中单独处理，此处兜底
 await doSendMessage(text, []);
};
const handleFileParseScenario = async (doc) => {
 if (!props.sessionId) {
 isCreatingFromHome = true;
 messages.value.push({ id: genId(), role: 'user', content: `上传了文件：${doc.name}`, done: true });
 emit('create-session-from-home', { text: `上传了文件：${doc.name}`, attachments: [doc] });
 return;
 }
 await ensureDbSession(props.sessionId);
 messages.value.push({ id: genId(), role: 'user', content: `上传了文件：${doc.name}`, done: true });
 scrollToBottom();
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, { role: 'user', content: `上传了文件：${doc.name}` }).catch(() => { });
 }
 const result = productConfig.simulateFileParse(doc.name, doc.size || 0);
 await pushProductMessages(result.messages);
 if (result.formCard) applyFormCard(result.formCard);
};
const handleQueryResultClick = async (product) => {
 const index = mockProducts.findIndex(p => p.id === product.id);
 if (index < 0) {
 ElMessage.warning('未找到商品模板');
 return;
 }
 const result = productConfig.prepareProduct(index);
 if (!result) return;
 await pushProductMessages(result.messages);
 if (result.formCard) applyFormCard(result.formCard);
 ElMessage({ message: `已复制「${product.name}」配置`, type: 'success', duration: 1500, plain: true });
};
const handleProductSelect = (id) => {
 const formCard = productConfig.selectProduct(id);
 if (formCard) applyFormCard(formCard);
 productConfig.showProductListPanel.value = false;
 ElMessage({ message: `已切换到「${productConfig.currentProduct.value?.name || ''}」`, duration: 1500, plain: true });
};
const handleProductCopy = (id) => {
 const newProduct = productConfig.copyProduct(id);
 if (newProduct) {
 ElMessage({ message: `已复制为「${newProduct.name}」`, type: 'success', duration: 1500, plain: true });
 }
};
const handleProductDelete = (id) => {
 const product = productConfig.products.value.find(p => p.id === id);
 if (!product) return;
 ElMessageBox.confirm(`确定要删除商品「${product.name}」吗？`, '删除商品', {
 confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
 }).then(() => {
 const formCard = productConfig.deleteProduct(id);
 if (formCard) applyFormCard(formCard);
 else if (productConfig.currentProductId.value === null) {
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 currentFormId.value = '';
 currentFormSchema.value = null;
 }
 ElMessage({ message: '已删除', type: 'success', duration: 1500, plain: true });
 }).catch(() => { });
};
const clearAuditTimers = () => {
 auditTimers.value.forEach(t => clearTimeout(t));
 auditTimers.value = [];
};
const handleRunAudit = () => {
 if (!canRunAudit.value) return;
 productConfig.saveDraft();
 productConfig.showAuditPanel.value = true;
 productConfig.auditPhase.value = 'progress';
 clearAuditTimers();
 auditTimers.value.push(setTimeout(() => { productConfig.auditPhase.value = 'results'; }, 6000));
};
const handleAuditClose = () => {
 productConfig.showAuditPanel.value = false;
 clearAuditTimers();
};
const handleAuditComplete = async () => {
 const productName = productConfig.currentProduct.value?.name || '配置';
 const successMsg = {
 id: genId(), role: 'assistant',
 content: `✅ 「${productName}」智能稽核通过，已成功提交！\n\n商品状态已更新为待审批。`,
 done: true, type: 'chat'
 };
 messages.value.push(successMsg);
 if (currentDbSessionId.value) {
 await saveMessage(currentDbSessionId.value, {
 role: 'assistant',
 content: successMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 scrollToBottom();
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 ElMessage({ message: '配置提交成功！', type: 'success', duration: 2000, plain: true });
};
onMounted(async () => {
 if (!props.sessionId) {
 currentDbSessionId.value = '';
 loadFormState();
 scrollToBottom();
 nextTick(() => inputRef.value?.focus());
 return;
 }
 if (props.dbSessionId) {
 currentDbSessionId.value = props.dbSessionId;
 }
 else {
 currentDbSessionId.value = '';
 }
 if (!currentDbSessionId.value) {
 const result = await apiCreateSession(props.userId || null, '新对话');
 if (result.session_id) {
 currentDbSessionId.value = result.session_id;
 emit('session-init', { localId: props.sessionId, dbSessionId: result.session_id });
 }
 }
 if (currentDbSessionId.value) {
 try {
 const dbMsgs = await apiLoadMessages(currentDbSessionId.value);
 // 如果消息列表已经有消息（从首页发送过来），则不覆盖
 // 否则使用数据库中的消息
 if (messages.value.length === 0) {
 messages.value = dbMsgs;
 } else {
 // 如果有数据库消息但本地已有消息，说明是新会话，不需要合并
 console.log('[ChatAssistant] 消息列表已存在，跳过数据库加载');
 }
 let lastFillingCard = null;
 for (let i = dbMsgs.length - 1; i >= 0; i--) {
 const msg = dbMsgs[i];
 if (msg.formCard && msg.formCard.status === 'filling' && msg.formCard.formSchema) {
 lastFillingCard = msg.formCard;
 break;
 }
 if (!lastFillingCard && msg.formId !== undefined && msg.formSchema !== null) {
 lastFillingCard = {
 msgId: msg.id,
 formId: msg.formId,
 formSchema: msg.formSchema,
 formName: msg.formSchema.formName || '',
 formCode: msg.formSchema.formCode || '',
 status: msg.formSubmitted ? 'submitted' : 'filling',
 fieldCount: msg.formSchema.fields?.length || 0,
 createdAt: msg.createdAt || new Date().toISOString(),
 formData: {}
 };
 }
 }
 if (lastFillingCard) {
 activeFormCard.value = lastFillingCard;
 activeFormMsgId.value = lastFillingCard.msgId || '';
 currentFormId.value = lastFillingCard.formId;
 currentFormSchema.value = lastFillingCard.formSchema;
 currentFormSubmitted.value = lastFillingCard.status === 'submitted';
 }
 else {
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 currentFormSubmitted.value = false;
 }
 }
 catch (e) {
 console.warn('[ChatAssistant] 加载消息失败:', e);
 messages.value = [];
 currentFormId.value = '';
 currentFormSchema.value = null;
 activeFormCard.value = null;
 pendingConfirmForm.value = null;
 currentFormSubmitted.value = false;
 }
 }
 else {
 loadFormState();
 }
 scrollToBottom();
 nextTick(() => inputRef.value?.focus());
});
defineExpose({ requestValidation: () => { }, sendMessageAfterSessionCreated });
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-secondary);
  overflow: hidden;
  flex: 1;
  min-width: 0;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 var(--space-6);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.session-name {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 50vw;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.icon-btn {
  width: 34px; height: 34px;
  background: none; border: none;
  border-radius: var(--radius-md);
  color: var(--text-tertiary); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: background var(--transition-fast), color var(--transition-fast);
  flex-shrink: 0;
  position: relative;
}

.icon-btn:hover { background: var(--bg-secondary); color: var(--text-secondary); }

.icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.icon-btn:disabled:hover { background: none; color: var(--text-tertiary); }

.product-list-btn .badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: #3b82f6;
  color: white;
  font-size: 10px;
  font-weight: 600;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.audit-btn:not(:disabled) { color: #f59e0b; }
.audit-btn:not(:disabled):hover { background: rgba(245, 158, 11, 0.1); color: #d97706; }

/* 响应式适配 */
@media (max-width: 1024px) {
  .chat-layout {
    flex-direction: column;
  }

  .chat-topbar {
    height: 52px;
    padding: 0 16px;
  }

  .session-name {
    max-width: 60vw;
  }
}

@media (max-width: 768px) {
  .chat-topbar {
    height: 48px;
    padding: 0 12px;
  }

  .session-name {
    font-size: 15px;
    max-width: 50vw;
  }

  .icon-btn {
    width: 32px;
    height: 32px;
  }
}

@media (max-width: 480px) {
  .chat-topbar {
    height: 44px;
    padding: 0 10px;
  }

  .session-name {
    font-size: 14px;
    max-width: 45vw;
  }

  .icon-btn {
    width: 28px;
    height: 28px;
  }

  .icon-btn svg {
    width: 14px;
    height: 14px;
  }
}

/* 平板横屏优化 */
@media (min-width: 769px) and (max-width: 1024px) and (orientation: landscape) {
  .chat-layout {
    flex-direction: row;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .chat-main {
    background: var(--bg-primary);
  }
}
</style>