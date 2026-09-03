<template>
  <div class="welcome-container" :class="modeClass">
    <div class="welcome-content">
      <header class="brand-section">
        <h1 class="brand-title">{{ greeting }}</h1>
        <p class="brand-subtitle">{{ meta.subtitle }}</p>
        <ul v-if="meta.tags.length" class="capability-tags" aria-label="核心能力">
          <li v-for="tag in meta.tags" :key="tag">{{ tag }}</li>
        </ul>
      </header>

      <!-- 运营助手：快捷入口（产品运营视图 → 右侧工作台面板） -->
      <section v-if="mode === 'ops'" class="ops-entry-section" aria-label="产品运营视图入口">
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
              <svg v-if="card.icon === 'chat'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              <svg v-else-if="card.icon === 'file'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="12" y1="18" x2="12" y2="12"/>
                <line x1="9" y1="15" x2="15" y2="15"/>
              </svg>
              <svg v-else-if="card.icon === 'search'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <svg v-else-if="card.icon === 'chart'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 3v18h18"/>
                <path d="M18 17V9"/>
                <path d="M13 17V5"/>
                <path d="M8 17v-3"/>
              </svg>
              <svg v-else-if="card.icon === 'check'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4"/>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
              </svg>
              <svg v-else-if="card.icon === 'trace'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="18" cy="5" r="3"/>
                <circle cx="6" cy="12" r="3"/>
                <circle cx="18" cy="19" r="3"/>
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
              </svg>
              <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
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

      <p class="footer-note">{{ meta.footer }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { assistantModes, buildSceneWelcome } from '../config/assistantModes.js'
import { useUserStore } from '../stores/user.js'

const props = defineProps({
  mode: { type: String, default: 'rd' },
})

const emit = defineEmits(['suggest', 'open-ops'])

const userStore = useUserStore()

const modeClass = computed(() =>
  props.mode === 'ops' ? 'mode-ops' : props.mode === 'query' ? 'mode-query' : 'mode-rd',
)

/** 问候语：按时段 + 展示名，每分钟刷新 */
function buildGreeting() {
  const h = new Date().getHours()
  let period = '你好'
  if (h < 6) period = '凌晨好'
  else if (h < 9) period = '早上好'
  else if (h < 12) period = '上午好'
  else if (h < 14) period = '中午好'
  else if (h < 18) period = '下午好'
  else period = '晚上好'
  const name = userStore.displayName
  return name ? `${period}，${name}` : `${period}`
}

const greeting = ref(buildGreeting())
let greetingTimer = null
function refreshGreeting() { greeting.value = buildGreeting() }
onMounted(() => { greetingTimer = setInterval(refreshGreeting, 60 * 1000) })
onBeforeUnmount(() => { if (greetingTimer) clearInterval(greetingTimer) })

const rdMeta = {
  subtitle: '智聊·对话配置、智读·文件配置与智查·历史复用一体完成，让商品上架更快、更准、更合规。',
  tags: ['智聊·对话配置', '智读·文件配置', '智查·历史复用'],
  footer: '本体负责填字段与拦冲突，大模型负责理解业务表达。',
}

const opsMeta = {
  subtitle: '市场洞察、立项研判、异动归因与风险稽核一屏直达，用本体推理定位问题，用规则保障决策合规。',
  tags: ['市场洞察', '立项研判', '异动归因', '风险稽核'],
  footer: '本体负责推理与追溯，规则负责红线判定，大模型负责解释与表达。',
}

const queryMeta = {
  subtitle: '智能问答、档案调阅与比对分析一体完成，快速查到商品资料，横向看清差异。',
  tags: ['智能问答', '档案调阅', '比对分析'],
  footer: '本体负责检索与比对，大模型负责理解提问与表达结论。',
}

/** 欢迎页卡片与左侧快捷场景共用配置，保证欢迎信息一致 */
const cards = computed(() => {
  const mode = ['rd', 'ops', 'query'].includes(props.mode) ? props.mode : 'rd'
  const shortcuts = assistantModes[mode]?.sceneShortcuts || []
  const iconByScene = {
    'rd.chat': 'chat',
    'rd.import': 'file',
    'rd.query': 'search',
    'rd.compare': 'chart',
    market_insight: 'chart',
    online_check: 'check',
    root_cause: 'trace',
    risk_audit: 'warn',
    ops_monitor: 'chart',
    ops_rules: 'check',
    'query.ask': 'chat',
    'query.archive': 'search',
    'query.compare': 'chart',
  }
  const styleByScene = {
    'rd.chat': { bg: '#eff6ff', color: '#2563eb' },
    'rd.import': { bg: '#ecfdf5', color: '#059669' },
    'rd.query': { bg: '#f0f9ff', color: '#0284c7' },
    'rd.compare': { bg: '#f5f3ff', color: '#6d28d9' },
    market_insight: { bg: '#ecfeff', color: '#0e7490' },
    online_check: { bg: '#fefce8', color: '#a16207' },
    root_cause: { bg: '#f0fdf4', color: '#15803d' },
    risk_audit: { bg: '#fff1f2', color: '#be123c' },
    ops_monitor: { bg: '#eff6ff', color: '#2563eb' },
    ops_rules: { bg: '#f8fafc', color: '#475569' },
    'query.ask': { bg: '#eff6ff', color: '#2563eb' },
    'query.archive': { bg: '#f0f9ff', color: '#0284c7' },
    'query.compare': { bg: '#f5f3ff', color: '#6d28d9' },
  }
  // 欢迎页只展示核心入口卡（对比/规则等仍可从侧边栏进入）
  const welcomeScenes = mode === 'ops'
    ? ['market_insight', 'online_check', 'root_cause', 'risk_audit']
    : mode === 'query'
      ? ['query.ask', 'query.archive', 'query.compare']
      : ['rd.chat', 'rd.import', 'rd.query']

  return shortcuts
    .filter((s) => welcomeScenes.includes(s.scene))
    .map((s) => {
      const welcome = buildSceneWelcome(s)
      const style = styleByScene[s.scene] || { bg: '#f8fafc', color: '#475569' }
      return {
        ...s,
        icon: iconByScene[s.scene] || 'chat',
        bg: style.bg,
        color: style.color,
        example: s.example || (s.nextSteps && s.nextSteps[0]) || s.text || '',
        welcome: welcome.content,
        nextSteps: welcome.nextSteps,
      }
    })
})

const meta = computed(() =>
  props.mode === 'ops' ? opsMeta : props.mode === 'query' ? queryMeta : rdMeta,
)
</script>

<style scoped>
.welcome-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 0 24px 28px;
  min-height: 0;
  overflow: visible;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, var(--welcome-glow), transparent),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 48%);
}
.mode-rd {
  --welcome-accent: #2563eb;
  --welcome-accent-soft: #dbeafe;
  --welcome-glow: rgba(37, 99, 235, 0.12);
  --welcome-card-hover: #93c5fd;
  --welcome-shadow: rgba(37, 99, 235, 0.12);
  --welcome-top-offset: 10vh;
}
.mode-ops {
  --welcome-accent: #0f766e;
  --welcome-accent-soft: #ccfbf1;
  --welcome-glow: rgba(15, 118, 110, 0.12);
  --welcome-card-hover: #5eead4;
  --welcome-shadow: rgba(15, 118, 110, 0.12);
  --welcome-top-offset: 10vh;
}
.mode-query {
  --welcome-accent: #6d28d9;
  --welcome-accent-soft: #ede9fe;
  --welcome-glow: rgba(109, 40, 217, 0.12);
  --welcome-card-hover: #c4b5fd;
  --welcome-shadow: rgba(109, 40, 217, 0.12);
  --welcome-top-offset: 10vh;
}

