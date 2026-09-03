<template>
  <teleport to="body">
    <transition name="drawer-slide">
      <div v-if="modelValue" class="drafts-mask" @click.self="$emit('update:modelValue', false)">
        <div class="drafts-panel">
          <header class="drafts-head">
            <span class="drafts-title">我的草稿</span>
            <button class="drafts-close" @click="$emit('update:modelValue', false)">✕</button>
          </header>
          <div class="drafts-body">
            <div v-if="!drafts.length" class="drafts-empty">
              暂无未提交的草稿。完成对话配置后，草稿会自动出现在这里。
            </div>
            <div v-for="(item, i) in drafts" :key="item.id" class="draft-card">
              <div class="draft-card-head">
                <span class="draft-index">{{ i + 1 }}</span>
                <span class="draft-name" :title="item.name">{{ item.name }}</span>
                <span v-if="item.workOrderId" class="draft-wo">{{ item.workOrderId }}</span>
              </div>
              <div class="draft-desc" :title="item.desc">{{ item.desc || '—' }}</div>
              <div class="draft-actions">
                <button class="draft-btn primary" @click="$emit('open-draft', item)">进入工作台</button>
                <button class="draft-btn danger" @click="$emit('delete-draft', item)">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { computed } from 'vue'
import { useProductConfig } from '../../composables/useProductConfig.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'open-draft', 'delete-draft'])

const productConfig = useProductConfig()

/** 未提交草稿（status=draft），对齐原型「我的草稿 = 未提交的配置」语义 */
const drafts = computed(() =>
  (productConfig.products.value || []).filter((p) => p.status !== 'submitted'),
)
</script>

<style scoped>
.drafts-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  justify-content: flex-end;
}
.drafts-panel {
  width: min(420px, 92vw);
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 32px rgba(15, 23, 42, 0.18);
}
.drafts-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}
.drafts-title { font-size: 15px; font-weight: 700; color: #0f172a; }
.drafts-close {
  border: none; background: transparent; color: #64748b;
  font-size: 14px; cursor: pointer; padding: 4px 8px; border-radius: 6px;
}
.drafts-close:hover { background: #f1f5f9; color: #0f172a; }

.drafts-body { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 12px; }
.drafts-empty { font-size: 13px; color: #94a3b8; text-align: center; padding: 48px 0; line-height: 1.8; }

.draft-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.draft-card:hover { border-color: #93c5fd; }
.draft-card-head { display: flex; align-items: center; gap: 8px; min-width: 0; }
.draft-index {
  width: 20px; height: 20px; border-radius: 6px;
  background: #eff6ff; color: #2563eb;
  font-size: 11px; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.draft-name { font-size: 13px; font-weight: 600; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.draft-wo { font-size: 10px; color: #64748b; background: #f1f5f9; padding: 2px 6px; border-radius: 999px; flex-shrink: 0; }
.draft-desc {
  font-size: 12px; color: #64748b;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.draft-actions { display: flex; gap: 8px; }
.draft-btn {
  flex: 1;
  padding: 7px 0;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
}
.draft-btn.primary { background: #2563eb; color: #fff; }
.draft-btn.primary:hover { background: #1d4ed8; }
.draft-btn.danger { background: #fff; border-color: #fecaca; color: #dc2626; }
.draft-btn.danger:hover { background: #fef2f2; }

.drawer-slide-enter-active, .drawer-slide-leave-active { transition: opacity 0.2s ease; }
.drawer-slide-enter-active .drafts-panel, .drawer-slide-leave-active .drafts-panel { transition: transform 0.24s ease; }
.drawer-slide-enter-from, .drawer-slide-leave-to { opacity: 0; }
.drawer-slide-enter-from .drafts-panel, .drawer-slide-leave-to .drafts-panel { transform: translateX(100%); }
</style>
