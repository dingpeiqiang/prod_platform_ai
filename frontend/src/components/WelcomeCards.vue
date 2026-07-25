<template>
  <div class="welcome-container" :class="modeClass">
    <div class="welcome-content">
      <header class="brand-section">
        <p class="brand-eyebrow">{{ meta.eyebrow }}</p>
        <h1 class="brand-title">{{ meta.title }}</h1>
        <p class="brand-subtitle">{{ meta.subtitle }}</p>
        <ul class="capability-tags" aria-label="核心能力">
          <li v-for="tag in meta.tags" :key="tag">{{ tag }}</li>
        </ul>
      </header>

      <section class="scenarios-section" aria-labelledby="welcome-scenarios-title">
        <div class="section-head">
          <h2 id="welcome-scenarios-title" class="section-title">从这里开始</h2>
          <p class="section-hint">点击场景将示例填入输入框，确认后再发送；也可直接输入</p>
        </div>
        <div class="suggestion-cards">
          <button
            v-for="card in cards"
            :key="card.label"
            type="button"
            class="suggestion-card"
            @click="emit('suggest', card.text)"
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
              <svg v-else-if="card.icon === 'shield'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
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
import { computed } from 'vue'
import { ZHIDU_TEST_PROMPT } from '../data/zhiduTestDoc.js'

const props = defineProps({
  mode: { type: String, default: 'rd' },
})

const emit = defineEmits(['suggest'])

const modeClass = computed(() => (props.mode === 'ops' ? 'mode-ops' : 'mode-rd'))

const rdMeta = {
  eyebrow: 'AI 原生 · 产商品研发',
  title: '产商品研发助手',
  subtitle: '智聊·对话配置、智读·文件配置、智查·历史复用与智检·合规校验一体完成，让商品上架更快、更准、更合规。',
  tags: ['智聊·对话配置', '智读·文件配置', '智查·历史复用', '智检·合规校验'],
  footer: '本体负责填字段与拦冲突，大模型负责理解业务表达。',
}

const opsMeta = {
  eyebrow: 'AI 原生 · 产商品运营',
  title: '产商品运营助手',
  subtitle: '市场洞察、立项研判、异动归因与风险稽核一屏直达，用本体推理定位问题，用规则保障决策合规。',
  tags: ['市场洞察', '立项研判', '异动归因', '风险稽核'],
  footer: '本体负责推理与追溯，规则负责红线判定，大模型负责解释与表达。',
}

const rdCards = [
  {
    label: '智聊·对话配置',
    desc: '直接说业务诉求，自动补全配置字段并拦截冲突',
    example: '家庭融合套餐 158 元 / 500M',
    text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
    icon: 'chat',
    bg: '#eff6ff',
    color: '#2563eb',
  },
  {
    label: '智读·文件配置',
    desc: '粘贴或上传方案文档，按你的内容映射为多套合规配置草稿',
    example: '家庭融合测试方案',
    text: ZHIDU_TEST_PROMPT,
    icon: 'file',
    bg: '#ecfdf5',
    color: '#059669',
  },
  {
    label: '智查·历史复用',
    desc: '检索历史商品与成熟配置，快速复制复用',
    example: '近30天大学生套餐',
    text: '查一下近30天大学生套餐配置',
    icon: 'search',
    bg: '#f0f9ff',
    color: '#0284c7',
  },
  {
    label: '智检·合规校验',
    desc: '按套餐信息校验：已入库在架套餐或未入库草稿',
    example: '校园体验流量包0元 / 当前配置',
    text: '校验校园体验流量包0元是否符合在架规则',
    icon: 'shield',
    bg: '#fff7ed',
    color: '#c2410c',
  },
]

const opsCards = [
  {
    label: '市场洞察',
    desc: '自然语言检索在售商品、增长指标与竞品态势',
    example: '在售5G套餐与风险商品',
    text: '查一下在售5G套餐的增长趋势和风险商品',
    icon: 'chart',
    bg: '#ecfeff',
    color: '#0e7490',
  },
  {
    label: '立项研判',
    desc: '评估新品是否满足上线门槛与风险红线',
    example: '青春卡套餐能否立项',
    text: '评估新推出的青春卡套餐能否通过立项审核',
    icon: 'check',
    bg: '#fefce8',
    color: '#a16207',
  },
  {
    label: '异动归因',
    desc: '多跳关联推理，定位收入、留存、渠道变化主因',
    example: '家庭融合畅享128收入下滑',
    text: '分析家庭融合畅享128本月收入下滑原因',
    icon: 'trace',
    bg: '#f0fdf4',
    color: '#15803d',
  },
  {
    label: '风险稽核',
    desc: '批量识别零费、低效与长期零销商品并给处置建议',
    example: '筛查在架0元资费风险',
    text: '筛查所有在架的0元资费风险商品',
    icon: 'warn',
    bg: '#fff1f2',
    color: '#be123c',
  },
]

const meta = computed(() => (props.mode === 'ops' ? opsMeta : rdMeta))
const cards = computed(() => (props.mode === 'ops' ? opsCards : rdCards))
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

.welcome-content {
  width: 100%;
  max-width: 880px;
  text-align: center;
  margin-block: auto;
}

.brand-section { margin-bottom: 24px; }
.brand-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--welcome-accent);
}
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
  .suggestion-cards { grid-template-columns: 1fr; }
  .card-example { white-space: normal; }
}

@media (prefers-reduced-motion: reduce) {
  .suggestion-card { transition: none; }
  .suggestion-card:hover { transform: none; }
}
</style>
