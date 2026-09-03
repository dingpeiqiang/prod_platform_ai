<template>
  <div class="assistant-page assistant-workbench" :class="{ 'keyboard-open': keyboardOpen }">
    <AssistantNavBar :mode="mode" :title="config.navTitle">
      <template v-if="$slots['nav-actions']" #actions>
        <slot name="nav-actions" />
      </template>
    </AssistantNavBar>

    <div class="workbench-body">
    <!-- 左侧栏：桌面常驻（可折叠），移动端为抽屉（overlay 版式见下） -->
    <aside class="workbench-side" :class="{ open: sideOpen, collapsed: sideCollapsed }">
      <div class="side-drawer-head">
        <span class="side-title">菜单</span>
        <button class="side-close-btn" type="button" @click="sideOpen = false" title="关闭">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div class="side-scroll">
        <div class="side-section side-session">
          <button class="new-session-btn" @click="closeDrawers(); $emit('new-session')" :disabled="streaming">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新建会话
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
                placeholder="搜索会话标题"
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
            <div v-else-if="searchOpen && searchKeyword && !displayedSessions.length" class="history-empty">
              未匹配到会话
            </div>
            <div v-else-if="!sessions.length" class="history-empty">暂无历史对话</div>
            <template v-else>
              <div
                v-for="s in displayedSessions"
                :key="s.session_id"
                class="history-item"
              >
                <button
                  class="side-btn history-btn"
                  @click="closeDrawers(); onPickSession(s)"
                >
                  <span class="btn-label history-label">{{ s.title || '新对话' }}</span>
                  <span class="btn-scene">{{ formatSessionTime(s.updated_at) }}</span>
                </button>
                <button
                  class="history-del-btn"
                  :title="'删除会话：' + (s.title || '新对话')"
                  @click.stop="$emit('delete-session', s.session_id)"
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

        <div class="side-section">
          <div class="side-title">快捷场景</div>
          <button
            v-for="item in config.sceneShortcuts"
            :key="item.label"
            class="side-btn"
            @click="closeDrawers(); $emit('shortcut', item)"
          >
            <span class="btn-label">{{ item.label }}</span>
            <span class="btn-scene">{{ item.desc || item.scene }}</span>
          </button>
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
              <p v-for="(tip, i) in config.tips" :key="i" class="tip-text">{{ tip }}</p>
            </div>
          </transition>
        </div>
      </div>

      <!-- 桌面折叠按钮（贴侧边栏右缘） -->
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
    <div v-if="sideOpen" class="drawer-backdrop" @click="sideOpen = false"></div>

    <main class="workbench-main">
      <!-- 移动端左侧栏入口（触控热区） -->
      <div class="mobile-toolbar">
        <button type="button" class="mobile-tb-btn" @click="sideOpen = true" title="菜单">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
        <span class="mobile-tb-title">{{ config.navTitle }}</span>
      </div>

      <div class="workbench-chat">
        <slot />
      </div>

      <div class="workbench-input">
        <slot name="restore-bar" />
        <ChatInput
          :modelValue="inputText"
          :disabled="streaming"
          :placeholder="config.inputPlaceholder"
          :assistantMode="mode"
          :context="context"
          @update:modelValue="$emit('update:inputText', $event)"
          @send="closeDrawers(); $emit('send', $event)"
          @stop="$emit('stop')"
          @quick-action="$emit('quick-action', $event)"
          @open-model-config="$emit('open-model-config', $event)"
          @context-remove="$emit('context-remove', $event)"
          @context-clear="$emit('context-clear')"
        />
      </div>
    </main>

    <!-- 右侧工作台面板：对话驱动的配置工作台/看板/比对面板挂载区 -->
    <template v-if="$slots.panel">
      <div v-show="panelOpen" class="workbench-panel" :style="{ width: panelWidth + 'px' }">
        <div
          class="panel-resizer"
          title="拖拽调整宽度"
          @mousedown="startPanelResize"
          @touchstart.prevent="startPanelResize"
        ></div>
        <div class="panel-body">
          <slot name="panel" />
        </div>
      </div>
    </template>
    </div><!-- /workbench-body -->
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import AssistantNavBar from './AssistantNavBar.vue'
import ChatInput from './ChatInput.vue'
import { assistantModes } from '../config/assistantModes.js'

const props = defineProps({
  mode:      { type: String, required: true },
  streaming: { type: Boolean, default: false },
  inputText: { type: String, default: '' },
  sessions:  { type: Array,   default: () => [] },
  sessionsLoading: { type: Boolean, default: false },
  /** 会话上下文标签（当前分析对象/业务意图），透传给 ChatInput 的 ContextBar */
  context:   { type: Array,   default: () => [] },
  /** 右侧工作台面板是否展开 */
  panelOpen: { type: Boolean, default: false },
  /** 右侧工作台面板宽度（px，含拖宽同步） */
  panelWidth: { type: Number, default: 480 },
})

