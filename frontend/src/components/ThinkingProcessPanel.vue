/**
 * 思考过程面板：时间线样式；本体推理为其中一环，内嵌网络图 + 推理预览
 *
 * 支持富元数据展示：
 * - 步骤进度按面板内相对序号展示（如 1/2），不依赖后端全局 step/totalSteps
 * - metadata：关键数据标签（意图类型、置信度等）
 * - elapsed：步骤耗时
 * - details：可展开的详细信息（SPARQL、LLM 原始返回等）
 */
<template>
  <div class="think-panel" :class="{ streaming: streaming || isCatchingUp }">
    <button type="button" class="think-toggle" @click="$emit('toggle')">
      <svg
        class="toggle-icon"
        :class="{ expanded: show !== false }"
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <polyline points="9 18 15 12 9 6" />
      </svg>
      <span class="think-title">思考过程</span>
      <span class="think-count">{{ visibleStepCount }} 步</span>
      <span v-if="ontoCount" class="think-onto-tag">含本体环节 {{ ontoCount }}</span>
      <span v-if="streaming || isCatchingUp" class="think-live">进行中</span>
    </button>

    <div v-show="show !== false" class="think-body">
      <TransitionGroup name="think-step" tag="ol" class="think-timeline">
        <li
          v-for="(step, si) in visibleSteps"
          :key="stepKey(step, si)"
          class="think-step"
          :class="{
            latest: isRunning(step, si),
            done: isDone(step, si),
            pending: isPending(step, si),
            'is-ontology': step.type === 'ontology',
            'step-enter': isRunning(step, si),
          }"
        >
          <div class="rail" aria-hidden="true">
            <span class="rail-dot" />
            <span v-if="si < visibleSteps.length - 1" class="rail-line" />
          </div>

          <div class="step-main">
            <div class="step-meta">
              <span class="step-progress">
                {{ si + 1 }}/{{ steps.length || visibleSteps.length }}
              </span>
              <span class="type-chip" :class="step.type === 'ontology' ? 'ontology' : step.type === 'tool' ? 'tool' : 'llm'">
                {{ step.type === 'ontology' ? '知识推理' : step.type === 'tool' ? '工具调用' : '处理步骤' }}
              </span>
              <span
                v-if="step.title"
                class="step-action"
              >
                {{ step.title }}
              </span>
              <span v-if="displayElapsed(step, si) != null" class="step-elapsed">
                {{ formatElapsed(displayElapsed(step, si)) }}
              </span>
            </div>

            <!-- 本体环节：动作说明 + 内嵌推理块（结论由 OntologyReasoningBlock 展示） -->
            <template v-if="step.type === 'ontology' && step.ontologyChain">
              <div v-if="step.content" class="step-text">{{ step.content }}</div>
              <OntologyReasoningBlock
                :preview="step.ontologyPreview"
                :chain="step.ontologyChain"
                :chain-reveal-count="step.chainRevealCount || 0"
                :streaming="(streaming || isCatchingUp) && isRunning(step, si)"
              />
            </template>
            <template v-else>
              <!-- 主文本：动作说明（无独立 title 时作为标题） -->
              <div
                v-if="displayStepContent(step, si)"
                class="step-text"
                :class="{
                  'as-action': !step.title && !formatStepResult(step),
                  'is-loading': isRunning(step, si),
                }"
              >
                {{ displayStepContent(step, si) }}<span v-if="isRunning(step, si)" class="loading-dots" aria-hidden="true">…</span>
              </div>

              <!-- 步骤结果：完成后再露出 -->
              <div v-if="showStepResult(step, si)" class="step-result">
                <span class="result-label">结果</span>
                <span class="result-value">{{ formatStepResult(step) }}</span>
              </div>

              <!-- 元数据标签 -->
              <div v-if="hasMetadata(step) && showStepResult(step, si)" class="step-metadata">
                <span
                  v-for="(entry, ki) in metadataEntries(step)"
                  :key="ki"
                  class="meta-tag"
                  :class="metaTagClass(entry.key)"
                >
                  {{ entry.label }}: {{ entry.value }}
                </span>
              </div>

              <!-- 可展开的详情 -->
              <div v-if="step.details && showStepResult(step, si)" class="step-details-wrap">
                <button
                  type="button"
                  class="step-details-toggle"
                  @click.stop="toggleDetails(si)"
                >
                  <svg
                    class="details-arrow"
                    :class="{ expanded: expandedDetails[si] }"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <polyline points="9 18 15 12 9 6" />
                  </svg>
                  {{ expandedDetails[si] ? '收起详情' : '展开详情' }}
                </button>
                <div v-show="expandedDetails[si]" class="step-details-content">
                  <pre>{{ localizeDetails(step.details) }}</pre>
                </div>
              </div>
            </template>
          </div>
        </li>
      </TransitionGroup>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch, onUnmounted } from 'vue'
