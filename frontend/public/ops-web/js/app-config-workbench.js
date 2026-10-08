(function(global){
  const { ref, reactive, computed, onMounted, onUnmounted, watch } = global.Vue;
  const ElMessage = global.ElMessage;
  



const MIN_WIDTH = 400
const MAX_WIDTH = 900
const DEFAULT_WIDTH = 560


const { launchProductConfig, testReport, campusProductConfig } = window.__CFG;
const ConfigWorkbench = {
  __name: 'ProductConfigPanel',
  props: {
  archiveSnapshot: { type: Object, default: null },
  // 对话式配置：用 AI 从用户需求解析出的字段值（{ 字段key: 值 }）。传入了即以"对话式配置"模式渲染，"
  // 仅预填已解析字段，其余字段置空，等待"一键智填"用"AI 推荐值填充、"
  parsedFields: { type: Object, default: null },
  // 对话式配置专用工单号：工作台页签/底部工单号均以本值展示，避免读取产品运营助手全局 launch 单号
  workOrderId: { type: String, default: '' },
  // 文件导入批量配置：传入了则该工作台处于草稿/批量配置"模式，"
  // 底部展示 第x/共y + 一/下一个导航，提交后自动跳下一个草稿。
  batchContext: { type: Object, default: null },
  // 场景专属配置 schema：对话式/批量场景可传入与 launchOptimizeData 同构的配置数据。
  //（如校园青春单/campusDraftData.campusProductConfig）。未传入时回落到全局 launchProductConfig。
  schemaConfig: { type: Object, default: null },
  // 多商品：可选商品列表与当前选中商品 id（由父组件 App 注入），用于头部商品名下拉切换。
  products: { type: Array, default: () => [] },
  activeProductId: { type: String, default: '' },
},
  emits: ['close', 'submitted', 'go-prev', 'go-next', 'batch-submitted', 'copy-init', 'copy-rename', 'dialogue-rename', 'panel-action', 'select-product'],
  setup(__props, { expose: __expose, emit: __emit }) {

const emit = __emit

// 只读档案模式：传入 archiveSnapshot 时，本面板以"配置档案"形式只读展示某份归档工单的完整配置。// archiveSnapshot = { productInfo: { workOrderId, prodPrcName, templateLabel, status }, values: { 字段key: 值 } }
const props = __props

// 当前选中商品（响应式）：以 window.__WB_PRODUCT 初始化，商品切换时更新（见 activeProductId watcher），
// 驱动所有页签/派生态全体重算。页面内派生数据统一读取本 ref，而非非响应式的 window.__WB_PRODUCT。
const currentProduct = ref(window.__WB_PRODUCT || {})

// 复制模式：基于某份存量商品归档档案「复制」出的可编辑新商品（保持只读档案预填的字段值为模板数据，
const copyMode = ref(false)
// 复制模式下的新工单号（进入复制时生成一次）
const copyWorkOrderId = ref('')
const isArchive = computed(() => !!props.archiveSnapshot && !copyMode.value)
const dialogueMode = computed(() => !!props.parsedFields)
// 是否存在真实商品实例（URL/req_id 外链解析出的逐品，含外链占位）。存在即按该商品实际字段渲染各页签，
// 避免多商品切换时预览/权益等仍显示 launch 静态示例（畅享59元青春版）。
const hasProductInstance = computed(() => {
  const p = currentProduct.value || {}
  return !!(p.id || p.name || p.code)
})
// 动态预览模式：对话式配置、只读档案（存量快照）、以及外链多商品均按 formData/商品实际值渲染
// 手机预览/免填单/短信模板，避免静态示例文案泄漏到具体商品视图。
const dynamicPreview = computed(() => dialogueMode.value || isArchive.value || hasProductInstance.value)
// 批量配置模式：批量上下文存在且非只读档案
const batchMode = computed(() => !!props.batchContext && !isArchive.value)
// 对话式配置独立状态：仅在该场景内流转（草稿→待审核），不读写产品运营助手全局 launch 状态。
const dialogueStatus = ref('draft')

// ========== 面板宽度拖拽 ==========
const panelWidth = ref(DEFAULT_WIDTH)
const resizing = ref(false)

// ========== 多商品切换（头部横向平铺，溢出横向滚动） ==========
// 全部商品在头部平铺展示为标签；超出宽度时该区域可横向滚动（不再隐藏进下拉）。
// 右侧「查看商品列表」下拉作为快捷导航，选择后同步高亮并滚动定位到对应顶部标签。
const products = computed(() => props.products || [])
const activeProductId = computed(() => props.activeProductId || '')
const productMenuOpen = ref(false)
const ppStripRef = ref(null)

// 选中商品后把对应顶部标签滚动到可视区，保证切换与标签高亮联动。
const scrollActiveChipIntoView = () => {
  const strip = ppStripRef.value
  if (!strip) return
  const chip = strip.querySelector('.pp-chip-slot.active')
  if (chip && chip.scrollIntoView) chip.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' })
}

const onSelectProduct = (p) => {
  productMenuOpen.value = false
  if (p && p.id !== activeProductId.value) emit('select-product', p)
  // 切换后（无论来自顶部标签还是下拉列表）滚动定位到高亮标签
  requestAnimationFrame(scrollActiveChipIntoView)
}
const toggleProductMenu = () => { productMenuOpen.value = !productMenuOpen.value }

// 顶部平铺全部商品标签
const visibleProducts = computed(() => products.value)

const onDocClick = (e) => {
  if (productMenuOpen.value && !e.target.closest('.product-picker')) {
    productMenuOpen.value = false
  }
}
let ppResizeObserver = null
onMounted(() => {
  document.addEventListener('click', onDocClick)
  requestAnimationFrame(scrollActiveChipIntoView)
  if (window.ResizeObserver && ppStripRef.value) {
    ppResizeObserver = new ResizeObserver(() => scrollActiveChipIntoView())
    ppResizeObserver.observe(ppStripRef.value)
  }
  window.addEventListener('resize', scrollActiveChipIntoView)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('resize', scrollActiveChipIntoView)
  if (ppResizeObserver) { ppResizeObserver.disconnect(); ppResizeObserver = null }
})
watch(products, () => requestAnimationFrame(scrollActiveChipIntoView))
// 直接切换（如外部更新 activeProductId）时也滚动定位到高亮标签
watch(activeProductId, () => requestAnimationFrame(scrollActiveChipIntoView))

const startResize = (e) => {
  e.preventDefault()
  resizing.value = true
  const startX = e.touches ? e.touches[0].clientX : e.clientX
  const startWidth = panelWidth.value

  const onMove = (ev) => {
    const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX
    const delta = startX - clientX
    panelWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta))
  }

  const onUp = () => {
    resizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onUp)
}

onUnmounted(() => {
  resizing.value = false
})

// ========== Tab 切换 ==========
const baseTabs = [
  { key: 'demand', label: '需求提报' },
  { key: 'scheme', label: '配置方案' },
  { key: 'config', label: '配置信息' },
  { key: 'summary', label: '信息预览' },
  { key: 'audit', label: '稽核报告' },
  { key: 'calibrate', label: '校准报告' },
  { key: 'testrep', label: '测试报告' },
  { key: 'approval', label: '审批矩阵' },
  { key: 'monitor', label: '监控运维' },
]

// 页签在流程中的推进顺序（数值越大越靠后）：「按进度逐步显示」以此为阈值裁剪页签。
// 需求提报(0)→配置方案(1)→配置信息/信息预览(2)→稽核报告(3)→校准报告(4)→测试报告(5)→审批矩阵(6)→监控运维(7)
const TAB_PROGRESS_ORDER = {
  demand: 0, scheme: 1, config: 2, summary: 2, audit: 3, calibrate: 4, testrep: 5, approval: 6, monitor: 7,
}
// 工作流环节(stage) → 已到达的页签推进阈值（由 stage-map.json 的 tab 反查 TAB_PROGRESS_ORDER）。
// stage 06 自动测试注释为“04”，本表按 gen_workflows.py 口径以 06 为准并兼容 04。
const TAB_PROGRESS_INDEX = (function(){
  const fallback = { '00':0, '01':1, '02':2, '03':3, '04':6, '05':4, '06':5, '07':7, '08':6, '09':7, '10':7, '11':6, '12':6 }
  try {
    const sm = window.__WB_STAGE_MAP
    if (sm && sm.stages) {
      const m = {}
      Object.keys(sm.stages).forEach(function(k){
        const key = sm.stages[k] && sm.stages[k].tab
        if (key !== undefined && TAB_PROGRESS_ORDER[key] !== undefined) m[k] = TAB_PROGRESS_ORDER[key]
      })
      // 以 stage-map 为主，缺失环节用内置兜底补齐，避免个别环节无映射导致退化为显示全部
      Object.keys(fallback).forEach(function(k){ if (m[k] === undefined) m[k] = fallback[k] })
      return m
    }
  } catch (e) {}
  return fallback
})()
const tabs = computed(() => {
  // 按流程进度逐步显示页签：根据当前 stage 在流程中的顺序，仅显示「已到达环节（含当前）」的页签，
  // 隐藏尚未到达的后续环节页签。未携带 stage（如 view=workbench 整体视图）时显示全部。
  const stage = String((window.__WB_QUERY && window.__WB_QUERY.stage) || '')
  if (stage === '' || TAB_PROGRESS_INDEX[stage] === undefined) return baseTabs
  const reached = TAB_PROGRESS_INDEX[stage]
  return baseTabs.filter((t) => (TAB_PROGRESS_ORDER[t.key] === undefined ? true : TAB_PROGRESS_ORDER[t.key] <= reached))
})
// 工作流环节 -> 默认页签， 环节口径）：
// 需求提报→需求提报/ 需求分析→配置方案 / 智能配置→配置信息、信息预览/ 规格稽核→稽核报告/
// 资费校准→校准报告/ 自动化测试→测试报告 / 上线审批→审批矩阵/ 监控运维→监控运维。
const DEFAULT_TAB_BY_STAGE = (function(){
  // Prefer stage-map.json (window.__WB_STAGE_MAP, single source of truth shared with gen_workflows.py);
  // fall back to the built-in defaults when map is unavailable so the page always works.
  try {
    const sm = window.__WB_STAGE_MAP;
    if (sm && sm.stages) {
      const m = {};
      Object.keys(sm.stages).forEach(function(k){ m[k] = sm.stages[k].tab; });
      return m;
    }
  } catch (e) {}
  return { '00':'demand', '01':'scheme', '02':'config', '03':'audit', '05':'calibrate', '04':'approval', '06':'testrep', '07':'monitor', '08':'approval', '09':'monitor', '10':'monitor', '11':'approval', '12':'approval' };
})()
const activeTab = ref((window.__WB_QUERY && DEFAULT_TAB_BY_STAGE[window.__WB_QUERY.stage]) || (window.__WB_DEFAULT_TAB || 'config'))

// 页签联动：按工作流环节切换到对应页面tab
// 自动化测试（04）→ 测试报告
const showTestReport = () => {
  if (currentStatus.value === 'testing' || currentStatus.value === 'tested' || testReport.uploaded) {
    activeTab.value = 'testrep'
  }
}

// 上线审批，36）→ 审批矩阵
const showApproval = () => {
  activeTab.value = 'approval'
}

// 监控运维，37/09/10）→ 监控运维
const showMonitor = () => {
  activeTab.value = 'monitor'
}

// ========== 商品基本信息 ==========
const productInfo = computed(() => {
  if (isArchive.value && props.archiveSnapshot.productInfo) {
    return {
      workOrderId: props.archiveSnapshot.productInfo.workOrderId,
      prodPrcName: props.archiveSnapshot.productInfo.prodPrcName,
      templateLabel: props.archiveSnapshot.productInfo.templateLabel,
      status: props.archiveSnapshot.productInfo.status || 'listed',
    }
  }
  // 对话式配置：标题沿用用户解析出的套餐名称（若有）；未命名时用占位标题，
  // 工单号和状态使用本场景自己的值（不读写产品运营助手全局 launch 状态），保证该场景内自活。
  if (dialogueMode.value && props.parsedFields) {
    // 商品名优先取用户在表单中实际编辑后的名称，改名后面板标题/预览实时同步
    const editedName = String(formData.prodName ?? '').trim()
    const name = editedName || props.parsedFields.prodName || '校园套餐（待命名，'
    return {
      ...launchProductConfig.productInfo,
      prodPrcName: name,
      workOrderId: props.workOrderId || '',
      status: dialogueStatus.value,
    }
  }
  // 复制模式：基于存量商品归档档案生成的新商品（草稿态、新工单号、商品名追加“（复制）”）
  if (copyMode.value) {
    const src = props.archiveSnapshot?.productInfo || {}
    const vals = props.archiveSnapshot?.values || {}
    // 商品名优先取用户在表单中实际编辑后的名称，保证改名后面板标题/预览实时同步
    const editedName = String(formData.prodName ?? '').trim()
    return {
      ...launchProductConfig.productInfo,
      workOrderId: copyWorkOrderId.value,
      prodPrcName: editedName || (vals.prodName || src.prodPrcName || '') + '（复制）',
      templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
      status: copyStatus.value,
    }
  }
  // 多商品：标题/工单号以当前选中商品实例为准（名称优先取表单实际编辑值），切换商品即联动。
  const cur = currentProduct.value || {}
  if (cur.name || cur.code) {
    const editedName = String(formData.prodName ?? '').trim()
    return {
      ...launchProductConfig.productInfo,
      prodPrcName: editedName || cur.name || launchProductConfig.productInfo.prodPrcName,
      workOrderId: cur.code || launchProductConfig.productInfo.workOrderId,
    }
  }
  return launchProductConfig.productInfo
})

const currentStatus = computed(() => {
  if (submitted.value) {
    return productInfo.value.status === 'completed' ? 'completed' :
           productInfo.value.status === 'listed' ? 'listed' :
           productInfo.value.status === 'testing' ? 'testing' :
           productInfo.value.status === 'tested' ? 'tested' :
           productInfo.value.status === 'filing' ? 'filing' :
           productInfo.value.status === 'channel' ? 'channel' : 'submitted'
  }
  return productInfo.value.status
})

const footerSubmittedText = computed(() => {
  if (currentStatus.value === 'listed') return '已上架销售中'
  if (currentStatus.value === 'completed') return '已审批通过'
  if (currentStatus.value === 'testing') return '审批通过，进入测试'
  if (currentStatus.value === 'tested') return '测试完成，等待备案公示'
  if (currentStatus.value === 'filing') return filing.submitted ? '备案已提交，等待通过' : '备案公示中，待提交备案'
  if (currentStatus.value === 'channel') return '待选择销售渠道'
  return '已提交，进入审批阶段'
})

// ========== 表单数据 ==========
// 场景 schema：默认为 launchProductConfig；对话式/批量场景传入 schemaConfig 时（如校园青春卡）使用该场景专属配置，
// 保证各场景字段 AI推荐理由不同（尤其避免把"沿用原套餐"这类仅适用于存量复制"启动优化的表述带入新建套餐场景）
const config = computed(() => {
  // 仅对话式/批量（parsedFields 存在）且显式传入 schemaConfig 时切换场景 schema
  if (props.schemaConfig && !props.archiveSnapshot) return props.schemaConfig
  return launchProductConfig
})
const formData = reactive({})
const modifiedFields = reactive({})

// 归档（配置档案）模式：测试报告/备案公示/销售渠道三个页签改用该工单自身的历史快照，
// 而非全局"当前正在配置的套餐"状态；非归档模式回落到全局数据源、"
const archiveTestReport = computed(() => (isArchive.value ? (props.archiveSnapshot.testReport || {}) : testReport))

// 对话式配置：记录"用户已解析"字段（渲染为"已解析彩色标签，
const parsedFilled = reactive({})
// 记录用 AI 自动补全的字段（渲染一 "AI推荐"标签，"
const aiFilled = reactive({})

// 初始化表单数据。
const initFormData = () => {
  // 对话式配置模式：打开工作台即自动填入——已解析字段标记"已解析"，未解析字段用 AI 按模板自动补全（标"复用"户"AI推荐"，
  if (dialogueMode.value) {
    const vals = props.parsedFields || {}
    const applyField = (key, field) => {
      if (vals[key] !== undefined && vals[key] !== '') {
        // 用户已解析字段：直接填入并标"已解析"
        formData[key] = vals[key]
        parsedFilled[key] = '根据用户需求解析得出'
        aiFilled[key] = undefined
      } else {
        // 未解析字段：AI 自动补全模板推荐值。
        parsedFilled[key] = undefined
        // 商品名称未由用户给出时保持空，由标题占位展示，而非复用示例套餐同。
        if (key === 'prodName' || key === 'prodPrcName') {
          formData[key] = ''
          aiFilled[key] = undefined
          return
        }
        if (field.tag === 'reuse') {
          // 对话式批量配置无原套餐可复用，统一按 AI推荐"给出补全理由
          aiFilled[key] = `${field.label}由AI按业务模板自动补全（建议值：${field.value ?? '--'}）`
        } else {
          // 对话式批量配置为全新商品（无原套餐参考），AI 推荐理由不得引用
          // "沿用原套餐"复用原套餐"基于原套餐"等仅适用「存量复制"启动优化」场景的表述
          const reason = field.tagReason || ''
          const tainted = /(沿用|复用|基于|继承|参考|参照)原套餐/.test(reason)
          aiFilled[key] = tainted
            ? `${field.label}由AI按业务模板自动补全（建议值：${field.value ?? '--'}）`
            : (reason || `${field.label}由AI按业务模板推荐`)
        }
        if (field.type === 'table') {
          formData[key] = (field.rows || []).map((r) => ({ ...r }))
        } else if (field.type === 'checkbox-group') {
          // 对话式配置默认勾选：渠道类型选中前四个渠道，适用地区选中四川全省
          if (key === 'channelType') {
            formData[key] = ['offline', 'online', 'local-online', 'cmcc-online']
          } else if (key === 'applicableArea') {
            formData[key] = ['sc']
          } else {
            formData[key] = Array.isArray(field.value) ? field.value.slice() : []
          }
        } else {
          // 累计订购次数：AI 推荐默认 1，避免留空。
          formData[key] = key === 'totalOrderTimes'
            ? '1'
            : (field.value !== undefined ? field.value : '')
        }
      }
    }
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => applyField(key, field))
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => applyField(field.key, field))
    })
    return
  }
  // 只读档案模式：用归档快照里的字段值填充（缺失字段回落到模板默认值）
  if (isArchive.value && props.archiveSnapshot.values) {
    const vals = props.archiveSnapshot.values
    // 基础信息是对象（key→field），其余 sections 是字段数组。
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => {
      formData[key] = (vals[key] !== undefined ? vals[key] : field.value)
    })
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => {
        if (field.type === 'table') {
          formData[field.key] = Array.isArray(vals[field.key])
            ? vals[field.key].map((r) => ({ ...r }))
            : (field.rows || []).map((r) => ({ ...r }))
        } else {
          formData[field.key] = (vals[field.key] !== undefined ? vals[field.key] : field.value)
        }
      })
    })
    return
  }
  // 基础信息
  Object.entries(config.value.baseInfo).forEach(([key, field]) => {
    formData[key] = field.value
  })
  // 资源配置
  config.value.resourceSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 营销资源
  config.value.marketingSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 销售规则。
  config.value.salesSections.forEach((section) => {
    section.fields.forEach((field) => {
      if (field.type === 'table') {
        // 表格类型：深拷贝 rows 分到 formData
        formData[field.key] = (field.rows || []).map((r) => ({ ...r }))
      } else {
        formData[field.key] = field.value
      }
    })
  })
  // 多商品：以当前选中商品的身份/资源值覆盖模板默认值，使各页签随商品切换联动。
  applyProductSeed()
}

// 用 window.__WB_PRODUCT（当前选中商品）覆盖身份与资源字段，未提供的字段保持模板默认值。
const applyProductSeed = () => {
  const p = window.__WB_PRODUCT
  if (!p) return
  const seed = {
    prodName: p.name,
    prodPrcName: p.name,
    prodCode: p.code,
    monthlyFee: p.fee,
    flowAmount: p.flow,
    flowUnit: p.flowUnit,
    voiceAmount: p.voice,
    smsAmount: p.sms,
  }
  Object.keys(seed).forEach((key) => {
    if (seed[key] !== undefined && seed[key] !== '' && key in formData) {
      formData[key] = seed[key]
    }
  })
}
// 对话式配置：字段初始空值（标量空串 / 多选空数组 / 表格空数组）
const emptyValue = (field) => {
  if (field.type === 'table') return []
  if (field.type === 'checkbox-group') return []
  return ''
}
initFormData()

// 多商品切换：activeProductId 变化时，把新商品实例同步进响应式 currentProduct，
// 并完整重跑 initFormData() 全量回填各表单/页签（不再只覆写 8 个身份字段），
// 同时清空上一商品的修改态与标签态，确保点击商品 chip / 列表后所有 tab 按新商品重新初始化。
watch(() => props.activeProductId, () => {
  currentProduct.value = window.__WB_PRODUCT || {}
  Object.keys(modifiedFields).forEach((k) => { delete modifiedFields[k] })
  Object.keys(parsedFilled).forEach((k) => { delete parsedFilled[k] })
  Object.keys(aiFilled).forEach((k) => { delete aiFilled[k] })
  initFormData()
  snapshotPrevValues()
})

// 字段标签：优先"用户已解析；对话式配置场景下无原套餐可复用，其余字段统一标"AI推荐"，"
// 只读档案（存量快照）为历史归档数据，不显示 AI推荐/复用标签；否则按"AI补全/原配置标签"
const fieldTag = (field) => {
  if (parsedFilled[field.key]) return 'parsed'
  if (isArchive.value) return null
  if (dialogueMode.value) return 'ai'
  if (aiFilled[field.key]) return 'ai'
  return field.tag || null
}

// 基础信息字段列表
const baseFields = computed(() =>
  Object.entries(config.value.baseInfo).map(([key, field]) => ({ key, ...field }))
)

// 资源配置 sections
const resourceSections = computed(() => config.value.resourceSections)

// 营销资源 sections
const marketingSections = computed(() => config.value.marketingSections)

// 销售规则 sections
const salesSections = computed(() => config.value.salesSections)

