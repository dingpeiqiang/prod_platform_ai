**
 * 思考过程面板：时间线样式；本体推理为其中一环，内嵌网络图 + 推理预览
 *
 * 支持富元数据展示：
 * - 步骤进度按面板内相对序号展示（如 1/2），不依赖后端全局 step/totalSteps
 * - metadata：关键数据标签（意图类型、置信度等）
 * - elapsed：步骤耗时
 * - details：可展开的详细信息（SPARQL、LLM 原始返回等）
 */
<template>
  <div class="think-panel" :class="{ streaming: streaming || isCatchingUp, collapsed: !expanded }">
    <button type="button" class="think-toggle" @click="onToggle">
      <svg
        class="toggle-icon"
        :class="{ expanded: expanded }"
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
      <span v-if="ontoCount" class="think-onto-tag">含推理分析 {{ ontoCount }} 处</span>
      <span v-if="streaming || isCatchingUp" class="think-live">进行中</span>
      <button
        v-if="streaming || isCatchingUp"
        type="button"
        class="think-skip-btn"
        @click.stop="emit('skip')"
        title="跳过思考动画"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="5 4 15 12 5 20 5 4"/>
          <line x1="19" y1="5" x2="19" y2="19"/>
        </svg>
        跳过
      </button>
    </button>

    <div v-show="expanded" class="think-body">
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
            'segment-start': isSegmentStart(step, si),
          }"
        >
          <!-- 分组小节标题：多意图时每个子问题前插入醒目标题与分隔 -->
          <div v-if="isSegmentStart(step, si)" class="segment-header">
            <span class="segment-badge">{{ step.segment }}</span>
          </div>

          <!-- 步骤行：左侧固定数字序号 + 标题，右侧完成对号/展开图标；点击整行展开详情 -->
          <button
            type="button"
            class="step-row"
            :disabled="!stepHasExtra(step, si)"
            @click="stepHasExtra(step, si) && toggleExtra(si)"
          >
            <span class="step-num" aria-hidden="true">{{ si + 1 }}</span>
            <span class="step-row-text" :class="{ 'is-loading': isRunning(step, si) }">
              {{ stepRowText(step, si) }}<span v-if="isRunning(step, si)" class="loading-dots" aria-hidden="true">…</span>
            </span>
            <!-- 完成后：轻量对号（绿色细线）；可展开时：小箭头 -->
            <svg
              v-if="isDone(step, si)"
              class="row-check"
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <svg
              v-else-if="stepHasExtra(step, si)"
              class="row-chevron"
              :class="{ expanded: expandedExtra[si] }"
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>

          <!-- 展开区：固定「输入 / 过程 / 输出」三行（缺数据段给简洁缺省文案） -->
          <div v-if="stepHasExtra(step, si) && expandedExtra[si]" class="step-extra-body">
            <div v-if="fromStepLabel(step)" class="extra-line">
              <span class="extra-key">承接</span>
              <span class="extra-val">↳ 承接自「{{ fromStepLabel(step) }}」的上一步输出</span>
            </div>

            <div class="extra-line">
              <span class="extra-key">输入</span>
              <span class="extra-val">{{ stepInputText(step) }}</span>
            </div>

            <div class="extra-line">
              <span class="extra-key">过程</span>
              <span class="extra-val">
                <span v-for="(seg, gi) in stepProcessSegments(step)" :key="gi" class="process-seg">{{ seg }}</span>
              </span>
            </div>

            <div class="extra-line">
              <span class="extra-key">输出</span>
              <span class="extra-val">{{ stepOutputText(step, si) }}</span>
            </div>

            <div v-if="stepBranchText(step)" class="extra-line">
              <span class="extra-key">分支</span>
              <span class="extra-val branch-val">{{ stepBranchText(step) }}</span>
            </div>

            <!-- 本体环节推理块（网络图等富内容，附加在过程之下） -->
            <template v-if="step.type === 'ontology' && step.ontologyChain">
              <OntologyReasoningBlock
                :preview="step.ontologyPreview"
                :chain="step.ontologyChain"
                :chain-reveal-count="step.chainRevealCount || 0"
                :streaming="(streaming || isCatchingUp) && isRunning(step, si)"
              />
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
import {
  toolLabel,
  paramLabel,
  paramValue,
  toolOutputSummary,
  toolOutputEntries,
} from '../utils/businessLabels.js'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
  streaming: { type: Boolean, default: false },
  localize: { type: Function, default: (t) => t },
})

const emit = defineEmits(['toggle', 'complete', 'skip'])

