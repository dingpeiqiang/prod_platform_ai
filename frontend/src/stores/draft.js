import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 草稿 store
 *
 * 职责：
 * 1. 独立存储「文件导入批量配置」解析出的多个草稿产品（status = 'draft'）
 * 2. 提供草稿列表、当前打开草稿、状态流转（草稿 → 已提交）、上/下一个切换
 * 3. 在「我的草稿」抽屉中展示；点击某个草稿可恢复对应配置工作台
 *
 * 草稿结构：
 *   {
 *     id, workOrderId, prodName, summary,
 *     parsedFields: 喂给配置工作台的字段,   //（对话式配置 schema）
 *     values: 用户实际编辑后的表单值快照,
 *     status: 'draft' | 'submitted',      // 草稿 / 已提交(审批中)
 *     source: 'batch',                      // 来源：文件批量导入
 *     parsedAt: 解析时间,
 *     active: 是否当前打开中的草稿
 *   }
 */
export const useDraftStore = defineStore('draft', () => {
  // 草稿不持久化：每次刷新页面草稿清空，仅在当前会话内存中保留
  const drafts = ref([])

  // 草稿（status=draft）列表，按解析时间倒序
  const draftList = computed(() =>
    drafts.value
      .filter((d) => d.status === 'draft')
      .sort((a, b) => (b.parsedAt || 0) - (a.parsedAt || 0))
  )

  const total = computed(() => draftList.value.length)

  // 尚未提交的草稿：可进入/恢复工作台继续配置
  const editableDrafts = computed(() => draftList.value)

  const findById = (id) => drafts.value.find((d) => d.id === id)

  /**
   * 批量导入解析出的草稿产品（幂等：同 workOrderId 不重复）
   * @param {Array} products [{ workOrderId, prodName, summary, parsedFields }]
   */
  function batchImport(products) {
    const added = []
    ;(products || []).forEach((p, idx) => {
      if (!p || !p.workOrderId) return
      const exist = drafts.value.find((d) => d.workOrderId === p.workOrderId)
      if (exist) {
        if (exist.status === 'submitted') {
          // 已提交/归档过的草稿在重新导入时重置为草稿（重新导入覆盖）
          exist.status = 'draft'
          exist.parsedFields = p.parsedFields || exist.parsedFields
          exist.values = null
          exist.parsedAt = Date.now()
        }
        added.push(exist)
        return
      }
      const entry = {
        id: 'draft_' + Date.now().toString(36) + '_' + idx + Math.random().toString(36).slice(2, 5),
        workOrderId: p.workOrderId,
        prodName: p.prodName || '未命名产品',
        summary: p.summary || '',
        parsedFields: p.parsedFields || {},
        values: null,
        status: 'draft',
        source: p.source || 'batch',
        parsedAt: Date.now(),
        active: false,
      }
      drafts.value.unshift(entry)
      added.push(entry)
    })
    return added
  }

  /**
   * 标记当前打开的草稿（其余 active 置 false）
   */
  function setActive(id) {
    drafts.value.forEach((d) => { d.active = d.id === id })
  }

  /**
   * 通用草稿登记：支持指定来源（dialog/batch/copy 等）。
   * 幂等：同 workOrderId 不重复；若已提交则重置为草稿（重新登记覆盖）。
   * 用于「存量产品复制配置」等：点击复制后把复制出的新产品登记到「我的草稿」。
   */
  function upsertDraft({ workOrderId, prodName, summary, parsedFields, source }) {
    if (!workOrderId) return null
    let d = drafts.value.find((x) => x.workOrderId === workOrderId)
    if (d) {
      d.status = 'draft'
      d.source = source || d.source || 'dialog'
      d.prodName = prodName || d.prodName || '未命名产品'
      d.summary = summary || d.summary || ''
      d.parsedFields = parsedFields || d.parsedFields || {}
      d.parsedAt = Date.now()
    } else {
      d = {
        id: 'draft_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
        workOrderId,
        prodName: prodName || '未命名产品',
        summary: summary || '',
        parsedFields: parsedFields || {},
        values: null,
        status: 'draft',
        source: source || 'dialog',
        parsedAt: Date.now(),
        active: false,
      }
      drafts.value.unshift(d)
    }
    return d
  }

  /**
   * 对话式配置草稿：打开配置工作台即登记一条草稿到「我的草稿」，
   * 关闭/新建会话后仍留痕，可在「我的草稿」点击重新进入对应工作台（幂等：同工单不重复）。
   * 若之前已提交过则重置为草稿（重新进入配置覆盖）。
   */
  function upsertDialogueDraft({ workOrderId, prodName, summary, parsedFields }) {
    if (!workOrderId) return null
    let d = drafts.value.find((x) => x.workOrderId === workOrderId)
    if (d) {
      d.status = 'draft'
      d.source = 'dialog'
      d.prodName = prodName || d.prodName || '未命名产品'
      d.summary = summary || d.summary || ''
      d.parsedFields = parsedFields || d.parsedFields || {}
      d.parsedAt = Date.now()
    } else {
      d = {
        id: 'draft_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
        workOrderId,
        prodName: prodName || '未命名产品',
        summary: summary || '',
        parsedFields: parsedFields || {},
        values: null,
        status: 'draft',
        source: 'dialog',
        parsedAt: Date.now(),
        active: false,
      }
      drafts.value.unshift(d)
    }
    return d
  }

  /**
   * 保存草稿配置的用户编辑表单值（用于恢复到工作台时回填/只读）
   */
  function saveValues(id, values) {
    const d = findById(id)
    if (d) { d.values = values; }
  }

  /**
   * 草稿提交后：置为 submitted（移出草稿列表），返回给调用方以决定是否跳转下一个
   */
  function markSubmitted(id) {
    const d = findById(id)
    if (d) { d.status = 'submitted'; d.active = false; }
    return d
  }

  /**
   * 删除某条草稿
   */
  function remove(id) {
    drafts.value = drafts.value.filter((d) => d.id !== id)
  }

  function clear() {
    drafts.value = []
  }

  return {
    drafts,
    draftList,
    editableDrafts,
    total,
    findById,
    batchImport,
    upsertDialogueDraft,
    upsertDraft,
    setActive,
    saveValues,
    markSubmitted,
    remove,
    clear,
  }
})
