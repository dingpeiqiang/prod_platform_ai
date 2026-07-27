<template>
  <div v-if="visible" class="batch-panel" :class="{ open: detailOpen }">
    <!-- 常驻：一行摘要，不挡对话 -->
    <div class="batch-bar">
      <div class="bar-left">
        <span class="bar-title">智读清单</span>
        <span class="sum-pill">{{ items.length }}</span>
        <span class="sum-pill ok">通过 {{ passedCount }}</span>
        <span class="sum-pill bad">待修 {{ pendingCount }}</span>
        <span v-if="storedCount" class="sum-pill stored">入库 {{ storedCount }}</span>
      </div>
      <div class="bar-actions">
        <button
          type="button"
          class="btn-primary"
          :disabled="!confirmableCount"
          @click="$emit('confirm-pass')"
        >
          入库（{{ confirmableCount }}）
        </button>
        <button type="button" class="btn-ghost" @click="detailOpen = !detailOpen">
          {{ detailOpen ? '收起详情' : '展开详情' }}
        </button>
        <button type="button" class="btn-icon" title="关闭清单" @click="$emit('close')">×</button>
      </div>
    </div>

    <!-- 芯片行：快速切换套餐 -->
    <div class="chip-row">
      <button
        v-for="item in items"
        :key="item.productId || item.index"
        type="button"
        class="pkg-chip"
        :class="[chipClass(item), { active: item.productId === activeId }]"
        @click="onSelect(item)"
      >
        <span class="chip-idx">{{ item.index || '·' }}</span>
        <span class="chip-name">{{ shortName(item) }}</span>
        <span class="chip-status">{{ statusLabel(item) }}</span>
      </button>
    </div>

    <!-- 仅当前选中项详情，可收起 -->
    <div v-if="detailOpen && activeItem" class="detail-pane">
      <div class="detail-head">
        <strong>{{ activeItem.draft?.offeringName || `草稿${activeItem.index}` }}</strong>
        <span v-if="activeItem.draftId" class="draft-id">{{ activeItem.draftId }}</span>
        <span class="status" :class="chipClass(activeItem)">{{ statusLabel(activeItem) }}</span>
      </div>

      <div class="card-grid">
        <div class="col">
          <div class="col-title">原文</div>
          <p class="excerpt">{{ activeItem.sourceExcerpt || '—' }}</p>
        </div>
        <div class="col">
          <div class="col-title">映射</div>
          <p>
            月费 {{ formatFee(activeItem.draft?.monthlyFee) }}
            · {{ activeItem.draft?.targetUser || '—' }}
            · {{ activeItem.draft?.channelScope || '—' }}
          </p>
          <p class="msg-root">
            报文 {{ activeItem.draft?.categoryName || activeItem.categoryName || activeItem.draft?.messageRootKey || '—' }}
          </p>
        </div>
        <div class="col">
          <div class="col-title">场景 / 规则</div>
          <p>
            {{ activeItem.draft?.bizScenario || '—' }}
            <template v-if="activeItem.draft?.basedOnTemplate">
              · {{ activeItem.draft.basedOnTemplate }}
            </template>
          </p>
          <p v-if="activeItem.draft?.successSmsImmediate" class="sms-preview">
            {{ activeItem.draft.successSmsImmediate }}
          </p>
          <div v-if="activeItem.issues?.length" class="issue-list">
            <span
              v-for="(iss, idx) in activeItem.issues"
              :key="`${iss.ruleId}-${idx}`"
              class="issue-tag"
              :class="levelClass(iss.issueLevel)"
              :title="iss.message || iss.issueType"
            >
              {{ iss.ruleId }}
            </span>
          </div>
          <p v-else-if="activeItem.compliancePass" class="rules-ok">合规通过</p>
        </div>
      </div>

      <div v-if="!activeItem.compliancePass && evidenceList.length" class="evidence-box">
        <span class="evidence-title">证据</span>
        <code v-for="(ev, ei) in evidenceList" :key="ei">{{ ev }}</code>
      </div>

      <div v-if="!activeItem.compliancePass && fixButtons.length" class="fix-row">
        <button
          v-for="fix in fixButtons"
          :key="fix.key"
          type="button"
          class="fix-btn"
          @click="$emit('apply-fix', { productId: activeItem.productId, fixKey: fix.key })"
        >
          {{ fix.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
})

const emit = defineEmits(['select', 'apply-fix', 'confirm-pass', 'close'])

/** 默认收起详情，只留芯片条，避免挡对话 */
const detailOpen = ref(false)

watch(
  () => props.visible,
  (v) => {
    if (v) detailOpen.value = false
  },
)

watch(
  () => props.activeId,
  (id, prev) => {
    // 用户点选或一键体验切到待修正项时，自动展开详情
    if (id && id !== prev) {
      const item = props.items.find((i) => i.productId === id)
      if (item && !item.compliancePass) detailOpen.value = true
    }
  },
)

const passedCount = computed(() => props.items.filter((i) => i.compliancePass).length)
const pendingCount = computed(() => props.items.filter((i) => !i.compliancePass).length)
const storedCount = computed(() => props.items.filter((i) => i.status === '已入库' || i.draftId).length)
const confirmableCount = computed(() =>
  props.items.filter((i) => i.compliancePass && i.status !== '已入库' && !i.draftId).length,
)

const activeItem = computed(() => {
  if (!props.items.length) return null
  return props.items.find((i) => i.productId === props.activeId) || props.items[0]
})

