<template>
  <aside class="insight-board">
    <div class="board-head">
      <div class="board-title">
        <span class="head-icon">📈</span>
        实时看板
        <span v-if="cards.length" class="head-count">{{ cards.length }}</span>
      </div>
      <button type="button" class="collapse-btn" :class="{ collapsed }" @click="collapsed = !collapsed" :title="collapsed ? '展开' : '收起'">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline v-if="collapsed" points="9 6 15 12 9 18"/>
          <polyline v-else points="15 6 9 12 15 18"/>
        </svg>
      </button>
    </div>

    <div v-if="!collapsed" class="board-body">
      <!-- 空态：会话尚无生产物，给出引导 -->
      <div v-if="!cards.length" class="board-empty">
        <div class="empty-icon">💡</div>
        <p class="empty-title">会话开始后，这里会实时沉淀 AI 的业务结论</p>
        <p class="empty-desc">每完成一轮对话，与当前场景相关的关键指标与结论要点会自动汇总到这里。</p>
      </div>

      <!-- 结论卡片流：最新在顶部，新条目高亮 -->
      <template v-else>
        <div class="board-caption">本会话生产物 · 按时间倒序</div>
        <TransitionGroup name="insight" tag="div" class="insight-cards">
          <div
            v-for="(card, idx) in cards"
            :key="card.id"
            class="insight-card"
            :class="[`tone-${card.tone}`, { fresh: idx === 0 && freshOn }]"
          >
            <div class="card-head">
              <span class="card-icon">{{ card.icon }}</span>
              <span class="card-scene">{{ card.sceneLabel }}</span>
              <span class="card-time">{{ formatTime(card.timestamp) }}</span>
            </div>
            <div class="card-metrics">
              <div v-for="(m, mi) in card.metrics" :key="mi" class="metric">
                <span class="metric-value" :class="`mv-${m.tone || 'neutral'}`">{{ m.value }}</span>
                <span class="metric-label">{{ m.label }}</span>
              </div>
            </div>
            <ul v-if="card.points.length" class="card-points">
              <li v-for="(p, pi) in card.points" :key="pi">{{ p }}</li>
            </ul>
          </div>
        </TransitionGroup>
      </template>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onUnmounted, watch } from 'vue'
import { extractInsights } from '../config/insightBoard.js'

const props = defineProps({
  /** 会话消息列表：每条 done 的助手消息提炼为一张结论卡 */
  messages: { type: Array, default: () => [] },
  /** 兼容旧签名（旧看板组件的 productConfig prop），新版提炼不再直接依赖 */
  productConfig: { type: Object, default: null },
})

const collapsed = ref(false)
/** 新结论入场高亮：有新卡片时短暂点亮 */
const freshOn = ref(false)
let freshTimer = 0

const cards = computed(() => extractInsights(props.messages).slice().reverse())

watch(
  () => cards.value[0]?.id,
  (id, prev) => {
    if (!id || id === prev) return
    freshOn.value = true
    if (freshTimer) clearTimeout(freshTimer)
    freshTimer = setTimeout(() => {
      freshOn.value = false
      freshTimer = 0
    }, 2400)
  },
)

onUnmounted(() => {
  if (freshTimer) clearTimeout(freshTimer)
})

const formatTime = (ts) => {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.insight-board {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  min-width: 0;
  background: #fbfbfd;
  border-left: 1px solid #e5e7eb;
}
.board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #eef0f3;
  flex-shrink: 0;
}
.board-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: #0f172a;
  font-size: 14px;
}
.head-icon { font-size: 14px; }
.head-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #0f766e;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 600;
}
.collapse-btn {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
}
.collapse-btn:hover { background: #e2e8f0; color: #475569; }
.board-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.board-caption {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.02em;
}
.board-empty {
  margin-top: 40px;
  text-align: center;
  padding: 0 12px;
}
.empty-icon { font-size: 28px; margin-bottom: 10px; }
.empty-title { font-size: 13px; font-weight: 600; color: #475569; margin: 0 0 6px; }
.empty-desc { font-size: 12px; color: #94a3b8; line-height: 1.7; margin: 0; }

.insight-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.insight-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left: 3px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  transition: box-shadow 0.3s, border-color 0.3s;
}
.insight-card.tone-good { border-left-color: #22c55e; }
.insight-card.tone-warn { border-left-color: #f59e0b; }
.insight-card.tone-bad { border-left-color: #ef4444; }
/* 新结论入场高亮：柔和描边渐隐 */
.insight-card.fresh {
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.28);
  border-color: #99f6e4;
}
.card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.card-icon { font-size: 13px; }
.card-scene {
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
  background: #f0fdfa;
  border: 1px solid #ccfbf1;
  border-radius: 999px;
  padding: 1px 8px;
}
.card-time {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
  font-family: ui-monospace, monospace;
}
.card-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
}
.metric {
  display: flex;
  flex-direction: column;
  min-width: 44px;
}
.metric-value {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.metric-value.mv-good { color: #16a34a; }
.metric-value.mv-warn { color: #d97706; }
.metric-value.mv-bad { color: #dc2626; }
.metric-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 1px;
}
.card-points {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.card-points li {
  font-size: 12px;
  color: #475569;
  line-height: 1.55;
  padding-left: 12px;
  position: relative;
  word-break: break-all;
}
.card-points li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 7px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #94a3b8;
}

/* 新卡片入场动画 */
.insight-enter-active {
  transition: opacity 0.35s ease, transform 0.35s ease;
}
.insight-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}

@media (prefers-reduced-motion: reduce) {
  .insight-card { transition: none; }
  .insight-enter-active { transition: none; }
}
</style>