import OntologyReasoningBlock from './OntologyReasoningBlock.vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
  streaming: { type: Boolean, default: false },
  localize: { type: Function, default: (t) => t },
})

const emit = defineEmits(['toggle', 'complete'])

/** 每步「加载中」停留时长 */
const STEP_RUN_MS = 520
/** 完成后到下一步出现的间隔 */
const STEP_GAP_MS = 180

const ontoCount = computed(() => props.steps.filter((s) => s.type === 'ontology').length)

/**
 * 自上而下播放状态：
 * - displayCount：已出现在时间线上的步数（含当前加载中的那一步）
 * - headPhase：'running' | 'done' —— 最新一步是加载中还是已完成
 * - playing：是否处于播放（流式或流结束后补播）
 */
const displayCount = ref(0)
const headPhase = ref('done')
const playing = ref(false)
/** 本轮是否走过 streaming（区分历史瞬间全量 vs 需要补播） */
const sessionPlayed = ref(false)

/**
 * 待播放源步骤：流式/补播中过滤 pending；历史回放用全量。
 */
const sourceSteps = computed(() => {
  const list = props.steps || []
  if (!props.streaming && !sessionPlayed.value) return list
  const hasStatus = list.some((s) => s.status)
  if (!hasStatus) return list
  const active = list.filter((s) => s.status !== 'pending')
  return active.length ? active : list.slice(0, 1)
})

let playToken = 0
let playTimer = null
let completeSent = false

const clearPlayTimer = () => {
  if (playTimer) {
    clearTimeout(playTimer)
    playTimer = null
  }
}

const sleepPlay = (ms, token) =>
  new Promise((resolve) => {
    clearPlayTimer()
    playTimer = setTimeout(() => {
      playTimer = null
      resolve(token === playToken)
    }, ms)
  })

const signalComplete = (instant = false) => {
  if (completeSent && !instant) return
  completeSent = true
  emit('complete', { instant: !!instant })
}

/**
 * 自上而下串行播放：出现一步(running) → 完成(done+结果) → 再出现下一步
 */
const runPlayback = async () => {
  const token = ++playToken
  playing.value = true
  completeSent = false

  while (token === playToken) {
    const total = sourceSteps.value.length
    if (total <= 0) {
      await sleepPlay(120, token)
      if (token !== playToken) return
      if (!props.streaming) break
      continue
    }

    // 还没有展示第一步：先挂上 running
    if (displayCount.value === 0) {
      displayCount.value = 1
      headPhase.value = 'running'
      const ok = await sleepPlay(STEP_RUN_MS, token)
      if (!ok) return
      headPhase.value = 'done'
      const okGap = await sleepPlay(STEP_GAP_MS, token)
      if (!okGap) return
      continue
    }

    // 当前头步仍在 running：等它完成
    if (headPhase.value === 'running') {
      const ok = await sleepPlay(STEP_RUN_MS, token)
      if (!ok) return
      headPhase.value = 'done'
      const okGap = await sleepPlay(STEP_GAP_MS, token)
      if (!okGap) return
      continue
    }

    // 头步已 done，还有后续源步骤：再往下挂一步
    if (displayCount.value < total) {
      displayCount.value += 1
      headPhase.value = 'running'
      continue
    }

    // 已播完当前源；若仍在 streaming，等新步骤进来
    if (props.streaming) {
      const ok = await sleepPlay(160, token)
      if (!ok) return
      continue
    }

    // 流结束且播完
    break
  }

  if (token === playToken) {
    playing.value = false
    displayCount.value = Math.max(displayCount.value, sourceSteps.value.length)
    headPhase.value = 'done'
    signalComplete(false)
  }
}

