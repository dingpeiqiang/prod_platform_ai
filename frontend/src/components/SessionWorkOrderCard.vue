<template>
  <section class="session-wo-card" aria-label="会话商品配置工单">
    <header class="swo-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 11l3 3L22 4"/>
        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
      </svg>
      <span class="swo-title">商品配置工单</span>
      <span class="swo-count">{{ workOrders.length }}</span>
    </header>

    <ul class="swo-list">
      <li
        v-for="wo in workOrders"
        :key="wo.workOrderId || wo.id"
        class="swo-item"
        :class="{ clickable: !!formCard, highlight: wo.workOrderId && wo.workOrderId === highlightId }"
        @click="formCard && $emit('edit-product', wo)"
        title="点击查看/编辑草稿"
      >
        <div class="swo-row">
          <span class="swo-name">{{ wo.title || wo.offeringName || '商品配置工单' }}</span>
          <span class="swo-status" :class="`st-${wo.status || 'open'}`">{{ statusText(wo.status) }}</span>
        </div>
        <div class="swo-meta">
          <span v-if="wo.workOrderId" class="swo-wo">{{ wo.workOrderId }}</span>
          <span v-if="wo.offeringId" class="swo-wo">{{ wo.offeringId }}</span>
          <span v-if="formatTime(wo.createdAt)" class="swo-time">{{ formatTime(wo.createdAt) }}</span>
        </div>
        <!-- 稽核结果：草稿合规结论 + 问题规则明细 -->
        <div v-if="auditInfo(wo)" class="swo-audit" :class="auditInfo(wo).pass ? 'audit-pass' : 'audit-fail'">
          <span class="swo-audit-badge">{{ auditInfo(wo).pass ? '稽核通过' : '稽核未通过' }}</span>
          <span v-for="issue in auditInfo(wo).issues" :key="issue.key" class="swo-audit-issue"
                :title="issue.desc">{{ issue.key }}</span>
        </div>
        <!-- 工单操作：已完成/已取消工单不可再提交或删除（防重复提交），仅保留预览/编辑/复制 -->
        <div v-if="hasActions(wo)" class="swo-actions" @click.stop>
          <button type="button" class="swo-act" @click="$emit('preview-product', wo)">预览</button>
          <button type="button" class="swo-act" @click="$emit('edit-product', wo)">编辑</button>
          <button v-if="!isClosed(wo)" type="button" class="swo-act submit" @click="$emit('submit-product', wo)">提交</button>
          <button type="button" class="swo-act" @click="$emit('copy-product', wo)">复制</button>
          <button v-if="!isClosed(wo)" type="button" class="swo-act danger" @click="$emit('delete-product', wo)">删除</button>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup>
const props = defineProps({
  workOrders: { type: Array, default: () => [] },
  /** 关联的配置方案草稿表单卡（点击工单条目 → 右侧抽屉编辑） */
  formCard: { type: Object, default: null },
  /** 最近一次操作命中的工单号（提交/复制/删除回执后高亮该条目） */
  highlightId: { type: String, default: '' },
})

defineEmits([
  'query-offering',
  'edit-product',
  'preview-product',
  'copy-product',
  'delete-product',
  'submit-product',
])

const STATUS_MAP = {
  open: '待处理',
  in_progress: '进行中',
  done: '已完成',
  cancelled: '已取消',
}

const statusText = (status) => STATUS_MAP[status] || status || '-'

/** 稽核结果视图模型：compliancePass + 问题规则列表（R-C03/R-C06 等）；无稽核数据时返回 null 不展示 */
const auditInfo = (wo) => {
  const pass = wo.compliancePass
  if (pass === undefined || pass === null) return null
  const rawIssues = Array.isArray(wo.complianceIssues) ? wo.complianceIssues : []
  const issues = rawIssues
    .map((it) => ({
      key: it?.ruleId || it?.ruleCode || '?',
      desc: [it?.issueType, it?.issueDesc, it?.field].filter(Boolean).join(' · '),
    }))
  return { pass: !!pass, issues }
}

/** 消息关联了本地草稿（formCard/productId）时才提供操作 */
const hasActions = (wo) => !!(wo.productId || props.formCard)

/** 终态工单（已完成/已取消）：草稿已备案或已删除，不可再提交/删除 */
const isClosed = (wo) => ['done', 'cancelled'].includes(String(wo?.status || ''))

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.session-wo-card {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
}

.swo-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.swo-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.swo-count {
  min-width: 17px;
  padding: 0 5px;
  text-align: center;
  font-size: 11px;
  line-height: 16px;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 8px;
}

.swo-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.swo-item {
  padding: 6px 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 7px;
  transition: all 0.2s;
}

.swo-item.clickable {
  cursor: pointer;
}

.swo-item.clickable:hover {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.04);
}

.swo-item.expanded {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.03);
}

.swo-expand-hint {
  font-size: 10px;
  color: #3b82f6;
  cursor: pointer;
}

.swo-draft-editor {
  margin-top: 8px;
}

.swo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.swo-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.swo-status {
  flex-shrink: 0;
  padding: 1px 7px;
  font-size: 10px;
  border-radius: 4px;
}

.st-open {
  background: #fef3c7;
  color: #92400e;
}

.st-in_progress {
  background: #dbeafe;
  color: #1d4ed8;
}

.st-done {
  background: #dcfce7;
  color: #15803d;
}

.st-cancelled {
  background: #f1f5f9;
  color: #64748b;
}

.swo-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 3px;
}

.swo-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.swo-act {
  padding: 2px 10px;
  font-size: 11px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.swo-act:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.swo-act.danger:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.swo-act.submit {
  border-color: #22c55e;
  color: #15803d;
}

.swo-act.submit:hover {
  background: #dcfce7;
}

.swo-wo {
  font-size: 10px;
  padding: 1px 5px;
  background: #f1f5f9;
  color: #64748b;
  border-radius: 3px;
  font-family: ui-monospace, monospace;
}

.swo-time {
  font-size: 10px;
  color: var(--text-tertiary);
}

.swo-audit {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}

.swo-audit-badge {
  padding: 1px 7px;
  font-size: 10px;
  border-radius: 4px;
  font-weight: 500;
}

.audit-pass .swo-audit-badge {
  background: #dcfce7;
  color: #15803d;
}

.audit-fail .swo-audit-badge {
  background: #fee2e2;
  color: #b91c1c;
}

.swo-audit-issue {
  padding: 1px 5px;
  font-size: 10px;
  border-radius: 3px;
  background: #fef3c7;
  color: #92400e;
  font-family: ui-monospace, monospace;
  cursor: default;
}

.swo-item.highlight {
  border-color: #22c55e;
  background: rgba(34, 197, 94, 0.06);
  box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.35);
  animation: swo-flash 1.2s ease-out 1;
}

@keyframes swo-flash {
  0% {
    background: rgba(34, 197, 94, 0.18);
  }
  100% {
    background: rgba(34, 197, 94, 0.06);
  }
}
</style>
