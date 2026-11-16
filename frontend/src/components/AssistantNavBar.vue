<template>
  <header class="assistant-nav">
    <div class="nav-brand">
      <div class="brand">PRODUCT WORKBENCH</div>
      <div class="title">{{ title }}</div>
    </div>

    <div class="assistant-switcher">
      <button
        class="switch-item"
        :class="{ active: !activeAssistant }"
        @click="onSwitch(null)"
      >
        工作台
      </button>
      <span class="switch-sep" />
      <button
        v-for="a in assistants"
        :key="a.key"
        class="switch-item"
        :class="{ active: activeAssistant === a.key }"
        :style="activeAssistant === a.key ? { '--sw-accent': a.color, '--sw-bg': a.bg } : null"
        @click="onSwitch(a.key)"
      >
        {{ a.name }}
      </button>
    </div>

    <div class="nav-right">
      <div v-if="$slots.actions" class="nav-actions">
        <slot name="actions" />
      </div>
      <template v-else>
        <MyDrafts :open-signal="openDraftSignal" @open-draft="$emit('open-draft', $event)" />
        <WorkOrderCenter @open-product-metrics="$emit('open-product-metrics', $event)" />
        <NotificationButton @open-approval="$emit('open-approval', $event)" />
        <UserMenu />
      </template>
    </div>
  </header>
</template>

<script setup>
import UserMenu from './UserMenu.vue'
import NotificationButton from './NotificationButton.vue'
import WorkOrderCenter from './WorkOrderCenter.vue'
import MyDrafts from './MyDrafts.vue'
import { assistants } from '../config/assistantModes.js'
import { useWorkbenchStore } from '../stores/workbench.js'
import { storeToRefs } from 'pinia'

defineProps({
  title: { type: String, default: '' },
  openDraftSignal: { type: Number, default: 0 },
})

defineEmits(['open-approval', 'open-product-metrics', 'open-draft'])

const workbenchStore = useWorkbenchStore()
const { activeAssistant } = storeToRefs(workbenchStore)

function onSwitch(key) {
  if (key === null) {
    workbenchStore.clearAssistant()
  } else {
    workbenchStore.selectAssistant(key)
  }
}
</script>

<style scoped>
.assistant-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 28px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.nav-brand { min-width: 0; flex-shrink: 0; }
.brand {
  font-size: 12px;
  color: #0f766e;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 2px;
}

.assistant-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: #f1f5f9;
  border-radius: 12px;
}
.switch-item {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 9px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
  white-space: nowrap;
}
.switch-item:hover { color: #0f172a; }
.switch-item.active {
  background: var(--sw-bg, #ffffff);
  color: var(--sw-accent, #0f172a);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}
.switch-sep {
  width: 1px;
  height: 16px;
  background: #cbd5e1;
  margin: 0 2px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
