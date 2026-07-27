<template>
  <div class="assistant-page assistant-workbench">
    <AssistantNavBar :mode="mode" :title="config.navTitle">
      <template v-if="$slots['nav-actions']" #actions>
        <slot name="nav-actions" />
      </template>
    </AssistantNavBar>

    <div class="workbench-body">
      <aside class="workbench-side">
        <div class="side-section side-session">
          <button class="new-session-btn" @click="$emit('new-session')" :disabled="streaming">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新建会话
          </button>

          <div class="side-title history-title">
            <span>历史对话</span>
            <button class="refresh-btn" @click="$emit('refresh-sessions')" :disabled="sessionsLoading" title="刷新">
              <svg :class="{ spin: sessionsLoading }" width="12" height="12" viewBox="0 0 24 24"
                   fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
            </button>
          </div>

          <div class="history-list">
            <div v-if="sessionsLoading" class="history-empty">加载中...</div>
            <div v-else-if="!sessions.length" class="history-empty">暂无历史对话</div>
            <template v-else>
              <button
                v-for="s in sessions.slice(0, 8)"
                :key="s.session_id"
                class="side-btn history-btn"
                @click="$emit('switch-session', s.session_id)"
              >
                <span class="btn-label history-label">{{ s.title || '新对话' }}</span>
                <span class="btn-scene">{{ formatSessionTime(s.updated_at) }}</span>
              </button>
            </template>
          </div>
        </div>

        <div class="side-section">
          <div class="side-title">快捷场景</div>
          <button
            v-for="item in config.sceneShortcuts"
            :key="item.label"
            class="side-btn"
            @click="$emit('shortcut', item)"
          >
            <span class="btn-label">{{ item.label }}</span>
            <span class="btn-scene">{{ item.desc || item.scene }}</span>
          </button>
        </div>

        <div class="side-section side-tips">
          <div class="side-title">使用提示</div>
          <p v-for="(tip, i) in config.tips" :key="i" class="tip-text">{{ tip }}</p>
        </div>
      </aside>

      <main class="workbench-main">
        <slot />

        <div class="workbench-input">
          <ChatInput
            :modelValue="inputText"
            :disabled="streaming"
            :placeholder="config.inputPlaceholder"
            :assistantMode="mode"
            @update:modelValue="$emit('update:inputText', $event)"
            @send="$emit('send', $event)"
            @stop="$emit('stop')"
            @quick-action="$emit('quick-action', $event)"
          />
        </div>
      </main>

      <aside v-if="$slots.right" class="workbench-right">
        <slot name="right" />
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AssistantNavBar from './AssistantNavBar.vue'
import ChatInput from './ChatInput.vue'
import { assistantModes } from '../config/assistantModes.js'

const props = defineProps({
  mode:      { type: String, required: true },
  streaming: { type: Boolean, default: false },
  inputText: { type: String, default: '' },
  sessions:  { type: Array,   default: () => [] },
  sessionsLoading: { type: Boolean, default: false },
})

defineEmits([
  'update:inputText',
  'send',
  'stop',
  'new-session',
  'refresh-sessions',
  'switch-session',
  'shortcut',
  'quick-action',
])

const config = computed(() => assistantModes[props.mode] || assistantModes.rd)

const formatSessionTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<style scoped>
.assistant-workbench { display: flex; flex-direction: column; height: 100%; }
.workbench-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }
.workbench-side { width: 240px; flex-shrink: 0; border-right: 1px solid #e5e7eb; padding: 16px; overflow-y: auto; background: #fafafa; display: flex; flex-direction: column; gap: 20px; }
.workbench-right { flex-shrink: 0; display: flex; min-height: 0; overflow: hidden; height: 100%; }
.workbench-right :deep(.form-panel),
.workbench-right :deep(.ops-panel) { height: 100%; }
.side-section { display: flex; flex-direction: column; gap: 10px; }
.side-session { flex-shrink: 0; }
.side-title { font-weight: 700; color: #334155; font-size: 13px; }
.history-title { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
.side-btn { border: 1px solid #e2e8f0; background: #fff; padding: 10px 12px; border-radius: 12px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; transition: border-color 0.15s; }
.side-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.new-session-btn { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; padding: 10px 12px; border-radius: 12px; border: 1px solid #93c5fd; background: #eff6ff; color: #2563eb; font-weight: 600; font-size: 13px; cursor: pointer; transition: all 0.15s; }
.new-session-btn:hover { background: #dbeafe; border-color: #60a5fa; }
.new-session-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.history-list { display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow-y: auto; }
.side-btn.history-btn { padding: 8px 10px; }
.btn-label { font-weight: 600; color: #0f172a; font-size: 13px; }
.btn-label.history-label { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; width: 100%; display: block; }
.btn-scene { font-size: 11px; color: #64748b; line-height: 1.4; }
.side-tips { border-top: 1px solid #e5e7eb; padding-top: 14px; margin-top: auto; }
.tip-text { font-size: 12px; color: #94a3b8; line-height: 1.6; }
.refresh-btn { background: transparent; border: none; color: #94a3b8; cursor: pointer; padding: 2px; border-radius: 4px; display: flex; align-items: center; }
.refresh-btn:hover { background: #e2e8f0; color: #475569; }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.history-empty { font-size: 12px; color: #94a3b8; text-align: center; padding: 8px 0; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
.workbench-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
</style>