const startOrContinuePlayback = () => {
  sessionPlayed.value = true
  if (playing.value) return
  runPlayback()
}

const snapToFull = () => {
  playToken += 1
  clearPlayTimer()
  playing.value = false
  displayCount.value = sourceSteps.value.length
  headPhase.value = 'done'
  sessionPlayed.value = false
  signalComplete(true)
}

watch(
  () => [props.streaming, sourceSteps.value.length],
  ([streaming, len]) => {
    if (streaming) {
      completeSent = false
      startOrContinuePlayback()
      return
    }
    if (!sessionPlayed.value) {
      snapToFull()
      return
    }
    if (displayCount.value < len || headPhase.value === 'running') {
      startOrContinuePlayback()
    } else {
      playing.value = false
      signalComplete(false)
    }
  },
  { immediate: true },
)

const isCatchingUp = computed(
  () => playing.value || (sessionPlayed.value && displayCount.value < sourceSteps.value.length),
)

/**
 * 展示用步骤：自上而下截取；最新一步在 running 时隐藏结果，呈现「加载中」
 */
const visibleSteps = computed(() => {
  const list = sourceSteps.value
  if (!list.length) return list

  // 历史瞬间全量
  if (!playing.value && !sessionPlayed.value) {
    return list
  }

  const n = Math.min(Math.max(displayCount.value, 0), list.length)
  if (n <= 0) return []

  return list.slice(0, n).map((step, i) => {
    const isHead = i === n - 1
    if (isHead && headPhase.value === 'running') {
      return {
        ...step,
        status: 'running',
        metadata: { ...(step.metadata || {}), phase: 'running' },
        // 加载中不展示结果/详情/本体块，完成后再露出
        result: null,
        details: null,
        ontologyChain: null,
        ontologyPreview: null,
        _playRunning: true,
        stepStartedAt: step.stepStartedAt || Date.now(),
      }
    }
    return {
      ...step,
      status: 'done',
      metadata: { ...(step.metadata || {}), phase: 'done' },
      _playRunning: false,
    }
  })
})

const visibleStepCount = computed(() => {
  const total = Math.max((props.steps || []).length, sourceSteps.value.length)
  if (playing.value || isCatchingUp.value) {
    return Math.max(visibleSteps.value.length, total)
  }
  return total
})

const expandedDetails = reactive({})
const liveNow = ref(Date.now())
let liveTimer = null

/** 稳定 key：按 id 固定，避免 running→done 时整节点重建闪烁 */
const stepKey = (step, si) =>
  step.id
  || `${step.type || 'llm'}-${step.title || 'step'}-${si}`

const isRunning = (step, si) => {
  if (step._playRunning) return true
  if (step.status === 'running' || step.metadata?.phase === 'running' || step.metadata?.phase === 'waiting_llm') {
    return true
  }
  return false
}

const isDone = (step, si) => {
  if (isRunning(step, si)) return false
  return step.status === 'done' || !step.status
}

const isPending = (step) => step.status === 'pending'