const emit = defineEmits([
  'new-session', 'refresh-sessions', 'switch-session', 'delete-session', 'shortcut',
  'quick-action', 'send', 'stop', 'open-model-config',
  'context-remove', 'context-clear', 'update:inputText',
  'update:panelWidth', 'update:panelOpen',
])

const MIN_PANEL_W = 400
const MAX_PANEL_W = 900

/** 拖拽调宽（mouse + touch）；拖拽结束后持久化宽度由父级负责 */
function startPanelResize(e) {
  e.preventDefault()
  const startX = e.touches ? e.touches[0].clientX : e.clientX
  const startW = props.panelWidth
  const onMove = (ev) => {
    const x = ev.touches ? ev.touches[0].clientX : ev.clientX
    const next = Math.min(MAX_PANEL_W, Math.max(MIN_PANEL_W, startW - (x - startX)))
    emit('update:panelWidth', next)
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const config = computed(() => assistantModes[props.mode] || assistantModes.rd)

/** 抽屉开关（仅移动端/平板生效，桌面由 CSS 直接常驻展示） */
const sideOpen = ref(false)
const keyboardOpen = ref(false)
/** 桌面侧边栏折叠态（仅 ≥1024px 生效，移动端折叠类不作用） */
const sideCollapsed = ref(false)

/** 会话检索 */
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

const displayedSessions = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (searchOpen.value && kw) {
    return props.sessions.filter((s) => (s.title || '').toLowerCase().includes(kw)).slice(0, 12)
  }
  return props.sessions.slice(0, 8)
})

const onPickSession = (s) => {
  if (searchOpen.value && searchKeyword.value) {
    searchOpen.value = false
    searchKeyword.value = ''
  }
  emit('switch-session', s.session_id)
}

const closeDrawers = () => {
  sideOpen.value = false
}

/** 触屏软键盘：视觉视口升高时顶起输入区，避免被键盘遮挡 */
function onVisualViewport() {
  const vv = window.visualViewport
  if (!vv) return
  keyboardOpen.value = vv.height < window.innerHeight - 60
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

onMounted(() => {
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', onVisualViewport)
    window.visualViewport.addEventListener('scroll', onVisualViewport)
  }
})

onUnmounted(() => {
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', onVisualViewport)
    window.visualViewport.removeEventListener('scroll', onVisualViewport)
  }
})
</script>

