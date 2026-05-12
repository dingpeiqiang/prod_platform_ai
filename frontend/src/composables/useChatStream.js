import { ref } from 'vue';
import { genId, formatMarkdownText } from '../utils/chatUtils.js';
import { saveMessage, updateMessage } from '../services/chatApi.js';
import { getEventHandler, getPostProcessor, registerEventHandler, registerPostProcessor } from './useIntentRegistry.js';
export function useChatStream(messagesRef, currentDbSessionIdRef) {
 const isStreaming = ref(false);
 let abortCtrl = null;
 const stopStream = () => {
 if (abortCtrl) {
 abortCtrl.abort();
 abortCtrl = null;
 }
 };
 const handleEvent = (data, idx) => {
 const msg = messagesRef.value[idx];
 if (!msg)
 return;
 switch (data.type) {
 case 'thinking':
 case 'decision':
 case 'executing': {
 const last = msg.reasoning[msg.reasoning.length - 1];
 if (last && last.content === data.content)
 break;
 msg.reasoning.push({ type: 'thinking', content: data.content, result: data.result || null });
 msg.showReasoning = true;
 msg.latestStepIndex = msg.reasoning.length - 1;
 if (currentDbSessionIdRef.value && msg.dbMessageId) {
 const stepTypeMap = { thinking: 'thinking', decision: 'decision', executing: 'action' };
 const existingMeta = msg.metadata || {};
 let reasoningFull = [];
 if (existingMeta.reasoning_full) {
 try {
 reasoningFull = JSON.parse(existingMeta.reasoning_full);
 }
 catch { }
 }
 reasoningFull.push({
 type: stepTypeMap[data.type] || 'thinking',
 content: data.content,
 result: data.result || null,
 _index: reasoningFull.length
 });
 updateMessage(currentDbSessionIdRef.value, msg.dbMessageId, {
 metadata: {
 ...existingMeta,
 reasoning_full: JSON.stringify(reasoningFull),
 stream_status: 'streaming'
 }
 }).catch(err => console.warn('[SSE] 更新 thinking 步骤失败:', err));
 }
 break;
 }
 case 'reasoning': {
 const steps = msg.reasoning;
 if (steps && steps.length) {
 const lastThinkingIdx = steps.reduce((acc, s, i) => s.type === 'thinking' ? i : acc, -1);
 if (lastThinkingIdx >= 0) {
 const target = steps[lastThinkingIdx];
 if (!target.reasoning) {
 target.reasoning = '';
 target._showReasoning = false;
 }
 target.reasoning += data.content || '';
 }
 }
 break;
 }
 case 'text_start':
 msg.streamText = '';
 break;
 case 'text':
 msg.streamText = (msg.streamText || '') + (data.content || '');
 break;
 case 'text_end':
 if (msg.streamText && !msg._formatted) {
 msg.streamText = formatMarkdownText(msg.streamText);
 msg._formatted = true;
 }
 break;
 case 'intent': {
 const { intentType, action, data: intentData } = data;
 const handler = getEventHandler(intentType);
 if (handler) {
 handler({ type: intentType, action, content: intentData }, msg);
 }
 break;
 }
 case 'result': {
 const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content;
 msg.intentResult = parsed;
 break;
 }
 case 'config':
 case 'delete_form':
 case 'manage_history':
 case 'validation_fail':
 case 'validation_pass': {
 const handler = getEventHandler(data.type);
 if (handler) {
 handler(data, msg);
 }
 break;
 }
 case 'error': {
 let errMsg = data.content || data.message || '未知错误';
 if (data.error_code) {
 errMsg += ` [${data.error_code}]`;
 }
 if (data.recoverable === false) {
 errMsg += ' (不可恢复)';
 }
 msg.reasoning.push({ type: 'error', content: errMsg });
 break;
 }
 case 'tool_error': {
 let errorContent = `⚠️ 工具 ${data.tool} 执行失败: ${data.error}`;
 if (data.error_code) {
 errorContent += ` [${data.error_code}]`;
 }
 if (data.recoverable === false) {
 errorContent += ' (不可恢复)';
 }
 msg.reasoning.push({ type: 'error', content: errorContent });
 break;
 }
 }
 };
 const sendStreamMessage = async (text, { formCode = null, formData = null } = {}) => {
 if (!currentDbSessionIdRef.value)
 return;
 const aiMsg = {
 id: genId(), role: 'assistant',
 reasoning: [], streamText: '', content: '',
 showReasoning: false,
 done: false, type: 'chat'
 };
 messagesRef.value.push(aiMsg);
 const msgIdx = messagesRef.value.length - 1;
 let dbMessageId = null;
 if (currentDbSessionIdRef.value) {
 try {
 const saved = await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: '',
 reasoning: [],
 metadata: { stream_status: 'streaming' }
 });
 if (saved?.message_id) {
 dbMessageId = saved.message_id;
 aiMsg.dbMessageId = dbMessageId;
 }
 }
 catch (e) {
 console.warn('[SSE] 初始保存 AI 消息失败:', e);
 }
 }
 isStreaming.value = true;
 abortCtrl = new AbortController();
 try {
 const chatHistory = messagesRef.value
 .filter(m => m.role === 'user' || (m.role === 'assistant' && m.done && m.content))
 .slice(0, -1)
 .slice(-20)
 .map(m => ({ role: m.role, content: m.content || m.streamText || '' }));
 const requestBody = {
 messages: [...chatHistory, { role: 'user', content: text }]
 };
 if (formCode)
 requestBody.formCode = formCode;
 if (formData)
 requestBody.formData = formData;
 const resp = await fetch('/api/v1/chat/stream', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify(requestBody),
 signal: abortCtrl.signal
 });
 if (!resp.ok)
 throw new Error(`HTTP ${resp.status}`);
 const reader = resp.body.getReader();
 const decoder = new TextDecoder();
 let buffer = '';
 let intentData = null;
 let intentType = 'form';
 let eventCount = 0;
 while (true) {
 const { done, value } = await reader.read();
 if (done)
 break;
 buffer += decoder.decode(value, { stream: true });
 const frames = buffer.split('\n\n');
 buffer = frames.pop();
 for (const frame of frames) {
 if (!frame.startsWith('data:'))
 continue;
 try {
 const data = JSON.parse(frame.slice(5).trim());
 eventCount++;
 if (eventCount <= 3)
 console.log('[SSE] 事件 #' + eventCount + ':', data.type, data.content?.substring?.(0, 50) || '');
 handleEvent(data, msgIdx);
 if (data.type === 'done') {
 if (!intentType && data.intentType) {
 intentType = data.intentType;
 }
 else if (!intentType && data.isForm) {
 intentType = 'form';
 }
 if (data.intentData)
 intentData = data.intentData;
 }
 if (data.type === 'intent' && data.intentType) {
 intentType = data.intentType;
 if (data.data) {
 intentData = data.data;
 const msg = messagesRef.value[msgIdx];
 if (msg) {
 msg.metadata = {
 intentType: data.data.intentType,
 formCode: data.data.formCode,
 extractedFields: data.data.extractedFields,
 confidence: data.data.confidence,
 model: data.data.model
 };
 }
 }
 }
 if (data.type === 'result') {
 try {
 const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content;
 if (parsed?.formCode && !intentData) {
 intentData = parsed;
 }
 }
 catch { }
 }
 }
 catch { }
 }
 }
 const msg = messagesRef.value[msgIdx];
 if (msg) {
 msg.done = true;
 // 有 reasoning 内容或有错误时保持面板展开
 const hasReasoning = msg.reasoning.some(r => r.reasoning && r.reasoning.trim());
 msg.showReasoning = msg.reasoning.some(r => r.type === 'error') || hasReasoning || false;
 const postProcessor = getPostProcessor(intentType);
 if (postProcessor) {
 try {
 await postProcessor(msg, intentData);
 }
 catch (e) {
 console.error('[SSE 流结束] 后处理器执行异常:', e);
 }
 }
 else {
 msg.content = msg.streamText;
 }
 if (currentDbSessionIdRef.value) {
 const finalMetadata = {
 ...(msg.metadata || {}),
 reasoning_full: JSON.stringify(msg.reasoning.map((r, i) => ({ ...r, _index: i }))),
 reasoning: msg.reasoning.map(s => s.content || '').join('\n'),
 stream_status: 'done',
 done: 'true',
 intent_type: intentType || undefined,
 form_code: intentData?.formCode || undefined,
 extracted_fields: intentData?.extractedFields || undefined,
 confidence: intentData?.confidence != null ? String(intentData.confidence) : undefined,
 model: intentData?.model || undefined
 };
 Object.keys(finalMetadata).forEach(k => finalMetadata[k] === undefined && delete finalMetadata[k]);
 if (msg.dbMessageId) {
 await updateMessage(currentDbSessionIdRef.value, msg.dbMessageId, {
 content: msg.content || msg.streamText || '',
 metadata: finalMetadata
 });
 }
 else {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: msg.content || msg.streamText || '',
 reasoning: msg.reasoning,
 metadata: finalMetadata
 });
 }
 }
 }
 }
 catch (err) {
 if (err.name !== 'AbortError') {
 console.error(err);
 const msg = messagesRef.value[msgIdx];
 if (msg) {
 msg.done = true;
 msg.showReasoning = true;
 msg.reasoning.push({ type: 'error', content: '请求出错：' + err.message });
 msg.content = '抱歉，遇到了一些问题，请稍后重试。';
 if (currentDbSessionIdRef.value) {
 await saveMessage(currentDbSessionIdRef.value, {
 role: 'assistant',
 content: msg.content,
 reasoning: msg.reasoning,
 metadata: msg.metadata || null
 }).catch(() => { });
 }
 }
 }
 else {
 const msg = messagesRef.value[msgIdx];
 if (msg) {
 msg.done = true;
 msg.showReasoning = false;
 if (!msg.content)
 msg.content = msg.streamText || '（已停止）';
 }
 }
 }
 finally {
 isStreaming.value = false;
 abortCtrl = null;
 }
 };
 return {
 isStreaming,
 stopStream,
 sendStreamMessage
 };
}