.welcome-content {
  width: 100%;
  max-width: 880px;
  text-align: center;
  padding-top: var(--welcome-top-offset, 10vh);
}

.brand-section { margin-bottom: 24px; }
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

/* 运营助手：产品运营视图入口卡 */
.ops-entry-section { margin-bottom: 16px; }
.ops-entry-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ccfbf1;
  border-radius: 14px;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
}
.ops-entry-card:hover {
  border-color: #5eead4;
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.12);
  transform: translateY(-2px);
}
.ops-entry-card:focus-visible {
  outline: 2px solid #0f766e;
  outline-offset: 2px;
}
.ops-entry-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ccfbf1;
  color: #0f766e;
}
.ops-entry-body { flex: 1; min-width: 0; }
.ops-entry-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 2px;
}
.ops-entry-desc { font-size: 12px; color: #64748b; }
.ops-entry-arrow { flex-shrink: 0; color: #0f766e; opacity: 0.6; }
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
  .welcome-container { padding: 0 16px 24px; }
  .brand-title { font-size: 22px; }
  .suggestion-cards { grid-template-columns: 1fr; }
  .card-example { white-space: normal; }
}

@media (prefers-reduced-motion: reduce) {
  .suggestion-card { transition: none; }
  .suggestion-card:hover { transform: none; }
}
</style>
