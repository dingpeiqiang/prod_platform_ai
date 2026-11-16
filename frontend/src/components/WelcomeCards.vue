<template>
  <div class="welcome-container" :class="modeClass">
    <div class="welcome-content">
      <header class="brand-section">
        <div class="brand-title-row">
          <h1 class="brand-title">{{ greeting }}</h1>
        </div>
        <p class="brand-subtitle">{{ meta.subtitle }}</p>
        <ul v-if="meta.tags.length" class="capability-tags" aria-label="核心能力">
          <li v-for="tag in meta.tags" :key="tag">{{ tag }}</li>
        </ul>
      </header>

      <!-- 工作台总览：三个助手入口 -->
      <section v-if="!hasAssistant" class="scenarios-section" aria-labelledby="welcome-scenarios-title">
        <div class="section-head">
          <h2 id="welcome-scenarios-title" class="section-title">选择一个助手开始</h2>
          <p class="section-hint">点击下方任一助手，进入对应场景；也可直接在下方输入框提问</p>
        </div>
        <div class="assistant-cards">
          <button
            v-for="a in assistants"
            :key="a.key"
            type="button"
            class="assistant-card"
            :style="{ '--ac-color': a.color, '--ac-bg': a.bg }"
            @click="onPickAssistant(a.key)"
          >
            <div class="card-icon" :style="{ background: a.bg, color: a.color }" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path v-if="a.icon === 'flask'" d="M9 3h6M10 3v6L5 19a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-10V3"/>
                <path v-else-if="a.icon === 'chart'" d="M3 3v18h18M7 14l4-4 3 3 5-6"/>
                <g v-else><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></g>
              </svg>
            </div>
            <div class="card-content">
              <div class="card-title">{{ a.name }}</div>
              <div class="card-desc">{{ a.desc }}</div>
            </div>
            <div class="card-arrow" aria-hidden="true">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
            </div>
          </button>
        </div>
      </section>

      <!-- 运营助手：快捷入口（产品运营视图 → 右侧详情面板） -->
      <section v-if="showOpsEntry" class="ops-entry-section" aria-label="产品运营视图入口">
        <button type="button" class="ops-entry-card" @click="emit('open-ops')">
          <div class="ops-entry-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 3v18h18"/>
              <path d="M7 14l4-4 4 2 5-6"/>
            </svg>
          </div>
          <div class="ops-entry-body">
            <div class="ops-entry-title">产品运营视图</div>
            <div class="ops-entry-desc">收入总览 · 规模活跃 · 结构占比 · 重点产品</div>
          </div>
          <svg class="ops-entry-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </button>
      </section>

      <!-- 选中助手后：场景卡片 -->
      <section class="scenarios-section" aria-labelledby="welcome-scenarios-title">
        <div class="section-head">
          <h2 id="welcome-scenarios-title" class="section-title">从这里开始</h2>
          <p class="section-hint">点击场景将展示该场景的欢迎说明；也可直接输入你的问题</p>
        </div>
        <div class="suggestion-cards">
          <button
            v-for="card in cards"
            :key="card.label"
            type="button"
            class="suggestion-card"
            @click="emit('suggest', { ...card, autoSend: true })"
          >
            <div class="card-icon" :style="{ background: card.bg, color: card.color }" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="card-content">
              <div class="card-title">{{ card.label }}</div>
              <div class="card-desc">{{ card.desc }}</div>
              <div class="card-example">示例：{{ card.example }}</div>
            </div>
          </button>
        </div>
      </section>

      <p v-if="meta.footer" class="footer-note">{{ meta.footer }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { assistantModes, assistants, buildSceneWelcome } from '../config/assistantModes.js'
import { useUserStore } from '../stores/user.js'
import { useWorkbenchStore } from '../stores/workbench.js'
import { usePerspectiveStore } from '../stores/perspective.js'
import { storeToRefs } from 'pinia'

const emit = defineEmits(['suggest', 'open-ops'])

const userStore = useUserStore()
const workbenchStore = useWorkbenchStore()
const perspectiveStore = usePerspectiveStore()
const { activeAssistant } = storeToRefs(workbenchStore)
const { opsPerspective } = storeToRefs(perspectiveStore)

const hasAssistant = computed(() => !!activeAssistant.value)
const mode = computed(() => activeAssistant.value || 'rd')

// 运营助手欢迎页：显示"产品运营视图"快捷入口（点击后右侧详情面板展示）
const showOpsEntry = computed(() => activeAssistant.value === 'ops')

const modeClass = computed(() => {
  const map = { rd: 'mode-rd', ops: 'mode-ops', query: 'mode-query' }
  return map[activeAssistant.value] || 'mode-workbench'
})

function buildGreeting() {
  const h = new Date().getHours()
  let period = '你好'
  if (h < 6) period = '凌晨好'
  else if (h < 9) period = '早上好'
  else if (h < 12) period = '上午好'
  else if (h < 14) period = '中午好'
  else if (h < 18) period = '下午好'
  else period = '晚上好'
  return `${period}，${userStore.displayName}`
}

const greeting = ref(buildGreeting())
let greetingTimer = null
function refreshGreeting() { greeting.value = buildGreeting() }
onMounted(() => { greetingTimer = setInterval(refreshGreeting, 60 * 1000) })
onBeforeUnmount(() => { if (greetingTimer) clearInterval(greetingTimer) })

// 工作台总览的 meta
const workbenchMeta = {
  subtitle: 'AI原生产品工作台覆盖产品研发、运营、查询全生命周期。选择一个助手，进入对应场景开始对话。',
  tags: ['研发', '运营', '查询'],
  footer: '顶部或下方任一入口均可切换助手；切换时当前对话会自动归档到历史对话。',
}

const assistantMetaMap = {
  rd: {
    subtitle: '围绕产品研发环节，提供营销活动管理、资费配置、业务规则管理等场景的支持。',
    tags: ['营销活动管理', '资费配置', '业务规则管理'],
  },
  ops: {
    subtitle: '围绕产品上线后的运营环节，提供数据分析、异动归因、优化方案等支持。',
    tags: ['数据分析', '异动归因', '优化方案'],
  },
  query: {
    subtitle: '快速查询产品信息、配置、版本、归属等资料。',
    tags: ['产品资料', '版本与归属'],
  },
}

const meta = computed(() => {
  if (!activeAssistant.value) return workbenchMeta
  const base = assistantMetaMap[activeAssistant.value] || workbenchMeta
  // 运营助手：平台视角去掉「优化方案」能力标签，代理商视角保留
  if (activeAssistant.value === 'ops' && opsPerspective.value === 'platform') {
    return { ...base, tags: (base.tags || []).filter((t) => t !== '优化方案') }
  }
  return base
})

const cards = computed(() => {
  if (!activeAssistant.value) return []
  const cfg = assistantModes[activeAssistant.value]
  // 运营助手：按当前视角（平台 / 代理商）渲染同一套 3 个场景卡的不同口径文案
  let shortcuts =
    activeAssistant.value === 'ops' && cfg?.sceneByPerspective
      ? cfg.sceneByPerspective[opsPerspective.value] || cfg.sceneShortcuts || []
      : cfg?.sceneShortcuts || []
  // 运营助手：平台视角隐藏「套餐优化建议」场景卡，代理商视角保留
  if (activeAssistant.value === 'ops' && opsPerspective.value === 'platform') {
    shortcuts = shortcuts.filter((s) => s.scene !== 'ops.scene3')
  }
  const styleByScene = {
    'rd.scene1': { bg: '#eff6ff', color: '#2563eb' },
    'rd.scene2': { bg: '#ecfdf5', color: '#059669' },
    'rd.scene3': { bg: '#f5f3ff', color: '#6d28d9' },
    'ops.scene1': { bg: '#ecfdf5', color: '#059669' },
    'ops.scene2': { bg: '#fff7ed', color: '#ea580c' },
    'ops.scene3': { bg: '#eff6ff', color: '#2563eb' },
    'query.scene1': { bg: '#f5f3ff', color: '#6d28d9' },
    'query.scene2': { bg: '#eff6ff', color: '#2563eb' },
  }
  return shortcuts.map((s) => {
    const welcome = buildSceneWelcome(s)
    const style = styleByScene[s.scene] || { bg: '#f8fafc', color: '#475569' }
    return {
      ...s,
      bg: style.bg,
      color: style.color,
      example: s.example || (s.nextSteps && s.nextSteps[0]) || s.text || '',
      welcome: welcome.content,
      nextSteps: welcome.nextSteps,
    }
  })
})

function onPickAssistant(key) {
  workbenchStore.selectAssistant(key)
}
</script>

<style scoped>
.welcome-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12px 24px 28px;
  min-height: 0;
  overflow: visible;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, var(--welcome-glow), transparent),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 48%);
}
.mode-workbench {
  --welcome-accent: #0f766e;
  --welcome-accent-soft: #ccfbf1;
  --welcome-glow: rgba(15, 118, 110, 0.12);
  --welcome-card-hover: #5eead4;
  --welcome-shadow: rgba(15, 118, 110, 0.12);
}
.mode-rd {
  --welcome-accent: #2563eb;
  --welcome-accent-soft: #dbeafe;
  --welcome-glow: rgba(37, 99, 235, 0.12);
  --welcome-card-hover: #93c5fd;
  --welcome-shadow: rgba(37, 99, 235, 0.12);
}
.mode-ops {
  --welcome-accent: #0f766e;
  --welcome-accent-soft: #ccfbf1;
  --welcome-glow: rgba(15, 118, 110, 0.12);
  --welcome-card-hover: #5eead4;
  --welcome-shadow: rgba(15, 118, 110, 0.12);
}
.mode-query {
  --welcome-accent: #6d28d9;
  --welcome-accent-soft: #ede9fe;
  --welcome-glow: rgba(109, 40, 217, 0.12);
  --welcome-card-hover: #c4b5fd;
  --welcome-shadow: rgba(109, 40, 217, 0.12);
}

