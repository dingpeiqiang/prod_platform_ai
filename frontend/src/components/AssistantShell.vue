<template>
  <div class="assistant-page assistant-workbench">
    <AssistantNavBar :title="navTitle" :open-draft-signal="openDraftSignal" @open-approval="$emit('open-approval', $event)" @open-product-metrics="$emit('open-product-metrics', $event)" @open-draft="$emit('open-draft', $event)">
      <template v-if="$slots['nav-actions']" #actions>
        <slot name="nav-actions" />
      </template>
    </AssistantNavBar>

    <div class="workbench-body">
      <aside class="workbench-side" :class="{ collapsed: sideCollapsed }">
        <div class="side-inner">
          <div class="side-section side-session">
            <button class="new-session-btn" @click="$emit('new-session')" :disabled="streaming">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              新建会话
            </button>
          </div>

          <!-- 快捷场景：仅在选中助手时显示 -->
          <div v-if="hasAssistant && sideSceneShortcuts.length" class="side-section">
            <div class="side-title">快捷场景</div>
            <button
              v-for="item in sideSceneShortcuts"
              :key="item.label"
              class="side-btn"
              @click="$emit('shortcut', item)"
            >
              <span class="btn-label">{{ item.label }}</span>
              <span class="btn-scene">{{ item.desc || item.scene }}</span>
            </button>
          </div>

          <div class="side-section side-history">
            <div class="side-title history-title">
              <span>历史对话</span>
              <div class="history-title-actions">
                <button
                  class="refresh-btn"
                  :class="{ active: searchOpen }"
                  @click="toggleSearch"
                  title="检索会话"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24"
                       fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="11" cy="11" r="8"/>
                    <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                </button>
                <button class="refresh-btn" @click="$emit('refresh-sessions')" :disabled="sessionsLoading" title="刷新">
                  <svg :class="{ spin: sessionsLoading }" width="12" height="12" viewBox="0 0 24 24"
                       fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="23 4 23 10 17 10"/>
                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                  </svg>
                </button>
              </div>
            </div>

            <transition name="search-slide">
              <div v-if="searchOpen" class="history-search">
                <svg class="search-icon" width="13" height="13" viewBox="0 0 24 24"
                     fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
                <input
                  ref="searchInputEl"
                  v-model="searchKeyword"
                  class="search-input"
                  type="text"
                  placeholder="搜索会话标题/内容/工单号"
                />
                <button v-if="searchKeyword" class="search-clear" @click="searchKeyword = ''" title="清空">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </transition>

            <div class="history-list">
              <div v-if="sessionsLoading" class="history-empty">加载中...</div>
              <div v-else-if="searchOpen && searchKeyword && !searchResults.length" class="history-empty">
                未匹配到会话
              </div>
              <div v-else-if="!sessions.length" class="history-empty">
                {{ hasAssistant ? '暂无历史对话' : '选择助手后开始对话' }}
              </div>
              <template v-else>
                <div
                  v-for="s in displayedSessions"
                  :key="s.session_id || s.id"
                  class="history-item"
                  :class="{ 'cross-assistant': searchOpen && searchKeyword && s.assistantKey && s.assistantKey !== activeAssistant }"
                >
                  <button
                    class="side-btn history-btn"
                    @click="onPickSession(s)"
                  >
                    <span class="btn-label history-label">{{ s.title || '新对话' }}</span>
                    <span class="btn-scene">
                      <span v-if="s.matchReason" class="match-reason" v-html="s.matchReason"></span>
                      <template v-else>{{ formatSessionTime(s.updated_at) }}</template>
                    </span>
                  </button>
                  <button
                    class="history-del-btn"
                    :title="'删除会话：' + (s.title || '新对话')"
                    @click.stop="onDeleteSession(s)"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </template>
            </div>
          </div>

          <div class="side-section side-tips">
            <div class="side-title tips-title">
              <span>使用提示</span>
              <button
                class="tips-toggle"
                :class="{ active: tipsOpen }"
                :aria-expanded="tipsOpen"
                :title="tipsOpen ? '收起提示' : '展开提示'"
                @click="tipsOpen = !tipsOpen"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
              </button>
            </div>
            <transition name="tips-collapse">
              <div v-show="tipsOpen" class="tips-body">
                <p v-for="(tip, i) in tips" :key="i" class="tip-text">{{ tip }}</p>
              </div>
            </transition>
          </div>
        </div>

        <button
          class="side-collapse-btn"
          :class="{ collapsed: sideCollapsed }"
          :title="sideCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :aria-expanded="!sideCollapsed"
          @click="sideCollapsed = !sideCollapsed"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
      </aside>

      <main class="workbench-main">
        <OpsPerspectiveSwitch v-if="mode === 'ops'" />
        <slot />

        <div class="workbench-input">
          <slot name="input-bar" />
          <ChatInput
            :modelValue="inputText"
            :disabled="streaming"
            :placeholder="inputPlaceholder"
            :assistantMode="mode"
            :activeScene="activeScene"
            :skillSignal="skillSignal"
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
import { computed, ref, nextTick } from 'vue'
import AssistantNavBar from './AssistantNavBar.vue'
import ChatInput from './ChatInput.vue'
import OpsPerspectiveSwitch from './OpsPerspectiveSwitch.vue'
import { assistantModes, workbench } from '../config/assistantModes.js'
import { useWorkbenchStore } from '../stores/workbench.js'
import { storeToRefs } from 'pinia'

