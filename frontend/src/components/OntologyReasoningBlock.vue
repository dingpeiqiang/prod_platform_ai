/**
 * 本体推理解释链：结论 → 有序步骤 → 路径选择 → 关系图 → 证据折叠
 */
<template>
  <div v-if="chain" class="onto-reason">
    <div v-if="conclusion" class="onto-conclusion">
      <span class="kicker">归因结论</span>
      <p>{{ conclusion }}</p>
    </div>

    <ol v-if="reasoningSteps.length" class="onto-steps">
      <li
        v-for="(step, i) in visibleSteps"
        :key="step.id || i"
        class="onto-step"
        :class="{ latest: streaming && i === visibleSteps.length - 1 }"
      >
        <span class="idx">{{ i + 1 }}</span>
        <div class="step-body">
          <div class="step-head">
            <strong>{{ step.label }}</strong>
            <span v-if="step.ruleId" class="rule">{{ formatRule(step.ruleId) }}</span>
          </div>
          <p v-if="step.detail" class="step-detail">{{ step.detail }}</p>
        </div>
      </li>
    </ol>

    <div v-if="pathOptions.length" class="onto-paths" role="tablist" aria-label="根因路径">
      <button
        v-for="p in pathOptions"
        :key="p.rank"
        type="button"
        role="tab"
        class="path-chip"
        :class="{ active: activeRank === p.rank, primary: p.isPrimary }"
        :aria-selected="activeRank === p.rank"
        @click="selectPath(p.rank)"
      >
        <span class="rank">#{{ p.rank }}</span>
        <span class="name">{{ p.name }}</span>
        <span class="meta">{{ formatWeight(p.weight) }} · {{ p.typeCn || '' }}</span>
      </button>
    </div>

    <div v-if="activePathHint" class="onto-path-hint">
      <ol v-if="activePathHint.steps?.length">
        <li v-for="(s, si) in activePathHint.steps" :key="si">{{ s }}</li>
      </ol>
      <ul v-if="activePathHint.evidence?.length" class="ev-inline">
        <li v-for="(e, ei) in activePathHint.evidence" :key="ei">{{ e }}</li>
      </ul>
    </div>

    <OntologyChainViz
      :chain="chainForViz"
      :reveal-count="chainRevealCount"
      :streaming="streaming"
      :focus-relation-ids="focusRelationIds"
      compact
    />

    <div v-if="triples.length" class="onto-evidence">
      <button type="button" class="ev-toggle" @click="showEvidence = !showEvidence">
        {{ showEvidence ? '收起支撑数据' : '查看支撑数据' }}
        <span class="ev-count">{{ triples.length }}</span>
      </button>
      <ul v-if="showEvidence" class="ev-list">
        <li v-for="(t, ti) in triples" :key="ti">{{ formatTriple(t) }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import OntologyChainViz from './OntologyChainViz.vue'
import { formatRule, formatTriple, formatWeight } from '../utils/ontologyLabels.js'

const props = defineProps({
  preview: { type: Object, default: null },
  chain: { type: Object, default: null },
  chainRevealCount: { type: Number, default: 0 },
  streaming: { type: Boolean, default: false },
  activePathRank: { type: Number, default: null },
})

const emit = defineEmits(['update:activePathRank', 'path-select'])

const injectedRank = inject('rootCauseActiveRank', null)
const localRank = ref(1)
const showEvidence = ref(false)

const activeRank = computed({
  get() {
    if (props.activePathRank != null) return props.activePathRank
    if (injectedRank?.value != null) return injectedRank.value
    return localRank.value
  },
  set(v) {
    localRank.value = v
    if (injectedRank && typeof injectedRank === 'object' && 'value' in injectedRank) {
      injectedRank.value = v
    }
    emit('update:activePathRank', v)
    emit('path-select', v)
  },
})

watch(
  () => props.chain,
  () => {
    showEvidence.value = false
    if (props.activePathRank == null && !injectedRank) {
      localRank.value = 1
    }
  },
)

const conclusion = computed(
  () => props.preview?.conclusion || props.chain?.conclusion || props.preview?.narrative || '',
)

const reasoningSteps = computed(() => {
  if (props.preview?.reasoningSteps?.length) return props.preview.reasoningSteps
  const hops = props.chain?.hops || []
  if (!hops.length) return []
  return hops.map((h, i) => ({
    id: h.id || `hop-${i}`,
    label: h.object || h.predicate || `步骤 ${i + 1}`,
    detail: [h.subject, h.predicate, h.object].filter(Boolean).join(' · '),
    ruleId: h.rule,
  }))
})

const visibleSteps = computed(() => {
  const steps = reasoningSteps.value
  if (!props.streaming || !props.chainRevealCount) return steps
  // 流式时按 hop 进度逐步展示推理步骤（最多与步骤数对齐）
  const n = Math.min(steps.length, Math.max(1, Math.ceil((props.chainRevealCount / Math.max(props.chain?.hops?.length || 1, 1)) * steps.length)))
  return steps.slice(0, n)
})

const pathOptions = computed(() => props.preview?.pathOptions || [])

const activePathHint = computed(() =>
  pathOptions.value.find((p) => p.rank === activeRank.value) || null,
)

const focusRelationIds = computed(() => {
  const opt = activePathHint.value
  if (opt?.relationIds?.length) return opt.relationIds
  return []
})

const triples = computed(
  () => props.preview?.triples || props.chain?.triples || [],
)

const chainForViz = computed(() => {
  if (!props.chain) return null
  if (props.preview) {
    return {
      ...props.chain,
      summary: props.preview.summary || props.chain.summary,
      compliancePass: props.preview.compliancePass,
      blocked: props.preview.blocked,
      conclusion: props.preview.conclusion || props.chain.conclusion,
    }
  }
  return props.chain
})

function selectPath(rank) {
  activeRank.value = rank
}
</script>

<style scoped>
.onto-reason {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.onto-conclusion {
  padding: 8px 10px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ecfeff, #f8fafc);
  border: 1px solid #a5f3fc;
}

.onto-conclusion .kicker {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  color: #0f766e;
  letter-spacing: 0.02em;
  margin-bottom: 2px;
}

.onto-conclusion p {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
  color: #0f172a;
  line-height: 1.45;
}

.onto-steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.onto-step {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 6px 8px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.onto-step.latest {
  border-color: #93c5fd;
  background: #eff6ff;
}

.onto-step .idx {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #0f766e;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}

.step-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}

.step-head strong {
  font-size: 12px;
  color: #0f172a;
}

.step-head .rule {
  font-size: 10px;
  color: #0369a1;
}

.step-detail {
  margin: 2px 0 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.onto-paths {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.path-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  text-align: left;
  min-width: 96px;
  transition: border-color 0.15s, background 0.15s;
}

.path-chip:hover {
  border-color: #7dd3fc;
}

.path-chip.active {
  border-color: #0ea5e9;
  background: #f0f9ff;
  box-shadow: 0 0 0 1px rgba(14, 165, 233, 0.25);
}

.path-chip.primary:not(.active) {
  border-color: #fcd34d;
  background: #fffbeb;
}

.path-chip .rank {
  font-size: 10px;
  font-weight: 700;
  color: #0ea5e9;
}

.path-chip .name {
  font-size: 12px;
  font-weight: 650;
  color: #0f172a;
}

.path-chip .meta {
  font-size: 10px;
  color: #94a3b8;
}

.onto-path-hint {
  padding: 6px 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px dashed #cbd5e1;
}

.onto-path-hint ol,
.onto-path-hint ul {
  margin: 0;
  padding-left: 16px;
}

.onto-path-hint li {
  font-size: 11px;
  color: #334155;
  line-height: 1.4;
}

.ev-inline {
  margin-top: 4px !important;
  color: #64748b;
}

.onto-evidence {
  padding-top: 2px;
}

.ev-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  color: #0369a1;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.ev-count {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
}

.ev-list {
  margin: 6px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ev-list li {
  font-size: 11px;
  color: #475569;
  line-height: 1.4;
}
</style>
