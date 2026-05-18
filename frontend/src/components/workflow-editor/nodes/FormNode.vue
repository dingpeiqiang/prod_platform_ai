<template>
  <div class="node form-node" :class="{ selected, 'is-config-mode': configMode, 'is-compact': compact && !configMode }">
    <div v-if="!configMode" class="node-header">
      <span class="node-icon">📋</span>
      <span class="node-title">{{ data.label }}</span>
      <button @click="toggleConfig" class="config-toggle" :class="{ active: showConfig }">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </button>
    </div>
    <div v-if="compact && !configMode" class="node-compact-body">
      <span class="compact-summary">{{ formSummary }}</span>
      <span class="compact-hint">双击配置</span>
    </div>
    <div v-if="!compact || configMode" class="node-body">
      <div class="mode-selector">
        <button 
          @click="switchMode('online')" 
          :class="['mode-btn', { active: formMode === 'online' }]"
        >
          📝 在线编辑
        </button>
        <button 
          @click="switchMode('reference')" 
          :class="['mode-btn', { active: formMode === 'reference' }]"
        >
          🔗 关联表单
        </button>
      </div>

      <div v-if="formMode === 'online'" class="online-form-panel">
        <div class="form-preview">
          <div v-if="formSchema && formSchema.length > 0">
            <div class="preview-fields">
              <div v-for="(field, idx) in formSchema.slice(0, 3)" :key="field.field" class="preview-field">
                <span class="field-type">{{ getFieldTypeLabel(field.type) }}</span>
                <span class="field-name">{{ field.title }}</span>
              </div>
              <div v-if="formSchema.length > 3" class="preview-more">
                +{{ formSchema.length - 3 }} 个字段
              </div>
            </div>
          </div>
          <div v-else class="empty-form">
            <span>暂无字段</span>
            <button @click="openFormEditor" class="edit-btn">编辑表单</button>
          </div>
        </div>
        <button @click="openFormEditor" class="edit-form-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 20h9"/>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7.5 19.5a2.121 2.121 0 0 1-3-3z"/>
          </svg>
          编辑表单结构
        </button>
      </div>

      <div v-if="formMode === 'reference'" class="reference-form-panel">
        <el-select v-model="selectedFormCode" @change="onFormSelect" class="form-select" placeholder="选择表单">
          <el-option v-for="form in availableForms" :key="form.formCode" :label="form.formName" :value="form.formCode" />
        </el-select>
        <div v-if="selectedForm" class="selected-form-info">
          <div class="form-info-row">
            <span class="info-label">表单名称：</span>
            <span class="info-value">{{ selectedForm.formName }}</span>
          </div>
          <div class="form-info-row">
            <span class="info-label">字段数量：</span>
            <span class="info-value">{{ selectedForm.fieldCount || 0 }} 个</span>
          </div>
          <div class="form-info-row">
            <span class="info-label">状态：</span>
            <span :class="['info-value', selectedForm.isActive ? 'active' : 'inactive']">
              {{ selectedForm.isActive ? '启用' : '禁用' }}
            </span>
          </div>
        </div>
        <el-button v-if="selectedForm" @click="loadFormSchema" type="primary" size="small" class="load-schema-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          加载表单结构
        </el-button>
      </div>
    </div>
    
    <Handle v-if="!configMode" type="target" :position="Position.Left" id="target" />
    <Handle v-if="!configMode" type="source" :position="Position.Right" id="source" />
  </div>

  <Teleport to="body">
    <el-dialog 
      v-model="showModal" 
      title="可视化表单设计器" 
      fullscreen
      :close-on-click-modal="false"
      @close="handleClose"
    >
      <div class="designer-wrapper">
        <form-create-designer 
          ref="designerRef"
          :rule="formSchema"
          @change="onDesignerChange"
        />
      </div>
      
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" @click="saveFormSchema">保存</el-button>
      </template>
    </el-dialog>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { Handle, Position } from '@vue-flow/core';
import { ElSelect, ElOption, ElButton, ElDialog } from 'element-plus';
import FormCreateDesigner from 'form-create-designer';
import * as formApi from '@/services/formApi';
import { nodeDisplayProps } from './nodeDisplayProps.js';

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  ...nodeDisplayProps
});

const emit = defineEmits(['update']);

const designerRef = ref(null);
const showConfig = ref(false);
const formMode = ref(props.data.formMode || 'online');
const selectedFormCode = ref(props.data.selectedFormCode || '');
const availableForms = ref([]);
const showModal = ref(false);
const formSchema = ref([]);

const selectedForm = computed(() => {
  return availableForms.value.find(f => f.formCode === selectedFormCode.value);
});

const formSummary = computed(() => {
  if (formMode.value === 'reference') {
    const name = selectedForm.value?.formName;
    return name ? `关联: ${name}` : '关联表单';
  }
  const count = formSchema.value?.length || 0;
  return count > 0 ? `在线表单 · ${count} 字段` : '在线表单';
});

const toggleConfig = () => {
  showConfig.value = !showConfig.value;
};

const switchMode = (mode) => {
  formMode.value = mode;
  emitUpdate();
};

