import { ElMessage, ElMessageBox } from 'element-plus';
import { genId, formatVersionTime } from '../utils/chatUtils.js';
import { saveMessage } from '../services/chatApi.js';
export function useIntentHandlers(messagesRef, currentDbSessionIdRef, emit) {
 const _updateIntentData = (msg, intentType, partial) => {
 if (!msg._intentData)
 msg._intentData = {};
 msg._intentData[intentType] = { ...msg._intentData[intentType], ...partial };
 };
 const loadVersions = async (msg) => {
 const intentData = msg._intentData?.['delete_form'];
 const formCode = intentData?.formCode || msg.intentData?.formCode;
 if (!formCode)
 return;
 _updateIntentData(msg, 'delete_form', { loadingVersions: true });
 try {
 const resp = await fetch(`/api/v1/chat/form-versions/${encodeURIComponent(formCode)}`);
 const result = await resp.json();
 if (result.success && result.versions?.length) {
 _updateIntentData(msg, 'delete_form', { versionList: result.versions });
 }
 else {
 _updateIntentData(msg, 'delete_form', { versionList: [] });
 }
 }
 catch (e) {
 console.error('加载版本列表失败', e);
 _updateIntentData(msg, 'delete_form', { versionList: [] });
 }
 finally {
 _updateIntentData(msg, 'delete_form', { loadingVersions: false });
 }
 };
 const handleRollback = async (msg, version) => {
 const intentData = msg._intentData?.['delete_form'];
 const formCode = intentData?.formCode || msg.intentData?.formCode;
 try {
 await ElMessageBox.confirm(`确定回退到 ${formatVersionTime(version.timestamp)} 的版本？当前版本会自动备份。`, '确认回退', { confirmButtonText: '确定回退', cancelButtonText: '取消', type: 'warning' });
 }
 catch { return; }
 _updateIntentData(msg, 'delete_form', { rollingBack: version.id, rollbackResult: null });
 try {
 const resp = await fetch('/api/v1/chat/rollback-form', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify({ formCode, versionId: version.id })
 });
 const result = await resp.json();
 _updateIntentData(msg, 'delete_form', { rollbackResult: result });
 if (result.success) {
 ElMessage({ message: `已成功回退到 ${version.id}`, type: 'success', duration: 3000, plain: true });
 messagesRef.value.push({
 id: genId(), role: 'assistant',
 content: `🔄 表单「${result.data?.formName || formCode}」已恢复到版本 ${version.id}，现在可以正常使用了。`,
 done: true, type: 'chat'
 });
 }
 else {
 ElMessage({ message: result.message || '回退失败', type: 'error', duration: 4000, plain: true });
 }
 }
 catch (e) {
 console.error('回退失败', e);
 _updateIntentData(msg, 'delete_form', { rollbackResult: { success: false, message: '网络错误' } });
 }
 finally {
 _updateIntentData(msg, 'delete_form', { rollingBack: null });
 }
 };
 const scoreLevel = (score) => {
 if (score >= 80)
 return 'good';
 if (score >= 60)
 return 'warn';
 return 'bad';
 };
 const scoreLabel = (score) => {
 if (score >= 90)
 return '优秀';
 if (score >= 70)
 return '良好';
 if (score >= 50)
 return '一般';
 return '需改善';
 };
 const topFieldStats = (fieldStats) => {
 if (!fieldStats)
 return {};
 const entries = Object.entries(fieldStats);
 entries.sort((a, b) => (b[1]?.distinctValues || 0) - (a[1]?.distinctValues || 0));
 return Object.fromEntries(entries.slice(0, 5));
 };
 const handleAnalyzeHistory = async (msg, sendMessage) => {
 const formCode = msg.historyData?.formCode || msg.intentData?.formCode;
 if (!formCode)
 return;
 msg.historyData = null;
 messagesRef.value.push({ id: genId(), role: 'user', content: `分析一下${msg.historyData?.formName || formCode}的数据质量`, done: true });
 await sendMessage(`分析一下${msg.historyData?.formName || formCode}的数据质量`);
 };
 const handleImportHistory = async (msg) => {
 const formCode = msg.historyData?.formCode || msg.intentData?.formCode;
 if (!formCode)
 return;
 msg.importing = true;
 try {
 const resp = await fetch('/api/v1/chat/history/import', {
 method: 'POST',
 headers: { 'Content-Type': 'application/json' },
 body: JSON.stringify({ formCode })
 });
 const result = await resp.json();
 msg.importResult = result;
 }
 catch (e) {
 msg.importResult = { success: false, message: '导入失败: ' + e.message };
 }
 finally {
 msg.importing = false;
 }
 };
 const handleExportHistory = async (msg, opts) => {
 const formCode = msg.historyData?.formCode || msg.intentData?.formCode;
 if (!formCode)
 return;
 try {
 const resp = await fetch(`/api/v1/config/export/${formCode}?format=${opts.format}`);
 if (!resp.ok) {
 const err = await resp.json().catch(() => ({ detail: '导出失败' }));
 ElMessage.error(err.detail || '导出失败');
 return;
 }
 const blob = await resp.blob();
 const disposition = resp.headers.get('Content-Disposition') || '';
 const filenameMatch = disposition.match(/filename\*?=['"]?(?:UTF-8'')?([^;\n"']+)/i);
 const filename = filenameMatch ? decodeURIComponent(filenameMatch[1]) : `${formCode}_export.${opts.format}`;
 const url = URL.createObjectURL(blob);
 const link = document.createElement('a');
 link.href = url;
 link.download = filename;
 document.body.appendChild(link);
 link.click();
 document.body.removeChild(link);
 URL.revokeObjectURL(url);
 ElMessage.success(`已导出 ${filename}`);
 }
 catch (e) {
 ElMessage.error('导出失败：' + e.message);
 }
 };
 const handleFixValidationErrors = (inputTextRef, errors) => {
 if (errors?.length > 0) {
 const first = errors[0];
 inputTextRef.value = `修改一下：${first.fieldName || '字段'}，${first.reason}`;
 }
 };
 const handleIgnoreValidationWarnings = () => {
 ElMessage.info('已忽略提示，可继续操作');
 };
 const handleIntentEvent = ({ intentType, action, payload, msg }, inputTextRef, sendMessage, currentFormIdRef, currentFormSchemaRef) => {
 switch (intentType) {
 case 'delete_form': {
 if (action === 'rollback') {
 handleRollback(msg, payload);
 }
 else if (action === 'load-versions') {
 loadVersions(msg);
 }
 break;
 }
 case 'manage_history': {
 if (action === 'import') {
 handleImportHistory(msg);
 }
 else if (action === 'analyze') {
 handleAnalyzeHistory(msg, sendMessage);
 }
 else if (action === 'export') {
 handleExportHistory(msg, payload);
 }
 break;
 }
 case 'validate': {
 if (action === 'fix') {
 handleFixValidationErrors(inputTextRef, payload);
 }
 else if (action === 'ignore-warnings') {
 handleIgnoreValidationWarnings();
 }
 break;
 }
 }
 };
 return {
 handleRollback,
 loadVersions,
 handleAnalyzeHistory,
 handleImportHistory,
 handleExportHistory,
 handleFixValidationErrors,
 handleIgnoreValidationWarnings,
 handleIntentEvent,
 scoreLevel,
 scoreLabel,
 topFieldStats
 };
}