// ========== Section 折叠 ==========
const expandedSections = reactive({})
;[...config.value.resourceSections, ...config.value.marketingSections, ...config.value.salesSections].forEach((s) => {
  expandedSections[s.key] = true
})

const toggleSection = (key) => {
  expandedSections[key] = !expandedSections[key]
}

// ========== 面板操作 → 对话镜像 ==========
// 右侧面板上的操作会实时映射为左侧对话中的一条用户消息，用于记录用户操作过程。
// 每次操作用 emit('panel-action', { text, category, timestamp }) 通知父组件（ChatPage）追加用户消息。
const cloneVal = (v) => (Array.isArray(v) ? v.map((r) => ({ ...r })) : v)

// 记录各字段的上一次值，用于计算"从…修改为—"
const prevValues = reactive({})
const snapshotPrevValues = () => {
  Object.keys(formData).forEach((k) => { prevValues[k] = cloneVal(formData[k]) })
}
snapshotPrevValues()

// 用户侧消息内容 → 展示值（select/checkbox 用 label，数组/表格拼接，其余原样）
const toDisplay = (key, val) => {
  if (val === undefined || val === null || val === '') return ''
  const f = findField(key)
  if (f && f.options) return optionLabel(key, val)
  if (Array.isArray(val)) {
    if (val.length === 0) return ''
    if (val.some((v) => typeof v === 'object')) {
      return val.map((r) => Object.values(r || {}).filter((x) => x !== '' && x !== null && x !== undefined).join(' ')).join('，')
    }
    return val.map((v) => String(v)).join('、')
  }
  return String(val)
}

// 统一出口：发出面板操作记当。
const emitPanelAction = (payload) => {
  const text = (payload && payload.text) || ''
  if (!text) return
  emit('panel-action', { text, category: payload.category || 'action', timestamp: Date.now() })
}

// 文本输入防抖：停止输入后一次性记录，避免每个按键生成一条消息。
const fieldDebounceTimers = {}
const scheduleRecordFieldChange = (key) => {
  if (fieldDebounceTimers[key]) clearTimeout(fieldDebounceTimers[key])
  fieldDebounceTimers[key] = setTimeout(() => {
    delete fieldDebounceTimers[key]
    recordFieldChange(key)
  }, 500)
}

// 记录单个字段值被修改（从…改为…，仅在展示值真正变化时记录，
const recordFieldChange = (key) => {
  const field = findField(key)
  const label = field ? (field.label || key) : key
  const newVal = toDisplay(key, formData[key])
  const oldVal = toDisplay(key, prevValues[key])
  prevValues[key] = cloneVal(formData[key])
  if (!label || oldVal === newVal) return
  let text = `已将「${label}」修改为「${newVal}」`
  if (oldVal) text += `（原为「${oldVal}」）`
  emitPanelAction({ text, category: 'field' })
}

// ========== 字段修改标记 ==========
const markModified = (key) => {
  modifiedFields[key] = true
  const field = findField(key)
  if (field && field.options) {
    // 下拉选择：离散修改，立即记录
    recordFieldChange(key)
  } else {
    // 文本案/文本域：防抖记录，避免逐字刷屏
    scheduleRecordFieldChange(key)
  }
}

// ========== Checkbox 多选组 ==========
const checkboxValue = (key, val) => {
  const v = formData[key]
  if (Array.isArray(v)) return v.includes(val)
  if (typeof v === 'string') return v.split(',').includes(val)
  return false
}
const toggleCheckbox = (key, val) => {
  let arr = Array.isArray(formData[key]) ? [...formData[key]] : (formData[key] ? formData[key].split(',') : [])
  if (arr.includes(val)) {
    arr = arr.filter((v) => v !== val)
  } else {
    arr.push(val)
  }
  formData[key] = arr
  modifiedFields[key] = true
  const field = findField(key)
  const label = field ? (field.label || key) : key
  const opts = (field && field.options) || []
  const selectedLabels = arr.map((v) => {
    const o = opts.find((x) => String(x.value) === String(v))
    return o ? o.label : String(v)
  })
  emitPanelAction({ text: `已更新「${label}」勾选为，${selectedLabels.join('、') || '（无，'}`, category: 'field' })
}

// ========== 表格行管理==========
const tableRows = (key) => {
  return Array.isArray(formData[key]) ? formData[key] : []
}

const addTableRow = (key, columns) => {
  const newRow = {}
  columns.forEach((col) => {
    if (col.type === 'auto') {
      newRow[col.key] = ''
    } else if (col.type === 'select') {
      if (col.dynamicOptions) {
        // 动态选项列：取关联字段值对应的第一个选项
        const refField = col.options[Object.keys(col.options)[0]]
        newRow[col.key] = refField && refField[0] ? refField[0].value : ''
      } else {
        newRow[col.key] = col.options && col.options[0] ? col.options[0].value : ''
      }
    } else {
      newRow[col.key] = ''
    }
  })
  if (!Array.isArray(formData[key])) {
    formData[key] = []
  }
  formData[key].push(newRow)
  modifiedFields[key] = true
  const fLabel = findField(key)?.label || key
  emitPanelAction({ text: `已在「${fLabel}」中新增一行配置`, category: 'table' })
}

const deleteTableRow = (key, rowIdx) => {
  if (Array.isArray(formData[key])) {
    formData[key].splice(rowIdx, 1)
    modifiedFields[key] = true
    const fLabel = findField(key)?.label || key
    emitPanelAction({ text: `已在「${fLabel}」中删除一行配置`, category: 'table' })
  }
}

// ========== 前项限制提示语自动生成==========
const labelMap = { age: '年龄', globalLevel: '全球通等线', baseMonthlyFee: '基础套餐月费' }
const ruleTypeMap = { gt: '大于', lt: '小于', ge: '大于等于', le: '小于等于', eq: '等于' }
const limitTypeMap = { allow: '允许办理', disallow: '不允许办理' }

const generatePreLimitTip = (key, rowIdx) => {
  const row = tableRows(key)[rowIdx]
  if (!row) return ''
  const label = labelMap[row.label] || row.label || '条件'
  const ruleType = ruleTypeMap[row.ruleType] || row.ruleType || ''
  const limitValue = row.limitValue || ''
  const limitType = limitTypeMap[row.limitType] || row.limitType || ''
  if (!limitValue) return `${label}${ruleType}___${limitType}`
  return `${label}${ruleType}${limitValue}${limitType}本套餐`
}

// ========== 后项限制动态选项 ==========
const getDynamicOptions = (col, row) => {
  if (!col.dynamicOptions || !col.options) return []
  // 后项限制业务名称列：依赖同行 ruleType 字段
  const refValue = row.ruleType || Object.keys(col.options)[0]
  return col.options[refValue] || col.options[Object.keys(col.options)[0]] || []
}

// 表格单元格变更时：如果修改的是被依赖的字段（如后项限制的规则类型），重置动态选项列的值。
const onTableCellChange = (key, rowIdx) => {
  modifiedFields[key] = true
  // 后项限制：ruleType 变更时，检查 bizName 是否在新选项中，不在则重置。
  if (key === 'postLimitTable') {
    const row = tableRows(key)[rowIdx]
    // 找到 bizName 列定义。
    const section = config.value.salesSections.find((s) => s.key === 'post-limit')
    if (section) {
      const tableField = section.fields.find((f) => f.key === key)
      if (tableField) {
        const bizCol = tableField.columns.find((c) => c.key === 'bizName')
        if (bizCol && bizCol.dynamicOptions) {
          const newOptions = getDynamicOptions(bizCol, row)
          const stillValid = newOptions.some((o) => o.value === row.bizName)
          if (!stillValid && newOptions.length > 0) {
            row.bizName = newOptions[0].value
          }
        }
      }
    }
  }
}

// 动态选项变更时：更新当前单元格的值。
const onDynamicOptionChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
  recordFieldChange(key)
}

// 普通 select 单元格变更。
const onSelectCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  // 触发 onTableCellChange 逻辑（处理后项限制 ruleType 变更时重置/bizName，
  onTableCellChange(key, rowIdx)
  recordFieldChange(key)
}

// 普通 input 单元格变更。
const onInputCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
  // 前项限制：输入限定值时自动刷新提示语（auto类型单元格依赖同行数据）
  if (key === 'preLimitTable') {
    // auto类型单元格是 computed 式渲染，row 更新后自动刷新。
  }
  scheduleRecordFieldChange(key)
}

// ========== 信息预览 - APP手机壳模板==========
const previewSections = computed(() => config.value.previewSections || launchProductConfig.previewSections)

const phoneTab = ref('user')
const phoneTime = ref('9:41')

const phoneNavTitle = computed(() => {
  const map = { user: '商品详情', freefill: '受理免填单', sms: '短信通知' }
  return map[phoneTab.value] || '商品详情'
})

// ========== 信息预览 Tab 数据 —— 统一从 formData 取当前配置值，确保与对话式配置工作台一致==========
const previewFee = computed(() => {
  const v = formData.monthlyFee
  if (v !== undefined && v !== '') return String(v).replace(/\.00$/, '')
  return String(productInstance.value.fee || '').replace(/\.00$/, '')
})
// 预览区商品名：优先取表单里用户实际编辑后的名称（改名后手机预览/免填单/短信模板同步更新，
const previewName = computed(() => {
  const v = String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim()
  return v || productInfo.value.prodPrcName
})
const previewFlowUnit = computed(() => formData.flowUnit || productInstance.value.flowUnit || 'GB')
const previewFlowAmount = computed(() => formData.flowAmount || productInstance.value.flow || '')
const previewVoiceAmount = computed(() => formData.voiceAmount || productInstance.value.voice || '')
const previewSmsAmount = computed(() => formData.smsAmount || productInstance.value.sms || '')
const previewFlowOvercharge = computed(() => formData.flowOvercharge || '0.29元/MB')
const previewVoiceOvercharge = computed(() => formData.voiceOvercharge || '0.15元/分钟')
const previewSmsOvercharge = computed(() => formData.smsOvercharge || '')
// 权益表：对话式配置与只读档案（存量快照）按权益资源配置动态展示；产品运营助手(launch)工作台保持原始静态权益不发。
const previewRights = computed(() =>
  dynamicPreview.value && Array.isArray(formData.rightsTable) ? formData.rightsTable : []
)

// 根据字段 key 查找配置定义（用于 value→label 映射，
const findField = (key) => {
  if (config.value.baseInfo && config.value.baseInfo[key]) return config.value.baseInfo[key]
  const all = [
    ...(config.value.resourceSections || []).flatMap((s) => s.fields),
    ...(config.value.marketingSections || []).flatMap((s) => s.fields),
    ...(config.value.salesSections || []).flatMap((s) => s.fields),
  ]
  return all.find((f) => f.key === key)
}
const optionLabel = (key, val) => {
  if (val === undefined || val === '' || val === null) return ''
  const f = findField(key)
  const opt = ((f && f.options) || []).find((o) => String(o.value) === String(val))
  return opt ? opt.label : String(val)
}

// 权益列表 —— 对话式只读档案按权益配置表渲染，非对话式（产品运营助手 launch）保持原始静态权益列表。
const equityList = computed(() => {
  if (!dynamicPreview.value) { return [
      { name: '国内通用流量', desc: '120GB · 当月未用自动结转至次月', short: '流', color: '#06a0ff' },
      { name: '国内通话', desc: '1000分钟 · 国内主叫', short: '语', color: '#10b981' },
      { name: '5G-A网络权益', desc: '5G-A网络覆盖下享受极致速率体验', short: '5G', color: '#8b5cf6' }
    ]
  }
  const rights = previewRights.value
  if (rights.length) {
    const palette = ['#06a0ff', '#00be5f', '#f59e0b', '#8b5cf6', '#ef4444', '#0ea5e9']
    return rights.map((r, i) => ({
      name: r.rightName,
      desc: r.obtainType === 'auto' ? '订购后自动发放，次月起生效' : '办理后手动领取',
      short: (r.rightName || '权').slice(0, 1),
      color: palette[i % palette.length],
    }))
  }
  // 无权益配置表时：按当前商品实际资源生成权益项（避免回落到与本品无关的固定权益）
  const palette = ['#06a0ff', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444']
  const items = []
  if (previewFlowAmount.value) items.push({ name: '国内通用流量', desc: previewFlowAmount.value + (previewFlowUnit.value || 'GB') + ' · 当月未用自动结转至次月', short: '流' })
  if (previewVoiceAmount.value) items.push({ name: '国内通话', desc: previewVoiceAmount.value + '分钟 · 国内主叫', short: '语' })
  if (previewSmsAmount.value) items.push({ name: '国内短信', desc: previewSmsAmount.value + '条 · 国内点对点短信', short: '短' })
  if (items.length) return items.map((it, i) => ({ ...it, color: palette[i % palette.length] }))
  return [
    { name: '腾讯视频月卡', desc: '热门综艺抢先省', short: '规', color: '#06a0ff' },
    { name: '爱奇艺月单', desc: '热播剧集随心省', short: '套', color: '#00be5f' },
  ]
})

// 免填单数据 —— 对话式只读档案按实际配置值生成。
const freeFillItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const area = (Array.isArray(formData.applicableArea) ? formData.applicableArea : [])
      .map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('、') || '四川省'
    const includeRes = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '流量',
      previewVoiceAmount.value + '分钟语音',
      previewSmsAmount.value ? previewSmsAmount.value + '条短信' : '',
      previewRights.value.map((r) => r.rightName).join('、'),
    ].filter(Boolean).join('+')
    return [
      { label: '资费名称', value: name },
      { label: '套餐月费', value: `${previewFee.value}元/月` },
      { label: '包含资源', value: includeRes || '--', highlight: true },
      { label: '超套收费标准', value: [previewFlowOvercharge.value, previewVoiceOvercharge.value, previewSmsOvercharge.value].filter(Boolean).join(' / ') || '--' },
      { label: '限定条件', value: `${area}范围内使用` },
      { label: '生效方式', value: optionLabel('effRule', formData.effRule) || '立即生效' },
      { label: '有效月', value: optionLabel('validity', formData.validity) || '长期有效' },
      { label: '续订方式', value: optionLabel('renewMethod', formData.renewMethod) || '自动续订' },
      { label: '同时订购', value: `${formData.orderTimes || '1'}次` },
      { label: '套餐固定费', value: `${previewFee.value || '--'}元` },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'free-fill-preview')
  return section ? section.items : []
})

// 短信模板数据 —— 对话式只读档案按实际配置值生成。
const smsItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const fee = `${previewFee.value}元`
    const ress = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '流量',
      previewVoiceAmount.value + '分钟语音',
      previewSmsAmount.value ? previewSmsAmount.value + '条短信' : '',
    ].filter(Boolean).join('和')
    return [
      { label: '办理成功短信', value: `恭喜您成功办理${name}，月费${fee}，含${ress}，感谢您的支持！` },
      { label: '预约生效短信', value: `您的${name}已生效，月费${fee}，生效当月起享受套餐权益。` },
      { label: '前台二确短信', value: `尊敬的客户，您正在办理${name}，月费${fee}，是否确认办理？` },
      { label: '退订短信', value: `您好，您的${name}已退订，如有疑问请致电10000。` },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'sms-preview')
  return section ? section.items : []
})

// ========== 底部操作 ==========
const saveDraft = () => {
  Object.keys(modifiedFields).forEach((k) => (modifiedFields[k] = false))
  ElMessage.success('草稿"保存成功')
  emitPanelAction({ text: `保存当前配置为草稿（${productInfo.value.prodPrcName}）`, category: 'action' })
}

// ========== 智能稽核 ==========
const auditVisible = ref(false)
const auditLoading = ref(false)
const auditProgressText = ref('')
const auditResults = ref([])
// 只读档案模式强制为已提交（submitted=true），使所有字段禁用、底部显示归档状态。
const submitted = ref(!!props.archiveSnapshot)
// 复制模式商品的状态（草稿 →待审核），与全局 launch 状态隔离。
const copyStatus = ref('draft')

// 复制：基于存量商品归档档案生成可编辑新商品。保持只读档案预填字段值为模板数据，
// 改为草稿态可编辑，生成新工单号，商品名追加“（复制）”，并通知父组件登记「我的草稿」。
const onCopyFromArchive = () => {
  if (!props.archiveSnapshot) return
  const src = props.archiveSnapshot.productInfo || {}
  const vals = props.archiveSnapshot.values || {}
  copyWorkOrderId.value = `WO${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 90 + 10)}`
  copyStatus.value = 'draft'
  copyMode.value = true
  submitted.value = false
  // 清空 AI/已解析标签，使预填字段按普通模板数据显示（可自由编辑）
  Object.keys(aiFilled).forEach((k) => { aiFilled[k] = undefined })
  Object.keys(parsedFilled).forEach((k) => { parsedFilled[k] = undefined })
  if (formData.prodName !== undefined) formData.prodName = String(formData.prodName) + '（复制）'
  if (formData.prodCode !== undefined) formData.prodCode = copyWorkOrderId.value
  activeTab.value = 'base'
  ElMessage.success('已复制存量商品配置，转为可编辑新商品')
  emitPanelAction({ text: `基于存量商品「${src.prodPrcName || src.workOrderId || '存量商品'}」复制生成新商品配置`, category: 'action' })
  // 通知父组件在「我的草稿」登记该复制品（source='copy'，
  emit('copy-init', {
    workOrderId: copyWorkOrderId.value,
    prodName: (vals.prodName || src.prodPrcName || '') + '（复制）',
    templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
    summary: `${vals.monthlyFee ? vals.monthlyFee + '元/月' : ''} · 复制自?${src.prodPrcName || src.workOrderId || '存量商品'}`,
    values: { ...vals },
  })
}

// 复制模式/对话式配置下监听商品名编辑，实时同步给父组件（我的草稿/流程/权责/审批展示保持一致）
watch(() => formData.prodName, (name) => {
  if (copyMode.value && copyWorkOrderId.value && name) {
    emit('copy-rename', String(name).trim())
  }
  // 对话式配置：用户在工作台内命名/改名后，实时同步给父组件（流程/stepper 等联动）
  if (dialogueMode.value && !copyMode.value && props.workOrderId && name) {
    emit('dialogue-rename', String(name).trim())
  }
})

// 收集面板内用户实际编辑的全部表单值（含表格行），用于归档工单"查看完整工作台档案"只读回看
const collectFormValues = () => {
  const values = {}
  Object.keys(formData).forEach((k) => {
    const v = formData[k]
    values[k] = Array.isArray(v) ? v.map((r) => ({ ...r })) : v
  })
  return values
}

const showAudit = () => {
  emitPanelAction({ text: `点击「智能稽核」，对当前配置进行合规性检查`, category: 'action' })
  auditVisible.value = true
  auditLoading.value = true
  auditResults.value = []

  const steps = [
    '正在执行智能稽核...',
    '基础信息完整性校验/..',
    '必填字段检查/..',
    '业务规则校验...',
    '短信模板合规性检查/..',
  ]

  let stepIdx = 0
  auditProgressText.value = steps[0]

  const stepTimer = setInterval(() => {
    stepIdx++
    if (stepIdx < steps.length) {
      auditProgressText.value = steps[stepIdx]
    } else {
      clearInterval(stepTimer)
    }
  }, 800)

  setTimeout(() => {
    clearInterval(stepTimer)
    auditLoading.value = false

    const results = []
    results.push({ type: 'success', title: '必填项检查通过', desc: '所有必填字段已填写' })
    if (!formData.monthlyFee) {
      results.push({ type: 'error', title: '缺少套餐固定费', desc: '套餐固定费未填写，根据规范必填' })
    } else {
      results.push({ type: 'success', title: '月租配置合规', desc: '套餐固定费、税率配置完整' })
    }
    if (!formData.flowAmount) {
      results.push({ type: 'error', title: '缺少流量资源配置', desc: '流量资源配置未填写，根据规范必填' })
    } else {
      results.push({ type: 'success', title: '资源配额检查通过', desc: '流量、语音、短信资源配置完整' })
    }
    results.push({ type: 'success', title: '日期配置合规', desc: '销售日期配置符合业务规则' })
    results.push({ type: 'success', title: '短信模板合规', desc: '短信模板关键词检查通过' })

    auditResults.value = results

    const hasError = results.some((r) => r.type === 'error')
    if (!hasError) {
      setTimeout(() => {
        submitted.value = true
        // 对话式配置用本场景独立状态（草稿 →待审核），不读写产品运营助手全局 launch 状态，避免跨场景串批。
        if (dialogueMode.value) {
          dialogueStatus.value = 'submitted'
        } else if (copyMode.value) {
          // 复制模式同样用独立状态，不覆盖全局 launch 配置
          copyStatus.value = 'submitted'
        } else {
          launchProductConfig.productInfo.status = 'submitted'
        }
        auditVisible.value = false
        ElMessage.success('稽核通过，配置已提交')
        emit('submitted', {
          workOrderId: productInfo.value.workOrderId,
          values: collectFormValues(),
          // 提交时回传面板内实际商品名（用户在复制/编辑后可能改名），供父组件同步审批/流程/回复
          productName: String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim(),
        })
        emitPanelAction({ text: `智能稽核通过，配置已提交（工单号：${productInfo.value.workOrderId}）`, category: 'action' })
        // 批量配置模式：提交后通知父组件标记草稿已提交并自动跳转下一个。
        if (batchMode.value) {
          emit('batch-submitted')
        }
      }, 600)
    }
  }, 4200)
}

const onSubmit = () => {
  emitPanelAction({ text: `点击「提交配置」，发起智能稽核并提交（${productInfo.value.prodPrcName}）`, category: 'action' })
  showAudit()
}

// tab 数量增加时自动向左扩展面板宽度，避免 tab 溢出（只扩不减，尊重用户手动拖窄，
watch(() => tabs.value.length, (count) => {
  const targetByCount = { 5: DEFAULT_WIDTH, 6: 640, 7: 720, 8: 800 }
  const target = targetByCount[count]
  if (target && target > panelWidth.value) {
    panelWidth.value = target
  }
})

