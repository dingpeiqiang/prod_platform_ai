<template>
  <div class="compliance-panel" :class="statusClass">
    <div class="cp-header">
      <div class="cp-title">
        <span class="cp-dot" />
        <span>合规检查</span>
      </div>
      <span class="cp-badge">{{ statusLabel }}</span>
    </div>

    <div v-if="!issues.length && compliancePass" class="cp-pass">
      <p>合规通过（R-C08）</p>
      <p class="cp-hint">无 HIGH 问题且必填齐全，可生成配置草稿</p>
    </div>

    <div v-else-if="!issues.length" class="cp-empty">
      <p>等待本体推理结果</p>
      <p class="cp-hint">智聊·对话配置后，冲突与必填问题将显示在此</p>
    </div>

    <ul v-else class="cp-list">
      <li
        v-for="(issue, idx) in issues"
        :key="`${issue.ruleId}-${idx}`"
        class="cp-item"
        :class="levelClass(issue.issueLevel)"
      >
        <div class="cp-item-top">
          <span class="cp-level">{{ issue.issueLevel || 'INFO' }}</span>
          <span class="cp-rule">{{ issue.ruleId }}</span>
          <span class="cp-type">{{ issue.issueType }}</span>
        </div>
        <p class="cp-msg">{{ issue.message }}</p>
        <div v-if="triplesOf(issue).length" class="cp-triples">
          <div class="cp-triples-title">证据三元组</div>
          <div v-for="(t, ti) in triplesOf(issue)" :key="ti" class="cp-triple">
            <span class="t-s">{{ t.s }}</span>
            <span class="t-p">— {{ t.p }} —</span>
            <span class="t-o">{{ t.o }}</span>
          </div>
        </div>
        <p v-else-if="issue.evidence?.length" class="cp-evidence">
          证据：{{ issue.evidence.join('；') }}
        </p>
      </li>
    </ul>

    <div v-if="inferredFields?.length" class="cp-inferred">
      <div class="cp-inferred-title">本体补全字段</div>
      <div
        v-for="f in inferredFields"
        :key="f.field"
        class="cp-inferred-row"
      >
        <code>{{ fieldLabel(f.field) }}</code>
        <span>{{ f.value }}</span>
        <em>{{ sourceLabel(f.fillSource) }}{{ f.rule ? ` · ${f.rule}` : '' }}</em>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  issues: { type: Array, default: () => [] },
  compliancePass: { type: Boolean, default: false },
  inferredFields: { type: Array, default: () => [] },
})

const hasHigh = computed(() =>
  (props.issues || []).some((i) => i.issueLevel === 'HIGH'),
)

const statusClass = computed(() => {
  if (props.compliancePass) return 'is-pass'
  if (hasHigh.value) return 'is-block'
  if (props.issues?.length) return 'is-warn'
  return 'is-idle'
})

const statusLabel = computed(() => {
  if (props.compliancePass) return '可提交'
  if (hasHigh.value) return '已阻断'
  if (props.issues?.length) return '待补全'
  return '待校验'
})

function levelClass(level) {
  if (level === 'HIGH') return 'level-high'
  if (level === 'MEDIUM') return 'level-medium'
  return 'level-low'
}

function triplesOf(issue) {
  if (issue?.triples?.length) {
    return issue.triples.map((t) => ({
      s: labelVal(t.s),
      p: labelRel(t.p),
      o: labelVal(t.o),
    }))
  }
  if (!issue?.evidence?.length) return []
  return issue.evidence
    .map((raw) => {
      const text = String(raw)
      if (text.includes('—')) {
        const parts = text.split('—').map((p) => p.trim())
        if (parts.length >= 3) return { s: labelVal(parts[0]), p: labelRel(parts[1]), o: labelVal(parts.slice(2).join('—')) }
        if (parts.length === 2) {
          const [left, right] = parts
          if (right.includes('=')) {
            const [k, v] = right.split('=')
            return { s: labelVal(left), p: labelRel(k), o: labelVal(v) }
          }
          return { s: labelVal(left), p: '关联', o: labelVal(right) }
        }
      }
      if (text.includes('=')) {
        const [k, v] = text.split('=')
        return { s: '当前草稿', p: labelRel(k), o: labelVal(v) }
      }
      return null
    })
    .filter(Boolean)
}

