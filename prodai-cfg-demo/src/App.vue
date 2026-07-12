<script setup>
import { ref, computed } from 'vue'
import AppHeader from './components/AppHeader.vue'
import ChatPanel from './components/ChatPanel.vue'
import ConfigPanel from './components/ConfigPanel.vue'
import MobilePaneBar from './components/MobilePaneBar.vue'
import SlidePanel from './components/SlidePanel.vue'
import { useMobileLayout } from './composables/useMobileLayout.js'
import ProductListItems from './components/ProductListItems.vue'
import { useAppState } from './composables/useAppState.js'
import { mockProducts } from './data/mockData.js'

const fileInput = ref(null)

const {
  mode,
  products,
  currentProductId,
  currentProduct,
  isModified,
  auditStatus,
  currentSkill,
  messages,
  chatInput,
  showProductPanel,
  showAuditPanel,
  showSubmitModal,
  toastMessage,
  toastVisible,
  activeTab,
  showProductListView,
  formData,
  modifiedFields,
  auditContentHtml,
  SKILL_CONFIG,
  inputPlaceholder,
  handleCardClick,
  removeSkill,
  sendMessage,
  prepareProduct,
  handleFileUpload,
  selectProduct,
  copyProduct,
  deleteProduct,
  showProductEditor,
  markFieldModified,
  saveDraft,
  runAudit,
  hideAuditPanel,
  confirmSubmit,
  showToast,
} = useAppState()

const { isMobile, mobilePane, showChatOnMobile, showConfigOnMobile } = useMobileLayout(mode)

const mobileProductLabel = computed(() => {
  const name = currentProduct.value?.name
  if (!name) return '未选择'
  return name.length > 8 ? `${name.slice(0, 8)}…` : name
})

function onMobilePaneSwitch(pane) {
  if (pane === 'chat') showChatOnMobile()
  else showConfigOnMobile()
}

function onFilePick() {
  fileInput.value?.click()
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) handleFileUpload(file)
  e.target.value = ''
}

function onViewMock(index) {
  showToast(`查看商品：${mockProducts[index].name}`)
}

function onSelectFromList(id) {
  selectProduct(id)
  showProductListView.value = false
  activeTab.value = 'base'
}

function onEditFromList(id) {
  showProductEditor(id)
  activeTab.value = 'base'
  showConfigOnMobile()
}

function onAuditContentClick(e) {
  if (e.target.closest('.audit-close-btn')) hideAuditPanel()
}
</script>

<template>
  <AppHeader :product-count="products.length" @open-product-list="showProductPanel = true" />

  <main
    class="main-container"
    :class="{
      'is-mobile': isMobile,
      'pane-chat': isMobile && mobilePane === 'chat',
      'pane-config': isMobile && mobilePane === 'config',
      'has-split': mode === 'split',
    }"
  >
    <ChatPanel
      class="main-chat"
      :mode="mode"
      :mobile-hidden="isMobile && mobilePane === 'config'"
      :messages="messages"
      v-model:chat-input="chatInput"
      :current-skill="currentSkill"
      :skill-config="SKILL_CONFIG"
      :input-placeholder="inputPlaceholder"
      @card-click="handleCardClick"
      @remove-skill="removeSkill"
      @send="sendMessage"
      @file-upload="onFilePick"
      @prepare-product="
        (i) => {
          prepareProduct(i)
          showConfigOnMobile()
        }
      "
      @view-product="onViewMock"
    />

    <ConfigPanel
      class="main-config"
      :visible="mode === 'split'"
      :mobile-hidden="isMobile && mobilePane === 'chat'"
      :is-mobile="isMobile"
      :current-product="currentProduct"
      :audit-status="auditStatus"
      :is-modified="isModified"
      v-model:active-tab="activeTab"
      :show-product-list-view="showProductListView"
      :products="products"
      :current-product-id="currentProductId"
      :form-data="formData"
      :modified-fields="modifiedFields"
      @back-from-list="showProductListView = false"
      @select-product="onSelectFromList"
      @copy="copyProduct"
      @edit="onEditFromList"
      @delete="deleteProduct"
      @field-change="markFieldModified"
      @save-draft="saveDraft"
      @audit="runAudit"
      @submit="showSubmitModal = true"
      @back-to-chat="showChatOnMobile"
    />
  </main>

  <MobilePaneBar
    v-if="isMobile && mode === 'split'"
    :pane="mobilePane"
    :product-label="mobileProductLabel"
    @switch="onMobilePaneSwitch"
  />

  <input
    ref="fileInput"
    type="file"
    accept=".docx,.pdf,.xlsx,.doc"
    class="sr-only"
    @change="onFileChange"
  />

  <SlidePanel
    :show="showProductPanel"
    title="商品列表"
    icon="fa-list"
    :count="products.length"
    :full-screen="isMobile"
    @close="showProductPanel = false"
  >
    <ProductListItems
      :products="products"
      :current-product-id="currentProductId"
      @select="
        (id) => {
          selectProduct(id)
          showProductPanel = false
          showConfigOnMobile()
        }
      "
      @copy="copyProduct"
      @edit="showProductEditor"
      @delete="deleteProduct"
    />
  </SlidePanel>

  <SlidePanel
    :show="showAuditPanel"
    title="智能稽核"
    icon="fa-clipboard-list"
    :width="isMobile ? '100%' : '400px'"
    :full-screen="isMobile"
    @close="hideAuditPanel"
  >
    <div class="audit-content" v-html="auditContentHtml" @click="onAuditContentClick" />
  </SlidePanel>

  <Teleport to="body">
    <div v-if="showSubmitModal" class="modal show" role="dialog">
      <div class="modal-content">
        <div class="modal-title">
          <i class="fa-solid fa-triangle-exclamation" />
          提交确认
        </div>
        <div class="modal-body">
          <p>提交配置前，将进行智能稽核检查：</p>
          <ul>
            <li>基础信息完整性校验</li>
            <li>必填字段检查</li>
            <li>业务规则校验</li>
            <li>短信模板合规性检查</li>
          </ul>
          <p>是否立即进行智能稽核？</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="modal-btn cancel" @click="showSubmitModal = false">取消</button>
          <button type="button" class="modal-btn confirm" @click="confirmSubmit">
            <i class="fa-solid fa-check" /> 开始稽核
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <div class="toast" :class="{ show: toastVisible }">{{ toastMessage }}</div>
</template>