<style scoped>
.assistant-workbench { display: flex; flex-direction: column; height: 100%; }
.workbench-body { display: flex; flex: 1; min-height: 0; overflow: hidden; position: relative; }
/* 左侧栏：移动端为全屏抽屉，桌面常驻可折叠 */
.workbench-side {
  position: relative;
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.24s ease;
}
.workbench-side.collapsed { width: 0; border-right-color: transparent; }
.side-scroll { display: flex; flex-direction: column; gap: 20px; padding: 16px; overflow-y: auto; flex: 1; transition: opacity 0.18s ease; }
.workbench-side.collapsed .side-scroll { opacity: 0; pointer-events: none; }
.side-drawer-head { display: none; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.side-close-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; background: transparent; color: #64748b; cursor: pointer; border-radius: 8px; }
.side-close-btn:hover { background: #f1f5f9; color: #0f172a; }

/* 桌面折叠按钮 */
.side-collapse-btn {
  position: absolute;
  top: 20px;
  right: -12px;
  z-index: 20;
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

.side-section { display: flex; flex-direction: column; gap: 10px; }
.side-session { flex-shrink: 0; }
.side-history { flex: 1; min-height: 0; }
.side-title { font-weight: 700; color: #334155; font-size: 13px; }
.history-title { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
.history-title-actions { display: flex; align-items: center; gap: 2px; }
.side-btn { border: 1px solid #e2e8f0; background: #fff; padding: 10px 12px; border-radius: 12px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; transition: border-color 0.15s; }
.side-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.new-session-btn { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; padding: 10px 12px; border-radius: 12px; border: 1px solid #93c5fd; background: #eff6ff; color: #2563eb; font-weight: 600; font-size: 13px; cursor: pointer; transition: all 0.15s; }
.new-session-btn:hover { background: #dbeafe; border-color: #60a5fa; }
.new-session-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.history-list { display: flex; flex-direction: column; gap: 8px; flex: 1; min-height: 0; overflow-y: auto; padding-right: 2px; scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }
.history-list::-webkit-scrollbar { width: 6px; }
.history-list::-webkit-scrollbar-track { background: transparent; }
.history-list::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.history-list::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
.side-btn.history-btn { padding: 8px 10px; }
.btn-label { font-weight: 600; color: #0f172a; font-size: 13px; }
.btn-label.history-label { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; width: 100%; display: block; }
.btn-scene { font-size: 11px; color: #64748b; line-height: 1.4; }

/* 历史条目：hover 显示删除 */
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
.history-del-btn:focus-visible { opacity: 1; }
.history-del-btn:hover { background: #fee2e2; color: #dc2626; }

/* 会话检索框 */
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
.search-slide-enter-active, .search-slide-leave-active {
  transition: opacity 0.18s ease, max-height 0.2s ease, margin 0.18s ease;
  max-height: 40px;
  overflow: hidden;
}
.search-slide-enter-from, .search-slide-leave-to {
  opacity: 0; max-height: 0; margin-top: -4px;
}

/* 使用提示折叠 */
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
.history-empty { font-size: 12px; color: #94a3b8; text-align: center; padding: 8px 0; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }

.workbench-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.workbench-chat { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
.assistant-workbench.keyboard-open .workbench-input { padding-bottom: max(16px, env(safe-area-inset-bottom)); }

/* 还原条（restore bar）：面板关闭后保留上下文，一键恢复 */
.restore-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
}
.restore-badge {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}
.restore-name {
  flex: 1;
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.restore-btn {
  border: none;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.restore-btn:hover { background: #1d4ed8; }
.restore-close {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  padding: 4px;
  flex-shrink: 0;
}
.restore-close:hover { color: #0f172a; }
.restore-slide-enter-active, .restore-slide-leave-active { transition: all 0.24s ease; }
.restore-slide-enter-from, .restore-slide-leave-to { opacity: 0; transform: translateY(8px); }

/* 右侧工作台面板：resizer 贴左缘、面板占满高度 */
.workbench-panel {
  flex-shrink: 0;
  position: relative;
  min-width: 0;
  border-left: 1px solid #e5e7eb;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}
.panel-resizer {
  position: absolute;
  left: -3px;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 5;
}
.panel-resizer:hover { background: rgba(37, 99, 235, 0.15); }
/* 对标原型 .detail-panel-body：面板体为唯一纵向滚动容器 */
.panel-body { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; }
.panel-body::-webkit-scrollbar { width: 6px; }
.panel-body::-webkit-scrollbar-track { background: transparent; }
.panel-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.panel-body::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

@media (max-width: 1023px) {
  .workbench-panel {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 45;
    width: min(92vw, 480px) !important;
    box-shadow: -8px 0 24px rgba(15, 23, 42, 0.12);
  }
}

/* 移动端顶部工具栏（<1024px 显示） */
.mobile-toolbar { display: none; }

.drawer-backdrop { display: none; }

/* 移动优先 + 断点降级：<768 单列全屏；768–1023 左侧抽屉；≥1024 双区常驻 */
@media (max-width: 1023px) {
  .mobile-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    border-bottom: 1px solid #e5e7eb;
    background: #fff;
    flex-shrink: 0;
  }
  .mobile-tb-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border: 1px solid #e2e8f0;
    background: #f8fafc;
    color: #334155;
    border-radius: 10px;
    cursor: pointer;
  }
  .mobile-tb-title { flex: 1; text-align: center; font-weight: 700; color: #0f172a; font-size: 14px; }

  /* 左侧栏 → 全屏抽屉 */
  .workbench-side {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 40;
    width: min(84vw, 320px);
    transform: translateX(-100%);
    transition: transform 0.24s ease;
    box-shadow: 8px 0 24px rgba(15,23,42,0.12);
  }
  .workbench-side.open { transform: translateX(0); }
  .workbench-side.collapsed { width: min(84vw, 320px); }
  .side-drawer-head { display: flex; }
  /* 折叠按钮仅桌面展示 */
  .side-collapse-btn { display: none; }

  .drawer-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 35;
    background: rgba(15,23,42,0.35);
  }
}

@media (min-width: 1024px) {
  .drawer-backdrop { display: none !important; }
  .workbench-side { transform: none; position: static; box-shadow: none; }
  .side-drawer-head { display: none; }
}

/* 触控优化：热区 ≥44px；表/清单横向可滚由子组件承载 */
@media (pointer: coarse) {
  .mobile-tb-btn, .side-close-btn { min-width: 44px; min-height: 44px; }
  .side-btn, .new-session-btn { min-height: 44px; }
}

/* 安全区：底部输入与抽屉底部留白 */
@media (max-width: 767px) {
  .workbench-side { padding-bottom: env(safe-area-inset-bottom); }
}
</style>