// 对话式配置·增量字段更新：用户通过后续对话（如"商品叫xxx"/"月费改成xxx"）调整已打开面板的字段值。
// 效果等同用户在工作台内直接修改并标注"已解析"（渲染彩色标签），并联动手机预览/免填单/短信模板/流程名称。
const applyFieldUpdate = (key, value) => {
  if (!key) return
  formData[key] = value
  // 标记"已解析"、清除原月 AI推荐"标签
  parsedFilled[key] = '根据用户需求解析得出'
  aiFilled[key] = undefined
  // 商品名称/工单名联动：更新预览区与流程 stepper（productInfo computed 已优先取表单名称，
  if (key === 'prodName') {
    // 若同时存在商品档案名称（prodPrcName）字段也一并同步，保证标题/预览/免填单一致。
    if ('prodPrcName' in config.value.baseInfo) formData.prodPrcName = value
  }
}

// 暴露提交方法，供外部（如快捷回复"智能稽核并提交"）调用，效果等同点击"提交配置"
__expose({
  submit: onSubmit,
  showTestReport,
  showApproval,
  showMonitor,
  // 供历史会话恢复时同步"已提交"状态，使底部操作栏随流程阶段切换"
  setSubmitted: (val) => { submitted.value = !!val },
  // 供对话式配置场景：用户追加对话增量更新面板字段。
  applyFieldUpdate,
})

// ===== 工作流节点内容（按 node_name 索引的 result_json）读取工具 =====
// 各页签优先取「对应工作流节点保存内容」，取不到再回落到基于商品实例的静态兜底，保证既有形态不破。
const nodeContent = () => {
  const p = currentProduct.value || {}
  const byNode = p.nodeResults || {}
  const q = window.__WB_QUERY || {}
  // 当前商品对应的 offer_seq / offer_id，用于从多商品 offers[] 中定位本品
  const wantSeq = p.offer_seq !== undefined && p.offer_seq !== null && p.offer_seq !== '' ? String(p.offer_seq) : ''
  const wantId = String(p.code || q.offer_id || '').trim()
  // 从（可能是 JSON 字符串的）节点内容中解出对象/数组
  const asObj = (v) => {
    if (!v) return null
    if (typeof v === 'object') return v
    try { return JSON.parse(v) } catch (e) { return null }
  }
  // 取某节点的解析后对象
  const node = (nm) => asObj(byNode[nm])
  // 从节点内容里挑出当前商品对应的元素：offers[] 按 offer_seq/offer_id 命中，命中不到取首元素
  const offerOf = (rec) => {
    const obj = asObj(rec)
    if (!obj) return null
    const arr = Array.isArray(obj) ? obj : (Array.isArray(obj.offers) ? obj.offers : null)
    if (arr && arr.length) {
      if (wantSeq) { const hit = arr.filter((o) => o && String(o.offer_seq) === wantSeq)[0]; if (hit) return hit }
      if (wantId) { const hit = arr.filter((o) => o && String(o.offer_id || o.offerId) === wantId)[0]; if (hit) return hit }
      return arr[0]
    }
    return obj
  }
  // 深度取首个非空标量（支持点路径），用于兼容多种信封字段命名
  const pick = (obj, paths) => {
    if (!obj) return ''
    for (const path of paths) {
      let cur = obj
      for (const part of path.split('.')) { cur = cur == null ? null : cur[part] }
      if (cur !== undefined && cur !== null && String(cur).trim() !== '') return cur
    }
    return ''
  }
  return { byNode, node, offerOf, pick, asObj }
}

// ===== 商品实例数据源（单一来源）：所有页签的数据统一由它派生 =====
// 实例身份取自 ①URL 外链（offer_id/name/req_id）②已配置的表单值（formData）③全局 launch 档案。
// 切换不同商品（换 URL 参数 / 修改表单）即全量联动初始化各页签，而非各页签各自一小撮演示数据。
const productInstance = computed(() => {
  const q = window.__WB_QUERY || {}
  const p = currentProduct.value || {}
  const num = (v) => (v === undefined || v === null || v === '' ? '' : String(v))
  const name = String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim()
    || p.name || q.name || q.offer_name || '待命名商品'
  const code = num(formData.prodCode) || num(p.code) || num(productInfo.value.workOrderId) || num(q.offer_id) || 'SP' + Date.now().toString().slice(-8)
  const fee = num(formData.monthlyFee) || num(p.fee) || ''
  const flow = num(formData.flowAmount) || num(p.flow) || ''
  const flowUnit = num(formData.flowUnit) || num(p.flowUnit) || 'GB'
  const voice = num(formData.voiceAmount) || num(p.voice) || ''
  const sms = num(formData.smsAmount) || num(p.sms) || ''
  const applicant = '产销品域 / 产商品中心'
  const department = '产销品域 / 产商品中心'
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
  const resources = [flow ? flow + flowUnit + '流量' : '', voice ? voice + '分钟语音' : '', sms ? sms + '条短信' : ''].filter(Boolean).join('、')
  // 需求提报单原文（requirement_report）——用于需求页签动态填值
  const reqReport = nodeContent().offerOf(nodeContent().byNode.requirement_report)
  return { name, code, fee, flow, flowUnit, voice, sms, applicant, department, resources, today, now, reqReport }
})

// 需求提报页签 —— 优先取需求提报单节点（requirement_report）保存内容，缺失回落静态兜底。
const demandView = computed(() => {
  const nc = nodeContent()
  const r = productInstance.value.reqReport || {}
  const pick = nc.pick
  const rawReq = nc.byNode.requirement_report || {}
  const rawObj = nc.asObj(rawReq) || {}
  const reqId = pick(rawObj, ['req_id']) || (window.__WB_QUERY && window.__WB_QUERY.req_id) || ''
  const inst = productInstance.value
  // 需求提报单字段（含薄平单品：product_type/price/resources/out_price/billing_cycle/effective_way/validity/change_rule/cancel_rule）
  const ptype = pick(r, ['product_type', 'fields.product_type'])
  const series = pick(r, ['series', 'fields.series'])
  const price = pick(r, ['price', 'fields.price']) || inst.fee
  const resources = pick(r, ['resources', 'fields.resources']) || inst.resources
  const outPrice = pick(r, ['out_price', 'fields.out_price', 'outPrice'])
  const billing = pick(r, ['billing_cycle', 'fields.billing_cycle'])
  const validity = pick(r, ['validity', 'fields.validity'])
  const changeRule = pick(r, ['change_rule', 'fields.change_rule'])
  const cancelRule = pick(r, ['cancel_rule', 'fields.cancel_rule'])
  const effectiveWay = pick(r, ['effective_way', 'fields.effective_way'])
  // 业务需求概述：由真实字段拼装（无节点内容时才用静态兜底文案）
  const requireParts = []
  if (ptype) requireParts.push('产品类型：' + ptype)
  if (series) requireParts.push('所属系列：' + series)
  if (price) requireParts.push('套餐档位：' + price + '元/月')
  if (resources) requireParts.push('套内资源：' + resources)
  if (outPrice) requireParts.push('套外资费：' + outPrice)
  if (billing) requireParts.push('计费周期：' + billing)
  if (validity) requireParts.push('套餐有效期：' + validity)
  if (changeRule) requireParts.push('变更规则：' + changeRule)
  if (cancelRule) requireParts.push('退订/拆机规则：' + cancelRule)
  const require = pick(r, ['require', 'requirement', 'desc', 'fields.require']) || pick(rawObj, ['require'])
    || (requireParts.length ? requireParts.join('；') + '。' : ('推出' + inst.name + '，含' + (inst.resources || '基础资源') + '；首月按天折算，次月起按月收取；支撑营业厅、APP渠道销售。'))
  const target = pick(r, ['target', 'target_group', 'customer', 'fields.target']) || pick(rawObj, ['target', 'target_group'])
    || (ptype ? ('面向' + ptype + (series ? '（' + series + '）' : '') + '目标客群') : ('面向目标客群推出 ' + inst.name))
  const effective = pick(r, ['effective', 'fields.effective']) || pick(rawObj, ['effective']) || effectiveWay || '订购立即生效'
  return {
    no: pick(rawObj, ['requirement_no', 'no']) || reqId || ('XQ-' + inst.today),
    name: pick(r, ['name', 'fields.name']) || inst.name,
    applicant: pick(r, ['applicant', 'reporter', 'fields.applicant']) || inst.applicant,
    department: pick(r, ['department', 'fields.department']) || inst.department,
    date: pick(r, ['date', 'fields.date']) || inst.today,
    target,
    require,
    effective,
    channels: (() => {
      const raw = pick(r, ['channels', 'fields.channels'])
      if (Array.isArray(raw) && raw.length) return raw
      if (typeof raw === 'string' && raw.trim()) return raw.split(/[,，、]/).map((s) => s.trim()).filter(Boolean)
      return ['营业厅', 'APP']
    })(),
  }
})

// 加载方案页签 —— 优先取配置方案节点（requirement）保存内容，缺失回落静态兜底。
const schemeView = computed(() => {
  const nc = nodeContent()
  const r = nc.offerOf(nc.byNode.requirement) || {}
  const plan = nc.asObj(r.plan_json) || {}
  const base = plan.baseInfo || {}
  const opt = plan.optionalInfo || {}
  const pick = nc.pick
  const keep = (v, fallback) => (v !== undefined && v !== null && String(v).trim() !== '' ? v : fallback)
  return {
    no: keep(pick(r, ['scheme_no', 'no']), 'SCH-' + productInstance.value.today),
    requireNo: demandView.value.no,
    version: keep(pick(r, ['version']), 'V1.0'),
    name: keep(pick(r, ['name', 'fields.name']), productInstance.value.name + '加载方案'),
    author: '产商品中心（CRM_POS，',
    status: keep(pick(r, ['status']), '已确订'),
    summary: [
      ['资费名称', keep(pick(r, ['name', 'fields.name']) || pick(base, ['prodPrcName']), productInstance.value.name)],
      ['月租', (productInstance.value.fee ? productInstance.value.fee + '元/月' : '--') + '（首月按天折算，次月起按月收取）'],
      ['包含资源', pick(opt, ['printContent.containResource']) || pick(r, ['resources', 'fields.resources']) || productInstance.value.resources || '--'],
      ['套外标准', keep(pick(opt, ['outPrice']), '流量3元/GB、语音/.15元/分钟、短信/.1元/权')],
      ['生效方式', keep(pick(r, ['effective_way', 'fields.effective']), '订购立即生效 / 退订预约生效')],
      ['发布范围', keep(pick(r, ['publish_range']), '全省22个地市，营业厅、APP渠道')],
    ],
    items: ['基础信息', '月租', '语音资源', '流量资源', '短信资源', '前项限制', '后项限制'],
  }
})

// ========== 规格稽核 / 自动化测试：进行中 → 完成（与后端轮询同步） ==========
// 最终结论节点 spec/test 由后端在稽核/测试完成后写入（运行期仅写 audit_pending/test_pending）。
// 前端轮询 result/query：后端产出后自动转「完成」；后端长时间无信号时 5s 兜底转完成，避免空转。
const nodeTick = ref(0)
const auditSimDone = ref(false)
const testSimDone = ref(false)
const approvalSimDone = ref(false)
// 需求审批矩阵：动态轮询后端审批进度（GET /approval/status?req_id=...），由返回的 approval_matrix
// 实时驱动「需求审核」节点状态（进行中→已通过），非倒计时硬跳。matrix 为 null 时回落静态兜底。
const demandApprovalMatrix = ref(null)
const stageRank = (() => {
  const s = String((window.__WB_QUERY && window.__WB_QUERY.stage) || '')
  if (s === '') return 99 // 整体视图：全部环节都视为已到达
  const n = parseInt(s, 10)
  return isNaN(n) ? 99 : n
})()
const hasNodeResult = (name) => {
  const byNode = (currentProduct.value && currentProduct.value.nodeResults) || {}
  const raw = byNode[name]
  if (!raw) return false
  const obj = typeof raw === 'object' ? raw : (() => { try { return JSON.parse(raw) } catch (e) { return null } })()
  return !!(obj && Object.keys(obj).length)
}
// 稽核在环节03及以后、测试在环节06及以后才会由后端产出（或已存在对应 pending 临时态）。
const auditExpected = () => stageRank >= 3 || hasNodeResult('audit_pending')
const testExpected = () => stageRank >= 6 || hasNodeResult('test_pending')
// 审批在审批类环节（04/08/11/12）、整体视图（stage 缺失/99）或已存在审批建议节点（report）时对应。
const approvalExpected = () => {
  const s = String((window.__WB_QUERY && window.__WB_QUERY.stage) || '')
  return s === '' || s === '99' || ['04', '08', '11', '12'].indexOf(s) >= 0 || hasNodeResult('report')
}

// 轮询后端节点结果：后端写入 spec/test 后自动转「完成」；5s 未完成先兜底转完成，
// 后端稍后产出仍会覆盖渲染，保证「进行中」时长与后端同步且不会长时间空转。
let _nodePollTimer = null
let _auditFallbackTimer = null
let _testFallbackTimer = null
let _approvalFallbackTimer = null
const stopNodePolling = () => {
  if (_nodePollTimer) { clearInterval(_nodePollTimer); _nodePollTimer = null }
}
// 需求审批进度轮询：按 req_id 周期性查询后端审批进度，将 approval_matrix 写入 demandApprovalMatrix，
// 驱动「需求审批矩阵」动态刷新（需求审核节点 进行中→已通过），直至需求阶段通过或超时（60s）停止。
let _demandApprovalPollTimer = null
const stopDemandApprovalPolling = () => {
  if (_demandApprovalPollTimer) { clearInterval(_demandApprovalPollTimer); _demandApprovalPollTimer = null }
}
const startDemandApprovalPolling = () => {
  const q = window.__WB_QUERY || {}
  if (String(q.demand_approval || '') !== '1') return
  const reqId = String(q.req_id || '')
  if (!reqId) return
  const base = (window.__WB_API_BASE || (location.pathname.indexOf('/prod-ai/') === 0 ? '/prod-ai' : '')) + '/api/v1/appstore'
  const url = base + '/approval/status?req_id=' + encodeURIComponent(reqId) + '&chat_id=' + encodeURIComponent(String(q.chatId || q.chat_id || ''))
  const startedAt = Date.now()
  const pollOnce = () => {
    fetch(url, { method: 'GET' })
      .then((r) => (r && r.ok ? r.json() : null))
      .then((data) => {
        // 批量信封：审批进度在 offers[0]（工单级，各元素冗余同一 approval_matrix）
        const item = data && Array.isArray(data.offers) && data.offers.length ? data.offers[0] : data
        const matrix = item && Array.isArray(item.approval_matrix) ? item.approval_matrix : null
        if (matrix && matrix.length) {
          demandApprovalMatrix.value = matrix
          // 需求阶段通过（或无 requirement 节点未通过）或工单终态「通过」→ 停止轮询
          const reqNodes = matrix.filter((n) => String(n.stage || '') === 'requirement')
          const reqPassed = reqNodes.length > 0 && reqNodes.every((n) => String(n.status || '').indexOf('已通过') >= 0)
          if (reqPassed || String((item && item.status) || '') === '通过') stopDemandApprovalPolling()
        }
      })
      .catch(() => {})
  }
  pollOnce()
  _demandApprovalPollTimer = setInterval(() => {
    pollOnce()
    if (Date.now() - startedAt > 60000) stopDemandApprovalPolling()
  }, 2000)
}
const startNodePolling = () => {
  const canRefresh = typeof window.__WB_REFRESH_RESULT === 'function' && window.__WB_HAS_REQ
  // 兜底：5s 后仍未完成（后端无产出/降级）则前端置完成，避免「进行中」无限停留
  const armFallback = () => {
    if (auditExpected()) _auditFallbackTimer = setTimeout(() => { if (!auditView.value.ready) auditSimDone.value = true }, 5000)
    if (testExpected()) _testFallbackTimer = setTimeout(() => { if (!testRepView.value.ready) testSimDone.value = true }, 5000)
    if (approvalExpected()) _approvalFallbackTimer = setTimeout(() => { if (!approvalView.value.ready) approvalSimDone.value = true }, 5000)
  }
  armFallback()
  // 需求审批矩阵动态进度：仅当 url 带 demand_approval=1（已发起）时启动轮询，
  // 周期性拉取后端审批进度（approval_matrix 随 10s 惰性流转），真实反映「需求审核 进行中→已通过」。
  startDemandApprovalPolling()
  if (!canRefresh) return
  const startedAt = Date.now()
  _nodePollTimer = setInterval(async () => {
    try { await window.__WB_REFRESH_RESULT() } catch (e) {}
    // 首次接口降级后可能由轮询才解析出真实商品，同步当前商品引用
    if (window.__WB_PRODUCT) currentProduct.value = window.__WB_PRODUCT
    nodeTick.value++
    const needAudit = auditExpected() && !hasNodeResult('spec')
    const needTest = testExpected() && !hasNodeResult('test')
    const needApproval = approvalExpected() && !hasNodeResult('report') && !(window.__WB_QUERY && window.__WB_QUERY.approval_nodes)
    if ((!needAudit && !needTest && !needApproval) || Date.now() - startedAt > 60000) stopNodePolling()
  }, 2000)
}
onMounted(startNodePolling)
onUnmounted(() => {
  stopNodePolling()
  stopDemandApprovalPolling()
  if (_auditFallbackTimer) clearTimeout(_auditFallbackTimer)
  if (_testFallbackTimer) clearTimeout(_testFallbackTimer)
  if (_approvalFallbackTimer) clearTimeout(_approvalFallbackTimer)
})

// 稽核报告页签 —— 优先取规格稽核节点（spec）保存内容，缺失回落静态兜底。
const auditView = computed(() => {
  const nc = nodeContent()
  const raw = nc.byNode.spec
  const obj = nc.asObj(raw)
  const offer = nc.offerOf(raw) || {}
  const pick = nc.pick
  // 环节是否已产出结果：无 spec 节点内容（未完成/进行中）时不渲染“全部通过”静态报告。
  const ready = (!!(obj && Object.keys(obj).length) || !!(offer && Object.keys(offer).length)) || auditSimDone.value
  const passVal = String(obj ? pick(obj, ['pass']) || pick(offer, ['pass']) : '').trim()
  const passed = passVal === '' ? true : passVal === '1' || passVal === 'true' || passVal === '通过'
  const errList = (offer && Array.isArray(offer.error_list) ? offer.error_list : (obj && Array.isArray(obj.error_list) ? obj.error_list : []))
  const checks = errList.length
    ? errList.map((e) => {
        const item = pick(e, ['item', 'name', 'check_item', 'desc']) || '—'
        const lv = pick(e, ['result', 'level', 'status']) || (String(e && e.pass) === '0' ? '未通过' : '通过')
        return [item, lv]
      })
    : [
        ['基础信息完整态', '通过'],
        ['资源配置完整态', '通过'],
        ['套外资费完整态', '通过'],
        ['字段格式规范态', '通过'],
        ['字段枚举合法态', '通过'],
        ['配置项关联一致态', '通过'],
        ['业务规则完整态', '通过'],
      ]
  const suggest = obj ? pick(obj, ['audit_suggest']) : ''
  return {
    tick: nodeTick.value,
    ready,
    object: 'offer_id = ' + productInstance.value.code,
    status: passed ? '通过' : '未通过',
    time: productInstance.value.today,
    conclusion: suggest || ('稽核通过：配置符合本次落地销售品 新增销售品' + productInstance.value.code + ' 规则、'),
    checks,
  }
})

// 校准报告页签 —— 优先取资费校准节点（fee）保存内容，缺失回落静态兜底。
const calibrateView = computed(() => {
  const nc = nodeContent()
  const raw = nc.byNode.fee
  const obj = nc.asObj(raw)
  const offer = nc.offerOf(raw) || {}
  const pick = nc.pick
  const cmp = offer && Array.isArray(offer.compare_list) ? offer.compare_list : (obj && Array.isArray(obj.compare_list) ? obj.compare_list : [])
  const rows = cmp.length
    ? cmp.map((c) => [
        pick(c, ['project_name', 'item', 'name']) || '—',
        pick(c, ['requirement_desc', 'demand', 'expect']) || '—',
        pick(c, ['billing_desc', 'config', 'actual']) || '—',
        pick(c, ['result']) || '一致',
      ])
    : [
        ['套餐固定费', productInstance.value.fee + '元', productInstance.value.fee + '元/月', '一致'],
        ['国内通用流量', (productInstance.value.flow || '--') + productInstance.value.flowUnit, (productInstance.value.flow || '--') + productInstance.value.flowUnit, '一致'],
        ['国内通话', (productInstance.value.voice || '--') + '分钟', (productInstance.value.voice || '--') + '分钟', '一致'],
        ['套外流量标准', '3元/GB', '3元/GB', '一致'],
        ['套外语音费率', '0.15元/分钟', '0.15元/分钟', '一致'],
        ['套外短信费率', '0.1元/权', '0.1元/权', '一致'],
      ]
  const allSame = rows.every((r) => String(r[3]).indexOf('一致') >= 0 || String(r[3]).indexOf('通过') >= 0)
  // risk_summary 是面向 AI 会话的完整多商品 Markdown 报告，不能直接塞进结论条。
  // 这里只提取其中的「校准结论」单行文本；取不到时用结构化比对结果生成一句话结论。
  const conclusion = (() => {
    const risk = obj ? String(pick(obj, ['risk_summary']) || '') : ''
    if (risk) {
      const m = risk.match(/校准结论[：:]\s*\**\s*([^\n*]+)/)
      if (m && m[1] && m[1].trim()) return m[1].trim()
    }
    return allSame
      ? '资费校准通过：计费配置与方案需求保持一致，未发现叠加/互斥冲突'
      : '存在差异：计费配置与方案需求不完全一致，请核对上方比对明细'
  })()
  return {
    object: productInstance.value.code,
    status: allSame ? '通过' : '存在差异',
    time: productInstance.value.today,
    conclusion,
    rows,
  }
})

