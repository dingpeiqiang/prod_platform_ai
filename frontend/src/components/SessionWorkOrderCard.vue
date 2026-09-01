<template>
  <section class="session-wo-card" aria-label="会话商品配置工单">
    <header class="swo-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M9 11l3 3L22 4"/>
        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
      </svg>
      <span class="swo-title">商品配置工单</span>
      <span class="swo-count">{{ filtered.length }}</span>
      <button
        v-if="workOrders.length > COLLAPSE_THRESHOLD"
        type="button"
        class="swo-collapse"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? `展开全部（${filtered.length}）` : '收起' }}
      </button>
    </header>

    <template v-if="!collapsed">
      <!-- 筛选/搜索工具条：大批量（数百条）时按状态过滤 + 关键词定位 -->
      <div v-if="workOrders.length > COLLAPSE_THRESHOLD" class="swo-toolbar">
        <div class="swo-tabs">
          <button
            v-for="tab in STATUS_TABS"
            :key="tab.key"
            type="button"
            class="swo-tab"
            :class="{ active: filterStatus === tab.key }"
            @click="filterStatus = tab.key"
          >
            {{ tab.label }}
            <span v-if="tab.key !== 'all'" class="swo-tab-count">{{ statusCount(tab.key) }}</span>
          </button>
        </div>
        <input
          v-model="keyword"
          type="text"
          class="swo-search"
          placeholder="搜工单号/名称/商品编码"
        >
      </div>

      <ul class="swo-list">
        <li
          v-for="wo in pagedItems"
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
            <span v-if="sourceLabel(wo)" class="swo-source" :title="`来源：${sourceLabel(wo)}`">{{ sourceLabel(wo) }}</span>
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
      <div v-if="!filtered.length" class="swo-empty">无匹配工单</div>

      <!-- 分页控件：仅条目数超过单页容量时出现 -->
      <div v-if="filtered.length > pageSize" class="swo-pager">
        <button type="button" class="pg-btn" :disabled="page <= 1" @click="page--">上一页</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ filtered.length }} 条</span>
        <button type="button" class="pg-btn" :disabled="page >= totalPages" @click="page++">下一页</button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

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

/** 折叠/工具条阈值：低于该数量保持原有平铺展示 */
const COLLAPSE_THRESHOLD = 20
/** 单页容量：数百条工单时分页渲染，避免一次挂载全部 DOM */
const pageSize = 20

const STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: 'open', label: '待处理' },
  { key: 'in_progress', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'cancelled', label: '已取消' },
]

const collapsed = ref(false)
const filterStatus = ref('all')
const keyword = ref('')
const page = ref(1)

const norm = (s) => String(s || '').trim().toLowerCase()

const filtered = computed(() => {
  let list = props.workOrders || []
  if (filterStatus.value !== 'all') {
    list = list.filter((wo) => String(wo.status || 'open') === filterStatus.value)
  }
  const kw = norm(keyword.value)
  if (kw) {
    list = list.filter((wo) =>
      norm(wo.workOrderId).includes(kw) ||
      norm(wo.title).includes(kw) ||
      norm(wo.offeringName).includes(kw) ||
      norm(wo.offeringId).includes(kw))
  }
  return list
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))

const pagedItems = computed(() => {
  if (filtered.value.length <= pageSize) return filtered.value
  const start = (page.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

// 数据刷新（新工单到达）或过滤条件变化时回到第一页，避免停在越界/空页
watch(filtered, () => {
  if (page.value > totalPages.value) page.value = 1
})
watch([filterStatus, keyword], () => {
  page.value = 1
})

const statusCount = (key) =>
  (props.workOrders || []).filter((wo) => String(wo.status || 'open') === key).length

const STATUS_MAP = {
  open: '待处理',
  in_progress: '进行中',
  done: '已完成',
  cancelled: '已取消',
}

const statusText = (status) => STATUS_MAP[status] || status || '-'

/** 工单来源 → 业务标签（source 为空/未知时返回空串不展示） */
const SOURCE_MAP = {
  rd_file_parse: '智读·文件配置',
  rd_config_draft: '智聊·对话配置',
  root_cause: '根因处置',
  risk_audit: '风险稽核',
  risk_delist: '风险下架',
  risk_price: '风险调价',
  ontology_rules: '规则引擎',
  ops_assistant: '运营助手',
  manual: '手动创建',
}

const sourceLabel = (wo) => SOURCE_MAP[String(wo?.source || '').trim()] || ''

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

.swo-collapse {
  margin-left: auto;
  padding: 1px 8px;
  font-size: 11px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  cursor: pointer;
}

.swo-collapse:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.swo-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.swo-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.swo-tab {
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid var(--border-light);
  border-radius: 10px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.swo-tab.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
  color: #1d4ed8;
  font-weight: 500;
}

.swo-tab-count {
  margin-left: 2px;
  font-size: 10px;
  opacity: 0.75;
}

.swo-search {
  flex: 1;
  min-width: 120px;
  max-width: 200px;
  margin-left: auto;
  padding: 3px 8px;
  font-size: 11px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-primary);
  outline: none;
}

.swo-search:focus {
  border-color: #3b82f6;
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

.swo-source {
  font-size: 10px;
  padding: 1px 6px;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 3px;
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

.swo-empty {
  padding: 10px 0;
  text-align: center;
  font-size: 11px;
  color: var(--text-tertiary);
}

.swo-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
}

.pg-btn {
  padding: 2px 10px;
  font-size: 11px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.pg-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
}

.pg-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pg-info {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
