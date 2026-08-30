<template>
  <ElDrawer
    v-model="visible"
    title="商品配置预览"
    direction="rtl"
    size="520px"
    :with-header="true"
  >
    <template #header>
      <div class="preview-header">
        <h3>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
          商品配置预览
          <span class="readonly-tag">只读</span>
        </h3>
      </div>
    </template>

    <div v-if="product" class="preview-body">
      <div class="preview-meta">
        <div class="pm-name">{{ product.name }}</div>
        <p v-if="product.desc" class="pm-desc">{{ product.desc }}</p>
        <div class="product-tags">
          <span class="status-tag" :class="statusMeta.statusClass">{{ statusMeta.statusText }}</span>
          <span class="status-tag" :class="statusMeta.auditClass">{{ statusMeta.auditText }}</span>
        </div>
      </div>

      <section v-for="group in fieldGroups" :key="group.label" class="field-group">
        <h4 class="group-title">{{ group.label }}</h4>
        <div class="field-grid">
          <div v-for="f in group.fields" :key="f.fieldCode" class="field-row">
            <span class="field-label">
              {{ f.fieldName }}
              <i v-if="f.required" class="req">*</i>
            </span>
            <span class="field-value">
              {{ formatValue(f) }}<span v-if="f.unit && hasValue(f)" class="field-unit">{{ f.unit }}</span>
              <em v-if="f.fillSource" class="fill-src">{{ f.fillSource }}</em>
            </span>
          </div>
        </div>
      </section>

      <section v-if="product.issues?.length" class="issue-group">
        <h4 class="group-title warn">合规问题（{{ product.issues.length }}）</h4>
        <ul class="issue-list">
          <li v-for="(issue, idx) in product.issues" :key="idx" class="issue-item" :class="issueClass(issue)">
            <span class="issue-rule">{{ issue.ruleId || '规则' }}</span>
            <span class="issue-msg">{{ issue.message }}</span>
          </li>
        </ul>
      </section>
      <section v-else-if="product.compliancePass" class="issue-group">
        <div class="compliance-ok">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          合规校验通过
        </div>
      </section>
    </div>
    <div v-else class="empty-state">该商品配置不存在或已被删除</div>
  </ElDrawer>
</template>

<script setup>
import { computed } from 'vue'
import { ElDrawer } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** previewProduct(id) 产出：{ id, name, desc, status, auditStatus, compliancePass, issues, schema, formData } */
  product: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const statusMeta = computed(() => {
  const p = props.product || {}
  const statusText = p.status === 'draft' ? '草稿' : p.status === 'submitted' ? '待审批' : '已完成'
  const statusClass =
    p.status === 'draft' ? 'status-draft' : p.status === 'submitted' ? 'status-approval' : 'status-submitted'
  const auditText = p.auditStatus === 'pass' ? '已通过' : p.auditStatus === 'fail' ? '不通过' : '未稽核'
  const auditClass =
    p.auditStatus === 'pass' ? 'status-pass' : p.auditStatus === 'fail' ? 'status-fail' : 'status-pending'
  return { statusText, statusClass, auditText, auditClass }
})

/**
 * 模板 schema 字段按 section 分组（顺序对齐模板 sections）；
 * 无 sections 的 mock schema 平铺为「配置字段」；(兼容) 重复字段不在预览中重复展示
 */
const fieldGroups = computed(() => {
  const schema = props.product?.schema
  if (!schema) return []
  const fields = (schema.fields || []).filter((f) => !f.fieldName?.includes('(兼容)'))
  const sections = Array.isArray(schema.sections) ? schema.sections : []
  const labelByCode = new Map(sections.map((s) => [s.code, s.label || s.code]))
  const groups = []
  const byLabel = new Map()
  const ensureGroup = (label) => {
    if (byLabel.has(label)) return byLabel.get(label)
    const g = { label, fields: [] }
    byLabel.set(label, g)
    groups.push(g)
    return g
  }
  for (const s of sections) ensureGroup(s.label || s.code)
  const fallback = ensureGroup(sections.length ? '其他配置' : '配置字段')
  for (const f of fields) {
    const label = f.section ? labelByCode.get(f.section) : null
    ;(label ? ensureGroup(label) : fallback).fields.push(f)
  }
  return groups.filter((g) => g.fields.length)
})

function rawValue(field) {
  const raw = field.value ?? props.product?.formData?.[field.fieldCode]
  return raw
}

function hasValue(field) {
  const raw = rawValue(field)
  return raw !== null && raw !== undefined && raw !== ''
}

function formatValue(field) {
  const raw = rawValue(field)
  if (!hasValue(field)) return '—'
  if (Array.isArray(raw)) return raw.map((v) => optionLabel(field, v)).join('、')
  if (typeof raw === 'object') return JSON.stringify(raw)
  return optionLabel(field, raw)
}

function optionLabel(field, value) {
  const opt = (field.options || []).find((o) => String(o.value) === String(value))
  return opt ? opt.label : String(value)
}

function issueClass(issue) {
  const level = String(issue.issueLevel || issue.level || '').toUpperCase()
  if (level === 'HIGH' || level === 'ERROR') return 'level-high'
  if (level === 'MEDIUM' || level === 'WARN') return 'level-medium'
  return 'level-low'
}
</script>

<style scoped>
.preview-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.readonly-tag {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
}

.preview-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-meta .pm-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary, #1e293b);
}

.preview-meta .pm-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin: 6px 0 8px;
  line-height: 1.45;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.status-pending {
  background: #f1f5f9;
  color: #64748b;
}

.status-pass {
  background: #f6ffed;
  color: #52c41a;
}

.status-fail {
  background: #fff1f0;
  color: #ff4d4f;
}

.status-draft {
  background: #fffbe6;
  color: #faad14;
}

.status-submitted {
  background: #f6ffed;
  color: #52c41a;
}

.status-approval {
  background: #dbeafe;
  color: #3b82f6;
}

.field-group {
  background: var(--bg-primary, #fff);
  border: 1px solid var(--border-default, #e2e8f0);
  border-radius: 10px;
  padding: 12px 14px;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin: 0 0 10px;
}

.group-title.warn {
  color: #faad14;
}

.field-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.field-label {
  flex-shrink: 0;
  max-width: 45%;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.field-label .req {
  color: #ff4d4f;
  font-style: normal;
  margin-left: 2px;
}

.field-value {
  font-size: 13px;
  color: var(--text-primary, #1e293b);
  text-align: right;
  word-break: break-all;
}

.field-unit {
  margin-left: 3px;
  font-size: 11px;
  color: var(--text-tertiary, #94a3b8);
}

.fill-src {
  margin-left: 6px;
  font-size: 10px;
  font-style: normal;
  padding: 1px 5px;
  border-radius: 3px;
  background: #eff6ff;
  color: #3b82f6;
}

.issue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.issue-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  padding: 6px 8px;
  border-radius: 6px;
}

.issue-item.level-high {
  background: #fff1f0;
  color: #ff4d4f;
}

.issue-item.level-medium {
  background: #fffbe6;
  color: #faad14;
}

.issue-item.level-low {
  background: #f1f5f9;
  color: #64748b;
}

.issue-rule {
  flex-shrink: 0;
  font-weight: 600;
}

.compliance-ok {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #52c41a;
}

.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: var(--text-tertiary, #94a3b8);
  font-size: 13px;
}

@media (max-width: 768px) {
  .field-row {
    flex-direction: column;
    gap: 2px;
  }

  .field-label {
    max-width: none;
  }

  .field-value {
    text-align: left;
  }
}
</style>
