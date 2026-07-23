/**
 * 思考过程面板：时间线样式；本体推理为其中一环，内嵌网络图 + 推理预览
 *
 * 支持富元数据展示：
 * - step / totalSteps：步骤进度（如 2/4）
 * - metadata：关键数据标签（意图类型、置信度等）
 * - elapsed：步骤耗时
 * - details：可展开的详细信息（SPARQL、LLM 原始返回等）
 */
<template>
  <div class="think-panel" :class="{ streaming }">
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
      <span class="think-title">{{ streaming ? '正在思考' : '思考过程' }}</span>
      <span class="think-count">{{ steps.length }} 步</span>
      <span v-if="ontoCount" class="think-onto-tag">含本体环节 {{ ontoCount }}</span>
      <span v-if="streaming" class="think-live">进行中</span>
    </button>

    <div v-show="show !== false" class="think-body">
      <ol class="think-timeline">
        <li
          v-for="(step, si) in steps"
          :key="si"
          class="think-step"
          :class="{
            latest: streaming && si === steps.length - 1,
            done: !streaming || si < steps.length - 1,
            'is-ontology': step.type === 'ontology',
          }"
        >
          <div class="rail" aria-hidden="true">
            <span class="rail-dot" />
            <span v-if="si < steps.length - 1" class="rail-line" />
          </div>

          <div class="step-main">
            <div class="step-meta">
              <span class="step-progress" v-if="step.metadata?.step">
                {{ step.metadata.step }}{{ step.metadata.totalSteps ? '/' + Math.min(step.metadata.totalSteps, steps.length) : '' }}
              </span>
              <span class="type-chip" :class="step.type === 'ontology' ? 'ontology' : 'llm'">
                {{ step.type === 'ontology' ? '本体推理' : '模型思考' }}
              </span>
              <span v-if="step.elapsed != null && step.elapsed >= 0" class="step-elapsed">
                {{ formatElapsed(step.elapsed) }}
              </span>
            </div>

            <!-- 本体环节：内嵌网络图 -->
            <template v-if="step.type === 'ontology' && step.ontologyChain">
              <OntologyReasoningBlock
                :preview="step.ontologyPreview"
                :chain="step.ontologyChain"
                :chain-reveal-count="step.chainRevealCount || 0"
                :streaming="streaming && si === steps.length - 1"
              />
            </template>
            <template v-else>
              <!-- 主文本 -->
              <div class="step-text">{{ localize(step.content) }}</div>

              <!-- 元数据标签 -->
              <div v-if="hasMetadata(step)" class="step-metadata">
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
              <div v-if="step.details" class="step-details-wrap">
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
                  <pre>{{ step.details }}</pre>
                </div>
              </div>
            </template>
          </div>
        </li>
      </ol>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import OntologyReasoningBlock from './OntologyReasoningBlock.vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
  streaming: { type: Boolean, default: false },
  localize: { type: Function, default: (t) => t },
})

defineEmits(['toggle'])

const ontoCount = computed(() => props.steps.filter((s) => s.type === 'ontology').length)

const expandedDetails = reactive({})

const toggleDetails = (idx) => {
  expandedDetails[idx] = !expandedDetails[idx]
}

/** 格式化耗时显示 */
const formatElapsed = (seconds) => {
  if (seconds == null || seconds < 0) return ''
  if (seconds < 0.01) return '<10ms'
  if (seconds < 1) return Math.round(seconds * 1000) + 'ms'
  if (seconds < 60) return seconds.toFixed(1) + 's'
  return Math.floor(seconds / 60) + 'm ' + Math.round(seconds % 60) + 's'
}

/** 检查步骤是否有有效的 metadata */
const hasMetadata = (step) => {
  if (!step.metadata) return false
  const meta = step.metadata
  // 只显示有意义的字段
  return meta.intentLabel || meta.confidence != null || meta.inputTokens || meta.resultCount != null || meta.policySetId || meta.ruleCount != null || meta.evidenceCount != null
}

/** 提取 metadata 为标签数组 */
const metadataEntries = (step) => {
  if (!step.metadata) return []
  const meta = step.metadata
  const entries = []

  if (meta.intentLabel) {
    entries.push({ key: 'intent', label: '意图', value: meta.intentLabel })
  }
  if (meta.confidence != null) {
    entries.push({ key: 'confidence', label: '置信度', value: Math.round(meta.confidence * 100) + '%' })
  }
  if (meta.inputTokens) {
    entries.push({ key: 'tokens', label: 'Token', value: meta.inputTokens + ' in / ' + (meta.outputTokens || 0) + ' out' })
  }
  if (meta.resultCount != null) {
    entries.push({ key: 'result', label: '结果', value: meta.resultCount + ' 条' })
  }
  if (meta.policySetId) {
    entries.push({ key: 'policy', label: '策略集', value: meta.policySetId })
  }
  if (meta.verdict) {
    entries.push({ key: 'verdict', label: '结论', value: meta.verdict })
  }
  if (meta.ruleCount != null) {
    entries.push({ key: 'rule', label: '规则', value: meta.ruleCount + ' 条' })
  }
  if (meta.evidenceCount != null) {
    entries.push({ key: 'evidence', label: '证据', value: meta.evidenceCount + ' 条' })
  }
  if (meta.ontologyCount != null) {
    entries.push({ key: 'ontology', label: '本体', value: meta.ontologyCount + ' 个' })
  }

  return entries
}

/** metadata 标签样式类 */
const metaTagClass = (key) => {
  switch (key) {
    case 'intent': return 'meta-intent'
    case 'confidence': return 'meta-confidence'
    case 'tokens': return 'meta-tokens'
    case 'policy':
    case 'verdict': return 'meta-policy'
    case 'rule':
    case 'evidence': return 'meta-rule'
    case 'result': return 'meta-result'
    case 'ontology': return 'meta-ontology'
    default: return ''
  }
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
  animation: step-in 0.24s ease;
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

.step-elapsed {
  font-size: 10px;
  color: #94a3b8;
  margin-left: auto;
}

.step-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}

.think-step.latest .step-text {
  color: #0f172a;
  font-weight: 550;
}

.think-step.is-ontology .step-meta {
  margin-bottom: 0;
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

@keyframes pulse-live {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .think-step,
  .think-live {
    animation: none;
  }
}
</style>