watch(
  () => props.streaming || playing.value,
  (active) => {
    if (liveTimer) {
      clearInterval(liveTimer)
      liveTimer = null
    }
    if (active) {
      liveNow.value = Date.now()
      liveTimer = setInterval(() => {
        liveNow.value = Date.now()
      }, 1000)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (liveTimer) clearInterval(liveTimer)
  playToken += 1
  clearPlayTimer()
})

const toggleDetails = (idx) => {
  expandedDetails[idx] = !expandedDetails[idx]
}

const isWaitingStep = (step) =>
  step._playRunning
  || step._waiting
  || step.status === 'running'
  || step.metadata?.phase === 'waiting_llm'
  || step.metadata?.phase === 'running'

const stepStartedAt = (step) => step.waitingStartedAt || step.stepStartedAt || step.timestamp || null

const liveElapsedSeconds = (step) => {
  const startedAt = stepStartedAt(step)
  if (!startedAt) return 0
  return Math.max(0, Math.floor((liveNow.value - startedAt) / 1000))
}

/** 流式进行中步骤：本地 1 秒读秒 */
const displayElapsed = (step, index) => {
  if (isDone(step, index) && step.elapsed != null && step.elapsed >= 0) {
    return step.elapsed
  }
  if (isRunning(step, index)) {
    return liveElapsedSeconds(step)
  }
  return step.elapsed != null ? step.elapsed : null
}

const displayStepContent = (step, index) => {
  let text = props.localize(step.content) || step.title || ''
  if (isRunning(step, index)) {
    // 加载中统一给一点进行中感
    if (text && !/[.。…]$/.test(text) && !/中$/.test(text)) {
      text = text.replace(/[.。…]?$/, '')
    }
    const sec = liveElapsedSeconds(step)
    if (/已等待\s*\d+s/.test(text)) {
      return text.replace(/已等待\s*\d+s/, `已等待 ${sec}s`)
    }
    return text
  }
  return text
}

/** 仅完成态展示结果 */
const showStepResult = (step, si) => {
  if (isRunning(step, si)) return false
  return !!formatStepResult(step)
}

/** 格式化步骤结果：支持 string / number / 对象摘要 */
const formatStepResult = (step) => {
  const raw = step.result
  if (raw == null || raw === '') return ''
  if (typeof raw === 'string' || typeof raw === 'number' || typeof raw === 'boolean') {
    return String(raw)
  }
  if (Array.isArray(raw)) {
    if (!raw.length) return '（空）'
    if (raw.every((x) => typeof x === 'string' || typeof x === 'number')) {
      return raw.join('，')
    }
    return JSON.stringify(raw, null, 0)
  }
  if (typeof raw === 'object') {
    const keys = Object.keys(raw)
    if (!keys.length) return '（空）'
    if (raw.summary) return String(raw.summary)
    if (raw.message) return String(raw.message)
    const pairs = keys.slice(0, 6).map((k) => `${k}=${raw[k]}`)
    return pairs.join('，') + (keys.length > 6 ? '…' : '')
  }
  return String(raw)
}

/** 格式化耗时显示 */
const formatElapsed = (seconds) => {
  if (seconds == null || seconds < 0) return ''
  if (seconds === 0) return '0s'
  if (seconds < 0.01) return '<10ms'
  if (seconds < 1) return Math.round(seconds * 1000) + 'ms'
  if (seconds < 60) return (Number.isInteger(seconds) ? seconds : seconds.toFixed(1)) + 's'
  return Math.floor(seconds / 60) + 'm ' + Math.round(seconds % 60) + 's'
}

/** 检查步骤是否有业务侧可展示的 metadata（隐藏 Token 等技术字段） */
const hasMetadata = (step) => {
  if (!step.metadata) return false
  const meta = step.metadata
  return meta.intentLabel
    || meta.confidence != null
    || meta.resultCount != null
    || meta.ruleCount != null
    || meta.evidenceCount != null
    || meta.verdict
    || meta.offeringName
    || meta.offeringId
    || meta.highPriorityCount != null
    || meta.alertCount != null
    || meta.openWorkOrderCount != null
    || meta.compareCount != null
    || meta.reasonEngine
    || meta.tool
    || meta.scannedCount != null
    || meta.suggestDelistCount != null
    || meta.policySetId
}

/** 提取 metadata 为业务可读标签 */
const metadataEntries = (step) => {
  if (!step.metadata) return []
  const meta = step.metadata
  const entries = []

  if (meta.intentLabel) {
    entries.push({ key: 'intent', label: '业务意图', value: meta.intentLabel })
  }
  if (meta.confidence != null) {
    const c = Number(meta.confidence)
    const pct = c <= 1 ? Math.round(c * 100) : Math.round(c)
    entries.push({ key: 'confidence', label: '把握度', value: pct + '%' })
  }
  if (meta.offeringName || meta.offeringId) {
    entries.push({
      key: 'target',
      label: '分析对象',
      value: meta.offeringName || meta.offeringId,
    })
  }
  if (meta.resultCount != null) {
    entries.push({ key: 'result', label: '命中', value: meta.resultCount + ' 条' })
  }
  if (meta.compareCount != null) {
    entries.push({ key: 'result', label: '方案', value: meta.compareCount + ' 套' })
  }
  if (meta.alertCount != null) {
    entries.push({ key: 'result', label: '告警', value: meta.alertCount + ' 条' })
  }
  if (meta.highPriorityCount != null) {
    entries.push({ key: 'verdict', label: '高优', value: meta.highPriorityCount + ' 条' })
  }
  if (meta.openWorkOrderCount != null) {
    entries.push({ key: 'result', label: '工单', value: meta.openWorkOrderCount + ' 张' })
  }
  if (meta.scannedCount != null) {
    entries.push({ key: 'result', label: '扫描', value: meta.scannedCount + ' 条' })
  }
  if (meta.suggestDelistCount != null) {
    entries.push({ key: 'verdict', label: '建议下架', value: String(meta.suggestDelistCount) })
  }
  if (meta.verdict) {
    entries.push({ key: 'verdict', label: '结论', value: meta.verdict })
  }
  if (meta.policySetId) {
    entries.push({ key: 'policy', label: '策略集', value: String(meta.policySetId) })
  }
  if (meta.reasonEngine) {
    entries.push({ key: 'engine', label: '推理引擎', value: String(meta.reasonEngine) })
  }
  if (meta.ruleCount != null) {
    entries.push({ key: 'rule', label: '参考规则', value: meta.ruleCount + ' 条' })
  }
  if (meta.evidenceCount != null) {
    entries.push({ key: 'evidence', label: '支撑证据', value: meta.evidenceCount + ' 条' })
  }
  if (meta.tool) {
    entries.push({ key: 'tool', label: '工具', value: String(meta.tool) })
  }

  return entries
}

/** metadata 标签样式类 */
const metaTagClass = (key) => {
  switch (key) {
    case 'intent': return 'meta-intent'
    case 'confidence': return 'meta-confidence'
    case 'policy':
    case 'verdict': return 'meta-policy'
    case 'rule':
    case 'evidence': return 'meta-rule'
    case 'result':
    case 'target': return 'meta-result'
    case 'engine':
    case 'tool': return 'meta-engine'
    default: return ''
  }
}

/** 详情文案：去掉规则编码等技术后缀，便于业务阅读 */
const localizeDetails = (raw) => {
  if (!raw) return ''
  let text = props.localize(String(raw))
  text = text
    .replace(/引用规则\s*[:：]/g, '参考规则：')
    .replace(/命中规则\s*[:：]/g, '命中规则：')
    .replace(/（R-[A-Z0-9-]+）/gi, '')
    .replace(/\bR-[A-Z0-9-]+\b/gi, '')
    .replace(/SPARQL|Prompt|Token|LLM|ontology/gi, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
  return text
}
</script>

<style scoped>
.think-panel {
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  overflow: hidden;
}

.think-panel.streaming {
  border-color: #bfdbfe;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.08);
}

.think-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: #334155;
}

.toggle-icon {
  transition: transform 0.2s ease;
  color: #64748b;
  flex-shrink: 0;
}

.toggle-icon.expanded {
  transform: rotate(90deg);
}

.think-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.think-count {
  font-size: 11px;
  color: #94a3b8;
}

.think-onto-tag {
  font-size: 11px;
  color: #1d4ed8;
  background: #dbeafe;
  padding: 1px 8px;
  border-radius: 999px;
}

.think-live {
  margin-left: auto;
  font-size: 11px;
  color: #2563eb;
  animation: pulse-live 1.2s ease infinite;
}

.think-body {
  padding: 0 12px 12px;
  position: relative;
}

.think-timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}