const props = defineProps({
  streaming: { type: Boolean, default: false },
  inputText:  { type: String, default: '' },
  sessions:   { type: Array,  default: () => [] },
  sessionsLoading: { type: Boolean, default: false },
  activeScene: { type: String, default: '' },
  openDraftSignal: { type: Number, default: 0 },
  // 技能自动勾选信号（资费配置快捷回复联动）：透传给 ChatInput
  skillSignal: { type: Object, default: null },
})

const workbenchStore = useWorkbenchStore()
const { activeAssistant, sessions: allSessions } = storeToRefs(workbenchStore)

const hasAssistant = computed(() => !!activeAssistant.value)
const mode = computed(() => activeAssistant.value || 'rd')
const config = computed(() => assistantModes[activeAssistant.value] || assistantModes.rd)

// 侧边栏快捷场景：隐藏「套餐优化建议」
const sideSceneShortcuts = computed(() =>
  (config.value.sceneShortcuts || []).filter((s) => s.scene !== 'ops.scene3')
)

const navTitle = computed(() => workbench.navTitle)

const inputPlaceholder = computed(() => {
  if (!activeAssistant.value) return workbench.inputPlaceholder
  return config.value.inputPlaceholder
})

const tips = computed(() => {
  if (!activeAssistant.value) return workbench.tips
  return config.value.tips
})

const tipsOpen = ref(false)
const sideCollapsed = ref(false)

// ============ 历史会话检索 ============
const searchOpen = ref(false)
const searchKeyword = ref('')
const searchInputEl = ref(null)

const toggleSearch = () => {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) {
    nextTick(() => searchInputEl.value?.focus())
  } else {
    searchKeyword.value = ''
  }
}

