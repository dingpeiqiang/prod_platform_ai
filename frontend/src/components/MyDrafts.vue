<template>
  <div class="mydraft-wrap">
    <!-- 右上角「我的草稿」图标（放在「我的产品」左侧） -->
    <button
      class="mydraft-trigger"
      :class="{ active: open }"
      type="button"
      :title="`我的草稿（${total} 条）`"
      @click="open = true"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20h9"/>
        <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
        <line x1="8" y1="20" x2="12" y2="16"/>
      </svg>
      <span v-if="total > 0" class="badge">{{ total > 99 ? '99+' : total }}</span>
    </button>

    <Teleport to="body">
      <Transition name="md-mask-fade">
        <div v-if="open" class="md-mask" @click.self="open = false"></div>
      </Transition>
      <Transition name="md-slide">
        <aside v-if="open" class="md-drawer">
          <header class="md-header">
            <div class="md-header-left">
              <span class="md-title">我的草稿</span>
              <span class="md-sub">草稿状态的未提交套餐配置 · 点击产品可继续配置</span>
            </div>
            <button class="md-close" @click="open = false" title="关闭">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </header>

          <div class="md-tip">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            点击产品可进入对应配置工作台继续完善，提交后自动跳转到下一个草稿。
          </div>

          <div class="md-body">
            <!-- 空状态 -->
            <div v-if="!draftList.length" class="md-empty">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 20h9"/>
                <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>
                <line x1="8" y1="20" x2="12" y2="16"/>
              </svg>
              <p>暂无草稿产品</p>
              <span>在「资费配置」场景选择「文件导入批量配置」，上传配置文件后解析出的产品会出现在这里。</span>
            </div>

            <!-- 草稿列表 -->
            <div v-else class="md-list">
              <div
                v-for="(d, idx) in draftList"
                :key="d.id"
                class="md-card"
                :class="{ active: d.active }"
              >
                <div class="md-card-head">
                  <div class="md-card-main">
                    <div class="md-card-title-row">
                      <span class="md-idx">#{{ idx + 1 }}</span>
                      <span class="md-product">{{ d.prodName }}</span>
                      <span class="md-status">草稿</span>
                    </div>
                    <div class="md-woid">工单：{{ d.workOrderId }}</div>
                    <div class="md-summary">{{ d.summary }}</div>
                  </div>
                </div>
                <div class="md-actions">
                  <button class="md-btn primary" @click="enterDraft(d)">
                    <svg v-if="d.active" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                    {{ d.active ? '查看工作台' : '进入工作台' }}
                  </button>
                  <button class="md-btn ghost" @click="removeDraft(d)">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                    删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useDraftStore } from '../stores/draft.js'
import { ElMessage } from 'element-plus'

const draftStore = useDraftStore()
const { draftList, total } = storeToRefs(draftStore)

const open = ref(false)

const props = defineProps({
  openSignal: { type: Number, default: 0 },
})

watch(() => props.openSignal, (v) => {
  if (v > 0) open.value = true
})

const emit = defineEmits(['open-draft'])

// 进入/恢复某草稿产品对应的配置工作台
const enterDraft = (d) => {
  open.value = false
  draftStore.setActive(d.id)
  emit('open-draft', d)
}

const removeDraft = (d) => {
  draftStore.remove(d.id)
  ElMessage.success(`草稿「${d.prodName}」已删除`)
}
</script>

<style scoped>
.mydraft-wrap { display: inline-flex; }

.mydraft-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s, color 0.15s;
}
.mydraft-trigger:hover {
  border-color: #fbbf24;
  background: #fffbeb;
  color: #b45309;
}
.mydraft-trigger.active {
  border-color: #fbbf24;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15);
  color: #d97706;
}
.mydraft-trigger .badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f59e0b;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 999px;
  border: 2px solid #fff;
  line-height: 1;
}

.md-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 90;
}
.md-mask-fade-enter-active, .md-mask-fade-leave-active { transition: opacity 0.2s ease; }
.md-mask-fade-enter-from, .md-mask-fade-leave-to { opacity: 0; }

.md-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 460px;
  max-width: 92vw;
  background: #f8fafc;
  box-shadow: -16px 0 40px rgba(15, 23, 42, 0.18);
  z-index: 100;
  display: flex;
  flex-direction: column;
}
.md-slide-enter-active, .md-slide-leave-active { transition: transform 0.28s ease; }
.md-slide-enter-from, .md-slide-leave-to { transform: translateX(100%); }

.md-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}
.md-title { font-size: 17px; font-weight: 700; color: #0f172a; }
.md-sub { display: block; margin-top: 2px; font-size: 12px; color: #94a3b8; }
.md-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}
.md-close:hover { border-color: #cbd5e1; color: #0f172a; }

.md-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 16px;
  margin: 12px 16px 0;
  border-radius: 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  font-size: 12px;
  line-height: 1.5;
}
.md-tip svg { flex-shrink: 0; margin-top: 1px; }

.md-body { flex: 1; overflow-y: auto; padding: 16px; }
.md-body::-webkit-scrollbar { width: 6px; }
.md-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }

.md-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #cbd5e1;
  gap: 10px;
  text-align: center;
}
.md-empty p { font-size: 14px; font-weight: 600; color: #94a3b8; margin: 0; }
.md-empty span { font-size: 12px; color: #cbd5e1; max-width: 280px; line-height: 1.6; }

.md-list { display: flex; flex-direction: column; gap: 12px; }
.md-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px 16px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.md-card:hover { border-color: #fcd34d; box-shadow: 0 4px 16px rgba(245, 158, 11, 0.1); }
.md-card.active { border-color: #f59e0b; background: #fffbeb; box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.12); }

.md-card-title-row { display: flex; align-items: center; gap: 8px; }
.md-idx {
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
  background: #fef3c7;
  border-radius: 6px;
  padding: 2px 6px;
}
.md-product { font-size: 15px; font-weight: 700; color: #0f172a; }
.md-status {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  background: #fef3c7;
  color: #b45309;
  border: 1px solid #fde68a;
  white-space: nowrap;
}
.md-woid { margin-top: 4px; font-size: 12px; color: #94a3b8; }
.md-summary { margin-top: 4px; font-size: 12px; color: #475569; }

.md-actions { display: flex; gap: 8px; padding-top: 12px; }
.md-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 1;
  justify-content: center;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}
.md-btn:hover { border-color: #cbd5e1; background: #f8fafc; }
.md-btn.primary { background: #fffbeb; border-color: #fcd34d; color: #b45309; }
.md-btn.primary:hover { background: #fef3c7; }
.md-btn.ghost { background: #f8fafc; }
.md-btn.ghost:hover { background: #fee2e2; border-color: #fecaca; color: #dc2626; }
</style>