.think-step {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: 8px;
}

/* TransitionGroup 入场：分步揭示时每步淡入上移 */
.think-step-enter-active {
  transition: opacity 0.38s ease, transform 0.38s ease;
}
.think-step-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.think-step-leave-active {
  transition: opacity 0.18s ease;
  position: absolute;
  width: 100%;
  pointer-events: none;
}
.think-step-leave-to {
  opacity: 0;
}
.think-step-move {
  transition: transform 0.32s ease;
}

/* 仅最新一步播入场，避免列表更新时已有步骤「全部刷新」闪动 */
.think-step.step-enter {
  animation: none;
}

.rail {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 6px;
}

.rail-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #cbd5e1;
  box-sizing: border-box;
  z-index: 1;
  flex-shrink: 0;
}

.rail-line {
  flex: 1;
  width: 2px;
  min-height: 14px;
  margin-top: 4px;
  background: #cbd5e1;
  border-radius: 1px;
}

.think-step.done .rail-dot {
  border-color: #64748b;
  background: #64748b;
}

.think-step.is-ontology.done .rail-dot {
  border-color: #2563eb;
  background: #2563eb;
}

.think-step.latest .rail-dot {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.2);
}

.think-step.pending {
  opacity: 0.45;
}

.think-step.pending .rail-dot {
  border-color: #cbd5e1;
  background: #fff;
}

