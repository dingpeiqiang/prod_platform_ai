/**
 * 本体工具推理过程块：在思考时间线「过程」下方，把本体工具的执行逻辑
 * 渲染为分阶段时间线（加载本体 → 启动引擎 → 规则触发 → 归因路径 → 证据沉淀），
 * 替代原先一行摘要式的 trace 文本，让业务人员看懂本体的推理逻辑。
 *
 * 数据来源（后端下发）：step.trace = [{stage, phase?, message}]；phase 存在时走结构化渲染，
 * 缺 phase 时回退为普通留痕行（兼容历史数据）。
 */
<template>
  <div v-if="phases.length || plainLines.length" class="onto-trace">
    <div class="onto-trace-head">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
      </svg>
      <span class="head-title">{{ headTitle }}</span>
      <span v-if="phases.length" class="head-count">{{ phases.length }} 个阶段</span>
    </div>

    <!-- 结构化阶段时间线：phase 存在（新后端下发） -->
    <ol v-if="phases.length" class="phase-list">
      <li
        v-for="(p, i) in phases"
        :key="i"
        class="phase-item"
        :class="{ primary: p.isPrimary }"
      >
        <span class="phase-idx">{{ i + 1 }}</span>
        <div class="phase-body">
          <div class="phase-head">
            <strong>{{ p.phase }}</strong>
            <span v-if="p.isPrimary" class="primary-tag">主因</span>
          </div>
          <p v-if="p.message" class="phase-msg">{{ p.message }}</p>
        </div>
      </li>
    </ol>

    <!-- 旧版留痕行：仅 message（历史数据兜底） -->
    <ul v-else class="plain-list">
      <li v-for="(line, i) in plainLines" :key="i">{{ line }}</li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { toolLabel } from '../utils/businessLabels.js'

const props = defineProps({
  /** 思考时间线步骤（需含 name/tool 与 trace） */
  step: { type: Object, default: null },
})

/** 本体阶段条目：仅保留 stage=ontology 且带 phase 的结构化项 */
const phases = computed(() => {
  const trace = props.step?.trace
  if (!Array.isArray(trace)) return []
  return trace
    .filter((t) => t && typeof t === 'object' && t.stage === 'ontology' && t.phase)
    .map((t) => ({
      phase: String(t.phase || ''),
      message: String(t.message || ''),
      isPrimary: /主因/.test(String(t.phase || '')),
    }))
})

/** 旧版留痕行（无 phase 的 message） */
const plainLines = computed(() => {
  const trace = props.step?.trace
  if (!Array.isArray(trace)) return []
  return trace
    .filter((t) => t && typeof t === 'object' && t.stage === 'ontology' && !t.phase)
    .map((t) => String(t.message || ''))
    .filter(Boolean)
})

const headTitle = computed(() => {
  const label = toolLabel(props.step?.name || props.step?.tool || '')
  return label ? `「${label}」本体推理过程` : '本体推理过程'
})
</script>

<style scoped>
.onto-trace {
  margin-top: 6px;
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.onto-trace-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #1d4ed8;
}

.head-title {
  font-size: 11px;
  font-weight: 700;
  color: #1e40af;
}

.head-count {
  margin-left: auto;
  font-size: 10px;
  color: #60a5fa;
}

.phase-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.phase-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.phase-item.primary {
  border-color: #fcd34d;
  background: #fffbeb;
}

.phase-idx {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}

.phase-item.primary .phase-idx {
  background: #fef3c7;
  color: #b45309;
}

.phase-body {
  min-width: 0;
}

.phase-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.phase-head strong {
  font-size: 12px;
  color: #0f172a;
}

.primary-tag {
  font-size: 10px;
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fcd34d;
  padding: 0 5px;
  border-radius: 999px;
}

.phase-msg {
  margin: 1px 0 0;
  font-size: 11px;
  color: #475569;
  line-height: 1.45;
  word-break: break-word;
}

.plain-list {
  margin: 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.plain-list li {
  font-size: 11px;
  color: #64748b;
  line-height: 1.45;
  word-break: break-word;
}
</style>
