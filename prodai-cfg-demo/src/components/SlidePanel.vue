<script setup>
const props = defineProps({
  show: Boolean,
  title: String,
  icon: { type: String, default: 'fa-list' },
  count: { type: Number, default: null },
  width: { type: String, default: '500px' },
  fullScreen: { type: Boolean, default: false },
})
defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <div class="overlay" :class="{ show }" @click="$emit('close')" />
    <aside
      class="panel"
      :class="{ show, 'panel-full': fullScreen }"
      :style="fullScreen ? undefined : { width: props.width }"
      role="dialog"
      :aria-hidden="!show"
    >
      <header class="panel-header">
        <h3>
          <i :class="`fa-solid ${icon}`" />
          {{ title }}
          <span v-if="count !== null" class="count">{{ count }}</span>
        </h3>
        <button type="button" class="close" aria-label="关闭" @click="$emit('close')">
          <i class="fa-solid fa-xmark" />
        </button>
      </header>
      <div class="panel-body">
        <slot />
      </div>
    </aside>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(2px);
  z-index: 99;
  opacity: 0;
  visibility: hidden;
  transition: var(--transition);
}

.overlay.show {
  opacity: 1;
  visibility: visible;
}

.panel {
  position: fixed;
  right: 0;
  top: 0;
  width: min(480px, 100vw);
  height: 100%;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

.panel.show {
  transform: translateX(0);
}

.panel.panel-full {
  width: 100% !important;
  max-width: 100vw;
  height: 100%;
  height: 100dvh;
  padding-top: env(safe-area-inset-top, 0px);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h3 {
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 8px;
}

.count {
  background: var(--primary);
  color: white;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.75rem;
}

.close {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--surface-muted);
  border-radius: var(--radius-sm);
  font-size: 1rem;
  color: var(--text-secondary);
  transition: var(--transition);
}

.close:hover {
  background: var(--border);
  color: var(--text);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
</style>