/**
 * 面板展开状态：
 * - 流式播放期间强制展开（用户实时看到思考推进）
 * - 播放完成后自动折叠为摘要条，点击摘要条可重新展开
 * - expanded 为组件内唯一事实来源；show prop 仅作初始同步（默认收起），不回写压制点击
 */
const expanded = ref(props.show !== false)

/** 点击标题：直接切换内部展开态（组件自身为唯一事实来源），并通知父级同步 showReasoning */
const onToggle = () => {
  expanded.value = !expanded.value
  emit('toggle')
}

watch(
  () => props.show,
  (val, oldVal) => {
    // 仅在外部显式从「收起态」变为「展开态」时同步（如用户点开其他消息的联动），避免与点击互搏
    if (val === true && oldVal === false) expanded.value = true
  },
)

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
    // 播放完成：自动折叠为摘要条，正文成为视觉焦点
    expanded.value = false
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
      expanded.value = true
      startOrContinuePlayback()
      return
    }
    if (!sessionPlayed.value) {
      snapToFull()
      // 历史消息瞬时全量渲染：默认折叠为摘要条（仅首次，不覆盖用户后续点击）
      expanded.value = false
      return
    }
    if (displayCount.value < len || headPhase.value === 'running') {
      startOrContinuePlayback()
    } else {
      playing.value = false
      // 播放完成：自动折叠为摘要条，正文成为视觉焦点
      expanded.value = false
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

const expandedExtra = reactive({})
const liveNow = ref(Date.now())
let liveTimer = null

/** 稳定 key：按 id 固定，避免 running→done 时整节点重建闪烁 */
const stepKey = (step, si) =>
  step.id
  || `${step.type || 'llm'}-${step.title || 'step'}-${si}`

/** 是否处于某分组小节的开头（该步骤带 segment 且与上一步步的 segment 不同，或为首步） */
const isSegmentStart = (step, si) => {
  if (!step || !step.segment) return false
  if (si <= 0) return true
  const prev = visibleSteps.value[si - 1]
  return !prev || prev.segment !== step.segment
}

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

const toggleExtra = (idx) => {
  expandedExtra[idx] = !expandedExtra[idx]
}

/** 步骤行文案：进行中带动作说明，完成后仅标题 */
const stepRowText = (step, index) => {
  if (isRunning(step, index)) {
    return displayStepContent(step, index)
  }
  return step.title || displayStepContent(step, index)
}

/** 步骤是否有可展开的详情（固定三段式：输入/过程/输出，所有完成步骤均可展开） */
const stepHasExtra = (step, si) => {
  if (isRunning(step, si)) return false
  if (step.goal || step.content || step.result) return true
  if (step.type === 'ontology' && step.ontologyChain) return true
  return !!(step.manualHint || step.details || step.io)
}

const stepStartedAt = (step) => step.waitingStartedAt || step.stepStartedAt || step.timestamp || null

const liveElapsedSeconds = (step) => {
  const startedAt = stepStartedAt(step)
  if (!startedAt) return 0
  return Math.max(0, Math.floor((liveNow.value - startedAt) / 1000))
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

/* 携带 io 数据的步骤（工具或思考环节）进入完成态时，呈现「输入 → 动作 → 输出」链条 */
const isToolIo = (step, si) =>
  step.io
  && !isRunning(step, si)
  && step.type !== 'ontology'

/**
 * 输入区需要隐藏的内部噪声键（与后端 ThinkingCopy.HIDDEN_INPUT_KEYS 对齐）：
 * 会话号/原始问题/内部码等对业务无意义，不展示。
 * 注意：text/draft/product_type 是 rd 工具的实际入参（配置需求/已有草稿/品类），放行展示。
 */
const HIDDEN_INPUT_KEYS = new Set([
  'session_id', 'sessionId', 'session', 'question', 'intent_type', 'intentType',
  'action', 'documentText',
  'config', 'maxEntities', 'limit', 'file_id', 'file_ids', 'fileId',
  'file_name', 'fileName',
])

/** 过滤后的业务可读输入项：跳过隐藏键与空值 */
const ioInputEntries = (step) => {
  const input = step.io?.input
  if (!input || typeof input !== 'object') return []
  return Object.entries(input)
    .filter(([key, val]) => !HIDDEN_INPUT_KEYS.has(String(key)) && val != null && val !== '')
    .map(([key, val]) => ({ key, val }))
}

/** 输入的原始 question（用户话术）——被隐藏键过滤掉，但「输入」行需要它兜底 */
const rawQuestion = (step) => {
  const q = step.io?.input?.question || step.io?.input?.text
  return q != null ? String(q) : ''
}

/**
 * 「输入」行文案：业务参数优先；无业务参数时回退用户话术截断；再无则缺省。
 */
const stepInputText = (step) => {
  const input = step.io?.input
  // 数据流承接型输入（后端 from_step 声明上游节点）：展示上游产出的实质内容
  if (input && typeof input === 'object' && input.from_step) {
    const parts = []
    if (input.structured_intent && typeof input.structured_intent === 'object') {
      const intentParts = Object.entries(input.structured_intent)
        .filter(([, v]) => v != null && v !== '')
        .map(([k, v]) => (k === 'action' ? `动作=${v}` : `${paramLabel(k)}：${paramValue(k, v)}`))
      if (intentParts.length) parts.push(intentParts.join('；'))
    }
    if (Array.isArray(input.upstream_outputs) && input.upstream_outputs.length) {
      parts.push(input.upstream_outputs
        .map((u) => u && u.summary ? String(u.summary) : '')
        .filter(Boolean)
        .join('；'))
    }
    if (input.missing_params) parts.push(`待补充：${input.missing_params}`)
    if (input.requirement) parts.push(String(input.requirement))
    if (parts.length) return parts.join('；')
  }
  const items = ioInputEntries(step)
  if (items.length) {
    return items
      .map((item) => (item.key === 'requirement' ? item.val : `${paramLabel(item.key)}：${paramValue(item.key, item.val)}`))
      .join('；')
  }
  const q = rawQuestion(step)
  if (q) return q.length > 40 ? q.slice(0, 40) + '…' : q
  return '本环节无需额外参数，按方案直接执行'
}

/** 步骤 id → 工作流节点中文名（承接行展示用） */
const FROM_STEP_LABELS = {
  intent: '识别需求',
  plan: '定下处理方案',
  execute: '执行处理',
  summarize: '汇总结果',
  clarify: '组织追问',
  confirm: '歧义确认',
}

/** 「承接」行：该步骤的输入来自哪个上游节点（后端 input.from_step 声明，无则不显示该行） */
const fromStepLabel = (step) => {
  const input = step.io?.input
  const from = input && typeof input === 'object' ? input.from_step : null
  if (!from) return ''
  const clean = String(from).replace(/^tool_/, '')
  if (clean.startsWith('tool')) {
    return toolLabel(clean) || clean
  }
  return FROM_STEP_LABELS[clean] || clean
}

/** 「分支」行：本轮实际命中的工作流分支（后端 output.branch_taken 下发） */
const stepBranchText = (step) => {
  const out = step.io?.output
  const taken = out && typeof out === 'object' ? (out.branch_taken || '') : ''
  return String(taken || '').trim()
}

/**
 * 「过程」行文案：组合多段信息——这步做什么（content）、为什么做（goal）、具体怎么做（manualHint）。
 * 去重后按顺序拼接，让业务人员看到比标题更丰满的执行过程。
 * 叙事规则：
 * - 工具步骤的 content 已含动作描述（后端下发 summary 类话术），仅在与标题不同时补充执行说明，避免重复标题
 * - 思考步骤的 goal 讲「为什么」、content 讲「做什么」，两者互补时保留；goal 是 content 的重复时丢弃
 */
const stepProcessSegments = (step) => {
  const segs = []
  const push = (t) => {
    const s = String(t || '').trim().replace(/…$/, '')
    if (s && !segs.includes(s)) segs.push(s)
  }
  if (step.type === 'tool') {
    const label = toolLabel(step.name || step.tool || '')
    if (label && label !== step.title && !String(step.content || '').includes(label)) {
      push(`执行「${label}」`)
    }
    push(step.content)
    push(step.manualHint)
    if (!segs.length) push(step.goal)
  } else {
    push(step.content)
    push(step.goal)
  }
  if (!segs.length) {
    if (step.type === 'ontology') return ['基于业务知识库做关联推理，把结论串成链路']
    return ['按既定业务流程处理']
  }
  return segs
}

/**
 * 「输出」行文案：io 摘要优先（thinking 步骤后端补齐 output.summary 后同构生效），
 * 其次 result，无则区分「处理中/无产出」。
 */
const stepOutputText = (step, si) => {
  if (isToolIo(step, si)) {
    const summary = ioOutputSummary(step)
    if (summary) return summary
    const entries = ioOutputEntries(step)
    if (entries.length) {
      return entries.map((e) => `${e.label}: ${e.value}`).join('；')
    }
  }
  const result = formatStepResult(step)
  if (result) return result
  if (isRunning(step, si)) return '处理中…'
  return '—'
}
const ioOutputSummary = (step) => {
  const output = step.io?.output
  if (!output || typeof output !== 'object') return ''
  if (output.summary) return String(output.summary)
  return ''
}

const ioOutputEntries = (step) => toolOutputEntries('tool', step.io?.output)

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

.think-skip-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  padding: 2px 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 6px;
  font-size: 11px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}

.think-skip-btn:hover {
  border-color: #93c5fd;
  color: #3b82f6;
  background: #f0f9ff;
}

.think-body {
  padding: 0 14px 12px;
  position: relative;
}

.think-timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* ── 步骤行：数字序号 + 标题 + 右侧小展开图标 ─────────────── */

.think-step {
  border-bottom: 1px solid #eef2f7;
}

.think-step:last-child {
  border-bottom: none;
}

/* 分组小节：多意图时在每个子问题步骤序列前插入标题分隔 */
.think-step.segment-start {
  border-top: 1px dashed rgba(120, 140, 255, 0.35);
}

.think-step.segment-start:first-child {
  border-top: none;
}

.segment-header {
  padding: 8px 0 2px;
}

.segment-badge {
  font-size: 12px;
  font-weight: 600;
  color: #4c5bff;
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

.step-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 8px 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.step-row:disabled {
  cursor: default;
}

/* 左侧数字序号：固定数字，朴素圆形底 */
.step-num {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  background: #eef2f7;
  box-sizing: border-box;
  transition: color 0.25s ease;
}

.think-step.done .step-num {
  color: #94a3b8;
}

.think-step.latest .step-num {
  color: #2563eb;
  background: #eff6ff;
}

.think-step.pending .step-num {
  opacity: 0.45;
}

.step-row-text {
  flex: 0 1 auto;
  min-width: 0;
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
  word-break: break-word;
}

.think-step.done .step-row-text {
  color: #475569;
}

.think-step.latest .step-row-text {
  color: #0f172a;
  font-weight: 550;
}

.step-row-text.is-loading {
  color: #2563eb;
}

.loading-dots {
  display: inline-block;
  min-width: 1em;
  animation: pulse-live 1.2s ease infinite;
}

/* 右侧轻量对号：细线绿色，完成即现 */
.row-check {
  flex-shrink: 0;
  color: #22c55e;
  animation: check-in 0.25s ease;
}

@keyframes check-in {
  from {
    opacity: 0;
    transform: scale(0.5);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* 右侧小展开图标：低调灰，hover 时着色（仅未完成步骤显示） */
.row-chevron {
  flex-shrink: 0;
  color: #cbd5e1;
  transition: transform 0.15s ease, color 0.15s ease;
}

.step-row:hover .row-chevron {
  color: #94a3b8;
}

.row-chevron.expanded {
  transform: rotate(180deg);
  color: #94a3b8;
}

/* ── 展开区：纯文字排版，缩进对齐标题 ─────────────────────── */

.step-extra-body {
  padding: 0 0 10px 26px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  animation: extra-in 0.2s ease;
}

@keyframes extra-in {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.extra-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.extra-key {
  flex-shrink: 0;
  width: 30px;
  color: #94a3b8;
}

.extra-val {
  flex: 1;
  min-width: 0;
  color: #475569;
  word-break: break-word;
}

/* 「分支」行：条件分支命中标注 */
.branch-val {
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 4px;
  padding: 0 6px;
  display: inline-block;
  font-size: 11px;
}

/* 「过程」行分段：每段独立成行，段间留小间距，视觉上更像分步说明 */
.process-seg {
  display: block;
}

.process-seg + .process-seg {
  margin-top: 3px;
}

.extra-inline {
  margin-right: 10px;
}

.extra-inline:last-child {
  margin-right: 0;
}

.extra-sub {
  color: #94a3b8;
  margin-right: 4px;
}

.extra-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.extra-tag {
  font-size: 11px;
  color: #64748b;
  background: #eef2f7;
  padding: 1px 7px;
  border-radius: 4px;
  white-space: nowrap;
}

.extra-details {
  margin: 0;
}

.extra-details pre {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #64748b;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'SF Mono', 'Fira Code', ui-monospace, monospace;
}

.think-step.pending {
  opacity: 0.45;
}

@keyframes pulse-live {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .think-step.step-enter,
  .think-live,
  .row-check,
  .step-extra-body {
    animation: none;
  }
  .think-step-enter-active,
  .think-step-leave-active,
  .think-step-move,
  .step-num,
  .row-chevron {
    transition: none;
  }
}
</style>
