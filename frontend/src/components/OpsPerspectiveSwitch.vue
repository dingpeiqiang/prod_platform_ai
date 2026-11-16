<template>
  <div class="ops-persp-anchor" role="group" aria-label="运营视角切换" :class="{ floating: floating }">
    <span class="persp-label">运营视角</span>
    <div class="persp-seg">
      <button
        v-for="p in perspectives"
        :key="p.key"
        type="button"
        class="persp-btn"
        :class="{ active: p.key === opsPerspective }"
        :title="p.desc"
        @click="perspectiveStore.setOpsPerspective(p.key)"
      >
        <svg v-if="p.key === 'platform'" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>
        </svg>
        <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
        </svg>
        {{ p.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { usePerspectiveStore, OPS_PERSPECTIVES } from '../stores/perspective.js'

const perspectiveStore = usePerspectiveStore()
const { opsPerspective } = storeToRefs(perspectiveStore)
const perspectives = OPS_PERSPECTIVES

// floating: 是否以浮动胶囊形式贴于主区域右上角（默认 true，由外部控制）
defineProps({
  floating: { type: Boolean, default: true },
})
</script>

<style scoped>
.ops-persp-anchor.floating {
  position: absolute;
  top: 12px;
  right: 16px;
  z-index: 30;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.ops-persp-anchor:not(.floating) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.persp-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  white-space: nowrap;
}
.persp-seg {
  display: inline-flex;
  padding: 3px;
  gap: 2px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
}
.persp-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.18s, color 0.18s, box-shadow 0.18s;
}
.persp-btn:hover { color: #0f766e; }
.persp-btn.active {
  background: #fff;
  color: #0f766e;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}
.persp-btn:focus-visible {
  outline: 2px solid #0f766e;
  outline-offset: 2px;
}
</style>