.welcome-content {
  width: 100%;
  max-width: 880px;
  text-align: center;
  margin-block: auto;
}

.brand-section { margin-bottom: 24px; }

.brand-title-row { margin: 0 0 8px; }
.brand-title {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
  line-height: 1.3;
}
.brand-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0 auto;
  line-height: 1.65;
  max-width: 640px;
}
.capability-tags {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
}
.capability-tags li {
  font-size: 12px;
  font-weight: 600;
  color: var(--welcome-accent);
  background: var(--welcome-accent-soft);
  border-radius: 999px;
  padding: 5px 12px;
}

.scenarios-section { margin-bottom: 16px; }
.section-head { margin-bottom: 12px; }
.section-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}
.section-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}

/* 助手入口卡片（总览） */
.assistant-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  text-align: left;
}
.assistant-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  text-align: left;
  font: inherit;
  color: inherit;
  position: relative;
}
.assistant-card:hover {
  border-color: var(--ac-color, #93c5fd);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}
.assistant-card:focus-visible {
  outline: 2px solid var(--ac-color, #2563eb);
  outline-offset: 2px;
}
.assistant-card .card-arrow {
  position: absolute;
  right: 12px;
  bottom: 12px;
  color: var(--ac-color, #94a3b8);
  opacity: 0.6;
}

/* 场景卡片 */
.suggestion-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  text-align: left;
}
.suggestion-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  text-align: left;
  font: inherit;
  color: inherit;
}
.suggestion-card:hover {
  border-color: var(--welcome-card-hover);
  box-shadow: 0 8px 24px var(--welcome-shadow);
  transform: translateY(-2px);
}
.suggestion-card:focus-visible {
  outline: 2px solid var(--welcome-accent);
  outline-offset: 2px;
}

.card-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-content { min-width: 0; flex: 1; }
.card-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}
.card-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 6px;
}
.card-example {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.footer-note {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .welcome-container { padding: 8px 16px 24px; }
  .brand-title { font-size: 22px; }
  .assistant-cards { grid-template-columns: 1fr; }
  .suggestion-cards { grid-template-columns: 1fr; }
  .card-example { white-space: normal; }
}

@media (prefers-reduced-motion: reduce) {
  .suggestion-card, .assistant-card { transition: none; }
  .suggestion-card:hover, .assistant-card:hover { transform: none; }
}

/* 运营助手：产品运营视图入口 */
.ops-entry-section {
  width: 100%;
  max-width: 1000px;
  margin-bottom: 12px;
}
.ops-entry-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 16px 18px;
  background: linear-gradient(135deg, #e6effb 0%, #f0f9ff 60%, #ffffff 100%);
  border: 1px solid #dbe7f6;
  border-radius: 14px;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.ops-entry-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}
.ops-entry-card:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}
.ops-entry-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #dbeafe;
  color: #2563eb;
}
.ops-entry-body { flex: 1; min-width: 0; }
.ops-entry-title { font-size: 15px; font-weight: 700; color: #0f172a; margin-bottom: 3px; }
.ops-entry-desc { font-size: 12px; color: #64748b; }
.ops-entry-arrow { flex-shrink: 0; color: #2563eb; }
</style>