// 测试报告页签 —— 优先取自动化测试节点（test）保存内容，缺失回落静态兜底。
const testRepView = computed(() => {
  const nc = nodeContent()
  const raw = nc.byNode.test
  const obj = nc.asObj(raw)
  const offer = nc.offerOf(raw) || {}
  const pick = nc.pick
  // 环节是否已产出结果：无 test 节点内容（未完成/进行中）时不渲染“建议上线”静态报告。
  const ready = (!!(obj && Object.keys(obj).length) || !!(offer && Object.keys(offer).length)) || testSimDone.value
  const dimObj = nc.asObj(offer.dimension_summary) || nc.asObj(obj && obj.dimension_summary) || {}
  const dimNames = { ACC: '受理验证 (ACC)', BILL: '计费验证 (BILL)', CUST: '客服验证 (CUST)' }
  const dimKeys = Object.keys(dimObj).filter((k) => dimObj[k] && typeof dimObj[k] === 'object')
  const dims = dimKeys.length
    ? dimKeys.map((k) => {
        const pass = String(dimObj[k].pass ?? '0')
        const fail = String(dimObj[k].fail ?? '0')
        const total = String((Number(pass) || 0) + (Number(fail) || 0))
        return [dimNames[k] || k, total, pass, Number(fail) > 0 ? '存在失败' : '通过']
      })
    : [
        ['受理验证 (ACC)', '10', '10', '通过'],
        ['计费验证 (BILL)', '10', '10', '通过'],
        ['客服验证 (CUST)', '10', '10', '通过'],
      ]
  const casesArr = nc.asObj(offer.cases_json) || nc.asObj(obj && obj.cases_json)
  const cases = Array.isArray(casesArr) && casesArr.length
    ? casesArr.slice(0, 12).map((c) => [
        pick(c, ['case_id', 'id', 'caseId']) || '—',
        pick(c, ['case_name', 'name', 'title', 'desc']) || '—',
        pick(c, ['level', 'priority']) || 'P0',
        pick(c, ['result', 'status']) || (String(c.pass) === '0' ? '未通过' : '通过'),
      ])
    : [
        ['ACC-001', '销售品基础准入规则校验', 'P0', '通过'],
        ['ACC-004', '订购操作能力校验', 'P0', '通过'],
        ['ACC-011', '模拟订购接口预测试', 'P0', '通过'],
        ['BILL-001', '基础资费金额合法性校验', 'P0', '通过'],
        ['BILL-004', '资源扣减规则校验', 'P0', '通过'],
        ['BILL-010', '资费生效失效联动校验', 'P0', '通过'],
        ['CUST-001', '客服产品基础视图完整态', 'P0', '通过'],
        ['CUST-004', '产品资费对外说明话术校验', 'P0', '通过'],
        ['CUST-008', '对外展示信息合规校验', 'P0', '通过'],
      ]
  const total = dims.reduce((s, d) => s + (Number(d[1]) || 0), 0) || 30
  const pass = dims.reduce((s, d) => s + (Number(d[2]) || 0), 0) || 30
  const conclusion = pick(offer, ['overall_conclusion']) || pick(obj || {}, ['overall_conclusion']) || '建议上线'
  return {
    tick: nodeTick.value,
    ready,
    no: 'TEST-REP-' + productInstance.value.today + '-' + productInstance.value.code.slice(-3),
    name: productInstance.value.name,
    total,
    pass,
    rate: total ? Math.round((pass / total) * 100) + '%' : '100%',
    conclusion,
    dims,
    cases,
  }
})

// 验证报告页签 —— 优先取自动化测试节点（test）的受理凭证/场景，缺失回落静态兜底。
const verifyView = computed(() => {
  const nc = nodeContent()
  const raw = nc.byNode.test
  const offer = nc.offerOf(raw) || {}
  const pick = nc.pick
  const scenesRaw = offer.testScenes
  let scenes = []
  if (Array.isArray(scenesRaw) && scenesRaw.length) {
    scenes = scenesRaw.map((s) => (typeof s === 'string' ? [s, '通过'] : [pick(s, ['scene_name', 'name', 'sceneCode']) || '—', pick(s, ['result', 'status']) || '通过']))
  } else {
    scenes = [
      ['套餐新装 (S_O_TC)', '通过'],
      ['副卡加装 (S_ADD_CARD)', '通过'],
      ['套餐退订 (S_U_TC)', '通过'],
    ]
  }
  return {
    orderId: pick(offer, ['orderId']) || ('ORD' + productInstance.value.today.replace(/-/g, '') + Date.now().toString().slice(-6)),
    offerInstId: pick(offer, ['offerInstId']) || ('OI' + productInstance.value.today.replace(/-/g, '') + Date.now().toString().slice(-6)),
    status: '通过',
    time: productInstance.value.today,
    scenes,
    points: [
      ['P_EFF_DATE 生效时间', '通过'],
      ['P_EXP_DATE 失效时间', '通过'],
      ['P_STATUS 实例状态', '通过'],
      ['P_ORD_CNT 订购数量', '通过'],
    ],
  }
})

// 审批矩阵页签 —— 审批进度查询子流（wf_sub_08）透传 approval_nodes/wo_type/step_stage，按真实审批进度渲染；
// 无透传时回落 report 节点或静态兜底，保证既有形态不破。
const approvalView = computed(() => {
  const nc = nodeContent()
  const q = window.__WB_QUERY || {}
  // 审批结果尚未产出（既无 URL 透传的审批矩阵，也无 report 审批节点）且前端兜底未触发时，
  // 视为「审批中」——渲染 pending 卡片，由轮询/兜底自动翻转为完成。
  const hasUrlNodes = !!q.approval_nodes
  const hasReportNode = !!nc.byNode.report
  const ready = hasUrlNodes || hasReportNode || approvalSimDone.value || !approvalExpected()
  const tick = nodeTick.value
  // 工作流按真实审批矩阵计算的有效节点（JSON 数组字符串），仅含「已开始阶段」且截断到当前节点
  const anodes = (() => {
    const raw = q.approval_nodes
    if (!raw) return []
    try {
      const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
      return Array.isArray(arr) ? arr.filter((n) => n && typeof n === 'object') : []
    } catch (e) { return [] }
  })()
  // 存在透传节点：以真实进度为准，标题/状态/结论随业务阶段切换
  if (anodes.length) {
    const woType = String(q.wo_type || '')
    const isOptimize = woType === 'optimize'
    const stageIdx = String(q.step_stage || '')
    const reqNodes = anodes.filter((n) => String(n.stage || '') === '需求')
    const launchNodes = anodes.filter((n) => String(n.stage || '') === '上线')
    const allPass = anodes.every((n) => String(n.status || '').indexOf('已通过') >= 0)
    const rejected = anodes.filter((n) => {
      const s = String(n.status || '')
      return s.indexOf('驳回') >= 0 || s.indexOf('未通过') >= 0 || s.indexOf('拒绝') >= 0
    })
    const hasLaunchStarted = launchNodes.length > 0
    // 标题：优化工单 / 已进入上线阶段 / 仍处需求阶段
    const title = isOptimize ? '优化工单审批矩阵' : (hasLaunchStarted ? '上线审批矩阵' : '需求审批矩阵')
    // 状态徽标：驳回 > 审批中 > 已通过
    let status = '审批中'
    if (rejected.length) status = '已驳回'
    else if (allPass) status = '已通过'
    // 结论条与流程衔接一致（与工作流 approval_summary 口径对齐）
    let note
    if (isOptimize) {
      note = allPass ? '优化工单审批已通过 ✅，请回复【查询监控】回检'
        : (rejected.length ? ('优化工单审批未通过：节点【' + (rejected[0].node || '') + '】已驳回，意见：' + (rejected[0].opinion || ''))
          : ('优化工单审批中，当前待【' + ((anodes.find((n) => String(n.status || '').indexOf('已通过') < 0) || {}).node || '') + '】审批'))
    } else if (rejected.length) {
      note = '审批未通过：节点【' + (rejected[0].node || '') + '】已驳回，意见：' + (rejected[0].opinion || '') + '；可修改执行方案后重新发起'
    } else if (allPass && hasLaunchStarted) {
      note = '审批已通过，销售品上架完成 ✅'
    } else if (!hasLaunchStarted && reqNodes.length && !reqNodes.every((n) => String(n.status || '').indexOf('已通过') >= 0)) {
      note = '等待需求审核，通过后方可进入需求分析'
    } else if (stageIdx === '1' || (!hasLaunchStarted && reqNodes.length)) {
      note = '需求审核已通过，可进入需求分析（回复【开始配置】）；后续将进入上线审批'
    } else {
      note = '审批进行中，请等待当前节点审批'
    }
    const badgeClass = status === '已通过' ? 'badge-green' : (status === '已驳回' ? 'badge-fail' : 'badge-blue')
    const statusColor = status === '已通过' ? '#16a34a' : (status === '已驳回' ? '#dc2626' : '#2563eb')
    return {
      title,
      badge: status,
      badgeClass,
      statusColor,
      no: String(q.approval_id || '') || ('SP-' + productInstance.value.today + '-005'),
      name: productInstance.value.name,
      status,
      ready: true,
      tick,
      nodes: anodes.map((n, i) => [
        n.seq || (i + 1), n.stage || '', n.node || '', n.role || '', n.status || '', n.opinion || '', n.time || '',
      ]),
      note,
    }
  }
  // 回落：report 节点内容或静态兜底
  const raw = nc.byNode.report
  const obj = nc.asObj(raw)
  const pick = nc.pick
  const reportText = obj ? (pick(obj, ['approval_suggest', 'text', 'report']) || (typeof raw === 'string' ? raw : '')) : (typeof raw === 'string' ? raw : '')
  const allPass = obj ? String(pick(obj, ['all_pass'])) : ''
  const hasReport = !!(reportText || (obj && Object.keys(obj).length))
  return {
    title: '上线审批矩阵',
    badge: '已通过',
    badgeClass: 'badge-green',
    statusColor: '#16a34a',
    no: pick(obj || {}, ['approval_id', 'no']) || ('SP-' + productInstance.value.today + '-005'),
    name: productInstance.value.name,
    status: hasReport && allPass !== '0' ? '已通过' : (hasReport ? '审批中' : '已通过'),
    ready,
    tick,
    nodes: [
      ['1', '需求', '产品经理审核', '产品经理', '已通过', '审核通过，同意上枀', productInstance.value.now],
      ['2', '需求', '资费主管审核', '资费主管', '已通过', '审核通过，同意上枀', productInstance.value.now],
      ['3', '需求', '运营审核', '运营专员', '已通过', '审核通过，同意上枀', productInstance.value.now],
      ['4', '上线', '上线审批', 'IT支撑', '已通过', '审核通过，同意上枀', productInstance.value.now],
    ],
    note: reportText || '审批已通过，销售品上架完成 ✅（审批通过即视为销售品已自动上线，无需二次确认）、',
  }
})

// 需求提报单下方「需求审批矩阵」内嵌块 —— 只读展示需求阶段审批节点（产品经理/资费主管/运营），
// 凭 URL approval_nodes / report 节点结果自动更新状态：未发起→全部待审核；已发起→逐节点流转；
// 需求阶段全部通过（出现上线阶段节点或 report 审批通过）→ 全部已通过。不发查询词即自动呈现。
const DEMAND_APPROVAL_FALLBACK = [
  { seq: 1, stage: '需求', node: '产品经理审核', role: '产品经理' },
  { seq: 2, stage: '需求', node: '资费主管审核', role: '资费主管' },
  { seq: 3, stage: '需求', node: '运营审核', role: '运营专员' },
]
const demandApprovalView = computed(() => {
  const nc = nodeContent()
  const q = window.__WB_QUERY || {}
  const tick = nodeTick.value
  const s = String(q.stage || '')
  // URL 透传的真实审批矩阵（wf_sub_08），仅取需求阶段节点
  let anodes = []
  try {
    const raw = q.approval_nodes
    const arr = raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : []
    if (Array.isArray(arr)) anodes = arr.filter((n) => n && typeof n === 'object')
  } catch (e) { anodes = [] }
  const isPassed = (n) => String(n && n.status || '').indexOf('已通过') >= 0
  const isRejected = (n) => {
    const st = String(n && n.status || '')
    return st.indexOf('驳回') >= 0 || st.indexOf('未通过') >= 0 || st.indexOf('拒绝') >= 0
  }
  const reportNode = nc.byNode.report
  // 已发起标记：工作流在「已发起需求审批」后于 url 追加 demand_approval=1；未发起时无此标记。
  const demoApproval = String(q.demand_approval || '') === '1'
  // 轮询后端审批矩阵（需求阶段要求节点是否全部通过），驱动固定虚拟节点流转
  const liveMatrix = demandApprovalMatrix.value
  const liveReqNodes = Array.isArray(liveMatrix) ? liveMatrix.filter((n) => String(n.stage || '') === 'requirement') : []
  const liveReqPassed = liveReqNodes.length > 0 && liveReqNodes.every(isPassed)
  const reqNodes = anodes.filter((n) => String(n.stage || '') === '需求')
  const launchNodes = anodes.filter((n) => String(n.stage || '') === '上线')
  const hasLaunch = launchNodes.some((n) => {
    const st = String(n.status || '')
    return st !== '' && st.indexOf('待审核') < 0 && st.indexOf('未开始') < 0
  })
  // 是否已发起需求审批：出现需求/上线阶段节点、report 审批节点，或处于需求/优化审批类环节，或 URL 带已发起标记
  const started = reqNodes.length > 0 || hasLaunch || !!reportNode || ['11', '12'].indexOf(s) >= 0 || demoApproval
  const reqAllPass = reqNodes.length > 0 && reqNodes.every(isPassed)
  const rejected = reqNodes.filter(isRejected)[0] || anodes.filter(isRejected)[0]
  // 需求阶段全部通过：上线阶段已开启，或真实节点全通过，或轮询到需求审批已通过
  const allPass = hasLaunch || reqAllPass || liveReqPassed
  // 组装矩阵行：优先真实节点（URL 透传），否则用固定 3 个虚拟节点 + 轮询流转状态
  const rows = (() => {
    if (reqNodes.length) {
      return reqNodes.map((n, i) => [n.seq || (i + 1), n.stage || '需求', n.node || '', n.role || '', n.status || '', n.opinion || '', n.time || ''])
    }
    // 已发起且未全部通过：首个节点审批中、其余待审核
    const firstOpen = demoApproval && !allPass ? 0 : -1
    return DEMAND_APPROVAL_FALLBACK.map((n, i) => {
      let status = '待审核'
      let opinion = ''
      if (allPass) { status = '已通过'; opinion = '审核通过，同意上架' }
      else if (firstOpen >= 0) { status = i === firstOpen ? '审批中' : (i < firstOpen ? '已通过' : '待审核') }
      return [n.seq, n.stage, n.node, n.role, status, opinion, (status === '待审核' ? '' : productInstance.value.now)]
    })
  })()
  let status = '待发起'
  let badgeClass = 'badge-blue'
  let statusColor = '#2563eb'
  let note = '需求工单尚未发起审批；回复【发起需求审批】后进入审批流转。'
  if (allPass) {
    status = '已通过'; badgeClass = 'badge-green'; statusColor = '#16a34a'
    note = '需求审核已通过，可进入需求分析（回复【开始配置】）；后续将进入上线审批。'
  } else if (rejected) {
    status = '已驳回'; badgeClass = 'badge-fail'; statusColor = '#dc2626'
    note = '需求审核未通过：节点【' + (rejected.node || '') + '】已驳回，意见：' + (rejected.opinion || '') + '；可修改后重新发起。'
  } else if (started) {
    status = '审批中'; badgeClass = 'badge-amber'; statusColor = '#d97706'
    const waiting = reqNodes.find((n) => !isPassed(n) && !isRejected(n)) || reqNodes[reqNodes.length - 1]
      || DEMAND_APPROVAL_FALLBACK[0]
    note = waiting ? ('需求审批中，当前待【' + (waiting.node || '') + '】审批。') : '需求审批流转中，请等待当前节点审批。'
  }
  return {
    visible: true,
    tick,
    no: String(q.approval_id || '') || ('XQSP-' + productInstance.value.today + '-005'),
    product: productInstance.value.name,
    status,
    badgeClass,
    statusColor,
    rows,
    note,
  }
})

// 运营视图页签概要 —— 名称/编码随实例联动（完整 ECharts 数据面板在独立模块，保留演示数据，
const monitorView = computed(() => ({
  single: {
    name: productInstance.value.name,
    status: '在售一',
    cards: [
      ['累计订购量', '3,842', '户'],
      ['本月新增', '1,206', '户'],
      ['本月退订', '86', '户'],
      ['净增', '1,120', '户'],
      ['在售收入', '76.4', '万元'],
      ['资源利用率', '68%', '—'],
    ],
    warn: '当前无异常告警；通用流量资源利用率接近阈值，建议关注后续趋势、',
  },
  total: {
    cards: [
      ['在售销售品', '48', '一'],
      ['本月新增订购', '2.4一', '户'],
      ['总在售收入', '1,820', '万元'],
      ['整体健康度', '良好', '—'],
    ],
    risks: [
      ['流量资源利用率偏高', '一', '多款销售品内通用流量包额发放较集一'],
      ['部分老套餐退订上单', '使', '需关注同"G-A等新套餐的迁移引对'],
    ],
  },
}))

// ========== 产销品到期预警（静态示例视图） ==========
// 展示全部商品的生效/到期日期与剩余天数，按到期状态区分「已到期 / 临期(≤30天)」，
// 仅显示「临期30天以内」的商品，并按剩余天数升序排列（最紧迫的排在最前），
// 供监控运维页签内直观掌握各产销品的有效期风险，便于及时续约或下线评估。
const expiryView = computed(() => {
  const rows = [
    { name: '5G-A套餐单品199元', code: '900102308', type: '单品', effDate: '2024-06-01', expDate: '2026-06-30', remain: 18, status: 'due' },
    { name: '畅享59元青春版', code: '900102021', type: '单品', effDate: '2024-03-01', expDate: '2026-09-30', remain: 6, status: 'due' },
    { name: '畅享79元畅享版', code: '900102105', type: '单品', effDate: '2023-11-01', expDate: '2026-09-20', remain: 3, status: 'due' },
    { name: '5G-A套餐单品299元', code: '900102310', type: '单品', effDate: '2025-01-01', expDate: '2025-09-30', remain: 0, status: 'expired' },
    { name: '校园青春卡39元', code: 'XY-0023', type: '校园', effDate: '2025-09-01', expDate: '2026-09-30', remain: 47, status: 'due' },
    { name: '爱家畅享版199元', code: '900305102', type: '融合', effDate: '2025-01-01', expDate: '2026-01-01', remain: 0, status: 'expired' },
    { name: '畅享5G-A精选卡', code: '900108881', type: '单品', effDate: '2025-07-01', expDate: '2026-10-05', remain: 12, status: 'due' },
    { name: '宽带融合套餐299元', code: '900203116', type: '融合', effDate: '2025-05-01', expDate: '2028-05-31', remain: 590, status: 'ok' },
    { name: '畅享129元尊享版', code: '900102422', type: '单品', effDate: '2025-08-01', expDate: '2026-08-31', remain: 327, status: 'ok' },
  ].map((r) => {
    const label = r.status === 'expired' ? '已到期' : '临期'
    const cls = r.status === 'expired' ? 'badge-red' : 'badge-amber'
    const bar = r.status === 'expired' ? 0 : Math.min(100, Math.round(((r.remain || 0) / 30) * 100))
    const barColor = r.status === 'expired' ? '#dc2626' : '#d97706'
    return { ...r, label, cls, bar, barColor }
  })
  // 仅保留「临期30天以内」的商品（已到期 remain=0 也算），并按剩余天数升序排列
  const filtered = rows
    .filter((r) => r.status !== 'ok' && r.remain <= 30)
    .slice()
    .sort((a, b) => a.remain - b.remain)
  const expiredCount = filtered.filter((r) => r.status === 'expired').length
  const dueCount = filtered.filter((r) => r.status === 'due').length
  return {
    rows: filtered,
    total: filtered.length,
    expiredCount,
    dueCount,
    okCount: rows.filter((r) => r.status === 'ok').length,
    warn: filtered.length > 0
      ? `共有 ${expiredCount} 款已到期、${dueCount} 款临期（30天内）商品，建议及时办理续约/下线评估，避免影响在售与用户续费`
      : '现无临期30天以内的商品，暂无到期预警',
  }
})