const escapeHtml = (s) => String(s || '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]))

const highlight = (text, kw) => {
  if (!text || !kw) return escapeHtml(text)
  const safe = escapeHtml(text)
  const kwSafe = escapeHtml(kw)
  const idx = safe.toLowerCase().indexOf(kwSafe.toLowerCase())
  if (idx === -1) return safe
  return safe.slice(0, idx) + '<mark>' + safe.slice(idx, idx + kwSafe.length) + '</mark>' + safe.slice(idx + kwSafe.length)
}

// 搜索结果：跨所有助手会话，匹配标题/消息内容/工单号
const searchResults = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return []
  const results = []
  for (const s of allSessions.value) {
    const titleMatch = (s.title || '').toLowerCase().includes(kw)
    let contentMatch = false
    let workOrderMatch = false
    let matchedText = ''
    for (const m of (s.messages || [])) {
      const content = (m.content || '').toLowerCase()
      if (content.includes(kw)) {
        contentMatch = true
        const idx = content.indexOf(kw)
        const start = Math.max(0, idx - 12)
        const end = Math.min(content.length, idx + kw.length + 20)
        matchedText = (start > 0 ? '…' : '') + (m.content || '').slice(start, end) + (end < content.length ? '…' : '')
        break
      }
      const woid = m.workOrderId
      if (woid && woid.toLowerCase().includes(kw)) {
        workOrderMatch = true
        matchedText = '工单 ' + woid
        break
      }
    }
    if (titleMatch || contentMatch || workOrderMatch) {
      const reason = workOrderMatch
        ? '工单 ' + highlight(matchedText.split('工单 ')[1], kw)
        : contentMatch
          ? highlight(matchedText, kw)
          : '标题：' + highlight(s.title, kw)
      results.push({
        session_id: s.id,
        id: s.id,
        title: s.title || '新对话',
        updated_at: s.updated_at,
        assistantKey: s.assistantKey,
        matchReason: reason,
      })
    }
  }
  return results.sort((a, b) => (b.updated_at || 0) - (a.updated_at || 0)).slice(0, 12)
})

const displayedSessions = computed(() => {
  if (searchOpen.value && searchKeyword.value.trim()) {
    return searchResults.value
  }
  return props.sessions.slice(0, 8)
})

const emit = defineEmits([
  'update:inputText',
  'send',
  'stop',
  'new-session',
  'refresh-sessions',
  'switch-session',
  'delete-session',
  'shortcut',
  'quick-action',
  'open-approval',
  'open-product-metrics',
  'open-draft',
])

const onPickSession = (s) => {
  const sid = s.session_id || s.id
  if (searchOpen.value && searchKeyword.value) {
    searchOpen.value = false
    searchKeyword.value = ''
  }
  emit('switch-session', sid)
}

const onDeleteSession = (s) => {
  const sid = s.session_id || s.id
  emit('delete-session', sid)
}

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
.workbench-body { display: flex; flex: 1; min-height: 0; overflow: hidden; position: relative; }
.workbench-side {
  position: relative;
  width: 240px; flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  background: #fafafa;
  transition: width 0.24s ease;
}
.workbench-side.collapsed { width: 0; border-right-color: transparent; }
.side-inner {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
  display: flex; flex-direction: column; gap: 20px;
  transition: opacity 0.18s ease;
}
.workbench-side.collapsed .side-inner { opacity: 0; pointer-events: none; }

.side-collapse-btn {
  position: absolute;
  top: 20px;
  right: -12px;
  z-index: 20;
  width: 24px; height: 24px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
  transition: transform 0.24s ease, background 0.15s, color 0.15s, border-color 0.15s;
}
.side-collapse-btn:hover {
  background: #eff6ff;
  color: #2563eb;
  border-color: #93c5fd;
}
.side-collapse-btn.collapsed svg { transform: rotate(180deg); }
.side-collapse-btn svg { transition: transform 0.24s ease; }