const getFieldTypeLabel = (type) => {
  const labels = {
    input: '单行文本',
    textarea: '多行文本',
    number: '数字',
    select: '下拉选择',
    radio: '单选框',
    checkbox: '多选框',
    date: '日期',
    datetime: '日期时间',
    email: '邮箱',
    phone: '电话',
    url: '网址',
    switch: '开关',
    slider: '滑块',
    rate: '评分',
    color: '颜色选择',
    upload: '文件上传',
    cascader: '级联选择',
    time: '时间选择',
    datepicker: '日期选择器',
    inputNumber: '数字输入',
    selectMultiple: '多选选择'
  };
  return labels[type] || type;
};

const onFormSelect = () => {
  emitUpdate();
};

const loadFormSchema = async () => {
  if (!selectedFormCode.value) return;
  
  try {
    const result = await formApi.getForm(selectedFormCode.value);
    if (result.success && result.data) {
      formSchema.value = result.data.fields || [];
      emitUpdate();
    }
  } catch (error) {
    console.error('加载表单结构失败:', error);
  }
};

const openFormEditor = () => {
  showModal.value = true;
};

const handleClose = () => {
  showModal.value = false;
};

const onDesignerChange = (list) => {
  formSchema.value = list;
};

const saveFormSchema = () => {
  emitUpdate();
  showModal.value = false;
};

const emitUpdate = () => {
  emit('update', props.data.id, {
    formMode: formMode.value,
    selectedFormCode: selectedFormCode.value,
    formSchema: formSchema.value
  });
};

const loadForms = async () => {
  try {
    const result = await formApi.listForms();
    if (result.success && result.data) {
      availableForms.value = result.data.map(form => ({
        formCode: form.formCode,
        formName: form.formName,
        fieldCount: form.fields?.length || 0,
        isActive: form.isActive
      }));
    }
  } catch (error) {
    console.error('加载表单列表失败:', error);
  }
};

onMounted(() => {
  loadForms();
  
  if (props.data.formSchema) {
    formSchema.value = [...props.data.formSchema];
  }
});

watch(() => props.data, (newData) => {
  formMode.value = newData.formMode || 'online';
  selectedFormCode.value = newData.selectedFormCode || '';
  if (newData.formSchema) {
    formSchema.value = [...newData.formSchema];
  }
}, { deep: true });
</script>

<style scoped>
.form-node {
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  min-width: 260px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.form-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.form-node.is-compact {
  min-width: 160px;
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

.form-node.is-config-mode {
  min-width: unset;
  border: none;
  box-shadow: none;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
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

.config-toggle {
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.config-toggle:hover,
.config-toggle.active {
  background: rgba(255, 255, 255, 0.3);
}

.node-body {
  padding: 10px;
}

.mode-selector {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.mode-btn {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: white;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: #3b82f6;
}

.mode-btn.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.online-form-panel,
.reference-form-panel {
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

.form-preview {
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
  margin-bottom: 8px;
}

.preview-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.field-type {
  font-size: 10px;
  color: #64748b;
  background: #e2e8f0;
  padding: 2px 6px;
  border-radius: 3px;
}

.field-name {
  color: #334155;
}

.preview-more {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 2px;
}

.empty-form {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: #94a3b8;
  font-size: 11px;
}

.empty-form .edit-btn {
  padding: 4px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
}

.empty-form .edit-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.edit-form-btn,
.load-schema-btn {
  width: 100%;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: white;
  color: #3b82f6;
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.2s;
}

.edit-form-btn:hover,
.load-schema-btn:hover {
  background: #eff6ff;
  border-color: #3b82f6;
}

.form-select {
  width: 100%;
  margin-bottom: 8px;
}

.selected-form-info {
  padding: 8px;
  background: #f8fafc;
  border-radius: 4px;
  margin-bottom: 8px;
}

.form-info-row {
  display: flex;
  gap: 4px;
  font-size: 11px;
  margin-bottom: 4px;
}

.form-info-row:last-child {
  margin-bottom: 0;
}

.info-label {
  color: #64748b;
}

.info-value {
  color: #334155;
}

.info-value.active {
  color: #10b981;
}

.info-value.inactive {
  color: #f59e0b;
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

<style>
:deep(.el-dialog) {
  width: 100% !important;
  max-width: 100% !important;
  height: 100% !important;
  margin: 0 !important;
  top: 0 !important;
  left: 0 !important;
  transform: none !important;
}

:deep(.el-dialog__wrapper) {
  width: 100% !important;
  height: 100vh !important;
  display: flex !important;
  align-items: stretch !important;
}

:deep(.el-dialog__body) {
  width: 100% !important;
  height: calc(100% - 100px) !important;
  padding: 0 !important;
  overflow: hidden !important;
}

:deep(.el-dialog__header) {
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: 10 !important;
}

:deep(.el-dialog__footer) {
  position: fixed !important;
  bottom: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: 10 !important;
}

.designer-wrapper {
  width: 100%;
  height: 100%;
  margin-top: 56px;
  margin-bottom: 44px;
}

.fc-designer-container {
  width: 100% !important;
  height: 100% !important;
}
</style>