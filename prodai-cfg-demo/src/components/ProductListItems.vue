<script setup>
defineProps({
  products: { type: Array, default: () => [] },
  currentProductId: { type: String, default: null },
  compact: { type: Boolean, default: false },
})

defineEmits(['select', 'copy', 'edit', 'delete'])

function statusMeta(p) {
  const statusText =
    p.status === 'draft' ? '草稿' : p.status === 'submitted' ? '待审批' : '已完成'
  const statusClass =
    p.status === 'draft'
      ? 'status-draft'
      : p.status === 'submitted'
        ? 'status-approval'
        : 'status-submitted'
  const auditText =
    p.auditStatus === 'pass' ? '已通过' : p.auditStatus === 'fail' ? '不通过' : '未稽核'
  const auditClass =
    p.auditStatus === 'pass'
      ? 'status-pass'
      : p.auditStatus === 'fail'
        ? 'status-fail'
        : 'status-pending'
  return { statusText, statusClass, auditText, auditClass }
}
</script>

<template>
  <div v-if="!products.length" class="empty-state">
    <i class="fa-solid fa-inbox" />
    <p>暂无商品，请通过左侧功能创建</p>
  </div>
  <div v-else class="list-scroll">
    <article
      v-for="p in products"
      :key="p.id"
      class="list-item"
      :class="{ active: p.id === currentProductId }"
      @click="$emit('select', p.id)"
    >
      <div class="list-item-header">
        <span class="list-item-name">{{ p.name }}</span>
      </div>
      <p class="list-item-desc">{{ p.desc }}</p>
      <div class="list-item-tags">
        <span class="status-tag" :class="statusMeta(p).statusClass">{{ statusMeta(p).statusText }}</span>
        <span class="status-tag" :class="statusMeta(p).auditClass">{{ statusMeta(p).auditText }}</span>
      </div>
      <div class="list-item-actions">
        <button type="button" class="list-action-btn copy" @click.stop="$emit('copy', p.id)">
          <i class="fa-solid fa-copy" /> 复制
        </button>
        <button type="button" class="list-action-btn edit" @click.stop="$emit('edit', p.id)">
          <i class="fa-solid fa-pen" /> 编辑
        </button>
        <button type="button" class="list-action-btn delete" @click.stop="$emit('delete', p.id)">
          <i class="fa-solid fa-trash" /> 删除
        </button>
      </div>
    </article>
  </div>
</template>

<style scoped>
.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: var(--text-muted);
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 16px;
  opacity: 0.5;
}

.list-scroll {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-item {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px;
  cursor: pointer;
  transition: var(--transition);
}

.list-item:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-sm);
}

.list-item.active {
  border-color: var(--primary);
  background: var(--primary-muted);
}

.list-item-name {
  font-weight: 600;
  font-size: 0.875rem;
}

.list-item-desc {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin: 8px 0 10px;
  line-height: 1.45;
}

.list-item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.status-tag {
  font-size: 0.6875rem;
  padding: 3px 8px;
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
.status-draft {
  background: var(--warning-bg);
  color: var(--warning);
}
.status-submitted {
  background: var(--success-bg);
  color: var(--success);
}
.status-approval {
  background: var(--primary-muted);
  color: var(--primary);
}

.list-item-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.list-action-btn {
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  border: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: var(--transition);
}

.list-action-btn.copy {
  background: var(--primary-muted);
  color: var(--primary);
}
.list-action-btn.copy:hover {
  background: var(--primary);
  color: white;
}
.list-action-btn.edit {
  background: var(--warning-bg);
  color: var(--warning);
}
.list-action-btn.edit:hover {
  background: var(--warning);
  color: white;
}
.list-action-btn.delete {
  background: var(--error-bg);
  color: var(--error);
}
.list-action-btn.delete:hover {
  background: var(--error);
  color: white;
}

@media (max-width: 767px) {
  .list-item {
    padding: 14px 12px;
  }

  .list-item-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .list-action-btn {
    justify-content: center;
    min-height: 44px;
    font-size: 0.8125rem;
    padding: 10px 12px;
  }
}
</style>