.workbench-right { flex-shrink: 0; display: flex; flex-direction: column; min-height: 0; overflow: hidden; height: 100%; }
.workbench-right > :deep(.config-panel) { flex: 1; min-height: 0; }
.workbench-right :deep(.form-panel),
.workbench-right :deep(.ops-panel) { height: 100%; }
.side-section { display: flex; flex-direction: column; gap: 10px; }
.side-session { flex-shrink: 0; }
.side-history { flex: 1; min-height: 0; }
.side-title { font-weight: 700; color: #334155; font-size: 13px; }
.history-title { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
.side-btn { border: 1px solid #e2e8f0; background: #fff; padding: 10px 12px; border-radius: 12px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; transition: border-color 0.15s; }
.side-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.new-session-btn { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; padding: 10px 12px; border-radius: 12px; border: 1px solid #93c5fd; background: #eff6ff; color: #2563eb; font-weight: 600; font-size: 13px; cursor: pointer; transition: all 0.15s; }
.new-session-btn:hover { background: #dbeafe; border-color: #60a5fa; }
.new-session-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.history-list { display: flex; flex-direction: column; gap: 8px; flex: 1; min-height: 0; overflow-y: auto; padding-right: 2px; }
.history-list::-webkit-scrollbar { width: 6px; }
.history-list::-webkit-scrollbar-track { background: transparent; }
.history-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.history-list::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
.history-list { scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }
.side-btn.history-btn { padding: 8px 10px; }
.btn-label { font-weight: 600; color: #0f172a; font-size: 13px; }
.btn-label.history-label { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; width: 100%; display: block; }
.btn-scene { font-size: 11px; color: #64748b; line-height: 1.4; }
.history-item { position: relative; display: flex; align-items: stretch; }
.history-item .history-btn { flex: 1; min-width: 0; }
.history-del-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.95);
  color: #94a3b8;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}
.history-item:hover .history-del-btn,
.history-del-btn:focus-visible {
  opacity: 1;
}
.history-del-btn:hover {
  background: #fee2e2;
  color: #dc2626;
}
.side-tips { border-top: 1px solid #e5e7eb; padding-top: 14px; margin-top: auto; }
.tips-title { display: flex; align-items: center; justify-content: space-between; }
.tips-toggle {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; padding: 0;
  border: 1px solid #e2e8f0; border-radius: 50%;
  background: #fff; color: #94a3b8; cursor: pointer;
  transition: all 0.15s;
}
.tips-toggle:hover { border-color: #93c5fd; color: #2563eb; background: #f0f9ff; }
.tips-toggle.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.tips-body { display: flex; flex-direction: column; gap: 6px; overflow: hidden; }
.tip-text { font-size: 12px; color: #94a3b8; line-height: 1.6; }
.tips-collapse-enter-active, .tips-collapse-leave-active {
  transition: opacity 0.18s ease, max-height 0.22s ease;
  max-height: 240px;
}
.tips-collapse-enter-from, .tips-collapse-leave-to {
  opacity: 0; max-height: 0;
}
.refresh-btn { background: transparent; border: none; color: #94a3b8; cursor: pointer; padding: 2px; border-radius: 4px; display: flex; align-items: center; }
.refresh-btn:hover { background: #e2e8f0; color: #475569; }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.refresh-btn.active { background: #2563eb; color: #fff; }
.history-title-actions { display: flex; align-items: center; gap: 2px; }
.history-empty { font-size: 12px; color: #94a3b8; text-align: center; padding: 8px 0; }

.history-search {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #fff;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.history-search:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}
.search-icon { color: #94a3b8; flex-shrink: 0; }
.search-input {
  flex: 1; min-width: 0;
  border: none; outline: none; background: transparent;
  font-size: 12px; color: #0f172a;
}
.search-input::placeholder { color: #94a3b8; }
.search-clear {
  border: none; background: transparent; color: #94a3b8;
  cursor: pointer; padding: 2px; border-radius: 4px;
  display: flex; align-items: center;
}
.search-clear:hover { background: #e2e8f0; color: #475569; }

.side-btn.history-btn.cross-assistant {
  border-color: #c7d7fe;
  background: linear-gradient(135deg, #fff, #f8faff);
}
.match-reason { color: #475569; }
.match-reason :deep(mark) {
  background: #fef08a;
  color: #713f12;
  padding: 0 2px;
  border-radius: 3px;
  font-weight: 600;
}

.search-slide-enter-active, .search-slide-leave-active {
  transition: opacity 0.18s ease, max-height 0.2s ease, margin 0.18s ease;
  max-height: 40px;
  overflow: hidden;
}
.search-slide-enter-from, .search-slide-leave-to {
  opacity: 0; max-height: 0; margin-top: -4px;
}
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
.workbench-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; position: relative; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
</style>
