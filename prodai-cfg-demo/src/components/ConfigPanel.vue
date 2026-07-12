<script setup>
import ConfigForm from './ConfigForm.vue'
import ProductListItems from './ProductListItems.vue'

defineProps({
  visible: Boolean,
  mobileHidden: { type: Boolean, default: false },
  isMobile: { type: Boolean, default: false },
  currentProduct: Object,
  auditStatus: String,
  isModified: Boolean,
  activeTab: String,
  showProductListView: Boolean,
  products: Array,
  currentProductId: String,
  formData: Object,
  modifiedFields: Object,
})

const emit = defineEmits([
  'update:activeTab',
  'back-from-list',
  'back-to-chat',
  'select-product',
  'copy',
  'edit',
  'delete',
  'field-change',
  'save-draft',
  'audit',
  'submit',
])

const tabs = [
  { id: 'base', label: '基础信息' },
  { id: 'release', label: '发布信息' },
  { id: 'optional', label: '可选配置' },
  { id: 'summary', label: '信息预览' },
]

const auditClass = {
  pending: 'status-pending',
  pass: 'status-pass',
  fail: 'status-fail',
}

const auditText = {
  pending: '未稽核',
  pass: '已通过',
  fail: '不通过',
}
</script>

<template>
  <aside v-show="visible" class="config-container" :class="{ 'mobile-hidden': mobileHidden }">
    <template v-if="!showProductListView">
      <div class="config-header">
        <button
          v-if="isMobile"
          type="button"
          class="mobile-back-chat"
          aria-label="返回对话"
          @click="emit('back-to-chat')"
        >
          <i class="fa-solid fa-arrow-left" /> 对话
        </button>
        <div class="scheme-info">
          <span class="scheme-name">{{ currentProduct?.name || '暑期促销活动方案' }}</span>
          <span class="template-badge">个人主资费</span>
          <span class="status-tag" :class="auditClass[auditStatus]">{{ auditText[auditStatus] }}</span>
          <div v-if="isModified" class="draft-indicator">
            <span class="dot" />
            <span>有未保存的修改</span>
          </div>
        </div>
      </div>

      <nav class="tabs" role="tablist">
        <button
          v-for="t in tabs"
          :key="t.id"
          type="button"
          role="tab"
          class="tab"
          :class="{ active: activeTab === t.id }"
          @click="emit('update:activeTab', t.id)"
        >
          {{ t.label }}
        </button>
      </nav>

      <div class="form-area">
        <ConfigForm
          :form-data="formData"
          :modified-fields="modifiedFields"
          :active-tab="activeTab"
          @field-change="emit('field-change', $event)"
        />
      </div>

      <footer class="config-footer">
        <div class="footer-left">
          <button type="button" class="footer-btn btn-draft" @click="emit('save-draft')">
            <i class="fa-regular fa-floppy-disk" /> 保存草稿
          </button>
          <button type="button" class="footer-btn btn-check" @click="emit('audit')">
            <i class="fa-solid fa-clipboard-check" /> 智能稽核
          </button>
        </div>
        <button type="button" class="btn-submit" @click="emit('submit')">
          <i class="fa-solid fa-paper-plane" /> 提交配置
        </button>
      </footer>
    </template>

    <div v-else class="product-list-view">
      <div class="product-list-view-header">
        <h3>
          <i class="fa-solid fa-list" /> 商品列表
          <span class="count">{{ products.length }}</span>
        </h3>
        <button type="button" class="back-btn" @click="emit('back-from-list')">
          <i class="fa-solid fa-arrow-left" /> 返回配置
        </button>
      </div>
      <ProductListItems
        :products="products"
        :current-product-id="currentProductId"
        @select="emit('select-product', $event)"
        @copy="emit('copy', $event)"
        @edit="emit('edit', $event)"
        @delete="emit('delete', $event)"
      />
    </div>
  </aside>
</template>

<style scoped>
.config-container {
  flex: 0.6;
  background: var(--surface-muted);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.config-header {
  background: var(--surface);
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
}

.scheme-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.scheme-name {
  font-weight: 700;
  font-size: 0.9375rem;
}

.template-badge {
  background: var(--primary);
  color: white;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.6875rem;
  font-weight: 600;
}

.status-tag {
  font-size: 0.6875rem;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 500;
}

.status-pending {
  background: var(--surface-muted);
  color: var(--text-secondary);
}
.status-pass {
  background: var(--success-bg);
  color: var(--success);
}
.status-fail {
  background: var(--error-bg);
  color: var(--error);
}

.draft-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--warning);
}

.dot {
  width: 8px;
  height: 8px;
  background: var(--warning);
  border-radius: 50%;
  animation: pulse-dot 2s infinite;
}

.tabs {
  display: flex;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  padding: 0 12px;
  gap: 4px;
}

.tab {
  padding: 14px 16px;
  border: none;
  background: transparent;
  font-size: 0.875rem;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: var(--transition);
  font-weight: 500;
}

.tab:hover {
  color: var(--primary);
}

.tab.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.form-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.config-footer {
  background: var(--surface);
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.footer-left {
  display: flex;
  gap: 10px;
}

.footer-btn {
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: var(--transition);
}

.btn-draft {
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text);
}

.btn-draft:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.btn-check {
  background: var(--surface);
  border: 1px solid var(--warning);
  color: var(--warning);
}

.btn-check:hover {
  background: var(--warning-bg);
}

.btn-submit {
  background: var(--primary);
  border: none;
  color: white;
  padding: 10px 22px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: var(--transition);
}

.btn-submit:hover {
  background: var(--primary-hover);
  box-shadow: 0 4px 14px var(--primary-glow);
}

.product-list-view {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.product-list-view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.product-list-view-header h3 {
  font-size: 1.125rem;
  display: flex;
  align-items: center;
  gap: 8px;
}

.count {
  background: var(--primary);
  color: white;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 0.8125rem;
}

.back-btn {
  padding: 8px 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: var(--transition);
}

.back-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.mobile-back-chat {
  display: none;
  width: 100%;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  color: var(--primary);
  align-items: center;
  gap: 8px;
  min-height: 44px;
}

@media (max-width: 767px) {
  .config-container {
    flex: 1 !important;
    width: 100%;
    min-width: 0;
  }

  .config-container.mobile-hidden {
    display: none !important;
  }

  .mobile-back-chat {
    display: inline-flex;
  }

  .config-header {
    padding: 12px 14px;
  }

  .scheme-name {
    font-size: 0.875rem;
    line-height: 1.35;
  }

  .tabs {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    flex-wrap: nowrap;
    padding: 0 8px;
    scrollbar-width: none;
  }

  .tabs::-webkit-scrollbar {
    display: none;
  }

  .tab {
    flex-shrink: 0;
    padding: 12px 14px;
    font-size: 0.8125rem;
  }

  .form-area {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
    padding: 12px 10px 16px;
  }

  .config-header,
  .tabs,
  .config-footer {
    flex-shrink: 0;
  }

  .config-footer {
    flex-direction: column;
    align-items: stretch;
    padding: 12px 14px calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .footer-left {
    flex-direction: column;
    width: 100%;
  }

  .footer-btn,
  .btn-submit {
    width: 100%;
    justify-content: center;
    min-height: 44px;
  }

  .product-list-view {
    padding: 14px 12px;
  }

  .product-list-view-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .back-btn {
    width: 100%;
    justify-content: center;
    min-height: 44px;
  }
}
</style>