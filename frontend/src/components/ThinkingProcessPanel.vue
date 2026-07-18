/**
 * 思考过程面板：时间线样式；本体推理为其中一环，内嵌网络图 + 推理预览
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
              <span class="type-chip" :class="step.type === 'ontology' ? 'ontology' : 'llm'">
                {{ step.type === 'ontology' ? '本体推理' : '模型思考' }}
              </span>
            </div>
            <!-- 本体环节：不再写路径摘要，细节只在网络图 / 推理链中展示 -->
            <div v-if="step.type !== 'ontology'" class="step-text">{{ localize(step.content) }}</div>

            <OntologyReasoningBlock
              v-if="step.type === 'ontology' && step.ontologyChain"
              :preview="step.ontologyPreview"
              :chain="step.ontologyChain"
              :chain-reveal-count="step.chainRevealCount || 0"
              :streaming="streaming && si === steps.length - 1"
            />
            <div
              v-else-if="step.type === 'ontology'"
              class="step-text"
            >{{ localize(step.content || '调用本体平台') }}</div>
          </div>
        </li>
      </ol>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import OntologyReasoningBlock from './OntologyReasoningBlock.vue'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
  streaming: { type: Boolean, default: false },
  localize: { type: Function, default: (t) => t },
})

defineEmits(['toggle'])

const ontoCount = computed(() => props.steps.filter((s) => s.type === 'ontology').length)
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
  margin-bottom: 2px;
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
