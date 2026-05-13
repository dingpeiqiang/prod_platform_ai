<template>
  <div class="base-modal">
    <el-dialog
      v-model="visible"
      :title="title"
      :width="width"
      :top="top"
      :close-on-click-modal="closeOnClickModal"
      :close-on-press-escape="closeOnPressEscape"
      :show-close="showClose"
      :modal="modal"
      class="app-dialog"
      @close="handleClose"
    >
      <slot>
        <div class="modal-content">
          <slot name="content" />
        </div>
      </slot>

      <template #header>
        <div class="dialog-header">
          <div class="dialog-title">
            <span v-if="icon" class="title-icon">{{ icon }}</span>
            <span class="title-text">{{ title }}</span>
          </div>
          <button 
            v-if="showClose" 
            class="dialog-close-btn" 
            @click="handleClose"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <slot name="footer">
            <button 
              v-if="showCancel" 
              class="modal-btn modal-btn-cancel" 
              @click="handleCancel"
            >
              {{ cancelText }}
            </button>
            <button 
              v-if="showConfirm" 
              class="modal-btn modal-btn-confirm" 
              :disabled="confirmLoading"
              @click="handleConfirm"
            >
              <span v-if="confirmLoading" class="btn-loading">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle class="spin" cx="12" cy="12" r="10"/>
                </svg>
              </span>
              {{ confirmText }}
            </button>
          </slot>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = withDefaults(defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: ''
  },
  width: {
    type: [String, Number],
    default: '500px'
  },
  top: {
    type: String,
    default: '20vh'
  },
  closeOnClickModal: {
    type: Boolean,
    default: true
  },
  closeOnPressEscape: {
    type: Boolean,
    default: true
  },
  showClose: {
    type: Boolean,
    default: true
  },
  modal: {
    type: Boolean,
    default: true
  },
  showCancel: {
    type: Boolean,
    default: true
  },
  showConfirm: {
    type: Boolean,
    default: true
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  confirmText: {
    type: String,
    default: '确定'
  },
  confirmLoading: {
    type: Boolean,
    default: false
  }
}), {})

const emit = defineEmits(['update:visible', 'close', 'cancel', 'confirm'])

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const handleCancel = () => {
  emit('update:visible', false)
  emit('cancel')
}

const handleConfirm = () => {
  emit('confirm')
}

defineExpose({
  visible: props.visible
})
</script>

<style scoped>
.base-modal {
  :deep(.app-dialog) {
    .el-dialog__wrapper {
      background: var(--bg-overlay);
      backdrop-filter: blur(4px);
    }

    .el-dialog {
      background: var(--bg-elevated);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-xl);
      box-shadow: var(--shadow-xl);
    }

    .el-dialog__body {
      padding: var(--space-5);
      color: var(--text-primary);
    }

    .el-dialog__footer {
      padding: var(--space-4) var(--space-5);
      border-top: 1px solid var(--border-light);
    }
  }
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
}

.dialog-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.title-icon {
  font-size: var(--font-size-lg);
}

.title-text {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.dialog-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  color: var(--text-tertiary);
  transition: all var(--transition-fast);
}

.dialog-close-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.modal-content {
  color: var(--text-primary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

.modal-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2-5) var(--space-4);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  transition: all var(--transition-fast);
  border: none;
  cursor: pointer;
}

.modal-btn-cancel {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

.modal-btn-cancel:hover {
  background: var(--border-default);
  color: var(--text-primary);
}

.modal-btn-confirm {
  background: var(--color-primary-500);
  color: var(--text-inverse);
}

.modal-btn-confirm:hover:not(:disabled) {
  background: var(--color-primary-600);
}

.modal-btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-loading {
  animation: spin 1s linear infinite;
}

.btn-loading svg {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin {
  fill: none;
  stroke-linecap: round;
  animation: spin 1.5s ease-in-out infinite;
}

@keyframes spin {
  0% { stroke-dasharray: 1 100; }
  50% { stroke-dasharray: 50 50; }
  100% { stroke-dasharray: 1 100; }
}
</style>