.step-main {
  min-width: 0;
  padding: 2px 0 12px;
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.step-progress {
  font-size: 10px;
  font-weight: 700;
  color: #2563eb;
  background: #eff6ff;
  padding: 0 6px;
  border-radius: 4px;
  line-height: 1.6;
}

.type-chip {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.5;
  padding: 0 7px;
  border-radius: 4px;
}

.type-chip.llm {
  background: #f1f5f9;
  color: #475569;
}

.type-chip.ontology {
  background: #dbeafe;
  color: #1d4ed8;
}

.type-chip.tool {
  background: #fef3c7;
  color: #b45309;
}

.step-elapsed {
  font-size: 10px;
  color: #94a3b8;
  margin-left: auto;
}

.step-action {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.step-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}

.step-text.as-action {
  font-weight: 550;
}

.think-step.latest .step-text {
  color: #0f172a;
  font-weight: 550;
}

.step-text.is-loading {
  color: #2563eb;
}

.loading-dots {
  display: inline-block;
  min-width: 1em;
  animation: pulse-live 1.2s ease infinite;
}

.step-result {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  animation: result-in 0.32s ease;
}

.result-label {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.6;
  color: #15803d;
  background: #dcfce7;
  padding: 0 6px;
  border-radius: 4px;
}

.result-value {
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
  word-break: break-word;
}

.think-step.is-ontology .step-meta {
  margin-bottom: 0;
}

.think-step.is-ontology .step-text {
  margin-bottom: 6px;
}

/* 元数据标签 */
.step-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.meta-tag {
  font-size: 10px;
  line-height: 1.5;
  padding: 1px 7px;
  border-radius: 4px;
  white-space: nowrap;
}

.meta-intent {
  background: #ede9fe;
  color: #6d28d9;
}

.meta-confidence {
  background: #dcfce7;
  color: #16a34a;
}

.meta-tokens {
  background: #fef3c7;
  color: #b45309;
}

.meta-policy {
  background: #fce7f3;
  color: #be185d;
}

.meta-rule {
  background: #ffedd5;
  color: #c2410c;
}

.meta-result {
  background: #dbeafe;
  color: #1d4ed8;
}

.meta-engine {
  background: #e0e7ff;
  color: #4338ca;
}

.meta-ontology {
  background: #e0f2fe;
  color: #0369a1;
}

/* 详情展开 */
.step-details-wrap {
  margin-top: 6px;
}

.step-details-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 6px;
  font-size: 11px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.step-details-toggle:hover {
  border-color: #93c5fd;
  color: #3b82f6;
  background: #f0f9ff;
}

.details-arrow {
  transition: transform 0.15s ease;
}

.details-arrow.expanded {
  transform: rotate(90deg);
}

.step-details-content {
  margin-top: 6px;
  padding: 8px 10px;
  background: #f1f5f9;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.step-details-content pre {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', 'Fira Code', ui-monospace, monospace;
}

@keyframes step-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes result-in {
  from {
    opacity: 0;
    transform: translateY(3px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes pulse-live {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .think-step.step-enter,
  .think-live,
  .step-result {
    animation: none;
  }
  .think-step-enter-active,
  .think-step-leave-active,
  .think-step-move {
    transition: none;
  }
}
</style>