function fieldLabel(code) {
  const map = {
    includeVoice: '语音', includeData: '流量', includeBroadband: '宽带',
    offeringName: '商品名称', monthlyFee: '月费', bizScenario: '业务场景',
    targetUser: '目标用户', channelScope: '销售渠道', mutexGroup: '互斥组',
    basedOnTemplate: '配置模板', bindExistingMainPkg: '绑定在架主套餐',
  }
  return map[code] || code
}

function sourceLabel(src) {
  const map = { scenario_default: '场景缺省', template: '模板推荐', user_said: '用户表述' }
  return map[src] || src
}

function labelRel(p) {
  const map = {
    mutexGroup: '互斥组', inScenario: '所属场景', forTargetUser: '面向客群',
    hasElement: '包含要素', basedOnTemplate: '基于模板', bindExisting: '绑定在架商品',
  }
  return map[p] || p
}

function labelVal(v) {
  if (v == null) return '—'
  const map = {
    MAIN_PKG: '主套餐互斥组', 'OF-HF-128': '家庭融合畅享128', draft: '当前草稿', empty: '空',
  }
  return map[String(v)] || String(v)
}
</script>

<style scoped>
.compliance-panel {
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fafafa;
  padding: 12px;
  margin-bottom: 12px;
}

.compliance-panel.is-pass {
  border-color: #86efac;
  background: #f0fdf4;
}

.compliance-panel.is-block {
  border-color: #fca5a5;
  background: #fef2f2;
}

.compliance-panel.is-warn {
  border-color: #fcd34d;
  background: #fffbeb;
}

.cp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cp-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.cp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9ca3af;
}

.is-pass .cp-dot { background: #22c55e; }
.is-block .cp-dot { background: #ef4444; }
.is-warn .cp-dot { background: #f59e0b; }

.cp-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e5e7eb;
  color: #374151;
}

.is-pass .cp-badge { background: #bbf7d0; color: #166534; }
.is-block .cp-badge { background: #fecaca; color: #991b1b; }
.is-warn .cp-badge { background: #fde68a; color: #92400e; }

.cp-pass,
.cp-empty {
  font-size: 13px;
  color: #374151;
}

.cp-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.cp-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cp-item {
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.cp-item.level-high {
  border-color: #fecaca;
  background: #fff5f5;
}

.cp-item.level-medium {
  border-color: #fde68a;
  background: #fffbeb;
}

.cp-item-top {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
}

.cp-level {
  font-weight: 700;
  color: #b45309;
}

.level-high .cp-level { color: #dc2626; }

.cp-rule {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #4b5563;
}

.cp-type {
  color: #6b7280;
}

.cp-msg {
  margin: 0;
  font-size: 12px;
  color: #1f2937;
  line-height: 1.45;
}

.cp-evidence {
  margin: 4px 0 0;
  font-size: 11px;
  color: #6b7280;
  line-height: 1.4;
}

.cp-triples {
  margin-top: 8px;
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.cp-triples-title {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 6px;
}

.cp-triple {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  padding: 3px 0;
  color: #334155;
}

.t-s, .t-o {
  font-weight: 600;
  color: #0f172a;
}

.t-p {
  color: #64748b;
}

.cp-inferred {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #d1d5db;
}

.cp-inferred-title {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}

.cp-inferred-row {
  display: grid;
  grid-template-columns: 110px 1fr auto;
  gap: 6px;
  font-size: 11px;
  padding: 3px 0;
  color: #4b5563;
}

.cp-inferred-row code {
  font-size: 11px;
  color: #1d4ed8;
}

.cp-inferred-row em {
  font-style: normal;
  color: #059669;
  white-space: nowrap;
}
</style>
