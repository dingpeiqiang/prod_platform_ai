<script setup>
defineProps({
  pane: { type: String, required: true },
  productLabel: { type: String, default: '未选择商品' },
})
defineEmits(['switch'])
</script>

<template>
  <nav class="mobile-pane-bar" role="tablist" aria-label="对话与配置切换">
    <button
      type="button"
      role="tab"
      class="pane-btn"
      :class="{ active: pane === 'chat' }"
      :aria-selected="pane === 'chat'"
      @click="$emit('switch', 'chat')"
    >
      <i class="fa-solid fa-comments" />
      <span>对话</span>
    </button>
    <button
      type="button"
      role="tab"
      class="pane-btn"
      :class="{ active: pane === 'config' }"
      :aria-selected="pane === 'config'"
      @click="$emit('switch', 'config')"
    >
      <i class="fa-solid fa-sliders" />
      <span class="pane-label">配置</span>
      <span class="pane-sub" :title="productLabel">{{ productLabel }}</span>
    </button>
  </nav>
</template>

<style scoped>
.mobile-pane-bar {
  display: none;
  flex-shrink: 0;
  background: var(--surface);
  border-top: 1px solid var(--border-color);
  padding: 6px 12px;
  padding-bottom: calc(6px + env(safe-area-inset-bottom, 0px));
  gap: 8px;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
}

@media (max-width: 767px) {
  .mobile-pane-bar {
    display: flex;
  }
}

.pane-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 8px 6px;
  min-height: 48px;
  border: none;
  background: var(--surface-muted);
  border-radius: var(--radius);
  font-size: 12px;
  color: var(--text-secondary);
  transition: var(--transition);
}

.pane-btn i {
  font-size: 18px;
}

.pane-btn.active {
  background: var(--primary-light);
  color: var(--primary-color);
  font-weight: 600;
}

.pane-sub {
  font-size: 10px;
  font-weight: 400;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: inherit;
  opacity: 0.85;
}

.pane-btn:not(.active) .pane-sub {
  display: none;
}
</style>