const evidenceList = computed(() => {
  const item = activeItem.value
  if (!item) return []
  const list = []
  for (const iss of item.issues || []) {
    for (const ev of iss.evidence || []) {
      if (ev && !list.includes(ev)) list.push(ev)
    }
  }
  return list.slice(0, 4)
})

const fixButtons = computed(() => (activeItem.value ? fixesFor(activeItem.value) : []))

function onSelect(item) {
  emit('select', item.productId)
  detailOpen.value = true
}

function shortName(item) {
  const name = item.draft?.offeringName || `草稿${item.index}`
  return name.length > 10 ? `${name.slice(0, 10)}…` : name
}

function statusLabel(item) {
  if (item.status === '已入库' || item.draftId) return '已入库'
  if (item.compliancePass) return '通过'
  return '待修'
}

function chipClass(item) {
  if (item.status === '已入库' || item.draftId) return 'stored'
  if (item.compliancePass) return 'pass'
  return 'fail'
}

function formatFee(fee) {
  if (fee === 0 || fee === '0') return '0'
  if (fee === null || fee === undefined || fee === '') return '空'
  return String(fee)
}

function levelClass(level) {
  if (level === 'HIGH') return 'high'
  if (level === 'MEDIUM') return 'medium'
  return 'low'
}

function fixesFor(item) {
  const rules = new Set((item.issues || []).map((i) => i.ruleId))
  const fixes = []
  if (rules.has('R-C05') || rules.has('R-C07')) {
    fixes.push({ key: 'contract12', label: '补协议期12个月' })
    fixes.push({ key: 'internal', label: '转内部验证' })
  }
  if (rules.has('R-C06') && (item.issues || []).some((i) => i.field === 'monthlyFee')) {
    fixes.push({ key: 'fee19', label: '确认月费19元' })
  }
  if (rules.has('R-C04')) {
    fixes.push({ key: 'dependBb', label: '补依赖宽带' })
  }
  return fixes
}
</script>

<style scoped>
.batch-panel {
  flex-shrink: 0;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 8px 12px;
  max-height: none;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
}

.bar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.bar-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.sum-pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-weight: 600;
}

.sum-pill.ok {
  background: #dcfce7;
  color: #166534;
}

.sum-pill.bad {
  background: #fee2e2;
  color: #991b1b;
}

.sum-pill.stored {
  background: #ccfbf1;
  color: #0f766e;
}

.btn-primary,
.btn-ghost,
.fix-btn {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
  background: #fff;
  line-height: 1.2;
}

.btn-primary {
  background: #0f766e;
  border-color: #0f766e;
  color: #fff;
}

.btn-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-ghost {
  color: #475569;
}

.btn-icon {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.btn-icon:hover {
  background: #e2e8f0;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.pkg-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  padding: 4px 10px 4px 6px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  color: #334155;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.pkg-chip:hover {
  border-color: #94a3b8;
}

.pkg-chip.pass {
  border-color: #86efac;
  background: #f0fdf4;
}

.pkg-chip.fail {
  border-color: #fca5a5;
  background: #fef2f2;
}

.pkg-chip.stored {
  border-color: #5eead4;
  background: #f0fdfa;
}

.pkg-chip.active {
  box-shadow: 0 0 0 2px rgba(15, 118, 110, 0.28);
  border-color: #0f766e;
}

.pkg-chip:focus-visible {
  outline: 2px solid rgba(15, 118, 110, 0.35);
  outline-offset: 1px;
}

.chip-idx {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #e2e8f0;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.fail .chip-idx {
  background: #fecaca;
  color: #991b1b;
}

.pass .chip-idx {
  background: #bbf7d0;
  color: #166534;
}

.chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 9em;
  font-weight: 600;
}

.chip-status {
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
}

.fail .chip-status {
  color: #b91c1c;
  font-weight: 600;
}

.pass .chip-status {
  color: #15803d;
  font-weight: 600;
}

.detail-pane {
  margin-top: 8px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  max-height: 22vh;
  overflow: auto;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.detail-head strong {
  font-size: 13px;
  color: #0f172a;
}

.draft-id {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #0f766e;
}

.status {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
}

.status.pass {
  background: #dcfce7;
  color: #166534;
}

.status.fail {
  background: #fee2e2;
  color: #991b1b;
}

.status.stored {
  background: #ccfbf1;
  color: #0f766e;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.col-title {
  font-size: 10px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: none;
  margin-bottom: 2px;
}

.col p,
.excerpt {
  margin: 0;
  font-size: 12px;
  color: #334155;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.msg-root {
  margin-top: 4px !important;
  color: #0f766e !important;
  font-weight: 600;
}

.sms-preview {
  margin-top: 6px !important;
  padding: 6px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #475569 !important;
  font-size: 11px !important;
  -webkit-line-clamp: 3 !important;
}

.issue-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.issue-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fee2e2;
  color: #991b1b;
}

.issue-tag.medium {
  background: #fef3c7;
  color: #92400e;
}

.issue-tag.low {
  background: #e2e8f0;
  color: #475569;
}

.rules-ok {
  margin-top: 4px !important;
  color: #15803d !important;
  font-weight: 600;
  font-size: 12px;
}

.evidence-box {
  margin-top: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  background: #fff7ed;
  border: 1px dashed #fdba74;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.evidence-title {
  font-size: 11px;
  font-weight: 700;
  color: #9a3412;
}

.evidence-box code {
  font-size: 10px;
  background: #fff;
  border: 1px solid #fed7aa;
  border-radius: 4px;
  padding: 1px 5px;
  color: #9a3412;
}

.fix-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.fix-btn {
  background: #fff7ed;
  border-color: #fdba74;
  color: #9a3412;
}

@media (max-width: 900px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
