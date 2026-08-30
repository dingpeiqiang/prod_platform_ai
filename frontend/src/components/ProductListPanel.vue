<template>
  <ElDrawer
    v-model="visible"
    title="已配置商品"
    direction="rtl"
    size="480px"
    :with-header="true"
  >
    <template #header>
      <div class="panel-header">
        <h3>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="8" y1="6" x2="21" y2="6"/>
            <line x1="8" y1="12" x2="21" y2="12"/>
            <line x1="8" y1="18" x2="21" y2="18"/>
            <line x1="3" y1="6" x2="3.01" y2="6"/>
            <line x1="3" y1="12" x2="3.01" y2="12"/>
            <line x1="3" y1="18" x2="3.01" y2="18"/>
          </svg>
          已配置商品
          <span class="count-badge">{{ products.length }}</span>
        </h3>
      </div>
    </template>

    <div v-if="!products.length" class="empty-state">
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
      </svg>
      <p>暂无已配置商品，请先通过智聊·对话配置生成草稿</p>
    </div>

    <div v-else class="product-list">
      <article
        v-for="p in products"
        :key="p.id"
        class="product-item"
        :class="{ active: p.id === currentProductId }"
        @click="$emit('select', p.id)"
      >
        <div class="product-item-header">
          <span class="product-name">{{ p.name }}</span>
        </div>
        <p class="product-desc">{{ p.desc }}</p>
        <div class="product-tags">
          <span class="status-tag" :class="statusMeta(p).statusClass">{{ statusMeta(p).statusText }}</span>
          <span class="status-tag" :class="statusMeta(p).auditClass">{{ statusMeta(p).auditText }}</span>
        </div>
        <div class="product-actions">
          <button type="button" class="action-btn preview" @click.stop="$emit('preview', p.id)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            预览
          </button>
          <button type="button" class="action-btn copy" @click.stop="$emit('copy', p.id)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            复制
          </button>
          <button type="button" class="action-btn edit" @click.stop="$emit('edit', p.id)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑
          </button>
          <button type="button" class="action-btn delete" @click.stop="$emit('delete', p.id)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
            删除
          </button>
        </div>
      </article>
    </div>
  </ElDrawer>
</template>

<script setup>
import { computed } from 'vue'
import { ElDrawer } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  products: { type: Array, default: () => [] },
  currentProductId: { type: String, default: null },
})

const emit = defineEmits(['update:modelValue', 'select', 'preview', 'copy', 'edit', 'delete'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function statusMeta(p) {
  const statusText = p.status === 'draft' ? '草稿' : p.status === 'submitted' ? '待审批' : '已完成'
  const statusClass =
    p.status === 'draft'
      ? 'status-draft'
      : p.status === 'submitted'
        ? 'status-approval'
        : 'status-submitted'
  const auditText = p.auditStatus === 'pass' ? '已通过' : p.auditStatus === 'fail' ? '不通过' : '未稽核'
  const auditClass =
    p.auditStatus === 'pass'
      ? 'status-pass'
      : p.auditStatus === 'fail'
        ? 'status-fail'
        : 'status-pending'
  return { statusText, statusClass, auditText, auditClass }
}
</script>

<style scoped>
.panel-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.count-badge {
  background: #3b82f6;
  color: white;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: var(--text-tertiary);
}

.empty-state svg {
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-state p {
  font-size: 14px;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-item {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-default, #e2e8f0);
  border-radius: 12px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.product-item:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
}

.product-item.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.product-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary, #1e293b);
}

.product-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin: 8px 0 10px;
  line-height: 1.45;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.status-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.status-pending {
  background: #f1f5f9;
  color: #64748b;
}

.status-pass {
  background: #f6ffed;
  color: #52c41a;
}

.status-fail {
  background: #fff1f0;
  color: #ff4d4f;
}

.status-draft {
  background: #fffbe6;
  color: #faad14;
}

.status-submitted {
  background: #f6ffed;
  color: #52c41a;
}

.status-approval {
  background: #dbeafe;
  color: #3b82f6;
}

.product-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.action-btn {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.preview {
  background: #d1fae5;
  color: #059669;
}

.action-btn.preview:hover {
  background: #059669;
  color: white;
}

.action-btn.copy {
  background: #dbeafe;
  color: #3b82f6;
}

.action-btn.copy:hover {
  background: #3b82f6;
  color: white;
}

.action-btn.edit {
  background: #fffbe6;
  color: #faad14;
}

.action-btn.edit:hover {
  background: #faad14;
  color: white;
}

.action-btn.delete {
  background: #fff1f0;
  color: #ff4d4f;
}

.action-btn.delete:hover {
  background: #ff4d4f;
  color: white;
}

@media (max-width: 768px) {
  .product-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .action-btn {
    justify-content: center;
    min-height: 36px;
    font-size: 13px;
    padding: 8px 12px;
  }
}
</style>
