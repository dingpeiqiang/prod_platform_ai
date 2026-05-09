import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { genId } from '../utils/chatUtils.js';
import { saveMessage, updateMessage } from '../services/chatApi.js';
export function useFormHandling(messagesRef, currentDbSessionIdRef) {
 const currentFormId = ref('');
 const currentFormSchema = ref(null);
 const currentFormSubmitted = ref(false);
 const activeFormCard = ref(null);
 const activeFormMsgId = ref('');
 const pendingConfirmForm = ref(null);
 const updateFormCardStatus = (msgId, formId, status) => {
 let targetMsg = null;
 if (msgId) {
 targetMsg = messagesRef.value.find(m => m.id === msgId);
 }
 if (!targetMsg && formId) {
 targetMsg = messagesRef.value.find(m => m.formCard?.formId === formId);
 }
 if (targetMsg?.formCard) {
 targetMsg.formCard.status = status;
 }
 };
 const generateForm = async (intentData) => {
 const { formCode, extractedFields, fieldRecommendations } = intentData;
 const loadingMsg = {
 id: genId(), role: 'assistant',
 content: `正在为你生成 ${formCode || ''} 表单`, done: true, type: 'chat',
 loading: true,
 loadingDots: 0
 };
 messagesRef.value.push(loadingMsg);
 const loadingInterval = setInterval(() => {
 if (loadingMsg.loadingDots < 3) {
 loadingMsg.loadingDots++;
 loadingMsg.content = `正在为你生成 ${formCode || ''} 表单${'.'.repeat(loadingMsg.loadingDots)}`;
 }
 else {
 loadingMsg.loadingDots = 0;
 loadingMsg.content = `正在为你生成 ${formCode || ''} 表单`;
 }
 }, 400);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: `正在为你生成 ${formCode || ''} 表单...`,
 reasoning: []
 }).catch(() => { });
 }
 try {
 const body = {
 userInput: `生成${formCode || ''}表单`, formCode,
 extractedFields: Object.keys(extractedFields || {}).length ? extractedFields : undefined,
 fieldRecommendations: fieldRecommendations || undefined
 };
 const resp = await fetch('/api/v1/form/generate', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify(body)
 });
 clearInterval(loadingInterval);
 loadingMsg.loading = false;
 const result = await resp.json();
 let replyMsg;
 if (result.success) {
 const msgId = genId();
 const newFormCard = {
 msgId: msgId,
 formId: result.formId,
 formName: result.formSchema?.formName || formCode,
 formCode: result.formSchema?.formCode || formCode,
 status: 'filling',
 fieldCount: result.formSchema?.fields?.length || 0,
 createdAt: new Date().toISOString(),
 formSchema: result.formSchema,
 formData: {}
 };
 replyMsg = {
 id: msgId, role: 'assistant',
 content: `✅ 已生成表单，请在右侧填写并提交。`, done: true, type: 'chat',
 formCard: newFormCard
 };
 activeFormCard.value = newFormCard;
 activeFormMsgId.value = msgId;
 }
 else {
 replyMsg = {
 id: genId(), role: 'assistant',
 content: result.message || '生成表单失败，请重试。', done: true, type: 'chat'
 };
 }
 messagesRef.value.push(replyMsg);
 if (currentDbSessionIdRef.value && replyMsg.formCard) {
 const saved = await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: replyMsg.content,
 reasoning: [],
 formCard: replyMsg.formCard
 }).catch(() => { });
 if (saved?.message_id) {
 replyMsg.formCard.msgId = saved.message_id;
 activeFormCard.value.msgId = saved.message_id;
 replyMsg.id = saved.message_id;
 }
 }
 }
 catch {
 const errorMsg = {
 id: genId(), role: 'assistant',
 content: '生成表单时出现网络错误，请重试。', done: true, type: 'chat'
 };
 messagesRef.value.push(errorMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: errorMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 }
 };
 const updateFormFields = async (intentData) => {
 const { formCode, detectedFormCode, extractedFields } = intentData;
 const actualFormCode = formCode || detectedFormCode;
 if (!currentFormSchema.value) {
 const fallbackMsg = {
 id: genId(), role: 'assistant',
 content: `检测到你想要更新表单，但没有找到现有表单。正在为你生成新的 ${actualFormCode || ''} 表单...`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(fallbackMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: fallbackMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 if (actualFormCode) {
 await generateForm({
 formCode: actualFormCode,
 extractedFields: extractedFields || {}
 });
 }
 return;
 }
 let loadingMsg;
 if (extractedFields && Object.keys(extractedFields).length > 0) {
 const fieldNames = Object.keys(extractedFields);
 loadingMsg = {
 id: genId(), role: 'assistant',
 content: `正在更新字段: ${fieldNames.join(', ')}...`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(loadingMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: loadingMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 }
 const newSchema = JSON.parse(JSON.stringify(currentFormSchema.value));
 let replyMsg;
 if (extractedFields && Object.keys(extractedFields).length > 0) {
 let updatedCount = 0;
 for (const [fieldCode, value] of Object.entries(extractedFields)) {
 const field = newSchema.fields?.find(f => f.fieldCode === fieldCode || f.code === fieldCode);
 if (field) {
 field.value = value;
 updatedCount++;
 }
 }
 if (updatedCount > 0) {
 currentFormSchema.value = newSchema;
 replyMsg = {
 id: genId(), role: 'assistant',
 content: `✅ 已更新 ${updatedCount} 个字段，请查看右侧表单确认。`,
 done: true, type: 'chat'
 };
 }
 else {
 replyMsg = {
 id: genId(), role: 'assistant',
 content: `⚠️ 未找到可更新的字段，表单项可能不匹配。`,
 done: true, type: 'chat'
 };
 }
 }
 else {
 replyMsg = {
 id: genId(), role: 'assistant',
 content: `⚠️ 未提取到字段值，请尝试更明确的表达。`,
 done: true, type: 'chat'
 };
 }
 messagesRef.value.push(replyMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: replyMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 };
 const handleConfirmSubmit = async (data) => {
 const schema = data.schema || currentFormSchema.value;
 pendingConfirmForm.value = {
 formId: data.formId,
 formCode: data.formCode,
 formName: data.formName,
 data: data.data,
 schema: schema
 };
 const _getFieldOptions = (field) => {
 if (field.enumConfig) {
 if (field.enumConfig.type === 'static' && Array.isArray(field.enumConfig.options)) {
 return field.enumConfig.options;
 }
 if (field.enumConfig.type === 'api') {
 const fallback = field.enumConfig.api?.fallback;
 if (Array.isArray(fallback))
 return fallback;
 }
 }
 return field.options || [];
 };
 let fieldLines = [];
 if (schema && schema.fields) {
 for (const field of schema.fields) {
 const val = data.data[field.fieldCode];
 let displayVal;
 if (val !== undefined && val !== null && val !== '') {
 const options = _getFieldOptions(field);
 if (options.length > 0) {
 if (Array.isArray(val)) {
 displayVal = val.map(v => {
 const opt = options.find(o => o.value === v);
 return opt ? `${opt.label}[${v}]` : v;
 }).join(', ');
 }
 else {
 const opt = options.find(o => o.value === val);
 displayVal = opt ? `${opt.label}[${val}]` : String(val);
 }
 }
 else {
 displayVal = Array.isArray(val) ? val.join(', ') : String(val);
 }
 }
 else {
 displayVal = '(未填写)';
 }
 fieldLines.push(`- ${field.fieldName}(${field.fieldCode})：${displayVal}`);
 }
 }
 const validationMessage = `请帮我校验【${data.formName}】（${data.formCode}）的填写内容是否符合业务规则：\n${fieldLines.join('\n')}`;
 return validationMessage;
 };
 const checkUserConfirmation = (userMessage) => {
 if (!pendingConfirmForm.value)
 return false;
 const msg = userMessage.toLowerCase();
 return msg.includes('确认') || msg.includes('好的') || msg.includes('是') || msg.includes('提交');
 };
 const handleDoConfirmSubmit = async () => {
 if (!pendingConfirmForm.value)
 return;
 const formData = pendingConfirmForm.value.data;
 const formId = pendingConfirmForm.value.formId;
 const formName = pendingConfirmForm.value.formName;
 const schema = pendingConfirmForm.value.schema;
 pendingConfirmForm.value = null;
 if (activeFormCard.value) {
 updateFormCardStatus(activeFormCard.value.msgId, formId, 'submitted');
 activeFormCard.value.status = 'submitted';
 activeFormCard.value.formData = formData;
 if (currentDbSessionIdRef.value && activeFormCard.value.msgId) {
 await updateMessage(currentDbSessionIdRef.value, activeFormCard.value.msgId, {
 metadata: { formCard: JSON.stringify(activeFormCard.value) }
 }).catch(e => console.warn('[handleDoConfirmSubmit] 更新 formCard 失败:', e));
 }
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 }
 try {
 const response = await fetch('/api/v1/form/submit', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify({
 formId: formId,
 data: formData,
 version: 1
 })
 });
 const result = await response.json();
 if (result.success) {
 let summaryLines = [];
 if (schema && schema.fields) {
 for (const field of schema.fields) {
 const val = formData[field.fieldCode];
 if (val !== undefined && val !== null && val !== '') {
 const displayVal = Array.isArray(val) ? val.join(', ') : String(val);
 summaryLines.push(`- **${field.fieldName}**: ${displayVal}`);
 }
 }
 }
 const summary = summaryLines.length > 0 ? `\n\n提交内容：\n${summaryLines.join('\n')}` : '';
 const submitMsg = {
 id: genId(), role: 'assistant',
 content: `✅ ${formName || '表单'}已成功提交！${summary}\n\n还有什么我可以帮你的吗？`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(submitMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: submitMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 ElMessage({ message: '表单提交成功！', type: 'success', plain: true });
 }
 else {
 const errorMsg = {
 id: genId(), role: 'assistant',
 content: `❌ 提交失败：${result.message || '未知错误'}`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(errorMsg);
 ElMessage.error(result.message || '提交失败');
 }
 }
 catch (e) {
 console.error('[handleDoConfirmSubmit] 提交失败:', e);
 const errorMsg = {
 id: genId(), role: 'assistant',
 content: `❌ 提交失败：${e.message}`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(errorMsg);
 ElMessage.error('提交失败: ' + e.message);
 }
 };
 const handleCancelSubmit = () => {
 if (pendingConfirmForm.value) {
 pendingConfirmForm.value = null;
 const cancelMsg = {
 id: genId(), role: 'assistant',
 content: '好的，已取消提交。表单数据保留在右侧，可以继续修改。',
 done: true, type: 'chat'
 };
 messagesRef.value.push(cancelMsg);
 }
 };
 const handleFormSubmit = async (formData, formId) => {
 if (!activeFormCard.value)
 return;
 const card = activeFormCard.value;
 const schema = card.formSchema;
 let summaryLines = [];
 if (schema && schema.fields) {
 for (const field of schema.fields) {
 const val = formData[field.fieldCode];
 if (val !== undefined && val !== null && val !== '') {
 const displayVal = Array.isArray(val) ? val.join(', ') : String(val);
 summaryLines.push(`- **${field.fieldName}**: ${displayVal}`);
 }
 }
 }
 const summary = summaryLines.length > 0
 ? `提交内容：\n${summaryLines.join('\n')}`
 : '';
 updateFormCardStatus(card.msgId, card.formId, 'submitted');
 card.status = 'submitted';
 card.formData = formData;
 if (currentDbSessionIdRef.value && card.msgId) {
 await updateMessage(currentDbSessionIdRef.value, card.msgId, {
 metadata: { formCard: JSON.stringify(card) }
 }).catch(e => console.warn('[handleFormSubmit] 更新 formCard 失败:', e));
 }
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 ElMessage({ message: '表单提交成功！', type: 'success', plain: true });
 const submitMsg = {
 id: genId(), role: 'assistant',
 content: `✅ 表单已成功提交！${summary ? '\n\n' + summary : ''}\n\n还有什么我可以帮你的吗？`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(submitMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: submitMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 };
 const handleFormCancel = async () => {
 if (!activeFormCard.value)
 return;
 const cancelledCard = activeFormCard.value;
 const cancelMsgId = genId();
 updateFormCardStatus(cancelledCard.msgId, cancelledCard.formId, 'cancelled');
 cancelledCard.status = 'cancelled';
 const targetMsg = messagesRef.value.find(m => m.id === cancelledCard.msgId);
 if (targetMsg && currentDbSessionIdRef.value) {
 targetMsg.formCard = cancelledCard;
 const payload = {
 content: targetMsg.content,
 metadata: {
 formCard: JSON.stringify(cancelledCard)
 }
 };
 try {
 await updateMessage(currentDbSessionIdRef.value, cancelledCard.msgId, payload);
 }
 catch (e) {
 console.error('[handleFormCancel] 更新失败:', e);
 }
 }
 activeFormCard.value = null;
 activeFormMsgId.value = '';
 const cancelMsg = {
 id: cancelMsgId, role: 'assistant',
 content: '好的，已取消。还有什么我可以帮你的吗？', done: true, type: 'chat'
 };
 messagesRef.value.push(cancelMsg);
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: cancelMsg.content,
 reasoning: []
 }).catch(() => { });
 }
 };
 const handleConfirmSubmitForActiveForm = () => {
 if (!activeFormCard.value)
 return;
 const formName = activeFormCard.value.formName || '当前表单';
 const guideMsg = {
 id: genId(), role: 'assistant',
 content: `好的，请先完成右侧的「${formName}」并点击提交按钮。\n\n提交后我会帮你处理后续操作。`,
 done: true, type: 'chat'
 };
 messagesRef.value.push(guideMsg);
 };
 const handleFormFieldChange = (fieldCode, value) => {
 if (activeFormCard.value) {
 activeFormCard.value.formData[fieldCode] = value;
 }
 };
 return {
 currentFormId,
 currentFormSchema,
 currentFormSubmitted,
 activeFormCard,
 activeFormMsgId,
 pendingConfirmForm,
 generateForm,
 updateFormFields,
 handleConfirmSubmit,
 checkUserConfirmation,
 handleDoConfirmSubmit,
 handleCancelSubmit,
 handleFormSubmit,
 handleFormCancel,
 handleConfirmSubmitForActiveForm,
 handleFormFieldChange,
 updateFormCardStatus
 };
}