const __returned__ = { emit, props, products, activeProductId, copyMode, copyWorkOrderId, isArchive, dialogueMode, dynamicPreview, batchMode, dialogueStatus, panelWidth, resizing, startResize, productMenuOpen, toggleProductMenu, onSelectProduct, ppStripRef, visibleProducts, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH, baseTabs, tabs, activeTab, showTestReport, showApproval, showMonitor, productInfo, currentStatus, footerSubmittedText, config, formData, modifiedFields, archiveTestReport, parsedFilled, aiFilled, initFormData, emptyValue, fieldTag, baseFields, resourceSections, marketingSections, salesSections, expandedSections, toggleSection, cloneVal, prevValues, snapshotPrevValues, toDisplay, emitPanelAction, fieldDebounceTimers, scheduleRecordFieldChange, recordFieldChange, markModified, checkboxValue, toggleCheckbox, tableRows, addTableRow, deleteTableRow, labelMap, ruleTypeMap, limitTypeMap, generatePreLimitTip, getDynamicOptions, onTableCellChange, onDynamicOptionChange, onSelectCellChange, onInputCellChange, previewSections, phoneTab, phoneTime, phoneNavTitle, previewFee, previewName, previewFlowUnit, previewFlowAmount, previewVoiceAmount, previewSmsAmount, previewFlowOvercharge, previewVoiceOvercharge, previewSmsOvercharge, previewRights, findField, optionLabel, equityList, freeFillItems, smsItems, saveDraft, auditVisible, auditLoading, auditProgressText, auditResults, submitted, copyStatus, onCopyFromArchive, collectFormValues, showAudit, onSubmit, applyFieldUpdate, productInstance, demandView, schemeView, auditView, calibrateView, testRepView, verifyView, approvalView, monitorView, ref, reactive, computed, onUnmounted, watch, get ElMessage() { return ElMessage }, get launchProductConfig() { return launchProductConfig }, get testReport() { return testReport }, get campusProductConfig() { return campusProductConfig } }
Object.assign(__returned__, { demand: demandView, scheme: schemeView, audit: auditView, calibrate: calibrateView, testRep: testRepView, verify: verifyView, approval: approvalView, demandApproval: demandApprovalView, monitor: monitorView, monitorTab: ref('single'), expiryView })
return __returned__
}

}
  // Attach the SFC template (runtime+compiler build compiles it at runtime)
  ConfigWorkbench.template = "\r\n  <div class=\"config-panel\" :style=\"{ width: panelWidth + 'px' }\">\r\n    <!-- 拖拽调宽 -->\r\n    <div\r\n      class=\"config-panel-resizer\"\r\n      @mousedown=\"startResize\"\r\n      @touchstart.passive=\"startResize\"\r\n    >\r\n      <div class=\"resizer-line\"></div>\r\n    </div>\r\n\r\n    <!-- 顶部标题 -->\r\n    <div class=\"config-header\">\r\n      <div class=\"config-header-left\">\r\n        <svg class=\"config-header-icon\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M12 2L2 7l10 5 10-5-10-5z\"/>\r\n          <path d=\"M2 17l10 5 10-5\"/>\r\n          <path d=\"M2 12l10 5 10-5\"/>\r\n        </svg>\r\n        <div class=\"config-header-text\">\r\n          <div class=\"pp-strip\" ref=\"ppStripRef\">\r\n            <button\r\n              v-for=\"p in visibleProducts\"\r\n              :key=\"p.id\"\r\n              class=\"pp-chip pp-chip-slot\"\r\n              :class=\"{ active: p.id === activeProductId }\"\r\n              @click.stop=\"onSelectProduct(p)\"\r\n              :title=\"p.name\"\r\n            >\r\n              <span class=\"pp-chip-name\">{{ p.name }}</span>\r\n              <span v-if=\"p.quantity !== undefined && p.quantity !== null && p.quantity !== ''\" class=\"pp-chip-qty\">x{{ p.quantity }}</span>\r\n            </button>\r\n          </div>\r\n        </div>\r\n      </div>\r\n      <div class=\"config-header-actions\">\r\n        <button\r\n          class=\"pp-list-btn\"\r\n          :class=\"{ active: productMenuOpen }\"\r\n          @click.stop=\"toggleProductMenu\"\r\n          title=\"查看商品列表\"\r\n        >\r\n          <svg width=\"15\" height=\"15\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><line x1=\"8\" y1=\"6\" x2=\"21\" y2=\"6\"/><line x1=\"8\" y1=\"12\" x2=\"21\" y2=\"12\"/><line x1=\"8\" y1=\"18\" x2=\"21\" y2=\"18\"/><line x1=\"3\" y1=\"6\" x2=\"3.01\" y2=\"6\"/><line x1=\"3\" y1=\"12\" x2=\"3.01\" y2=\"12\"/><line x1=\"3\" y1=\"18\" x2=\"3.01\" y2=\"18\"/></svg>\r\n          <span class=\"pp-chip-name\">查看商品列表</span>\r\n          <span class=\"pp-count\">{{ products.length }}</span>\r\n        </button>\r\n        <div v-if=\"productMenuOpen\" class=\"product-picker product-picker-menu\">\r\n          <div class=\"pp-menu-head\">全部商品<span class=\"pp-menu-count\">元'{{ products.length }} 户</span></div>\r\n          <div class=\"pp-menu-body\">\r\n            <div v-for=\"p in products\" :key=\"p.id\" class=\"pp-menu-item\" :class=\"{ active: p.id === activeProductId }\" @click.stop=\"onSelectProduct(p)\" :title=\"p.name\">\r\n              <div class=\"pp-menu-item-top\">\r\n                <span class=\"pp-menu-name\">{{ p.name }}</span>\r\n                <span class=\"pp-menu-status\">{{ p.status }}</span>\r\n              </div>\r\n              <div class=\"pp-menu-item-sub\">\r\n                <span class=\"pp-menu-code\">{{ p.code }}</span>\r\n                <span class=\"pp-menu-fee\" v-if=\"p.fee\">{{ p.fee }}元/月</span>\r\n                <svg v-if=\"p.id === activeProductId\" class=\"pp-menu-check\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\"><polyline points=\"20 6 9 17 4 12\"/></svg>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n        <button v-if=\"isArchive && !copyMode\" class=\"config-copy-btn\" @click=\"onCopyFromArchive\" title=\"基于该存量商品复制新套餐\">\r\n          <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n            <rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\"/>\r\n            <path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/>\r\n          </svg>\r\n          <span>复制</span>\r\n        </button>\r\n      </div>\r\n    </div>\r\n\r\n    <!-- Tab 导航 -->\r\n    <div class=\"config-tabs\">\r\n      <button\r\n        v-for=\"tab in tabs\"\r\n        :key=\"tab.key\"\r\n        class=\"config-tab\"\r\n        :class=\"{ active: activeTab === tab.key }\"\r\n        @click=\"activeTab = tab.key\"\r\n      >\r\n        <svg v-if=\"tab.key === 'base'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/>\r\n          <polyline points=\"14 2 14 8 20 8\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'resource'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z\"/>\r\n          <polyline points=\"3.27 6.96 12 12.01 20.73 6.96\"/>\r\n          <line x1=\"12\" y1=\"22.08\" x2=\"12\" y2=\"12\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'marketing'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <line x1=\"12\" y1=\"1\" x2=\"12\" y2=\"23\"/>\r\n          <path d=\"M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'sales'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\"/>\r\n          <rect x=\"8\" y=\"2\" width=\"8\" height=\"4\" rx=\"1\"/>\r\n          <path d=\"M9 14l2 2 4-4\"/>\r\n        </svg>\r\n        <svg v-else width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z\"/>\r\n          <path d=\"M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z\"/>\r\n        </svg>\r\n        {{ tab.label }}\r\n      </button>\r\n    </div>\r\n\r\n    <!-- Tab 内容 -->\r\n    <div class=\"config-body\">\r\n      <!-- 基础信息 Tab -->\r\n            <!-- 配置信息 Tab（合并：基础信息/月租/语音/流量/短信/前项限制/后项限制，'-->\n      <div v-show=\"activeTab === 'config'\" class=\"tab-content\">\n<div class=\"form-section\">\r\n          <div class=\"section-header open\">\r\n            <span class=\"section-title\">基础信息</span>\r\n          </div>\r\n          <div class=\"section-content base-info-grid\">\r\n            <div class=\"form-row base-field-row\" v-for=\"field in baseFields\" :key=\"field.key\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <select v-if=\"field.options\" v-model=\"formData[field.key]\" class=\"form-input form-select\" :class=\"{ modified: modifiedFields[field.key] }\" :disabled=\"submitted\" @change=\"markModified(field.key)\">\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in marketingSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <div class=\"form-row\" v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <div v-if=\"field.unit\" class=\"input-with-unit\">\r\n                  <input\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                  <span class=\"input-unit\">{{ field.unit }}</span>\r\n                </div>\r\n                <select\r\n                  v-else-if=\"field.type === 'select'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-select\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @change=\"markModified(field.key)\"\r\n                >\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <textarea\r\n                  v-else-if=\"field.type === 'textarea'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-textarea\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  rows=\"3\"\r\n                  @input=\"markModified(field.key)\"\r\n                ></textarea>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in resourceSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <!-- table 类型：表标'+ 添加按钮 -->\r\n            <template v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <div v-if=\"field.type === 'table'\" class=\"table-field\">\r\n                <div class=\"table-field-header\">\r\n                  <label class=\"form-label table-field-label\">\r\n                    <span class=\"label-text\">{{ field.label }}</span>\r\n                    <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                    <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n                  </label>\r\n                  <button class=\"table-add-btn\" :disabled=\"submitted\" @click=\"addTableRow(field.key, field.columns)\">\r\n                    <svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n                      <line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/>\r\n                      <line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/>\r\n                    </svg>\r\n                    添加\r\n                  </button>\r\n                </div>\r\n                <div class=\"limit-table-wrap\">\r\n                  <table class=\"limit-table\">\r\n                    <thead>\r\n                      <tr>\r\n                        <th v-for=\"col in field.columns\" :key=\"col.key\">{{ col.label }}</th>\r\n                        <th class=\"col-action\">操作</th>\r\n                      </tr>\r\n                    </thead>\r\n                    <tbody>\r\n                      <tr v-for=\"(row, rowIdx) in tableRows(field.key)\" :key=\"rowIdx\">\r\n                        <td v-for=\"col in field.columns\" :key=\"col.key\">\r\n                          <div v-if=\"col.type === 'auto'\" class=\"table-cell-auto\">\r\n                            {{ generatePreLimitTip(field.key, rowIdx) }}\r\n                          </div>\r\n                          <select\r\n                            v-else-if=\"col.type === 'select' && col.dynamicOptions\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onDynamicOptionChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in getDynamicOptions(col, row)\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <select\r\n                            v-else-if=\"col.type === 'select'\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onSelectCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in col.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <input\r\n                            v-else\r\n                            type=\"text\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input\"\r\n                            :placeholder=\"col.placeholder || ''\"\r\n                            :disabled=\"submitted\"\r\n                            @input=\"onInputCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          />\r\n                        </td>\r\n                        <td class=\"col-action\">\r\n                          <button class=\"table-del-btn\" :disabled=\"submitted\" @click=\"deleteTableRow(field.key, rowIdx)\" title=\"删除\">\r\n                            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                              <polyline points=\"3 6 5 6 21 6\"/>\r\n                              <path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/>\r\n                            </svg>\r\n                          </button>\r\n                        </td>\r\n                      </tr>\r\n                      <tr v-if=\"tableRows(field.key).length === 0\">\r\n                        <td :colspan=\"field.columns.length + 1\" class=\"table-empty\">暂无数据，点击\"添加\"按钮新增</td>\r\n                      </tr>\r\n                    </tbody>\r\n                  </table>\r\n                </div>\r\n              </div>\r\n              <!-- 常规字段 -->\r\n              <div v-else class=\"form-row\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <div v-if=\"field.unit\" class=\"input-with-unit\">\r\n                  <input\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                  <span class=\"input-unit\">{{ field.unit }}</span>\r\n                </div>\r\n                <select\r\n                  v-else-if=\"field.type === 'select'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-select\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @change=\"markModified(field.key)\"\r\n                >\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <textarea\r\n                  v-else-if=\"field.type === 'textarea'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-textarea\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  rows=\"3\"\r\n                  @input=\"markModified(field.key)\"\r\n                ></textarea>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n              </div>\r\n            </template>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in salesSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <!-- 遍历 section 内字 -->\r\n            <template v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <!-- table 类型：表￀'+ 添加按钮 -->\r\n              <div v-if=\"field.type === 'table'\" class=\"table-field\">\r\n                <div class=\"table-field-header\">\r\n                  <label class=\"form-label table-field-label\">\r\n                    <span class=\"label-text\">{{ field.label }}</span>\r\n                    <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                    <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n                  </label>\r\n                  <button class=\"table-add-btn\" :disabled=\"submitted\" @click=\"addTableRow(field.key, field.columns)\">\r\n                    <svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n                      <line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/>\r\n                      <line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/>\r\n                    </svg>\r\n                    添加\r\n                  </button>\r\n                </div>\r\n                <div class=\"limit-table-wrap\">\r\n                  <table class=\"limit-table\">\r\n                    <thead>\r\n                      <tr>\r\n                        <th v-for=\"col in field.columns\" :key=\"col.key\">{{ col.label }}</th>\r\n                        <th class=\"col-action\">操作</th>\r\n                      </tr>\r\n                    </thead>\r\n                    <tbody>\r\n                      <tr v-for=\"(row, rowIdx) in tableRows(field.key)\" :key=\"rowIdx\">\r\n                        <td v-for=\"col in field.columns\" :key=\"col.key\">\r\n                          <!-- auto 类型：只读，根据同行其他单元格自动生成提示语 -->\r\n                          <div v-if=\"col.type === 'auto'\" class=\"table-cell-auto\">\r\n                            {{ generatePreLimitTip(field.key, rowIdx) }}\r\n                          </div>\r\n                          <!-- dynamicOptions 类型：选项依赖同行另一个字段的 -->\r\n                          <select\r\n                            v-else-if=\"col.type === 'select' && col.dynamicOptions\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onDynamicOptionChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in getDynamicOptions(col, row)\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <!-- 普通select -->\r\n                          <select\r\n                            v-else-if=\"col.type === 'select'\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onSelectCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in col.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <!-- 普通input -->\r\n                          <input\r\n                            v-else\r\n                            type=\"text\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input\"\r\n                            :placeholder=\"col.placeholder || ''\"\r\n                            :disabled=\"submitted\"\r\n                            @input=\"onInputCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          />\r\n                        </td>\r\n                        <td class=\"col-action\">\r\n                          <button class=\"table-del-btn\" :disabled=\"submitted\" @click=\"deleteTableRow(field.key, rowIdx)\" title=\"删除\">\r\n                            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                              <polyline points=\"3 6 5 6 21 6\"/>\r\n                              <path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/>\r\n                            </svg>\r\n                          </button>\r\n                        </td>\r\n                      </tr>\r\n                      <tr v-if=\"tableRows(field.key).length === 0\">\r\n                        <td :colspan=\"field.columns.length + 1\" class=\"table-empty\">暂无数据，点击\"添加\"按钮新增</td>\r\n                      </tr>\r\n                    </tbody>\r\n                  </table>\r\n                </div>\r\n              </div>\r\n              <!-- 常规字段：checkbox-group / select / input -->\r\n              <div v-else class=\"form-row\">\r\n                <label class=\"form-label\">\r\n                  <span v-if=\"field.required\" class=\"required\">*</span>\r\n                  <span class=\"label-text\">{{ field.label }}</span>\r\n                  <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI推荐</span>\r\n                  <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">复用</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '根据用户需求解析得出'\">已解析</span>\r\n                </label>\r\n                <div class=\"form-control\">\r\n                  <!-- checkbox-group: 多选框 -->\r\n                  <div v-if=\"field.type === 'checkbox-group'\" class=\"checkbox-group\">\r\n                    <label\r\n                      v-for=\"opt in field.options\"\r\n                      :key=\"opt.value\"\r\n                      class=\"checkbox-item\"\r\n                      :class=\"{ checked: checkboxValue(field.key, opt.value) }\"\r\n                    >\r\n                      <input\r\n                        type=\"checkbox\"\r\n                        :value=\"opt.value\"\r\n                        :checked=\"checkboxValue(field.key, opt.value)\"\r\n                        :disabled=\"submitted\"\r\n                        @change=\"toggleCheckbox(field.key, opt.value)\"\r\n                      />\r\n                      <span>{{ opt.label }}</span>\r\n                    </label>\r\n                  </div>\r\n                  <div v-else-if=\"field.unit\" class=\"input-with-unit\">\r\n                    <input\r\n                      :type=\"field.type || 'text'\"\r\n                      v-model=\"formData[field.key]\"\r\n                      class=\"form-input\"\r\n                      :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                      :disabled=\"submitted\"\r\n                      @input=\"markModified(field.key)\"\r\n                    />\r\n                    <span class=\"input-unit\">{{ field.unit }}</span>\r\n                  </div>\r\n                  <select\r\n                    v-else-if=\"field.type === 'select'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input form-select\"\r\n                    :class=\"{ modified: modifiedFields[field.key] }\"\r\n                    :disabled=\"submitted\"\r\n                    @change=\"markModified(field.key)\"\r\n                  >\r\n                    <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                  </select>\r\n                  <textarea\r\n                    v-else-if=\"field.type === 'textarea'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input form-textarea\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    rows=\"3\"\r\n                    @input=\"markModified(field.key)\"\r\n                  ></textarea>\r\n                  <input\r\n                    v-else\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                </div>\r\n              </div>\r\n            </template>\r\n          </div>\r\n        </div>\n      </div>\n<!-- 需求提报单 Tab -->\n      <div v-show=\"activeTab === 'demand'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\n              <span>需求提报单</span>\n            </div>\n            <span class=\"rv-badge badge-blue\">{{ demand.name }}</span>\n          </div>\n          <div class=\"rv-grid grid-2\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">提报编号</span><span class=\"rv-v\">{{ demand.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">提报于</span><span class=\"rv-v\">{{ demand.applicant }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">提报部门</span><span class=\"rv-v\">{{ demand.department }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">提报日期</span><span class=\"rv-v\">{{ demand.date }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">目标客户美</span><span class=\"rv-v\">{{ demand.target }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">期望生效日期</span><span class=\"rv-v\">{{ demand.effective }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">业务需求概过</div>\n            <div class=\"rv-p\">{{ demand.require }}</div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">销售渠道</div>\n            <div class=\"rv-tags\"><span v-for=\"c in demand.channels\" :key=\"c\" class=\"rv-tag\">{{ c }}</span></div>\n          </div>\n        </div>\n\n        <!-- 需求审批矩阵（内嵌只读，凭 URL approval_nodes/report 节点自动更新状态，免发查询词） -->\n        <div class=\"rv-card\" style=\"margin-top:12px;\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/></svg>\n              <span>需求审批矩阵</span>\n            </div>\n            <span class=\"rv-badge\" :class=\"demandApproval.badgeClass\">{{ demandApproval.status }}</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">审批单号</span><span class=\"rv-v mono\">{{ demandApproval.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">关联需求</span><span class=\"rv-v\">{{ demandApproval.product }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">状态</span><span class=\"rv-v\" :style=\"{color:demandApproval.statusColor,fontWeight:600}\">{{ demandApproval.status }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">审批流转矩阵</div>\n            <table class=\"rv-table\">\n              <thead><tr><th style=\"width:60px\">序号</th><th style=\"width:70px\">业务阶段</th><th>审批节点</th><th>审批角色</th><th style=\"width:90px\">状态</th><th>审批意见</th><th style=\"width:170px\">更新时间</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(n,i) in demandApproval.rows\" :key=\"i\">\n                  <td>{{ i + 1 }}</td><td>{{ n[1] }}</td><td>{{ n[2] }}</td><td>{{ n[3] }}</td>\n                  <td><span class=\"rv-badge\" :class=\"String(n[4]).indexOf('已通过') >= 0 ? 'badge-green' : (String(n[4]).indexOf('驳回') >= 0 || String(n[4]).indexOf('未通过') >= 0 ? 'badge-fail' : (String(n[4]).indexOf('审批中') >= 0 ? 'badge-amber' : 'badge-blue'))\">{{ n[4] }}</span></td>\n                  <td>{{ n[5] }}</td>\n                  <td>{{ n[6] }}</td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl\" :class=\"demandApproval.status === '已通过' ? 'concl-pass' : ''\">\n            <span>{{ demandApproval.note }}</span>\n          </div>\n        </div>\n      </div>\n\n      <!-- 加载方案 Tab -->\n      <div v-show=\"activeTab === 'scheme'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\n              <span>加载方案</span>\n            </div>\n            <span class=\"rv-badge badge-green\">{{ scheme.status }}</span>\n          </div>\n          <div class=\"rv-grid grid-2\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">方案编号</span><span class=\"rv-v\">{{ scheme.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">关联需求单</span><span class=\"rv-v\">{{ scheme.requireNo }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">方案名称</span><span class=\"rv-v\">{{ scheme.name }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">方案版本 / 编制</span><span class=\"rv-v\">{{ scheme.version }} · {{ scheme.author }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">方案要点</div>\n            <div class=\"rv-summary\">\n              <div v-for=\"(row,i) in scheme.summary\" :key=\"i\" class=\"rv-sum-row\">\n                <span class=\"rv-sum-k\">{{ row[0] }}</span>\n                <span class=\"rv-sum-v\">{{ row[1] }}</span>\n              </div>\n            </div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">加载配置模块</div>\n            <div class=\"rv-tags\"><span v-for=\"it in scheme.items\" :key=\"it\" class=\"rv-tag\">{{ it }}</span></div>\n          </div>\n        </div>\n      </div>\n\n      <!-- 稽核报告 Tab -->\n      <div v-show=\"activeTab === 'audit'\" class=\"tab-content rv-wrap\">\n        <div v-if=\"!audit.ready\" class=\"rv-card rv-pending\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M9 12l2 2 4-4\"/><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>\n              <span>配置规格稽核报告</span>\n            </div>\n            <span class=\"rv-badge badge-amber\">稽核进行中</span>\n          </div>\n          <div class=\"rv-pending-body\">\n            <div class=\"rv-pending-spinner\"></div>\n            <div class=\"rv-pending-title\">规格稽核进行中</div>\n            <div class=\"rv-pending-desc\">正在按配置规格对销售品进行合规校验，稽核完成后将自动展示检查项与结论。</div>\n          </div>\n        </div>\n        <div v-else class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M9 12l2 2 4-4\"/><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>\n              <span>配置规格稽核报告</span>\n            </div>\n            <span class=\"rv-badge badge-green\">稽核通过</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">稽核对象</span><span class=\"rv-v mono\">{{ audit.object }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">稽核时间</span><span class=\"rv-v\">{{ audit.time }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">稽核结果</span><span class=\"rv-v\" style=\"color:#16a34a;font-weight:600\">{{ audit.status }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">稽核检查项</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>检查项</th><th style=\"width:120px\">结果</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(c,i) in audit.checks\" :key=\"i\">\n                  <td>{{ c[0] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ c[1] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl concl-pass\">\n            <span class=\"rv-cc-icon\">，</span>\n            <span>{{ audit.conclusion }}</span>\n          </div>\n        </div>\n      </div>\n\n      <!-- 校准报告 Tab -->\n      <div v-show=\"activeTab === 'calibrate'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M12 6v6l4 2\"/></svg>\n              <span>资费校准报告</span>\n            </div>\n            <span class=\"rv-badge badge-green\">校准通过</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">校准对象</span><span class=\"rv-v mono\">{{ calibrate.object }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">校准时间</span><span class=\"rv-v\">{{ calibrate.time }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">比对明细（套餐需求'vs 计费配置，</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>项目</th><th>套餐描述（需求）</th><th>计费配置描述（系统）</th><th style=\"width:100px\">比对结果</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(r,i) in calibrate.rows\" :key=\"i\">\n                  <td>{{ r[0] }}</td><td>{{ r[1] }}</td><td>{{ r[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ r[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl concl-pass\">\n            <span class=\"rv-cc-icon\">，</span>\n            <span>{{ calibrate.conclusion }}</span>\n          </div>\n        </div>\n      </div>\n\n      <!-- 测试报告 Tab -->\n      <div v-show=\"activeTab === 'testrep'\" class=\"tab-content rv-wrap\">\n        <div v-if=\"!testRep.ready\" class=\"rv-card rv-pending\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polygon points=\"12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26\"/></svg>\n              <span>销售品自动化测试报告</span>\n            </div>\n            <span class=\"rv-badge badge-amber\">测试进行中</span>\n          </div>\n          <div class=\"rv-pending-body\">\n            <div class=\"rv-pending-spinner\"></div>\n            <div class=\"rv-pending-title\">自动化测试进行中</div>\n            <div class=\"rv-pending-desc\">正在执行受理、计费、客服三维度用例校验，测试完成后将自动展示用例明细与整体结论。</div>\n          </div>\n        </div>\n        <div v-else class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polygon points=\"12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26\"/></svg>\n              <span>销售品自动化测试报告</span>\n            </div>\n            <span class=\"rv-badge badge-blue\">建议上线</span>\n          </div>\n          <div class=\"rv-metrics\">\n            <div class=\"rv-metric\"><span class=\"rv-m-num\">{{ testRep.total }}</span><span class=\"rv-m-tt\">总用例数</span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#16a34a\">{{ testRep.pass }}</span><span class=\"rv-m-tt\">通过用例</span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#16a34a\">{{ testRep.rate }}</span><span class=\"rv-m-tt\">通过率</span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#2563eb\">{{ testRep.conclusion }}</span><span class=\"rv-m-tt\">整体结论</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">三大验证维度</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>验证维度</th><th>用例数</th><th>通过</th><th style=\"width:100px\">结果</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(d,i) in testRep.dims\" :key=\"i\">\n                  <td>{{ d[0] }}</td><td>{{ d[1] }}</td><td>{{ d[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ d[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">重点用例（节选）</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>用例ID</th><th>用例名称</th><th style=\"width:80px\">等级</th><th style=\"width:100px\">结果</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(c,i) in testRep.cases\" :key=\"i\">\n                  <td>{{ c[0] }}</td><td>{{ c[1] }}</td><td>{{ c[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ c[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">受理验证报告（并入自动化测试，</div>\n            <div class=\"rv-strip\" style=\"margin-bottom:10px\">\n              <div class=\"rv-cell\"><span class=\"rv-k\">受理凭证 orderId</span><span class=\"rv-v mono\">{{ verify.orderId }}</span></div>\n              <div class=\"rv-cell\"><span class=\"rv-k\">offerInstId</span><span class=\"rv-v mono\">{{ verify.offerInstId }}</span></div>\n              <div class=\"rv-cell\"><span class=\"rv-k\">验证时间</span><span class=\"rv-v\">{{ verify.time }}</span></div>\n            </div>\n            <table class=\"rv-table\">\n              <thead><tr><th>受理场景</th><th style=\"width:120px\">结果</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(s,i) in verify.scenes\" :key=\"i\">\n                  <td>{{ s[0] }}</td><td><span class=\"rv-badge badge-green\">{{ s[1] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n            <div class=\"rv-k\" style=\"margin-top:12px\">关键测点</div>\n            <div class=\"rv-tags\"><span v-for=\"(p,i) in verify.points\" :key=\"i\" class=\"rv-tag tag-ok\">，'{{ p[0] }} · {{ p[1] }}</span></div>\n          </div>\n        </div>\n      </div>\n\n      <!-- 审批矩阵 Tab -->\n      <div v-show=\"activeTab === 'approval'\" class=\"tab-content rv-wrap\">\n        <div v-if=\"!approval.ready\" class=\"rv-card rv-pending\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/></svg>\n              <span>审批矩阵</span>\n            </div>\n            <span class=\"rv-badge badge-amber\">审批进行中</span>\n          </div>\n          <div class=\"rv-pending-body\">\n            <div class=\"rv-pending-spinner\"></div>\n            <div class=\"rv-pending-title\">审批流转进行中</div>\n            <div class=\"rv-pending-desc\">正在按审批矩阵逐节点流转，审批完成后将自动展示流转明细与结论。</div>\n          </div>\n        </div>\n        <div v-else class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/></svg>\n              <span>{{ approval.title }}</span>\n            </div>\n            <span class=\"rv-badge\" :class=\"approval.badgeClass\">{{ approval.badge }}</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">审批单号</span><span class=\"rv-v mono\">{{ approval.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">关联销售品</span><span class=\"rv-v\">{{ approval.name }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">状态</span><span class=\"rv-v\" :style=\"{color:approval.statusColor,fontWeight:600}\">{{ approval.status }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">审批流转矩阵</div>\n            <table class=\"rv-table\">\n              <thead><tr><th style=\"width:60px\">序号</th><th style=\"width:70px\">业务阶段</th><th>审批节点</th><th>审批角色</th><th style=\"width:90px\">状态</th><th>审批意见</th><th style=\"width:170px\">更新时间</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(n,i) in approval.nodes\" :key=\"i\">\n                  <td>{{ i + 1 }}</td><td>{{ n[1] }}</td><td>{{ n[2] }}</td><td>{{ n[3] }}</td>\n                  <td><span class=\"rv-badge\" :class=\"String(n[4]).indexOf('已通过') >= 0 ? 'badge-green' : (String(n[4]).indexOf('驳回') >= 0 || String(n[4]).indexOf('未通过') >= 0 ? 'badge-fail' : 'badge-blue')\">{{ n[4] }}</span></td>\n                  <td>{{ n[5] }}</td>\n                  <td>{{ n[6] }}</td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl\" :class=\"approval.status === '已通过' ? 'concl-pass' : ''\">\n            <span class=\"rv-cc-icon\">，</span>\n            <span>{{ approval.note }}</span>\n          </div>\n        </div>\n      </div>\n\n      \n      <!-- 运营视图：整项'ECharts 大盘（同文档内联，CSS 作用基'#ops-view-root，按比例缩放适配，'-->\n      <div v-show=\"activeTab === 'monitor'\" class=\"tab-content rv-wrap\">\n<!-- 产销品到期预警视图：监控运维页签内子块，展示全部商品到期风险（静态示例） -->\n      <div class=\"rv-card\" style=\"margin:16px 0;\">\n        <div class=\"rv-head\">\n          <div class=\"rv-title\">\n            <svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><polyline points=\"12 6 12 12 16 14\"/></svg>\n            产销品到期预警\n          </div>\n          <div style=\"display:flex;gap:8px;\">\n            <span class=\"rv-badge badge-red\">已到期 {{ expiryView.expiredCount }}</span>\n            <span class=\"rv-badge badge-amber\">临期 (≤30天) {{ expiryView.dueCount }}</span>\n          </div>\n        </div>\n\n        <div class=\"rv-metrics\">\n          <div class=\"rv-metric\"><div class=\"rv-m-num\" style=\"color:#2563eb;\">{{ expiryView.total }}</div><div class=\"rv-m-tt\">临期预警商品</div></div>\n          <div class=\"rv-metric\"><div class=\"rv-m-num\" style=\"color:#dc2626;\">{{ expiryView.expiredCount }}</div><div class=\"rv-m-tt\">已到期</div></div>\n          <div class=\"rv-metric\"><div class=\"rv-m-num\" style=\"color:#d97706;\">{{ expiryView.dueCount }}</div><div class=\"rv-m-tt\">临期 (≤30天)</div></div>\n          <div class=\"rv-metric\"><div class=\"rv-m-num\" style=\"color:#16a34a;\">{{ expiryView.okCount }}</div><div class=\"rv-m-tt\">其余正常</div></div>\n        </div>\n\n        <div style=\"overflow-x:auto;\">\n          <table class=\"rv-table\">\n            <thead>\n              <tr>\n                <th>商品名称</th>\n                <th>商品编码</th>\n                <th>类型</th>\n                <th>生效日期</th>\n                <th>到期日期</th>\n                <th>剩余天数</th>\n                <th>剩余进度(30天)</th>\n                <th>状态</th>\n              </tr>\n            </thead>\n            <tbody>\n              <tr v-for=\"r in expiryView.rows\" :key=\"r.code\">\n                <td style=\"font-weight:600;white-space:nowrap;\">{{ r.name }}</td>\n                <td style=\"font-family:Consolas,Menlo,monospace;color:#64748b;white-space:nowrap;\">{{ r.code }}</td>\n                <td><span class=\"rv-tag\">{{ r.type }}</span></td>\n                <td style=\"white-space:nowrap;\">{{ r.effDate }}</td>\n                <td style=\"white-space:nowrap;\">{{ r.expDate }}</td>\n                <td><b :style=\"{ color: r.status === 'ok' ? '#16a34a' : '#dc2626' }\">{{ r.status === 'expired' ? '已到期' : r.remain + ' 天' }}</b></td>\n                <td>\n                  <div style=\"display:flex;align-items:center;gap:8px;\">\n                    <div style=\"flex:0 0 80px;height:6px;background:#f1f5f9;border-radius:3px;overflow:hidden;\">\n                      <div :style=\"{ width: r.bar + '%', background: r.barColor }\" style=\"height:100%;border-radius:3px;\"></div>\n                    </div>\n                    <span style=\"font-size:12px;color:#94a3b8;\">{{ r.bar }}%</span>\n                  </div>\n                </td>\n                <td><span class=\"rv-badge\" :class=\"r.cls\">{{ r.label }}</span></td>\n              </tr>\n            </tbody>\n          </table>\n        </div>\n\n        <div class=\"rv-warn\" style=\"margin-top:14px;\">\n          <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/></svg>\n          <span>{{ expiryView.warn }}</span>\n        </div>\n      </div>\n\n        <div id=\"ops-view-root\">\n          <div class=\"ops-scaler\">\n<div class=\"layout\">\r\n  <main class=\"main\">\r\n\r\n    <!-- ======================================================\r\n         视图一：单品运营配置（刚上线'· 无数据）\r\n    ======================================================= -->\r\n    <section id=\"view-single\" class=\"view active\">\r\n\r\n      <div class=\"card\" style=\"padding:16px 20px;\">\r\n        <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n          <div>\r\n            <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n              5G-A套餐159元\r\n              <span style=\"display:inline-flex;align-items:center;gap:5px;font-size:11.5px;font-weight:600;padding:3px 11px;border-radius:20px;background:var(--green-bg);color:var(--green);\">\r\n                <span style=\"width:6px;height:6px;border-radius:50%;background:currentColor;\"></span>\r\n                已上线 · 监控初始化中\r\n              </span>\r\n            </div>\r\n            <div style=\"font-size:11.5px;color:var(--text-lt);margin-top:4px;\">\r\n              产品编码：00102308 &nbsp;·&nbsp; 上线时间：2026-09-15 10:24 &nbsp;·&nbsp; 归属：省公司市场部\r\n            </div>\r\n          </div>\r\n          <div style=\"display:flex;gap:8px;\">\r\n            <button class=\"btn btn-sm btn-primary\" onclick=\"switchView('board')\">查看运营视图</button>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <div class=\"card\" style=\"padding:14px 18px;background:#FBFCFE;border:1px solid #D3DEEC;\">\r\n        <div style=\"display:flex;gap:12px;font-size:12.5px;line-height:1.6;color:#00704D;\">\r\n          <div style=\"width:20px;height:20px;border-radius:5px;background:var(--green);color:#fff;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0;\">，</div>\r\n          <div>\r\n            <b>配置、稽核、校准、测试、受理验证已全部通过</b>，OA 上线审批已完成。\r\n            当前产品进入监控运维阶段，因刚上线暂无实际数据，以下<b>通用经营指标口径</b> 一'<b>预警推送规则</b>，确认后系统将自 T+1 日出账起自动采集并触发监控。\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 指标字典与预警规则'-->\r\n      \r\n\r\n      \r\n    </section>\r\n\r\n    <!-- ======================================================\r\n         视图二：驾驶舱风格调度大盘（仅通用经营指标）\r\n    ======================================================= -->\r\n    <section id=\"view-board\" class=\"view\">\r\n\r\n      <!-- 顶部：面包屑 + 月份选择（参考首页工具栏）-->\r\n      <div style=\"display:flex;align-items:center;justify-content:space-between;height:44px;margin-bottom:6px;\">\r\n        <div style=\"font-size:13px;\">\r\n          <span style=\"color:#4b5563;font-weight:500;\">全省大盘</span>\r\n        </div>\r\n        <div style=\"position:relative;display:inline-flex;align-items:center;\">\r\n          <input type=\"month\" id=\"boardMonthPicker\" value=\"2026-09\" title=\"选择月份\"\r\n                 style=\"padding:8px 36px 8px 12px;border:1px solid #d1d5db;border-radius:6px;font-size:13px;color:#374151;background:#fff;appearance:none;-webkit-appearance:none;font-family:inherit;\">\r\n          <span style=\"position:absolute;right:10px;color:#9ca3af;pointer-events:none;font-size:13px;\">📅</span>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 第一排：已'= 全省系列收入，'/3），可'= 健康度分析（1/3，'-->\r\n      <div class=\"main-grid\">\r\n        <!-- 左：系列收入总卡 -->\r\n        <div class=\"market-revenue-card\">\r\n          <div class=\"section-title\">市场收入</div>\r\n          <div class=\"market-revenue-cols\">\r\n            <!-- 累计收入 -->\r\n            <div class=\"market-revenue-col\">\r\n              <div style=\"display:flex;align-items:flex-start;gap:12px;\">\r\n                <div class=\"mr-icon mr-icon-blue\">🖥</div>\r\n                <div>\r\n                  <div style=\"font-size:13px;color:#4b5563;font-weight:500;\">累计收入</div>\r\n                  <div class=\"mr-value-line\">\r\n                    <div class=\"mr-value\">1023.21<span class=\"unit\">万元</span></div>\r\n                    <div style=\"font-size:12.5px;display:flex;align-items:center;gap:6px;white-space:nowrap;\">\r\n                      <span style=\"color:#374151;\">月同比</span>\r\n                      <span class=\"yoy-down\">-10.1% <i class=\"caret\">▼</i></span>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n            <!-- 当月收入 -->\r\n            <div class=\"market-revenue-col\">\r\n              <div style=\"display:flex;align-items:flex-start;gap:12px;\">\r\n                <div class=\"mr-icon mr-icon-amber\">👤</div>\r\n                <div>\r\n                  <div style=\"font-size:13px;color:#4b5563;font-weight:500;\">当月收入</div>\r\n                  <div class=\"mr-value-line\">\r\n                    <div class=\"mr-value\">980.55<span class=\"unit\">万元</span></div>\r\n                    <div style=\"font-size:12.5px;display:flex;align-items:center;gap:6px;white-space:nowrap;\">\r\n                      <span style=\"color:#374151;\">月同比</span>\r\n                      <span class=\"yoy-up\">3.6% <i class=\"caret\">▲</i></span>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 内嵌三系列收入：通信服务收入→'G-A系列 / 算力服务收入→权益随心选系列'/ 智能服务收入→融合套餐系列'-->\r\n          <div class=\"series-revenue-inner\">\r\n            <div class=\"series-grid\">\r\n              <!-- 5G-A系列收入 -->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#dbeafe;color:#2563eb;\">📶</div>\r\n                  <div class=\"series-head-name\">5G-A系列收入</div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">累计收入</div>\r\n                    <div class=\"sp-value\">613.45<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-up\">2.7% <i class=\"caret\">▲</i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">当月收入</div>\r\n                    <div class=\"sp-value\">420.18<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-down\">-5.2% <i class=\"caret\">▼</i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 权益随心选系列收入 -->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#d1fae5;color:#059669;\">🎛</div>\r\n                  <div class=\"series-head-name\">权益随心选系列收入</div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">累计收入</div>\r\n                    <div class=\"sp-value\">245.67<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-down\">-6.2% <i class=\"caret\">▼</i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">当月收入</div>\r\n                    <div class=\"sp-value\">180.34<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-up\">1.9% <i class=\"caret\">▲</i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 融合套餐系列收入 -->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#ffedd5;color:#ea580c;\">🧩</div>\r\n                  <div class=\"series-head-name\">融合套餐系列收入</div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">累计收入</div>\r\n                    <div class=\"sp-value\">188.22<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-down\">-4.8% <i class=\"caret\">▼</i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">当月收入</div>\r\n                    <div class=\"sp-value\">150.91<span class=\"unit\">户</span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">月同比</span><span class=\"yoy-up\">2.3% <i class=\"caret\">▲</i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <!-- 右：健康度分析'-->\r\n        <div class=\"health-analysis-card\">\r\n          <div class=\"section-title\">健康度分析</div>\r\n          <div class=\"health-analysis\">\r\n            <div class=\"health-count-list\">\r\n              <div class=\"health-count-row health-count-row--total\">\r\n                <span>套餐总数</span>\r\n                <span class=\"value\">10户</span>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-warn\">\r\n                <span>预警套餐数</span>\r\n                <span><span class=\"value\">4户</span><i class=\"trigger\">▼</i></span>\r\n                <div id=\"dropdown-warn\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A套餐单品299元</span><span class=\"sc sc-yellow\">59</span></div>\r\n                  <div class=\"item\"><span>5G-A融合套餐199元</span><span class=\"sc sc-red\">52</span></div>\r\n                  <div class=\"item\"><span>权益随心通09元</span><span class=\"sc sc-red\">58</span></div>\r\n                  <div class=\"item\"><span>权益随心通9元</span><span class=\"sc sc-red\">48</span></div>\r\n                </div>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-excellent\">\r\n                <span>健康套餐数</span>\r\n                <span><span class=\"value\">3户</span><i class=\"trigger\">▼</i></span>\r\n                <div id=\"dropdown-excellent\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A融合套餐199元</span><span class=\"sc sc-green\">91</span></div>\r\n                  <div class=\"item\"><span>5G-A套餐159元</span><span class=\"sc sc-green\">88</span></div>\r\n                  <div class=\"item\"><span>5G-A融合套餐239元</span><span class=\"sc sc-green\">87</span></div>\r\n                </div>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-good\">\r\n                <span>亚健康套餐数</span>\r\n                <span><span class=\"value\">3户</span><i class=\"trigger\">▼</i></span>\r\n                <div id=\"dropdown-good\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A套餐单品199元</span><span class=\"sc sc-yellow\">79</span></div>\r\n                  <div class=\"item\"><span>5G-A融合套餐299元</span><span class=\"sc sc-yellow\">74</span></div>\r\n                  <div class=\"item\"><span>5G-A套餐单品239元</span><span class=\"sc sc-yellow\">65</span></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n            <div class=\"health-pie-wrap\">\r\n              <div class=\"health-pie-figure\">\r\n                <svg id=\"healthPieSvg\" width=\"220\" height=\"100\" viewBox=\"0 0 220 100\" role=\"img\" aria-label=\"套餐健康度分布\">\r\n                  <ellipse cx=\"110\" cy=\"82\" rx=\"78\" ry=\"6\" fill=\"rgba(29, 78, 216, 0.12)\"></ellipse>\r\n                  <ellipse cx=\"110\" cy=\"50\" rx=\"58\" ry=\"16\" stroke=\"#2563eb\" stroke-width=\"3\" fill=\"none\" opacity=\"0.18\"></ellipse>\r\n                  <g id=\"healthPieBottom\" transform=\"translate(0,10) scale(1,0.40) translate(0,64)\"></g>\r\n                  <g id=\"healthPieTop\" transform=\"scale(1,0.40) translate(0,64)\"></g>\r\n                </svg>\r\n              </div>\r\n              <div class=\"health-pie-legend\">\r\n                <div class=\"health-pie-legend-row\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#0ea5ff\"></span>\r\n                    <span class=\"health-pie-legend-label\">健康套餐（≥80分）</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#0ea5ff\">30%</span>\r\n                </div>\r\n                <div class=\"health-pie-legend-row\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#ff2a7a\"></span>\r\n                    <span class=\"health-pie-legend-label\">亚健康套餐（60-80分）</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#ff2a7a\">40%</span>\r\n                </div>\r\n                <div class=\"health-pie-legend-row\" style=\"padding-bottom:0;\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#ff7a00\"></span>\r\n                    <span class=\"health-pie-legend-label\">预警套餐，'lt;60分）</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#ff7a00\">30%</span>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 第二排：单套餐收入下钻（tab 切换 + 导出 + 双图）-->\r\n      <div class=\"series-revenue-box\">\r\n        <div class=\"sr-toolbar\">\r\n          <div class=\"sr-tabs\">\r\n            <button type=\"button\" class=\"sr-tab active\" data-tab=\"wga\">5G-A系列收入</button>\r\n            <button type=\"button\" class=\"sr-tab\" data-tab=\"quanyi\">权益随心选系列收入</button>\r\n            <button type=\"button\" class=\"sr-tab\" data-tab=\"ronghe\">融合套餐系列收入</button>\r\n          </div>\r\n          <button type=\"button\" class=\"sr-export\" id=\"srExportBtn\">⇩导出</button>\r\n        </div>\r\n        <div class=\"sr-content\" id=\"srContent\"></div>\r\n      </div>\r\n\r\n      <!-- 第三排：套餐下钻列表 -->\r\n      <div class=\"card\" id=\"board-list\">\r\n        <div class=\"card-head\">\r\n          <div class=\"card-title\">下钻至全量套餐</div>\r\n          <div class=\"card-sub\">点击单个套餐进入健康度运营视图</div>\r\n        </div>\r\n        <div class=\"prod-grid\">\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A套餐159元','900102308','单品')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A套餐159元</div><div class=\"prod-code\">900102308</div></div>\r\n              <div class=\"health-score hs-green\">88</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">单品</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>12.5 万</b></div>\r\n              <div>新增<b>1.82 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+16.4%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A套餐单品199元','900113045','单品')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A套餐单品199元</div><div class=\"prod-code\">900113045</div></div>\r\n              <div class=\"health-score hs-green\">85</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">单品</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>8.3 万</b></div>\r\n              <div>新增<b>1.14 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+12.6%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A套餐单品239元','900113043','单品')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A套餐单品239元</div><div class=\"prod-code\">900113043</div></div>\r\n              <div class=\"health-score hs-green\">82</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">单品</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>6.7 万</b></div>\r\n              <div>新增<b>0.86 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+8.2%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A套餐单品299元','900102310','单品')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A套餐单品299元</div><div class=\"prod-code\">900102310</div></div>\r\n              <div class=\"health-score hs-yellow\">79</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">单品</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>4.2 万</b></div>\r\n              <div>新增<b>0.42 万</b></div>\r\n              <div>环比<b style=\"color:var(--green);\">-3.6%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A融合套餐199元','900113046','融合')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A融合套餐199元</div><div class=\"prod-code\">900113046</div></div>\r\n              <div class=\"health-score hs-green\">91</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">融合</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>15.8 万</b></div>\r\n              <div>新增<b>2.14 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+21.8%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A融合套餐199元','900102306','融合')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A融合套餐199元</div><div class=\"prod-code\">900102306</div></div>\r\n              <div class=\"health-score hs-yellow\">74</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">融合</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>3.6 万</b></div>\r\n              <div>新增<b>0.32 万</b></div>\r\n              <div>环比<b style=\"color:var(--green);\">-7.4%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A融合套餐239元','900113044','融合')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A融合套餐239元</div><div class=\"prod-code\">900113044</div></div>\r\n              <div class=\"health-score hs-green\">87</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">融合</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>9.4 万</b></div>\r\n              <div>新增<b>1.26 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+14.2%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A融合套餐299元','900102307','融合')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A融合套餐299元</div><div class=\"prod-code\">900102307</div></div>\r\n              <div class=\"health-score hs-green\">83</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">融合</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>到达用户<b>5.8 万</b></div>\r\n              <div>新增<b>0.68 万</b></div>\r\n              <div>环比<b style=\"color:var(--red);\">+9.6%</b></div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- ===== 单品健康度详情（点击后显示） ===== -->\r\n      <div id=\"health-detail\" style=\"display:none;\">\r\n        <div class=\"back-link\" onclick=\"closeHealth()\">→'返回 5G-A 产品列表</div>\r\n        <div class=\"card\" style=\"padding:16px 20px;\">\r\n          <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n            <div>\r\n              <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n                <span id=\"hd-name\">5G-A套餐159元</span>\r\n                <span id=\"hd-code\" style=\"font-size:12px;font-weight:400;color:var(--text-lt);\">900102308</span>\r\n                <span id=\"hd-type\" class=\"prod-type pt-single\" style=\"margin-bottom:0;\">单品</span>\r\n              </div>\r\n            </div>\r\n            <div style=\"display:flex;gap:8px;\">\r\n              <button class=\"btn btn-sm btn-primary\" onclick=\"opsOpenRules()\">运营规则</button>\n              <button class=\"btn btn-sm\">导出报表</button>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <div class=\"health-hero\">\r\n          <div class=\"hero-score hs-green\"><b>88</b><span>分</span></div>\r\n          <div class=\"hero-body\">\r\n            <div class=\"hero-tagline\" style=\"background:var(--green-bg);color:var(--green);\">健康 · 较上月'+4 分</div>\r\n            <div class=\"hero-desc\">5G-A 网络驻留比持续提升，高值用户占比稳定；当月新增 1.82 万、新增占比10.0% 保持稳健，续约率 91.5%、整体运营健康、</div>\r\n            <div class=\"hero-metrics\">\r\n              <div>到达用户<b>12.5 万</b></div>\r\n              <div>当月新增<b style=\"color:var(--green);\">1.82 万</b></div>\r\n              <div>当月收入<b>3,168 万</b></div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <div class=\"dim-grid\">\r\n\r\n          <!-- ①效益维度：赚了多少？赚得怎么样？ -->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\"></span>效益维度<span class=\"q\">赚了多少 · 赚得怎么标</span></div>\r\n              <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                <div class=\"ops-tabs\" id=\"opsEffectTabs\">\r\n                  <button type=\"button\" class=\"active\" data-effect=\"income\">收入趋势</button>\r\n                  <button type=\"button\" data-effect=\"profit\">利润率</button>\r\n                  <button type=\"button\" data-effect=\"crm\">营销费用占比</button>\r\n                </div>\r\n                <div class=\"ops-mod-score\">40<small> 分</small></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 收入趋势：双轴'当月+累计) + 同'环比折线 -->\r\n            <div class=\"ops-tabpane active\" id=\"opsEffectIncomePane\">\r\n              <div class=\"ops-sub-toggle\" id=\"opsEffectRateTabs\">\r\n                <button type=\"button\" class=\"active\" data-rate=\"yoy\">同比</button>\r\n                <button type=\"button\" data-rate=\"mom\">环比</button>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsEffectIncome\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>当月收入(万元)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>累计收入(万元)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>当月收入同比/环比(%)</span></div>\r\n            </div>\r\n\r\n            <!-- 利润率：折线 -->\r\n            <div class=\"ops-tabpane\" id=\"opsEffectProfitPane\">\r\n              <div class=\"ops-chartbox\" id=\"opsEffectProfit\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>利润率(%)<b style=\"margin-left:6px;color:var(--text);\">14.2%</b></span></div>\r\n            </div>\r\n\r\n            <!-- 营销费用占比：折线 -->\r\n            <div class=\"ops-tabpane\" id=\"opsEffectCrmPane\">\r\n              <div class=\"ops-chartbox\" id=\"opsEffectCrm\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>营销费用占比(%)<b style=\"margin-left:6px;color:var(--text);\">16.5%</b></span></div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- ①市场维度：有多少于'· 增长怎么标'· 人从哪来，'-->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--green);\"></span>市场维度<span class=\"q\">有多少人 · 增长怎么样 · 人从哪里来</span></div>\r\n              <div class=\"ops-mod-score\">22<small> 分</small></div>\r\n            </div>\r\n\r\n            <!-- 左：用户 ，'右：渠道划分 -->\r\n            <div class=\"ops-mkt-2col\">\r\n\r\n              <!-- 用户区块 -->\r\n              <div class=\"ops-mkt-block ops-mkt-users\">\r\n                <div class=\"ops-mkt-block-head\">\r\n                  <div style=\"display:flex;align-items:center;gap:10px;flex-wrap:wrap;\">\r\n                    <span class=\"ops-mkt-block-title\">用户</span>\r\n                    <div class=\"ops-tabs\" id=\"opsUserTabs\">\r\n                      <button type=\"button\" class=\"active\" data-utab=\"scale\">规模</button>\r\n                      <button type=\"button\" data-utab=\"active\">活跃</button>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n\r\n                <!-- 规模：双轴到达用户+月净增 + 同环比折线 -->\r\n                <div class=\"ops-utabpane active\" id=\"opsUserScalePane\">\r\n                  <div class=\"ops-rate-row\">\r\n                    <span class=\"ops-mkt-sub\">到达用户 · 月净增</span>\r\n                    <div class=\"ops-sub-toggle\" id=\"opsGrowthRateTabs\">\r\n                      <button type=\"button\" class=\"active\" data-grate=\"yoy\">同比</button>\r\n                      <button type=\"button\" data-grate=\"mom\">环比</button>\r\n                    </div>\r\n                  </div>\r\n                  <div class=\"ops-chartbox\" id=\"opsMktScale\" style=\"height:195px;\"></div>\r\n                  <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>到达用户(户)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>月净增(户)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>同比/环比(%)</span></div>\r\n                </div>\r\n\r\n                <!-- 活跃：活跃用户率 + 付费活跃率'双折线'-->\r\n                <div class=\"ops-utabpane\" id=\"opsUserActivePane\">\r\n                  <div class=\"ops-chartbox\" id=\"opsActiveTrend\" style=\"height:195px;\"></div>\r\n                  <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>活跃用户率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>付费活跃率(%)</span></div>\r\n                </div>\r\n              </div>\r\n\r\n              <!-- 渠道划分区块 -->\r\n              <div class=\"ops-mkt-block ops-mkt-channel\">\r\n                <div class=\"ops-mkt-block-head\">\r\n                  <span class=\"ops-mkt-block-title\">渠道划分</span>\r\n                  <select class=\"ops-mkt-month\" id=\"opsChannelMonth\">\r\n                    <option value=\"2025-12\">2025年12月</option>\r\n                    <option value=\"2026-01\">2026年1月</option>\r\n                    <option value=\"2026-02\">2026年1月</option>\r\n                    <option value=\"2026-03\">2026年1月</option>\r\n                    <option value=\"2026-04\">2026年1月</option>\r\n                    <option value=\"2026-05\">2026年1月</option>\r\n                    <option value=\"2026-06\">2026年1月</option>\r\n                    <option value=\"2026-07\">2026年1月</option>\r\n                    <option value=\"2026-08\">2026年1月</option>\r\n                    <option value=\"2026-09\" selected>2026年1月</option>\r\n                  </select>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsChannelPie\" style=\"height:245px;\"></div>\r\n              </div>\r\n\r\n            </div>\r\n          </div>\r\n\r\n          <!-- ①运营质量维度：运营有没有问题？哪里有问题）-->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--yellow);\"></span>运营质量维度<span class=\"q\">运营有没有问预'· 哪里有问预</span></div>\r\n              <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                <div class=\"ops-tabs\" id=\"opsQualityTabs\">\r\n                  <button type=\"button\" class=\"active\" data-qt=\"retain\">留存</button>\r\n                  <button type=\"button\" data-qt=\"service\">服务</button>\r\n                </div>\r\n                <div class=\"ops-mod-score\">18<small> 分</small></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 留存：T+1活跃率 / T+3 / T+6 退订率 -->\r\n            <div class=\"ops-tabpane active\" id=\"opsQRetainPane\">\r\n              <div class=\"ops-q-chips\">\r\n                <div class=\"ops-q-chip c-green\"><span class=\"q-ico\">📈</span><span>T+1 活跃率</span><div class=\"q-row\"><b>78.4%</b><span class=\"q-trend\">▲.2</span></div><em>新增次日仍活跃</em></div>\r\n                <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">📉</span><span>T+3 退订率</span><div class=\"q-row\"><b>1.40%</b><span class=\"q-trend\">▲.06</span></div><em>新增 3 日退订</em></div>\r\n                <div class=\"ops-q-chip c-blue\"><span class=\"q-ico\">📉</span><span>T+6 退订率</span><div class=\"q-row\"><b>2.30%</b><span class=\"q-trend\">▲.06</span></div><em>新增 6 日退订</em></div>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsQRetain\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>T+1活跃率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>T+3退订率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>T+6退订率(%)</span></div>\r\n            </div>\r\n\r\n            <!-- 服务：资费投诉率 / 业务办理成功率'-->\r\n            <div class=\"ops-tabpane\" id=\"opsQServicePane\">\r\n              <div class=\"ops-q-chips\">\r\n                <div class=\"ops-q-chip c-blue\"><span class=\"q-ico\">，</span><span>业务办理成功率</span><div class=\"q-row\"><b>99.2%</b><span class=\"q-trend\">▲.1</span></div><em>高于 98% 目标线</em></div>\r\n                <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">⚠️</span><span>资费投诉率</span><div class=\"q-row\"><b>0.02%</b><span class=\"q-trend\">▼</span></div><em>远低于0.1% 预警线</em></div>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsQService\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>业务办理成功率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--red);\"></i>资费投诉率(%)</span></div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- ①生命周期维度：用户处在哪个阶段？新增质量怎么样？ -->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--purple);\"></span>生命周期维度<span class=\"q\">用户处在哪个阶段 · 新增质量怎么标</span></div>\r\n              <div class=\"ops-mod-score\">8<small> 分</small></div>\r\n            </div>\r\n            <!-- 用户生命线：各阶段表率'+ 占比 -->\r\n            <div class=\"ops-life\">\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-new\">新增</span>\r\n                <div class=\"ln-ico c-blue\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z\"/><path d=\"M2 19a6 6 0 0 1 10-4.2\"/><path d=\"M19 8v6M16 11h6\"/></svg></div>\r\n                <div class=\"ln-name\">新增用户</div>\r\n                <div class=\"ln-val\">10.0<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--blue);\"></i>本月新增占比</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-act\">激活</span>\r\n                <div class=\"ln-ico c-green\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13 2 3 14h7l-1 8 10-12h-7l1-8z\"/></svg></div>\r\n                <div class=\"ln-name\">活跃用户</div>\r\n                <div class=\"ln-val\">86.4<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--green);\"></i>月活跃率</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-ret\">留存</span>\r\n                <div class=\"ln-ico c-purple\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z\"/><path d=\"m9 12 2 2 4-4\"/></svg></div>\r\n                <div class=\"ln-name\">留存用户</div>\r\n                <div class=\"ln-val\">97.7<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--purple);\"></i>T+6 留存</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-sil\">沉默</span>\r\n                <div class=\"ln-ico c-amber\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z\"/></svg></div>\r\n                <div class=\"ln-name\">沉默用户</div>\r\n                <div class=\"ln-val\">8.5<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:#D48806;\"></i>沉默占比</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-churn\">流失 · 续约</span>\r\n                <div class=\"ln-ico c-red\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12a9 9 0 1 1-2.9-6.6\"/><path d=\"M21 3v5h-5\"/></svg></div>\r\n                <div class=\"ln-name\">续约率</div>\r\n                <div class=\"ln-val\">91.5<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--red);\"></i>到期续约率</div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 阶段占比流量图 -->\r\n            <div class=\"ops-flow\">\r\n              <div class=\"ops-flow-head\">\r\n                <div class=\"ops-flow-title\"><i></i>存量结构 · 阶段占比</div>\r\n              </div>\r\n              <div class=\"ops-flow-track\">\r\n                <div class=\"ops-flow-seg\" style=\"width:86.4%;background:linear-gradient(90deg,#00A86C,#00B578);\">活跃用户<small>86.4%</small></div>\r\n                <div class=\"ops-flow-seg\" style=\"width:8.5%;background:linear-gradient(90deg,#C07A06,#D48806);\">沉默<small>8.5%</small></div>\r\n                <div class=\"ops-flow-seg\" style=\"width:5.1%;background:linear-gradient(90deg,#D91B27,#F5222D);\">流失<small>5.1%</small></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n        </div>\r\n\r\n        <div class=\"card\">\r\n          <div class=\"card-head\"><div class=\"card-title\" style=\"font-size:13px;\"><span style=\"width:3px;height:14px;background:var(--red);border-radius:2px;display:inline-block;\"></span>异动预警</div></div>\r\n          <div class=\"alert alert-green\" style=\"margin-bottom:0;\"><div class=\"alert-icon\">，</div><div class=\"alert-text\"><b>存量基本盘稳定：</b>投诉率0.02%、T+6 退订率 2.30%、业务办理成功率 99.2%、续约率 91.5% 均在正常范围</div></div>\r\n        </div>\r\n      </div>\r\n        <!-- ===== 5G-A套餐单品299元 · 健康度详情（亚健康画像） ===== -->\r\n        <div id=\"health-detail-299\" style=\"display:none;\">\r\n          <div class=\"back-link\" onclick=\"closeHealth()\">→ 返回 5G-A 产品列表</div>\r\n          <div class=\"card\" style=\"padding:16px 20px;\">\r\n            <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n              <div>\r\n                <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n                  <span id=\"hd-name-299\">5G-A套餐单品299元</span>\r\n                  <span id=\"hd-code-299\" style=\"font-size:12px;font-weight:400;color:var(--text-lt);\">900102310</span>\r\n                  <span id=\"hd-type-299\" class=\"prod-type pt-single\" style=\"margin-bottom:0;\">单品</span>\r\n                </div>\r\n              </div>\r\n              <div style=\"display:flex;gap:8px;\">\r\n                <button class=\"btn btn-sm\">导出报表</button>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <div class=\"health-hero\">\r\n            <div class=\"hero-score hs-yellow\"><b>79</b><span>分</span></div>\r\n            <div class=\"hero-body\">\r\n              <div class=\"hero-tagline\" style=\"background:var(--yellow-bg);color:#D48806;\">亚健康'· 较上月'-3 分</div>\r\n              <div class=\"hero-desc\">高端档位用户增长放缓，收入环比连续两月下滑（-2.8%），利润率降至11.6% 而营销费用占比升至 18.9%，续约率 88.6% 偏低、沉默用户占比14.2% 偏高，需重点维系高值存量用户并加大权益焕活、</div>\r\n              <div class=\"hero-metrics\">\r\n                <div>到达用户<b>4.2 万</b></div>\r\n                <div>当月新增<b style=\"color:var(--green);\">0.42 万</b></div>\r\n                <div>当月收入<b>1,246 万</b></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <div class=\"dim-grid\">\r\n\r\n            <!-- ①效益维度 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\"></span>效益维度<span class=\"q\">赚了多少 · 赚得怎么标</span></div>\r\n                <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                  <div class=\"ops-tabs\" id=\"opsEffectTabs299\">\r\n                    <button type=\"button\" class=\"active\" data-effect=\"income\">收入趋势</button>\r\n                    <button type=\"button\" data-effect=\"profit\">利润率</button>\r\n                    <button type=\"button\" data-effect=\"crm\">营销费用占比</button>\r\n                  </div>\r\n                  <div class=\"ops-mod-score\">33<small> 分</small></div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane active\" id=\"opsEffectIncomePane299\">\r\n                <div class=\"ops-sub-toggle\" id=\"opsEffectRateTabs299\">\r\n                  <button type=\"button\" class=\"active\" data-rate=\"yoy\">同比</button>\r\n                  <button type=\"button\" data-rate=\"mom\">环比</button>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsEffectIncome299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>当月收入(万元)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>累计收入(万元)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>当月收入同比/环比(%)</span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsEffectProfitPane299\">\r\n                <div class=\"ops-chartbox\" id=\"opsEffectProfit299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>利润率(%)<b style=\"margin-left:6px;color:var(--text);\">11.6%</b></span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsEffectCrmPane299\">\r\n                <div class=\"ops-chartbox\" id=\"opsEffectCrm299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>营销费用占比(%)<b style=\"margin-left:6px;color:var(--text);\">18.9%</b></span></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- ①市场维度 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--green);\"></span>市场维度<span class=\"q\">有多少人 · 增长怎么样 · 人从哪里来</span></div>\r\n                <div class=\"ops-mod-score\">19<small> 分</small></div>\r\n              </div>\r\n\r\n              <div class=\"ops-mkt-2col\">\r\n                <div class=\"ops-mkt-block ops-mkt-users\">\r\n                  <div class=\"ops-mkt-block-head\">\r\n                    <div style=\"display:flex;align-items:center;gap:10px;flex-wrap:wrap;\">\r\n                      <span class=\"ops-mkt-block-title\">用户</span>\r\n                      <div class=\"ops-tabs\" id=\"opsUserTabs299\">\r\n                        <button type=\"button\" class=\"active\" data-utab=\"scale\">规模</button>\r\n                        <button type=\"button\" data-utab=\"active\">活跃</button>\r\n                      </div>\r\n                    </div>\r\n                  </div>\r\n\r\n                  <div class=\"ops-utabpane active\" id=\"opsUserScalePane299\">\r\n                    <div class=\"ops-rate-row\">\r\n                      <span class=\"ops-mkt-sub\">到达用户 · 月净增</span>\r\n                      <div class=\"ops-sub-toggle\" id=\"opsGrowthRateTabs299\">\r\n                        <button type=\"button\" class=\"active\" data-grate=\"yoy\">同比</button>\r\n                        <button type=\"button\" data-grate=\"mom\">环比</button>\r\n                      </div>\r\n                    </div>\r\n                    <div class=\"ops-chartbox\" id=\"opsMktScale299\" style=\"height:195px;\"></div>\r\n                    <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>到达用户(户)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>月净增(户)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>同比/环比(%)</span></div>\r\n                  </div>\r\n\r\n                  <div class=\"ops-utabpane\" id=\"opsUserActivePane299\">\r\n                    <div class=\"ops-chartbox\" id=\"opsActiveTrend299\" style=\"height:195px;\"></div>\r\n                    <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>活跃用户率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>付费活跃率(%)</span></div>\r\n                  </div>\r\n                </div>\r\n\r\n                <div class=\"ops-mkt-block ops-mkt-channel\">\r\n                  <div class=\"ops-mkt-block-head\">\r\n                    <span class=\"ops-mkt-block-title\">渠道划分</span>\r\n                    <select class=\"ops-mkt-month\" id=\"opsChannelMonth299\">\r\n                      <option value=\"2025-12\">2025年12月</option>\r\n                      <option value=\"2026-01\">2026年1月</option>\r\n                      <option value=\"2026-02\">2026年1月</option>\r\n                      <option value=\"2026-03\">2026年1月</option>\r\n                      <option value=\"2026-04\">2026年1月</option>\r\n                      <option value=\"2026-05\">2026年1月</option>\r\n                      <option value=\"2026-06\">2026年1月</option>\r\n                      <option value=\"2026-07\">2026年1月</option>\r\n                      <option value=\"2026-08\">2026年1月</option>\r\n                      <option value=\"2026-09\" selected>2026年1月</option>\r\n                    </select>\r\n                  </div>\r\n                  <div class=\"ops-chartbox\" id=\"opsChannelPie299\" style=\"height:245px;\"></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- ①运营质量维度 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--yellow);\"></span>运营质量维度<span class=\"q\">运营有没有问预'· 哪里有问预</span></div>\r\n                <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                  <div class=\"ops-tabs\" id=\"opsQualityTabs299\">\r\n                    <button type=\"button\" class=\"active\" data-qt=\"retain\">留存</button>\r\n                    <button type=\"button\" data-qt=\"service\">服务</button>\r\n                  </div>\r\n                  <div class=\"ops-mod-score\">17<small> 分</small></div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane active\" id=\"opsQRetainPane299\">\r\n                <div class=\"ops-q-chips\">\r\n                  <div class=\"ops-q-chip c-green\"><span class=\"q-ico\">📈</span><span>T+1 活跃率</span><div class=\"q-row\"><b>76.1%</b><span class=\"q-trend\">▲.3</span></div><em>新增次日仍活跃</em></div>\r\n                  <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">⚠️</span><span>T+3 退订率</span><div class=\"q-row\"><b>2.10%</b><span class=\"q-trend\">▲.03</span></div><em>新增 3 日退订</em></div>\r\n                  <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">⚠️</span><span>T+6 退订率</span><div class=\"q-row\"><b>3.20%</b><span class=\"q-trend\">▲.02</span></div><em>新增 6 日退订</em></div>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsQRetain299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>T+1活跃率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>T+3退订率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>T+6退订率(%)</span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsQServicePane299\">\r\n                <div class=\"ops-q-chips\">\r\n                  <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">⚠️</span><span>业务办理成功率</span><div class=\"q-row\"><b>98.4%</b><span class=\"q-trend\">▲.1</span></div><em>略低于099% 目标</em></div>\r\n                  <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">⚠️</span><span>资费投诉率</span><div class=\"q-row\"><b>0.04%</b><span class=\"q-trend\">▼</span></div><em>接近 0.05% 预警</em></div>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsQService299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>业务办理成功率(%)</span><span><i class=\"ops-dot\" style=\"background:var(--red);\"></i>资费投诉率(%)</span></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- ①生命周期维度 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--purple);\"></span>生命周期维度<span class=\"q\">用户处在哪个阶段 · 新增质量怎么标</span></div>\r\n                <div class=\"ops-mod-score\">6<small> 分</small></div>\r\n              </div>\r\n              <div class=\"ops-life\">\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-new\">新增</span>\r\n                  <div class=\"ln-ico c-blue\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z\"/><path d=\"M2 19a6 6 0 0 1 10-4.2\"/><path d=\"M19 8v6M16 11h6\"/></svg></div>\r\n                  <div class=\"ln-name\">新增用户</div>\r\n                  <div class=\"ln-val\">10.5<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--blue);\"></i>本月新增占比</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-act\">激活</span>\r\n                  <div class=\"ln-ico c-green\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13 2 3 14h7l-1 8 10-12h-7l1-8z\"/></svg></div>\r\n                  <div class=\"ln-name\">活跃用户</div>\r\n                  <div class=\"ln-val\">80.5<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--green);\"></i>月活跃率</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-ret\">留存</span>\r\n                  <div class=\"ln-ico c-purple\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z\"/><path d=\"m9 12 2 2 4-4\"/></svg></div>\r\n                  <div class=\"ln-name\">留存用户</div>\r\n                  <div class=\"ln-val\">96.8<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--purple);\"></i>T+6 留存</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-sil\">沉默</span>\r\n                  <div class=\"ln-ico c-amber\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z\"/></svg></div>\r\n                  <div class=\"ln-name\">沉默用户</div>\r\n                  <div class=\"ln-val\">14.2<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:#D48806;\"></i>沉默占比</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-churn\">流失 · 续约</span>\r\n                  <div class=\"ln-ico c-red\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12a9 9 0 1 1-2.9-6.6\"/><path d=\"M21 3v5h-5\"/></svg></div>\r\n                  <div class=\"ln-name\">续约率</div>\r\n                  <div class=\"ln-val\">88.6<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--red);\"></i>到期续约率</div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-flow\">\r\n                <div class=\"ops-flow-head\">\r\n                  <div class=\"ops-flow-title\"><i></i>存量结构 · 阶段占比</div>\r\n                </div>\r\n                <div class=\"ops-flow-track\">\r\n                  <div class=\"ops-flow-seg\" style=\"width:80.5%;background:linear-gradient(90deg,#00A86C,#00B578);\">活跃用户<small>80.5%</small></div>\r\n                  <div class=\"ops-flow-seg\" style=\"width:14.2%;background:linear-gradient(90deg,#C07A06,#D48806);\">沉默<small>14.2%</small></div>\r\n                  <div class=\"ops-flow-seg\" style=\"width:5.3%;background:linear-gradient(90deg,#D91B27,#F5222D);\">流失<small>5.3%</small></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n\r\n          </div>\r\n\r\n          <div class=\"card\">\r\n            <div class=\"card-head\"><div class=\"card-title\" style=\"font-size:13px;\"><span style=\"width:3px;height:14px;background:var(--red);border-radius:2px;display:inline-block;\"></span>异动预警</div></div>\r\n            <div class=\"alert alert-yellow\" style=\"margin-bottom:0;\"><div class=\"alert-icon\">!</div><div class=\"alert-text\"><b>收入连续两月环比下滑，32.8%，</b>，续约率 88.6% 与 T+6 退订率 3.20% 均偏离健康线、沉默用户占比14.2% 偏高，建议针对到期用户开展权益加码维系并加大沉默用户唤醒</div></div>\r\n          </div>\r\n        </div>\r\n\r\n\r\n    \n        <!-- 运营规则：通用经营指标·预警规则·推送方式（从单产品视图移入）-->\n        <div id=\"ops-rules-panel\" style=\"display:none;\">\n          <div class=\"back-link\" onclick=\"opsCloseRules()\">→ 返回单产品画像</div>\n          <div class=\"ops-rules-bar\"><span class=\"ops-rules-title\">运营规则 · 通用经营指标 · 预警规则 · 推送方式</span></div>\n<div class=\"card\">\r\n        <div class=\"card-head\">\r\n          <div class=\"card-title\">通用经营指标 · 预警规则 · 推送方式</div>\r\n        </div>\r\n\r\n        <div style=\"display:flex;gap:14px;flex-wrap:wrap;align-items:center;font-size:11.5px;color:var(--text-sub);padding:10px 14px;background:#F7F9FC;border-radius:6px;margin-bottom:12px;\">\r\n          <b style=\"color:var(--text);font-size:12px;\">预警机制</b>\r\n          <span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">达到阈值即触发</span></span>\r\n          <span style=\"display:flex;align-items:center;gap:5px;\"><i style=\"width:9px;height:9px;border-radius:2px;background:var(--purple);display:inline-block;\"></i>异常推送（日调度）</span>\r\n        </div>\r\n\r\n        <div class=\"table-responsive\">\r\n          <table class=\"metric-table\">\r\n            <thead>\r\n              <tr>\r\n                <th style=\"width:108px;\">指标类型</th>\r\n                <th style=\"width:116px;\">指标名称</th>\r\n                <th style=\"width:320px;\">预警规则</th>\r\n                <th>指标口径</th>\r\n              </tr>\r\n            </thead>\r\n            <tbody>\r\n              <!-- 效益维度：收入与价值类 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">效益维度</td>\r\n                <td class=\"m-name\">当月收入</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">环比→'gt;10%</span></span></td>\r\n                <td class=\"m-desc\">该套餐当月出账收入（报表口径，</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">累计收入</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">环比→&gt;10%</span></span></td>\r\n                <td class=\"m-desc\">该套餐当年1 月至统计月累计出账收入</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">收入同'环比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">下滑&gt;5% 或连续两月下滑</span></span></td>\r\n                <td class=\"m-desc\">（本月收入 - 上月收入）÷ 上月收入（或同比口径，</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">利润率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 12%</span></span></td>\r\n                <td class=\"m-desc\">（收入 - 营销费用  - 成本）÷ 收入</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">营销费用占比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">高于 18%</span></span></td>\r\n                <td class=\"m-desc\">该套餐营销费用 ÷ 该套餐收入</td>\r\n              </tr>\r\n\r\n              <!-- 市场维度：规模、活跃与渠道 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">市场维度</td>\r\n                <td class=\"m-name\">到达用户</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">环比→'gt;5%</span></span></td>\r\n                <td class=\"m-desc\">当月出账且状态正常的该套餐主卡用户数（到达用户），副卡不重复计算</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">月净增用户</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">连续 3 日为费</span></span></td>\r\n                <td class=\"m-desc\">当月新增  - 当月退订</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">活跃用户率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 60%</span></span></td>\r\n                <td class=\"m-desc\">当月有使用行为的该套餐用户÷ 到达用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">付费活跃率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 5%</span></span></td>\r\n                <td class=\"m-desc\">付费活跃用户（当月产生实际计费账单）÷ 到达用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">渠道结构占比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">单一渠道占比&gt;70%</span></span></td>\r\n                <td class=\"m-desc\">线下营业厅 / 中国电信线上渠道 / 10000热线 / 安徽电信线上渠道 到达用户占比</td>\r\n              </tr>\r\n\r\n              <!-- 运营质量维度：质量与留存 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">运营质量维度</td>\r\n                <td class=\"m-name\">T+1 活跃率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 70%</span></span></td>\r\n                <td class=\"m-desc\">订购次日内仍活跃的新增用户'÷ 当期新增用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+3 退订率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">高于 3%</span></span></td>\r\n                <td class=\"m-desc\">订购后 3 天内退订用户 ÷ 当期新增用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+6 退订率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">高于 10%</span></span></td>\r\n                <td class=\"m-desc\">订购同06 天内退订用户'÷ 当期新增用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">业务办理成功率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 98%</span></span></td>\r\n                <td class=\"m-desc\">该套餐受理成功笔数 ÷ 受理总笔数</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">资费投诉率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">高于 0.1%</span></span></td>\r\n                <td class=\"m-desc\">涉及该套餐资费的投诉工单数'÷ 到达用户</td>\r\n              </tr>\r\n\r\n              <!-- 生命周期维度：新增、留存与用户结构 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">生命周期维度</td>\r\n                <td class=\"m-name\">新用户吸引占比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 8%</span></span></td>\r\n                <td class=\"m-desc\">新入网用户中订购该套餐的人数占比</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">活跃用户占比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 60%</span></span></td>\r\n                <td class=\"m-desc\">存量用户中当月为活跃阶段的占比（月活跃率，</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+6 留存率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 95%</span></span></td>\r\n                <td class=\"m-desc\">订购后第 6 天仍保持套餐状态的新增用户 ÷ 当期新增用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">沉默用户占比</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">高于 10%</span></span></td>\r\n                <td class=\"m-desc\">当月无任何使用行为的用户 ÷ 到达用户</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">续约率</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">阀</span><span class=\"th\">低于 90%</span></span></td>\r\n                <td class=\"m-desc\">到期用户中成功续约该套餐的用户占比</td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n      </div>\n        </div>\n      </section>\r\n\r\n  </main>\r\n</div>\n          </div>\n        </div>\n\n      \n      </div>\n      <div v-show=\"activeTab === 'summary'\" class=\"tab-content phone-preview-tab\">\r\n        <div class=\"phone-frame\">\r\n          <!-- 手机状态栏 -->\r\n          <div class=\"phone-statusbar\">\r\n            <span class=\"phone-time\">{{ phoneTime }}</span>\r\n            <div class=\"phone-status-icons\">\r\n              <svg width=\"14\" height=\"10\" viewBox=\"0 0 24 18\" fill=\"currentColor\"><path d=\"M1 14h2v6H1zM6 11h2v9H6zM11 8h2v12h-2zM16 5h2v15h-2zM21 2h2v18h-2z\"/></svg>\r\n              <svg width=\"14\" height=\"10\" viewBox=\"0 0 24 18\" fill=\"currentColor\"><path d=\"M12 3C7 3 2.7 5.1 0 8.5l2 2.5C4.2 8.3 7.9 6.5 12 6.5s7.8 1.8 10 4.5l2-2.5C21.3 5.1 17 3 12 3z\"/></svg>\r\n              <div class=\"phone-battery\">\r\n                <div class=\"phone-battery-bar\"></div>\r\n                <div class=\"phone-battery-cap\"></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- APP标题 -->\r\n          <div class=\"phone-navbar\">\r\n            <svg class=\"phone-nav-back\" width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"15 18 9 12 15 6\"/></svg>\r\n            <span class=\"phone-nav-title\">{{ phoneNavTitle }}</span>\r\n            <svg class=\"phone-nav-more\" width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><circle cx=\"12\" cy=\"5\" r=\"2\"/><circle cx=\"12\" cy=\"12\" r=\"2\"/><circle cx=\"12\" cy=\"19\" r=\"2\"/></svg>\r\n          </div>\r\n\r\n          <!-- APP内容 -->\r\n          <div class=\"phone-screen\">\r\n            <!-- 板块1: 用户受理信息 -->\r\n            <div v-if=\"phoneTab === 'user'\" class=\"app-page\">\r\n              <!-- 套餐横幅卡片 -->\r\n              <div class=\"app-banner-card\">\r\n                <div class=\"app-banner-bg\"></div>\r\n                <div class=\"app-banner-content\">\r\n                  <div class=\"app-banner-name\">{{ productInfo.prodPrcName }}</div>\r\n                  <div class=\"app-banner-fee\"><span class=\"fee-num\">{{ previewFee }}</span><span class=\"fee-unit\">元/月</span></div>\r\n                  <div class=\"app-banner-tags\">\r\n                    <span class=\"app-tag\">{{ previewFlowAmount }}{{ previewFlowUnit }}流量</span>\r\n                    <span class=\"app-tag\">{{ previewVoiceAmount }}分钟</span>\r\n                    <span class=\"app-tag\">{{ dynamicPreview ? (previewRights.length ? previewRights.length + '项权益' : '视频权益') : '5G-A网络权益' }}</span>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 资源详情卡片 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">套餐资源</div>\r\n                <div class=\"app-resource-grid\">\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon flow\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M5 12h14M12 5l7 7-7 7\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ previewFlowAmount }}<span class=\"unit\">{{ previewFlowUnit }}</span></div>\r\n                    <div class=\"app-resource-label\">通用流量</div>\r\n                  </div>\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon voice\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ previewVoiceAmount }}<span class=\"unit\">分钟</span></div>\r\n                    <div class=\"app-resource-label\">国内语音</div>\r\n                  </div>\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon video\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\"/><line x1=\"8\" y1=\"21\" x2=\"16\" y2=\"21\"/><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"21\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ dynamicPreview ? previewRights.length : 2 }}<span class=\"unit\">户</span></div>\r\n                    <div class=\"app-resource-label\">{{ dynamicPreview ? '权益资源' : '视频权益' }}</div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 权益详情 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">包含权益</div>\r\n                <div class=\"app-equity-list\">\r\n                  <div class=\"app-equity-item\" v-for=\"eq in equityList\" :key=\"eq.name\">\r\n                    <div class=\"app-equity-icon\" :style=\"{ background: eq.color }\">{{ eq.short }}</div>\r\n                    <div class=\"app-equity-info\">\r\n                      <div class=\"app-equity-name\">{{ eq.name }}</div>\r\n                      <div class=\"app-equity-desc\">{{ eq.desc }}</div>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 费用说明 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">费用说明</div>\r\n                <div class=\"app-fee-list\">\r\n                  <div class=\"app-fee-row\"><span>套餐月费</span><span class=\"app-fee-val\">{{ previewFee }}.00元/月</span></div>\r\n                  <div class=\"app-fee-row\"><span>套外流量</span><span class=\"app-fee-val\">{{ previewFlowOvercharge }}</span></div>\r\n                  <div class=\"app-fee-row\"><span>套外语音</span><span class=\"app-fee-val\">{{ previewVoiceOvercharge }}</span></div>\r\n                  <div v-if=\"previewSmsOvercharge\" class=\"app-fee-row\"><span>套外短信</span><span class=\"app-fee-val\">{{ previewSmsOvercharge }}</span></div>\r\n                  <div v-if=\"dynamicPreview\" class=\"app-fee-row\"><span>有效月</span><span class=\"app-fee-val\">{{ optionLabel('validity', formData.validity) || '长期有效' }}</span></div>\r\n                  <div v-if=\"!dynamicPreview\" class=\"app-fee-row highlight\"><span>收费方式</span><span class=\"app-fee-val\">首月按天折算，次月起按月收取</span></div>\r\n                </div>\r\n              </div>\r\n              <!-- 规则说明 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">规则说明</div>\r\n                <div class=\"app-rule-list\">\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>生效方式：{{ optionLabel('effRule', formData.effRule) || '立即生效' }}</div>\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>退订方式：{{ optionLabel('cancelRule', formData.cancelRule) || '立即失效' }}</div>\r\n                  <div v-if=\"!dynamicPreview\" class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>流量可结转至次月</div>\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>使用范围：{{ (Array.isArray(formData.applicableArea) ? formData.applicableArea.map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('、') : optionLabel('applicableArea', formData.applicableArea)) || '四川省内' }}</div>\r\n                  <div v-if=\"dialogueMode\" class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>前项限制：{{ (Array.isArray(formData.preLimitTable) ? formData.preLimitTable : []).map((r) => r.tipMsg).filter(Boolean).join('，') || '目标客群为在校大学生，允许办理' }}</div>\r\n                  <div v-else class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>新老商品互斥，不可同时订购</div>\r\n                </div>\r\n              </div>\r\n              <!-- 办理按钮 -->\r\n              <div class=\"app-action-bar\">\r\n                <button class=\"app-btn-secondary\">加入购物转</button>\r\n                <button class=\"app-btn-primary\">立即办理</button>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 板块2: 免填 -->\r\n            <div v-if=\"phoneTab === 'freefill'\" class=\"app-page\">\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">免填单信息</div>\r\n                <div class=\"app-formfill-list\">\r\n                  <div class=\"app-formfill-row\" v-for=\"item in freeFillItems\" :key=\"item.label\">\r\n                    <span class=\"app-formfill-label\">{{ item.label }}</span>\r\n                    <span class=\"app-formfill-value\" :class=\"{ highlight: item.highlight }\">{{ item.value }}</span>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <div v-if=\"!dynamicPreview\" class=\"app-card\">\r\n                <div class=\"app-card-title\">优惠信息</div>\r\n                <div class=\"app-discount-box\">\r\n                  <div class=\"app-discount-tag\">首月半价</div>\r\n                  <div class=\"app-discount-detail\">\r\n                    <div class=\"app-discount-title\">新用户专属</div>\r\n                    <div class=\"app-discount-desc\">首月仅需29.50元，次月起恢复99元/月</div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <div class=\"app-action-bar\">\r\n                <button class=\"app-btn-primary full\">确认受理</button>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 板块3: 短信模板预览 -->\r\n            <div v-if=\"phoneTab === 'sms'\" class=\"app-page\">\r\n              <div class=\"app-sms-chat\">\r\n                <div class=\"app-sms-time\">今天 {{ phoneTime }}</div>\r\n                <div class=\"app-sms-msg\" v-for=\"(item, idx) in smsItems\" :key=\"idx\">\r\n                  <div class=\"app-sms-avatar\">\r\n                    <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/></svg>\r\n                  </div>\r\n                  <div class=\"app-sms-bubble-wrap\">\r\n                    <div class=\"app-sms-label\">{{ item.label }}</div>\r\n                    <div class=\"app-sms-bubble\">{{ item.value }}</div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 底部Tab导航 -->\r\n          <div class=\"phone-tabbar\">\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'user' }\" @click=\"phoneTab = 'user'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"/><circle cx=\"12\" cy=\"7\" r=\"4\"/></svg>\r\n              <span>受理信息</span>\r\n            </div>\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'freefill' }\" @click=\"phoneTab = 'freefill'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\r\n              <span>免填单</span>\r\n            </div>\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'sms' }\" @click=\"phoneTab = 'sms'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/></svg>\r\n              <span>短信预览</span>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 手机Home -->\r\n          <div class=\"phone-home-indicator\"></div>\r\n        </div>\r\n      </div>\r\n    </div>\r\n\r\n      <div class=\"config-footer\">\r\n      <!-- 批量配置导航：第x/共y + 上一个'下一个'-->\r\n      <div v-if=\"batchMode\" class=\"batch-nav\">\r\n        <button\r\n          class=\"batch-nav-btn\"\r\n          :disabled=\"!batchContext.hasPrev || submitted\"\r\n          @click=\"$emit('go-prev')\"\r\n          :title=\"batchContext.hasPrev ? '上一个草稿商品' : '已是第一个'\"\r\n        >\r\n          <svg width=\"13\" height=\"13\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n            <polyline points=\"15 18 9 12 15 6\"/>\r\n          </svg>\r\n          上一个\r\n        </button>\r\n        <span class=\"batch-nav-count\">第'{{ batchContext.index }} / {{ batchContext.total }} 户</span>\r\n        <button\r\n          class=\"batch-nav-btn\"\r\n          :disabled=\"!batchContext.hasNext || submitted\"\r\n          @click=\"$emit('go-next')\"\r\n          :title=\"batchContext.hasNext ? '下一个草稿商品' : '已是最后一个'\"\r\n        >\r\n          下一个\r\n          <svg width=\"13\" height=\"13\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n            <polyline points=\"9 18 15 12 9 6\"/>\r\n          </svg>\r\n        </button>\r\n      </div>\r\n      <template v-if=\"!submitted\">\r\n      </template>\r\n      <div v-else class=\"footer-submitted\" :class=\"currentStatus\">\r\n        <svg v-if=\"currentStatus === 'completed' || currentStatus === 'testing'\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <polyline points=\"20 6 9 17 4 12\"/>\r\n        </svg>\r\n        <svg v-else width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <circle cx=\"12\" cy=\"12\" r=\"10\"/>\r\n          <polyline points=\"12 6 12 12 16 14\"/>\r\n        </svg>\r\n        <span class=\"footer-submitted-text\">{{ footerSubmittedText }}</span>\r\n        <span class=\"footer-submitted-id\">工单编号：{{ productInfo.workOrderId }}</span>\r\n      </div>\r\n    </div>\r\n\r\n    <!-- 智能稽核弹窗 -->\r\n    <transition name=\"audit-fade\">\r\n      <div v-if=\"auditVisible\" class=\"audit-overlay\" @click.self=\"auditVisible = false\">\r\n        <div class=\"audit-modal\">\r\n          <div class=\"audit-modal-header\">\r\n            <span class=\"audit-modal-title\">智能稽核</span>\r\n            <button class=\"audit-close\" @click=\"auditVisible = false\">\r\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                <line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/>\r\n                <line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/>\r\n              </svg>\r\n            </button>\r\n          </div>\r\n          <div class=\"audit-modal-body\">\r\n            <div v-if=\"auditLoading\" class=\"audit-loading\">\r\n              <div class=\"audit-spinner\"></div>\r\n              <p>{{ auditProgressText }}</p>\r\n            </div>\r\n            <div v-else class=\"audit-results\">\r\n              <div v-for=\"(item, idx) in auditResults\" :key=\"idx\" class=\"audit-item\" :class=\"item.type\">\r\n                <div class=\"audit-icon\">\r\n                  <svg v-if=\"item.type === 'success'\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                    <polyline points=\"20 6 9 17 4 12\"/>\r\n                  </svg>\r\n                  <svg v-else width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                    <circle cx=\"12\" cy=\"12\" r=\"10\"/>\r\n                    <line x1=\"15\" y1=\"9\" x2=\"9\" y2=\"15\"/>\r\n                    <line x1=\"9\" y1=\"9\" x2=\"15\" y2=\"15\"/>\r\n                  </svg>\r\n                </div>\r\n                <div class=\"audit-text\">\r\n                  <div class=\"audit-item-title\">{{ item.title }}</div>\r\n                  <div class=\"audit-item-desc\">{{ item.desc }}</div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n    </transition>\r\n  </div>\r\n";
  ConfigWorkbench.__source = 'ConfigWorkbench';
  global.ConfigWorkbench = ConfigWorkbench;
})(window);
