<template>
  <div class="assistant-page assistant-workbench" :class="{ 'keyboard-open': keyboardOpen }">
    <AssistantNavBar :mode="mode" :title="config.navTitle">
      <template v-if="$slots['nav-actions']" #actions>
        <slot name="nav-actions" />
      </template>
    </AssistantNavBar>

    <div class="workbench-body">
    <!-- 移动端：左侧栏桌面占位（抽屉版本见下方 overlay） -->
    <aside class="workbench-side" :class="{ open: sideOpen }">
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
                @click="closeDrawers(); $emit('switch-session', s.session_id)"
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
            @click="closeDrawers(); $emit('shortcut', item)"
          >
            <span class="btn-label">{{ item.label }}</span>
            <span class="btn-scene">{{ item.desc || item.scene }}</span>
          </button>
        </div>

        <div class="side-section side-tips">
          <div class="side-title">使用提示</div>
          <p v-for="(tip, i) in config.tips" :key="i" class="tip-text">{{ tip }}</p>
        </div>
      </div>
    </aside>
    <div v-if="sideOpen" class="drawer-backdrop" @click="sideOpen = false"></div>

    <main class="workbench-main">
      <!-- 移动端左侧栏入口 + 右侧汇总入口（触控热区） -->
      <div class="mobile-toolbar">
        <button type="button" class="mobile-tb-btn" @click="sideOpen = true" title="菜单">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
        <span class="mobile-tb-title">{{ config.navTitle }}</span>
        <button v-if="$slots.right" type="button" class="mobile-tb-btn" @click="rightOpen = true" title="会话汇总">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3v18h18"/><polyline points="7 15 11 11 14 14 18 9"/>
          </svg>
        </button>
      </div>

      <!-- 移动端：消息区顶部紧凑「会话汇总」状态条（点击展开右侧抽屉） -->
      <div v-if="$slots.right && summaryStats.length" class="mobile-summary-bar" @click="rightOpen = true">
        <svg class="msb-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 3v18h18"/><polyline points="7 15 11 11 14 14 18 9"/>
        </svg>
        <span class="msb-item" v-for="(item, i) in summaryStats" :key="i" :class="`tone-${item.tone || 'neutral'}`">
          <span class="msb-label">{{ item.label }}</span>
          <span class="msb-value">{{ item.value }}</span>
        </span>
        <span class="msb-more">展开 ›</span>
      </div>

      <div class="workbench-chat">
        <slot />
      </div>

      <div class="workbench-input">
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

    <!-- 右侧汇总：桌面常驻 / 平板、手机收为右抽屉 -->
    <template v-if="$slots.right">
      <aside class="workbench-right" :class="{ 'mobile-drawer': true, open: rightOpen }">
        <div class="right-drawer-head">
          <span class="side-title">会话汇总</span>
          <button class="side-close-btn" type="button" @click="rightOpen = false" title="关闭">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <slot name="right" />
      </aside>
      <div v-if="rightOpen" class="drawer-backdrop" @click="rightOpen = false"></div>
    </template>
    </div><!-- /workbench-body -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
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
  /** 移动端紧凑「会话汇总」状态条数据：[{ label, value, tone }] */
  summaryStats: { type: Array, default: () => [] },
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
  'open-model-config',
  'context-remove',
  'context-clear',
])

const config = computed(() => assistantModes[props.mode] || assistantModes.rd)

/** 抽屉开关（仅移动端/平板生效，桌面由 CSS 直接常驻展示） */
const sideOpen = ref(false)
const rightOpen = ref(false)
const keyboardOpen = ref(false)

const closeDrawers = () => {
  sideOpen.value = false
  rightOpen.value = false
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
.workbench-body { display: flex; flex: 1; min-height: 0; overflow: hidden; }
/* 左侧栏：移动端为全屏抽屉，桌面常驻 */
.workbench-side {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-scroll { display: flex; flex-direction: column; gap: 20px; padding: 16px; overflow-y: auto; flex: 1; }
.side-drawer-head { display: none; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.side-close-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; background: transparent; color: #64748b; cursor: pointer; border-radius: 8px; }
.side-close-btn:hover { background: #f1f5f9; color: #0f172a; }

.workbench-right { width: 300px; flex-shrink: 0; height: 100%; overflow: hidden; display: flex; min-height: 0; }
.workbench-right :deep(.scene-summary-panel) { height: 100%; }
.right-drawer-head { display: none; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; background: #fff; }

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
.workbench-chat { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
.assistant-workbench.keyboard-open .workbench-input { padding-bottom: max(16px, env(safe-area-inset-bottom)); }

/* 移动端顶部工具栏 + 紧凑会话汇总状态条（<1024px 显示） */
.mobile-toolbar, .mobile-summary-bar { display: none; }

.drawer-backdrop { display: none; }

/* 移动优先 + 断点降级：<768 单列全屏；768–1023 右侧抽屉、左侧抽屉；≥1024 三区常驻 */
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
  .mobile-toolbar .mobile-tb-btn:last-child { margin-left: auto; }

  .mobile-summary-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    border-bottom: 1px solid #eef0f3;
    background: #fbfbfd;
    overflow-x: auto;
    flex-shrink: 0;
    cursor: pointer;
    scrollbar-width: none;
  }
  .mobile-summary-bar::-webkit-scrollbar { display: none; }
  .msb-icon { color: #0f766e; flex-shrink: 0; }
  .msb-item { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; white-space: nowrap; color: #475569; }
  .msb-label { color: #94a3b8; }
  .msb-value { font-weight: 700; color: #0f172a; }
  .msb-item.tone-good .msb-value { color: #16a34a; }
  .msb-item.tone-warn .msb-value { color: #d97706; }
  .msb-item.tone-bad .msb-value { color: #dc2626; }
  .msb-more { margin-left: auto; font-size: 11px; color: #0f766e; flex-shrink: 0; }

  /* 右侧汇总 → 全屏右抽屉 */
  .workbench-right.mobile-drawer {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 50;
    width: min(84vw, 360px);
    max-width: 100%;
    transform: translateX(100%);
    transition: transform 0.24s ease;
    box-shadow: -8px 0 24px rgba(15,23,42,0.12);
    background: #fbfbfd;
  }
  .workbench-right.mobile-drawer.open { transform: translateX(0); }
  .right-drawer-head { display: flex; }
  .workbench-right.mobile-drawer :deep(.scene-summary-panel) { border-left: none; }

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
  .side-drawer-head { display: flex; }

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
  .workbench-right.mobile-drawer { position: static; transform: none; width: 300px; background: transparent; box-shadow: none; }
  .right-drawer-head { display: none; }
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