<style scoped>
.main-container {
  display: flex;
  flex: 1;
  min-height: 0;
}

@media (max-width: 767px) {
  .main-container.is-mobile {
    flex-direction: column;
    min-height: 0;
  }

  .main-container.is-mobile.has-split .main-chat,
  .main-container.is-mobile.has-split .main-config {
    flex: 1;
    min-height: 0;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.audit-content :deep(.audit-item) {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}

.audit-content :deep(.audit-item.error) {
  background: var(--error-bg);
}
.audit-content :deep(.audit-item.warning) {
  background: var(--warning-bg);
}
.audit-content :deep(.audit-item.success) {
  background: var(--success-bg);
}

.audit-content :deep(.audit-icon) {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.audit-content :deep(.audit-item.error .audit-icon) {
  background: var(--error);
}
.audit-content :deep(.audit-item.success .audit-icon) {
  background: var(--success);
}

.audit-content :deep(.audit-title) {
  font-weight: 600;
  margin-bottom: 4px;
  font-size: 0.875rem;
}

.audit-content :deep(.audit-desc) {
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.audit-content :deep(.audit-progress) {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-secondary);
}

.audit-content :deep(.audit-progress i) {
  font-size: 2.5rem;
  color: var(--primary);
  margin-bottom: 16px;
}

.audit-content :deep(.audit-done) {
  text-align: center;
  margin-top: 16px;
  color: var(--success);
  font-size: 0.875rem;
}

.loading-dots {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin: 16px 0;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: var(--primary);
  border-radius: 50%;
  animation: bounce-dot 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}
.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

.modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-content {
  background: var(--surface);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 420px;
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.modal-title {
  padding: 16px 20px;
  background: var(--surface-elevated);
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-title i {
  color: var(--warning);
}

.modal-body {
  padding: 20px;
  font-size: 0.9375rem;
  line-height: 1.6;
}

.modal-body ul {
  margin: 12px 0;
  padding-left: 1.25rem;
  color: var(--text-secondary);
}

.modal-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.modal-btn {
  padding: 10px 18px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  transition: var(--transition);
}

.modal-btn.cancel {
  background: var(--surface);
  border: 1px solid var(--border);
}

.modal-btn.confirm {
  background: var(--primary);
  border: none;
  color: white;
}

.toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%) translateY(80px);
  background: var(--text);
  color: white;
  padding: 12px 24px;
  border-radius: var(--radius-md);
  z-index: 1000;
  opacity: 0;
  transition: var(--transition);
  font-size: 0.875rem;
  box-shadow: var(--shadow-lg);
}

.toast.show {
  transform: translateX(-50%) translateY(0);
  opacity: 1;
}

@media (max-width: 767px) {
  .modal {
    padding: 12px;
    align-items: flex-end;
  }

  .modal-content {
    max-width: 100%;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
    margin-bottom: env(safe-area-inset-bottom, 0px);
  }

  .toast {
    bottom: calc(72px + env(safe-area-inset-bottom, 0px));
    max-width: calc(100vw - 32px);
    text-align: center;
  }
}
</style>